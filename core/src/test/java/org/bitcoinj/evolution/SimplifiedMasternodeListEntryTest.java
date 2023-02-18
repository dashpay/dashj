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
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimplifiedMasternodeListEntryTest {
    NetworkParameters UNITTEST = UnitTestParams.get();

    @Test
    public void readBytesV2Test() {
        byte[] smle = Utils.HEX.decode("e7aef4f585df3def44b855219ae93d6e8cc49a8c96658c5cc0813c48f5384c33e2999069d702d61d852a74b1e07d6f58101e0352d84043e866ff7946bdf5987f00000000000000000000ffff7f0000012f3197fe8172fd3207d71125a053ff32266e11110c06c1184d5be0a8118d0131d6119b138ec4d0398e7eacc5e16a75f718ed796c3a4cab668936c1f6d0945a7b97d7c0fee7cf0101002caa114755a4648d422a5caa5c915597f8c733b8e146");
        SimplifiedMasternodeListEntry entry = new SimplifiedMasternodeListEntry(UNITTEST, smle, 0, NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion());
        BLSScheme.setLegacyDefault(entry.version == SimplifiedMasternodeListEntry.LEGACY_BLS_VERSION);
        assertEquals(2, entry.version);
        assertEquals(1, entry.type);
        assertEquals("334c38f5483c81c05c8c65968c9ac48c6e3de99a2155b844ef3ddf85f5f4aee7", entry.proRegTxHash.toString());
        assertEquals("7f98f5bd4679ff66e84340d852031e10586f7de0b1742a851dd602d7699099e2", entry.confirmedHash.toString());
        assertEquals(new MasternodeAddress("127.0.0.1", 12081), entry.service);
        assertEquals("yXPUGD63qih1Hzgxy7gY9LFKof2KqBf3hU", Address.fromPubKeyHash(UNITTEST, entry.keyIdVoting.getBytes()).toString());
        assertEquals(new BLSLazyPublicKey(UNITTEST, Utils.HEX.decode("97fe8172fd3207d71125a053ff32266e11110c06c1184d5be0a8118d0131d6119b138ec4d0398e7eacc5e16a75f718ed"), 0, false), entry.pubKeyOperator);
        assertEquals(KeyId.fromString("46e1b833c7f89755915caa5c2a428d64a4554711"), entry.platformNodeId);
        assertTrue(entry.isValid);
    }
}
