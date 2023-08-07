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

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CoinJoinTest {
    static NetworkParameters PARAMS = UnitTestParams.get();
    static Context context = new Context(PARAMS);

    @Test
    public void standardDenominationTest() {
        class DenomTest {
            final Coin amount;
            final boolean isDenomiation;
            final String stringValue;
            DenomTest(long amount, boolean isDenomination, String stringValue) {
                this.amount = Coin.valueOf(amount);
                this.isDenomiation = isDenomination;
                this.stringValue = stringValue;
            }
        }
        DenomTest [] tests = {
                new DenomTest(1000010000, true, "10.0001"),
                new DenomTest(100001000, true, "1.00001"),
                new DenomTest(10000100, true, "0.100001"),
                new DenomTest(1000010, true, "0.0100001"),
                new DenomTest(100001, true, "0.00100001"),
                new DenomTest(10000, false, "N/A"),
                new DenomTest(20000, false, "N/A"),
                new DenomTest(1000, false, "N/A"),
                new DenomTest(546, false, "N/A"),
                new DenomTest(1000000000, false, "N/A"),
        };
        for (DenomTest test : tests) {
            assertEquals(test.isDenomiation, CoinJoin.isDenominatedAmount(test.amount));
            assertEquals(test.stringValue, CoinJoin.denominationToString(CoinJoin.amountToDenomination(test.amount)));
        }

        assertEquals(Coin.parseCoin("0.00100001"), CoinJoin.getSmallestDenomination());

        for (int i = 0; i < CoinJoin.getStandardDenominations().size(); ++i) {
            Coin value = CoinJoin.getStandardDenominations().get(i);
            assertEquals(value, CoinJoin.denominationToAmount(CoinJoin.amountToDenomination(value)));
        }
    }
    @Test
    public void collateralTests() {
        // Good collateral values
        assertTrue(CoinJoin.isCollateralAmount(Coin.parseCoin("0.00010000")));
        assertTrue(CoinJoin.isCollateralAmount(Coin.parseCoin("0.00012345")));
        assertTrue(CoinJoin.isCollateralAmount(Coin.parseCoin("0.00032123")));
        assertTrue(CoinJoin.isCollateralAmount(Coin.parseCoin("0.00019000")));
    
        // Bad collateral values
        assertFalse(CoinJoin.isCollateralAmount(Coin.parseCoin("0.00009999")));
        assertFalse(CoinJoin.isCollateralAmount(Coin.parseCoin("0.00040001")));
        assertFalse(CoinJoin.isCollateralAmount(Coin.parseCoin("0.00100000")));
        assertFalse(CoinJoin.isCollateralAmount(Coin.parseCoin("0.00100001")));
    }

    @Test
    public void isCollateralValidTest() {
        // Transaction{b283bb8d74a0ad97030a43acda41c3fbfd17fe70d43881448c37c3c60ac72ebb
        //   type TRANSACTION_NORMAL(0)
        // purpose: UNKNOWN
        //   in   PUSHDATA(71)[304402202edab2fb737f7672bd9898e00855a86ca3bdc60a676a16766edb505370e9e0d50220139fd47f674e2ccee32139cf7a82e441f6f2c7d79d7135ac900a3a836591ae9301] PUSHDATA(33)[0262ffa9b2c936262abd869ead9cfde301d29adbe3d4b18d8cd6a150d45e61d656]  0.0004 DASH
        //        P2PKH addr:yjK5RKViMoQn81wwrikm5y7T8vgvUQYC2i  outpoint:541d1f1c67897810837a02f048de84fa141fd8c6fe18aea66048d4e4ca6817cb:0
        //   out  DUP HASH160 PUSHDATA(20)[d1a0b93ec28bba201c03fb01a934727782c7b9e2] EQUALVERIFY CHECKSIG  0.0003 DASH
        //        P2PKH addr:yfRrcZuHQbJMgs4ZCwAK1hJp1d99vvjrcw
        //   fee  0.00052356 DASH/kB, 0.0001 DASH for 191 bytes
        // }

        byte[] fromTxPayload = Utils.HEX.decode("01000000016bd8a29a65ea37bc987990d5f5cffb7f851ccfb3ac84c6f3fab7a55ad8d27cd4260000006a47304402203570a91993f3e3592feb786a2b716aa49f1eae9408ce2235cc921c564273394b02205fd031db0930cbc9a6b3268cfd13414154aedd513352061904d63a9798ed32de012103d4f84df688e87a4da8c359b5fbececdf2489913e77e7f95a203b8ecef8605a5effffffff03409c0000000000001976a914fc3901464cf9aeaf9ca2ff8f33456a08beb4034588ac57e90000000000001976a914d88f454cc05a36cdd93d40cb6483ff8bc6bffeb788ac48109700000000001976a9141556b9d0f17aaebe09877a0929815716d51398ce88ac00000000");
        Transaction fromTx = new Transaction(PARAMS, fromTxPayload);
        byte[] txPayload = Utils.HEX.decode("0100000001cb1768cae4d44860a6ae18fec6d81f14fa84de48f0027a83107889671c1f1d54000000006a47304402202edab2fb737f7672bd9898e00855a86ca3bdc60a676a16766edb505370e9e0d50220139fd47f674e2ccee32139cf7a82e441f6f2c7d79d7135ac900a3a836591ae9301210262ffa9b2c936262abd869ead9cfde301d29adbe3d4b18d8cd6a150d45e61d656ffffffff0130750000000000001976a914d1a0b93ec28bba201c03fb01a934727782c7b9e288ac00000000");
        Transaction txCollateral = new Transaction(PARAMS, txPayload);

        // should fail since the inputs are not connected
        assertFalse(CoinJoin.isCollateralValid(txCollateral));

        // we need to rebuild the collateral input and connect to fromTx as the source
        TransactionInput input = txCollateral.getInputs().get(0);
        TransactionOutPoint outPoint = input.getOutpoint();
        TransactionInput inputWithConnection = new TransactionInput(PARAMS, txCollateral, input.getScriptBytes(),
                new TransactionOutPoint(PARAMS, outPoint.getIndex(), fromTx));
        txCollateral.clearInputs();
        txCollateral.addInput(inputWithConnection);

        assertTrue(CoinJoin.isCollateralValid(txCollateral));
    }

    @Test
    public void attemptToModifyStandardDenominationsTest() {
        List<Coin> denominations = CoinJoin.getStandardDenominations();
        // modification of this list is not allowed
        assertThrows(UnsupportedOperationException.class, () -> denominations.add(Coin.COIN));
        assertThrows(UnsupportedOperationException.class, () -> denominations.remove(0));
    }
}
