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

import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.script.Script;

public class CoinJoinTransactionInput extends TransactionInput {
    // memory only
    private final Script prevPubKey;
    private final boolean hasSignature;
    private final int rounds;

    public CoinJoinTransactionInput(TransactionInput txin, Script script, int rounds) {
        super(txin.getParams(), txin.getParentTransaction(), txin.getScriptBytes(), txin.getOutpoint(), txin.getValue());
        this.prevPubKey = script;
        this.rounds = rounds;
        this.hasSignature = false;
    }

    public int getRounds() {
        return rounds;
    }

    public Script getPrevPubKey() {
        return prevPubKey;
    }

    public boolean hasSignature() {
        return hasSignature;
    }
}
