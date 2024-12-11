/*
 * Copyright 2023 Dash Core Group
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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinBroadcastTx;
import org.bitcoinj.coinjoin.CoinJoinQueue;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PreMessageReceivedEventListener;
import org.bitcoinj.examples.debug.CoinJoinReport;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.ThreeMethodPeerDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

/**
 * Downloads the given transaction and its dependencies from a peers memory pool then prints them out.
 */
public class CoinJoinMonitor {

    private static WalletAppKit kit;

    private static CoinJoinReport report;

    static int txCount = 0;
    static int dsqCount = 0;
    private static final HashMap<Sha256Hash, CoinJoinBroadcastTx> mapDSTX = new HashMap<>();
    private static final ArrayList<CoinJoinBroadcastTx> listDSTX = Lists.newArrayList();
    private static final HashSet<CoinJoinQueue> queueSet = new HashSet<>();
    private static final HashMap<Sha256Hash, Block> mapBlocks = new HashMap<>();
    private static final long startTime = Utils.currentTimeSeconds();

    static class SessionInfo {
        CoinJoinQueue dsq;
        Stopwatch watch;

        CoinJoinBroadcastTx dstx;

        private SessionInfo(CoinJoinQueue dsq) {
            this.dsq = dsq;
            this.watch = Stopwatch.createStarted();
        }

        static SessionInfo start(CoinJoinQueue dsq) {
            return new SessionInfo(dsq);
        }

        public void stop(CoinJoinBroadcastTx dstx) {
            watch.stop();
            this.dstx = dstx;
        }
    }

    private static final HashMap<Sha256Hash, SessionInfo> pendingSessions = new HashMap<>();
    private static final ArrayList<SessionInfo> completedSessions = Lists.newArrayList();

    static class DenomInfo {
        int count = 0;
        Coin total = Coin.ZERO;
        ArrayList<Integer> outputs = new ArrayList<>();

        double getUsage() {
            int totalOutputs = 0;
            for (int i = 0; i < outputs.size(); ++i) {
                totalOutputs += outputs.get(i);
            }
            return 100.0 * totalOutputs / (outputs.size() * 10);
        }

        double getRate(double timeIntervalHours) {
            return ((double) total.value / timeIntervalHours) / 100_000_000;
        }
    }

    private static void calculateDSTX(long timeInterval) {
        double hours = (double) timeInterval / 3600;
        TreeMap<Coin, DenomInfo> denoms = new TreeMap<>();
        for (Coin denom : CoinJoin.getStandardDenominations()) {
            denoms.put(denom, new DenomInfo());
        }
        for (CoinJoinBroadcastTx dstx : mapDSTX.values()) {
            Coin amount = dstx.getTx().getOutput(0).getValue();
            DenomInfo pair = denoms.get(amount);
            pair.count = pair.count + 1;
            pair.total = pair.total.add(amount.multiply(dstx.getTx().getOutputs().size()));
            pair.outputs.add(dstx.getTx().getOutputs().size());
        }

        System.out.println("Denominations Mixed ----------------------------");
        for (Map.Entry<Coin, DenomInfo> entry : denoms.entrySet()) {
            System.out.printf("%10s: %d / %s; %.1f %.8f DASH/hr\n", entry.getKey().toFriendlyString(), entry.getValue().count,
                    entry.getValue().total.toFriendlyString(), entry.getValue().getUsage(),
                    entry.getValue().getRate(hours));
        }

        System.out.println("Network Utilization ----------------------------");
        System.out.println("Masternodes: " + kit.system().masternodeListManager.getMasternodeList().countEnabled());
        System.out.printf("Hours: %.1f\n", hours);
        System.out.printf("Sessions/hour: %.1f\n", (double) completedSessions.size() / hours);


        //System.out.println("Session Times");
        //for (SessionInfo sessionInfo : completedSessions) {
        //    System.out.println(CoinJoin.denominationToAmount(sessionInfo.dsq.getDenomination()).toFriendlyString() +
        //            ":" + sessionInfo.watch.elapsed(TimeUnit.SECONDS) + "s " + sessionInfo.dstx.getTx().getInputs().size() + "/10");
        //}
    }

