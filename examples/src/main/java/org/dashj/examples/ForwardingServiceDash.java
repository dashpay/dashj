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

package org.dashj.examples;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.dashj.core.*;
import org.dashj.crypto.ChildNumber;
import org.dashj.crypto.KeyCrypterException;
import org.dashj.evolution.*;
import org.dashj.governance.GovernanceManager;
import org.dashj.governance.GovernanceObject;
import org.dashj.governance.GovernanceVoteBroadcast;
import org.dashj.kits.EvolutionWalletAppKit;
import org.dashj.kits.WalletAppKit;
import org.dashj.masternode.owner.MasternodeControl;
import org.dashj.params.DevNetParams;
import org.dashj.params.MainNetParams;
import org.dashj.params.RegTestParams;
import org.dashj.params.TestNet3Params;
import org.dashj.store.FlatDB;
import org.dashj.utils.BriefLogFormatter;
import org.dashj.wallet.DeterministicKeyChain;
import org.dashj.wallet.Wallet;
import org.dashj.wallet.SendRequest;
import org.dashj.wallet.listeners.WalletCoinsReceivedEventListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * ForwardingService demonstrates basic usage of the library. It sits on the network and when it receives coins, simply
 * sends them onwards to an address given on the command line.
 */
public class ForwardingServiceDash {
    private static Address forwardingAddress;
    private static WalletAppKit kit;

    private static MasternodeControl control;

    public static void main(String[] args) throws Exception {
        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        BriefLogFormatter.initVerbose();
        if (args.length < 1) {
            System.err.println("Usage: address-to-send-back-to [regtest|testnet|devnet] [devnet-name] [devnet-sporkaddress] [devnet-port] [devnet-dnsseed...]");
            return;
        }

        // Figure out which network we should connect to. Each one gets its own set of files.
        NetworkParameters params;
        String filePrefix;
        if (args.length > 1 && args[1].equals("testnet")) {
            params = TestNet3Params.get();
            filePrefix = "forwarding-service-testnet";
        } else if (args.length > 1 && args[1].equals("regtest")) {
            params = RegTestParams.get();
            filePrefix = "forwarding-service-regtest";
        } else if( args.length > 6 && args[1].equals("devnet")) {
            String [] dnsSeeds = new String[args.length - 5];
            System.arraycopy(args, 5, dnsSeeds, 0, args.length - 5);
            params = DevNetParams.get(args[2], args[3], Integer.parseInt(args[4]), dnsSeeds);
            filePrefix = "forwarding-service-devnet";
        } else {
            params = MainNetParams.get();
            filePrefix = "forwarding-service";
        }
        // Parse the address given as the first parameter.
        forwardingAddress = Address.fromBase58(params, args[0]);

        // Start up a basic app using a class that automates some boilerplate.
        if(args[1] == "devnet") {
            kit = new WalletAppKit(params, new File("."), filePrefix, false) {
                @Override
                protected void onSetupCompleted() {
                    super.onSetupCompleted();
                    peerGroup().setMinBroadcastConnections(2);
                    peerGroup().setMaxConnections(3);
                }
            };
        } else {
            kit = new EvolutionWalletAppKit(params, new File("."), filePrefix, false) {
                @Override
                protected void onSetupCompleted() {
                    super.onSetupCompleted();
                    peerGroup().setMinBroadcastConnections(2);
                    peerGroup().setMaxConnections(3);
                }
            };
        }


        //kit = new LevelDBWalletAppKit(params, new File("."), filePrefix);

        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
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

                if(tx.getType() != Transaction.Type.TRANSACTION_NORMAL)
                    return;
                // Wait until it's made it into the block chain (may run immediately if it's already there).
                //
                // For this dummy app of course, we could just forward the unconfirmed transaction. If it were
                // to be double spent, no harm done. Wallet.allowSpendingUnconfirmedTransactions() would have to
                // be called in onSetupCompleted() above. But we don't do that here to demonstrate the more common
                // case of waiting for a block.
                Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        createUser(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());

                Futures.addCallback(tx.getConfidence().getDepthFuture(2), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        topup(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());

                Futures.addCallback(tx.getConfidence().getDepthFuture(3), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        reset(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());

                Futures.addCallback(tx.getConfidence().getDepthFuture(4), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        forwardCoins(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                }, MoreExecutors.directExecutor());
            }
        });

        Address sendToAddress = kit.wallet().currentReceiveKey().toAddress(params);
        System.out.println("Send coins to: " + sendToAddress);
        System.out.println("Waiting for coins to arrive. Press Ctrl-C to quit.");

