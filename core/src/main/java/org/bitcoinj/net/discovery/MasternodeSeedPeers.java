/*
 * Copyright 2011 Micheal Swiggs
 * Copyrigth 2021 Eric Britten
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

package org.bitcoinj.net.discovery;

import org.bitcoinj.core.NetworkParameters;

import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * MasternodeSeedPeers stores a pre-determined list of Dash node addresses. These nodes are selected based on being
 * masternodes on the network. The intention is to be a last resort way of finding a connection
 * to the network, in case other systems fail or are not available.
 */
public class MasternodeSeedPeers implements PeerDiscovery {
    private static final int ENOUGH_MASTERNODES = 20;
    private final NetworkParameters params;
    private final String[] seedAddrs;
    private int pnseedIndex;

    // if there are more than 20 masternodes, then don't use HP masternodes in peer discovery
    private static String[] mergeArrays(String[] masternodeArray, String[] hpMasternodeArray) {
        if (masternodeArray.length > ENOUGH_MASTERNODES || hpMasternodeArray.length == 0) {
            return masternodeArray;
        } else {
            String[] result = Arrays.copyOf(masternodeArray, masternodeArray.length + hpMasternodeArray.length);
            System.arraycopy(hpMasternodeArray, 0, result, masternodeArray.length, hpMasternodeArray.length);
            return result;
        }
    }
    /**
     * Supports finding peers by IP addresses
     *
     * @param params Network parameters to be used for port information.
     */
    public MasternodeSeedPeers(NetworkParameters params) {
        this(mergeArrays(params.getDefaultMasternodeList(), params.getDefaultHPMasternodeList()), params);
    }

    /**
     * Supports finding peers by IP addresses
     *
     * @param seedAddrs IP addresses for seed addresses.
     * @param params Network parameters to be used for port information.
     */
    public MasternodeSeedPeers(String[] seedAddrs, NetworkParameters params) {
        this.seedAddrs = seedAddrs;
        this.params = params;
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
        if (seedAddrs == null || seedAddrs.length == 0)
            throw new PeerDiscoveryException("No IP address seeds configured; unable to find any peers");

        if (pnseedIndex >= seedAddrs.length) return null;
        return new InetSocketAddress(convertAddress(seedAddrs[pnseedIndex++]),
                params.getPort());
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
        InetSocketAddress[] addresses = new InetSocketAddress[seedAddrs.length];
        for (int i = 0; i < seedAddrs.length; ++i) {
            addresses[i] = new InetSocketAddress(convertAddress(seedAddrs[i]), params.getPort());
        }
        return addresses;
    }

    private InetAddress convertAddress(String seed) throws UnknownHostException {
        return InetAddress.getByName(seed);
    }

    public String[] getSeedAddrs() {
        return seedAddrs;
    }

    @Override
    public void shutdown() {
    }
}
