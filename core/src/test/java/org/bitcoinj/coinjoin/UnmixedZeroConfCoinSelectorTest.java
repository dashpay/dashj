/*
 * Copyright 2022 Dash Core Group
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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnmixedZeroConfCoinSelectorTest extends TestWithCoinJoinWallet {


    @Test
    public void selectable() {
        UnmixedZeroConfCoinSelector coinSelector = new UnmixedZeroConfCoinSelector(walletEx);

        // lastTxCoinJoin is fully mixed and should not be selected
        assertFalse(coinSelector.shouldSelect(lastTxCoinJoin));
        // txDenomination, txDeposit is mixed zero rounds, so it should be selected
        assertTrue(coinSelector.shouldSelect(txDenomination));
        assertTrue(coinSelector.shouldSelect(txDeposit));
        assertTrue(coinSelector.shouldSelect(txReceiveZeroConf));
    }
}
