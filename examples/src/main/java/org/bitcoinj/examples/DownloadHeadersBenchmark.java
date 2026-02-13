/*
 * Copyright 2026 Dash Core Group
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

package org.bitcoinj.examples;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.manager.DashSystem;
import org.bitcoinj.net.discovery.MasternodeSeedPeers;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Wallet;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark program to compare header download times between regular headers (v1)
 * and compressed headers (v2) as defined in DIP-0025.
 *
 * Usage: DownloadHeadersBenchmark <network> <version> [--debuglog]
 *   network: mainnet or testnet
 *   version: 1 (regular headers) or 2 (compressed headers)
 *
 * Example:
 *   DownloadHeadersBenchmark testnet 1
 *   DownloadHeadersBenchmark mainnet 2
 */
public class DownloadHeadersBenchmark {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: DownloadHeadersBenchmark <network> <version> [--debuglog]");
            System.out.println("  network: mainnet or testnet");
            System.out.println("  version: 1 (regular headers) or 2 (compressed headers)");
            System.exit(1);
        }

        // Parse arguments
        String network = args[0];
        int headerVersion = Integer.parseInt(args[1]);

        if (headerVersion != 1 && headerVersion != 2) {
            System.out.println("Error: version must be 1 or 2");
            System.exit(1);
        }

        // Setup logging
        if (args.length >= 3 && args[2].equals("--debuglog")) {
            BriefLogFormatter.initVerbose();
        } else {
            BriefLogFormatter.initWithSilentBitcoinJ();
        }

        // Get network parameters
        NetworkParameters params;
        switch (network.toLowerCase()) {
            case "testnet":
                params = TestNet3Params.get();
                break;
            case "mainnet":
            default:
                params = MainNetParams.get();
                break;
        }

        boolean useCompressedHeaders = (headerVersion == 2);

        System.out.println("===========================================");
        System.out.println("Header Download Benchmark");
        System.out.println("===========================================");
        System.out.println("Network: " + network);
        System.out.println("Header version: " + headerVersion + " (" +
                (useCompressedHeaders ? "compressed" : "regular") + ")");
        System.out.println("Start time: " + new Date());
        System.out.println("===========================================");

        // Create a new wallet (we don't need to restore from seed)
        KeyChainGroup keyChainGroup = KeyChainGroup.createBasic(params);
        Wallet wallet = new Wallet(params, keyChainGroup);
        DashSystem vSystem = new DashSystem(wallet.getContext());
        vSystem.initDash(true, true);
        vSystem.masternodeSync.addSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST);
        vSystem.initDashSync(".", "benchmark");
        // Use in-memory block store for speed (we don't need persistence)
        MemoryBlockStore blockStore = new MemoryBlockStore(params);
        BlockChain chain = new BlockChain(params, blockStore);

        // Setup peer group
        PeerGroup peerGroup = new PeerGroup(params, chain);
        peerGroup.addPeerDiscovery(new MasternodeSeedPeers(params));

        // Enable or disable compressed headers
        peerGroup.setUseCompressedHeaders(useCompressedHeaders);

        // Add wallet to chain and peer group
        chain.addWallet(wallet);
        peerGroup.addWallet(wallet);

        // Track download progress
        final long[] headerCount = {0};
        final long[] headerDoneTime = {0};
        final long startTime = System.currentTimeMillis();

        DownloadProgressTracker progressTracker = new DownloadProgressTracker() {
            @Override
            protected void progress(double pct, int blocksSoFar, Date date) {
                headerCount[0] = blocksSoFar;
                if (blocksSoFar % 10000 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double headersPerSec = blocksSoFar / (elapsed / 1000.0);
                    System.out.printf("Progress: %.1f%% (%d headers, %.0f headers/sec)%n",
                            pct, blocksSoFar, headersPerSec);
                }
            }

            @Override
            public void doneHeaderDownload() {
                super.doneHeaderDownload();
                headerDoneTime[0] = System.currentTimeMillis();
                long totalTimeMs = headerDoneTime[0] - startTime;//endTime - startTime;

                // Calculate statistics
                printReport(chain, totalTimeMs, headerVersion, useCompressedHeaders);
            }

            @Override
            public void doneDownload() {
                System.out.println("Download complete!");
            }
        };

        // Start download and measure time
        System.out.println("\nStarting header download...\n");

        peerGroup.start();
        peerGroup.startBlockChainDownload(progressTracker);

        // Wait for download to complete
        progressTracker.await();

        long endTime = System.currentTimeMillis();
        long totalTimeMs = headerDoneTime[0] - startTime;//endTime - startTime;

        // Calculate statistics
        printReport(chain, totalTimeMs, headerVersion, useCompressedHeaders);

        // Shutdown
        peerGroup.stop();
    }

    private static void printReport(BlockChain chain, long totalTimeMs, int headerVersion, boolean useCompressedHeaders) {
        long totalHeaders = chain.getBestChainHeight();
        double totalTimeSec = totalTimeMs / 1000.0;
        double headersPerSec = totalHeaders / totalTimeSec;

        // Print results
        System.out.println("\n===========================================");
        System.out.println("Results");
        System.out.println("===========================================");
        System.out.println("Header version: " + headerVersion + " (" +
                (useCompressedHeaders ? "compressed" : "regular") + ")");
        System.out.println("Total headers: " + totalHeaders);
        System.out.println("Total time: " + formatDuration(totalTimeMs));
        System.out.printf("Headers per second: %.2f%n", headersPerSec);
        System.out.println("End time: " + new Date());
        System.out.println("===========================================");
    }

    private static String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long ms = millis % 1000;

        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        } else if (minutes > 0) {
            return String.format("%d:%02d.%03d", minutes, seconds, ms);
        } else {
            return String.format("%d.%03d seconds", seconds, ms);
        }
    }
}
