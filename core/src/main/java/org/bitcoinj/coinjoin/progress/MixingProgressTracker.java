/*
 * Copyright (c) 2023 Dash Core Group
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
package org.bitcoinj.coinjoin.progress;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.bitcoinj.coinjoin.PoolMessage;
import org.bitcoinj.coinjoin.PoolState;
import org.bitcoinj.coinjoin.PoolStatus;
import org.bitcoinj.coinjoin.listeners.CoinJoinTransactionListener;
import org.bitcoinj.coinjoin.listeners.MixingCompleteListener;
import org.bitcoinj.coinjoin.listeners.SessionCompleteListener;

import org.bitcoinj.coinjoin.listeners.SessionStartedListener;
import org.bitcoinj.coinjoin.utils.CoinJoinTransactionType;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletEx;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class MixingProgressTracker implements SessionStartedListener, SessionCompleteListener, MixingCompleteListener,
        NewBestBlockListener, CoinJoinTransactionListener {
    protected int completedSessions = 0;
    protected int timedOutSessions = 0;
    protected int timedOutConnections = 0;
    protected int timedOutQueues = 0;
    protected int timedOutWhileSigning = 0;
    protected int timedOutWhileAccepting = 0;
    private double lastPercent = 0;

    private final SettableFuture<PoolMessage> future = SettableFuture.create();

    public MixingProgressTracker() {

    }

    @Override
    public void onSessionComplete(WalletEx wallet, int sessionId, int denomination, PoolState state,
                                  PoolMessage message, MasternodeAddress address, boolean joined) {
        if (message == PoolMessage.MSG_SUCCESS) {
            completedSessions++;
            lastPercent = calculatePercentage(wallet);
        } else if (message == PoolMessage.ERR_CONNECTION_TIMEOUT) {
            timedOutConnections++;
        } else {
            switch (state) {
                case POOL_STATE_QUEUE:
                    ++timedOutQueues;
                    break;
                case POOL_STATE_SIGNING:
                    ++timedOutWhileSigning;
                    break;
                case POOL_STATE_ACCEPTING_ENTRIES:
                    ++timedOutWhileAccepting;
                    break;
            }
            timedOutSessions++;
        }
    }

    @Override
    public void onMixingComplete(WalletEx wallet, List<PoolStatus> statusList) {
        lastPercent = 100.0;
        if (statusList.contains(PoolStatus.FINISHED))
            future.set(PoolMessage.MSG_SUCCESS);
        else future.set(PoolMessage.ERR_SESSION);
    }

    private double calculatePercentage(WalletEx wallet) {
        return 100.0 * wallet.getBalance(Wallet.BalanceType.COINJOIN).value / wallet.getBalance(Wallet.BalanceType.DENOMINATED).value;
    }

    /**
     * Returns a listenable future that completes a pool message when mixing is completed.
     */
    public ListenableFuture<PoolMessage> getFuture() {
        return future;
    }

    public double getProgress() {
        return lastPercent;
    }

    /**
     * Wait for mixing to be complete.
     */
    public void await() throws InterruptedException {
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onSessionStarted(WalletEx wallet, int sessionId, int denomination, PoolMessage message) {

    }

    @Override
    public void notifyNewBestBlock(StoredBlock block) throws VerificationException {

    }

    @Override
    public void onTransactionProcessed(Transaction tx, CoinJoinTransactionType type, int sessionId) {

    }
}
