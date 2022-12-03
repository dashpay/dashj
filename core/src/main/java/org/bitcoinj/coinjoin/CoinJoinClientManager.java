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

import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class CoinJoinClientManager {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinClientManager.class);
    // Keep track of the used Masternodes
    private final ArrayList<TransactionOutPoint> masternodesUsed = new ArrayList<>();

    private final ReentrantLock lock = Threading.lock("deqsessions");

    // TODO: or map<denom, CoinJoinClientSession> ??
    @GuardedBy("lock")
    private final Deque<CoinJoinClientSession> deqSessions = new ArrayDeque<>();


    private final AtomicBoolean isMixing = new AtomicBoolean(false);

    private int cachedLastSuccessBlock = 0;
    private int minBlocksToWait = 1; // how many blocks to wait for after one successful mixing tx in non-multisession mode
    private final StringBuilder strAutoDenomResult = new StringBuilder();

    private final Context context;
    private final Wallet mixingWallet;
    
    // Keep track of current block height
    private int cachedBlockHeight = 0;

    private boolean waitForAnotherBlock() {
        if (!mixingWallet.getContext().masternodeSync.isBlockchainSynced()) return true;

        if (CoinJoinClientOptions.isMultiSessionEnabled()) return false;

        return cachedBlockHeight - cachedLastSuccessBlock < minBlocksToWait;
    }

    // Make sure we have enough keys since last backup
    private boolean checkAutomaticBackup() {
        if (!CoinJoinClientOptions.isEnabled() || !isMixing())
            return false;

        return true;
    }

    public int cachedNumBlocks = Integer.MAX_VALUE;    // used for the overview screen
    public boolean createAutoBackups = true; // builtin support for automatic backups

    public CoinJoinClientManager(Wallet wallet) {
        mixingWallet = wallet;
        context = wallet.getContext();
    }

    public void processMessage(Peer from, String msg_type, boolean enable_bip61) {

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
                session.ResetPool();
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

    public boolean getMixingMasternodesInfo(ArrayList<Masternode> vecDmnsRet) {
        vecDmnsRet.clear();
        lock.lock();
        try {
            for (CoinJoinClientSession session : deqSessions) {
                Masternode dmn = session.getMixingMasternodeInfo();
                if (dmn != null) {
                    vecDmnsRet.add(dmn);
                }
            }
            return !vecDmnsRet.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /// Passively run mixing in the background according to the configuration in settings
    public boolean doAutomaticDenonimating() {
        return doAutomaticDenominating(false);
    }
    public boolean doAutomaticDenominating(boolean dryRun) {
        if (!CoinJoinClientOptions.isEnabled() || !isMixing()) return false;

        if (!mixingWallet.getContext().masternodeSync.isBlockchainSynced()) {
            strAutoDenomResult.append("Can't mix while sync in progress.");
            return false;
        }

        if (!dryRun && mixingWallet.isEncrypted()) {
            strAutoDenomResult.append("Wallet is locked.");
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
            Iterator<TransactionOutPoint> it = masternodesUsed.iterator();
            int i = 0;
            while (it.hasNext()) {
                it.next();
                if (i < masternodesUsed.size() - thresholdLow) {
                    it.remove();
                }
                i++;
            }

            log.info("  masternodesUsed: new size: {}, threshold: {}", masternodesUsed.size(), thresholdHigh);
        }

        boolean fResult = true;

        lock.lock();
        try {
            if (deqSessions.size() < CoinJoinClientOptions.getSessions()){
                deqSessions.addLast(new CoinJoinClientSession(mixingWallet));
            }
            for (CoinJoinClientSession session: deqSessions){
                if (!checkAutomaticBackup()) return false;

                if (waitForAnotherBlock()) {
                    strAutoDenomResult.append("Last successful action was too recent.");
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
        return false;
    }

    public boolean markAlreadyJoinedQueueAsTried(CoinJoinQueue dsq) {
        return false;
    }

    public void checkTimeout() {

    }

    public void processPendingDsaRequest() {

    }

    // TODO: this is not good because SPV doesn't know the outpoint
    public void addUsedMasternode(TransactionOutPoint outpointMn) {
        masternodesUsed.add(outpointMn);
    }
    public Masternode getRandomNotUsedMasternode() {
        return null;
    }

    public void updatedSuccessBlock() {
        cachedLastSuccessBlock = cachedBlockHeight;
    }

    public void doMaintenance() {

    }

}
