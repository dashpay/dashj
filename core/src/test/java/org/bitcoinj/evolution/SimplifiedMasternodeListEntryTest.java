/*
 * Copyright 2023 Dash Core Group
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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SimplifiedMasternodeListEntryTest {
    NetworkParameters UNITTEST = UnitTestParams.get();

    @Test
    public void readBytesV1Test() {
        byte[] smle = Utils.HEX.decode("318c32ec1598fa8f38dc76e28d15c00c20553c7020161a4c64742524a18465c39b2d789571a3b223a5365fbde69b3fe7143c4f9ebc6293afb84d340f0000000000000000000000000000ffff23a3e2204e1f08b4c1a8b9c1402ea84afe7c47f7e98d657df873b9747a0e4a497120ec62c81f314ad91a6f3384648e7e60f2734554f7f4c0fe75eec22907d6b043edb0df74ccc7b85a4500");
        SimplifiedMasternodeListEntry entry = new SimplifiedMasternodeListEntry(UNITTEST, smle, 0, NetworkParameters.ProtocolVersion.BLS_LEGACY.getBitcoinProtocolVersion());
        assertEquals(1, entry.version);
        assertEquals(0, entry.getType());
        assertEquals("c36584a1242574644c1a1620703c55200cc0158de276dc388ffa9815ec328c31", entry.proRegTxHash.toString());
        assertEquals("000000000f344db8af9362bc9e4f3c14e73f9be6bd5f36a523b2a37195782d9b", entry.confirmedHash.toString());
        assertEquals(new MasternodeAddress("35.163.226.32", 19999), entry.service);
        assertEquals("yidavU3B2BUNzaUv3gW6nmV4ojLNwPeazt", Address.fromPubKeyHash(UNITTEST, entry.keyIdVoting.getBytes()).toString());
        assertEquals(new BLSLazyPublicKey(UNITTEST, Utils.HEX.decode("08b4c1a8b9c1402ea84afe7c47f7e98d657df873b9747a0e4a497120ec62c81f314ad91a6f3384648e7e60f2734554f7"), 0, false), entry.pubKeyOperator);
        assertFalse(entry.isValid); // this masternode is not valid
        assertFalse(entry.isHPMN());
        assertEquals(Sha256Hash.wrap("a7fc065ab65f453c4b57c597467f4d126188d5807f08cfab6b1f6d52e30e067e"), entry.getHash());
    }

    @Test
    public void readBytesV2Test() {
        byte[] smle = Utils.HEX.decode("0200e7aef4f585df3def44b855219ae93d6e8cc49a8c96658c5cc0813c48f5384c33e2999069d702d61d852a74b1e07d6f58101e0352d84043e866ff7946bdf5987f00000000000000000000ffff7f0000012f3197fe8172fd3207d71125a053ff32266e11110c06c1184d5be0a8118d0131d6119b138ec4d0398e7eacc5e16a75f718ed796c3a4cab668936c1f6d0945a7b97d7c0fee7cf0101002caa114755a4648d422a5caa5c915597f8c733b8e146");
        SimplifiedMasternodeListEntry entry = new SimplifiedMasternodeListEntry(UNITTEST, smle, 0, NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion());
        assertEquals(2, entry.version);
        assertEquals(1, entry.getType());
        assertEquals("334c38f5483c81c05c8c65968c9ac48c6e3de99a2155b844ef3ddf85f5f4aee7", entry.proRegTxHash.toString());
        assertEquals("7f98f5bd4679ff66e84340d852031e10586f7de0b1742a851dd602d7699099e2", entry.confirmedHash.toString());
        assertEquals(new MasternodeAddress("127.0.0.1", 12081), entry.service);
        assertEquals("yXPUGD63qih1Hzgxy7gY9LFKof2KqBf3hU", Address.fromPubKeyHash(UNITTEST, entry.keyIdVoting.getBytes()).toString());
        assertEquals(new BLSLazyPublicKey(UNITTEST, Utils.HEX.decode("97fe8172fd3207d71125a053ff32266e11110c06c1184d5be0a8118d0131d6119b138ec4d0398e7eacc5e16a75f718ed"), 0, false), entry.pubKeyOperator);
        assertEquals(KeyId.fromString("46e1b833c7f89755915caa5c2a428d64a4554711"), entry.getPlatformNodeId());
        assertEquals(43564, entry.getPlatformHTTPPort());
        assertTrue(entry.isHPMN());
        assertTrue(entry.isValid);
    }

    // the v2 Evo entry from readBytesV2Test, split into its fields
    private static final String V2_EVO_ENTRY = "0200e7aef4f585df3def44b855219ae93d6e8cc49a8c96658c5cc0813c48f5384c33e2999069d702d61d852a74b1e07d6f58101e0352d84043e866ff7946bdf5987f00000000000000000000ffff7f0000012f3197fe8172fd3207d71125a053ff32266e11110c06c1184d5be0a8118d0131d6119b138ec4d0398e7eacc5e16a75f718ed796c3a4cab668936c1f6d0945a7b97d7c0fee7cf0101002caa114755a4648d422a5caa5c915597f8c733b8e146";
    // proRegTxHash + confirmedHash
    private static final String HASHES = V2_EVO_ENTRY.substring(4, 4 + 128);
    // pubKeyOperator + keyIDVoting + isValid
    private static final String KEYS_AND_VALID = V2_EVO_ENTRY.substring(4 + 128 + 36, 4 + 128 + 36 + 138);
    private static final String PLATFORM_NODE_ID = "46e1b833c7f89755915caa5c2a428d64a4554711";
    private static final String PLATFORM_NODE_ID_BYTES = V2_EVO_ENTRY.substring(V2_EVO_ENTRY.length() - 40);

    // CORE_P2P 127.0.0.1:12081, PLATFORM_P2P 127.0.0.1:36656, PLATFORM_HTTPS 127.0.0.1:43564
    private static final String EVO_NET_INFO = "01" + "03" +
            "00" + "01" + "0101047f000001" + "2f31" +
            "01" + "01" + "0101047f000001" + "8f30" +
            "02" + "01" + "0101047f000001" + "aa2c";
    // CORE_P2P 35.163.226.32:19999
    private static final String REGULAR_NET_INFO = "01" + "01" + "00" + "01" + "01010423a3e220" + "4e1f";

    private static byte[] serializeFresh(SimplifiedMasternodeListEntry entry) throws IOException {
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
        entry.bitcoinSerializeToStream(bos);
        return bos.toByteArray();
    }

    @Test
    public void readBytesV3EvoTest() throws IOException {
        // no inline platformHTTPPort at version 3, only the type and the platform node id
        String withoutVersion = HASHES + EVO_NET_INFO + KEYS_AND_VALID + "0100" + PLATFORM_NODE_ID_BYTES;
        String hex = "0300" + withoutVersion;
        byte[] bytes = Utils.HEX.decode(hex + "deadbeef"); // trailing bytes belong to the next entry

        SimplifiedMasternodeListEntry entry = new SimplifiedMasternodeListEntry(UNITTEST, bytes, 0, NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion());
        assertEquals(hex.length() / 2, entry.getMessageSize());
        assertEquals(3, entry.getVersion());
        assertEquals("334c38f5483c81c05c8c65968c9ac48c6e3de99a2155b844ef3ddf85f5f4aee7", entry.proRegTxHash.toString());
        assertEquals(new MasternodeAddress("127.0.0.1", 12081), entry.getService());
        assertNotNull(entry.getNetInfo());
        assertEquals(36656, entry.getNetInfo().getPlatformP2PPort());
        assertEquals(43564, entry.getPlatformHTTPPort());
        assertEquals(KeyId.fromString(PLATFORM_NODE_ID), entry.getPlatformNodeId());
        assertEquals("yXPUGD63qih1Hzgxy7gY9LFKof2KqBf3hU", Address.fromPubKeyHash(UNITTEST, entry.keyIdVoting.getBytes()).toString());
        assertTrue(entry.isHPMN());
        assertTrue(entry.isValid());

        assertEquals(hex, Utils.HEX.encode(serializeFresh(entry)));
        // the SML leaf hash covers everything except the version
        assertEquals(Sha256Hash.wrapReversed(Sha256Hash.hashTwice(Utils.HEX.decode(withoutVersion))), entry.getHash());

        SimplifiedMasternodeListEntry constructed = new SimplifiedMasternodeListEntry(UNITTEST, (short) 3, 1,
                entry.proRegTxHash, entry.confirmedHash, entry.getNetInfo(), entry.keyIdVoting, entry.pubKeyOperator,
                entry.getPlatformNodeId(), true);
        assertEquals(hex, Utils.HEX.encode(constructed.bitcoinSerialize()));
        assertEquals(hex.length() / 2, constructed.getMessageSize());
        assertEquals(43564, constructed.getPlatformHTTPPort());

        SimplifiedMasternodeListEntry copy = new SimplifiedMasternodeListEntry(UNITTEST, entry);
        assertEquals(hex, Utils.HEX.encode(serializeFresh(copy)));
        assertEquals(entry.getHash(), copy.getHash());
    }

    @Test
    public void readBytesV3RegularTest() throws IOException {
        String hex = "0300" + HASHES + REGULAR_NET_INFO + KEYS_AND_VALID + "0000";
        SimplifiedMasternodeListEntry entry = new SimplifiedMasternodeListEntry(UNITTEST, Utils.HEX.decode(hex), 0, NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion());
        assertEquals(hex.length() / 2, entry.getMessageSize());
        assertEquals(new MasternodeAddress("35.163.226.32", 19999), entry.getService());
        assertFalse(entry.isHPMN());
        assertEquals(0, entry.getPlatformHTTPPort());
        assertNull(entry.getPlatformNodeId());
        assertEquals(hex, Utils.HEX.encode(serializeFresh(entry)));
    }

    @Test
    public void readBytesV3WithoutIPv4CoreAddress() throws IOException {
        // an entry whose CORE_P2P list is empty still parses; the service is the unroutable default
        String hex = "0300" + HASHES + "0100" + KEYS_AND_VALID + "0000";
        SimplifiedMasternodeListEntry entry = new SimplifiedMasternodeListEntry(UNITTEST, Utils.HEX.decode(hex), 0, NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion());
        assertNotNull(entry.getService());
        assertEquals(0, entry.getService().getPort());
        assertEquals(hex, Utils.HEX.encode(serializeFresh(entry)));
    }

    @Test
    public void v3EntriesBackToBack() {
        // entries carry no length prefix, so a mis-sized v3 entry would corrupt the one after it
        String evo = "0300" + HASHES + EVO_NET_INFO + KEYS_AND_VALID + "0100" + PLATFORM_NODE_ID_BYTES;
        String regular = "0300" + HASHES + REGULAR_NET_INFO + KEYS_AND_VALID + "0000";
        byte[] bytes = Utils.HEX.decode(evo + regular + V2_EVO_ENTRY);
        int protocol = NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion();
        int offset = 0;
        SimplifiedMasternodeListEntry first = new SimplifiedMasternodeListEntry(UNITTEST, bytes, offset, protocol);
        offset += first.getMessageSize();
        SimplifiedMasternodeListEntry second = new SimplifiedMasternodeListEntry(UNITTEST, bytes, offset, protocol);
        offset += second.getMessageSize();
        SimplifiedMasternodeListEntry third = new SimplifiedMasternodeListEntry(UNITTEST, bytes, offset, protocol);
        offset += third.getMessageSize();
        assertEquals(bytes.length, offset);
        assertEquals(new MasternodeAddress("35.163.226.32", 19999), second.getService());
        assertEquals(2, third.getVersion());
        assertEquals(43564, third.getPlatformHTTPPort());
        assertNull(third.getNetInfo());
    }

    @Test
    public void v2EntryStillByteIdentical() throws IOException {
        SimplifiedMasternodeListEntry entry = new SimplifiedMasternodeListEntry(UNITTEST, Utils.HEX.decode(V2_EVO_ENTRY), 0, NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion());
        assertEquals(V2_EVO_ENTRY, Utils.HEX.encode(serializeFresh(entry)));
        assertNull(entry.getNetInfo());
    }
}
