package org.bitcoinj.examples;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.listeners.PreMessageReceivedEventListener;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.quorums.GetQuorumRotationInfo;
import org.bitcoinj.quorums.QuorumRotationInfo;
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
import java.util.concurrent.Future;

import static org.bitcoinj.utils.Threading.SAME_THREAD;

public class DownloadQRInfo {
    public static void main(String[] args) throws BlockStoreException, ExecutionException, InterruptedException {
        if (args.length < 2) {
            System.out.println("DownloadQRInfo network blockHash");
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
        SettableFuture<Boolean> qrInfoReceived = SettableFuture.create();
        peerGroup.addPreMessageReceivedEventListener(SAME_THREAD, (peer1, m) -> {
            try {
                if (m instanceof QuorumRotationInfo) {
                    System.out.println("Received qrinfo...");
                    File dumpFile = new File(params.getNetworkName() + "-qrinfo.dat");
                    OutputStream stream = new FileOutputStream(dumpFile);
                    stream.write(m.bitcoinSerialize());
                    stream.close();
                    qrInfoReceived.set(true);
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

        peer.sendMessage(new GetQuorumRotationInfo(params, Lists.newArrayList(params.getGenesisBlock().getHash()),
                Sha256Hash.wrap(blockHash), false)
        );


        qrInfoReceived.get();
        peerGroup.stopAsync();
    }
}