    public static void main(String[] args) throws Exception {
        BriefLogFormatter.initWithSilentBitcoinJ();
        System.out.println("CoinJoinMonitor:");
        final NetworkParameters params;
        String filePrefix;
        String checkpoints = null;

        switch (args[0]) {
            case "testnet":
                params = TestNet3Params.get();
                filePrefix = "coinjoin-monitor-testnet";
                checkpoints = "checkpoints-testnet.txt";
                break;
            default:
                params = MainNetParams.get();
                filePrefix = "coinjoin-monitor-mainnet";
                checkpoints = "checkpoints.txt";
                break;
        }
        System.out.println("Network: " + params.getNetworkName());
        System.out.println("File Prefix: " + filePrefix);

        report = new CoinJoinReport("", "", params);
        kit = new WalletAppKit(params, new File("."), filePrefix) {

            @Override
            protected PeerGroup createPeerGroup() throws TimeoutException {
                PeerGroup peerGroup = super.createPeerGroup();
                peerGroup.setBloomFilteringEnabled(false);
                peerGroup.setMaxConnections(6); // for small devnets
                peerGroup.setUseLocalhostPeerWhenPossible(false);
                peerGroup.setDropPeersAfterBroadcast(params.getDropPeersAfterBroadcast());
                return peerGroup;
            }

            @Override
            protected void onSetupCompleted() {
                //TODO: init auth keychains using AuthenticationGroupExtension
                kit.wallet().getContext().setDebugMode(false);
                kit.peerGroup().shouldSendDsq(true);
            }
        };
        kit.setDiscovery(new ThreeMethodPeerDiscovery(params, kit.system().masternodeListManager));

        if(checkpoints != null) {
            try {
                FileInputStream checkpointStream = new FileInputStream("./" + checkpoints);
                kit.setCheckpoints(checkpointStream);
            } catch (FileNotFoundException x) {
                //swallow
            }
        }

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();

        kit.chain().addNewBestBlockListener(new NewBestBlockListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                System.out.println("New Block: " + block.getHeight());

                long currentTime = Utils.currentTimeSeconds();
                long timeInterval = currentTime - startTime;
                System.out.println("Time Elapsed: "  + ((double)timeInterval / 3600) + " hrs");
                System.out.println("dsqCount:          " + queueSet.size());
                System.out.println("txCount:           " + mapDSTX.size());
                System.out.printf("Completed Queues:    %.1f%%%n",(double) mapDSTX.size() * 100  / queueSet.size());

                calculateDSTX(timeInterval);

                report.add(block, listDSTX);
                report.printReport();
                listDSTX.clear();
            }
        });
        kit.peerGroup().addPreMessageReceivedEventListener(Threading.SAME_THREAD, new PreMessageReceivedEventListener() {
            @Override
            public Message onPreMessageReceived(Peer peer, Message m) {
                if (m instanceof CoinJoinQueue) {
                    dsqCount++;
                    CoinJoinQueue dsq = (CoinJoinQueue) m;
                    if (queueSet.add(dsq)) {
                        writeTime();
                        System.out.println("dsq:  " + m);
                        // add to the pending sessions
                        pendingSessions.put(dsq.getProTxHash(), SessionInfo.start(dsq));
                    }
                } else if (m instanceof CoinJoinBroadcastTx) {
                    writeTime();
                    System.out.println("dstx: " + m);
                    CoinJoinBroadcastTx dstx = (CoinJoinBroadcastTx) m;
                    if (mapDSTX.put(dstx.getTx().getTxId(), dstx) == null) {
                        // finish the session
                        SessionInfo sessionInfo = pendingSessions.get(dstx.getProTxHash());
                        sessionInfo.stop(dstx);
                        completedSessions.add(sessionInfo);
                        pendingSessions.remove(dstx.getProTxHash());
                        listDSTX.add(dstx);
                    }
                    txCount++;
                }
                return m;
            }
        });

        System.out.println("Monitoring CoinJoin activity. Press Ctrl-C to quit.");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}

    }
    static DateFormat format = DateFormat.getDateTimeInstance();

    protected static void writeTime() {
        System.out.print(format.format(new Date(Utils.currentTimeMillis())));
        System.out.print(" ");
    }
}