        Context.get().masternodeSync.addEventListener(new MasternodeSyncListener() {
            @Override
            public void onSyncStatusChanged(int newStatus, double syncStatus) {
                if(newStatus == MasternodeSync.MASTERNODE_SYNC_FINISHED) {
                    FlatDB<MasternodeManager> mndb = new FlatDB<>(kit.directory().getAbsolutePath(), "mncache.dat", "magicMasternodeCache");
                    mndb.dump(Context.get().masternodeManager);

                    if(control == null) {
                        control = new MasternodeControl(Context.get(), "masternode.conf");
                        control.load();
                    }
/*
                    Address address = new Address(params, params.getAddressHeader(), new DumpedPrivateKey(params, "91hMFoqbdqJdmCfgSniMBsWP4Vy9HDbdujg5Y7vpqCMXafw49jt").getKey().getPubKeyHash());
                    int version = address.getVersion();

                    byte [] ip =  {13, (byte)229, 80, 108};
                    try {
                        Masternode mn = Context.get().masternodeManager.find(new MasternodeAddress(InetAddress.getByAddress("13.229.80.108", ip), 19999));
                        if (mn == null) {

                        }
                    } catch (UnknownHostException x) {

                    }
*/

                //} else if(newStatus == MasternodeSync.MASTERNODE_SYNC_GOVERNANCE) {

                    FlatDB<GovernanceManager> gmdb = new FlatDB<>(kit.directory().getAbsolutePath(), "goverance.dat", "magicGovernanceCache");
                    gmdb.dump(Context.get().governanceManager);
                    ArrayList<GovernanceObject> list = Context.get().governanceManager.getAllNewerThan(Utils.currentTimeSeconds() - 2 * 30 * 24 * 60 * 60);
                    //ArrayList<Pair<Integer, Masternode>> results = Context.get().masternodeManager.getMasternodeRanks(27143, 0);
                    //System.out.println(results.toString());
                    StringBuilder error = new StringBuilder();
                    for (GovernanceObject governanceObject : list) {
                        if(governanceObject.getObjectType() == 1) {
                            GovernanceVoteBroadcast broadcast = control.voteAlias("test_mn", governanceObject.getHash().toString(), "funding", "yes", error);
                        }
                    }
                }

            }
        });

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {}
    }

    static EvolutionUser currentUser;
    static ECKey privKey;
    static ECKey newPrivKey;

    private static void createUser(Transaction tx) {
        try {

            Coin amount = Coin.parseCoin("0.001");
            privKey = ECKey.fromPrivate(kit.wallet().getActiveKeyChain().getKeyByPath(DeterministicKeyChain.ACCOUNT_ZERO_PATH, false).getPrivKeyBytes());
            newPrivKey = ECKey.fromPrivate(kit.wallet().getActiveKeyChain().getKeyByPath(ImmutableList.of(ChildNumber.ONE_HARDENED), true).getPrivKeyBytes());
            SendRequest req = SendRequest.forSubTxRegister(kit.params(),
                    new SubTxRegister(1, "hashengineering"+new Random().nextInt()/1000,
                            privKey),
                    amount);

            final Wallet.SendResult sendResult = kit.wallet().sendCoins(req);

            currentUser = Context.get().evoUserManager.getUser(sendResult.tx.getHash());

            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("SubTxRegister! Transaction hash is " + sendResult.tx.getHashAsString());
                }
            }, MoreExecutors.directExecutor());

            FlatDB<EvolutionUserManager> mndb = new FlatDB<EvolutionUserManager>(kit.directory().getAbsolutePath(),"user.dat", "magicMasternodeCache");
            mndb.dump(Context.get().evoUserManager);
        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    private static void topup(Transaction tx) {
        try {


            SendRequest topup = SendRequest.forSubTxTopup(kit.params(),
                    new SubTxTopup(1, currentUser.getRegTxId()), Coin.valueOf(50000000));

            final Wallet.SendResult sendResult = kit.wallet().sendCoins(topup);


            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("SubTxTopup! Transaction hash is " + sendResult.tx.getHashAsString());
                }
            }, MoreExecutors.directExecutor());

            FlatDB<EvolutionUserManager> mndb = new FlatDB<EvolutionUserManager>(kit.directory().getAbsolutePath(),"user.dat", "magicMasternodeCache");
            mndb.dump(Context.get().evoUserManager);
        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    private static void reset(Transaction tx) {
        try {

            SendRequest reset = SendRequest.forSubTxResetKey(kit.params(),
                    new SubTxResetKey(1, currentUser.getRegTxId(), currentUser.getCurSubTx(), SubTxTransition.EVO_TS_MIN_FEE, new KeyId(newPrivKey.getPubKeyHash()), privKey));

            final Wallet.SendResult sendResult = kit.wallet().sendCoins(reset);

            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("SubTxResetKey! Transaction hash is " + sendResult.tx.getHashAsString());
                }
            }, MoreExecutors.directExecutor());

            FlatDB<EvolutionUserManager> mndb = new FlatDB<EvolutionUserManager>(kit.directory().getAbsolutePath(),"user.dat", "magicMasternodeCache");
            mndb.dump(Context.get().evoUserManager);
        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

    private static void forwardCoins(Transaction tx) {
        try {

            //Coin value = tx.getValueSentToMe(kit.wallet()).subtract(amount);
            System.out.println("Forwarding " + kit.wallet().getBalance());
            // Now send the coins back! Send with a small fee attached to ensure rapid confirmation.
            //final Coin amountToSend = value.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE);
            final Wallet.SendResult sendResult = //kit.wallet().sendCoins(kit.peerGroup(), forwardingAddress, amountToSend);
                    kit.wallet().sendCoins(SendRequest.emptyWallet(forwardingAddress));
            checkNotNull(sendResult);  // We should never try to send more coins than we have!
            System.out.println("Sending ...");
            // Register a callback that is invoked when the transaction has propagated across the network.
            // This shows a second style of registering ListenableFuture callbacks, it works when you don't
            // need access to the object the future returns.
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
                }
            }, MoreExecutors.directExecutor());

            //MasternodeDB.dumpMasternodes();
            FlatDB<MasternodeManager> mndb = new FlatDB<MasternodeManager>(kit.directory().getAbsolutePath(),"mncache.dat", "magicMasternodeCache");
            mndb.dump(Context.get().masternodeManager);
        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

}
