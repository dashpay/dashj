package org.bitcoinj.quorums;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class ChainLockTest {

    UnitTestParams PARAMS = UnitTestParams.get();

    @Test
    public void chainLockMessageTest() throws IOException {
        byte [] chainLockMsg = Utils.HEX.decode("ea480100f4a5708c82f589e19dfe9e9cd1dbab57f74f27b24f0a3c765ba6e007000000000a43f1c3e5b3e8dbd670bca8d437dc25572f72d8e1e9be673e9ebbb606570307c3e5f5d073f7beb209dd7e0b8f96c751060ab3a7fb69a71d5ccab697b8cfa5a91038a6fecf76b7a827d75d17f01496302942aa5e2c7f4a48246efc8d3941bf6c");
        Sha256Hash expectedHash = Sha256Hash.wrap("3764ada6c32f09bb4f02295415b230657720f8be17d6fe046f0f8bf3db72b8e0");
        Sha256Hash expectedId = Sha256Hash.wrap("6639d0da4a746f7260968e54be1b14fce8c5429f51bfe8762b58aae294e0925d");

        ChainLockSignature clsig = new ChainLockSignature(PARAMS, chainLockMsg);

        //verify that the serialized chain lock signature matches original data
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(chainLockMsg.length);
        clsig.bitcoinSerialize(bos);
        assertArrayEquals(chainLockMsg, bos.toByteArray());

        //compare the hash and the id
        Sha256Hash hash = clsig.getHash();
        Sha256Hash id = clsig.getRequestId();
        assertEquals(expectedHash, hash);
        assertEquals(expectedId, id);
    }
}
