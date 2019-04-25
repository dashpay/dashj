package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.crypto.BLSBatchVerifier;
import org.bitcoinj.quorums.listeners.RecoveredSignatureListener;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class ChainLockHandler implements RecoveredSignatureListener {

    static final long CLEANUP_INTERVAL = 1000 * 30;
    static final long CLEANUP_SEEN_TIMEOUT = 24 * 60 * 60 * 1000;

    // how long to wait for ixlocks until we consider a block with non-ixlocked TXs to be safe to sign
    static final long WAIT_FOR_ISLOCK_TIMEOUT = 10 * 60;

    Context context;
    SigningManager quorumSigningManager;
    InstantSendManager quorumInstantSendManager;

    private static final Logger log = LoggerFactory.getLogger(ChainLockHandler.class);
    ReentrantLock lock = Threading.lock("ChainLockHandler");
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

    // We keep track of txids from recently received blocks so that we can check if all TXs got ixlocked
    HashMap<Sha256Hash, HashSet<Sha256Hash>> blockTxs;
    HashMap<Sha256Hash, Long> txFirstSeenTime;

    HashMap<Sha256Hash, Long> seenChainLocks;

    long lastCleanupTime;


    public ChainLockHandler(Context context) {
        this.context = context;
        blockTxs = new HashMap<Sha256Hash, HashSet<Sha256Hash>>();
        txFirstSeenTime = new HashMap<Sha256Hash, Long>();
        seenChainLocks = new HashMap<Sha256Hash, Long>();
        lastCleanupTime = 0;
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        this.blockChain.addTransactionReceivedListener(this.transactionReceivedInBlockListener);
        this.quorumSigningManager = context.signingManager;
        this.quorumInstantSendManager = context.instantSendManager;
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

    void start()
    {
        quorumSigningManager.addRecoveredSignatureListener(this);
        /*scheduler->scheduleEvery([&]() {
        CheckActiveState();
        EnforceBestChainLock();
        // regularly retry signing the current chaintip as it might have failed before due to missing ixlocks
        TrySignChainTip();
    }, 5000);*/
    }

    void stop()
    {
        quorumSigningManager.removeRecoveredSignatureListener(this);
    }

    public boolean alreadyHave(InventoryItem inv)
    {
        return seenChainLocks.containsKey(inv.hash);
    }

    ChainLockSignature getChainLockByHash(Sha256Hash hash)
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
            log.info("{} -- invalid CLSIG ({}), peer={}",  clsig.toString(), from);
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


        /*scheduler->scheduleFromNow([&]() {
        CheckActiveState();
        EnforceBestChainLock();
    }, 0);*/

        log.info("chainlocks {} -- processed new CLSIG ({}), peer={}",
                 clsig.toString(), from);
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
           /* scheduler -> scheduleFromNow([ &]() {
                CheckActiveState();
                EnforceBestChainLock();
                TrySignChainTip();
                LOCK(cs);
                tryLockChainTipScheduled = false;
            },0);*/
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


    public void syncTransaction(Transaction tx, StoredBlock block, boolean inBlock)
    {
        boolean handleTx = true;
        if (tx.isCoinBase() || tx.getInputs().isEmpty()) {
            handleTx = false;
        }

        lock.lock();
        try {
            if (handleTx) {
                long curTime = Utils.currentTimeSeconds();
                txFirstSeenTime.put(tx.getHash(), curTime);
            }

            // We listen for SyncTransaction so that we can collect all TX ids of all included TXs of newly received blocks
            // We need this information later when we try to sign a new tip, so that we can determine if all included TXs are
            // safe.
            if (block != null && !inBlock) {
                HashSet<Sha256Hash> txs = blockTxs.get(block.getHeader().getHash());
                if (txs == null) {
                    // we want this to be run even if handleTx == false, so that the coinbase TX triggers creation of an empty entry
                    txs = new HashSet<Sha256Hash>();
                    blockTxs.put(block.getHeader().getHash(), txs);
                }
                if (handleTx) {
                    txs.add(tx.getHash());
                }
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

    boolean isTxSafeForMining(Sha256Hash txid)
    {
        if (!context.sporkManager.isSporkActive(SporkManager.SPORK_3_INSTANTSEND_BLOCK_FILTERING)) {
            return true;
        }
        if (!isNewInstantSendEnabled()) {
            return true;
        }

        long txAge = 0;

        lock.lock();
        try {
            if (!isSporkActive) {
                return true;
            }
            Long time = txFirstSeenTime.get(txid);
            if (time != null) {
                txAge = Utils.currentTimeSeconds() - time;
            }
        } finally {
            lock.unlock();
        }


        if (txAge < WAIT_FOR_ISLOCK_TIMEOUT && !quorumInstantSendManager.isLocked(txid)) {
            return false;
        }
        return true;
    }

    // WARNING: cs_main and cs should not be held!
// This should also not be called from validation signals, as this might result in recursive calls
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

    StoredBlock pindexNotify = nullptr;
        {
            LOCK(cs_main);
            if (lastNotifyChainLockBlockIndex != currentBestChainLockBlockIndex &&
                    chainActive.Tip()->getAncestor(currentBestChainLockBlockIndex->height) == currentBestChainLockBlockIndex) {
            lastNotifyChainLockBlockIndex = currentBestChainLockBlockIndex;
            pindexNotify = currentBestChainLockBlockIndex;
        }
        }

        if (pindexNotify) {
            getMainSignals().NotifyChainLock(pindexNotify);
        }*/
    }

    /*
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
        lock.lock();
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
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            if (!isEnforced) {
                return false;
            }

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
        } finally {
            lock.unlock();
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
/*
        lock.lock();
        try {
            Iterator<Map.Entry<Sha256Hash, Long>> it = seenChainLocks.entrySet().iterator()
            while(it.hasNext()) {
                Map.Entry<Sha256Hash, Long> entry = it.next();
                if (Utils.currentTimeMillis() - entry.getValue() >= CLEANUP_SEEN_TIMEOUT) {
                    it.remove();;
                }
            }

            Iterator<Map.Entry<Sha256Hash, HashSet<Sha256Hash>>> txs = blockTxs.entrySet().iterator();
            while(txs.hasNext()) {
                Map.Entry<Sha256Hash, HashSet<Sha256Hash>> entry = txs.next();
                StoredBlock block = mapBlockIndex.at(it -> first);
                if (internalHasChainLock(block.getHeight(), block.getHeader().getHash())) {
                    for (Sha256Hash txid : entry.getValue()){
                        txFirstSeenTime.remove(txid);
                    }
                    txs.remove();
                } else if (internalHasConflictingChainLock(block.getHeight(), block.getHeader().getHash())) {
                    txs.remove();
                }
            }

            Iterator<Map.Entry<Sha256Hash, Long>> it2 = txFirstSeenTime.entrySet().iterator();
            while (it2.hasNext()) {
                Map.Entry<Sha256Hash, Long> entry = it2.next();
                Transaction tx;
                Sha256Hash hashBlock;
                if (!GetTransaction(it -> first, tx, Params().GetConsensus(), hashBlock)) {
                    // tx has vanished, probably due to conflicts
                    it = txFirstSeenTime.erase(it);
                } else if (!hashBlock.IsNull()) {
                    StoredBlock block = blockChain.getBlockStore().get(hashBlock);
                    if (chainActive.Tip()->
                    GetAncestor(pindex -> height) == pindex && chainActive.Height() - pindex -> height >= 6){
                        // tx got confirmed >= 6 times, so we can stop keeping track of it
                        it2.remove();
                    }
                }
            }

            lastCleanupTime = Utils.currentTimeMillis();
        } finally {
            lock.unlock();
        }
*/
    }

    TransactionReceivedInBlockListener transactionReceivedInBlockListener = new TransactionReceivedInBlockListener() {
        @Override
        public void receiveFromBlock(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {

            // Call syncTransaction to update lock candidates and votes
            if(blockType == AbstractBlockChain.NewBlockType.BEST_CHAIN) {
                syncTransaction(tx, block, true);
            }
        }

        @Override
        public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
            return false;
        }
    };
}
