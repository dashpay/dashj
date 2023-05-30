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

import org.bitcoinj.core.MasternodeSignature;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CoinJoinQueueTest {
    static NetworkParameters PARAMS = UnitTestParams.get();

    // TODO: Core 19 updates will require changing this test
    @Test
    public void queueTest() {
        byte[] payload = Utils.HEX.decode("10000000ba877ac598e7bf07e10a4d7ca4ccb157d476c53c815665eab47f63b02278d17e06000000f822906300000000006011a3ff601dfb9435756c531617e0ae28d0849cbfe8c9949d69d8d5240429576acba82a90e324d5ca35c9983b7e15dca40347f23022cbfb699d94c0d91582c7b7ca4ccfdf0c5b0917eb9a064b071ef438515d65834f8d7c33526c4c2a43baf686");

        CoinJoinQueue queueFromHex = new CoinJoinQueue(PARAMS, payload, PARAMS.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_LEGACY));
        assertEquals(16, queueFromHex.getDenomination());
        assertEquals(new TransactionOutPoint(PARAMS, 6, Sha256Hash.wrap("7ed17822b0637fb4ea6556813cc576d457b1cca47c4d0ae107bfe798c57a87ba")), queueFromHex.getMasternodeOutpoint());
        assertEquals(1670390520, queueFromHex.getTime());
        assertFalse(queueFromHex.isReady());

        CoinJoinQueue queueFromCtor = new CoinJoinQueue(PARAMS, 16,
                new TransactionOutPoint(PARAMS, 6, Sha256Hash.wrap("7ed17822b0637fb4ea6556813cc576d457b1cca47c4d0ae107bfe798c57a87ba")),
                1670390520, false,
                new MasternodeSignature(Utils.HEX.decode("11a3ff601dfb9435756c531617e0ae28d0849cbfe8c9949d69d8d5240429576acba82a90e324d5ca35c9983b7e15dca40347f23022cbfb699d94c0d91582c7b7ca4ccfdf0c5b0917eb9a064b071ef438515d65834f8d7c33526c4c2a43baf686")));
        assertEquals(queueFromHex, queueFromCtor);
        assertArrayEquals(payload, queueFromCtor.bitcoinSerialize());
    }
}
