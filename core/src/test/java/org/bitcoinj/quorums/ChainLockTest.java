/*
 * Copyright 2020 Dash Core Group
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

package org.bitcoinj.quorums;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.params.UnitTestParams;
import org.dashj.bls.BLSJniLibrary;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ChainLockTest {

    static {
        BLSJniLibrary.init();
    }

    UnitTestParams PARAMS = UnitTestParams.get();

    @Test
    public void chainLockMessageLegacyTest() throws IOException {
        BLSScheme.setLegacyDefault(true);
        byte [] chainLockMsg = Utils.HEX.decode("ea480100f4a5708c82f589e19dfe9e9cd1dbab57f74f27b24f0a3c765ba6e007000000000a43f1c3e5b3e8dbd670bca8d437dc25572f72d8e1e9be673e9ebbb606570307c3e5f5d073f7beb209dd7e0b8f96c751060ab3a7fb69a71d5ccab697b8cfa5a91038a6fecf76b7a827d75d17f01496302942aa5e2c7f4a48246efc8d3941bf6c");
        Sha256Hash expectedHash = Sha256Hash.wrap("3764ada6c32f09bb4f02295415b230657720f8be17d6fe046f0f8bf3db72b8e0");
        Sha256Hash expectedId = Sha256Hash.wrap("6639d0da4a746f7260968e54be1b14fce8c5429f51bfe8762b58aae294e0925d");

        ChainLockSignature clsig = new ChainLockSignature(PARAMS, chainLockMsg, true);

        // verify that the serialized chain lock signature matches original data
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(chainLockMsg.length);
        clsig.bitcoinSerialize(bos);
        assertArrayEquals(chainLockMsg, bos.toByteArray());

        // compare the hash and the id
        Sha256Hash hash = clsig.getHash();
        Sha256Hash id = clsig.getRequestId();
        assertEquals(expectedHash, hash);
        assertEquals(expectedId, id);
        assertEquals(84202, clsig.getHeight());
        assertEquals(Sha256Hash.wrap("0000000007e0a65b763c0a4fb2274ff757abdbd19c9efe9de189f5828c70a5f4"), clsig.getBlockHash());

        // round trip
        byte [] serialized = clsig.bitcoinSerialize();
        assertArrayEquals(chainLockMsg, serialized);

        ChainLockSignature fromConstructor = new ChainLockSignature(84202, Sha256Hash.wrap("0000000007e0a65b763c0a4fb2274ff757abdbd19c9efe9de189f5828c70a5f4"),
                new BLSSignature(Utils.HEX.decode("0a43f1c3e5b3e8dbd670bca8d437dc25572f72d8e1e9be673e9ebbb606570307c3e5f5d073f7beb209dd7e0b8f96c751060ab3a7fb69a71d5ccab697b8cfa5a91038a6fecf76b7a827d75d17f01496302942aa5e2c7f4a48246efc8d3941bf6c"), true));
        assertEquals(clsig, fromConstructor);
    }

    // From V19 Devnet
    // 237b00000b170f05a9d8cdb0373502cc5c637c7135e670200aefaa14fcce0252ad010000b8156fdfcd4700967ef5c487fb7585d9b45e7d3ee42865f4b21bbeac41686b07423bc74befc6af701fe726a81254722716cc575aafe7751119e4222b13d32fec5446ab37d28f0d0bcc078d6e48543409bb6ed64900f4495f056e8cab1a9d2358

    @Test
    public void chainLockMessageBasicTest() throws IOException {
        BLSScheme.setLegacyDefault(false);
        byte [] chainLockMsg = Utils.HEX.decode("237b00000b170f05a9d8cdb0373502cc5c637c7135e670200aefaa14fcce0252ad010000b8156fdfcd4700967ef5c487fb7585d9b45e7d3ee42865f4b21bbeac41686b07423bc74befc6af701fe726a81254722716cc575aafe7751119e4222b13d32fec5446ab37d28f0d0bcc078d6e48543409bb6ed64900f4495f056e8cab1a9d2358");
        Sha256Hash expectedHash = Sha256Hash.wrap("3566bc6475cbcc3e9f231f4ed03764de41faaeaee3c77c033db6537964ed26e3");
        Sha256Hash expectedId = Sha256Hash.wrap("6b244be04bec653cacfd71fb9ffd6ecb7cd2c3b93e0af3afc325114c8b1e89af");

        ChainLockSignature clsig = new ChainLockSignature(PARAMS, chainLockMsg, false);

        // verify that the serialized chain lock signature matches original data
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(chainLockMsg.length);
        clsig.bitcoinSerialize(bos);
        assertArrayEquals(chainLockMsg, bos.toByteArray());

        // compare the hash and the id
        Sha256Hash hash = clsig.getHash();
        Sha256Hash id = clsig.getRequestId();
        assertEquals(expectedHash, hash);
        assertEquals(expectedId, id);
        assertEquals(31523, clsig.getHeight());
        assertEquals(Sha256Hash.wrap("000001ad5202cefc14aaef0a2070e635717c635ccc023537b0cdd8a9050f170b"), clsig.getBlockHash());

        // round trip
        byte [] serialized = clsig.bitcoinSerialize();
        assertArrayEquals(chainLockMsg, serialized);

        ChainLockSignature fromConstructor = new ChainLockSignature(31523, Sha256Hash.wrap("000001ad5202cefc14aaef0a2070e635717c635ccc023537b0cdd8a9050f170b"),
                new BLSSignature(Utils.HEX.decode("b8156fdfcd4700967ef5c487fb7585d9b45e7d3ee42865f4b21bbeac41686b07423bc74befc6af701fe726a81254722716cc575aafe7751119e4222b13d32fec5446ab37d28f0d0bcc078d6e48543409bb6ed64900f4495f056e8cab1a9d2358")));
        assertEquals(clsig, fromConstructor);
    }
}
