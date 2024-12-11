/*
 * Copyright (c) 2022 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoinj.coinjoin.utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinBroadcastTx;
import org.bitcoinj.coinjoin.CoinJoinClientManager;
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.coinjoin.CoinJoinClientQueueManager;
import org.bitcoinj.coinjoin.CoinJoinClientSession;
import org.bitcoinj.coinjoin.CoinJoinComplete;
import org.bitcoinj.coinjoin.CoinJoinFinalTransaction;
import org.bitcoinj.coinjoin.CoinJoinQueue;
import org.bitcoinj.coinjoin.CoinJoinStatusUpdate;
import org.bitcoinj.coinjoin.callbacks.RequestDecryptedKey;
import org.bitcoinj.coinjoin.callbacks.RequestKeyParameter;
import org.bitcoinj.coinjoin.listeners.CoinJoinTransactionListener;
import org.bitcoinj.coinjoin.listeners.MixingCompleteListener;
import org.bitcoinj.coinjoin.listeners.MixingStartedListener;
import org.bitcoinj.coinjoin.listeners.SessionCompleteListener;
import org.bitcoinj.coinjoin.listeners.SessionStartedListener;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PreMessageReceivedEventListener;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.MasternodeMetaDataManager;
import org.bitcoinj.evolution.SimplifiedMasternodeListDiff;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.quorums.ChainLocksHandler;
import org.bitcoinj.quorums.QuorumRotationInfo;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletEx;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.bitcoinj.utils.Threading.SAME_THREAD;

public class CoinJoinManager {

    private static final Logger log = LoggerFactory.getLogger(CoinJoinManager.class);
    private final Context context;
    public final HashMap<String, CoinJoinClientManager> coinJoinClientManagers;
    private final CoinJoinClientQueueManager coinJoinClientQueueManager;

    private MasternodeGroup masternodeGroup;
    private PeerGroup peerGroup;
    private SimplifiedMasternodeListManager masternodeListManager;
    private ChainLocksHandler chainLocksHandler;
    private ScheduledFuture<?> schedule;
    private AbstractBlockChain blockChain;

    private RequestKeyParameter requestKeyParameter;
    private RequestDecryptedKey requestDecryptedKey;
    private final ScheduledExecutorService scheduledExecutorService;

    public CoinJoinManager(Context context, ScheduledExecutorService scheduledExecutorService,
                           SimplifiedMasternodeListManager masternodeListManager,
                           MasternodeMetaDataManager masternodeMetaDataManager,
                           MasternodeSync masternodeSync,
                           ChainLocksHandler chainLocksHandler) {
        this.context = context;
        coinJoinClientManagers = new HashMap<>();
        this.masternodeListManager = masternodeListManager;
        this.chainLocksHandler = chainLocksHandler;
        coinJoinClientQueueManager = new CoinJoinClientQueueManager(context, this, masternodeListManager, masternodeMetaDataManager, masternodeSync);
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public static boolean isCoinJoinMessage(Message message) {
        return message instanceof CoinJoinStatusUpdate ||
                message instanceof CoinJoinFinalTransaction ||
                message instanceof CoinJoinComplete ||
                message instanceof CoinJoinQueue ||
                message instanceof CoinJoinBroadcastTx;
    }

    public CoinJoinClientQueueManager getCoinJoinClientQueueManager() {
        return coinJoinClientQueueManager;
    }

    public void processMessage(Peer from, Message message) {
        if (message instanceof CoinJoinQueue) {
            coinJoinClientQueueManager.processDSQueue(from, (CoinJoinQueue) message, false);
        } else if(message instanceof CoinJoinBroadcastTx) {
            processBroadcastTx((CoinJoinBroadcastTx) message);
        } else  {
            for (CoinJoinClientManager clientManager : coinJoinClientManagers.values()) {
                clientManager.processMessage(from, message, false);
            }
        }
    }

    private void processBroadcastTx(CoinJoinBroadcastTx dstx) {
        CoinJoin.addDSTX(dstx);
    }

    int tick = 0;

    public void doMaintenance() {
        if (CoinJoinClientOptions.isEnabled()) {
            // report masternode group
            if (masternodeGroup != null) {
                tick++;
                if (tick % 15 == 0) {
                    log.info(masternodeGroup.toString());
                }
            }
            coinJoinClientQueueManager.doMaintenance();

            for (CoinJoinClientManager clientManager : coinJoinClientManagers.values()) {
                clientManager.doMaintenance();
            }
        }
    }

    private final Runnable maintenanceRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                doMaintenance();
            } catch (Exception x) {
                log.info("error when running doMaintenance", x);
            }
        }
    };

    public void start() {
        log.info("CoinJoinManager starting...");
        schedule = scheduledExecutorService.scheduleWithFixedDelay(
                maintenanceRunnable, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        log.info("CoinJoinManager stopping...");
        if (schedule != null) {
            schedule.cancel(false);
            schedule = null;
        }

        for (CoinJoinClientManager clientManager: coinJoinClientManagers.values()) {
            clientManager.resetPool();
            clientManager.stopMixing();
            clientManager.close(blockChain);
        }
        stopAsync();
    }

    public boolean isRunning() {
        return schedule != null && !schedule.isCancelled();
    }

    public void initMasternodeGroup(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        masternodeGroup = new MasternodeGroup(context, blockChain, masternodeListManager);
        masternodeGroup.setCoinJoinManager(this);
        blockChain.addTransactionReceivedListener(transactionReceivedInBlockListener);
    }

    NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            CoinJoin.updatedBlockTip(block, chainLocksHandler);
        }
    };

    TransactionReceivedInBlockListener transactionReceivedInBlockListener = new TransactionReceivedInBlockListener() {
        @Override
        public void receiveFromBlock(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
            processTransaction(tx);
        }

        @Override
        public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
            return false;
        }
    };

    public void setBlockchain(AbstractBlockChain blockChain, PeerGroup peerGroup) {
        this.blockChain = blockChain;
        this.peerGroup = peerGroup;
        blockChain.addNewBestBlockListener(newBestBlockListener);
        if (peerGroup != null) {
            peerGroup.addPreMessageReceivedEventListener(SAME_THREAD, preMessageReceivedEventListener);
        }
    }

    public void close() {
        if (blockChain != null) {
            blockChain.removeNewBestBlockListener(newBestBlockListener);
            blockChain.removeTransactionReceivedListener(transactionReceivedInBlockListener);
        }
        if (peerGroup != null) {
            peerGroup.removePreMessageReceivedEventListener(preMessageReceivedEventListener);
        }
    }

    public boolean isMasternodeOrDisconnectRequested(MasternodeAddress address) {
        return masternodeGroup.isMasternodeOrDisconnectRequested(address);
    }

    public boolean addPendingMasternode(CoinJoinClientSession session) {
        return masternodeGroup.addPendingMasternode(session);
    }

    public boolean forPeer(MasternodeAddress address, MasternodeGroup.ForPeer forPeer, boolean warn) {
        return masternodeGroup.forPeer(address, forPeer, warn);
    }
    
    public void startAsync() {
        if (!masternodeGroup.isRunning()) {
            log.info("coinjoin: broadcasting senddsq(true) to all peers");
            peerGroup.shouldSendDsq(true);
            masternodeGroup.startAsync();
        }
    }

    public void stopAsync() {
        if (masternodeGroup != null && masternodeGroup.isRunning()) {
            if (peerGroup != null)
                peerGroup.shouldSendDsq(false);
            masternodeGroup.stopAsync();
            masternodeGroup = null;
        }
    }

    public boolean disconnectMasternode(Masternode service) {
        return masternodeGroup.disconnectMasternode(service);
    }

    @VisibleForTesting
    public void setMasternodeGroup(MasternodeGroup masternodeGroup) {
        this.masternodeGroup = masternodeGroup;
        masternodeGroup.setCoinJoinManager(this);
    }

    public SettableFuture<Boolean> getMixingFinishedFuture(Wallet wallet) {
        return coinJoinClientManagers.get(wallet.getDescription()).getMixingFinishedFuture();
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addSessionStartedListener(SessionStartedListener listener) {
        addSessionStartedListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addSessionStartedListener(Executor executor, SessionStartedListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.addSessionStartedListener(executor, listener);
        }
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public void removeSessionStartedListener(SessionStartedListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.removeSessionStartedListener(listener);
        }
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addSessionCompleteListener(SessionCompleteListener listener) {
        addSessionCompleteListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addSessionCompleteListener(Executor executor, SessionCompleteListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.addSessionCompleteListener(executor, listener);
        }
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public void removeSessionCompleteListener(SessionCompleteListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.removeSessionCompleteListener(listener);
        }
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addMixingCompleteListener(MixingStartedListener listener) {
        addMixingStartedListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addMixingStartedListener(Executor executor, MixingStartedListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.addMixingStartedListener(executor, listener);
        }
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public void removeMixingStartedListener(MixingStartedListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.removeMixingStartedListener(listener);
        }
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addMixingCompleteListener(MixingCompleteListener listener) {
        addMixingCompleteListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addMixingCompleteListener(Executor executor, MixingCompleteListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.addMixingCompleteListener(executor, listener);
        }
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public void removeMixingCompleteListener(MixingCompleteListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.removeMixingCompleteListener(listener);
        }
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addTransationListener (CoinJoinTransactionListener listener) {
        addTransationListener (Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addTransationListener (Executor executor, CoinJoinTransactionListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.addTransationListener (executor, listener);
        }
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public void removeTransactionListener(CoinJoinTransactionListener listener) {
        for (CoinJoinClientManager manager : coinJoinClientManagers.values()) {
            manager.removeTransationListener(listener);
        }
    }

    public void setRequestKeyParameter(RequestKeyParameter requestKeyParameter) {
        this.requestKeyParameter = requestKeyParameter;
    }

    public void setRequestDecryptedKey(RequestDecryptedKey requestDecryptedKey) {
        this.requestDecryptedKey = requestDecryptedKey;
    }

    public @Nullable KeyParameter requestKeyParameter(WalletEx mixingWallet) {
        return requestKeyParameter != null ? requestKeyParameter.requestKeyParameter(mixingWallet) : null;
    }

    public @Nullable ECKey requestDecryptKey(ECKey key) {
        return requestDecryptedKey != null ? requestDecryptedKey.requestDecryptedKey(key) : null;
    }

    public boolean isWaitingForNewBlock() {
        return coinJoinClientManagers.values().stream().anyMatch(CoinJoinClientManager::isWaitingForNewBlock);
    }

    public boolean isMixing() {
        return coinJoinClientManagers.values().stream().anyMatch(CoinJoinClientManager::isMixing);
    }

    public void processTransaction(Transaction tx) {
        coinJoinClientManagers.values().forEach(coinJoinClientManager -> coinJoinClientManager.processTransaction(tx));
    }

    public final PreMessageReceivedEventListener preMessageReceivedEventListener = (peer, m) -> {
        if (isCoinJoinMessage(m)) {
            processMessage(peer, m);
            return null;
        }
        return m;
    };
}
