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

import static org.bitcoinj.coinjoin.PoolMessage.ERR_DENOM;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_QUEUE_FULL;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_NOERR;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_QUEUE;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_SIGNING;
import static org.bitcoinj.coinjoin.PoolStatusUpdate.STATUS_ACCEPTED;
import static org.bitcoinj.coinjoin.PoolStatusUpdate.STATUS_REJECTED;
import static org.junit.Assert.assertEquals;

public class CoinJoinStatusUpdateTest {

    NetworkParameters PARAMS = UnitTestParams.get();
    @Test
    public void statusUpdateTest() {
        CoinJoinStatusUpdate customStatusUpdate = new CoinJoinStatusUpdate(PARAMS, 1, POOL_STATE_QUEUE, STATUS_REJECTED, ERR_QUEUE_FULL);
        assertEquals(customStatusUpdate.getSessionID(), 1);
        assertEquals(customStatusUpdate.getState(), POOL_STATE_QUEUE);
        assertEquals(customStatusUpdate.getStatusUpdate(), STATUS_REJECTED);
        assertEquals(customStatusUpdate.getMessageID(), ERR_QUEUE_FULL);
    }



    @Test
    public void statusUpdatePayloadTest() {
        // CoinJoinStatusUpdate(sessionID=783283, state=POOL_STATE_QUEUE, statusUpdate=STATUS_REJECTED, messageID=ERR_DENOM)
        byte[] payload = Utils.HEX.decode("b3f30b0001000000000000000000000001000000");
        CoinJoinStatusUpdate customStatusUpdate = new CoinJoinStatusUpdate(PARAMS, payload);
        assertEquals(customStatusUpdate.getSessionID(), 783283);
        assertEquals(customStatusUpdate.getState(), POOL_STATE_QUEUE);
        assertEquals(customStatusUpdate.getStatusUpdate(), STATUS_REJECTED);
        assertEquals(customStatusUpdate.getMessageID(), ERR_DENOM);

        // CoinJoinStatusUpdate(sessionID=512727, state=POOL_STATE_SIGNING, statusUpdate=STATUS_ACCEPTED, messageID=MSG_NOERR)
        payload = Utils.HEX.decode("d7d2070003000000000000000100000013000000");
        customStatusUpdate = new CoinJoinStatusUpdate(PARAMS, payload);
        assertEquals(customStatusUpdate.getSessionID(), 512727);
        assertEquals(customStatusUpdate.getState(), POOL_STATE_SIGNING);
        assertEquals(customStatusUpdate.getStatusUpdate(), STATUS_ACCEPTED);
        assertEquals(customStatusUpdate.getMessageID(), MSG_NOERR);
    }

    @Test
    public void updateStatusTest() {
        byte[] payload = Utils.HEX.decode("5faa0c0001000000000000000100000013000000");
        CoinJoinStatusUpdate statusUpdateFromHex = new CoinJoinStatusUpdate(PARAMS, payload);
        assertEquals(830047, statusUpdateFromHex.getSessionID());
        assertEquals(PoolState.POOL_STATE_QUEUE, statusUpdateFromHex.getState());
        assertEquals(PoolStatusUpdate.STATUS_ACCEPTED, statusUpdateFromHex.getStatusUpdate());
        assertEquals(PoolMessage.MSG_NOERR, statusUpdateFromHex.getMessageID());

        CoinJoinStatusUpdate statusUpdateFromCtor = new CoinJoinStatusUpdate(PARAMS, 830047,
                PoolState.POOL_STATE_QUEUE, PoolStatusUpdate.STATUS_ACCEPTED, PoolMessage.MSG_NOERR);
        assertEquals(statusUpdateFromHex, statusUpdateFromCtor);
    }

}
