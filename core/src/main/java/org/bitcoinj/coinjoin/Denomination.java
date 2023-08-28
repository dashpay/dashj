/*
 * Copyright (c) 2023 Dash Core Group
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

public enum Denomination {
    TEN(Coin.COIN.multiply(10).add(Coin.valueOf(10000))),
    ONE(Coin.COIN.add(Coin.valueOf(1000))),
    TENTH(Coin.COIN.div(10).add(Coin.valueOf(100))),
    HUNDREDTH(Coin.COIN.div(100).add(Coin.valueOf(10))),
    THOUSANDTH(Coin.COIN.div(1000).add(Coin.valueOf(1))),
    SMALLEST(THOUSANDTH.value);

    final Coin value;
    Denomination(Coin value) {
        this.value = value;
    }
}
