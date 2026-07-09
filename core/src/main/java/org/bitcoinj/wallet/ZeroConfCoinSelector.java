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

package org.bitcoinj.wallet;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

public class ZeroConfCoinSelector extends DefaultCoinSelector {

    private static final ZeroConfCoinSelector instance = new ZeroConfCoinSelector();

    public static ZeroConfCoinSelector get() {
        return instance;
    }

    protected ZeroConfCoinSelector() {

    }

    @Override
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            return isTransactionSelectable(tx);
        }
        return true;
    }

    boolean isTransactionSelectable(Transaction tx) {
        // Only pick chain-included transactions, or transactions that are pending (whether ours or not).
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return type.equals(TransactionConfidence.ConfidenceType.BUILDING) || type.equals(TransactionConfidence.ConfidenceType.PENDING) &&
                // In regtest mode we expect to have only one peer, so we won't see transactions propagate.
                // TODO: The value 1 below dates from a time when transactions we broadcast *to* were counted, set to 0
                // Compare against the network id rather than RegTestParams.get(): the latter lazily constructs
                // RegTestParams (recomputing and asserting the regtest genesis X11 hash), which can throw when first
                // triggered on a contended background thread (e.g. CoinJoin) even on mainnet/testnet wallets.
                (confidence.numBroadcastPeers() > 1 || NetworkParameters.ID_REGTEST.equals(tx.getParams().getId()));
    }
}
