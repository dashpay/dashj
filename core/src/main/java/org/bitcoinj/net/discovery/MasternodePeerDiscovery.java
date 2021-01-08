package org.bitcoinj.net.discovery;

import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Created by hashengineering on 12/5/18.
 */
public class MasternodePeerDiscovery implements PeerDiscovery {

    ArrayList<InetSocketAddress> masternodeSeeds;
    private int pnseedIndex;

    /**
     * Supports finding peers by IP addresses
     *
     * @param mnList IP addresses for seed addresses.
     */
    public MasternodePeerDiscovery(SimplifiedMasternodeList mnList) {
        masternodeSeeds = new ArrayList<InetSocketAddress>(mnList.size());
        mnList.forEachMN(true, new SimplifiedMasternodeList.ForeachMNCallback() {
            @Override
            public void processMN(SimplifiedMasternodeListEntry mn) {
                masternodeSeeds.add(mn.getService().toSocketAddress());
            }
        });
    }

    public MasternodePeerDiscovery(String [] mnList, int port) {
        masternodeSeeds = new ArrayList<InetSocketAddress>(mnList.length);
        for (String mn: mnList) {
            masternodeSeeds.add(new InetSocketAddress(mn, port));
        }
    }

    /**
     * Acts as an iterator, returning the address of each node in the list sequentially.
     * Once all the list has been iterated, null will be returned for each subsequent query.
     *
     * @return InetSocketAddress - The address/port of the next node.
     * @throws PeerDiscoveryException
     */
    @Nullable
    public InetSocketAddress getPeer() throws PeerDiscoveryException {
        try {
            return nextPeer();
        } catch (UnknownHostException e) {
            throw new PeerDiscoveryException(e);
        }
    }

    @Nullable
    private InetSocketAddress nextPeer() throws UnknownHostException, PeerDiscoveryException {
        if (masternodeSeeds == null || masternodeSeeds.isEmpty())
            throw new PeerDiscoveryException("No masternodes seeds configured; unable to find any peers");

        if (pnseedIndex >= masternodeSeeds.size()) return null;
        return masternodeSeeds.get(pnseedIndex++);
    }

    /**
     * Returns an array containing all the Dash nodes within the list.
     */
    @Override
    public InetSocketAddress[] getPeers(long services, long timeoutValue, TimeUnit timeoutUnit) throws PeerDiscoveryException {
        if (services != 0)
            throw new PeerDiscoveryException("Pre-determined peers cannot be filtered by services: " + services);
        try {
            return allPeers();
        } catch (UnknownHostException e) {
            throw new PeerDiscoveryException(e);
        }
    }

    private InetSocketAddress[] allPeers() throws UnknownHostException {
        return masternodeSeeds.toArray(new InetSocketAddress[0]);
    }

    @Override
    public void shutdown() {
    }
}
