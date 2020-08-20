package org.bitcoinj.evolution.listeners;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.evolution.CreditFundingTransaction;

/**
 *
 */

public interface CreditFundingTransactionEventListener {
    /**
     *
     * @param tx the credit funding transaction
     * @param block the block that the transaction was contained in, unless the tx is not confirmed
     * @param blockType main chain or side chain
     */
    void onTransactionReceived(CreditFundingTransaction tx, StoredBlock block,
                                           BlockChain.NewBlockType blockType);
}
