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
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
}
