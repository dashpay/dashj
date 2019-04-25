package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;
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
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class InstantSendManager implements RecoveredSignatureListener {

    Context context;
    SigningManager quorumSigningManager;

    private static final Logger log = LoggerFactory.getLogger(InstantSendManager.class);
    ReentrantLock lock = Threading.lock("InstantSendManager");
    InstantSendDatabase db;
    Thread workThread;
    boolean runWithoutThread;
    AbstractBlockChain blockChain;


    /**
     * Request ids of inputs that we signed. Used to determine if a recovered signature belongs to an
     * in-progress input lock.
     */
    HashSet<Sha256Hash> inputRequestIds;

    /**
     * These are the islocks that are currently in the middle of being created. Entries are created when we observed
     * recovered signatures for all inputs of a TX. At the same time, we initiate signing of our sigshare for the islock.
     * When the recovered sig for the islock later arrives, we can finish the islock and propagate it.
     */
    HashMap<Sha256Hash, InstantSendLock> creatingInstantSendLocks;
    // maps from txid to the in-progress islock
    HashMap<Sha256Hash, InstantSendLock> txToCreatingInstantSendLocks;

    // Incoming and not verified yet
    HashMap<Sha256Hash, Pair<Long, InstantSendLock>> pendingInstantSendLocks;

    // a set of recently IS locked TXs for which we can retry locking of children
    HashSet<Sha256Hash> pendingRetryTxs;
    boolean pendingRetryAllTxs;

    public InstantSendManager(Context context, InstantSendDatabase db) {
        this.context = context;
        this.db = db;
        this.quorumSigningManager = context.signingManager;
        creatingInstantSendLocks = new HashMap<Sha256Hash, InstantSendLock>();
        pendingInstantSendLocks = new HashMap<Sha256Hash, Pair<Long, InstantSendLock>>();
        pendingRetryTxs = new HashSet<Sha256Hash>();
        txToCreatingInstantSendLocks = new HashMap<Sha256Hash, InstantSendLock>();
    }

    public InstantSendManager(Context context, InstantSendDatabase db, boolean runWithoutThread) {
        this(context, db);
        this.runWithoutThread = runWithoutThread;
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        this.blockChain.addTransactionReceivedListener(this.transactionReceivedInBlockListener);
        this.blockChain.addNewBestBlockListener(this.newBestBlockListener);
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

            log.info("instantsend-CInstantSendManager::processInstantSendLock -- txid={}, islock={}: received islock, peer={}",
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
            return db.getInstantSendLockByHash(inv.hash) != null || pendingInstantSendLocks.containsKey(inv.hash);
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
                    didWork |= processPendingRetryLockTxs();

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

    boolean processTx(Transaction tx)
    {
        if (!isNewInstantSendEnabled()) {
            return true;
        }

        LLMQParameters.LLMQType llmqType = context.getParams().getLlmqForInstantSend();
        if (llmqType == LLMQParameters.LLMQType.LLMQ_NONE) {
            return true;
        }

        //we are not a masternode, so return false
        return false;
    }

    boolean checkCanLock(Transaction tx, boolean printDebug)
    {
        if (context.sporkManager.isSporkActive(SporkManager.SPORK_16_INSTANTSEND_AUTOLOCKS) ) {
            return false;
        }

        if (tx.getInputs().isEmpty()) {
            // can't lock TXs without inputs (e.g. quorum commitments)
            return false;
        }

        //Coin nValueIn = Coin.valueOf(0);
        for (TransactionInput  in : tx.getInputs()) {
            //Coin v = Coin.valueOf(0);
            if (!checkCanLock(in.getOutpoint(), printDebug, tx.getHash())) {
                return false;
            }

            //nValueIn += v;
        }

        // TODO decide if we should limit max input values. This was ok to do in the old system, but in the new system
        // where we want to have all TXs locked at some point, this is counterproductive (especially when ChainLocks later
        // depend on all TXs being locked first)
//    CAmount maxValueIn = sporkManager.GetSporkValue(SPORK_5_INSTANTSEND_MAX_VALUE);
//    if (nValueIn > maxValueIn * COIN) {
//        if (printDebug) {
//            log.info("instantsend", "CInstantSendManager::{} -- txid={}: TX input value too high. nValueIn=%f, maxValueIn={}", __func__,
//                     tx.getHash().toString(), nValueIn / (double)COIN, maxValueIn);
//        }
//        return false;
//    }

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
                log.info("instantsend -- txid={}: parent mempool TX {} is not locked",
                        txHash.toString(), outpoint.getHash().toString());
            }
            return false;
        }

        Transaction tx;
        Sha256Hash hashBlock;
        BlockStore blockStore = blockChain.getBlockStore();
        UTXO utxo;
        if(blockStore instanceof FullPrunedBlockStore) {
            // this relies on enabled txindex and won't work if we ever try to remove the requirement for txindex for masternodes
            try {
                utxo = ((FullPrunedBlockStore) blockStore).getTransactionOutput(outpoint.getHash(), outpoint.getIndex());
                if (printDebug) {
                    log.info("instantsend--CInstantSendManager::{} -- txid={}: failed to find parent TX {}",
                            txHash.toString(), outpoint.getHash().toString());
                }
                return false;

            } catch (BlockStoreException x) {
                log.error("BlockStoreException:  "+ x.getMessage());
                return false;
            }

            //try {
                //StoredUndoableBlock block = ((FullPrunedBlockStore) blockStore).getUndoBlock(hashBlock);

//                int txAge = blockChain.getBestChainHeight() - utxo.getHeight();
//                if (txAge < nInstantSendConfirmationsRequired) {
//                    if (context.chainLocksHandler.hasChainLock(utxo.notify(), block.getHash())*/) {
//                        if (printDebug) {
//                            log.info("instantsend", "CInstantSendManager::{} -- txid={}: outpoint {} too new and not ChainLocked. nTxAge={}, nInstantSendConfirmationsRequired={}",
//                                    txHash.toString(), outpoint.toStringShort(), txAge, nInstantSendConfirmationsRequired);
//                        }
//                        return false;
//                    }
//                }
            //} catch (BlockStoreException x) {
                //swallow
            //}
        }

        return true;
    }



    void handleNewInputLockRecoveredSig(RecoveredSignature recoveredSig, Sha256Hash txid)
    {
/*        LLMQParameters.LLMQType llmqType = context.getParams().getLlmqForInstantSend();

        if(blockChain.getBlockStore() instanceof FullPrunedBlockStore) {
            FullPrunedBlockStore blockStore = (FullPrunedBlockStore)blockChain.getBlockStore();
            Transaction tx;
            Sha256Hash hashBlock;
            UTXO utxo = blockStore.getTransactionOutput()
            if (!GetTransaction(txid, tx, hashBlock, true)) {
                return;
            }

            //if (LogAcceptCategory("instantsend")) {
                for (TransactionInput in :tx.getInputs()){
                    Sha256Hash id = ::SerializeHash(std::make_pair (INPUTLOCK_REQUESTID_PREFIX, in.prevout));
                    if (id == recoveredSig.id) {
                        log.info("instantsend--CInstantSendManager::{} -- txid={}: got recovered sig for input {}",
                                txid.toString(), in.getOutpoint().toStringShort());
                        break;
                    }
                }
            //}
        }

        trySignInstantSendLock(tx);
        */
    }

    void trySignInstantSendLock(Transaction tx)
    {
        /*LLMQParameters.LLMQType llmqType = context.getParams().getLlmqForInstantSend();

        for (TransactionInput in : tx.getInputs()) {
            auto id = ::SerializeHash(std::make_pair(INPUTLOCK_REQUESTID_PREFIX, in.prevout));
            if (!quorumSigningManager.hasRecoveredSignature(llmqType, id, tx.getHash())) {
                return;
            }
        }

        log.info("instantsend", "CInstantSendManager::{} -- txid={}: got all recovered sigs, creating CInstantSendLock\n", __func__,
                tx.getHash().toString());

        InstantSendLock islock = new InstantSendLock();
        islock.txid = tx.getHash();
        for (TransactionInput in : tx.getInputs()) {
            islock.inputs.add(in.getOutpoint());
        }

        Sha256Hash id = islock.getRequestId();

        if (quorumSigningManager.hasRecoveredSigForId(llmqType, id)) {
            return;
        }

        try {
            InstantSendLock e = creatingInstantSendLocks.put(id, islock);
            if (e == null) {
                return;
            }
            txToCreatingInstantSendLocks.put(tx.getHash(), e);
        } finally {
            lock.unlock();
        }

        quorumSigningManager.asyncSignIfMember(llmqType, id, tx.getHash());
        */
    }

    void handleNewInstantSendLockRecoveredSig(RecoveredSignature recoveredSig)
    {
        InstantSendLock islock;

        lock.lock();
        try {
            islock = creatingInstantSendLocks.get(recoveredSig.id);
            if (islock == null) {
                return;
            }

            creatingInstantSendLocks.remove(islock);
            txToCreatingInstantSendLocks.remove(islock.txid);
        } finally {
            lock.unlock();
        }

        if (islock.txid != recoveredSig.msgHash) {
            log.info("CInstantSendManager::{} -- txid={}: islock conflicts with {}, dropping own version",
                    islock.txid.toString(), recoveredSig.msgHash.toString());
            return;
        }

        islock.signature = recoveredSig.signature;
        processInstantSendLock(-1, islock.getHash(), islock);
    }
    


    boolean processPendingInstantSendLocks()
    {
        LLMQParameters.LLMQType llmqType = context.getParams().getLlmqForInstantSend();

        HashMap<Sha256Hash, Pair<Long, InstantSendLock>> pend; 

        lock.lock();
        
        try {
            pend = new HashMap<Sha256Hash, Pair<Long, InstantSendLock>>(pendingInstantSendLocks);
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
                    log.info("instantsend", "CInstantSendManager::{} -- txid={}, islock={}: passing reconstructed recSig to signing mgr, peer={}",
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
        /*if(blockChain.getBlockStore() instanceof FullPrunedBlockStore) {
            FullPrunedBlockStore blockStore = (FullPrunedBlockStore)blockChain.getBlockStore();
            Transaction tx;
            Sha256Hash hashBlock;
            // we ignore failure here as we must be able to propagate the lock even if we don't have the TX locally

            minedBlock = blockStore.get()
            if (GetTransaction(islock.txid, tx, Params().GetConsensus(), hashBlock)) {
                if (!hashBlock.IsNull()) {
                    {
                        LOCK(cs_main);
                        pindexMined = mapBlockIndex.at(hashBlock);
                    }

                    // Let's see if the TX that was locked by this islock is already mined in a ChainLocked block. If yes,
                    // we can simply ignore the islock, as the ChainLock implies locking of all TXs in that chain
                    if (context.chainLocksHandler.hasChainLock(pindexMined.nHeight, pindexMined.GetBlockHash())){
                        log.info("instantsend", "CInstantSendManager::{} -- txlock={}, islock={}: dropping islock as it already got a ChainLock in block {}, peer={}\n", __func__,
                                islock.txid.toString(), hash.toString(), hashBlock.toString(), from);
                        return;
                    }
                }
            }
        }*/

        lock.lock();
        try
        {
            log.info("instantsend-- txid={}, islock={}: processsing islock, peer={}",
                    islock.txid.toString(), hash.toString(), from);

            creatingInstantSendLocks.remove(islock.getRequestId());
            txToCreatingInstantSendLocks.remove(islock.txid);

            InstantSendLock otherIsLock;
            if (db.getInstantSendLockByHash(hash) != null) {
                return;
            }
            otherIsLock = db.getInstantSendLockByTxid(islock.txid);
            if (otherIsLock != null) {
                log.info("CInstantSendManager::{} -- txid={}, islock={}: duplicate islock, other islock={}, peer={}",
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

            pendingRetryTxs.add(islock.txid);
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

        Sha256Hash islockHash;
        lock.lock();
        try {
            islockHash = db.getInstantSendLockHashByTxid(tx.getHash());

            // update DB about when an IS lock was mined
            if (islockHash != null && !islockHash.equals(Sha256Hash.ZERO_HASH) && block != null) {
                if (posInBlock == -1) {
                    db.removeInstantSendLockMined(islockHash, block.getHeight());
                } else {
                    db.writeInstantSendLockMined(islockHash, block.getHeight());
                }
            }
        } finally {
            lock.unlock();
        }

        //boolean chainlocked = block && chainLocksHandler.HasChainLock(block.getHeight(), block.getHeader().getHash());
        if (!islockHash.equals(Sha256Hash.ZERO_HASH) /*|| chainlocked*/) {
            lock.lock();
            try {
                pendingRetryTxs.add(tx.getHash());
            } finally {
                lock.unlock();
            }
        } else {
            processTx(tx);
        }
    }

    void notifyChainLock(StoredBlock pindexChainLock)
    {
        handleFullyConfirmedBlock(pindexChainLock);
    }

    NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            // TODO remove this after DIP8 has activated
            //boolean fDIP0008Active = VersionBitsState(pindexNew.pprev, Params().GetConsensus(), Consensus::DEPLOYMENT_DIP0008, versionbitscache) == THRESHOLD_ACTIVE;

            if (context.sporkManager.isSporkActive(SporkManager.SPORK_19_CHAINLOCKS_ENABLED) /*&& fDIP0008Active*/) {
                // Nothing to do here. We should keep all islocks and let chainlocks handle them.
                return;
            }

            int nConfirmedHeight = block.getHeight() - context.getParams().getInstantSendKeepLock();
            //const CBlockIndex* pindex = pindexNew.GetAncestor(nConfirmedHeight);

            //if (pindex) {
            //    handleFullyConfirmedBlock(pindex);
            //}
        }
    };


    void handleFullyConfirmedBlock(StoredBlock block)
    {
        HashMap<Sha256Hash, InstantSendLock> removeISLocks;

        lock.lock();
        try {

            removeISLocks = db.removeConfirmedInstantSendLocks(block.getHeight());
            for (Map.Entry<Sha256Hash, InstantSendLock> p : removeISLocks.entrySet()) {
                Sha256Hash islockHash = p.getKey();
                InstantSendLock islock = p.getValue();
                log.info("instantsend--CInstantSendManager::{} -- txid={}, islock={}: removed islock as it got fully confirmed",
                        islock.txid.toString(), islockHash.toString());

                for (TransactionOutPoint in : islock.inputs) {
                    Sha256Hash inputRequestId = getRequestId(in);
                    inputRequestIds.remove(inputRequestId);
                }
            }

            // Retry all not yet locked mempool TXs and TX which where mined after the fully confirmed block
            pendingRetryAllTxs = true;
        } finally {
            lock.unlock();
        }

        for (Map.Entry<Sha256Hash, InstantSendLock> p : removeISLocks.entrySet()) {
            updateWalletTransaction(p.getValue().txid, null);
        }
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
        /*LOCK(mempool.cs);

        std::unordered_map<Sha256Hash, CTransactionRef> toDelete;

        for (auto& in : islock.inputs) {
        auto it = mempool.mapNextTx.find(in);
        if (it == mempool.mapNextTx.end()) {
            continue;
        }
        if (it.second.getHash() != islock.txid) {
            toDelete.add(it.second.getHash(), mempool.get(it.second.getHash()));

            log.info("CInstantSendManager::{} -- txid={}, islock={}: mempool TX {} with input {} conflicts with islock\n", __func__,
                    islock.txid.toString(), hash.toString(), it.second.getHash().toString(), in.toStringShort());
        }
    }

        for (auto& p : toDelete) {
        mempool.removeRecursive(*p.second, MemPoolRemovalReason::CONFLICT);
    }*/
    }

    boolean processPendingRetryLockTxs()
    {
        boolean retryAllTxs;
        HashSet<Sha256Hash> parentTxs;
        lock.lock();
        try {
            retryAllTxs = pendingRetryAllTxs;
            parentTxs = new HashSet<Sha256Hash>(pendingRetryTxs);
            pendingRetryAllTxs = false;
        } finally {
            lock.unlock();
        }

        if (!retryAllTxs && parentTxs.isEmpty()) {
            return false;
        }

        if (!isNewInstantSendEnabled()) {
            return false;
        }

        // Let's retry all unlocked TXs from mempool and and recently connected blocks

/*        TxConfidenceTable mempool = context.getConfidenceTable();
        HashMap<Sha256Hash, Transaction> txs = new HashMap<Sha256Hash, Transaction>();

        {
            if (retryAllTxs) {

                for (auto it = mempool.mapTx.begin(); it != mempool.mapTx.end(); ++it) {
                    txs.add(it.GetTx().getHash(), it.GetSharedTx());
                }
            } else {
                for (const auto& parentTx : parentTxs) {
                    auto it = mempool.mapNextTx.lower_bound(COutPoint(parentTx, 0));
                    while (it != mempool.mapNextTx.end() && it.first.hash == parentTx) {
                        txs.add(it.second.getHash(), mempool.get(it.second.getHash()));
                        ++it;
                    }
                }
            }
        }
*/

/*        if(blockChain.getBlockStore() instanceof  FullPrunedBlockStore) {
            FullPrunedBlockStore blockStore = (FullPrunedBlockStore)blockChain.getBlockStore();
            StoredBlock pindexWalk = blockChain.getChainHead();


            // scan blocks until we hit the last chainlocked block we know of. Also stop scanning after a depth of 6 to avoid
            // signing thousands of TXs at once. Also, after a depth of 6, blocks get eligible for ChainLocking even if unsafe
            // TXs are included, so there is no need to retroactively sign these.
            int depth = 0;
            while (pindexWalk != null && depth < 6) {
                if (chainLocksHandler.hasChainLock(pindexWalk.getHeight(), pindexWalk.getHeader().getHash())) {
                    break;
                }

                StoredUndoableBlock block = null;
                try
                {
                    block = blockStore.getUndoBlock(pindexWalk.getHeader().getHash())
                    if (block == null) {
                        pindexWalk = pindexWalk.getPrev(blockStore);
                        continue;
                    }
                } catch (BlockStoreException x) {
                    //can't find the block
                    continue;
                }

                for (Transaction tx : block.getTransactions()) {
                    if (retryAllTxs) {
                        txs.put(tx.getHash(), tx);
                    } else {
                        boolean isChild = false;
                        for (TransactionInput in : tx.getInputs()) {
                            if (parentTxs.contains(in.getOutpoint().getHash())) {
                                isChild = true;
                                break;
                            }
                        }
                        if (isChild) {
                            txs.put(tx.getHash(), tx);
                        }
                    }
                }

                pindexWalk = pindexWalk.getPrev(blockStore);
                depth++;
            }
        }
*/
 /*       boolean didWork = false;
        for (Map.Entry<Sha256Hash, Transaction> p : txs.entrySet()) {
        Transaction tx = p.getValue();
        lock.lock();
        try {
            if (txToCreatingInstantSendLocks.containsKey(tx.getHash())) {
                // we're already in the middle of locking this one
                continue;
            }
            if (isLocked(tx.getHash())) {
                continue;
            }
            if (isConflicted(tx)) {
                // should not really happen as we have already filtered these out
                continue;
            }
        } finally {
            lock.unlock();
        }

        // CheckCanLock is already called by ProcessTx, so we should avoid calling it twice. But we also shouldn't spam
        // the logs when retrying TXs that are not ready yet.
        //if (LogAcceptCategory("instantsend")) {
            if (!checkCanLock(tx, false)) {
                continue;
            }
            log.info("instantsend--CInstantSendManager::{} -- txid={}: retrying to lock",
                    tx.getHash().toString());
        //}

        processTx(tx);
        didWork = true;
    }

        return didWork;
  */
        return true;
    }

    InstantSendLock getInstantSendLockByHash(Sha256Hash hash)
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

    boolean isConflicted(Transaction tx)
    {
        lock.lock();
        try {
            Sha256Hash dummy;
            return getConflictingTx(tx) != null;
        } finally {
            lock.unlock();
        }
    }

    Sha256Hash getConflictingTx(Transaction tx)
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
        LLMQParameters llmqParameters = context.getParams().getLlmqs().get(llmqType);
        
        Sha256Hash txid = null;
        boolean isInstantSendLock = false;
        {
            lock.lock();
            try {
                if (inputRequestIds.contains(recoveredSig.id)) {
                    txid = recoveredSig.msgHash;
                }
                if (creatingInstantSendLocks.containsKey(recoveredSig.id)) {
                    isInstantSendLock = true;
                }
            } finally {
                lock.unlock();
            }
        }
        if (txid != null && !txid.equals(Sha256Hash.ZERO_HASH)) {
            handleNewInputLockRecoveredSig(recoveredSig, txid);
        } else if (isInstantSendLock) {
            handleNewInstantSendLockRecoveredSig(recoveredSig);
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
