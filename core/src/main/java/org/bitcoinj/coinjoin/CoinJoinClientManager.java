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
package org.bitcoinj.coinjoin;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.coinjoin.listeners.CoinJoinTransactionListener;
import org.bitcoinj.coinjoin.listeners.MixingCompleteListener;
import org.bitcoinj.coinjoin.listeners.MixingStartedListener;
import org.bitcoinj.coinjoin.listeners.SessionCompleteListener;
import org.bitcoinj.coinjoin.listeners.SessionStartedListener;
import org.bitcoinj.coinjoin.utils.CoinJoinManager;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.MasternodeMetaDataManager;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletEx;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkArgument;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_AUTO_TIMEOUT_MAX;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_AUTO_TIMEOUT_MIN;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_EXTRA;

public class CoinJoinClientManager implements WalletCoinsReceivedEventListener {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinClientManager.class);
    private static final Random random = new Random();
    // Keep track of the used Masternodes
    private final ArrayList<Sha256Hash> masternodesUsed = new ArrayList<>();

    private final ReentrantLock lock = Threading.lock("deqsessions");

    // TODO: or map<denom, CoinJoinClientSession> ??
    @GuardedBy("lock")
    private final Deque<CoinJoinClientSession> deqSessions = new ArrayDeque<>();

    private final AtomicBoolean isMixing = new AtomicBoolean(false);
    private boolean stopOnNothingToDo = false;
    private SettableFuture<Boolean> mixingFinished;
    private final EnumSet<PoolStatus> continueMixingOnStatus = EnumSet.noneOf(PoolStatus.class);

    private int cachedLastSuccessBlock = 0;
    private int minBlocksToWait = 1; // how many blocks to wait for after one successful mixing tx in non-multisession mode
    private String strAutoDenomResult = "";

    private final Context context;
    private final MasternodeSync masternodeSync;
    private final CoinJoinManager coinJoinManager;
    private final SimplifiedMasternodeListManager masternodeListManager;
    private final MasternodeMetaDataManager masternodeMetaDataManager;
    private final WalletEx mixingWallet;
    
    // Keep track of current block height
    private int cachedBlockHeight = 0;
    private final CopyOnWriteArrayList<ListenerRegistration<SessionStartedListener>> sessionStartedListeners
            = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListenerRegistration<SessionCompleteListener>> sessionCompleteListeners
            = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListenerRegistration<MixingStartedListener>> mixingStartedListeners
            = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListenerRegistration<MixingCompleteListener>> mixingCompleteListeners
            = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListenerRegistration<CoinJoinTransactionListener>> transactionListeners
            = new CopyOnWriteArrayList<>();

    private boolean waitForAnotherBlock() {
        if (masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) &&
                !masternodeSync.isBlockchainSynced()) return true;

        if (CoinJoinClientOptions.isMultiSessionEnabled()) return false;

        return cachedBlockHeight - cachedLastSuccessBlock < minBlocksToWait;
    }

    public boolean isWaitingForNewBlock() {
        return waitForAnotherBlock();
    }

    // Make sure we have enough keys since last backup
    private boolean checkAutomaticBackup() {
        // Let the KeyChain classes handle this
        return true;
    }

    public int cachedNumBlocks = Integer.MAX_VALUE;    // used for the overview screen

    public CoinJoinClientManager(Wallet wallet, MasternodeSync masternodeSync, CoinJoinManager coinJoinManager, SimplifiedMasternodeListManager masternodeListManager, MasternodeMetaDataManager masternodeMetaDataManager) {
        checkArgument(wallet instanceof WalletEx);
        mixingWallet = (WalletEx) wallet;
        context = wallet.getContext();
        this.masternodeSync = masternodeSync;
        this.coinJoinManager = coinJoinManager;
        this.masternodeMetaDataManager = masternodeMetaDataManager;
        this.masternodeListManager = masternodeListManager;
        mixingWallet.addCoinsReceivedEventListener(this);
        this.coinJoinManager.addWallet(mixingWallet);
    }

    public CoinJoinClientManager(WalletEx wallet, MasternodeSync masternodeSync, CoinJoinManager coinJoinManager, SimplifiedMasternodeListManager masternodeListManager, MasternodeMetaDataManager masternodeMetaDataManager) {
        mixingWallet = wallet;
        context = wallet.getContext();
        this.masternodeSync = masternodeSync;
        this.coinJoinManager = coinJoinManager;
        this.masternodeMetaDataManager = masternodeMetaDataManager;
        this.masternodeListManager = masternodeListManager;
        mixingWallet.addCoinsReceivedEventListener(this);
        this.coinJoinManager.addWallet(mixingWallet);
    }

    public Message processMessage(Peer from, Message message, boolean enable_bip61) {
        if (!CoinJoinClientOptions.isEnabled())
            return message;
        if (masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) && !masternodeSync.isBlockchainSynced())
            return message;

        if (message instanceof CoinJoinStatusUpdate ||
                message instanceof CoinJoinFinalTransaction ||
                message instanceof CoinJoinComplete) {
            lock.lock();
            try {
                for (CoinJoinClientSession session : deqSessions) {
                    boolean messageHandled = session.processMessage(from, message, enable_bip61);
                    if (messageHandled) {
                        return null;
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        return message;
    }



    public boolean startMixing() {
        queueMixingStartedListeners();
        return isMixing.compareAndSet(false, true);
    }
    public void stopMixing() {
        isMixing.set(false);
    }
    public boolean isMixing() {
        return isMixing.get();
    }
    public void resetPool() {
        cachedLastSuccessBlock = 0;
        masternodesUsed.clear();

        lock.lock();
        try {
            for (CoinJoinClientSession session : deqSessions){
                session.resetPool();
            }
            deqSessions.clear();
        } finally {
            lock.unlock();
        }
    }

    public String getStatuses() {
        StringBuilder status = new StringBuilder();
        boolean waitForBlock = waitForAnotherBlock();

        lock.lock();
        try {
            for (CoinJoinClientSession session :deqSessions){
                status.append(session.getStatus(waitForBlock)).append("; ");
            }
            return status.toString();
        } finally {
            lock.unlock();
        }
    }

    public String getSessionDenoms() {
        StringBuilder strSessionDenoms = new StringBuilder();

        lock.lock();
        try {
            for (CoinJoinClientSession session : deqSessions) {
                strSessionDenoms.append(CoinJoin.denominationToString(session.getSessionDenom()));
                strSessionDenoms.append("; ");
            }
            return strSessionDenoms.length() == 0 ? "N/A" : strSessionDenoms.toString();
        } finally {
            lock.unlock();
        }
    }

    private long lastTimeReportTooRecent = 0;
    private long lastMasternodesUsed = 0;

    /// Passively run mixing in the background according to the configuration in settings
    public boolean doAutomaticDenominating() {
        return doAutomaticDenominating(false, false);
    }

    public boolean doAutomaticDenominating(boolean finishCurrentSessions) {
        return doAutomaticDenominating(finishCurrentSessions, false);
    }

    public boolean doAutomaticDenominating(boolean finishCurrentSessions, boolean dryRun) {
        if (!CoinJoinClientOptions.isEnabled() || (!dryRun && !isMixing()))
            return false;

        if (masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) && !masternodeSync.isBlockchainSynced()) {
            strAutoDenomResult = "Can't mix while sync in progress.";
            return false;
        }

        if (!dryRun && mixingWallet.isEncrypted() && coinJoinManager.requestKeyParameter(mixingWallet) == null) {
            strAutoDenomResult = "Wallet is locked.";
            return false;
        }

        int mnCountEnabled = masternodeListManager.getListAtChainTip().getValidMNsCount();

        // If we've used 90% of the Masternode list then drop the oldest first ~30%
        int thresholdHigh = (int) (mnCountEnabled * 0.9);
        int thresholdLow = (int) (thresholdHigh * 0.7);

        if (!waitForAnotherBlock()) {
            if (masternodesUsed.size() != lastMasternodesUsed) {
                log.info("Checking masternodesUsed: size: {}, threshold: {}", masternodesUsed.size(), thresholdHigh);
                lastMasternodesUsed = masternodesUsed.size();
            }
        }

        if (masternodesUsed.size() > thresholdHigh) {
            // remove the first masternodesUsed.size() - thresholdLow masternodes
            // this might be a problem for SPV
            lock.lock();
            try {
                Iterator<Sha256Hash> it = masternodesUsed.iterator();
                int i = 0;
                while (it.hasNext()) {
                    it.next();
                    if (i < masternodesUsed.size() - thresholdLow) {
                        it.remove();
                    }
                    i++;
                }
            } finally {
                lock.unlock();
            }

            log.info("  masternodesUsed: new size: {}, threshold: {}", masternodesUsed.size(), thresholdHigh);
        }

        boolean fResult = true;

        lock.lock();
        try {
            if (deqSessions.size() < CoinJoinClientOptions.getSessions()) {
                CoinJoinClientSession newSession = new CoinJoinClientSession(mixingWallet, coinJoinManager, masternodeListManager, masternodeMetaDataManager, masternodeSync);
                log.info("creating new session: {}: ", newSession.getId());
                for (ListenerRegistration<SessionCompleteListener> listener : sessionCompleteListeners) {
                    newSession.addSessionCompleteListener(listener.executor, listener.listener);
                }
                for (ListenerRegistration<SessionStartedListener> listener : sessionStartedListeners) {
                    newSession.addSessionStartedListener(listener.executor, listener.listener);
                }
                for (ListenerRegistration<CoinJoinTransactionListener> listener : transactionListeners) {
                    newSession.addTransationListener (listener.executor, listener.listener);
                }
                deqSessions.addLast(newSession);
            }
            for (CoinJoinClientSession session: deqSessions) {
                if (!checkAutomaticBackup()) return false;

                // we may not need this
                if (!dryRun && waitForAnotherBlock()) {
                    if (Utils.currentTimeMillis() - lastTimeReportTooRecent > 15000 ) {
                        strAutoDenomResult = "Last successful action was too recent.";
                        log.info("DoAutomaticDenominating: {}", strAutoDenomResult);
                        lastTimeReportTooRecent = Utils.currentTimeMillis();
                    }
                    return false;
                }

                fResult &= session.doAutomaticDenominating(finishCurrentSessions, dryRun);
            }

            return fResult;
        } finally {
            lock.unlock();
        }
    }

    public boolean trySubmitDenominate(MasternodeAddress mnAddr) {
        lock.lock();
        try {
            for (CoinJoinClientSession session : deqSessions) {
                Masternode mnMixing = session.getMixingMasternodeInfo();
                if (mnMixing != null && mnMixing.getService().equals(mnAddr) && session.getState() == PoolState.POOL_STATE_QUEUE) {
                    session.submitDenominate();
                    return true;
                } else {
                    log.info(COINJOIN_EXTRA, "mixingMasternode {} != mnAddr {} or {} != {}",
                            mnMixing != null ? mnMixing.getService().getSocketAddress() : "null", mnAddr.getSocketAddress(),
                            session.getState(), PoolState.POOL_STATE_QUEUE);
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean markAlreadyJoinedQueueAsTried(CoinJoinQueue dsq) {
        lock.lock();
        try {
            for (CoinJoinClientSession session :deqSessions){
                Masternode mnMixing;
                if ((mnMixing = session.getMixingMasternodeInfo()) != null && mnMixing.getProTxHash().equals(dsq.getProTxHash())) {
                    dsq.setTried(true);
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void checkTimeout() {
        if (!CoinJoinClientOptions.isEnabled() || !isMixing()) return;

        lock.lock();
        try {
            for (CoinJoinClientSession session :deqSessions){
                if (session.checkTimeout()) {
                    strAutoDenomResult = "Session timed out.";
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void processPendingDsaRequest() {
        lock.lock();
        try {
            for (CoinJoinClientSession session :deqSessions){
                if (session.processPendingDsaRequest()) {
                    strAutoDenomResult = "Mixing in progress...";
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // TODO: this is not good because SPV doesn't know the outpoint
    public void addUsedMasternode(Sha256Hash proTxHash) {
        masternodesUsed.add(proTxHash);
    }
    public Masternode getRandomNotUsedMasternode() {
        SimplifiedMasternodeList mnList = masternodeListManager.getListAtChainTip();

        int nCountEnabled = mnList.getValidMNsCount();
        int nCountNotExcluded = nCountEnabled - masternodesUsed.size();

        log.info("coinjoin:  {} enabled masternodes, {} masternodes to choose from", nCountEnabled, nCountNotExcluded);
        if (nCountNotExcluded < 1) {
            return null;
        }

        // fill a vector
        ArrayList<Masternode> vpMasternodesShuffled = new ArrayList<>(nCountEnabled);

        mnList.forEachMN(true, new SimplifiedMasternodeList.ForeachMNCallback() {
            @Override
            public void processMN(SimplifiedMasternodeListEntry mn) {
                vpMasternodesShuffled.add(mn);
            }
        });

        // shuffle pointers
        Collections.shuffle(vpMasternodesShuffled);

        HashSet<Sha256Hash> excludeSet = new HashSet<>(masternodesUsed);

        // loop through
        for (Masternode dmn : vpMasternodesShuffled) {
            if (excludeSet.contains(dmn.getProTxHash())) {
                continue;
            }

            log.info("coinjoin: found, masternode={}", dmn.getProTxHash().toString().substring(0, 16));
            return dmn;
        }

        log.info("coinjoin: failed");
        return null;
    }

    private final NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            cachedBlockHeight = block.getHeight();
        }
    };

    public void setBlockChain(AbstractBlockChain blockChain) {
        blockChain.addNewBestBlockListener(newBestBlockListener);
        cachedBlockHeight = blockChain.getBestChainHeight();
    }

    public void close(AbstractBlockChain blockChain) {
        blockChain.removeNewBestBlockListener(newBestBlockListener);
        mixingWallet.removeCoinsReceivedEventListener(this);
        coinJoinManager.removeWallet(mixingWallet);
    }

    public void updatedSuccessBlock() {
        cachedLastSuccessBlock = cachedBlockHeight;
    }
    static int nTick = 0;
    static int nDoAutoNextRun = nTick + COINJOIN_AUTO_TIMEOUT_MIN;
    public void doMaintenance(boolean finishCurrentSession) {
        if (!CoinJoinClientOptions.isEnabled())
            return;

        if (masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE)
                &&!masternodeSync.isBlockchainSynced())
            return;

        nTick++;
        checkTimeout();
        processPendingDsaRequest();
        if (nDoAutoNextRun >= nTick) {
            doAutomaticDenominating(finishCurrentSession);
            nDoAutoNextRun = nTick + COINJOIN_AUTO_TIMEOUT_MIN + random.nextInt(COINJOIN_AUTO_TIMEOUT_MAX - COINJOIN_AUTO_TIMEOUT_MIN);
        }

        // are all sessions idle?
        boolean isIdle = !deqSessions.isEmpty(); // false if no sessions created yet
        for (CoinJoinClientSession session : deqSessions) {
            if (!session.hasNothingToDo()) {
                isIdle = false;
                break;
            }
        }
        // if all sessions idle, then trigger stop mixing
        if (isIdle) {
            List<PoolStatus> statuses = getSessionsStatus();
            for (PoolStatus status : statuses) {
                if (status == PoolStatus.FINISHED || (status.isError() && !continueMixingOnStatus.contains(status)))
                    triggerMixingFinished();
            }

        }
    }

    public void setStopOnNothingToDo(boolean stopOnNothingToDo) {
        this.stopOnNothingToDo = stopOnNothingToDo;
        if (stopOnNothingToDo)
            this.mixingFinished = SettableFuture.create();
    }

    protected void triggerMixingFinished() {
        if (stopOnNothingToDo) {
            mixingFinished.set(true);
            queueMixingCompleteListeners();
        }
    }

    public SettableFuture<Boolean> getMixingFinishedFuture() {
        return mixingFinished;
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
        // This is thread safe, so we don't need to take the lock.
        sessionStartedListeners.add(new ListenerRegistration<>(listener, executor));
        for (CoinJoinClientSession session: deqSessions) {
            session.addSessionStartedListener(executor, listener);
        }
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeSessionStartedListener(SessionStartedListener listener) {
        for (CoinJoinClientSession session: deqSessions) {
            session.removeSessionStartedListener(listener);
        }
        return ListenerRegistration.removeFromList(listener, sessionStartedListeners);
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
        // This is thread safe, so we don't need to take the lock.
        sessionCompleteListeners.add(new ListenerRegistration<>(listener, executor));
        for (CoinJoinClientSession session: deqSessions) {
            session.addSessionCompleteListener(executor, listener);
        }
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeSessionCompleteListener(SessionCompleteListener listener) {
        for (CoinJoinClientSession session: deqSessions) {
            session.removeSessionCompleteListener(listener);
        }
        return ListenerRegistration.removeFromList(listener, sessionCompleteListeners);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addMixingStartedListener(MixingStartedListener listener) {
        addMixingStartedListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addMixingStartedListener(Executor executor, MixingStartedListener listener) {
        // This is thread safe, so we don't need to take the lock.
        mixingStartedListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeMixingStartedListener(MixingStartedListener listener) {
        return ListenerRegistration.removeFromList(listener, mixingStartedListeners);
    }

    protected void queueMixingStartedListeners() {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<MixingStartedListener> registration : mixingStartedListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onMixingStarted(mixingWallet);
                }
            });
        }
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addSessionCompleteListener(MixingCompleteListener listener) {
        addMixingCompleteListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addMixingCompleteListener(Executor executor, MixingCompleteListener listener) {
        // This is thread safe, so we don't need to take the lock.
        mixingCompleteListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeMixingCompleteListener(MixingCompleteListener listener) {
        return ListenerRegistration.removeFromList(listener, mixingCompleteListeners);
    }

    protected void queueMixingCompleteListeners() {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<MixingCompleteListener> registration : mixingCompleteListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onMixingComplete(mixingWallet, getSessionsStatus());
                }
            });
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
        // This is thread safe, so we don't need to take the lock.
        transactionListeners.add(new ListenerRegistration<>(listener, executor));
        for (CoinJoinClientSession session: deqSessions) {
            session.addTransationListener (executor, listener);
        }
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeTransationListener(CoinJoinTransactionListener listener) {
        for (CoinJoinClientSession session: deqSessions) {
            session.removeTransactionListener(listener);
        }
        return ListenerRegistration.removeFromList(listener, transactionListeners);
    }

    public List<PoolStatus> getSessionsStatus() {
        ArrayList<PoolStatus> sessionsStatus = Lists.newArrayList();
        for (CoinJoinClientSession session : deqSessions) {
            sessionsStatus.add(session.getStatus());
        }
        return sessionsStatus;
    }

    public EnumSet<PoolStatus> getContinueMixingOnStatus() {
        return continueMixingOnStatus;
    }

    public void addContinueMixingOnError(PoolStatus error) {
        continueMixingOnStatus.add(error);
    }

    public void processTransaction(Transaction tx) {
        deqSessions.forEach(coinJoinClientSession -> coinJoinClientSession.processTransaction(tx));
    }

    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        processTransaction(tx);
    }
}
