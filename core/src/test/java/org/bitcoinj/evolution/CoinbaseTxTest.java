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
        txdata = Utils.HEX.decode("03000500010000000000000000000000000000000000000000000000000000000000000000ffffffff0502fa050105ffffffff02f4c21a3d0500000023210397181e4cc48fcba0e597bfb029d4cfc4473ae5772a0ff32223977d4e03e07fa9acf4c21a3d050000001976a91425e50daf158a83dfaacd1b77175900aa95a67d4188ac00000000260100fa050000aaaec8d6a8535a01bd844817dea1faed66f6c397b1dcaec5fe8c5af025023c35");
    }

    @Test
    public void verifyTest() {
        Sha256Hash txId = Sha256Hash.wrap("7fdcbf835dc7b0d4fe2c8d1b5b3ef32ea5a26d0a225b52a0bb7652804535a491");

        Transaction tx = new Transaction(PARAMS, txdata);
        CoinbaseTx cbtx = (CoinbaseTx)tx.getExtraPayloadObject();

        byte [] payloadDataToConfirm = Utils.HEX.decode("0100fa050000aaaec8d6a8535a01bd844817dea1faed66f6c397b1dcaec5fe8c5af025023c35");
        assertArrayEquals("Payload Data does not match", payloadDataToConfirm, cbtx.getPayload());
        assertEquals(txId, tx.getHash());
        assertEquals(1530, cbtx.getHeight());

        try {
            UnsafeByteArrayOutputStream stream = new UnsafeByteArrayOutputStream(txdata.length);
            tx.bitcoinSerialize(stream);
            assertArrayEquals("Coinbase transaction does not match it's data", txdata, stream.toByteArray());
        } catch (IOException x) {
            fail();
        }
    }
}
