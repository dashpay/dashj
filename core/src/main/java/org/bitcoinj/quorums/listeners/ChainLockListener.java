package org.bitcoinj.quorums.listeners;

import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.quorums.ChainLockSignature;

/**
 * Created by hashengineering on 5/1/19.
 */
interface ChainLockListener {
    void onNewChainLock(StoredBlock block, ChainLockSignature clsig);
}
