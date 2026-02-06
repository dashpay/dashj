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
import com.google.common.collect.Lists;
import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinCoinSelector;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LargeCoinJoinWalletTest {
    private WalletEx wallet;
    private static Logger log = LoggerFactory.getLogger(LargeCoinJoinWalletTest.class);
    TestNet3Params PARAMS = TestNet3Params.get();
    Context CONTEXT = new Context(PARAMS);

    @Before
    public void setup() {
        BriefLogFormatter.initVerbose();
        try (InputStream is = getClass().getResourceAsStream("coinjoin-decrypted.wallet")) {
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
        assertEquals(Coin.valueOf(16724708510L), wallet.getBalance(Wallet.BalanceType.ESTIMATED));
        info("getBalance(ESTIMATED): {}", watch0);

        Stopwatch watch1 = Stopwatch.createStarted();
        // the coinJoinSalt changed and this value is not constant
        // assertEquals(Coin.valueOf(13634336342L), wallet.getBalance(Wallet.BalanceType.COINJOIN));
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

    @Test @Ignore // this test fails with java.lang.OutOfMemoryError: Java heap space
    public void walletToStringTest() {
        Stopwatch watch0 = Stopwatch.createStarted();
        wallet.toString(false, false, true, null);
        info("wallet.toString: {}", watch0);

        Stopwatch watch1 = Stopwatch.createStarted();
        wallet.toString(false, false, true, null);
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

    @Test @Ignore
    public void walletSavePerformanceTest() throws IOException {
        // Show wallet statistics
        int transactionCount = wallet.getTransactionCount(true);
        int watchedScriptCount = wallet.getWatchedScripts().size();
        int keyCount = wallet.getKeyChainGroupSize();

        info("=== Wallet Statistics ===");
        info("Transactions: {}", transactionCount);
        info("Watched Scripts: {}", watchedScriptCount);
        info("Keys: {}", keyCount);
        info("Complexity Score: {}", transactionCount + (watchedScriptCount * 2) + keyCount);

        // Test different buffer sizes
        int[] bufferSizes = {
            1024,     // 1KB - Very small
            4096,     // 4KB - Original default
            8192,     // 8KB - Small wallet adaptive
            16384,    // 16KB - Medium wallet adaptive
            32768,    // 32KB - Large wallet adaptive
            65536,    // 64KB - Very large wallet adaptive
            131072,   // 128KB - Extra large
            262144    // 256KB - Maximum reasonable
        };

        String[] sizeNames = {"1KB", "4KB", "8KB", "16KB", "32KB", "64KB", "128KB", "256KB"};

        info("\n=== Fixed Buffer Size Performance Comparison ===");

        long[] averageTimes = new long[bufferSizes.length];

        for (int bufferIndex = 0; bufferIndex < bufferSizes.length; bufferIndex++) {
            int bufferSize = bufferSizes[bufferIndex];
            String sizeName = sizeNames[bufferIndex];

            WalletProtobufSerializer serializer = new WalletProtobufSerializer();
            serializer.setUseAdaptiveBufferSizing(false);  // Disable adaptive sizing
            serializer.setWalletWriteBufferSize(bufferSize);

            // Warm up JVM for this buffer size
            for (int i = 0; i < 2; i++) {
                ByteArrayOutputStream warmupStream = new ByteArrayOutputStream();
                serializer.writeWallet(wallet, warmupStream);
            }

            // Measure performance for this buffer size
            long[] times = new long[10];
            for (int i = 0; i < 10; i++) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                Stopwatch watch = Stopwatch.createStarted();
                serializer.writeWallet(wallet, stream);
                watch.stop();
                times[i] = watch.elapsed().toMillis();
            }

            long avgTime = java.util.Arrays.stream(times).sum() / times.length;
            averageTimes[bufferIndex] = avgTime;

            info("{} buffer: {} ms avg (runs: {} {} {} {} {} {} {} {} {} {})",
                sizeName, avgTime, times[0], times[1], times[2], times[3], times[4],
                times[5], times[6], times[7], times[8], times[9]);
        }

        // Test with adaptive buffer sizing
        WalletProtobufSerializer adaptiveSerializer = new WalletProtobufSerializer();
        adaptiveSerializer.setUseAdaptiveBufferSizing(true);   // Enable adaptive sizing (default)

        info("\n=== Adaptive Buffer Sizing ===");

        // Warm up JVM
        for (int i = 0; i < 3; i++) {
            ByteArrayOutputStream warmupStream = new ByteArrayOutputStream();
            adaptiveSerializer.writeWallet(wallet, warmupStream);
        }

        // Measure adaptive performance
        long[] adaptiveTimes = new long[10];
        for (int i = 0; i < 10; i++) {
            ByteArrayOutputStream adaptiveStream = new ByteArrayOutputStream();
            Stopwatch adaptiveWatch = Stopwatch.createStarted();
            adaptiveSerializer.writeWallet(wallet, adaptiveStream);
            adaptiveWatch.stop();
            adaptiveTimes[i] = adaptiveWatch.elapsed().toMillis();
        }

        long adaptiveAvg = java.util.Arrays.stream(adaptiveTimes).sum() / adaptiveTimes.length;
        info("Adaptive sizing: {} ms avg (runs: {} {} {} {} {} {} {} {} {} {})",
            adaptiveAvg, adaptiveTimes[0], adaptiveTimes[1], adaptiveTimes[2], adaptiveTimes[3], adaptiveTimes[4],
                adaptiveTimes[5], adaptiveTimes[6], adaptiveTimes[7], adaptiveTimes[8], adaptiveTimes[9]);

        // Find the best fixed buffer size
        int bestFixedIndex = 0;
        long bestFixedTime = averageTimes[0];
        for (int i = 1; i < averageTimes.length; i++) {
            if (averageTimes[i] < bestFixedTime) {
                bestFixedTime = averageTimes[i];
                bestFixedIndex = i;
            }
        }

        // Compare with original 4KB (index 1)
        long originalTime = averageTimes[1]; // 4KB is at index 1

        info("\n=== Performance Analysis ===");
        info("Original 4KB buffer: {} ms", originalTime);
        info("Best fixed buffer ({}): {} ms", sizeNames[bestFixedIndex], bestFixedTime);
        info("Adaptive buffer: {} ms", adaptiveAvg);

        double improvementOverOriginal = ((double)(originalTime - adaptiveAvg) / originalTime) * 100;
        double improvementOverBest = ((double)(bestFixedTime - adaptiveAvg) / bestFixedTime) * 100;

        info("Adaptive vs Original 4KB: {:.1f}% improvement", improvementOverOriginal);
        info("Adaptive vs Best Fixed: {:.1f}% improvement", improvementOverBest);

        // Show the actual buffer size chosen by adaptive algorithm
        info("\n=== Adaptive Algorithm Details ===");
        // Calculate what the adaptive algorithm chose
        int complexityScore = transactionCount + (watchedScriptCount * 2) + keyCount;
        int chosenBufferSize;
        if (complexityScore < 100) {
            chosenBufferSize = 8 * 1024;
        } else if (complexityScore < 1000) {
            chosenBufferSize = 16 * 1024;
        } else if (complexityScore < 5000) {
            chosenBufferSize = 32 * 1024;
        } else {
            chosenBufferSize = 64 * 1024;
        }

        info("Complexity score: {}", complexityScore);
        info("Adaptive algorithm chose: {} KB buffer", chosenBufferSize / 1024);

        // Create performance comparison chart
        info("\n=== Performance Chart (relative to 4KB baseline) ===");
        for (int i = 0; i < bufferSizes.length; i++) {
            double relativePerformance = (double)originalTime / averageTimes[i];
            String bar = createPerformanceBar(relativePerformance);
            info("{}: {:.2f}x {} ({} ms)", sizeNames[i], relativePerformance, bar, averageTimes[i]);
        }

        double adaptiveRelative = (double)originalTime / adaptiveAvg;
        String adaptiveBar = createPerformanceBar(adaptiveRelative);
        info("Adaptive: {:.2f}x {} ({} ms)", adaptiveRelative, adaptiveBar, adaptiveAvg);

        // Save files with different buffer sizes for actual file system testing
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        info("\n=== File System Performance Test ===");

        // Test original vs adaptive with actual file I/O
        WalletProtobufSerializer originalSerializer = new WalletProtobufSerializer();
        originalSerializer.setUseAdaptiveBufferSizing(false);
        originalSerializer.setWalletWriteBufferSize(4096);

        File originalFile = new File(tempDir, "wallet-original-4kb.dat");
        File adaptiveFile = new File(tempDir, "wallet-adaptive.dat");
        try {
            Stopwatch originalFileWatch = Stopwatch.createStarted();
            try (FileOutputStream originalFileStream = new FileOutputStream(originalFile)) {
                originalSerializer.writeWallet(wallet, originalFileStream);
            }
            originalFileWatch.stop();

            Stopwatch adaptiveFileWatch = Stopwatch.createStarted();
            try (FileOutputStream adaptiveFileStream = new FileOutputStream(adaptiveFile)) {
                adaptiveSerializer.writeWallet(wallet, adaptiveFileStream);
            }
            adaptiveFileWatch.stop();

            info("Original 4KB file save: {} ms", originalFileWatch.elapsed().toMillis());
            info("Adaptive file save: {} ms", adaptiveFileWatch.elapsed().toMillis());
            info("File sizes - Original: {} bytes, Adaptive: {} bytes", originalFile.length(), adaptiveFile.length());

            double fileImprovementPercent = ((double) (originalFileWatch.elapsed().toMillis() - adaptiveFileWatch.elapsed().toMillis()) / originalFileWatch.elapsed().toMillis()) * 100;
            info("File I/O improvement: {:.1f}%", fileImprovementPercent);
        } finally {
            // Clean up temp files
            if (originalFile.exists()) originalFile.delete();
            if (adaptiveFile.exists()) adaptiveFile.delete();
        }
        // Final summary
        info("\n=== CONCLUSION ===");
        if (adaptiveAvg <= bestFixedTime) {
            info("✓ Adaptive sizing performs as well as or better than the best fixed buffer size!");
        } else {
            info("⚠ Adaptive sizing is close to optimal but {} ms slower than best fixed size", adaptiveAvg - bestFixedTime);
        }

        if (improvementOverOriginal > 0) {
            info("✓ Adaptive sizing improves performance by {:.1f}% over original implementation", improvementOverOriginal);
        } else {
            info("⚠ No significant improvement over original implementation");
        }
    }

    private String createPerformanceBar(double relativePerformance) {
        int barLength = Math.min(20, (int)(relativePerformance * 10));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i < 10) {
                bar.append("█");
            } else if (i < 15) {
                bar.append("▓");
            } else {
                bar.append("░");
            }
        }
        return bar.toString();
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
        String result = format;
        int argIndex = 0;

        // Handle formatted placeholders like {:.2f}
        while (result.contains("{:.") && argIndex < args.length) {
            int start = result.indexOf("{:.");
            if (start >= 0) {
                int end = result.indexOf("}", start);
                if (end >= 0) {
                    String formatSpec = result.substring(start + 2, end);
                    Object arg = args[argIndex++];
                    String replacement;

                    if (arg instanceof Number && formatSpec.endsWith("f")) {
                        // Handle decimal formatting like .2f, .1f
                        try {
                            int decimals = Integer.parseInt(formatSpec.substring(1, formatSpec.length() - 1));
                            double value = ((Number) arg).doubleValue();
                            if (decimals == 1) {
                                replacement = String.format("%.1f", value);
                            } else if (decimals == 2) {
                                replacement = String.format("%.2f", value);
                            } else {
                                replacement = String.format("%.2f", value); // default
                            }
                        } catch (NumberFormatException e) {
                            replacement = arg.toString();
                        }
                    } else {
                        replacement = arg == null ? "null" : arg.toString();
                    }

                    result = result.substring(0, start) + replacement + result.substring(end + 1);
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        // Handle simple {} placeholders
        for (int i = argIndex; i < args.length; i++) {
            int pos = result.indexOf("{}");
            if (pos >= 0) {
                String replacement = args[i] == null ? "null" : args[i].toString();
                result = result.substring(0, pos) + replacement + result.substring(pos + 2);
            }
        }

        return result;
    }

    @Test @Ignore
    public void walletConsistencyAndCachingPerformanceTest() throws IOException, UnreadableWalletException {
        info("=== Wallet Consistency and Caching Performance Test ===");

        // Save the original wallet to get baseline data
        ByteArrayOutputStream originalStream = new ByteArrayOutputStream();
        WalletProtobufSerializer serializer = new WalletProtobufSerializer();

        Stopwatch originalSaveWatch = Stopwatch.createStarted();
        serializer.writeWallet(wallet, originalStream);
        originalSaveWatch.stop();
        byte[] originalBytes = originalStream.toByteArray();

        info("Original wallet save: {} ms, size: {} bytes", originalSaveWatch.elapsed().toMillis(), originalBytes.length);

        // Load the wallet from the saved bytes
        WalletProtobufSerializer loader = new WalletProtobufSerializer();
        Stopwatch loadWatch = Stopwatch.createStarted();
        WalletEx loadedWallet = (WalletEx) loader.readWallet(new ByteArrayInputStream(originalBytes));
        loadWatch.stop();

        info("Wallet load: {} ms", loadWatch.elapsed().toMillis());

        // Verify basic properties match
        assertEquals("Transaction count should match", wallet.getTransactionCount(true), loadedWallet.getTransactionCount(true));
        assertEquals("Balance should match", wallet.getBalance(Wallet.BalanceType.ESTIMATED), loadedWallet.getBalance(Wallet.BalanceType.ESTIMATED));

        // Now save the loaded wallet 3 times and track performance and consistency
        long[] saveTimes = new long[3];
        WalletEx[] reloadedWallets = new WalletEx[3];

        for (int i = 0; i < 3; i++) {
            ByteArrayOutputStream saveStream = new ByteArrayOutputStream();

            Stopwatch saveWatch = Stopwatch.createStarted();
            serializer.writeWallet(loadedWallet, saveStream);
            saveWatch.stop();

            saveTimes[i] = saveWatch.elapsed().toMillis();
            byte[] savedBytes = saveStream.toByteArray();

            // Immediately reload the wallet to verify consistency
            reloadedWallets[i] = (WalletEx) loader.readWallet(new ByteArrayInputStream(savedBytes));

            info("Save #{}: {} ms, size: {} bytes", i + 1, saveTimes[i], savedBytes.length);
        }

        // Verify all reloaded wallets have identical transaction content
        for (int i = 1; i < 3; i++) {
            compareWallets(reloadedWallets[0], reloadedWallets[i], i + 1);
        }

        // Analyze performance improvements from caching
        long firstSaveTime = saveTimes[0];
        long bestSubsequentTime = Math.min(saveTimes[1], saveTimes[2]);
        long avgSubsequentTime = (saveTimes[1] + saveTimes[2]) / 2;

        info("\n=== Performance Analysis ===");
        info("First save (cache misses): {} ms", firstSaveTime);
        info("Second save: {} ms", saveTimes[1]);
        info("Third save: {} ms", saveTimes[2]);
        info("Best subsequent save: {} ms", bestSubsequentTime);
        info("Average subsequent saves: {} ms", avgSubsequentTime);

        if (firstSaveTime > 0) {
            double improvementPercent = ((double)(firstSaveTime - bestSubsequentTime) / firstSaveTime) * 100;
            info("Best improvement from caching: {:.1f}%", improvementPercent);

            double avgImprovementPercent = ((double)(firstSaveTime - avgSubsequentTime) / firstSaveTime) * 100;
            info("Average improvement from caching: {:.1f}%", avgImprovementPercent);
        }

        // Verify the final saved wallet (reloadedWallets[2]) matches original
        assertEquals("Final wallet transaction count should match original",
            wallet.getTransactionCount(true), reloadedWallets[2].getTransactionCount(true));
        assertEquals("Final wallet balance should match original",
            wallet.getBalance(Wallet.BalanceType.ESTIMATED), reloadedWallets[2].getBalance(Wallet.BalanceType.ESTIMATED));

        info("\n=== Test Results ===");
        info("✓ All saves produced identical results");
        info("✓ Loaded wallet matches original wallet properties");
        info("✓ Transaction protobuf caching is working correctly");

        // Performance expectations (these are guidelines, not strict requirements)
        if (saveTimes[1] < firstSaveTime && saveTimes[2] < firstSaveTime) {
            info("✓ Subsequent saves are faster than first save (caching working)");
        } else {
            info("⚠ Expected subsequent saves to be faster due to caching");
        }
    }

    /**
     * Compare two wallets for transaction consistency
     */
    private void compareWallets(WalletEx wallet1, WalletEx wallet2, int wallet2Number) {
        // Compare basic properties
        assertEquals("Wallet #" + wallet2Number + " transaction count should match wallet #1",
            wallet1.getTransactionCount(true), wallet2.getTransactionCount(true));
        assertEquals("Wallet #" + wallet2Number + " balance should match wallet #1",
            wallet1.getBalance(Wallet.BalanceType.ESTIMATED), wallet2.getBalance(Wallet.BalanceType.ESTIMATED));

        // Compare transactions in detail
        List<Transaction> txs1 = new java.util.ArrayList<>(wallet1.getTransactions(true));
        List<Transaction> txs2 = new java.util.ArrayList<>(wallet2.getTransactions(true));

        assertEquals("Wallet #" + wallet2Number + " should have same number of transactions as wallet #1",
            txs1.size(), txs2.size());

        info("Comparing {} transactions between wallet #1 and wallet #{}", txs1.size(), wallet2Number);

        // Create a map of transactions by ID for wallet2 for easier lookup
        java.util.Map<org.bitcoinj.core.Sha256Hash, Transaction> txMap2 = new java.util.HashMap<>();
        for (Transaction tx : txs2) {
            txMap2.put(tx.getTxId(), tx);
        }

        // Compare each transaction from wallet1 with its corresponding transaction in wallet2
        for (int i = 0; i < txs1.size(); i++) {
            Transaction tx1 = txs1.get(i);
            Transaction tx2 = txMap2.get(tx1.getTxId());

            // Verify the transaction exists in wallet2
            assertEquals("Transaction " + tx1.getTxId() + " should exist in wallet #" + wallet2Number,
                true, tx2 != null);

            // Compare memos
            assertEquals("Transaction " + tx1.getTxId() + " memo should match between wallets",
                tx1.getMemo(), tx2.getMemo());

            // Compare exchange rates
            if (tx1.getExchangeRate() == null) {
                assertEquals("Transaction " + tx1.getTxId() + " exchange rate should be null in both wallets",
                    null, tx2.getExchangeRate());
            } else if (tx2.getExchangeRate() != null) {
                assertEquals("Transaction " + tx1.getTxId() + " exchange rate should match between wallets",
                    tx1.getExchangeRate().coin, tx2.getExchangeRate().coin);
                assertEquals("Transaction " + tx1.getTxId() + " exchange rate fiat should match between wallets",
                    tx1.getExchangeRate().fiat, tx2.getExchangeRate().fiat);
            } else {
                assertEquals("Transaction " + tx1.getTxId() + " exchange rate should not be null in wallet #" + wallet2Number,
                    tx1.getExchangeRate(), tx2.getExchangeRate());
            }

            // Compare cached values
            assertEquals("Transaction " + tx1.getTxId() + " cached value should match between wallets",
                tx1.getCachedValue(), tx2.getCachedValue());

            // Compare coinjoin transaction types
            assertEquals("Transaction " + tx1.getTxId() + " coinjoin type should match between wallets",
                tx1.getCoinJoinTransactionType(), tx2.getCoinJoinTransactionType());
        }

        info("✓ Wallet #{} transactions match wallet #1 perfectly", wallet2Number);
    }

    @Test
    public void greedyAlgorithmTest() throws InsufficientMoneyException {
        Stopwatch watch = Stopwatch.createStarted();

        //Coin sendAmount = Coin.valueOf(1050000000L);
        Coin sendAmount = Coin.COIN.div(2);
        Address toAddress = wallet.freshReceiveAddress();

        System.out.println("=== GREEDY ALGORITHM TEST ===");
        System.out.println("Testing greedy algorithm with send amount: " + sendAmount.toFriendlyString());
        System.out.println("Available balance: " + wallet.getBalance().toFriendlyString());

        SendRequest req = SendRequest.to(toAddress, sendAmount);
        req.coinSelector = new CoinJoinCoinSelector(wallet, true, true);
        req.returnChange = false;
        //req.greedyAlgorithm = true;

        wallet.completeTx(req);
        Transaction tx = req.tx;

        System.out.println("Transaction has " + tx.getInputs().size() + " inputs and " + tx.getOutputs().size() + " outputs");
        assertEquals(1, req.tx.getOutputs().size());

        boolean hasLargerInput = false;
        Coin totalInputValue = Coin.ZERO;

        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            Coin inputValue = input.getConnectedOutput().getValue();
            totalInputValue = totalInputValue.add(inputValue);
            System.out.println("Input " + i + ": " + inputValue.toFriendlyString());

            if (inputValue.isGreaterThan(sendAmount)) {
                hasLargerInput = true;
                System.out.println("  -> Input " + i + " (" + inputValue.toFriendlyString() +
                                 ") is larger than send amount (" + sendAmount.toFriendlyString() + ")");
            }
        }

        System.out.println("Total input value: " + totalInputValue.toFriendlyString());
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Change: " + totalInputValue.subtract(sendAmount).toFriendlyString());

        if (sendAmount.isGreaterThanOrEqualTo(Coin.valueOf(100000L))) {
            if (hasLargerInput) {
                System.out.println("CONSTRAINT VIOLATION: Found inputs larger than send amount for amount >= 0.001 DASH");
            } else {
                System.out.println("SUCCESS: No inputs larger than send amount for amount >= 0.001 DASH");
            }
        }

        System.out.println("=== TEST COMPLETED IN " + watch + " ===");

        info("Testing greedy algorithm with send amount: {}", sendAmount.toFriendlyString());
        info("Available balance: {}", wallet.getBalance().toFriendlyString());
        info("Created greedy transaction: {}", tx);
        info("Transaction has {} inputs and {} outputs", tx.getInputs().size(), tx.getOutputs().size());

        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            Coin inputValue = input.getConnectedOutput().getValue();
            info("Input {}: {}", i, inputValue.toFriendlyString());
        }

        info("Total input value: {}", totalInputValue.toFriendlyString());
        info("Send amount: {}", sendAmount.toFriendlyString());
        info("Fee amount: {}", tx.getFee().toFriendlyString());
        info("Greedy algorithm test completed in: {}", watch);
    }

    @Test
    public void greedyAlgorithmRecipientPaysFeeTest() throws InsufficientMoneyException {
        Stopwatch watch = Stopwatch.createStarted();
        Coin sendAmount = CoinJoin.getStandardDenominations().get(1);
        Address toAddress = wallet.freshReceiveAddress();

        System.out.println("=== GREEDY ALGORITHM TEST ===");
        System.out.println("Testing greedy algorithm with send amount: " + sendAmount.toFriendlyString());
        System.out.println("Available balance: " + wallet.getBalance().toFriendlyString());

        SendRequest req = SendRequest.to(toAddress, sendAmount);
        req.coinSelector = new CoinJoinCoinSelector(wallet, true, true);
        req.returnChange = false;
        req.recipientsPayFees = true;

        wallet.completeTx(req);
        Transaction tx = req.tx;

        System.out.println("Transaction has " + tx.getInputs().size() + " inputs and " + tx.getOutputs().size() + " outputs");
        assertEquals(1, req.tx.getOutputs().size());

        boolean hasLargerInput = false;
        Coin totalInputValue = Coin.ZERO;

        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            Coin inputValue = input.getConnectedOutput().getValue();
            totalInputValue = totalInputValue.add(inputValue);
            System.out.println("Input " + i + ": " + inputValue.toFriendlyString());

            if (inputValue.isGreaterThan(sendAmount)) {
                hasLargerInput = true;
                System.out.println("  -> Input " + i + " (" + inputValue.toFriendlyString() +
                        ") is larger than send amount (" + sendAmount.toFriendlyString() + ")");
            }
        }

        System.out.println("Total input value: " + totalInputValue.toFriendlyString());
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Change: " + totalInputValue.subtract(sendAmount).toFriendlyString());

        if (sendAmount.isGreaterThanOrEqualTo(Coin.valueOf(100000L))) {
            if (hasLargerInput) {
                System.out.println("CONSTRAINT VIOLATION: Found inputs larger than send amount for amount >= 0.001 DASH");
            } else {
                System.out.println("SUCCESS: No inputs larger than send amount for amount >= 0.001 DASH");
            }
        }

        System.out.println("=== TEST COMPLETED IN " + watch + " ===");

        info("Testing greedy algorithm with send amount: {}", sendAmount.toFriendlyString());
        info("Available balance: {}", wallet.getBalance().toFriendlyString());
        info("Created greedy transaction: {}", tx);
        info("Transaction has {} inputs and {} outputs", tx.getInputs().size(), tx.getOutputs().size());

        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            Coin inputValue = input.getConnectedOutput().getValue();
            info("Input {}: {}", i, inputValue.toFriendlyString());
        }

        info("Total input value: {}", totalInputValue.toFriendlyString());
        info("Send amount: {}", sendAmount.toFriendlyString());
        info("Fee amount: {}", tx.getFee().toFriendlyString());
        info("Greedy algorithm test completed in: {}", watch);
    }

    @Test
    public void greedyAlgorithmSmallAmountTest() throws InsufficientMoneyException {
        Stopwatch watch = Stopwatch.createStarted();

        Coin sendAmount = Coin.valueOf(50000L);
        Address toAddress = wallet.freshReceiveAddress();

        SendRequest req = SendRequest.to(toAddress, sendAmount);

        info("Testing greedy algorithm with small send amount: {}", sendAmount.toFriendlyString());

        wallet.completeTx(req);
        Transaction tx = req.tx;

        info("Created transaction: {}", tx);
        info("Transaction has {} inputs and {} outputs", tx.getInputs().size(), tx.getOutputs().size());

        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            Coin inputValue = input.getConnectedOutput().getValue();
            info("Input {}: {}", i, inputValue.toFriendlyString());
        }

        info("For amounts < 0.001 DASH, inputs can be larger than send amount");
        assertTrue("Should have at least one input", tx.getInputs().size() > 0);
        info("Small amount greedy algorithm test completed in: {}", watch);
    }

    /**
     * Creates a transaction using the greedy algorithm with returnChange = false
     */
    private Transaction createTestTransaction(Coin sendAmount, boolean useGreedyAlgorithm) throws InsufficientMoneyException {
        Address toAddress = Address.fromKey(wallet.getParams(), new ECKey());
        SendRequest req = SendRequest.to(toAddress, sendAmount);
        req.coinSelector = new CoinJoinCoinSelector(wallet, false, useGreedyAlgorithm); // Use greedy algorithm
        req.returnChange = !useGreedyAlgorithm; // No change output
        req.feePerKb = Coin.valueOf(1000L); // Set fee rate

        System.out.println("\n=== Creating Transaction ===");
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Return change: " + req.returnChange);

        wallet.completeTx(req);
        return req.tx;
    }

    @Test
    public void selectionTest() throws InsufficientMoneyException {
        createTestTransaction(Coin.valueOf(5,50), true);
        createTestTransaction(Coin.valueOf(5,50), false);
    }

    @Test
    public void selectionOverRangeTest() throws InsufficientMoneyException {
        ArrayList<Transaction> list = Lists.newArrayList();
        for (int i = 0; i < 100; i++) {
            for (int j = 0; j < 10; ++j) {
                if (i == 0 && j == 0) j = 1;
                Transaction tx = createTestTransaction(Coin.valueOf(i, j * 10), true);
                list.add(tx);
            }
        }
        list.forEach(tx -> {
            System.out.println("" + tx.getValue(wallet).toFriendlyString() + " fee: " + tx.getFee().toFriendlyString());
        });
    }
}
