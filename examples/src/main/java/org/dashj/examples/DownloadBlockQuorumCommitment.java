package org.dashj.examples;

import org.dashj.core.*;
import org.dashj.net.discovery.DnsDiscovery;
import org.dashj.params.MainNetParams;
import org.dashj.store.BlockStore;
import org.dashj.store.BlockStoreException;
import org.dashj.store.MemoryBlockStore;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DownloadBlockQuorumCommitment {
    public static void main(String[] args) throws BlockStoreException, ExecutionException, InterruptedException, IOException {
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

        String hash = "00000000000000316f3f7c6cd53f70e63ee09bf94deea435c36503f5511a3e09"; // contains a transaction of TRANSACTION_QUORUM_COMMITMENT type


        Sha256Hash blockHash = Sha256Hash.wrap(hash);
        Future<Block> future = peer.getBlock(blockHash);
        System.out.println("Waiting for node to send us the requested block: " + blockHash);
        Block block = future.get();
        System.out.println(block);
        System.out.println("Has TRANSACTION_QUORUM_COMMITMENT: " + block.getTransactions().stream().anyMatch(transaction -> transaction.getType() == Transaction.Type.TRANSACTION_QUORUM_COMMITMENT));

        peerGroup.stopAsync();
    }
}
