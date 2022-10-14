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

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.ZeroConfCoinSelector;

public class CoinJoinCoinSelector extends ZeroConfCoinSelector {
    private final Wallet wallet;
    private boolean onlyConfirmed;

    public CoinJoinCoinSelector(Wallet wallet) {
        super();
        this.wallet = wallet;
        this.onlyConfirmed = false;
    }
    public CoinJoinCoinSelector(Wallet wallet, boolean onlyConfirmed) {
        this(wallet);
        this.onlyConfirmed = onlyConfirmed;
    }

    @Override
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.isCoinJoin(wallet)) {
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
