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
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CoinJoinCompleteTest {
    static NetworkParameters PARAMS = UnitTestParams.get();

    @Test
    public void payloadTest() {
        // CoinJoinComplete(msgSessionID=549379, msgMessageID=20)
        byte[] payload = Utils.HEX.decode("0362080014000000");

        CoinJoinComplete completeMessage = new CoinJoinComplete(PARAMS, payload);
        assertEquals(20, completeMessage.getMsgMessageID().value);
        assertEquals(549379, completeMessage.getMsgSessionID());

        CoinJoinComplete fromCtor = new CoinJoinComplete(PARAMS, 549379, PoolMessage.MSG_SUCCESS);

        assertArrayEquals(payload, fromCtor.bitcoinSerialize());
    }
}
