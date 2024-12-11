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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import net.jcip.annotations.GuardedBy;
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.coinjoin.CoinJoinClientSession;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.MasternodeListManager;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.net.ClientConnectionManager;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.PeerDiscoveryException;
import org.bitcoinj.utils.ExponentialBackoff;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.lang.Math.min;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_EXTRA;


public class MasternodeGroup extends PeerGroup implements NewBestBlockListener {
    private static final Logger log = LoggerFactory.getLogger(MasternodeGroup.class);

    private final ReentrantLock pendingMasternodesLock = Threading.lock("pendingMasternodes");
    //@GuardedBy("pendingMasternodesLock")
    protected final HashSet<CoinJoinClientSession> pendingSessions = Sets.newHashSet();
    protected final HashMap<Sha256Hash, CoinJoinClientSession> masternodeMap = new HashMap<>();
    protected final HashMap<MasternodeAddress, CoinJoinClientSession> addressMap = new HashMap<>();

    private MasternodeListManager masternodeListManager;
    private final ArrayList<Masternode> pendingClosingMasternodes = new ArrayList<>();

    private final PeerDiscovery masternodeDiscovery = new PeerDiscovery() {
        @Override
        public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
            ArrayList<InetSocketAddress> addresses = new ArrayList<>();
            pendingMasternodesLock.lock();
            try {
                for (CoinJoinClientSession session : pendingSessions) {
                    if (session.getMixingMasternodeInfo() != null && !pendingClosingMasternodes.contains(session.getMixingMasternodeInfo())) {
                        addresses.add(session.getMixingMasternodeInfo().getService().getSocketAddress());
                        addressMap.put(session.getMixingMasternodeInfo().getService(), session);
                        log.info("discovery: {} -> {}", session.getMixingMasternodeInfo().getService().getAddr(), session);
                    }
                }
            } finally {
                pendingMasternodesLock.unlock();
            }
            return addresses.toArray(new InetSocketAddress[0]);
        }

