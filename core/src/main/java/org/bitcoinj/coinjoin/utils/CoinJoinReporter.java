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
package org.bitcoinj.coinjoin.utils;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.PoolMessage;
import org.bitcoinj.coinjoin.PoolStatus;
import org.bitcoinj.coinjoin.progress.MixingProgressTracker;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletEx;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CoinJoinReporter extends MixingProgressTracker {

    FileWriter fileWriter;
    BufferedWriter writer;

    HashMap<Integer, Stopwatch> sessionMap = Maps.newHashMap();

    static DateFormat format = DateFormat.getDateTimeInstance();

    protected void writeTime() throws IOException {
        writer.write(format.format(new Date(Utils.currentTimeMillis())));
        writer.write(" ");
    }

    protected void writeBalance(WalletEx wallet) throws IOException {
        String description = wallet.getDescription();
        writer.write(
                String.format(" Wallet: %s\n  total balance: %s\n  denominated:   %s\n  coinjoin:      %s",
                        description != null ? description : "no description",
                        wallet.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE).toFriendlyString(),
                        wallet.getBalance(Wallet.BalanceType.DENOMINATED_SPENDABLE).toFriendlyString(),
                        wallet.getBalance(Wallet.BalanceType.COINJOIN_SPENDABLE).toFriendlyString()
                )
        );
    }

    public CoinJoinReporter() {
        try {
            File reportFile = new File("./coinjoin-report.txt");
            fileWriter = new FileWriter(reportFile);
            writer = new BufferedWriter(fileWriter);
            writer.write("CoinJoin Report:");
            writer.newLine();
            writeTime();
            writer.write("starting...");
            writer.newLine();
            writer.newLine();
            writer.flush();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    public void close() {
        try {
            writer.flush();
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public void onSessionStarted(WalletEx wallet, int sessionId, int denomination, PoolMessage message) {
        super.onSessionStarted(wallet, sessionId, denomination, message);

        sessionMap.put(sessionId, Stopwatch.createStarted());
    }

    @Override
    public void onSessionComplete(WalletEx wallet, int sessionId, int denomination, PoolMessage message) {
        super.onSessionComplete(wallet, sessionId, denomination, message);
        try {
            writeTime();
            Stopwatch watch = sessionMap.get(sessionId);
            if (watch != null)
                watch.stop();
            if (message == PoolMessage.MSG_SUCCESS) {
                writer.write("Session Complete: ");
                writer.write(String.format("id: %d, denom: %s[%d]", sessionId, CoinJoin.denominationToAmount(denomination), denomination));
                writeWatch(watch);
                writer.newLine();
                writeStats(wallet);
            } else {
                writer.write("Session Failure: ");
                writer.write(String.format("id: %d, denom: %s[%d]", sessionId, CoinJoin.denominationToAmount(denomination), denomination));
                writeWatch(watch);
                writer.write(CoinJoin.getMessageByID(message));
            }
            writer.newLine();
            writer.flush();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public void onMixingComplete(WalletEx wallet, List<PoolStatus> statusList) {
        super.onMixingComplete(wallet, statusList);
        try {
            writeTime();
            writer.write("Mixing Complete: ");
            writer.newLine();
            writer.write("  status: " + Utils.listToString(statusList));
            writer.newLine();
            writer.write(String.format("  timeouts: %d, completed: %d", timedOutSessions, completedSessions));
            writer.newLine();
            writeBalance(wallet);
            writer.newLine();
            writeStats(wallet);
            writer.flush();
            writer.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    private void writeStats(WalletEx wallet) throws IOException {
        double percentComplete = 100.0 * wallet.getBalance(Wallet.BalanceType.COINJOIN_SPENDABLE).value / wallet.getBalance(Wallet.BalanceType.DENOMINATED_SPENDABLE).value;
        writer.write(String.format("  Session Stats: %d sessions, %.02f%%", completedSessions, percentComplete));
    }

    private void writeWatch(@Nullable Stopwatch watch) throws IOException {
        writer.write(String.format(" - %s ", watch != null ? watch : "N/A"));
    }
}
