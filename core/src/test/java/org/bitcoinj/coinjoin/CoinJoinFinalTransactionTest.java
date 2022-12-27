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
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CoinJoinFinalTransactionTest {
    static NetworkParameters PARAMS = UnitTestParams.get();

    @Test
    public void payloadTest() {
        // CoinJoinFinalTransaction(msgSessionID=512727, transaction=a57015f0f8c85cdee8ab47d9bb7792c23c21bfbad1a19b7b4368915ba970fae1)
        byte[] payload = Utils.HEX.decode("d7d20700020000000424e55da190e79da9540b0fc87e859261e4a2a33530a005cab68f5cd3ece0234a0200000000fffffffff7b9fdaf651dc308b6f538ac9dae090268f08f424fd91c705fe5d12174999f5d0400000000ffffffff20d63b2ca93309e3526cac6fab31545bd932ba705e7011e720ba69af59baaba10000000000ffffffff20d63b2ca93309e3526cac6fab31545bd932ba705e7011e720ba69af59baaba10300000000ffffffff044a420f00000000001976a914257b0482306f29fe7d97fb8b847746ba4a1606e588ac4a420f00000000001976a9146662e6130b7b1f06778ae1e24b687db752e1a83d88ac4a420f00000000001976a914ac2faba75d50cdd8dff86b8a457c45201aefb96f88ac4a420f00000000001976a914b89ab9894c767e22e85e2164b9f65fc0a4c3dc7e88ac00000000");
        Sha256Hash txid = Sha256Hash.wrap("a57015f0f8c85cdee8ab47d9bb7792c23c21bfbad1a19b7b4368915ba970fae1");

        CoinJoinFinalTransaction finalTransaction = new CoinJoinFinalTransaction(PARAMS, payload);
        assertEquals(512727, finalTransaction.getMsgSessionID());
        assertEquals(txid, finalTransaction.getTransaction().getTxId());

        assertArrayEquals(payload, finalTransaction.bitcoinSerialize());

        CoinJoinFinalTransaction fromCtor = new CoinJoinFinalTransaction(PARAMS, 512727, finalTransaction.getTransaction());
        assertArrayEquals(payload, fromCtor.bitcoinSerialize());
    }
}
