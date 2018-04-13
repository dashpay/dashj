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
import org.bitcoinj.params.RegTestParams;

/**
 * Created by Hash Engineering on 1/15/2016.
 */
public class InstantXCoinSelector extends DefaultCoinSelector {

    boolean usingInstantX = false;
    public InstantXCoinSelector()
    {

    }
    public void setUsingInstantX(boolean usingInstantX)
    {
        this.usingInstantX = usingInstantX;
    }
    @Override
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            return isSelectable(tx, usingInstantX);
        }
        return true;
    }
    public static boolean isSelectable(Transaction tx, boolean usingInstantX) {
        // Only pick chain-included transactions, or transactions that are pending (whether ours or not).
        // InstantSend requires 6 confirmations
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return (type.equals(TransactionConfidence.ConfidenceType.BUILDING) && (usingInstantX ? confidence.getDepthInBlocks() >= 6 : true)) ||
                type.equals(TransactionConfidence.ConfidenceType.PENDING) && (usingInstantX ? false : true) &&
                        // In regtest mode we expect to have only one peer, so we won't see transactions propagate.
                        // TODO: The value 1 below dates from a time when transactions we broadcast *to* were counted, set to 0
                        (confidence.numBroadcastPeers() > 1 || tx.getParams() == RegTestParams.get());
    }
}
