package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.crypto.BLSBatchVerifier;
import org.bitcoinj.quorums.listeners.RecoveredSignatureListener;
import org.bitcoinj.quorums.listeners.ChainLockListener;
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
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class InstantSendManager implements RecoveredSignatureListener {

    Context context;
    SigningManager quorumSigningManager;

    private static final Logger log = LoggerFactory.getLogger(InstantSendManager.class);
    ReentrantLock lock = Threading.lock("InstantSendManager");
    InstantSendDatabase db;
    Thread workThread;
    public boolean runWithoutThread;
    AbstractBlockChain blockChain;

    HashMap<Sha256Hash, Transaction> mapTx;
    HashSet<InstantSendLock> invalidInstantSendLocks;

    // Incoming and not verified yet
    HashMap<Sha256Hash, Pair<Long, InstantSendLock>> pendingInstantSendLocks;

    public InstantSendManager(Context context, InstantSendDatabase db) {
        this.context = context;
        this.db = db;
        this.quorumSigningManager = context.signingManager;
        pendingInstantSendLocks = new HashMap<Sha256Hash, Pair<Long, InstantSendLock>>();
        mapTx = new HashMap<Sha256Hash, Transaction>();
        invalidInstantSendLocks = new HashSet<InstantSendLock>();
    }

    @Override
    public String toString() {
        return String.format("InstantSendManager:  pendingInstantSendLocks %d, mapTx: %d, DB: %s", pendingInstantSendLocks.size(),
                mapTx.size(), db);
    }

    public InstantSendManager(Context context, InstantSendDatabase db, boolean runWithoutThread) {
        this(context, db);
        this.runWithoutThread = runWithoutThread;
    }

    public void setBlockChain(AbstractBlockChain blockChain, PeerGroup peerGroup) {
        this.blockChain = blockChain;
        this.blockChain.addTransactionReceivedListener(this.transactionReceivedInBlockListener);
        this.blockChain.addNewBestBlockListener(this.newBestBlockListener);
        peerGroup.addOnTransactionBroadcastListener(this.transactionBroadcastListener);
        context.chainLockHandler.addChainLockListener(this.chainLockListener, Threading.SAME_THREAD);
    }

    public void close(PeerGroup peerGroup) {
        blockChain.removeTransactionReceivedListener(this.transactionReceivedInBlockListener);
        blockChain.removeNewBestBlockListener(this.newBestBlockListener);
        peerGroup.removeOnTransactionBroadcastListener(this.transactionBroadcastListener);
        context.chainLockHandler.removeChainLockListener(this.chainLockListener);
    }

    public boolean isOldInstantSendEnabled()
    {
        return context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTSEND_ENABLED) &&
                !context.sporkManager.isSporkActive(SporkManager.SPORK_20_INSTANTSEND_LLMQ_BASED);
    }

    public boolean isNewInstantSendEnabled()
    {
        return context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTSEND_ENABLED) &&
                context.sporkManager.isSporkActive(SporkManager.SPORK_20_INSTANTSEND_LLMQ_BASED);
    }

    public boolean isInstantSendEnabled()
    {
        return context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTSEND_ENABLED);
    }

    public void processInstantSendLock(Peer peer, InstantSendLock isLock) {
        if(!isNewInstantSendEnabled())
            return;

        if (!preVerifyInstantSendLock(peer.hashCode(), isLock)) {
             return;
        }

        Sha256Hash hash = isLock.getHash();

        lock.lock();
        try {
            //TODO: how to handle this!
            if (db.getInstantSendLockByHash(hash) != null) {
                return;
            }
            if (pendingInstantSendLocks.containsKey(hash)) {
                return;
            }

            log.info("received islock:  txid={}, islock={} , peer={}",
                    isLock.txid.toString(), hash.toString(), peer.hashCode());

            pendingInstantSendLocks.put(hash, new Pair((long)peer.hashCode(), isLock));

            if(runWithoutThread) {
                processPendingInstantSendLocks();
            }
        } finally {
            lock.unlock();
        }
    }

    boolean preVerifyInstantSendLock(int nodeId, InstantSendLock islock)
    {

        if (islock.txid.equals(Sha256Hash.ZERO_HASH) || islock.inputs.isEmpty()) {
            return false;
        }

        HashSet<TransactionOutPoint> dups = new HashSet<TransactionOutPoint>();
        for (TransactionOutPoint o : islock.inputs) {
            if (!dups.add(o)) {
                return false;
            }
        }

        return true;
    }

    public boolean alreadyHave(InventoryItem inv)
    {
        if (!isNewInstantSendEnabled()) {
            return true;
        }

        lock.lock();
        try {
            boolean haslock = db.getInstantSendLockByHash(inv.hash) != null || pendingInstantSendLocks.containsKey(inv.hash);
            if(!invalidInstantSendLocks.isEmpty()) {
                for(InstantSendLock islock : invalidInstantSendLocks) {
                    if(inv.hash.equals(islock.getHash()))
                        return true;
                }
            }
            return haslock;
        } finally {
            lock.unlock();
        }
    }

    Runnable workerMainThread = new Runnable() {
        @Override
        public void run() {
            try {
                while (!workThread.isInterrupted()) {
                    boolean didWork = false;

                    didWork |= processPendingInstantSendLocks();

                    if (!didWork) {
                        Thread.sleep(100);
                    }
                }
            } catch (InterruptedException x) {
                //let the thread stop
            }
        }
    };

    public void start() {
        if(!runWithoutThread) {
            if (workThread != null)
                throw new IllegalThreadStateException("Thread is already running");

            workThread = new Thread(workerMainThread);
            workThread.start();
        }
        quorumSigningManager.addRecoveredSignatureListener(this);
    }

    public void stop() {

        quorumSigningManager.removeRecoveredSignatureListener(this);
        if(runWithoutThread)
            return;

        if(workThread == null)
            throw new IllegalThreadStateException("Thread is not running");

        try {

            if (!workThread.isInterrupted()) {
                workThread.join();
                workThread = null;
            } else {
                throw new IllegalThreadStateException("Thread was not interrupted");
            }
        } catch (InterruptedException x) {
            throw new IllegalThreadStateException("Thread was interrupted while waiting for it to die");
        }
    }

    void interuptWorkerThread() {
        workThread.interrupt();
    }

    public boolean checkCanLock(Transaction tx) {
        return checkCanLock(tx, false);
    }

    public boolean checkCanLock(Transaction tx, boolean printDebug)
    {
        if (context.sporkManager.isSporkActive(SporkManager.SPORK_16_INSTANTSEND_AUTOLOCKS) ) {
            return false;
        }

        if (tx.getInputs().isEmpty()) {
            // can't lock TXs without inputs (e.g. quorum commitments)
            return false;
        }

        BlockStore blockStore = blockChain.getBlockStore();
        Coin value = Coin.valueOf(0);
        for (TransactionInput  in : tx.getInputs()) {
            if (!checkCanLock(in.getOutpoint(), printDebug, tx.getHash())) {
                return false;
            }

            if(blockStore instanceof FullPrunedBlockStore) {
                // this relies on enabled txindex and won't work if we ever try to remove the requirement for txindex for masternodes
                try {
                    UTXO utxo = ((FullPrunedBlockStore) blockStore).getTransactionOutput(in.getOutpoint().getHash(), in.getOutpoint().getIndex());
                    value = value.add(utxo.getValue());

                } catch (BlockStoreException x) {
                    log.error("BlockStoreException:  "+ x.getMessage());
                }

            } else {
                if(in.getValue() != null)
                    value = value.add(in.getValue());
            }
        }

        // TODO decide if we should limit max input values. This was ok to do in the old system, but in the new system
        // where we want to have all TXs locked at some point, this is counterproductive (especially when ChainLocks later
        // depend on all TXs being locked first)
        Coin maxValueIn = Coin.valueOf(context.sporkManager.getSporkValue(SporkManager.SPORK_5_INSTANTSEND_MAX_VALUE));

        if (value.compareTo(maxValueIn) > 0) {
            if (printDebug) {
                log.info("txid={}: TX input value too high. nValueIn={}, maxValueIn={}",
                         tx.getHash().toString(), value, maxValueIn);
            }
            return false;
        }

        return true;
    }

    boolean checkCanLock(TransactionOutPoint outpoint, boolean printDebug, Sha256Hash txHash)
    {
        int nInstantSendConfirmationsRequired = context.getParams().getInstantSendConfirmationsRequired();

        if (isLocked(outpoint.getHash())) {
            // if prevout was ix locked, allow locking of descendants (no matter if prevout is in mempool or already mined)
            return true;
        }

        TxConfidenceTable mempool = context.getConfidenceTable();
        TransactionConfidence mempoolTx = mempool.get(outpoint.getHash());
        if (mempoolTx != null) {
            if (printDebug) {
                log.info("txid={}: parent mempool TX {} is not locked",
                        txHash.toString(), outpoint.getHash().toString());
            }
            return false;
        }

        Transaction tx;
        Sha256Hash hashBlock = null;
        BlockStore blockStore = blockChain.getBlockStore();
        UTXO utxo;
        if(blockStore instanceof FullPrunedBlockStore) {
            // this relies on enabled txindex and won't work if we ever try to remove the requirement for txindex for masternodes
            try {
                utxo = ((FullPrunedBlockStore) blockStore).getTransactionOutput(outpoint.getHash(), outpoint.getIndex());
                StoredBlock block = blockStore.get(utxo.getHeight());
                if(block != null)
                    hashBlock = block.getHeader().getHash();
                if (printDebug) {
                    log.info("txid={}: failed to find parent TX {}",
                            txHash.toString(), outpoint.getHash().toString());
                    return false;
                }

            } catch (BlockStoreException x) {
                log.error("BlockStoreException:  "+ x.getMessage());
                return false;
            }

            try {
                if(hashBlock == null)
                    return false;

                StoredUndoableBlock block = ((FullPrunedBlockStore) blockStore).getUndoBlock(hashBlock);

                int txAge = blockChain.getBestChainHeight() - utxo.getHeight();
                if (txAge < nInstantSendConfirmationsRequired) {
                    if (context.chainLockHandler.hasChainLock(utxo.getHeight(), block.getHash())) {
                        if (printDebug) {
                            log.info("txid={}: outpoint {} too new and not ChainLocked. nTxAge={}, nInstantSendConfirmationsRequired={}",
                                    txHash.toString(), outpoint.toStringShort(), txAge, nInstantSendConfirmationsRequired);
                        }
                        return false;
                    }
                }
            } catch (BlockStoreException x) {
                //swallow
                return false;
            }
        } else {
            try {
                TransactionOutput output = outpoint.getConnectedOutput();
                if(output != null) {
                    Transaction parent = output.getParentTransaction();
                    TransactionConfidence confidence = parent.getConfidence();
                    if(confidence != null) {
                        if (confidence.getDepthInBlocks() < nInstantSendConfirmationsRequired) {
                            StoredBlock block = blockStore.get(confidence.getAppearedAtChainHeight());
                            if (context.chainLockHandler.hasChainLock(confidence.getAppearedAtChainHeight(), block.getHeader().getHash()))
                            {
                                if (printDebug) {
                                    log.info("txid={}: outpoint {} too new and not ChainLocked. nTxAge={}, nInstantSendConfirmationsRequired={}",
                                            txHash.toString(), outpoint.toStringShort(), confidence.getDepthInBlocks(), nInstantSendConfirmationsRequired);
                                }
                                return false;
                            }
                        }
                    }
                }
            } catch (BlockStoreException x) {
                return false;
            }
        }

        return true;
    }

    boolean processPendingInstantSendLocks()
    {
        LLMQParameters.LLMQType llmqType = context.getParams().getLlmqForInstantSend();

        HashMap<Sha256Hash, Pair<Long, InstantSendLock>> pend; 

        lock.lock();
        
        try {
            pend = new HashMap<Sha256Hash, Pair<Long, InstantSendLock>>(pendingInstantSendLocks);
            pendingInstantSendLocks = new HashMap<Sha256Hash, Pair<Long, InstantSendLock>>();
        } finally {
            lock.unlock();
        }

        if (pend.isEmpty()) {
            return false;
        }

        if (!isNewInstantSendEnabled()) {
            return false;
        }

        int tipHeight;
        tipHeight = blockChain.getBestChainHeight();


        BLSBatchVerifier<Long, Sha256Hash> batchVerifier = new BLSBatchVerifier<Long, Sha256Hash>(false, true, 8);
        HashMap<Sha256Hash, Pair<Quorum, RecoveredSignature>> recSigs = new HashMap<Sha256Hash, Pair<Quorum, RecoveredSignature>>();

        for (Map.Entry<Sha256Hash, Pair<Long, InstantSendLock>> p : pend.entrySet()) {
            Sha256Hash hash = p.getKey();
            long nodeId = p.getValue().getFirst();
            InstantSendLock islock = p.getValue().getSecond();
    
            if (batchVerifier.getBadSources().contains(nodeId)) {
                continue;
            }
    
            if (!islock.signature.getSignature().isValid()) {
                batchVerifier.getBadSources().add(nodeId);
                continue;
            }
    
            Sha256Hash id = islock.getRequestId();
    
            // no need to verify an ISLOCK if we already have verified the recovered sig that belongs to it
            if (quorumSigningManager.hasRecoveredSig(llmqType, id, islock.txid)) {
                continue;
            }
    
            Quorum quorum = quorumSigningManager.selectQuorumForSigning(llmqType, tipHeight, id);
            if (quorum == null) {
                // should not happen, but if one fails to select, all others will also fail to select
                return false;
            }
            Sha256Hash signHash = LLMQUtils.buildSignHash(llmqType, quorum.commitment.quorumHash, id, islock.txid);
            batchVerifier.pushMessage(nodeId, hash, signHash, islock.signature.getSignature(), quorum.commitment.quorumPublicKey);
    
            // We can reconstruct the RecoveredSignature objects from the islock and pass it to the signing manager, which
            // avoids unnecessary double-verification of the signature. We however only do this when verification here
            // turns out to be good (which is checked further down)
            if (!quorumSigningManager.hasRecoveredSigForId(llmqType, id)) {
                RecoveredSignature recSig = new RecoveredSignature();
                recSig.llmqType = llmqType.getValue();
                recSig.quorumHash = quorum.commitment.quorumHash;
                recSig.id = id;
                recSig.msgHash = islock.txid;
                recSig.signature = islock.signature;
                recSigs.put(hash, new Pair(quorum, recSig));
            }
        }

        batchVerifier.verify();

        if (!batchVerifier.getBadSources().isEmpty()) {
            for (Long nodeId : batchVerifier.getBadSources()) {
                // Let's not be too harsh, as the peer might simply be unlucky and might have sent us an old lock which
                // does not validate anymore due to changed quorums
                //Misbehaving(nodeId, 20);
            }
        }
        for (Map.Entry<Sha256Hash, Pair<Long, InstantSendLock>>  p : pend.entrySet()) {
            Sha256Hash hash = p.getKey();
            long nodeId = p.getValue().getFirst();
            InstantSendLock islock = p.getValue().getSecond();

            if (batchVerifier.getBadMessages().contains(hash)) {
                log.info("-- txid={}, islock={}: invalid sig in islock, peer={}",
                        islock.txid.toString(), hash.toString(), nodeId);
                invalidInstantSendLocks.add(islock);
                continue;
            }

            processInstantSendLock(nodeId, hash, islock);

            // See comment further on top. We pass a reconstructed recovered sig to the signing manager to avoid
            // double-verification of the sig.
            Pair<Quorum, RecoveredSignature> it = recSigs.get(hash);
            if (it != null) {
                Quorum quorum = it.getFirst();
                RecoveredSignature recSig = it.getSecond();
                if (!quorumSigningManager.hasRecoveredSigForId(llmqType, recSig.id)) {
                    recSig.updateHash();
                    log.info("passing reconstructed recSig to signing mgr -- txid={}, islock={}: peer={}",
                            islock.txid.toString(), hash.toString(), nodeId);
                    quorumSigningManager.pushReconstructedRecoveredSig(recSig, quorum);
                }
            }
        }

        return true;
    }

    void processInstantSendLock(long from, Sha256Hash hash, InstantSendLock islock)
    {
        StoredBlock minedBlock = null;
        Transaction tx = null;

        tx = mapTx.get(islock.txid);

        if(tx != null && tx.getConfidence() != null) {
            TransactionConfidence confidence = tx.getConfidence();
            if(confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING)
            {
                long height = confidence.getAppearedAtChainHeight();

                try {
                    StoredBlock block = blockChain.getBlockStore().get((int)height);
                    if(block != null) {
                        // Let's see if the TX that was locked by this islock is already mined in a ChainLocked block. If yes,
                        // we can simply ignore the islock, as the ChainLock implies locking of all TXs in that chain
                        if (context.chainLockHandler.hasChainLock(height, block.getHeader().getHash())) {
                            log.info("txlock={}, islock={}: dropping islock as it already got a ChainLock in block {}, peer={}",
                                    islock.txid.toString(), hash.toString(), block.getHeader().getHash().toString(), from);
                            return;
                        }
                    }
                } catch (BlockStoreException x) {
                    //swallow
                }
            }
        }

        lock.lock();
        try
        {
            log.info("processing islock txid={}, islock={}:  peer={}",
                    islock.txid.toString(), hash.toString(), from);

            InstantSendLock otherIsLock;
            if (db.getInstantSendLockByHash(hash) != null) {
                return;
            }
            otherIsLock = db.getInstantSendLockByTxid(islock.txid);
            if (otherIsLock != null) {
                log.info("duplicate islock:  txid={}, islock={}: other islock={}, peer={}",
                        islock.txid.toString(), hash.toString(),otherIsLock.getHash().toString(), from);
            }
            for (TransactionOutPoint in : islock.inputs) {
                otherIsLock = db.getInstantSendLockByInput(in);
                if (otherIsLock != null) {
                    log.info("CInstantSendManager::{} -- txid={}, islock={}: conflicting input in islock. input={}, other islock={}, peer={}",
                            islock.txid.toString(), hash.toString(), in.toStringShort(), otherIsLock.getHash().toString(), from);
                }
            }

            db.writeNewInstantSendLock(hash, islock);
            if (minedBlock != null) {
                db.writeInstantSendLockMined(hash, minedBlock.getHeight());
            }
        } finally {
            lock.unlock();
        }

        removeMempoolConflictsForLock(hash, islock);
        updateWalletTransaction(islock.txid, tx);
    }

    void updateWalletTransaction(Sha256Hash txid, Transaction tx) {
        if(tx != null) {
            tx.getConfidence().setIXType(TransactionConfidence.IXType.IX_LOCKED);
            tx.getConfidence().queueListeners(TransactionConfidence.Listener.ChangeReason.IX_TYPE);
        }
    }

    public void syncTransaction(Transaction tx, StoredBlock block, int posInBlock)
    {
        if (!isNewInstantSendEnabled()) {
            return;
        }

        if (tx.isCoinBase() || tx.getInputs().isEmpty()) {
            // coinbase can't and TXs with no inputs be locked
            return;
        }

        if(!mapTx.containsKey(tx.getHash()) && posInBlock == -1)
        {
            mapTx.put(tx.getHash(), tx);
        }

        boolean isDisconnect = block != null && posInBlock == -1;

        Sha256Hash islockHash;
        lock.lock();
        try {
            islockHash = db.getInstantSendLockHashByTxid(tx.getHash());

            // update DB about when an IS lock was mined
            if (islockHash != null && !islockHash.equals(Sha256Hash.ZERO_HASH) && block != null) {
                if (isDisconnect) {
                    db.removeInstantSendLockMined(islockHash, block.getHeight());
                } else {
                    db.writeInstantSendLockMined(islockHash, block.getHeight());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    ChainLockListener chainLockListener = new ChainLockListener() {
        public void onNewChainLock(StoredBlock block) {
            handleFullyConfirmedBlock(block.getHeight());
        }
    };

    NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            // TODO remove this after DIP8 has activated
            //boolean fDIP0008Active = VersionBitsState(pindexNew.pprev, Params().GetConsensus(), Consensus::DEPLOYMENT_DIP0008, versionbitscache) == THRESHOLD_ACTIVE;

            if (context.sporkManager.isSporkActive(SporkManager.SPORK_19_CHAINLOCKS_ENABLED) /*&& fDIP0008Active*/) {
                // Nothing to do here. We should keep all islocks and let chainlocks handle them.
                return;
            }

            int confirmedHeight = block.getHeight() - context.getParams().getInstantSendKeepLock();

            handleFullyConfirmedBlock(confirmedHeight);

        }
    };


    void handleFullyConfirmedBlock(int height)
    {
        HashMap<Sha256Hash, InstantSendLock> removeISLocks;

        lock.lock();
        try {

            removeISLocks = db.removeConfirmedInstantSendLocks(height);
            for (Map.Entry<Sha256Hash, InstantSendLock> p : removeISLocks.entrySet()) {
                Sha256Hash islockHash = p.getKey();
                InstantSendLock islock = p.getValue();
                log.info("removed islock as it got fully confirmed -- txid={}, islock={}",
                        islock.txid.toString(), islockHash.toString());
            }

        } finally {
            lock.unlock();
        }

        for (Map.Entry<Sha256Hash, InstantSendLock> p : removeISLocks.entrySet()) {
            updateWalletTransaction(p.getValue().txid, mapTx.get(p.getValue().txid));
            mapTx.remove(p.getValue().txid);
        }

        //remove invalid signature related tx's
        for(InstantSendLock islock : invalidInstantSendLocks) {
            mapTx.remove(islock.txid);
        }
        invalidInstantSendLocks.clear();
    }
    static final String INPUTLOCK_REQUESTID_PREFIX = "inlock";

    Sha256Hash getRequestId(TransactionOutPoint outpoint) {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(40);
            Utils.stringToByteStream(INPUTLOCK_REQUESTID_PREFIX, bos);
            outpoint.bitcoinSerialize(bos);
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    void removeMempoolConflictsForLock(Sha256Hash hash, InstantSendLock islock)
    {
        //TODO:  should full verification mode have this?
    }

    public InstantSendLock getInstantSendLockByHash(Sha256Hash hash)
    {
        if (!isNewInstantSendEnabled()) {
            return null;
        }

        lock.lock();
        try {
            InstantSendLock islock = db.getInstantSendLockByHash(hash);
            return islock;
        } finally {
            lock.unlock();
        }
    }

    boolean isLocked(Sha256Hash txHash)
    {
        if (!isNewInstantSendEnabled()) {
            return false;
        }

        lock.lock();
        try {
            return db.getInstantSendLockByTxid(txHash) != null;
        } finally {
            lock.unlock();
        }
    }

    public boolean isConflicted(Transaction tx)
    {
        lock.lock();
        try {
            Sha256Hash dummy;
            return getConflictingTx(tx) != null;
        } finally {
            lock.unlock();
        }
    }

    public Sha256Hash getConflictingTx(Transaction tx)
    {
        if (!isNewInstantSendEnabled()) {
            return null;
        }

        lock.lock();
        try {
            for (TransactionInput in :tx.getInputs()){
                InstantSendLock otherIsLock = db.getInstantSendLockByInput(in.getOutpoint());
                if (otherIsLock == null) {
                    continue;
                }

                if (otherIsLock.txid.equals(tx.getHash())) {
                    return otherIsLock.txid;
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onNewRecoveredSignature(RecoveredSignature recoveredSig) {
        if (!isNewInstantSendEnabled()) {
            return;
        }

        LLMQParameters.LLMQType llmqType = context.getParams().getLlmqForInstantSend();
        if (llmqType == LLMQParameters.LLMQType.LLMQ_NONE) {
            return;
        }
    }

    TransactionReceivedInBlockListener transactionReceivedInBlockListener = new TransactionReceivedInBlockListener() {
        @Override
        public void receiveFromBlock(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {

            // Call syncTransaction to update lock candidates and votes
            if(blockType == AbstractBlockChain.NewBlockType.BEST_CHAIN) {
                syncTransaction(tx, block, relativityOffset);
            }
        }

        @Override
        public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
            return false;
        }
    };

    //TODO: finish connecting this.  Should we keep a list of transactions sent and received in spv mode?
    OnTransactionBroadcastListener transactionBroadcastListener = new OnTransactionBroadcastListener() {
        @Override
        public void onTransaction(Peer peer, Transaction t) {
            syncTransaction(t, null, -1);
        }
    };
}
