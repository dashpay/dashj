/*
 * Copyright by the original author or authors.
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

package org.bitcoinj.params;

import org.bitcoinj.core.Coin;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AbstractBitcoinNetParamsTest {
    private final AbstractBitcoinNetParams DASH_PARAMS = new AbstractBitcoinNetParams() {
        @Override
        public String getPaymentProtocolId() {
            return null;
        }
    };

    @Test
    public void isDifficultyTransitionPoint() {
        // difficulty changes every block
        assertTrue(DASH_PARAMS.isDifficultyTransitionPoint(210240 - 2));
        assertTrue(DASH_PARAMS.isDifficultyTransitionPoint(210240 - 1));
        assertTrue(DASH_PARAMS.isDifficultyTransitionPoint(210240));
    }

    @Test
    public void isRewardHalvingPoint() {
        assertTrue(DASH_PARAMS.isRewardHalvingPoint(210239));

        assertTrue(DASH_PARAMS.isRewardHalvingPoint(420479));

        assertFalse(DASH_PARAMS.isRewardHalvingPoint(630718));
        assertTrue(DASH_PARAMS.isRewardHalvingPoint(630719));
        assertFalse(DASH_PARAMS.isRewardHalvingPoint(630720));

        assertTrue(DASH_PARAMS.isRewardHalvingPoint(840959));
    }

    @Test
    public void getBlockInflation() {
        assertEquals(Coin.valueOf(2250000000L), DASH_PARAMS.getBlockInflation(210240 - 1, 0x1e0fffffL, false));
        assertEquals(Coin.valueOf(2250000000L), DASH_PARAMS.getBlockInflation(210240, 0x1e0fffffL, false));
        assertEquals(Coin.valueOf(2089285715L), DASH_PARAMS.getBlockInflation(210240 + 1, 0x1e0fffffL, false));
    }
}
