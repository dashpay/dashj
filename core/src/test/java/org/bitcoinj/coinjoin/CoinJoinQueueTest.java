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
