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

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.ZeroConfCoinSelector;

public class DenominatedCoinSelector extends ZeroConfCoinSelector {
    private static final DenominatedCoinSelector instance = new DenominatedCoinSelector();

    public static DenominatedCoinSelector get() {
        return instance;
    }

    private DenominatedCoinSelector() {
        super();
    }

    @Override
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            boolean isDenominated = isTransactionSelectable(tx);
            return isDenominated && super.shouldSelect(tx);
        }
        return true;
    }

    boolean isTransactionSelectable(Transaction tx) {
        for (TransactionOutput output : tx.getOutputs()) {
            if (output.isDenominated())
                return true;
        }
        return false;
    }
}
