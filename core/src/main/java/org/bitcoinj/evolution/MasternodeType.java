/*
 * Copyright 2023 Dash Core Group
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

package org.bitcoinj.evolution;

import org.bitcoinj.core.Coin;

public class MasternodeType {
    public static final MasternodeType REGULAR = new MasternodeType(0, 1, Coin.valueOf(1000,0), "Regular");
    public static final MasternodeType HIGHPERFORMANCE = new MasternodeType(1, 4, Coin.valueOf(4000,0), "HighPerformance");
    int index;
    int votingWeight;
    Coin collateralAmount;
    String description;
    private MasternodeType(int index, int votingWeight, Coin collateralAmount, String description) {
        this.index = index;
        this.votingWeight = votingWeight;
        this.collateralAmount = collateralAmount;
        this.description = description;
    }

    public static MasternodeType getMasternodeType(int index) {
        switch(index) {
            case 0: return REGULAR;
            case 1: return HIGHPERFORMANCE;
            default:
                throw new IllegalArgumentException("MasternodeType " + index + " is invalid.");
        }
    }
}