        @Override
        public void shutdown() {

        }
    };
    private CoinJoinManager coinJoinManager;

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

    public MasternodeGroup(NetworkParameters params, @Nullable AbstractBlockChain chain) {
        super(params, chain);
        init(null);
    }

    public MasternodeGroup(NetworkParameters params, @Nullable AbstractBlockChain chain, @Nullable AbstractBlockChain headerChain) {
        super(params, chain, headerChain);
        init(null);
    }

    /**
     * Creates a PeerGroup for the given context and chain. Blocks will be passed to the chain as they are broadcast
     * and downloaded. This is probably the constructor you want to use.
     *
     * @param context
     * @param chain
     */
    public MasternodeGroup(Context context, @Nullable AbstractBlockChain chain, SimplifiedMasternodeListManager masternodeListManager) {
        super(context, chain, false);
        init(masternodeListManager);
    }

    private void init(MasternodeListManager masternodeListManager) {
        this.masternodeListManager = masternodeListManager;
        addPeerDiscovery(masternodeDiscovery);
        setUseLocalhostPeerWhenPossible(false);
        setDropPeersAfterBroadcast(false);
        setMaxConnections(0);
        shouldSendDsq(true);
    }

    /**
     *
     * @param params
     * @param chain
     * @param connectionManager
     */
    public MasternodeGroup(NetworkParameters params, @Nullable AbstractBlockChain chain,
                           MasternodeListManager masternodeListManager, ClientConnectionManager connectionManager) {
        super(params, chain, connectionManager);
        init(masternodeListManager);
    }

    public boolean addPendingMasternode(CoinJoinClientSession session) {
        int maxConnections = getMaxConnections();
        pendingMasternodesLock.lock();
        try {
            log.info("adding masternode for mixing. maxConnections = {}, protx: {}", maxConnections, session.getMixingMasternodeInfo().getProTxHash());
            pendingSessions.add(session);
            masternodeMap.put(session.getMixingMasternodeInfo().getProTxHash(), session);
        } finally {
            pendingMasternodesLock.unlock();
        }
        updateMaxConnections();
        checkMasternodesWithoutSessions();
        return true;
    }



    // Exponential backoff for peers starts at 1 second and maxes at 5 seconds.
    private final ExponentialBackoff.Params masternodeBackoffParams = new ExponentialBackoff.Params(1000, 1.001f, 5 * 1000);
    // Tracks failures globally in case of a network failure.
    @GuardedBy("lock") private final ExponentialBackoff masternodeGroupBackoff = new ExponentialBackoff(new ExponentialBackoff.Params(1000, 1.5f, 2 * 1001));

    // Adds peerAddress to backoffMap map and inactives queue.
    // Returns true if it was added, false if it was already there.
    @Override
    protected boolean addInactive(PeerAddress peerAddress, int priority) {
        lock.lock();
        try {
            // Deduplicate, handle differently than PeerGroup
            if (inactives.contains(peerAddress))
                return false;

            // do not connect to another the same peer twice
            if (isNodeConnected(peerAddress) || isNodeConnected(peerAddress)) {
                log.info("attempting to connect to the same masternode again: {}", peerAddress);
                return false; // do not connect to the same masternode
            }

            backoffMap.put(peerAddress, new ExponentialBackoff(masternodeBackoffParams));
            if (priority != 0)
                priorityMap.put(peerAddress, priority);
            inactives.offer(peerAddress);
            return true;
        } finally {
            lock.unlock();
        }
    }

    private void updateMaxConnections() {
        int maxConnections;
        pendingMasternodesLock.lock();
        try {
            maxConnections = pendingSessions.size();
        } finally {
            pendingMasternodesLock.unlock();
        }
        try {
            log.info(COINJOIN_EXTRA, "updating max connections to min({}, {})", maxConnections, min(maxConnections, CoinJoinClientOptions.getSessions()));
            setMaxConnections(min(maxConnections, CoinJoinClientOptions.getSessions()));
        } catch (NoSuchElementException e) {
            // swallow, though this is not good, which
            log.info("caught exception", e);
        }
    }

    public boolean isMasternodeOrDisconnectRequested(MasternodeAddress addr) {
        boolean found = forPeer(addr, new ForPeer() {
            @Override
            public boolean process(Peer peer) {
                return true;
            }
        }, false);

        if (!found) {
            for (Masternode mn : pendingClosingMasternodes) {
                if (mn.getService().equals(addr)) {
                    found = true;
                }
            }
        }
        return found;
    }

    public boolean disconnectMasternode(Masternode mn) {
        return forPeer(mn.getService(), new ForPeer() {
            @Override
            public boolean process(Peer peer) {
                log.info("masternode[closing] {}", peer.getAddress().getSocketAddress());
                lock.lock();
                try {
                    pendingClosingMasternodes.add(mn);
                } finally {
                    lock.unlock();
                }
                //TODO: what if this disconnects the wrong one
                updateMaxConnections();
                peer.close();
                return true;
            }
        }, true);
    }

    @Override
    protected void handlePeerDeath(final Peer peer, @Nullable Throwable exception) {
        super.handlePeerDeath(peer, exception);
        Masternode masternode = null;
        for (Masternode mn : pendingClosingMasternodes) {
            if (peer.getAddress().getSocketAddress().equals(mn.getService().getSocketAddress())) {
                masternode = mn;
            }
        }
        log.info("handling this mn peer death: {} -> {}", peer.getAddress().getSocketAddress(),
                masternode != null ? masternode.getService().getSocketAddress() : "not found in closing list");
        if (masternode != null) {
            pendingMasternodesLock.lock();
            PeerAddress address = peer.getAddress();
            try {
                if (pendingClosingMasternodes.contains(masternode)) {
                    // if this is part of pendingClosingMasternodes, where we want to close the connection,
                    // we don't want to increase the backoff time
                    backoffMap.get(address).trackSuccess();
                }
                pendingClosingMasternodes.remove(masternode);
                pendingSessions.remove(masternodeMap.get(masternode.getProTxHash()));
                masternodeMap.remove(masternode.getProTxHash());
                addressMap.remove(masternode.getService());
            } finally {
                pendingMasternodesLock.unlock();
            }
        }
        checkMasternodesWithoutSessions();
    }

    @Nullable
    @Override
    protected Peer connectTo(PeerAddress address, boolean incrementMaxConnections, int connectTimeoutMillis) {
        if (!isMasternodeSession(address))
            return null;

        // TODO: this is considered a hack to get around the default behavior of PeerGroup
        if (isNodeConnected(address) || isNodePending(address)) {
            log.info("attempting to connect to the same masternode again: {}", address);
            return null; // do not connect to the same peer again
        }

        Masternode mn = masternodeListManager.getListAtChainTip().getMNByAddress(address.getSocketAddress());
        CoinJoinClientSession session;

        pendingMasternodesLock.lock();
        try {
            session = masternodeMap.get(mn.getProTxHash());
        } finally {
            pendingMasternodesLock.unlock();
        }

        // TODO: this is considered a hack to get around the default behavior of PeerGroup
        if (session == null || session.getMixingMasternodeInfo() == null) {
            log.warn("session is not connected to a masternode: {}", session);
            return null;
        }

        Peer peer = super.connectTo(address, incrementMaxConnections, connectTimeoutMillis);
        log.info("masternode[connecting] {}: {}; {}", peer.getAddress().getSocketAddress(),
                session.getMixingMasternodeInfo().getProTxHash().toString().substring(0, 16), session);
        return peer;

    }

    private boolean isNodeConnected(PeerAddress address) {
        return forPeer(new MasternodeAddress(address.getSocketAddress()), new ForPeer() {
            @Override
            public boolean process(Peer peer) {
                return peer.getAddress().equals(address);
            }
        }, false);
    }

    private boolean isNodePending(PeerAddress address) {
        List<Peer> pendingPeers = getPendingPeers();
        for (Peer peer: pendingPeers) {
            if (peer.getAddress().equals(address)) {
                return true;
            }
        }
        return false;
    }

    @GuardedBy("lock")
    private void checkMasternodesWithoutSessions() {
        List<Peer> listMasternodes = getConnectedPeers();
        ArrayList<Peer> masternodesToDrop = Lists.newArrayList();
        pendingMasternodesLock.lock();
        try {
            for (Peer masternode : listMasternodes) {
                boolean found = false;
                for (CoinJoinClientSession session : pendingSessions) {
                    Masternode sessionMixingMasternode = session.getMixingMasternodeInfo();
                    if (sessionMixingMasternode != null) {
                        if (sessionMixingMasternode.getService().getSocketAddress().equals(masternode.getAddress().getSocketAddress())) {
                            found = true;
                        }
                    } else {
                        // TODO: we may not need this anymore
                        log.info(COINJOIN_EXTRA, "session is not connected to a masternode: {}", session);
                    }
                }
                if (!found) {
                    log.info("masternode is not connected to a session: {}", masternode.getAddress().getSocketAddress());
                    masternodesToDrop.add(masternode);
                }
            }
        } finally {
            pendingMasternodesLock.unlock();
        }
        log.info("need to drop {} masternodes", masternodesToDrop.size());
        masternodesToDrop.forEach(peer -> {
            Masternode mn = masternodeListManager.getListAtChainTip().getMNByAddress(peer.getAddress().getSocketAddress());
            //pendingSessions.remove(mn.getProTxHash());
            log.info("masternode will be disconnected: {}: {}", peer, mn.getProTxHash());
            peer.close();
        });
    }

    public CoinJoinClientSession getMixingSession(Peer masternode) {
        return addressMap.get(new MasternodeAddress(masternode.getAddress().getSocketAddress()));
    }

    public void setCoinJoinManager(CoinJoinManager coinJoinManager) {
        this.coinJoinManager = coinJoinManager;
    }

    public interface ForPeer {
        boolean process(Peer peer);
    }

    public boolean forPeer(MasternodeAddress service, ForPeer predicate, boolean warn) {
        Preconditions.checkNotNull(service);
        List<Peer> peerList = getConnectedPeers();
        StringBuilder listOfPeers = new StringBuilder();
        for (Peer peer : peerList) {
            listOfPeers.append(peer.getAddress().getSocketAddress()).append(", ");
            if (peer.getAddress().getAddr().equals(service.getAddr())) {
                return predicate.process(peer);
            }
        }
        if (warn) {
            if (!isNodePending(new PeerAddress(params, service.getSocketAddress()))) {
                log.info("cannot find {} in the list of connected peers: {}", service.getSocketAddress(), listOfPeers);
                new Exception("cannot find " + service.getSocketAddress()).printStackTrace();
            } else {
                log.info("{} in the list of pending peers: {}", service.getSocketAddress(), listOfPeers);
            }
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("MasternodeGroup{connected=%d/max=%d, pending-mns=%d, closing-mns=%d}",
                numConnectedPeers(), getMaxConnections(), pendingSessions.size(), pendingClosingMasternodes.size()));
        for (Peer masternode : getConnectedPeers()) {
            builder.append("\n  ")
                    .append(masternode.getAddress().getSocketAddress())
                    .append(": ")
                    .append(getMixingSession(masternode));
        }
        return builder.toString();
    }

    protected boolean isMasternodeSession(PeerAddress address) {
        pendingMasternodesLock.lock();
        try {
            return addressMap.containsKey(new MasternodeAddress(address.getSocketAddress()));
        } finally {
            pendingMasternodesLock.unlock();
        }
    }

    @Override
    protected void triggerConnections() {
        // Run on a background thread due to the need to potentially retry and back off in the background.
        if (!executor.isShutdown())
            executor.execute(triggerMasternodeConnectionsJob);
    }

    private final Runnable triggerMasternodeConnectionsJob = new Runnable() {
        private boolean firstRun = true;
        private final static long MIN_PEER_DISCOVERY_INTERVAL = 1000L;

        @Override
        public void run() {
            try {
                go();
            } catch (Throwable e) {
                log.error("Exception when trying to build connections", e);  // The executor swallows exceptions :(
            }
        }

        public void go() {
            if (!isRunning()) return;

            if (coinJoinManager.isWaitingForNewBlock() || !coinJoinManager.isMixing())
                return;
 
            boolean doDiscovery = false;
            long now = Utils.currentTimeMillis();
            lock.lock();
            try {
                boolean havePeerWeCanTry = !inactives.isEmpty() && backoffMap.get(inactives.peek()).getRetryTime() <= now;
                doDiscovery = !havePeerWeCanTry;
            } finally {
                firstRun = false;
                lock.unlock();
            }

            // Don't hold the lock across discovery as this process can be very slow.
            boolean discoverySuccess = false;
            if (doDiscovery) {
                discoverySuccess = discoverPeers() > 0;
            }

            long retryTime;
            PeerAddress addrToTry;
            lock.lock();
            try {
                if (doDiscovery) {
                    // Require that we have enough connections, to consider this
                    // a success, or we just constantly test for new peers
                    if (discoverySuccess && countConnectedAndPendingPeers() >= getMaxConnections()) {
                        masternodeGroupBackoff.trackSuccess();
                    } else {
                        masternodeGroupBackoff.trackFailure();
                    }
                }
                // Inactives is sorted by backoffMap time.
                if (inactives.isEmpty()) {
                    if (countConnectedAndPendingPeers() < getMaxConnections()) {
                        long interval = Math.max(masternodeGroupBackoff.getRetryTime() - now, MIN_PEER_DISCOVERY_INTERVAL);
                        log.info("Masternode discovery didn't provide us any more masternodes, will try again in "
                                + interval + "ms.");
                        executor.schedule(this, interval, TimeUnit.MILLISECONDS);
                    } else {
                        // We have enough peers and discovery provided no more, so just settle down. Most likely we
                        // were given a fixed set of addresses in some test scenario.
                    }
                    return;
                } else {
                    do {
                        addrToTry = inactives.poll();
                    } while (isIpv6Unreachable() && addrToTry.getAddr() instanceof Inet6Address);
                    retryTime = backoffMap.get(addrToTry).getRetryTime();
                }
                retryTime = Math.max(retryTime, masternodeGroupBackoff.getRetryTime());
                if (retryTime > now) {
                    long delay = retryTime - now;
                    log.info("Waiting {} ms before next connect attempt to masternode {}", delay, addrToTry == null ? "" : "to " + addrToTry);
                    inactives.add(addrToTry);
                    executor.schedule(this, delay, TimeUnit.MILLISECONDS);
                    return;
                }
                connectTo(addrToTry, false, getConnectTimeoutMillis());
            } finally {
                lock.unlock();
            }
            if (countConnectedAndPendingPeers() < getMaxConnections()) {
                executor.execute(this);   // Try next peer immediately.
            }
        }
    };

    @Override
    public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
        log.info("New block found, restarting masternode connections job");
        triggerConnections();
    }

    @Override
    public ListenableFuture startAsync() {
        if (chain != null) {
            chain.addNewBestBlockListener(this);
        }

        return super.startAsync();
    }

    @Override
    public ListenableFuture stopAsync() {
        if (chain != null) {
            chain.removeNewBestBlockListener(this);
        }
        return super.stopAsync();
    }
}
