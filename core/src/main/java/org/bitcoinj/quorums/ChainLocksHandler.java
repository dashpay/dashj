package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.quorums.listeners.ChainLockListener;
import org.bitcoinj.quorums.listeners.RecoveredSignatureListener;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

public class ChainLocksHandler implements RecoveredSignatureListener {

    static final long CLEANUP_INTERVAL = 1000 * 30;
    static final long CLEANUP_SEEN_TIMEOUT = 24 * 60 * 60 * 1000;

    Context context;
    SigningManager quorumSigningManager;
    InstantSendManager quorumInstantSendManager;

    private static final Logger log = LoggerFactory.getLogger(ChainLocksHandler.class);
    ReentrantLock lock = Threading.lock("ChainLocksHandler");
    boolean tryLockChainTipScheduled;
    boolean isSporkActive;
    boolean isEnforced;
    AbstractBlockChain blockChain;

    Sha256Hash bestChainLockHash;
    ChainLockSignature bestChainLock;
    ChainLockSignature bestChainLockWithKnownBlock;
    StoredBlock bestChainLockBlock;
    StoredBlock lastNotifyChainLockBlock;

    long lastSignedHeight;
    Sha256Hash lastSignedRequestId;
    Sha256Hash lastSignedMsgHash;

    HashMap<Sha256Hash, Long> seenChainLocks;
    long lastCleanupTime;


    public ChainLocksHandler(Context context) {
        this.context = context;
        seenChainLocks = new HashMap<Sha256Hash, Long>();
        lastCleanupTime = 0;
        chainLockListeners = new CopyOnWriteArrayList<ListenerRegistration<ChainLockListener>>();
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        this.blockChain.addNewBestBlockListener(this.newBestBlockListener);
        this.quorumSigningManager = context.signingManager;
        this.quorumInstantSendManager = context.instantSendManager;
    }

    public void close() {
        this.blockChain.removeNewBestBlockListener(this.newBestBlockListener);
    }

    @Override
    public void onNewRecoveredSignature(RecoveredSignature recoveredSig) {
        ChainLockSignature clsig;
        {
            lock.lock();
            try {
                if (!isSporkActive) {
                    return;
                }

                if (recoveredSig.id != lastSignedRequestId || recoveredSig.msgHash != lastSignedMsgHash) {
                    // this is not what we signed, so lets not create a CLSIG for it
                    return;
                }
                if (bestChainLock.height >= lastSignedHeight) {
                    // already got the same or a better CLSIG through the CLSIG message
                    return;
                }

                clsig = new ChainLockSignature(lastSignedHeight, lastSignedMsgHash, recoveredSig.signature.getSignature());
            } finally {
                lock.unlock();
            }

        }
        processNewChainLock(null, clsig, clsig.getHash());
    }

    public void start()
    {
        quorumSigningManager.addRecoveredSignatureListener(this);
        //TODO: start the scheduler here:
        //  processChainLock();
    }

    public void stop()
    {
        quorumSigningManager.removeRecoveredSignatureListener(this);
    }

    void processChainLock() {
        checkActiveState();
        enforceBestChainLock();
        cleanup();
    }

    public boolean alreadyHave(InventoryItem inv)
    {
        return seenChainLocks.containsKey(inv.hash);
    }

    public ChainLockSignature getChainLockByHash(Sha256Hash hash)
    {
        lock.lock();
        try {
            if (hash != bestChainLockHash) {
                // we only propagate the best one and ditch all the old ones
                return null;
            }

            return bestChainLock;
        } finally {
            lock.unlock();
        }

    }

    public void processChainLockSignature(Peer peer, ChainLockSignature clsig)
    {
        if (!context.sporkManager.isSporkActive(SporkManager.SPORK_19_CHAINLOCKS_ENABLED)) {
            return;
        }

        Sha256Hash hash = clsig.getHash();
        processNewChainLock(peer, clsig, hash);
    }

