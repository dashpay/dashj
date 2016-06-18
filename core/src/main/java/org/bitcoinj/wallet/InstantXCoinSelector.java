package org.bitcoinj.wallet;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.params.RegTestParams;

/**
 * Created by Eric on 1/15/2016.
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
        // Only pick chain-included transactions, or transactions that are ours and pending.
        TransactionConfidence confidence = tx.getConfidence();
        TransactionConfidence.ConfidenceType type = confidence.getConfidenceType();
        return (type.equals(TransactionConfidence.ConfidenceType.BUILDING) && ( usingInstantX ? confidence.getDepthInBlocks() >= 6 : true))||
                //type.equals(TransactionConfidence.ConfidenceType.INSTANTX_LOCKED) || //TODO:InstantX
                type.equals(TransactionConfidence.ConfidenceType.PENDING) &&
                        confidence.getSource().equals(TransactionConfidence.Source.SELF) && ( usingInstantX ? false : true) &&
                        // In regtest mode we expect to have only one peer, so we won't see transactions propagate.
                        // TODO: The value 1 below dates from a time when transactions we broadcast *to* were counted, set to 0
                        (confidence.numBroadcastPeers() > 1 || tx.getParams() == RegTestParams.get());
    }
}
