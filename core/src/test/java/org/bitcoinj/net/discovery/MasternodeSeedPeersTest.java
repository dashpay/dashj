/*
 * Copyright 2021 Dash Core Group
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
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class MasternodeSeedPeersTest {
    private static final NetworkParameters TESTNET = TestNet3Params.get();

    @Test
    public void getPeer_one() throws Exception{
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(TESTNET);
        assertNotNull(seedPeers.getPeer());
    }
    
    @Test
    public void getPeer_all() throws Exception{
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(TESTNET);
        for (int i = 0; i < TESTNET.getDefaultMasternodeList().length; ++i) {
            assertNotNull("Failed on index: "+i, seedPeers.getPeer());
        }
        assertNull(seedPeers.getPeer());
    }
    
    @Test
    public void getPeers_length() throws Exception{
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(TESTNET);
        List<InetSocketAddress> addresses = seedPeers.getPeers(0, 0, TimeUnit.SECONDS);
        assertEquals(addresses.size(), TESTNET.getDefaultMasternodeList().length);
    }

    @Test
    public void getPeers_services() {
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(TESTNET);
        assertThrows(PeerDiscoveryException.class, () -> seedPeers.getPeers(VersionMessage.NODE_NETWORK, 0, TimeUnit.SECONDS));
    }

    @Test
    public void getPeers_empty() {
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(new String [0], TESTNET);
        assertThrows(PeerDiscoveryException.class, () -> seedPeers.getPeers(VersionMessage.NODE_NETWORK, 0, TimeUnit.SECONDS));
    }

    @Test
    public void testnetDefaultLists() {
        TestNet3Params params = TestNet3Params.get();
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(params);
        assertEquals(80, params.getDefaultMasternodeList().length);
        assertEquals(33, params.getDefaultHPMasternodeList().length);
        assertEquals(80, seedPeers.getSeedAddrs().length);
    }

    @Test
    public void devnetOneDefaultLists() {
        AbsintheDevNetParams params = AbsintheDevNetParams.get();
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(params);
        assertEquals(2, params.getDefaultMasternodeList().length);
        assertEquals(14, params.getDefaultHPMasternodeList().length);
        assertEquals(params.getDefaultMasternodeList().length + params.getDefaultHPMasternodeList().length, seedPeers.getSeedAddrs().length);
    }

    @Test
    public void devnetTwoDefaultLists() {
        OuzoDevNetParams params = OuzoDevNetParams.get();
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(params);
        assertEquals(5, params.getDefaultMasternodeList().length);
        assertEquals(15, params.getDefaultHPMasternodeList().length);
        assertEquals(params.getDefaultMasternodeList().length + params.getDefaultHPMasternodeList().length, seedPeers.getSeedAddrs().length);
    }
}