    void processNewChainLock(Peer from, ChainLockSignature clsig, Sha256Hash hash)
    {

        lock.lock();
        try {
            if (seenChainLocks.put(hash, Utils.currentTimeMillis()) != null) {
                return;
            }

            if (bestChainLock != null && (bestChainLock.height != -1 && clsig.height <= bestChainLock.height)) {
                // no need to process/relay older CLSIGs
                return;
            }
        } finally {
            lock.unlock();
        }


        Sha256Hash requestId = clsig.getRequestId();
        Sha256Hash msgHash = clsig.blockHash;
        if (!quorumSigningManager.verifyRecoveredSig(context.getParams().getLlmqChainLocks(), clsig.height, requestId, msgHash, clsig.signature)) {
            log.info("invalid CLSIG ({}), peer={}",  clsig.toString(), from != null ? from : "null");
            if (from != null) {
                //LOCK(cs_main);
                //Misbehaving(from, 10);
            }
            return;
        }


        lock.lock();
        try {

            if (internalHasConflictingChainLock(clsig.height, clsig.blockHash)) {
                // This should not happen. If it happens, it means that a malicious entity controls a large part of the MN
                // network. In this case, we don't allow him to reorg older chainlocks.
                log.info("{} -- new CLSIG ({}) tries to reorg previous CLSIG ({}), peer={}",
                        clsig.toString(), bestChainLock.toString(), from);
                return;
            }

            bestChainLockHash = hash;
            bestChainLock = clsig;

            try {
                StoredBlock block = blockChain.getBlockStore().get(clsig.blockHash);
                if (block == null) {
                    // we don't know the block/header for this CLSIG yet, so bail out for now
                    // when the block or the header later comes in, we will enforce the correct chain
                    return;
                }
                if (block.getHeight() != clsig.height) {
                    // Should not happen, same as the conflict check from above.
                    log.info("{} -- height of CLSIG ({}) does not match the specified block's height (%d)",
                            clsig.toString(), block.getHeight());
                    return;
                }
                bestChainLockWithKnownBlock = bestChainLock;
                bestChainLockBlock = block;
            } catch (BlockStoreException x) {
                return;
            }

        } finally {
            lock.unlock();
        }

        //TODO: have this part executed in a different thread, run by a scheduler?
        processChainLock();

        log.info("processed new CLSIG ({}), peer={}", clsig.toString(), from);
    }

    void acceptedBlockHeader(StoredBlock newBlock)
    {
        lock.lock();
        try {

            if (newBlock.getHeader().getHash().equals(bestChainLock.blockHash)) {
                log.info("{} -- block header {} came in late, updating and enforcing\n", newBlock.getHeader().getHash().toString());

                if (bestChainLock.height != newBlock.getHeight()) {
                    // Should not happen, same as the conflict check from ProcessNewChainLock.
                    log.info("{} -- height of CLSIG ({}) does not match the specified block's height (%d)",
                            bestChainLock.toString(), newBlock.getHeight());
                    return;
                }

                // when EnforceBestChainLock is called later, it might end up invalidating other chains but not activating the
                // CLSIG locked chain. This happens when only the header is known but the block is still missing yet. The usual
                // block processing logic will handle this when the block arrives
                bestChainLockWithKnownBlock = bestChainLock;
                bestChainLockBlock = newBlock;
            }
        } finally {
            lock.unlock();
        }

    }

    void updatedBlockTip(StoredBlock newBlock, StoredBlock pindexFork)
    {
        // don't call TrySignChainTip directly but instead let the scheduler call it. This way we ensure that cs_main is
        // never locked and TrySignChainTip is not called twice in parallel. Also avoids recursive calls due to
        // EnforceBestChainLock switching chains.
        lock.lock();
        try {
            if (tryLockChainTipScheduled) {
                return;
            }
            tryLockChainTipScheduled = true;
            processChainLock();
            tryLockChainTipScheduled = false;
        } finally {
            lock.unlock();
        }

    }

    void checkActiveState()
    {
        //TODO: check if DIP8 is active here

        lock.lock();
        try {
            boolean oldIsEnforced = isEnforced;
            isSporkActive = context.sporkManager.isSporkActive(SporkManager.SPORK_19_CHAINLOCKS_ENABLED);
            // TODO remove this after DIP8 is active
            boolean fEnforcedBySpork = (context.getParams().getId().equals(NetworkParameters.ID_TESTNET) && (context.sporkManager.getSporkValue(SporkManager.SPORK_19_CHAINLOCKS_ENABLED) == 1));
            isEnforced = (/*fDIP0008Active &&*/ isSporkActive) || fEnforcedBySpork;

            if (!oldIsEnforced && isEnforced) {
                // ChainLocks got activated just recently, but it's possible that it was already running before, leaving
                // us with some stale values which we should not try to enforce anymore (there probably was a good reason
                // to disable spork19)
                bestChainLockHash = Sha256Hash.ZERO_HASH;
                bestChainLock = bestChainLockWithKnownBlock = null;
                bestChainLockBlock = lastNotifyChainLockBlock = null;
            }
        } finally {
            lock.unlock();
        }

    }

