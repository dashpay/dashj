/*
 * Copyright 2018 Dash Core Group
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

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.params.UnitTestParams;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;


import static org.junit.Assert.*;

public class CoinbaseTxTest {

    Context context;
    UnitTestParams PARAMS;
    byte[] txdata;

    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = new Context(PARAMS);
        txdata = Utils.HEX.decode("03000500010000000000000000000000000000000000000000000000000000000000000000ffffffff0502fa050105ffffffff02f4c21a3d0500000023210397181e4cc48fcba0e597bfb029d4cfc4473ae5772a0ff32223977d4e03e07fa9acf4c21a3d050000001976a91425e50daf158a83dfaacd1b77175900aa95a67d4188ac00000000260100fa050000aaaec8d6a8535a01bd844817dea1faed66f6c397b1dcaec5fe8c5af025023c35");
    }

    @Test
    public void verifyTest() {
        Sha256Hash txId = Sha256Hash.wrap("7fdcbf835dc7b0d4fe2c8d1b5b3ef32ea5a26d0a225b52a0bb7652804535a491");

        Transaction tx = new Transaction(PARAMS, txdata);
        CoinbaseTx cbtx = (CoinbaseTx)tx.getExtraPayloadObject();
        assertSame(tx, cbtx.getParentTransaction());

        byte [] payloadDataToConfirm = Utils.HEX.decode("0100fa050000aaaec8d6a8535a01bd844817dea1faed66f6c397b1dcaec5fe8c5af025023c35");
        assertArrayEquals("Payload Data does not match", payloadDataToConfirm, cbtx.getPayload());
        assertEquals(txId, tx.getTxId());
        assertEquals(1530, cbtx.getHeight());
        assertEquals(1, cbtx.getVersion());
        assertArrayEquals("Coinbase transaction does not match it's data", txdata, tx.bitcoinSerialize());
    }

    @Test
    public void verifyV20Test() {
        // testnet coinbase tx 1be829f07fe057ebed7c77275aea78a159c50a7ff3b14b2e14f77d823d219289
        // from block 0000009e5871599e17764748b0e86e765aeba0d7954316a2717b2178c934e049 at height 901623
        txdata = Utils.HEX.decode("03000500010000000000000000000000000000000000000000000000000000000000000000ffffffff0603f7c10d0101ffffffff0397f4e127000000001976a914c69a0bda7daaae481be8def95e5f347a1d00a4b488ac94196f1600000000016a4dd56325000000001976a91464f2b2b84f62d68a2cd7f7f5fb2b5aa75ef716d788ac00000000af0300f7c10d00f51559d679d1784c435ddb4f21ee000e8d3f831159014babbc942dd7c537a073abee2f0688fe9719101305df27e230cf04ed3c3294566b5d72b5b5f190d7d30c0088073e9e8b46944f3560710a448c896c49e9f48912ef60e817294fc78eb7ccaf1f316fc7106a15bcb43fb3fc192e93b0182332d2eced64b246931ce9539713f1b966923ed9b87d11881aa50e288743734033fbd30bd70df061fe218064c0730169b103f950000000");
        Sha256Hash txId = Sha256Hash.wrap("1be829f07fe057ebed7c77275aea78a159c50a7ff3b14b2e14f77d823d219289");

        Transaction tx = new Transaction(PARAMS, txdata);
        assertEquals(txId, tx.getTxId());
        CoinbaseTx cbtx = (CoinbaseTx)tx.getExtraPayloadObject();
        assertSame(tx, cbtx.getParentTransaction());

        byte [] payloadDataToConfirm = Utils.HEX.decode("0300f7c10d00f51559d679d1784c435ddb4f21ee000e8d3f831159014babbc942dd7c537a073abee2f0688fe9719101305df27e230cf04ed3c3294566b5d72b5b5f190d7d30c0088073e9e8b46944f3560710a448c896c49e9f48912ef60e817294fc78eb7ccaf1f316fc7106a15bcb43fb3fc192e93b0182332d2eced64b246931ce9539713f1b966923ed9b87d11881aa50e288743734033fbd30bd70df061fe218064c0730169b103f950000000");
        assertArrayEquals("Payload Data does not match", payloadDataToConfirm, cbtx.getPayload());
        assertEquals(txId, tx.getTxId());
        assertEquals(901623, cbtx.getHeight());
        assertEquals(3, cbtx.getVersion());
        assertEquals(Sha256Hash.wrap("73a037c5d72d94bcab4b015911833f8d0e00ee214fdb5d434c78d179d65915f5"), cbtx.getMerkleRootMasternodeList());
        assertEquals(Sha256Hash.wrap("0cd3d790f1b5b5725d6b5694323ced04cf30e227df0513101997fe88062feeab"), cbtx.getMerkleRootQuorums());
        assertEquals(new BLSSignature(Utils.HEX.decode("88073e9e8b46944f3560710a448c896c49e9f48912ef60e817294fc78eb7ccaf1f316fc7106a15bcb43fb3fc192e93b0182332d2eced64b246931ce9539713f1b966923ed9b87d11881aa50e288743734033fbd30bd70df061fe218064c07301"), false), cbtx.getBestCLSignature());
        assertEquals(0, cbtx.getBestCLHeightDiff());
        assertEquals(Coin.valueOf(347775152489L), cbtx.getCreditPoolBalance());

        assertArrayEquals("Coinbase transaction does not match it's data", txdata, tx.bitcoinSerialize());

        CoinbaseTx cbTxCopy = new CoinbaseTx(PARAMS, 3, 901623, Sha256Hash.wrap("73a037c5d72d94bcab4b015911833f8d0e00ee214fdb5d434c78d179d65915f5"),
            Sha256Hash.wrap("0cd3d790f1b5b5725d6b5694323ced04cf30e227df0513101997fe88062feeab"),
            0 , new BLSSignature(Utils.HEX.decode("88073e9e8b46944f3560710a448c896c49e9f48912ef60e817294fc78eb7ccaf1f316fc7106a15bcb43fb3fc192e93b0182332d2eced64b246931ce9539713f1b966923ed9b87d11881aa50e288743734033fbd30bd70df061fe218064c07301"), false), Coin.valueOf(347775152489L));
        assertArrayEquals("Payload Data does not match", payloadDataToConfirm, cbTxCopy.getPayload());

        JSONObject fromCore = new JSONObject("{\n" +
                "  \"version\": 3,\n" +
                "  \"height\": 901623,\n" +
                "  \"merkleRootMNList\": \"73a037c5d72d94bcab4b015911833f8d0e00ee214fdb5d434c78d179d65915f5\",\n" +
                "  \"merkleRootQuorums\": \"0cd3d790f1b5b5725d6b5694323ced04cf30e227df0513101997fe88062feeab\",\n" +
                "  \"bestCLHeightDiff\": 0,\n" +
                "  \"bestCLSignature\": \"88073e9e8b46944f3560710a448c896c49e9f48912ef60e817294fc78eb7ccaf1f316fc7106a15bcb43fb3fc192e93b0182332d2eced64b246931ce9539713f1b966923ed9b87d11881aa50e288743734033fbd30bd70df061fe218064c07301\",\n" +
                "  \"creditPoolBalance\": 3477.75152489\n" +
                "}");

        assertEquals(fromCore.toString(2), cbtx.toJson().toString(2));
    }
}
