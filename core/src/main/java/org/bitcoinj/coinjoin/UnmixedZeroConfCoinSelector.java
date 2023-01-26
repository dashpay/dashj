/*
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
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.ZeroConfCoinSelector;

import java.util.Iterator;
import java.util.List;

public class UnmixedZeroConfCoinSelector extends ZeroConfCoinSelector {
    //private final Wallet wallet;
    private boolean onlyConfirmed = false;

    private static final UnmixedZeroConfCoinSelector instance = new UnmixedZeroConfCoinSelector();

    public static UnmixedZeroConfCoinSelector get() {
        return instance;
    }

    protected UnmixedZeroConfCoinSelector() {
        super();
    }

    /*public UnmixedZeroConfCoinSelector(Wallet wallet) {
        super();
        this.wallet = wallet;
        this.onlyConfirmed = false;
    }
    public UnmixedZeroConfCoinSelector(Wallet wallet, boolean onlyConfirmed) {
        this(wallet);
        this.onlyConfirmed = onlyConfirmed;
    }*/

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        CoinSelection selection = super.select(target, candidates);
        Iterator<TransactionOutput> iterator = selection.gathered.iterator();
        while (iterator.hasNext()) {
            TransactionOutput output = iterator.next();
            if (output.isDenominated()) {
                // remove denominated outputs
                iterator.remove();
                selection.valueGathered = selection.valueGathered.subtract(output.getValue());
            }
        }
        return selection;
    }

    @Override
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            for (TransactionOutput output : tx.getOutputs()) {
                if (!output.isDenominated()) {
                    TransactionConfidence confidence = tx.getConfidence();
                    if (onlyConfirmed)
                        return confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING;
                    return super.shouldSelect(tx);
                }
            }
            return false;
        }
        return true;
    }
}
