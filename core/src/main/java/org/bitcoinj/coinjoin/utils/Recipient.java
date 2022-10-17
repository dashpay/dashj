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
package org.bitcoinj.coinjoin.utils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.script.Script;

public class Recipient {
    private final Script scriptPubKey;
    private final Coin amount;
    private final boolean subtractFeeFromAmount;

    public Recipient(Script scriptPubKey, Coin amount, boolean subtractFeeFromAmount) {
        this.scriptPubKey = scriptPubKey;
        this.amount = amount;
        this.subtractFeeFromAmount = subtractFeeFromAmount;
    }

    public Script getScriptPubKey() {
        return scriptPubKey;
    }

    public Coin getAmount() {
        return amount;
    }

    public boolean isSubtractFeeFromAmount() {
        return subtractFeeFromAmount;
    }
}
