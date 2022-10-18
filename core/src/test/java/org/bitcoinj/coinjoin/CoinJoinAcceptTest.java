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
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CoinJoinAcceptTest {
    static NetworkParameters PARAMS = UnitTestParams.get();

    @Test
    public void acceptTest() {
        CoinJoinAccept cja = new CoinJoinAccept(PARAMS, 0, new Transaction(PARAMS));
        assertEquals(cja.getDenomination(), 0);
        assertEquals(cja.getTxCollateral().getTxId(), new Transaction(PARAMS).getTxId());
    }
}
