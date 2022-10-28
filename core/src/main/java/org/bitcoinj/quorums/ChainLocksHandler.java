/*
 * Copyright 2019 Dash Core Group
 *
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
package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.quorums.listeners.ChainLockListener;
import org.bitcoinj.quorums.listeners.RecoveredSignatureListener;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.dashj.bls.PrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

public class ChainLocksHandler extends AbstractManager implements RecoveredSignatureListener {

    static final long CLEANUP_INTERVAL = 1000 * 30;
    static final long CLEANUP_SEEN_TIMEOUT = 24 * 60 * 60 * 1000;

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

    HashMap<Sha256Hash, Long> seenChainLocks;
    long lastCleanupTime;

    ScheduledExecutorService scheduledExecutorService;
    ScheduledFuture<?> scheduledProcessChainLock;

    public ChainLocksHandler(Context context) {
        super(context);
        seenChainLocks = new HashMap<Sha256Hash, Long>();
        lastCleanupTime = 0;
        chainLockListeners = new CopyOnWriteArrayList<ListenerRegistration<ChainLockListener>>();
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        this.blockChain.addNewBestBlockListener(Threading.SAME_THREAD, this.newBestBlockListener);
        this.quorumSigningManager = context.signingManager;
        this.quorumInstantSendManager = context.instantSendManager;
    }

    public void close() {
        this.blockChain.removeNewBestBlockListener(this.newBestBlockListener);
        super.close();
    }

    @Override
    public void onNewRecoveredSignature(RecoveredSignature recoveredSig) {
        //do nothing.  In Dash Core, this handles signing CLSIG's
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
        unCache();
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
        if (!context.sporkManager.isSporkActive(SporkId.SPORK_19_CHAINLOCKS_ENABLED)) {
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

        log.info("process chainlock: {}", hash);
        Sha256Hash requestId = clsig.getRequestId();
        Sha256Hash msgHash = clsig.blockHash;
        if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.CHAINLOCK)) {
            try {
                if (!quorumSigningManager.verifyRecoveredSig(context.getParams().getLlmqChainLocks(), clsig.height, requestId, msgHash, clsig.signature)) {
                    log.info("invalid CLSIG ({}), peer={}", clsig, from != null ? from : "null");
                    if (from != null) {
                        // TODO: Dash Core increases ban score by 10
                    }
                    return;
                }
                saveLater();
            } catch (BlockStoreException x) {
                return;
            } catch (QuorumNotFoundException x) {

                if(scheduledProcessChainLock != null && !scheduledProcessChainLock.isDone())
                    scheduledProcessChainLock.cancel(true);

                log.info("ChainLock not verified due to missing quorum, try again in 5 seconds");
                //schedule this to be checked again in 1 second
                scheduledProcessChainLock = scheduledExecutorService.schedule(new Runnable() {
                                                              public void run() {
                                                                  // clear the clsig from the seen list
                                                                  seenChainLocks.remove(hash);
                                                                  processNewChainLock(null, clsig, hash);
                                                              }
                                                          }, 5, TimeUnit.SECONDS);

                return;
            }
        }

        lock.lock();
        try {

            if (internalHasConflictingChainLock(clsig.height, clsig.blockHash)) {
                // This should not happen. If it happens, it means that a malicious entity controls a large part of the MN
                // network. In this case, we don't allow him to reorg older chainlocks.
                log.info("new CLSIG ({}) tries to reorg previous CLSIG ({}), peer={}", clsig, bestChainLock, from);
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
                            clsig, block.getHeight());
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

        log.info("processed new CLSIG ({}), peer={}", clsig, from);
    }

    void acceptedBlockHeader(StoredBlock newBlock) {
        lock.lock();
        try {

            if (newBlock.getHeader().getHash().equals(bestChainLock.blockHash)) {
                log.info("block header {} came in late, updating and enforcing", newBlock.getHeader().getHash().toString());

                if (bestChainLock.height != newBlock.getHeight()) {
                    // Should not happen, same as the conflict check from ProcessNewChainLock.
                    log.info("height of CLSIG ({}) does not match the specified block's height ({})",
                            bestChainLock, newBlock.getHeight());
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

    void updatedBlockTip(StoredBlock newBlock, StoredBlock forkBlock) {
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

    void checkActiveState() {
        //TODO: check if DIP8 is active here
        boolean isDIP0008Active = (blockChain.getBestChainHeight() - 1) > params.getDIP0008BlockHeight();

        boolean oldIsEnforced = isEnforced;
        isSporkActive = context.sporkManager.isSporkActive(SporkId.SPORK_19_CHAINLOCKS_ENABLED);
        isEnforced = (isDIP0008Active && isSporkActive);

        if (!oldIsEnforced && isEnforced) {
            // ChainLocks got activated just recently, but it's possible that it was already running before, leaving
            // us with some stale values which we should not try to enforce anymore (there probably was a good reason
            // to disable spork19)
            lock.lock();
            try {
                bestChainLockHash = Sha256Hash.ZERO_HASH;
                bestChainLock = bestChainLockWithKnownBlock = null;
                bestChainLockBlock = lastNotifyChainLockBlock = null;
            } finally {
                lock.unlock();
            }
        }
    }

    public boolean isNewInstantSendEnabled() {
        return context.sporkManager.isSporkActive(SporkId.SPORK_2_INSTANTSEND_ENABLED);
    }

    void enforceBestChainLock() {
        ChainLockSignature clsig;
        StoredBlock currentBestBlock;
        StoredBlock currentBestChainLockBlock;

        lock.lock();
        try {

            if (!isEnforced) {
                return;
            }

            clsig = bestChainLockWithKnownBlock;
            currentBestBlock = currentBestChainLockBlock = this.bestChainLockBlock;

            if (currentBestChainLockBlock == null) {
                // we don't have the header/block, so we can't do anything right now
                return;
            }
        } finally {
            lock.unlock();
        }
        log.info("enforceBestChainLock -- enforcing block {} via CLSIG ({})", currentBestBlock.getHeader().getHash(),
                clsig);

        lock.lock();
        try {
            StoredBlock blockNotify = null;
            if (lastNotifyChainLockBlock == null || (!lastNotifyChainLockBlock.equals(currentBestChainLockBlock)) &&
                    blockChain.getBlockStore().get(currentBestChainLockBlock.getHeight()).equals(currentBestChainLockBlock)) {
                lastNotifyChainLockBlock = currentBestChainLockBlock;
                blockNotify = currentBestChainLockBlock;
            }

            if (blockNotify != null) {
                // one of the listeners will handle reorgs
                queueChainLockListeners(blockNotify);
                ArrayList<TransactionConfidence> confidences = context.getConfidenceTable().get(blockNotify);
                for (TransactionConfidence confidence : confidences) {
                    confidence.setChainLock(true);
                    confidence.queueListeners(TransactionConfidence.Listener.ChangeReason.CHAIN_LOCKED);
                }
            }
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        } finally {
            lock.unlock();
        }

    }

    public boolean hasChainLock(long height, Sha256Hash blockHash) {
        lock.lock();
        try {
            return internalHasChainLock(height, blockHash);
        } finally {
            lock.unlock();
        }
    }

    boolean internalHasChainLock(long height, Sha256Hash blockHash) {
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

    public boolean hasConflictingChainLock(long height, Sha256Hash blockHash) {
        lock.lock();
        try {
            return internalHasConflictingChainLock(height, blockHash);
        } finally {
            lock.unlock();
        }
    }

    boolean internalHasConflictingChainLock(long height, Sha256Hash blockHash) {
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

    void cleanup() {
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

    public int getBestChainLockBlockHeight() {
        return bestChainLockBlock != null ? bestChainLockBlock.getHeight() : -1;
    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }

    @Override
    public void checkAndRemove() {

    }

    @Override
    public void clear() {

    }

    @Override
    public AbstractManager createEmpty() {
        return new ChainLocksHandler(Context.get());
    }

    @Override
    protected void parse() throws ProtocolException {
        if(payload.length > 0) {
            ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
            buffer.put(readBytes(StoredBlock.COMPACT_SERIALIZED_SIZE));
            buffer.rewind();
            bestChainLockBlock = StoredBlock.deserializeCompact(params, buffer);
            bestChainLockHash = bestChainLockBlock.getHeader().getHash();
        }
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        if(bestChainLockBlock != null) {
            ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
            bestChainLockBlock.serializeCompact(buffer);
            stream.write(buffer.array());
        }
    }

    @Override
    public String toString() {
        return "ChainLocksHandler(" + (bestChainLockHash != null ? bestChainLockHash : "no chain locked") + ")";
    }

    public void setBestChainLockBlockMock(StoredBlock bestChainLockBlock) {
        this.bestChainLockBlock = bestChainLockBlock;
        BLSSecretKey secretKey = new BLSSecretKey(PrivateKey.fromSeedBIP32(bestChainLockBlock.getHeader().getHash().getBytes()));
        BLSSignature signature = secretKey.sign(bestChainLockBlock.getHeader().getHash());
        this.bestChainLock = new ChainLockSignature(bestChainLockBlock.getHeight(), bestChainLockBlock.getHeader().getHash(), signature);
        this.bestChainLockHash = bestChainLockBlock.getHeader().getHash();
        blockChain.handleChainLock(bestChainLockBlock);
    }

    public void setBestChainLockBlockMock(Block bestChainLockBlock, int height) {
        StoredBlock storedBlock = new StoredBlock(bestChainLockBlock, BigInteger.ONE, height);
        setBestChainLockBlockMock(storedBlock);
    }
}
