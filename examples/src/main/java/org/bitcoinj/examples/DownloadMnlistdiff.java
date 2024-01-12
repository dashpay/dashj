/*
 * Copyright 2024 Dash Core Group
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

import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.evolution.GetSimplifiedMasternodeListDiff;
import org.bitcoinj.evolution.SimplifiedMasternodeListDiff;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.bitcoinj.utils.Threading.SAME_THREAD;

public class DownloadMnlistdiff {
    public static void main(String[] args) throws BlockStoreException, ExecutionException, InterruptedException {
        if (args.length < 2) {
            System.out.println("DownloadMnlistdiff network blockHash");
            System.out.println("  one or more arguments are missing!");
            return;
        }
        BriefLogFormatter.init();
        String network = args[0];
        String blockHash = args[1];

        NetworkParameters params;
        switch (network) {
            case "testnet":
                params = TestNet3Params.get();
                break;
            default:
                params = MainNetParams.get();
                break;
        }
        Context context = Context.getOrCreate(params);
        BlockStore blockStore = new MemoryBlockStore(params);
        BlockChain chain = new BlockChain(params, blockStore);
        PeerGroup peerGroup = new PeerGroup(params, chain);
        peerGroup.addPeerDiscovery(new DnsDiscovery(params));
        peerGroup.setUseLocalhostPeerWhenPossible(false);

        peerGroup.start();
        peerGroup.waitForPeers(10).get();
        SettableFuture<Boolean> mnlistdiffReceivedFuture = SettableFuture.create();
        peerGroup.addPreMessageReceivedEventListener(SAME_THREAD, (peer1, m) -> {
            try {
                if (m instanceof SimplifiedMasternodeListDiff) {
                    System.out.println("Received mnlistdiff...");
                    File dumpFile = new File(params.getNetworkName() + "-mnlist.dat");
                    OutputStream stream = new FileOutputStream(dumpFile);
                    stream.write(m.bitcoinSerialize());
                    stream.close();
                    mnlistdiffReceivedFuture.set(true);
                    SimplifiedMasternodeListDiff diff = (SimplifiedMasternodeListDiff)m;
                    AtomicInteger countLegacy = new AtomicInteger();
                    AtomicInteger countEnabled = new AtomicInteger();
                    diff.getMnList().forEach(entry -> {
                        if (entry.isValid()) {
                            countLegacy.addAndGet((short) (entry.getVersion() == 1 ? 1 : 0));
                            countEnabled.addAndGet(1);
                        }
                    });
                    System.out.printf("Total: %d, Legacy: %d\n", countEnabled.get(), countLegacy.get());
                    return null;
                }
            } catch (FileNotFoundException e) {
                System.out.println("cannot find the file to write to");
                throw new RuntimeException(e);
            } catch (IOException e) {
                System.out.println("IO Error");
                e.printStackTrace();
                throw new RuntimeException(e);
            }

            return m;
        });
        Peer peer = peerGroup.getDownloadPeer();

        peer.sendMessage(new GetSimplifiedMasternodeListDiff(params.getGenesisBlock().getHash(), Sha256Hash.wrap(blockHash)));


        mnlistdiffReceivedFuture.get();
        peerGroup.stopAsync();
    }
}
