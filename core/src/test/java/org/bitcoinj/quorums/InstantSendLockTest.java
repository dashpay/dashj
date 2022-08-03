package org.bitcoinj.quorums;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
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

        InstantSendLock islock = new InstantSendLock(PARAMS, isLockMsg, InstantSendLock.ISLOCK_VERSION);


        //verify that the serialized InstantSendLock matches original data
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(isLockMsg.length);
        islock.bitcoinSerialize(bos);
        assertArrayEquals(isLockMsg, bos.toByteArray());

        Sha256Hash hash = islock.getHash();
        Sha256Hash id = islock.getRequestId();

        assertEquals(expectedHash, hash);
        assertEquals(expectedId, id);
    }

    @Test
    public void instantSendDeterministicLockMessageTest() throws IOException {
        byte [] isLockMsg = Utils.HEX.decode("0101a13402f343c05e8a6cd1255dc10446f9af507d02a366fbb1df4ef7130633c6b501000000f2a984e019605164b62b08a559789ac15e8d93ff6b041ffeef3457fb689413fc1c7c0b2ac5ebca64f80c0151a152612b23396193880b8f6856e9cbcc0501000082432a71e87357d9609c659d32cbf3bfb65ccce2337d08ab1485c32799491112509cd7c8e1cd40bdc814bf1eff868dc80f3fac74e37754eb19a5883d5721875a98dd3ea3112c3c52091852348351479b4d32a98d41200080532ed233a0aa355e");
        Sha256Hash expectedHash = Sha256Hash.wrap("fc3f6eec219419b2cedb83e9eefd348b005766a0ea0ebf6bf4339ded8e087614");
        Sha256Hash expectedId = Sha256Hash.wrap("26ce1d432cfb727f630c85b97a9c9e8be7911ca7c4f402023bb66e98da6aeb28");

        InstantSendLock islock = new InstantSendLock(PARAMS, isLockMsg, InstantSendLock.ISDLOCK_VERSION);
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