    public boolean isNewInstantSendEnabled()
    {
        return context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTSEND_ENABLED) &&
                context.sporkManager.isSporkActive(SporkManager.SPORK_20_INSTANTSEND_LLMQ_BASED);
    }

    void enforceBestChainLock()
    {
        ChainLockSignature clsig;
        StoredBlock pindex;
        StoredBlock currentBestChainLockBlock;
        {
            lock.lock();
            try {

                if (!isEnforced) {
                    return;
                }

                clsig = bestChainLockWithKnownBlock;
                pindex = currentBestChainLockBlock = this.bestChainLockBlock;

                if (currentBestChainLockBlock == null) {
                    // we don't have the header/block, so we can't do anything right now
                    return;
                }
            } finally {
                lock.unlock();
            }

        }
/*
        //TODO:  how do we handle this?
        boolean activateNeeded;
        {
            LOCK(cs_main);

            // Go backwards through the chain referenced by clsig until we find a block that is part of the main chain.
            // For each of these blocks, check if there are children that are NOT part of the chain referenced by clsig
            // and invalidate each of them.
            while (pindex && !chainActive.Contains(pindex)) {
                // Invalidate all blocks that have the same prevBlockHash but are not equal to blockHash
                auto itp = mapPrevBlockIndex.equal_range(pindex->pprev->getBlockHash());
                for (auto jt = itp.first; jt != itp.second; ++jt) {
                    if (jt->second == pindex) {
                        continue;
                    }
                    log.info(("{} -- CLSIG ({}) invalidates block {}\n",
                             clsig.toString(), jt->second->getBlockHash().toString());
                    DoInvalidateBlock(jt->second, false);
                }

                pindex = pindex->pprev;
            }
            // In case blocks from the correct chain are invalid at the moment, reconsider them. The only case where this
            // can happen right now is when missing superblock triggers caused the main chain to be dismissed first. When
            // the trigger later appears, this should bring us to the correct chain eventually. Please note that this does
            // NOT enforce invalid blocks in any way, it just causes re-validation.
            if (!currentBestChainLockBlockIndex->IsValid()) {
                ResetBlockFailureFlags(mapBlockIndex.at(currentBestChainLockBlockIndex->getBlockHash()));
            }

            activateNeeded = chainActive.Tip()->getAncestor(currentBestChainLockBlockIndex->height) != currentBestChainLockBlockIndex;
        }

        CValidationState state;
        if (activateNeeded && !ActivateBestChain(state, Params())) {
            log.info(("{} -- ActivateBestChain failed: {}\n",  FormatStateMessage(state));
        }
        */
        lock.lock();
        try {
            StoredBlock blockNotify = null;
            if (lastNotifyChainLockBlock == null || (!lastNotifyChainLockBlock.equals(currentBestChainLockBlock)) &&
                    blockChain.getBlockStore().get(currentBestChainLockBlock.getHeight()).equals(currentBestChainLockBlock)) {
                lastNotifyChainLockBlock = currentBestChainLockBlock;
                blockNotify = currentBestChainLockBlock;
            }

            if (blockNotify != null) {
                queueChainLockListeners(blockNotify);
            }
        } catch (BlockStoreException x) {

        } finally {
            lock.unlock();
        }

    }

    /*
    // TODO: How do we handle this?
    // WARNING, do not hold cs while calling this method as we'll otherwise run into a deadlock
    void doInvalidateBlock(StoredBlock pindex, boolean activateBestChain)
    {
        auto& params = Params();

        {
            LOCK(cs_main);

            // get the non-const pointer
            CBlockIndex* pindex2 = mapBlockIndex[pindex->getBlockHash()];

            CValidationState state;
            if (!InvalidateBlock(state, params, pindex2)) {
                log.info(("{} -- InvalidateBlock failed: {}\n",  FormatStateMessage(state));
                // This should not have happened and we are in a state were it's not safe to continue anymore
                assert(false);
            }
        }

        CValidationState state;
        if (activateBestChain && !ActivateBestChain(state, params)) {
            log.info(("{} -- ActivateBestChain failed: {}\n",  FormatStateMessage(state));
            // This should not have happened and we are in a state were it's not safe to continue anymore
            assert(false);
        }
    }
*/
    public boolean hasChainLock(long height, Sha256Hash blockHash)
    {
        lock.lock();
        try {
            return internalHasChainLock(height, blockHash);
        } finally {
            lock.unlock();
        }
    }

    boolean internalHasChainLock(long height, Sha256Hash blockHash)
    {
        checkState(lock.isHeldByCurrentThread());
        try {
            if (!isEnforced) {
                return false;
            }

            if (bestChainLockBlock == null) {
                return false;
            }

            if (height > bestChainLockBlock.getHeight()) {
                return false;
            }

            if (height == bestChainLockBlock.getHeight()) {
                return blockHash == bestChainLockBlock.getHeader().getHash();
            }

            StoredBlock cursor = bestChainLockBlock;
            while(cursor != null) {
                cursor = cursor.getPrev(blockChain.getBlockStore());
            }
            return cursor != null && cursor.getHeader().getHash().equals(blockHash);
        } catch (BlockStoreException x) {
            return false;
        }
    }

    public boolean hasConflictingChainLock(long height, Sha256Hash blockHash)
    {
        lock.lock();
        try {
            return internalHasConflictingChainLock(height, blockHash);
        } finally {
            lock.unlock();
        }
    }

    boolean internalHasConflictingChainLock(long height, Sha256Hash blockHash)
    {
        checkState(lock.isHeldByCurrentThread());
        try {
            if (!isEnforced) {
                return false;
            }

            if(bestChainLockBlock == null)
                return false;

            if (height > bestChainLockBlock.getHeight()) {
                return false;
            }

            if (height == bestChainLockBlock.getHeight()) {
                return blockHash != bestChainLockBlock.getHeader().getHash();
            }

            StoredBlock cursor = bestChainLockBlock;
            while(cursor != null) {
                cursor = cursor.getPrev(blockChain.getBlockStore());
            }
            return cursor != null && !cursor.getHeader().getHash().equals(blockHash);
        } catch (BlockStoreException x) {
            return false;
        }

    }

    void cleanup()
    {
        lock.lock();
        try {
            if (Utils.currentTimeMillis() - lastCleanupTime < CLEANUP_INTERVAL) {
                return;
            }
        } finally {
            lock.unlock();
        }

        lock.lock();
        try {
            Iterator<Map.Entry<Sha256Hash, Long>> it = seenChainLocks.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<Sha256Hash, Long> entry = it.next();
                if (Utils.currentTimeMillis() - entry.getValue() >= CLEANUP_SEEN_TIMEOUT) {
                    it.remove();
                }
            }

            lastCleanupTime = Utils.currentTimeMillis();
        } finally {
            lock.unlock();
        }

    }

    NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            updatedBlockTip(block, null);
        }
    };

    private transient CopyOnWriteArrayList<ListenerRegistration<ChainLockListener>> chainLockListeners;

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addChainLockListener(ChainLockListener listener) {
        addChainLockListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addChainLockListener(ChainLockListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        chainLockListeners.add(new ListenerRegistration<ChainLockListener>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeChainLockListener(ChainLockListener listener) {
        return ListenerRegistration.removeFromList(listener, chainLockListeners);
    }

    private void queueChainLockListeners(final StoredBlock block) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<ChainLockListener> registration : chainLockListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onNewChainLock(block);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onNewChainLock(block);
                    }
                });
            }
        }
    }

    public StoredBlock getBestChainLockBlock() {
        return bestChainLockBlock;
    }

    public Sha256Hash getBestChainLockHash() {
        return bestChainLockHash;
    }

    public long getLastSignedHeight() {
        return lastSignedHeight;
    }

    public Sha256Hash getLastSignedRequestId() {
        return lastSignedRequestId;
    }
}
