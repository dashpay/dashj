/*
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
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.MasternodeSeedPeers;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.authentication.AuthenticationGroupExtension;

import java.io.File;

/**
 * The following example shows you how to restore a HD wallet from a previously generated deterministic seed.
 * In this example we manually setup the blockchain, peer group, etc. You can also use the WalletAppKit which provides a restoreWalletFromSeed function to load a wallet from a deterministic seed.
 */
public class RestoreFromSeedThenDump {

    public static void main(String[] args) throws Exception {
        BriefLogFormatter.init();
        NetworkParameters params = TestNet3Params.get();
        if (args.length == 0) {
            System.out.println("Enter a seed phrase");
        }

        // Bitcoinj supports hierarchical deterministic wallets (or "HD Wallets"): https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki
        // HD wallets allow you to restore your wallet simply from a root seed. This seed can be represented using a short mnemonic sentence as described in BIP 39: https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki

        // Here we restore our wallet from a seed with no passphrase. Also have a look at the BackupToMnemonicSeed.java example that shows how to backup a wallet by creating a mnemonic sentence.
        String seedCode = args[0];
        String passphrase = "";
        long creationtime = 1547463771L;//1409478661L;

        DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);
        DerivationPathFactory factory = DerivationPathFactory.get(params);
        KeyChainGroup keyChainGroup = KeyChainGroup.builder(params)
                .addChain(DeterministicKeyChain.builder()
                        .seed(seed)
                        .accountPath(factory.bip32DerivationPath(0))
                        .build())
                .addChain(DeterministicKeyChain.builder()
                        .seed(seed)
                        .accountPath(factory.bip44DerivationPath(0))
                        .build())
                .build();

        // The wallet class provides a easy fromSeed() function that loads a new wallet from a given seed.
        Wallet wallet = new Wallet(params, keyChainGroup);
        AuthenticationGroupExtension mnext = new AuthenticationGroupExtension(wallet);
        mnext.addKeyChain(seed, factory.masternodeOwnerDerivationPath(), AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER);
        mnext.addKeyChain(seed, factory.masternodeVotingDerivationPath(), AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING);
        mnext.addKeyChain(seed, factory.masternodeOperatorDerivationPath(), AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR);
        wallet.addExtension(mnext);

        // Because we are importing an existing wallet which might already have transactions we must re-download the blockchain to make the wallet picks up these transactions
        // You can find some information about this in the guides: https://bitcoinj.github.io/working-with-the-wallet#setup
        // To do this we clear the transactions of the wallet and delete a possible existing blockchain file before we download the blockchain again further down.
        System.out.println(wallet.toString());
        wallet.clearTransactions(0);
        File chainFile = new File("restore-from-seed.spvchain");
        if (chainFile.exists()) {
            chainFile.delete();
        }

        // Setting up the BlochChain, the BlocksStore and connecting to the network.
        SPVBlockStore chainStore = new SPVBlockStore(params, chainFile);
        BlockChain chain = new BlockChain(params, chainStore);
        PeerGroup peerGroup = new PeerGroup(params, chain);
        peerGroup.addPeerDiscovery(new MasternodeSeedPeers(params));

        // Now we need to hook the wallet up to the blockchain and the peers. This registers event listeners that notify our wallet about new transactions.
        chain.addWallet(wallet);
        peerGroup.addWallet(wallet);

        DownloadProgressTracker bListener = new DownloadProgressTracker() {
            @Override
            public void doneDownload() {
                System.out.println("blockchain downloaded");
            }
        };

        // Now we re-download the blockchain. This replays the chain into the wallet. Once this is completed our wallet should know of all its transactions and print the correct balance.
        peerGroup.start();
        peerGroup.startBlockChainDownload(bListener);

        bListener.await();

        // Print a debug message with the details about the wallet. The correct balance should now be displayed.
        System.out.println(wallet.toString());

        // shutting down again
        peerGroup.stop();
        //System.out.println(wallet.toString(true, false, null, true, true,));
    }
}
