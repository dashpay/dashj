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
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.ZeroConfCoinSelector;

import java.util.ArrayList;
import java.util.List;
import static com.google.common.base.Preconditions.checkArgument;

public class CoinJoinCoinSelector extends ZeroConfCoinSelector {
    private final TransactionBag transactionBag;
    private boolean onlyConfirmed;

    public CoinJoinCoinSelector(TransactionBag transactionBag) {
        this(transactionBag, false);
    }
    public CoinJoinCoinSelector(TransactionBag transactionBag, boolean onlyConfirmed) {
        checkArgument(transactionBag != null, "transactionBag cannot be null");
        this.transactionBag = transactionBag;
        this.onlyConfirmed = onlyConfirmed;
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        ArrayList<TransactionOutput> selected = new ArrayList<>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        // TODO: Consider changing the wallets internal format to track just outputs and keep them ordered.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        // TODO: Take in network parameters when instanatiated, and then test against the current network. Or just have a boolean parameter for "give me everything"
        if (!target.equals(NetworkParameters.MAX_MONEY)) {
            sortOutputs(sortedOutputs);
        }
        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        for (TransactionOutput output : sortedOutputs) {
            if (total >= target.value)
                break;
            // don't bother with shouldSelect, since we have to check all the outputs
            // with isCoinJoin anyways.
            if (output.isCoinJoin(transactionBag) && !transactionBag.isLockedOutput(output.getOutPointFor())) {
                selected.add(output);
                total += output.getValue().value;
            }
        }
        return new CoinSelection(Coin.valueOf(total), selected);
    }

    @Override
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.isCoinJoin(transactionBag)) {
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
