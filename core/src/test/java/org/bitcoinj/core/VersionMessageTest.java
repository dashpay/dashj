/*
 * Copyright 2012 Matt Corallo
 * Copyright 2014 Andreas Schildbach
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

package org.bitcoinj.core;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.InetAddress;

import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

public class VersionMessageTest {
    private static final NetworkParameters UNITTEST = UnitTestParams.get();

    @Test
    public void decode_noRelay_bestHeight_subVer() throws Exception {
        // Test that we can decode version messages which miss data which some old nodes may not include
        String hex = "4c12010000000000000000003334a85500000000000000000000000000000000000000000000ffff7f000001479d000000000000000000000000000000000000ffff7f000001479d00000000000000000f2f626974636f696e6a3a302e31332f0004000000";
        VersionMessage ver = new VersionMessage(UNITTEST, HEX.decode(hex));
        assertFalse(ver.relayTxesBeforeFilter);
        assertEquals(1024, ver.bestHeight);
        assertEquals("/bitcoinj:0.13/", ver.subVer);
    }

    @Test
    public void decode_relay_bestHeight_subVer() throws Exception {
        String hex = "461201000000000000000000a634a85500000000000000000000000000000000000000000000ffff7f000001479d000000000000000000000000000000000000ffff7f000001479d00000000000000000f2f626974636f696e6a3a302e31332f0004000001";
        VersionMessage ver = new VersionMessage(UNITTEST, HEX.decode(hex));
        assertTrue(ver.relayTxesBeforeFilter);
        assertEquals(1024, ver.bestHeight);
        assertEquals("/bitcoinj:0.13/", ver.subVer);
    }

    @Test
    public void decode_relay_noBestHeight_subVer() throws Exception {
        String hex = "461201000000000000000000c334a85500000000000000000000000000000000000000000000ffff7f000001479d000000000000000000000000000000000000ffff7f000001479d00000000000000000f2f626974636f696e6a3a302e31332f0000000001";
        VersionMessage ver = new VersionMessage(UNITTEST, HEX.decode(hex));
        assertTrue(ver.relayTxesBeforeFilter);
        assertEquals(0, ver.bestHeight);
        assertEquals("/bitcoinj:0.13/", ver.subVer);
    }

    @Test
    public void decode_relay_noBestHeight_noSubVer() throws Exception {
        String hex = "00000000000000000000000048e5e95000000000000000000000000000000000000000000000ffff7f000001479d000000000000000000000000000000000000ffff7f000001479d0000000000000000";
        VersionMessage ver = new VersionMessage(UNITTEST, HEX.decode(hex));
        assertTrue(ver.relayTxesBeforeFilter);
        assertEquals(0, ver.bestHeight);
        assertEquals("", ver.subVer);
    }

    @Test
    public void roundTrip_ipv4_currentProtocolVersion() throws Exception {
        VersionMessage ver = new VersionMessage(UNITTEST, 1234);
        ver.time = 23456;
        ver.subVer = "/dashj/";
        ver.clientVersion = NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion();
        ver.localServices = 1;
        ver.fromAddr = new PeerAddress(UNITTEST, InetAddress.getByName("1.2.3.4"), 3888);
        ver.fromAddr.setParent(ver);
        ver.receivingAddr = new PeerAddress(UNITTEST, InetAddress.getByName("4.3.2.1"), 8333);
        ver.receivingAddr.setParent(ver);
        byte[] serialized = ver.bitcoinSerialize();
        VersionMessage ver2 = new VersionMessage(UNITTEST, serialized);
        assertEquals(1234, ver2.bestHeight);
        assertEquals(23456, ver2.time);
        assertEquals("/dashj/", ver2.subVer);
        assertEquals(NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion(), ver2.clientVersion);
        assertEquals(1, ver2.localServices);
        assertEquals("1.2.3.4", ver2.fromAddr.getAddr().getHostAddress());
        assertEquals(3888, ver2.fromAddr.getPort());
        assertEquals("4.3.2.1", ver2.receivingAddr.getAddr().getHostAddress());
        assertEquals(8333, ver2.receivingAddr.getPort());
    }

    @Test
    public void roundTrip_ipv4_ancientProtocolVersion() throws Exception {
        VersionMessage ver = new VersionMessage(UNITTEST, 0);
        ver.time = 23456;
        ver.clientVersion = 0;
        ver.localServices = 1;
        ver.receivingAddr = new PeerAddress(UNITTEST, InetAddress.getByName("4.3.2.1"), 8333);
        ver.receivingAddr.setParent(ver);
        byte[] serialized = ver.bitcoinSerialize();
        VersionMessage ver2 = new VersionMessage(UNITTEST, serialized);
        assertEquals(23456, ver2.time);
        assertEquals(0, ver2.clientVersion);
        assertEquals(1, ver2.localServices);
        assertEquals("4.3.2.1", ver2.receivingAddr.getAddr().getHostAddress());
        assertEquals(8333, ver2.receivingAddr.getPort());
    }

    @Test
    public void roundTrip_ipv6_currentProtocolVersion() throws Exception {
        VersionMessage ver = new VersionMessage(UNITTEST, 1234);
        ver.time = 23456;
        ver.subVer = "/dashj/";
        ver.clientVersion = NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion();
        ver.localServices = 1;
        ver.fromAddr = new PeerAddress(UNITTEST, InetAddress.getByName("2001:db8:85a3:0:0:8a2e:370:7334"), 3888);
        ver.fromAddr.setParent(ver);
        ver.receivingAddr = new PeerAddress(UNITTEST, InetAddress.getByName("2002:db8:85a3:0:0:8a2e:370:7335"), 8333);
        ver.receivingAddr.setParent(ver);
        byte[] serialized = ver.bitcoinSerialize();
        VersionMessage ver2 = new VersionMessage(UNITTEST, serialized);
        assertEquals(1234, ver2.bestHeight);
        assertEquals(23456, ver2.time);
        assertEquals("/dashj/", ver2.subVer);
        assertEquals(NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion(), ver2.clientVersion);
        assertEquals(1, ver2.localServices);
        assertEquals("2001:db8:85a3:0:0:8a2e:370:7334", ver2.fromAddr.getAddr().getHostAddress());
        assertEquals(3888, ver2.fromAddr.getPort());
        assertEquals("2002:db8:85a3:0:0:8a2e:370:7335", ver2.receivingAddr.getAddr().getHostAddress());
        assertEquals(8333, ver2.receivingAddr.getPort());
    }

    @Test
    public void roundTrip_ipv6_ancientProtocolVersion() throws Exception {
        VersionMessage ver = new VersionMessage(UNITTEST, 1234);
        ver.time = 23456;
        ver.subVer = "/dashj/";
        ver.clientVersion = 0;
        ver.localServices = 1;
        ver.fromAddr = new PeerAddress(UNITTEST, InetAddress.getByName("2001:db8:85a3:0:0:8a2e:370:7334"), 3888);
        ver.fromAddr.setParent(ver);
        ver.receivingAddr = new PeerAddress(UNITTEST, InetAddress.getByName("2002:db8:85a3:0:0:8a2e:370:7335"), 8333);
        ver.receivingAddr.setParent(ver);
        byte[] serialized = ver.bitcoinSerialize();
        VersionMessage ver2 = new VersionMessage(UNITTEST, serialized);
        assertEquals(23456, ver2.time);
        assertEquals(0, ver2.clientVersion);
        assertEquals(1, ver2.localServices);
        assertEquals("2002:db8:85a3:0:0:8a2e:370:7335", ver2.receivingAddr.getAddr().getHostAddress());
        assertEquals(8333, ver2.receivingAddr.getPort());
    }
}
