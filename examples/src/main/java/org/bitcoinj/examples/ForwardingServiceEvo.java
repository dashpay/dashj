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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.evolution.CreditFundingTransaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.net.discovery.ThreeMethodPeerDiscovery;
import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * ForwardingService demonstrates basic usage of the library. It sits on the network and when it receives coins, simply
 * sends them onwards to an address given on the command line.
 */
public class ForwardingServiceEvo {
    private static Address forwardingAddress;
    private static WalletAppKit kit;

    public static void main(String[] args) throws Exception {
        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        BriefLogFormatter.init();
        if (args.length < 1) {
            System.err.println("Usage: address-to-send-back-to [regtest|testnet|krupnik|devnet] [devnet-name] [devnet-sporkaddress] [devnet-port] [devnet-dnsseed...]");
            return;
        }

        // Figure out which network we should connect to. Each one gets its own set of files.
        NetworkParameters params;
        String filePrefix;
        String checkpoints = null;
        if (args.length > 1 && args[1].equals("testnet")) {
            params = TestNet3Params.get();
            filePrefix = "forwarding-service-testnet";
            checkpoints = "checkpoints-testnet.txt";
        } else if (args.length > 1 && args[1].equals("regtest")) {
            params = RegTestParams.get();
            filePrefix = "forwarding-service-regtest";
        } else if( args.length > 6 && args[1].equals("devnet")) {
            String [] dnsSeeds = new String[args.length - 5];
            System.arraycopy(args, 5, dnsSeeds, 0, args.length - 5);
            params = DevNetParams.get(args[2], args[3], Integer.parseInt(args[4]), dnsSeeds);
            filePrefix = "forwarding-service-devnet";
        }else {
            params = MainNetParams.get();
            filePrefix = "forwarding-service";
            checkpoints = "checkpoints.txt";
        }
        // Parse the address given as the first parameter.
        forwardingAddress = Address.fromBase58(params, args[0]);

        System.out.println("Network: " + params.getId());
        System.out.println("Forwarding address: " + forwardingAddress);

        // Start up a basic app using a class that automates some boilerplate.
        kit = new WalletAppKit(params, new File("."), filePrefix) {
            @Override
            protected void onSetupCompleted() {
                if(!kit.wallet().hasAuthenticationKeyChains())
                    kit.wallet().initializeAuthenticationKeyChains(kit.wallet().getKeyChainSeed(), null);
            }
        };
        kit.setDiscovery(new ThreeMethodPeerDiscovery(params, Context.get().masternodeListManager));

        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

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

        // We want to know when we receive money.
        kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                // Runs in the dedicated "user thread" (see bitcoinj docs for more info on this).
                //
                // The transaction "tx" can either be pending, or included into a block (we didn't see the broadcast).
                Coin value = tx.getValueSentToMe(w);
                System.out.println("Received tx for " + value.toFriendlyString() + ": " + tx);
                System.out.println("Transaction will be forwarded after it confirms.");
                // Wait until it's made it into the block chain (may run immediately if it's already there).
                //
                // For this dummy app of course, we could just forward the unconfirmed transaction. If it were
                // to be double spent, no harm done. Wallet.allowSpendingUnconfirmedTransactions() would have to
                // be called in onSetupCompleted() above. But we don't do that here to demonstrate the more common
                // case of waiting for a block.
                Futures.addCallback(tx.getConfidence().getDepthFuture(2), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        System.out.println("Confirmation received.");
                        forwardCoins(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());

                Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        System.out.println("Confirmation received.");
                        blockchainIdentity(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());
            }
        });

        Address sendToAddress = Address.fromKey(params, kit.wallet().currentReceiveKey());
        System.out.println("Send coins to: " + sendToAddress);
        System.out.println("Waiting for coins to arrive. Press Ctrl-C to quit.");

        System.out.println(kit.wallet()/*.getBlockchainIdentityKeyChain()*/);
        List<CreditFundingTransaction> list = kit.wallet().getCreditFundingTransactions();
        for(CreditFundingTransaction tx : list) {
            System.out.println(tx);
        }

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}
    }

    private static void forwardCoins(Transaction tx) {
        try {
            // Now send the coins onwards.
            SendRequest sendRequest = SendRequest.emptyWallet(forwardingAddress);
            Wallet.SendResult sendResult = kit.wallet().sendCoins(sendRequest);
            checkNotNull(sendResult);  // We should never try to send more coins than we have!
            System.out.println("Sending ...");
            // Register a callback that is invoked when the transaction has propagated across the network.
            // This shows a second style of registering ListenableFuture callbacks, it works when you don't
            // need access to the object the future returns.
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getTxId());
                }
            }, MoreExecutors.directExecutor());

        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    private static void blockchainIdentity(Transaction tx) {
        try {
            // Now send the coins onwards.

            if(CreditFundingTransaction.isCreditFundingTransaction(tx))
                return;

            AuthenticationKeyChain blockchainIdentityFunding = kit.wallet().getBlockchainIdentityFundingKeyChain();
            ECKey publicKey = blockchainIdentityFunding.freshAuthenticationKey();
            Coin fundingAmount = Coin.valueOf(40000);
            SendRequest sendRequest = SendRequest.creditFundingTransaction(kit.params(), publicKey, fundingAmount);
            Wallet.SendResult sendResult = kit.wallet().sendCoins(sendRequest);
            System.out.println("Sending Credit Funding Transaction...");
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("Blockchain Identity Funding Transaction hash is " + sendResult.tx.getTxId());
                    System.out.println(sendResult.tx.toString());
                }
            }, MoreExecutors.directExecutor());

        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

}
