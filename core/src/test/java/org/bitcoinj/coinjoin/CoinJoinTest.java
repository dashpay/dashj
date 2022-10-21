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
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoinJoinTest {
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
}
