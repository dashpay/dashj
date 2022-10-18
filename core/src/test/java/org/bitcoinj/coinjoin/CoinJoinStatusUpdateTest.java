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

import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.bitcoinj.coinjoin.PoolMessage.ERR_QUEUE_FULL;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_QUEUE;
import static org.bitcoinj.coinjoin.PoolStatusUpdate.STATUS_REJECTED;
import static org.junit.Assert.assertEquals;

public class CoinJoinStatusUpdateTest {

    @Test
    public void statusUpdateTest() {
        CoinJoinStatusUpdate customStatusUpdate = new CoinJoinStatusUpdate(UnitTestParams.get(), 1, POOL_STATE_QUEUE, STATUS_REJECTED, ERR_QUEUE_FULL);
        assertEquals(customStatusUpdate.getSessionID(), 1);
        assertEquals(customStatusUpdate.getState(), POOL_STATE_QUEUE);
        assertEquals(customStatusUpdate.getStatusUpdate(), STATUS_REJECTED);
        assertEquals(customStatusUpdate.getMessageID(), ERR_QUEUE_FULL);
    }
}
