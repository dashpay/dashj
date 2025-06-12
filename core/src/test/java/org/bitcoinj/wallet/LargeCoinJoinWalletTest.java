/*
 * Copyright 2025 Dash Core Group
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

package org.bitcoinj.wallet;

import com.google.common.base.Stopwatch;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class LargeCoinJoinWalletTest {
    private WalletEx wallet;
    private static Logger log = LoggerFactory.getLogger(LargeCoinJoinWalletTest.class);
    TestNet3Params PARAMS = TestNet3Params.get();
    Context CONTEXT = new Context(PARAMS);

    @Before
    public void setup() {
        BriefLogFormatter.initWithSilentBitcoinJ();
        try (InputStream is = getClass().getResourceAsStream("coinjoin-large.wallet")) {
            Stopwatch watch = Stopwatch.createStarted();
            wallet = (WalletEx) new WalletProtobufSerializer().readWallet(is);
            info("loading wallet: {}; {} transactions", watch, wallet.getTransactionCount(true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (UnreadableWalletException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void coinJoinInfoTest() {
        info("rounds: {}", wallet.coinjoin.rounds);

        Stopwatch watch0 = Stopwatch.createStarted();
        info("key usage: {}; time: {}", wallet.coinjoin.getKeyUsage(), watch0);
        Stopwatch watch1 = Stopwatch.createStarted();
        wallet.coinjoin.refreshUnusedKeys();
        info("unused key count: {}; time: {}", wallet.coinjoin.getUnusedKeyCount(), watch1);
    }

    @Test
    public void balanceAndMixingProgressTest() {
        Stopwatch watch0 = Stopwatch.createStarted();
        assertEquals(Coin.valueOf(13662399906L), wallet.getBalance(Wallet.BalanceType.ESTIMATED));
        info("getBalance(ESTIMATED): {}", watch0);

        Stopwatch watch1 = Stopwatch.createStarted();
        assertEquals(Coin.valueOf(13634336342L), wallet.getBalance(Wallet.BalanceType.COINJOIN));
        info("getBalance(COINJOIN): {}", watch1);

        Stopwatch watch2 = Stopwatch.createStarted();
        assertEquals(1.00, wallet.getCoinJoin().getMixingProgress(), 0.001);
        info("getMixingProgress: {}", watch2);
    }

    @Test
    public void transactionReportTest() {
        Stopwatch watch0 = Stopwatch.createStarted();
        wallet.getTransactionReport();
        info("getTransactionReport: {}", watch0);

        Stopwatch watch1 = Stopwatch.createStarted();
        wallet.getTransactionReport();
        info("getTransactionReport: {}", watch1);
    }

    @Test
    public void walletToStringTest() {
        Stopwatch watch0 = Stopwatch.createStarted();
        wallet.toString(true, false, true, null);
        info("wallet.toString: {}", watch0);

        Stopwatch watch1 = Stopwatch.createStarted();
        wallet.toString(true, false, true, null);
        info("wallet.toString: {}", watch1);

        Stopwatch watch2 = Stopwatch.createStarted();
        wallet.coinjoin.toString();
        info("wallet.coinjoin.toString: {}", watch2);

        Stopwatch watch3 = Stopwatch.createStarted();
        wallet.coinjoin.toString(true, false, null, true);
        info("wallet.coinjoin.toString(true, false, null, true): {}", watch3);

        Stopwatch watch4 = Stopwatch.createStarted();
        wallet.coinjoin.toString(true, false, null, false);
        info("wallet.coinjoin.toString(true, false, null, false): {}", watch4);
    }

    @Test
    public void getTransactionsTest() {
        Stopwatch watch0 = Stopwatch.createStarted();
        wallet.getTransactionCount(true);
        info("wallet.getTransactionCount: {}", watch0);

        Stopwatch watch1 = Stopwatch.createStarted();
        wallet.getTransactions(true);
        info("wallet.getTransactions: {}", watch1);

        Stopwatch watch2 = Stopwatch.createStarted();
        wallet.getTransactionList(true);
        info("wallet.getTransactionList: {}", watch2);

        Stopwatch watch3 = Stopwatch.createStarted();
        wallet.getWalletTransactions();
        info("wallet.getWalletTransactions(): {}", watch3);
    }

    public static void info(String format, Object... args) {
        log("INFO", format, args);
    }

    public static void error(String format, Object... args) {
        log("ERROR", format, args);
    }

    private static void log(String level, String format, Object... args) {
        String timestamp = java.time.LocalDateTime.now().toString();
        String message = formatMessage(format, args);
        System.out.printf("%s [%s] %s%n", timestamp, level, message);
    }

    private static String formatMessage(String format, Object... args) {
        for (Object arg : args) {
            format = format.replaceFirst("\\{\\}", arg == null ? "null" : arg.toString());
        }
        return format;
    }
}
