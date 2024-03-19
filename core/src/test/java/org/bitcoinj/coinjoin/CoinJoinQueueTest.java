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

package org.bitcoinj.coinjoin;

import org.bitcoinj.core.MasternodeSignature;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.params.UnitTestParams;
import org.bouncycastle.util.encoders.Hex;
import org.dashj.bls.BLSJniLibrary;
import org.dashj.bls.PrivateKey;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoinJoinQueueTest {
    static NetworkParameters PARAMS = UnitTestParams.get();

    // TODO: Core 19 updates will require changing this test

    @Before
    public void setup() {
        BLSJniLibrary.init();
        BLSScheme.setLegacyDefault(false);
    }

    @Test
    public void queueTest() {
        byte[] payload = Utils.HEX.decode("0800000036c6298f595939395ec930f936452726f33a311a79b2abe290ae01aad020011652498465000000000060a4f1ebf98b3b2df98c6375d391c4aba667edbaccb31610a8ded1eaba92c87ce59d2dcbea67fd59d212edd87553fbbeac0041bc514782b3ae5184f6d194c3dbdd8f94b5ce5e0e358aed3557b18188d51cbbcda80fba2ff7dabb808029ba255431");

        CoinJoinQueue queueFromHex = new CoinJoinQueue(PARAMS, payload, PARAMS.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_BASIC));
        assertEquals(8, queueFromHex.getDenomination());
        assertEquals(Sha256Hash.wrap("160120d0aa01ae90e2abb2791a313af326274536f930c95e393959598f29c636"), queueFromHex.getProTxHash());
        assertEquals(1703168338, queueFromHex.getTime());
        assertFalse(queueFromHex.isReady());

        CoinJoinQueue queueFromCtor = new CoinJoinQueue(
                PARAMS,
                8,
                Sha256Hash.wrap("160120d0aa01ae90e2abb2791a313af326274536f930c95e393959598f29c636"),
                1703168338,
                false,
                new MasternodeSignature(Utils.HEX.decode("a4f1ebf98b3b2df98c6375d391c4aba667edbaccb31610a8ded1eaba92c87ce59d2dcbea67fd59d212edd87553fbbeac0041bc514782b3ae5184f6d194c3dbdd8f94b5ce5e0e358aed3557b18188d51cbbcda80fba2ff7dabb808029ba255431"))
        );
        assertEquals(queueFromHex, queueFromCtor);
        assertArrayEquals(payload, queueFromCtor.bitcoinSerialize());
        BLSPublicKey masternodeOperatorKey = new BLSPublicKey(Utils.HEX.decode("066d57a6451b7800c1c2a6c6e04fe73ec2e1c95e492bacae760ad2f79ca3c30727ec9bf0daea43c08ff1ad6c2cf07612"), true);
        assertTrue(queueFromCtor.checkSignature(masternodeOperatorKey));
        assertFalse(queueFromCtor.isTried());
        queueFromCtor.setTried(true);
        assertTrue(queueFromCtor.isTried());
    }

    /*
        Test using a masternode with a basic operator key and a basic signature
     */
    @Test
    public void queueWithBasicMasternodePass() {
        byte[] payload = Utils.HEX.decode("08000000e6a70ae4131ee990a4c014421685e9a3f4ad1197f8e4f247eb4121905af74a3a17779865000000000160a9852a8fe5d7d4c1be091adbcc226d06b5446d0b3cf75384c945068eab7ede51cfc64666f0d2e6bee07b5b463b8016e209ea16c3a9faa915ddc1037aa59d5c74f403f8b0da263a97a18f81134be41f55b1b59f16f88c8e44c3801bd236f96444");
        byte[] masternodePublicKeyBytes = Utils.HEX.decode("a1a2ce03d33508fa6d0d0d106b405824a5a583ce109e1e5513c76d3c70aac13b49ed78980ac7fb0836d391d34c453a5d");
        CoinJoinQueue queueFromHex = new CoinJoinQueue(PARAMS, payload, PARAMS.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_BASIC));
        BLSPublicKey masternodePublicKey = new BLSPublicKey(masternodePublicKeyBytes, false);

        assertTrue(queueFromHex.checkSignature(masternodePublicKey));
        assertEquals(8, queueFromHex.getDenomination());
        assertArrayEquals(Hex.decode("a9852a8fe5d7d4c1be091adbcc226d06b5446d0b3cf75384c945068eab7ede51cfc64666f0d2e6bee07b5b463b8016e209ea16c3a9faa915ddc1037aa59d5c74f403f8b0da263a97a18f81134be41f55b1b59f16f88c8e44c3801bd236f96444"), queueFromHex.getSignature().getBytes());
        assertFalse(queueFromHex.isTried());
        assertTrue(queueFromHex.isTimeOutOfBounds());
    }

    /*
        Test using a masternode with a legacy operator key, but a basic signature
     */
    @Test
    public void queueWithLegacyMasternodeTest() {
        byte[] payload = Utils.HEX.decode("04000000d7c0742ff5feecdf9ba16028cdd20ce6d740e8134e726967b472e57c4e80e50a888698650000000001609477d10b679ac351aed7af38747b513847cb60b422aca6982f1ae9c1dc8a07e7a29c9d97b53cfdced846997f045079510924bbe9baf3a074d1ee495c36fbc968d1a632a9189297e5e9fd820343448ed3b559d39b039b13fb6499216fa95aa2bc");
        byte[] masternodePublicKeyBytes = Utils.HEX.decode("980d9cbfe63468e27b06bb20224f9f5a443a3a8d31fd4e7d52412121c5b7b2f6036089eae3dbbf36a1a7fa2fc1de654c");
        CoinJoinQueue queueFromHex = new CoinJoinQueue(PARAMS, payload, PARAMS.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_BASIC));
        BLSPublicKey masternodePublicKey = new BLSPublicKey(masternodePublicKeyBytes, true);

        assertTrue(queueFromHex.checkSignature(masternodePublicKey));
        assertEquals(4, queueFromHex.getDenomination());
        assertArrayEquals(Hex.decode("9477d10b679ac351aed7af38747b513847cb60b422aca6982f1ae9c1dc8a07e7a29c9d97b53cfdced846997f045079510924bbe9baf3a074d1ee495c36fbc968d1a632a9189297e5e9fd820343448ed3b559d39b039b13fb6499216fa95aa2bc"), queueFromHex.getSignature().getBytes());
        assertFalse(queueFromHex.isTried());
        assertTrue(queueFromHex.isTimeOutOfBounds());
    }

    @Test
    public void signTest() {
        CoinJoinQueue dsq = new CoinJoinQueue(
                PARAMS,
                8,
                Sha256Hash.wrap("160120d0aa01ae90e2abb2791a313af326274536f930c95e393959598f29c636"),
                1703168338,
                false
        );
        BLSSecretKey masternodeOperatorKey = new BLSSecretKey(PrivateKey.randomPrivateKey());

        dsq.sign(masternodeOperatorKey);

        assertTrue(dsq.checkSignature(masternodeOperatorKey.getPublicKey()));
    }
}
