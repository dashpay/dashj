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

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

/**
 * Created by Hash Engineering on 1/15/2016.
 */
public class InstantXCoinSelector extends DefaultCoinSelector {

    private static final InstantXCoinSelector instance = new InstantXCoinSelector();

    public static InstantXCoinSelector get() {
        return instance;
    }

    private InstantXCoinSelector() {

    }

    @Override
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            return isTransactionSelectable(tx);
        }
        return true;
    }

    boolean isTransactionSelectable(Transaction tx) {
        // Only pick chain-included transactions with at least `instantSendConfirmationsRequired` confirmations
        int instantSendConfirmationsRequired = tx.getParams().getInstantSendConfirmationsRequired();
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return (type.equals(TransactionConfidence.ConfidenceType.BUILDING) && confidence.getDepthInBlocks() >= instantSendConfirmationsRequired);
    }
}
