/*
 * Copyright (c) 2022 Dash Core Group
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
package org.bitcoinj.coinjoin.utils;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;
import org.bitcoinj.net.ClientConnectionManager;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public class MasternodeGroup extends PeerGroup {
    private static final Logger log = LoggerFactory.getLogger(MasternodeGroup.class);

    private final ReentrantLock pendingMasternodesLock = Threading.lock("pendingMasternodes");
    private final CopyOnWriteArrayList<Sha256Hash> pendingMasternodes = new CopyOnWriteArrayList<>();

    private final PeerDiscovery masternodeDiscovery = new PeerDiscovery() {
        @Override
        public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
            log.info("getPeers called");
            ArrayList<InetSocketAddress> addresses = new ArrayList<>();

            for (Sha256Hash proTxHash : pendingMasternodes) {
                SimplifiedMasternodeList mnList = context.masternodeListManager.getListAtChainTip();
                SimplifiedMasternodeListEntry mn = mnList.getMN(proTxHash);
                addresses.add(mn.getService().getSocketAddress());
            }
            return addresses.toArray(new InetSocketAddress[0]);
        }

        @Override
        public void shutdown() {

        }
    };
    /**
     * See {@link #MasternodeGroup(Context)}
     *
     * @param params
     */
    public MasternodeGroup(NetworkParameters params) {
        super(params);
    }

    /**
     * Creates a PeerGroup with the given context. No chain is provided so this node will report its chain height
     * as zero to other peers. This constructor is useful if you just want to explore the network but aren't interested
     * in downloading block data.
     *
     * @param context
     */
    public MasternodeGroup(Context context) {
        super(context);
    }

    /**
     * See {@link #MasternodeGroup(Context, AbstractBlockChain)}
     *
     * @param params
     * @param chain
     */
    public MasternodeGroup(NetworkParameters params, @Nullable AbstractBlockChain chain) {
        super(params, chain);
    }

    /**
     * See {@link #MasternodeGroup(Context, AbstractBlockChain)}
     *
     * @param params
     * @param chain
     * @param headerChain
     */
    public MasternodeGroup(NetworkParameters params, @Nullable AbstractBlockChain chain, @Nullable AbstractBlockChain headerChain) {
        super(params, chain, headerChain);
    }

    /**
     * Creates a PeerGroup for the given context and chain. Blocks will be passed to the chain as they are broadcast
     * and downloaded. This is probably the constructor you want to use.
     *
     * @param context
     * @param chain
     */
    public MasternodeGroup(Context context, @Nullable AbstractBlockChain chain) {
        super(context, chain);
        init();
    }

    private void init() {
        addPeerDiscovery(masternodeDiscovery);
        setUseLocalhostPeerWhenPossible(false);
        setDropPeersAfterBroadcast(false);
        setMaxConnections(0);
        shouldSendDsq(true);
    }

    /**
     * See {@link #MasternodeGroup(Context, AbstractBlockChain, AbstractBlockChain, ClientConnectionManager)}
     *
     * @param params
     * @param chain
     * @param connectionManager
     */
    public MasternodeGroup(NetworkParameters params, @Nullable AbstractBlockChain chain, ClientConnectionManager connectionManager) {
        super(params, chain, connectionManager);
    }

    public boolean addPendingMasternode(Sha256Hash proTxHash) {
        pendingMasternodesLock.lock();
        try {
            if (pendingMasternodes.contains(proTxHash))
                return false;
            setMaxConnections(getMaxConnections() + 1);
            log.info("adding masternode for mixing. maxConnections = {}", getMaxConnections());
            pendingMasternodes.add(proTxHash);
            return true;
        } finally {
            pendingMasternodesLock.unlock();
        }
    }

    public boolean isMasternodeOrDisconnectRequested(MasternodeAddress addr) {
        return forPeer(addr, new ForPeer() {
            @Override
            public boolean process(Peer peer) {
                return peer.isMasternode();
            }
        });
    }

    public interface ForPeer {
        boolean process(Peer peer);
    }

    public boolean forPeer(MasternodeAddress service, ForPeer predicate) {
        Preconditions.checkNotNull(service);
        List<Peer> peerList = getConnectedPeers();
        StringBuilder listOfPeers = new StringBuilder();
        for (Peer peer : peerList) {
            listOfPeers.append(peer.getAddress().getSocketAddress()).append(", ");
            if (peer.getAddress().getAddr().equals(service.getAddr())) {
                return predicate.process(peer);
            }
        }
        log.info("cannot find {} in the list of connected peers: {}", service.getSocketAddress(), listOfPeers);
        return false;
    }
}
