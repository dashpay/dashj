/*
 * Copyright 2026 Dash Core Group.
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

package org.bitcoinj.evolution;

import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExtNetInfoTest {
    private static final NetworkParameters UNITTEST = UnitTestParams.get();

    private static ExtNetInfo parse(String hex) {
        return new ExtNetInfo(UNITTEST, Utils.HEX.decode(hex), 0);
    }

    private static void assertRoundTrip(String hex) {
        ExtNetInfo info = parse(hex);
        assertEquals(hex.length() / 2, info.getMessageSize());
        assertEquals(hex, Utils.HEX.encode(info.serializeFresh()));
        assertEquals(hex, Utils.HEX.encode(info.duplicate().bitcoinSerialize()));
    }

    @Test
    public void serviceIPv4_bigEndianPort() {
        // version 1, 1 purpose: CORE_P2P -> [IPv4 1.2.3.4:9999]
        String hex = "01" + "01" + "00" + "01" + "01010401020304" + "270f";
        ExtNetInfo info = parse(hex);
        assertEquals(1, info.getVersion());
        assertEquals(new MasternodeAddress("1.2.3.4", 9999), info.getPrimaryService());
        assertRoundTrip(hex);
    }

    @Test
    public void multiPurpose_platformPorts() {
        String hex = "01" + "03" +
                "00" + "01" + "0101040a000001" + "270f" +   // CORE_P2P 10.0.0.1:9999
                "01" + "01" + "0101040a000001" + "6850" +   // PLATFORM_P2P 10.0.0.1:26704
                "02" + "01" + "0101040a000001" + "01bb";    // PLATFORM_HTTPS 10.0.0.1:443
        ExtNetInfo info = parse(hex);
        assertEquals(new MasternodeAddress("10.0.0.1", 9999), info.getPrimaryService());
        assertEquals(26704, info.getPlatformP2PPort());
        assertEquals(443, info.getPlatformHTTPPort());
        assertRoundTrip(hex);
    }

    @Test
    public void ipv6DomainAndInvalidEntries() {
        String hex = "01" + "01" + "00" + "04" +
                "01010401010101" + "0001" +                               // IPv4 1.1.1.1:1
                "010210000102030405060708090a0b0c0d0e0f" + "0002" +      // IPv6 :2
                "020c6e6f64652e6578616d706c65" + "0003" +                 // node.example:3
                "ff";                                                     // invalid
        ExtNetInfo info = parse(hex);
        List<NetInfoEntry> entries = info.getEntries(NetInfoPurpose.CORE_P2P);
        assertEquals(4, entries.size());
        assertEquals(NetInfoEntry.Network.IPV6, entries.get(1).getNetwork());
        assertEquals("node.example", entries.get(2).getHost());
        assertEquals(3, entries.get(2).getPort());
        assertTrue(entries.get(3).isInvalid());
        assertEquals(0, info.getPlatformHTTPPort());
        assertRoundTrip(hex);
    }

    @Test
    public void torV3IsNotAMasternodeAddress() {
        String hex = "01" + "01" + "00" + "01" + "010420" +
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f" + "270f";
        ExtNetInfo info = parse(hex);
        assertNull(info.getPrimaryService());
        assertRoundTrip(hex);
    }

    @Test
    public void emptyMap() {
        assertRoundTrip("0100");
        assertTrue(parse("0100").getEntries().isEmpty());
    }

    @Test
    public void unknownVersion_shortCircuits() {
        for (String version : new String[]{"00", "02", "ff"}) {
            // trailing bytes belong to the next field and must not be consumed
            ExtNetInfo info = new ExtNetInfo(UNITTEST, Utils.HEX.decode(version + "0300"), 0);
            assertEquals(1, info.getMessageSize());
            assertTrue(info.getEntries().isEmpty());
            assertEquals(version, Utils.HEX.encode(info.serializeFresh()));
        }
    }

    @Test
    public void constructedMatchesParsed() {
        Map<NetInfoPurpose, List<NetInfoEntry>> map = new LinkedHashMap<>();
        map.put(NetInfoPurpose.CORE_P2P, Arrays.asList(NetInfoEntry.service(new MasternodeAddress("10.0.0.1", 9999))));
        map.put(NetInfoPurpose.PLATFORM_HTTPS, Arrays.asList(NetInfoEntry.domain("evo.example.org", 443)));
        ExtNetInfo constructed = new ExtNetInfo(UNITTEST, ExtNetInfo.CURRENT_VERSION, map);
        byte[] bytes = constructed.bitcoinSerialize();
        assertEquals(bytes.length, constructed.getMessageSize());
        ExtNetInfo parsed = new ExtNetInfo(UNITTEST, bytes, 0);
        assertEquals(constructed, parsed);
        assertEquals(443, parsed.getPlatformHTTPPort());
    }

    @Test(expected = ProtocolException.class)
    public void torV2Rejected() {
        parse("01" + "01" + "00" + "01" + "01030a" + "00000000000000000000" + "270f");
    }

    @Test(expected = ProtocolException.class)
    public void unknownNetworkRejected() {
        parse("01" + "01" + "00" + "01" + "010704" + "01020304" + "270f");
    }

    @Test(expected = ProtocolException.class)
    public void wrongAddressLengthRejected() {
        parse("01" + "01" + "00" + "01" + "010110" + "01020304" + "270f");
    }

    @Test(expected = ProtocolException.class)
    public void unknownEntryTypeRejected() {
        parse("01" + "01" + "00" + "01" + "03");
    }

    @Test(expected = ProtocolException.class)
    public void unknownPurposeRejected() {
        parse("01" + "01" + "03" + "00");
    }

    @Test(expected = ProtocolException.class)
    public void duplicatePurposeRejected() {
        parse("01" + "02" + "0000" + "0000");
    }

    @Test(expected = ProtocolException.class)
    public void oversizedDomainRejected() {
        byte[] bytes = new byte[3 + 1 + 3 + 254 + 2];
        bytes[0] = 1; bytes[1] = 1; bytes[2] = 2; bytes[3] = 1; bytes[4] = 2;
        bytes[5] = (byte) 0xfd; bytes[6] = (byte) 254; bytes[7] = 0;   // varint 254
        new ExtNetInfo(UNITTEST, bytes, 0);
    }

    @Test(expected = ProtocolException.class)
    public void hugePurposeCountRejectedBeforeAllocating() {
        parse("01" + "ffffffffffffffff7f");
    }

    @Test(expected = ProtocolException.class)
    public void hugeEntryCountRejectedBeforeAllocating() {
        parse("01" + "01" + "00" + "feffffff7f");
    }

    @Test(expected = ProtocolException.class)
    public void truncatedRejected() {
        parse("01" + "01" + "00" + "01" + "010104010203");
    }
}
