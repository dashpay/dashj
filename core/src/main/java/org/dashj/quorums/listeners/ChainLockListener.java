package org.bitcoinj.quorums.listeners;

import org.bitcoinj.core.StoredBlock;

/**
 * The interface ChainLock listener.
 */
public interface ChainLockListener {
    /**
     * Called when a new chainlock is verified.
     *
     * @param block the block that is associated with the ChainLock
     */
    void onNewChainLock(StoredBlock block);
}
