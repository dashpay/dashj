/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.examples.debug.BlockReport;
import org.bitcoinj.examples.debug.TransactionReport;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.ThreeMethodPeerDiscovery;
import org.bitcoinj.params.*;
import org.bitcoinj.quorums.listeners.ChainLockListener;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletEx;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * CoinJoinForwardingService demonstrates usage of the library with the CoinJoinExtension.
 * It sits on the network and when it receives coins, simply sends them onwards to an address
 * given on the command line. The wallet maintains 30,000 CoinJoin addresses.
 */
public class CoinJoinForwardingService {
    private static final int COINJOIN_KEY_COUNT = 3000;

    private static Address forwardingAddress;
    private static WalletAppKit kit;

    private static TransactionReport txReport;
    private static BlockReport blockReport;

    public static void main(String[] args) throws Exception {
        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        BriefLogFormatter.init();
        if (args.length < 1) {
            System.err.println("Usage: address-to-send-back-to [regtest|testnet|333|devnet] [devnet-name] [devnet-sporkaddress] [devnet-port] [devnet-dnsseed...]");
            return;
        }

        // Figure out which network we should connect to. Each one gets its own set of files.
        NetworkParameters params;
        String filePrefix;
        String checkpoints = null;
        int lastArg = 2;
        if (args.length > 1 && args[1].equals("testnet")) {
            params = TestNet3Params.get();
            filePrefix = "coinjoin-forwarding-service-testnet";
            checkpoints = "checkpoints-testnet.txt";
        } else if (args.length > 1 && args[1].equals("regtest")) {
            params = RegTestParams.get();
            filePrefix = "coinjoin-forwarding-service-regtest";
        } else if (args.length > 1 && args[1].equals("white-russian")) {
            params = WhiteRussianDevNetParams.get();
            filePrefix = "coinjoin-forwarding-service-white-russian";
        } else if (args.length > 1 && args[1].equals("ouzo")) {
            params = OuzoDevNetParams.get();
            filePrefix = "coinjoin-forwarding-service-ouzo";
        } else if (args.length > 6 && args[1].equals("devnet")) {
            String[] dnsSeeds = new String[args.length - 5];
            System.arraycopy(args, 5, dnsSeeds, 0, args.length - 5);
            params = DevNetParams.get(args[2], args[3], Integer.parseInt(args[4]), dnsSeeds);
            filePrefix = "coinjoin-forwarding-service-devnet";
        } else {
            lastArg = 1;
            params = MainNetParams.get();
            filePrefix = "coinjoin-forwarding-service";
            checkpoints = "checkpoints.txt";
        }

        String clientPath = "";
        String confPath = "";
        if (lastArg + 1 < args.length) {
            clientPath = args[lastArg];
            if (lastArg + 2 > args.length)
                confPath = args[lastArg];
        }

        txReport = new TransactionReport(clientPath, confPath, params);
        blockReport = new BlockReport(clientPath, confPath, params);
        // Parse the address given as the first parameter.
        forwardingAddress = Address.fromBase58(params, args[0]);

        System.out.println("Network: " + params.getId());
        System.out.println("Forwarding address: " + forwardingAddress);

        // Use WalletEx as the wallet type so the CoinJoinExtension is included automatically.
        final NetworkParameters finalParams = params;
        kit = new WalletAppKit(params, new File("."), filePrefix) {
            @Override
            protected void onSetupCompleted() {
                kit.peerGroup().setMaxConnections(6); // for small devnets
                kit.peerGroup().setUseLocalhostPeerWhenPossible(false);
                kit.peerGroup().setDropPeersAfterBroadcast(finalParams.getDropPeersAfterBroadcast());
                kit.wallet().getContext().setDebugMode(false);
                kit.setDiscovery(new ThreeMethodPeerDiscovery(finalParams, kit.system().masternodeListManager));

                // Initialize CoinJoin key chain and issue keys up to COINJOIN_KEY_COUNT
                WalletEx walletEx = (WalletEx) kit.wallet();
                if (!walletEx.getCoinJoin().hasKeyChain(
                        org.bitcoinj.wallet.DerivationPathFactory.get(finalParams).coinJoinDerivationPath(0))) {
                    walletEx.initializeCoinJoin(0);
                }
                int issuedCount = walletEx.getCoinJoin().getKeyChainGroup()
                        .getActiveKeyChain().getIssuedReceiveKeys().size();
                int keysToIssue = COINJOIN_KEY_COUNT - issuedCount;
                if (keysToIssue > 0) {
                    System.out.println("Issuing " + keysToIssue + " CoinJoin keys (already have " + issuedCount + ")...");
                    for (int i = 0; i < keysToIssue; i++) {
                        walletEx.getCoinJoin().freshReceiveKey();
                    }
                    System.out.println("CoinJoin keys ready: " + COINJOIN_KEY_COUNT);
                } else {
                    System.out.println("CoinJoin keys already at target: " + issuedCount);
                }
            }

            @Override
            protected Wallet createWallet() {
                return new WalletEx(finalParams,
                        KeyChainGroup.builder(finalParams, structure)
                                .fromRandom(Script.ScriptType.P2PKH)
                                .build()
                );
            }
        };

        // Tell WalletAppKit to use WalletEx when loading an existing wallet file too.
        kit.setWalletFactory((p, kcg) -> new WalletEx(p, kcg));

        if (params == RegTestParams.get()) {
            kit.connectToLocalHost();
        }

        if (checkpoints != null) {
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

        // We want to know when we receive money.
        kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                Coin value = tx.getValueSentToMe(w);
                System.out.println("Received tx for " + value.toFriendlyString() + ": " + tx);
                System.out.println("Transaction will be forwarded after it confirms.");

                txReport.add(System.currentTimeMillis(), w.getLastBlockSeenHeight(), tx);
                tx.getConfidence().addEventListener(confidenceListener);
                Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        Context.propagate(kit.wallet().getContext());
                        System.out.println("Confirmation received.");
                        forwardCoins(tx);

                        txReport.printReport();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());
            }
        });

        kit.chain().addNewBestBlockListener(new NewBestBlockListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                blockReport.add(block);
                blockReport.printReport();
            }
        });
        kit.system().chainLockHandler.addChainLockListener(new ChainLockListener() {
            @Override
            public void onNewChainLock(StoredBlock block) {
                blockReport.setChainLock(block);
            }
        });

        Address sendToAddress = Address.fromKey(params, kit.wallet().currentReceiveKey());
        System.out.println("Send coins to: " + sendToAddress);
        System.out.println("Waiting for coins to arrive. Press Ctrl-C to quit.");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}
    }

    private static void forwardCoins(Transaction tx) {
        try {
            Wallet wallet = kit.wallet();
            if (wallet.getBalance().equals(Coin.ZERO)) {
                return;
            }

            Coin valueReceived = tx.getValueSentToMe(wallet);
            Coin tenPercent = valueReceived.divide(10);
            Coin dustThreshold = Coin.valueOf(542);

            Address senderAddress = getSenderAddress(tx, wallet.getNetworkParameters());

            SendRequest sendRequest;
            if (senderAddress != null && !tenPercent.isLessThan(dustThreshold)) {
                Transaction forwardTx = new Transaction(wallet.getNetworkParameters());
                forwardTx.addOutput(tenPercent, senderAddress);
                sendRequest = SendRequest.forTx(forwardTx);
                sendRequest.changeAddress = forwardingAddress;
                sendRequest.tx.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript("test message".getBytes(StandardCharsets.UTF_8)));
                System.out.println("Sending 10% (" + tenPercent.toFriendlyString() + ") back to " + senderAddress);
            } else {
                sendRequest = SendRequest.emptyWallet(forwardingAddress);
            }

            Wallet.SendResult sendResult = wallet.sendCoins(sendRequest);
            checkNotNull(sendResult);
            System.out.println("Sending ...");
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getTxId());
                    wallet.getTransaction(sendResult.tx.getTxId()).getConfidence().addEventListener(confidenceListener);
                }
            }, MoreExecutors.directExecutor());

        } catch (KeyCrypterException | InsufficientMoneyException e) {
            throw new RuntimeException(e);
        }
    }

    private static Address getSenderAddress(Transaction tx, NetworkParameters params) {
        for (TransactionInput input : tx.getInputs()) {
            try {
                Script scriptSig = input.getScriptSig();
                if (scriptSig.getChunks().size() == 2) {
                    byte[] pubKeyBytes = scriptSig.getChunks().get(1).data;
                    if (pubKeyBytes != null && (pubKeyBytes.length == 33 || pubKeyBytes.length == 65)) {
                        ECKey key = ECKey.fromPublicOnly(pubKeyBytes);
                        return Address.fromKey(params, key, Script.ScriptType.P2PKH);
                    }
                }
            } catch (Exception e) {
                // skip this input, try the next
            }
        }
        return null;
    }

    static TransactionConfidence.Listener confidenceListener = new TransactionConfidence.Listener() {
        @Override
        public void onConfidenceChanged(TransactionConfidence confidence, ChangeReason reason) {
            System.out.println(" confidence change: " + confidence.getTransactionHash() + ": " + reason);
            if (confidence.getDepthInBlocks() > 6) {
                confidence.removeEventListener(confidenceListener);
            }
        }
    };
}