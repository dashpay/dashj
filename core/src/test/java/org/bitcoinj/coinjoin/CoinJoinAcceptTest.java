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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CoinJoinAcceptTest {
    static NetworkParameters PARAMS = UnitTestParams.get();

    @Test
    public void acceptTest() {
        CoinJoinAccept cja = new CoinJoinAccept(PARAMS, 0, new Transaction(PARAMS));
        assertEquals(cja.getDenomination(), 0);
        assertEquals(cja.getTxCollateral().getTxId(), new Transaction(PARAMS).getTxId());
    }

    @Test
    public void payloadTest() {
        // CoinJoinAccept(denomination=16, txCollateral=956685418a5c4fecaedf74d2ac56862e22830658b6b256e4e2dea0b665e1f756)
        byte[] payload = Utils.HEX.decode("100000000100000001c493c126391c84670115d1d5ed81b27a741a99a8942289bc82396da0b490795c000000006b483045022100d9899056ed6aceee4411b965a07a4a624fc43faca0112fae49129101942ae42f02205b92eefecf87975a356fd80a918978da49594baf8bb1082f527542e56f4f191d012102bc626099759860a95d13a6cdeb754efa5deec2178cc65142b92bbd90ab4d1364ffffffff01204e0000000000001976a914b2705af1d15ee81e96fce5ec49cd9ed6639362f188ac00000000");
        Sha256Hash txid = Sha256Hash.wrap("956685418a5c4fecaedf74d2ac56862e22830658b6b256e4e2dea0b665e1f756");

        CoinJoinAccept acceptMessage = new CoinJoinAccept(PARAMS, payload);
        assertEquals(16, acceptMessage.getDenomination());
        assertEquals(txid, acceptMessage.getTxCollateral().getTxId());

        assertArrayEquals(payload, acceptMessage.bitcoinSerialize());
    }
}
