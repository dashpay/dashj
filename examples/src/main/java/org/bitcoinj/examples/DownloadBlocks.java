package org.bitcoinj.examples;

import org.bitcoinj.core.*;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DownloadBlocks {
    public static void main(String[] args) throws BlockStoreException, ExecutionException, InterruptedException {
        System.out.println("Connecting to node");
        final NetworkParameters params = MainNetParams.get();
        BlockStore blockStore = new MemoryBlockStore(params);
        BlockChain chain = new BlockChain(params, blockStore);
        PeerGroup peerGroup = new PeerGroup(params, chain);
        peerGroup.addPeerDiscovery(new DnsDiscovery(params));
        peerGroup.setUseLocalhostPeerWhenPossible(false);

        peerGroup.start();
        peerGroup.waitForPeers(10).get();
        Peer peer = peerGroup.getDownloadPeer();

        for (String hash : new String[]{
                "0000000000000011fbdfb7ebea7b3b68c11c82f68fe792e4a376f0f3e1ebfee5",
                "000000000000001b7c5666cbda73912a5bcff8fc179fdd421fc9bfe7b7e7be73",
                "000000000000000cdf5cc24c3beb0669b31e942d1301e07b53d6f0c7db10860d", // <<<<< doesn't work with dashj-0.13 branch
        }) {

            Sha256Hash blockHash = Sha256Hash.wrap(hash);
            Future<Block> future = peer.getBlock(blockHash);
            System.out.println("Waiting for node to send us the requested block: " + blockHash);
            Block block = future.get();
            System.out.println(block);
            try {
                UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(block.getMessageSize());
                block.bitcoinSerialize(bos);
                System.out.println(Utils.HEX.encode(bos.toByteArray()));
            } catch(IOException x) {
                throw new RuntimeException(x.getMessage());
            }
        }
        peerGroup.stopAsync();
    }
}
