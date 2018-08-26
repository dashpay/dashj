package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class CoinbaseTxTest {

    Context context;
    UnitTestParams PARAMS;
    byte[] txdata;

    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = Context.getOrCreate(PARAMS);
        txdata = Utils.HEX.decode("03000500010000000000000000000000000000000000000000000000000000000000000000ffffffff0502fe070101ffffffff022dc71a3d05000000232103ee68257f0a4b1d9cadaf24809b2dddcf557cd3f11db99430c3a682d003861891ac21c71a3d050000001976a914e1e24b11deb712819c7d0da193423bda1b47c25d88ac0000000024fe0700006c45528d7b8d4e7a33614a1c3806f4faf5c463f0b313aa0ece1ce12c34154a44");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";
    }

    @Test
    public void verifyTest() {
        Sha256Hash txId = Sha256Hash.wrap("afb7aeb1cb84049c7c81c980c996515dda91ec5c15c29398bad985e9a286d2dc");

        Transaction tx = new Transaction(PARAMS, txdata);
        CoinbaseTx cbtx = (CoinbaseTx)tx.getExtraPayloadObject();

        byte [] payloadDataToConfirm = Utils.HEX.decode("fe0700006c45528d7b8d4e7a33614a1c3806f4faf5c463f0b313aa0ece1ce12c34154a44");
        assertArrayEquals("Payload Data does not match", payloadDataToConfirm, cbtx.getPayload());
        assertEquals(txId, tx.getHash());
        assertEquals(2046, cbtx.getHeight());

        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(txdata.length);
            tx.bitcoinSerialize(stream);
            assertArrayEquals("Coinbase transaction does not match it's data", txdata, stream.toByteArray());
        } catch (IOException x) {
            fail();
        }
    }
}
