package org.dashj.quorums;

import org.dashj.core.Sha256Hash;
import org.dashj.core.UnsafeByteArrayOutputStream;
import org.dashj.core.Utils;
import org.dashj.params.UnitTestParams;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class InstantSendLockTest {

    UnitTestParams PARAMS = UnitTestParams.get();

    @Test
    public void instantSendLockMessageTest() throws IOException {
        byte [] isLockMsg = Utils.HEX.decode("011dbbda5861b12d7523f20aa5e0d42f52de3dcd2d5c2fe919ba67b59f050d206e00000000babb35d229d6bf5897a9fc3770755868d9730e022dc04c8a7a7e9df9f1caccbe8967c46529a967b3822e1ba8a173066296d02593f0f59b3a78a30a7eef9c8a120847729e62e4a32954339286b79fe7590221331cd28d576887a263f45b595d499272f656c3f5176987c976239cac16f972d796ad82931d532102a4f95eec7d80");
        Sha256Hash expectedHash = Sha256Hash.wrap("4001b2c5acff9fc94e60d5adda9f70c3f0f829d0cc08434844c31b0410dfaca0");
        Sha256Hash expectedId = Sha256Hash.wrap("59f533efe1af5753cdb1227eb4b0db230d6720b2bdbef9e5d79653b5fe1cbbbb");

        InstantSendLock islock = new InstantSendLock(PARAMS, isLockMsg);


        //verify that the serialized InstantSendLock matches original data
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(isLockMsg.length);
        islock.bitcoinSerialize(bos);
        assertArrayEquals(isLockMsg, bos.toByteArray());

        Sha256Hash hash = islock.getHash();
        Sha256Hash id = islock.getRequestId();

        assertEquals(expectedHash, hash);
        assertEquals(expectedId, id);
    }
}
