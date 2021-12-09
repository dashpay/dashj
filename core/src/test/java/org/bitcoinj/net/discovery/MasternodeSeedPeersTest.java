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
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;

public class MasternodeSeedPeersTest {
    private static final NetworkParameters TESTNET = TestNet3Params.get();

    @Test
    public void getPeer_one() throws Exception{
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(TESTNET);
        assertThat(seedPeers.getPeer(), notNullValue());
    }
    
    @Test
    public void getPeer_all() throws Exception{
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(TESTNET);
        for (int i = 0; i < TESTNET.getDefaultMasternodeList().length; ++i) {
            assertThat("Failed on index: "+i, seedPeers.getPeer(), notNullValue());
        }
        assertThat(seedPeers.getPeer(), equalTo(null));
    }
    
    @Test
    public void getPeers_length() throws Exception{
        MasternodeSeedPeers seedPeers = new MasternodeSeedPeers(TESTNET);
        InetSocketAddress[] addresses = seedPeers.getPeers(0, 0, TimeUnit.SECONDS);
        assertThat(addresses.length, equalTo(TESTNET.getDefaultMasternodeList().length));
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
}
