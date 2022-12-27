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

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_AUTO_TIMEOUT_MAX;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_AUTO_TIMEOUT_MIN;

public class CoinJoinClientManager {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinClientManager.class);
    private static Random random = new Random();
    // Keep track of the used Masternodes
    private final ArrayList<TransactionOutPoint> masternodesUsed = new ArrayList<>();

    private final ReentrantLock lock = Threading.lock("deqsessions");

    // TODO: or map<denom, CoinJoinClientSession> ??
    @GuardedBy("lock")
    private final Deque<CoinJoinClientSession> deqSessions = new ArrayDeque<>();


    private final AtomicBoolean isMixing = new AtomicBoolean(false);

    private int cachedLastSuccessBlock = 0;
    private int minBlocksToWait = 1; // how many blocks to wait for after one successful mixing tx in non-multisession mode
    private String strAutoDenomResult = "";

    private final Context context;
    private final Wallet mixingWallet;
    
    // Keep track of current block height
    private int cachedBlockHeight = 0;

    private boolean waitForAnotherBlock() {
        if (context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) &&
                !mixingWallet.getContext().masternodeSync.isBlockchainSynced()) return true;

        if (CoinJoinClientOptions.isMultiSessionEnabled()) return false;

        return cachedBlockHeight - cachedLastSuccessBlock < minBlocksToWait;
    }

    // Make sure we have enough keys since last backup
    private boolean checkAutomaticBackup() {
        return CoinJoinClientOptions.isEnabled() && isMixing();
    }

    public int cachedNumBlocks = Integer.MAX_VALUE;    // used for the overview screen

    public CoinJoinClientManager(Wallet wallet) {
        mixingWallet = wallet;
        context = wallet.getContext();
    }

    public void processMessage(Peer from, Message message, boolean enable_bip61) {
        if (!CoinJoinClientOptions.isEnabled())
            return;
        if (context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) && !context.masternodeSync.isBlockchainSynced())
            return;

        if (message instanceof CoinJoinStatusUpdate ||
                message instanceof CoinJoinFinalTransaction ||
                message instanceof CoinJoinComplete) {
            lock.lock();
            try {
                for (CoinJoinClientSession session : deqSessions) {
                    session.processMessage(from, message, enable_bip61);
                }
            } finally {
                lock.unlock();
            }
        }
    }



    public boolean startMixing() {
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

    public StringBuilder getStatuses() {
        StringBuilder status = new StringBuilder();
        boolean waitForBlock = waitForAnotherBlock();

        lock.lock();
        try {
            for (CoinJoinClientSession session :deqSessions){
                status.append(session.getStatus(waitForBlock)).append("; ");
            }
            return status;
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

    /// Passively run mixing in the background according to the configuration in settings
    public boolean doAutomaticDenominating() {
        return doAutomaticDenominating(false);
    }
    public boolean doAutomaticDenominating(boolean dryRun) {
        if (!CoinJoinClientOptions.isEnabled() || !isMixing())
            return false;

        if (context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) && !mixingWallet.getContext().masternodeSync.isBlockchainSynced()) {
            strAutoDenomResult = "Can't mix while sync in progress.";
            return false;
        }

        if (!dryRun && mixingWallet.isEncrypted()) {
            strAutoDenomResult = "Wallet is locked.";
            return false;
        }

        int mnCountEnabled = context.masternodeListManager.getListAtChainTip().getValidMNsCount();

        // If we've used 90% of the Masternode list then drop the oldest first ~30%
        int thresholdHigh = (int) (mnCountEnabled * 0.9);
        int thresholdLow = (int) (thresholdHigh * 0.7);
        log.info("Checking masternodesUsed: size: {}, threshold: {}", masternodesUsed.size(), thresholdHigh);

        if (masternodesUsed.size() > thresholdHigh) {
            // remove the first masternodesUsed.size() - thresholdLow masternodes
            // this might be a problem for SPV
            lock.lock();
            try {
                Iterator<TransactionOutPoint> it = masternodesUsed.iterator();
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
                deqSessions.addLast(new CoinJoinClientSession(mixingWallet));
            }
            for (CoinJoinClientSession session: deqSessions) {
                if (!checkAutomaticBackup()) return false;

                if (waitForAnotherBlock()) {
                    strAutoDenomResult = "Last successful action was too recent.";
                    log.info("DoAutomaticDenominating -- {}", strAutoDenomResult);
                    return false;
                }

                fResult &= session.doAutomaticDenominating(dryRun);
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
                if (mnMixing != null && mnMixing.getService().equals(mnAddr) && session.getState().get() == PoolState.POOL_STATE_QUEUE) {
                    session.submitDenominate();
                    return true;
                } else {
                    log.info("mixingMasternode {} != mnAddr {}", mnMixing != null ? mnMixing.getService().getSocketAddress() : "null", mnAddr.getSocketAddress());
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
                if ((mnMixing = session.getMixingMasternodeInfo()) != null && mnMixing.getCollateralOutpoint().equals(dsq.getMasternodeOutpoint())) {
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
    public void addUsedMasternode(TransactionOutPoint outpointMn) {
        masternodesUsed.add(outpointMn);
    }
    public Masternode getRandomNotUsedMasternode() {
        SimplifiedMasternodeList mnList = context.masternodeListManager.getListAtChainTip();

        int nCountEnabled = mnList.getValidMNsCount();
        int nCountNotExcluded = nCountEnabled -  masternodesUsed.size();

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

        HashSet<TransactionOutPoint> excludeSet = new HashSet<>(masternodesUsed);

        // loop through
        for (Masternode dmn : vpMasternodesShuffled) {
            if (excludeSet.contains(dmn.getCollateralOutpoint())) {
                continue;
            }

            log.info("coinjoin:  found, masternode={}", dmn.getProTxHash());
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
    }

    public void updatedSuccessBlock() {
        cachedLastSuccessBlock = cachedBlockHeight;
    }
    static int nTick = 0;
    static int nDoAutoNextRun = nTick + COINJOIN_AUTO_TIMEOUT_MIN;
    public void doMaintenance() {
        if (!CoinJoinClientOptions.isEnabled())
            return;

        if (context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE)
                &&!context.masternodeSync.isBlockchainSynced())
            return;

        nTick++;
        checkTimeout();
        processPendingDsaRequest();
        if (nDoAutoNextRun >= nTick) {
            doAutomaticDenominating();
            nDoAutoNextRun = nTick + COINJOIN_AUTO_TIMEOUT_MIN + random.nextInt(COINJOIN_AUTO_TIMEOUT_MAX - COINJOIN_AUTO_TIMEOUT_MIN);
        }
    }

}
