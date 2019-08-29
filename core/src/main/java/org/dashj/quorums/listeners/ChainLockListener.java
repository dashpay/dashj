package org.dashj.quorums.listeners;

import org.dashj.core.StoredBlock;

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
