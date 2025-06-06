package org.bitcoinj.quorums;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.OnTransactionBroadcastListener;
import org.bitcoinj.core.listeners.PreMessageReceivedEventListener;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.crypto.BLSBatchVerifier;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.quorums.listeners.RecoveredSignatureListener;
import org.bitcoinj.quorums.listeners.ChainLockListener;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsSentEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.utils.Threading.SAME_THREAD;

public class InstantSendManager implements RecoveredSignatureListener {

    Context context;
    SigningManager quorumSigningManager;

    private static final Logger log = LoggerFactory.getLogger(InstantSendManager.class);
    ReentrantLock lock = Threading.lock("InstantSendManager");
    protected InstantSendDatabase db;
    protected ChainLocksHandler chainLocksHandler;
    protected PeerGroup peerGroup;
    protected MasternodeSync masternodeSync;
    protected SporkManager sporkManager;
    protected SimplifiedMasternodeListManager masternodeListManager;
    @Deprecated
    Thread workThread;
    @Deprecated
    private boolean runWithoutThread = true;
    private ScheduledExecutorService scheduledExecutorService;
    AbstractBlockChain blockChain;
    private ArrayList<Wallet> wallets = new ArrayList<>();

    //Keep track of when the ISLOCK arrived
    HashMap<InstantSendLock, Long> invalidInstantSendLocks;

    // Incoming and not verified yet
    HashMap<Sha256Hash, Pair<Long, InstantSendLock>> pendingInstantSendLocks;

    public InstantSendManager(Context context, InstantSendDatabase db, SigningManager signingManager) {
        this.context = context;
        this.db = db;
        this.quorumSigningManager = signingManager;
        pendingInstantSendLocks = new HashMap<>();
        invalidInstantSendLocks = new HashMap<>();
        scheduledExecutorService = createScheduledExecutorService();
    }

    public InstantSendManager(Context context, InstantSendDatabase db, AbstractBlockChain blockChain,
                              @Nullable PeerGroup peerGroup, @Nullable ChainLocksHandler chainLocksHandler, SigningManager signingManager,
                              MasternodeSync masternodeSync, SporkManager sporkManager) {
        this.context = context;
        this.db = db;
        this.quorumSigningManager = signingManager;
        pendingInstantSendLocks = new HashMap<>();
        invalidInstantSendLocks = new HashMap<>();
        scheduledExecutorService = createScheduledExecutorService();
        setBlockChain(blockChain, peerGroup, chainLocksHandler, masternodeSync, sporkManager, masternodeListManager);
    }

    private ScheduledExecutorService createScheduledExecutorService() {
        return Executors.newScheduledThreadPool(2, new ContextPropagatingThreadFactory("instantsend"));
    }

    @Override
    public String toString() {
        return String.format("InstantSendManager:  pendingInstantSendLocks %d, DB: %s", pendingInstantSendLocks.size(), db);
    }

//    public InstantSendManager(Context context, InstantSendDatabase db, boolean runWithoutThread) {
//        this(context, db);
//        this.runWithoutThread = runWithoutThread;
//    }

//    public void setBlockChain(AbstractBlockChain blockChain, @Nullable PeerGroup peerGroup) {
//        this.peerGroup = peerGroup;
//        this.blockChain = blockChain;
//        this.blockChain.addTransactionReceivedListener(this.transactionReceivedInBlockListener);
//        this.blockChain.addNewBestBlockListener(this.newBestBlockListener);
//        if (peerGroup != null) {
//            peerGroup.addOnTransactionBroadcastListener(this.transactionBroadcastListener);
//        }
//        if (scheduledExecutorService == null)
//            scheduledExecutorService = createScheduledExecutorService();
//    }

    public void setBlockChain(AbstractBlockChain blockChain, @Nullable PeerGroup peerGroup,
                              @Nullable ChainLocksHandler chainLocksHandler, MasternodeSync masternodeSync,
                              SporkManager sporkManager, SimplifiedMasternodeListManager masternodeListManager) {
        this.peerGroup = peerGroup;
        this.blockChain = blockChain;
        this.masternodeSync = masternodeSync;
        this.masternodeListManager = masternodeListManager;
        this.sporkManager = sporkManager;
        this.blockChain.addTransactionReceivedListener(this.transactionReceivedInBlockListener);
        this.blockChain.addNewBestBlockListener(this.newBestBlockListener);
        if (peerGroup != null) {
            peerGroup.addOnTransactionBroadcastListener(this.transactionBroadcastListener);
            peerGroup.addPreMessageReceivedEventListener(SAME_THREAD, preMessageReceivedEventListener);
        }
        if (chainLocksHandler != null) {
            this.chainLocksHandler = chainLocksHandler;
            chainLocksHandler.addChainLockListener(this.chainLockListener);
        }
        if (scheduledExecutorService == null)
            scheduledExecutorService = createScheduledExecutorService();
    }

    public void close(PeerGroup peerGroup) {
        if (blockChain != null) {
            blockChain.removeTransactionReceivedListener(this.transactionReceivedInBlockListener);
            blockChain.removeNewBestBlockListener(this.newBestBlockListener);
            blockChain = null;
        }
        if (peerGroup != null) {
            peerGroup.removeOnTransactionBroadcastListener(this.transactionBroadcastListener);
            peerGroup.removePreMessageReceivedEventListener(preMessageReceivedEventListener);
        }
        chainLocksHandler.removeChainLockListener(this.chainLockListener);
        wallets.forEach(wallet -> wallet.removeCoinsSentEventListener(coinsSentEventListener));
        try {
            if (!scheduledExecutorService.awaitTermination(3000, TimeUnit.MILLISECONDS)) {
                log.warn("scheduled threads still remain");
            }
            scheduledExecutorService = null;
        } catch (InterruptedException e) {
            log.warn("thread termination interrupted");
            // swallow
        }
    }

    private boolean isInitialized() {
        return blockChain != null;
    }

    public boolean isInstantSendEnabled() {
        return sporkManager.isSporkActive(SporkId.SPORK_2_INSTANTSEND_ENABLED);
    }

    public void processInstantSendLock(Peer peer, InstantSendLock isLock) {
        if(!isInstantSendEnabled())
            return;

        if (!preVerifyInstantSendLock(isLock)) {
            // TODO: Dash Core increases ban score by 100
            return;
        }

        if (isLock.isDeterministic()) {
            try {
                StoredBlock blockIndex = blockChain.getBlockStore().get(isLock.cycleHash);
                if (blockIndex == null) {
                    // Maybe we don't have the block yet or maybe some peer spams invalid values for cycleHash
                    // TODO: DashCore increases ban score by 1
                    return;
                }

                final LLMQParameters.LLMQType llmqType = context.getParams().isDIP0024Active(blockIndex) ?
                        context.getParams().getLlmqDIP0024InstantSend() :
                        context.getParams().getLlmqForInstantSend();
                final int dkgInterval = LLMQParameters.fromType(llmqType).dkgInterval;
                if (blockIndex.getHeight() % dkgInterval != 0) {
                    // TODO: Dash Core increases ban score by 100
                    return;
                }
            } catch (BlockStoreException x) {
                throw new RuntimeException(x);
            }
        } else if (context.getParams().isDIP0024Active(blockChain.getChainHead())) {
            // Ignore non-deterministic islocks once rotation is active
            return;
        }

        context.getConfidenceTable().seen(isLock.txid, peer.getAddress());
        TransactionConfidence confidence = context.getConfidenceTable().getOrCreate(isLock.txid);
        if (confidence != null) {
            if (confidence.getIXType() != TransactionConfidence.IXType.IX_NONE) {
                return;
            }
            confidence.setIXType(TransactionConfidence.IXType.IX_REQUEST);
            confidence.setInstantSendLock(isLock);
            confidence.queueListeners(TransactionConfidence.Listener.ChangeReason.IX_TYPE);
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
                    isLock.txid, hash, peer.hashCode());

            pendingInstantSendLocks.put(hash, new Pair<>((long)peer.hashCode(), isLock));

            if(runWithoutThread) {
                try {
                    processPendingInstantSendLocks();
                } catch (BlockStoreException x) {
                    throw new VerificationException(x.getMessage());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean preVerifyInstantSendLock(InstantSendLock islock)
    {

        if (islock.txid.isZero() || islock.inputs.isEmpty()) {
            return false;
        }

        HashSet<TransactionOutPoint> dups = new HashSet<>();
        for (TransactionOutPoint o : islock.inputs) {
            if (!dups.add(o)) {
                return false;
            }
        }

        return true;
    }

    public final PreMessageReceivedEventListener preMessageReceivedEventListener = (peer, m) -> {
        if (m instanceof InventoryMessage) {
            if (isInstantSendEnabled() && masternodeSync != null && masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_INSTANTSENDLOCKS)) {
                InventoryMessage inv = (InventoryMessage) m;
                List<InventoryItem> items = inv.getItems();
                List<InventoryItem> instantSendLocks = new LinkedList<>();
                for (InventoryItem item : items) {
                    switch (item.type) {
                        case InstantSendLock:
                        case InstantSendDeterministicLock:
                            instantSendLocks.add(item);
                            break;
                        default:
                            break;
                    }
                }
                Iterator<InventoryItem> it = instantSendLocks.iterator();
                GetDataMessage getdata = new GetDataMessage(context.getParams());
                while (it.hasNext()) {
                    InventoryItem item = it.next();
                    if(!alreadyHave(item)) {
                        getdata.addItem(item);
                    }
                }
                if (!getdata.getItems().isEmpty()) {
                    // This will cause us to receive a bunch of block or tx messages.
                    peer.sendMessage(getdata);
                }
            }
        } else if (m instanceof InstantSendLock) {
            processInstantSendLock(peer, (InstantSendLock) m);
            return null;
        }
        return m;
    };

    public boolean alreadyHave(InventoryItem inv)
    {
        if (!isInstantSendEnabled()) {
            return true;
        }

        lock.lock();
        try {
            boolean haslock = db.getInstantSendLockByHash(inv.hash) != null || pendingInstantSendLocks.containsKey(inv.hash);
            TransactionConfidence confidence = context.getConfidenceTable().get(inv.hash);
            if (confidence != null && confidence.getIXType() != TransactionConfidence.IXType.IX_NONE) {
                return true;
            }
            if (!invalidInstantSendLocks.isEmpty()) {
                for (InstantSendLock islock : invalidInstantSendLocks.keySet()) {
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
                    boolean didWork = processPendingInstantSendLocks();

                    if (!didWork) {
                        Thread.sleep(100);
                    }
                }
            } catch (InterruptedException x) {
                //let the thread stop
            } catch (BlockStoreException x) {
                throw new RuntimeException(x);
            }
        }
    };

    @Deprecated
    public void start() {
        if(!runWithoutThread) {
            if (workThread != null)
                throw new IllegalThreadStateException("Thread is already running");

            workThread = new Thread(workerMainThread);
            workThread.start();
        }
        quorumSigningManager.addRecoveredSignatureListener(this);
    }

    @Deprecated
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
        if (tx.getInputs().isEmpty()) {
            // can't lock TXs without inputs (e.g. quorum commitments)
            return false;
        }

        BlockStore blockStore = blockChain.getBlockStore();
        Coin value = Coin.valueOf(0);
        for (TransactionInput  in : tx.getInputs()) {
            if (!checkCanLock(in.getOutpoint(), printDebug, tx.getTxId())) {
                return false;
            }

            if(blockStore instanceof FullPrunedBlockStore) {
                // this relies on enabled txindex and won't work if we ever try to remove the requirement for txindex for masternodes
                try {
                    UTXO utxo = ((FullPrunedBlockStore) blockStore).getTransactionOutput(in.getOutpoint().getHash(), in.getOutpoint().getIndex());
                    value = value.add(utxo.getValue());

                } catch (BlockStoreException x) {
                    log.error("BlockStoreException: ", x);
                }

            } else {
                if(in.getValue() != null)
                    value = value.add(in.getValue());
            }
        }

        return true;
    }

    boolean checkCanLock(TransactionOutPoint outpoint, boolean printDebug, Sha256Hash txHash) {
        int nInstantSendConfirmationsRequired = context.getParams().getInstantSendConfirmationsRequired();

        if (isLocked(outpoint.getHash())) {
            // if prevout was ix locked, allow locking of descendants (no matter if prevout is in mempool or already mined)
            return true;
        }

        TxConfidenceTable mempool = context.getConfidenceTable();
        TransactionConfidence mempoolTx = mempool.get(outpoint.getHash());
        if (mempoolTx != null) {
            if (printDebug) {
                log.info("txid={}: parent mempool TX {} is not locked", txHash, outpoint.getHash());
            }
            return false;
        }

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
                            txHash, outpoint.getHash());
                    return false;
                }

            } catch (BlockStoreException x) {
                log.error("BlockStoreException:  {}", x.getMessage());
                return false;
            }

            try {
                if(hashBlock == null)
                    return false;

                StoredUndoableBlock block = ((FullPrunedBlockStore) blockStore).getUndoBlock(hashBlock);

                int txAge = blockChain.getBestChainHeight() - utxo.getHeight();
                if (txAge < nInstantSendConfirmationsRequired) {
                    if (chainLocksHandler.hasChainLock(utxo.getHeight(), block.getHash())) {
                        if (printDebug) {
                            log.info("txid={}: outpoint {} too new and not ChainLocked. nTxAge={}, nInstantSendConfirmationsRequired={}",
                                    txHash, outpoint.toStringShort(), txAge, nInstantSendConfirmationsRequired);
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
                    if(confidence != null && confidence.getDepthInBlocks() < nInstantSendConfirmationsRequired) {
                        StoredBlock block = blockStore.get(confidence.getAppearedAtChainHeight());
                        if (chainLocksHandler.hasChainLock(confidence.getAppearedAtChainHeight(), block.getHeader().getHash()))
                        {
                            if (printDebug) {
                                log.info("txid={}: outpoint {} too new and not ChainLocked. nTxAge={}, nInstantSendConfirmationsRequired={}",
                                        txHash, outpoint.toStringShort(), confidence.getDepthInBlocks(), nInstantSendConfirmationsRequired);
                            }
                            return false;
                        }
                    }
                }
            } catch (BlockStoreException x) {
                return false;
            }
        }

        return true;
    }

    boolean processPendingInstantSendLocks() throws BlockStoreException {
        if (isInitialized()) {
            StoredBlock blockTip = blockChain.getChainHead();
            if (blockTip != null && context.getParams().isDIP0024Active(blockTip)) {
                return processPendingInstantSendLocks(true);
            } else {
                // Don't short circuit. Try to process deterministic and not deterministic islocks
                return processPendingInstantSendLocks(false) && processPendingInstantSendLocks(true);
            }
        }
        return false;
    }

    boolean processPendingInstantSendLocks(boolean isDeterministic) throws BlockStoreException {
        HashMap<Sha256Hash, Pair<Long, InstantSendLock>> pend = new HashMap<>();
        boolean moreWork = false;

        if (!isInstantSendEnabled()) {
            return false;
        }

        lock.lock();
        try {
            // only process a max 32 locks at a time to avoid duplicate verification of recovered signatures which have been
            // verified by CSigningManager in parallel
            final int maxCount = 32;
            // The keys of the removed values are temporaily stored here to avoid invalidating an iterator
            ArrayList<Sha256Hash> removed = Lists.newArrayListWithExpectedSize(maxCount);

            for (Map.Entry<Sha256Hash, Pair<Long, InstantSendLock>> entry : pendingInstantSendLocks.entrySet()) {
                Sha256Hash islockHash = entry.getKey();
                InstantSendLock islock = entry.getValue().getSecond();

                if (pend.size() >= maxCount) {
                    moreWork = true;
                    break;
                }
                // Check if we care about this islock on this run
                if (islock.isDeterministic() != isDeterministic) {
                    continue;
                }
                pend.put(islockHash, entry.getValue());
                removed.add(islockHash);
            }

            for (Sha256Hash islockHash : removed) {
                pendingInstantSendLocks.remove(islockHash);
            }

            // TODO: do we need to do this?  How shall we handle failed ISLock verification
            // try to process the invalidInstantSendLocks again
            // for (InstantSendLock isLock : invalidInstantSendLocks.keySet())
            //     pendingInstantSendLocks.put(isLock.getHash(), new Pair<>(Long.valueOf(0L), isLock));

        } finally {
            lock.unlock();
        }

        if (pend.isEmpty()) {
            return false;
        }

        LLMQParameters.LLMQType llmqType = isDeterministic ? context.getParams().getLlmqDIP0024InstantSend()
                : context.getParams().getLlmqForInstantSend();
        int dkgInterval = LLMQParameters.fromType(llmqType).dkgInterval;

        // First check against the current active set and don't ban
        HashSet<Sha256Hash> badISLocks = processPendingInstantSendLocks(llmqType, 0, pend, false);
        if (!badISLocks.isEmpty()) {
            log.info("InstantSendManager -- doing verification on old active set");

            // filter out valid IS locks from "pend"
            Iterator<Map.Entry<Sha256Hash, Pair<Long, InstantSendLock>>> iterator = pend.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Sha256Hash, Pair<Long, InstantSendLock>> entry = iterator.next();
                if (!badISLocks.contains(entry.getKey())) {
                    iterator.remove();
                }
            }
            // Now check against the previous active set and perform banning if this fails
            processPendingInstantSendLocks(llmqType, dkgInterval, pend, true);
        }

        return moreWork;
    }

    HashSet<Sha256Hash> processPendingInstantSendLocks(LLMQParameters.LLMQType llmqType, int signOffset, HashMap<Sha256Hash, Pair<Long, InstantSendLock>> pend, boolean ban) {
        int tipHeight;
        tipHeight = blockChain.getBestChainHeight();
        HashSet<Sha256Hash> badISLocks = new HashSet<>(pend.size());

        BLSBatchVerifier<Long, Sha256Hash> batchVerifier = new BLSBatchVerifier<>(false, true, 8);
        HashMap<Sha256Hash, Pair<Quorum, RecoveredSignature>> recSigs = new HashMap<>();

        int verifyCount = 0;
        int alreadyVerified = 0;
        int signHeight = -1;
        Quorum quorum = null;
        for (Map.Entry<Sha256Hash, Pair<Long, InstantSendLock>> p : pend.entrySet()) {
            Sha256Hash hash = p.getKey();

            if(!masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.INSTANTSENDLOCK)) {
                //If we don't verify the instantsendlock as being signed by a quorum...
                processInstantSendLock(p.getValue().getFirst(), hash, p.getValue().getSecond());
                continue;
            }

            long nodeId = p.getValue().getFirst().longValue();
            InstantSendLock islock = p.getValue().getSecond();
    
            if (batchVerifier.getBadSources().contains(nodeId)) {
                log.info("islock: bad sources contains this node: {}", nodeId);
                continue;
            }
    
            if (!islock.signature.getSignature().isValid()) {
                log.info("islock: signature is not valid: {}", islock.signature);
                batchVerifier.getBadSources().add(nodeId);
                continue;
            }
    
            Sha256Hash id = islock.getRequestId();
    
            // no need to verify an ISLOCK if we already have verified the recovered sig that belongs to it
            if (quorumSigningManager.hasRecoveredSig(llmqType, id, islock.txid)) {
                log.info("islock: signature has already been verified: {}", islock.txid);
                continue;
            }

            if (islock.isDeterministic()) {

                final StoredBlock blockIndex;
                try {
                    blockIndex = blockChain.getBlockStore().get(islock.cycleHash);
                } catch (BlockStoreException e) {
                    throw new RuntimeException(e);
                } catch (NullPointerException e) {
                    // blockchain is now null?
                    if (isInitialized()) {
                        throw e;
                    } else {
                        return Sets.newHashSet();
                    }
                }

                if (blockIndex == null) {
                    batchVerifier.getBadSources().add(nodeId);
                    continue;
                }

                final int dkgInterval = LLMQParameters.fromType(llmqType).dkgInterval;
                if (blockIndex.getHeight() + dkgInterval < blockChain.getBestChainHeight()) {
                    signHeight = blockIndex.getHeight() + dkgInterval - 1;
                }
            }

            quorum = quorumSigningManager.selectQuorumForSigning(llmqType, signHeight, id, signOffset);
            if (quorum == null) {
                // should not happen, but if one fails to select, all others will also fail to select
                log.info("islock: quorum not found to verify signature [tipHeight: {} vs {}]", tipHeight,
                        masternodeListManager.getQuorumListAtTip(llmqType).getHeight());
                log.info("islock: signHeight: {}, id: {}", signHeight, id);
                log.info("islock: dash-cli quorum selectquorum {} {}", llmqType.getValue(), Sha256Hash.wrap(id.getReversedBytes()));
                invalidInstantSendLocks.put(islock, Utils.currentTimeSeconds());
                return badISLocks;
            }
            Sha256Hash signHash = LLMQUtils.buildSignHash(llmqType, quorum.getCommitment().quorumHash, id, islock.txid);

            // debug
            islock.setQuorum(quorum.getQuorumHash(), quorum.getQuorumIndex());
            batchVerifier.pushMessage(nodeId, hash, signHash, islock.signature.getSignature(), quorum.getCommitment().quorumPublicKey.getPublicKey());
            verifyCount++;

            quorumSigningManager.logSignature("ISLOCK", quorum.getCommitment().quorumPublicKey.getPublicKey(), signHash, islock.signature.getSignature());

            // We can reconstruct the RecoveredSignature objects from the islock and pass it to the signing manager, which
            // avoids unnecessary double-verification of the signature. We however only do this when verification here
            // turns out to be good (which is checked further down)
            if (!quorumSigningManager.hasRecoveredSigForId(llmqType, id)) {
                RecoveredSignature recSig = new RecoveredSignature();
                recSig.llmqType = llmqType.getValue();
                recSig.quorumHash = quorum.getCommitment().quorumHash;
                recSig.id = id;
                recSig.msgHash = islock.txid;
                recSig.signature = islock.signature;
                recSigs.put(hash, new Pair<>(quorum, recSig));
            }
        }

        Stopwatch timer = Stopwatch.createStarted();
        if(masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.BLS_SIGNATURES))
            batchVerifier.verify();

        log.info("InstantSendManager -- verified locks. count={}, alreadyVerified={}, vt={}, nodes={}",
                verifyCount, alreadyVerified, timer, batchVerifier.getUniqueSourceCount());

        if (!batchVerifier.getBadSources().isEmpty()) {
            log.warn("islock: bad sources: {}", batchVerifier.getBadSources());
            for (Long nodeId : batchVerifier.getBadSources()) {
                // Let's not be too harsh, as the peer might simply be unlucky and might have sent us an old lock which
                // does not validate anymore due to changed quorums
                // TODO: Dash Core increases ban score of the peer by 20
            }
        }
        for (Map.Entry<Sha256Hash, Pair<Long, InstantSendLock>>  p : pend.entrySet()) {
            Sha256Hash hash = p.getKey();
            long nodeId = p.getValue().getFirst();
            InstantSendLock islock = p.getValue().getSecond();

            if (batchVerifier.getBadMessages().contains(hash)) {
                log.info("islock: -- txid={}, islock={}: invalid sig in islock, peer={}",
                        islock.txid, hash, nodeId);
                badISLocks.add(hash);

                // report invalid signature to the logs
                log.info("islock: validation failed: {}", islock);
                log.info("islock:   signHeight: {}", signHeight);
                log.info("islock:   quorum: {}", quorum.getQuorumHash());
                Sha256Hash id = islock.getRequestId();
                log.info("islock: dash-cli quorum selectquorum {} {}", llmqType.getValue(), Sha256Hash.wrap(id.getReversedBytes()));


                // TODO: how shall we handle failed islock verification? should we test again or forget?
                // testing again means that we might be a block behind or something
                // for now, let us not save this to be retested forever...
                // invalidInstantSendLocks.put(islock, Utils.currentTimeSeconds());
                TransactionConfidence confidence = context.getConfidenceTable().getOrCreate(islock.txid);
                if (confidence != null) {
                    log.info("islock: set to IX_LOCK_FAILED for {}", islock.txid);
                    confidence.setIXType(TransactionConfidence.IXType.IX_LOCK_FAILED);
                    confidence.queueListeners(TransactionConfidence.Listener.ChangeReason.IX_TYPE);
                } else {
                    log.info("confidence for {} not found", islock.txid);
                }
                continue;
            }

            processInstantSendLock(nodeId, hash, islock);

            // See comment further on top. We pass a reconstructed recovered sig to the signing manager to avoid
            // double-verification of the sig.
            Pair<Quorum, RecoveredSignature> it = recSigs.get(hash);
            if (it != null) {
                quorum = it.getFirst();
                RecoveredSignature recSig = it.getSecond();
                if (!quorumSigningManager.hasRecoveredSigForId(llmqType, recSig.id)) {
                    recSig.updateHash();
                    log.info("passing reconstructed recSig to signing mgr -- txid={}, islock={}: peer={}",
                            islock.txid, hash, nodeId);
                    quorumSigningManager.pushReconstructedRecoveredSig(recSig, quorum);
                }
            }
        }

        return badISLocks;
    }

    void processInstantSendLock(long from, Sha256Hash hash, InstantSendLock islock)
    {
        StoredBlock minedBlock = null;

        TransactionConfidence confidence = context.getConfidenceTable().getOrCreate(hash);
        if (confidence != null && confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
            long height = confidence.getAppearedAtChainHeight();

            try {
                StoredBlock block = blockChain.getBlockStore().get((int)height);
                if(block != null) {
                    // Let's see if the TX that was locked by this islock is already mined in a ChainLocked block. If yes,
                    // we can simply ignore the islock, as the ChainLock implies locking of all TXs in that chain
                    if (chainLocksHandler.hasChainLock(height, block.getHeader().getHash())) {
                        log.info("txlock={}, islock={}: dropping islock as it already got a ChainLock in block {}, peer={}",
                                islock.txid, hash, block.getHeader().getHash(), from);
                        return;
                    }
                }
            } catch (BlockStoreException x) {
                //swallow
            }
        }

        lock.lock();
        try
        {
            log.info("processing islock txid={}, islock={}:  peer={}",
                    islock.txid, hash, from);

            InstantSendLock otherIsLock;
            if (db.getInstantSendLockByHash(hash) != null) {
                return;
            }
            otherIsLock = db.getInstantSendLockByTxid(islock.txid);
            if (otherIsLock != null) {
                log.info("duplicate islock:  txid={}, islock={}: other islock={}, peer={}",
                        islock.txid, hash, otherIsLock, from);
            }
            for (TransactionOutPoint in : islock.inputs) {
                otherIsLock = db.getInstantSendLockByInput(in);
                if (otherIsLock != null) {
                    log.info("processInstantSendLock -- txid={}, islock={}: conflicting input in islock. input={}, other islock={}, peer={}",
                            islock.txid, hash, in.toStringShort(), otherIsLock.getHash(), from);
                }
            }

            db.writeNewInstantSendLock(hash, islock);
            if (minedBlock != null) {
                db.writeInstantSendLockMined(hash, minedBlock.getHeight());
            }

            //
            // If an ISLOCK was originally invalid, but was later validated
            // remove it here
            //
            if(invalidInstantSendLocks.containsKey(islock))
                invalidInstantSendLocks.remove(islock);
        } finally {
            lock.unlock();
        }

        removeMempoolConflictsForLock(hash, islock);
        updateWalletTransaction(islock.txid, null);
    }

    void updateWalletTransaction(Sha256Hash txid, Transaction tx) {
        TransactionConfidence confidence = tx != null ? tx.getConfidence() : context.getConfidenceTable().getOrCreate(txid);
        if (confidence != null) {
            scheduledExecutorService.schedule(() -> {
                confidence.setIXType(TransactionConfidence.IXType.IX_LOCKED);
                confidence.queueListeners(TransactionConfidence.Listener.ChangeReason.IX_TYPE);
            }, 250, TimeUnit.MILLISECONDS);
        } else {
            log.info("confidence for {} not found", txid);
        }
    }

    public void syncTransaction(Transaction tx, StoredBlock block, int posInBlock) {
        if (!isInstantSendEnabled()) {
            return;
        }

        if (tx.isCoinBase() || tx.getInputs().isEmpty()) {
            // coinbase can't and TXs with no inputs be locked
            return;
        }

        boolean isDisconnect = block != null && posInBlock == -1;

        Sha256Hash islockHash;
        lock.lock();
        try {
            islockHash = db.getInstantSendLockHashByTxid(tx.getTxId());

            // update DB about when an IS lock was mined
            if (islockHash != null && !islockHash.isZero() && block != null) {
                if (isDisconnect) {
                    db.removeInstantSendLockMined(islockHash, block.getHeight());
                } else {
                    db.writeInstantSendLockMined(islockHash, block.getHeight());
                }
                // remove related InstantSend Locks from the invalid list
                List<InstantSendLock> keysToRemove = new ArrayList<>();

                for (Map.Entry<InstantSendLock, Long> entry : invalidInstantSendLocks.entrySet()) {
                    if (entry.getKey().getHash().equals(tx.getTxId())) {
                        keysToRemove.add(entry.getKey());
                    }
                }

                for (InstantSendLock key : keysToRemove) {
                    invalidInstantSendLocks.remove(key);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    ChainLockListener chainLockListener = block -> handleFullyConfirmedBlock(block.getHeight());

    NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {

            if (sporkManager.isSporkActive(SporkId.SPORK_19_CHAINLOCKS_ENABLED)) {
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
                        islock.txid, islockHash);
            }

            // Keep invalid ISLocks for 1 hour
            List<InstantSendLock> keysToRemove = new ArrayList<>();

            for (Map.Entry<InstantSendLock, Long> entry : invalidInstantSendLocks.entrySet()) {
                if (entry.getValue() < Utils.currentTimeSeconds() - context.getParams().getInstantSendKeepLock()) {
                    keysToRemove.add(entry.getKey());
                }
            }

            for (InstantSendLock key : keysToRemove) {
                invalidInstantSendLocks.remove(key);
            }
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
        //TODO:  should full verification mode have this?
    }

    public InstantSendLock getInstantSendLockByHash(Sha256Hash hash)
    {
        if (!isInstantSendEnabled()) {
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

    public InstantSendLock getInstantSendLockByTxId(Sha256Hash hash)
    {
        if (!isInstantSendEnabled()) {
            return null;
        }

        lock.lock();
        try {
            InstantSendLock islock = db.getInstantSendLockByTxid(hash);
            if (islock == null) {
                // search invalidInstantSendLocks
                for (InstantSendLock invalidLock : invalidInstantSendLocks.keySet()) {
                    if (invalidLock.txid.equals(hash)) {
                        return invalidLock;
                    }
                }

                // search pendingInstantSendLocks
                for (Pair<Long, InstantSendLock> entry : pendingInstantSendLocks.values()) {
                    InstantSendLock pendingLock = entry.getSecond();
                    if (pendingLock.txid.equals(hash)) {
                        return pendingLock;
                    }
                }
                return null; // cannot find the lock associated with the txid
            } else {
                return islock;
            }
        } finally {
            lock.unlock();
        }
    }

    boolean isLocked(Sha256Hash txHash)
    {
        if (!isInstantSendEnabled()) {
            return false;
        }

        lock.lock();
        try {
            return db.getInstantSendLockByTxid(txHash) != null;
        } finally {
            lock.unlock();
        }
    }

    public boolean isConflicted(Transaction tx) {
        lock.lock();
        try {
            return getConflictingTx(tx) != null;
        } finally {
            lock.unlock();
        }
    }

    public Sha256Hash getConflictingTx(Transaction tx)
    {
        if (!isInstantSendEnabled()) {
            return null;
        }

        lock.lock();
        try {
            for (TransactionInput in :tx.getInputs()){
                InstantSendLock otherIsLock = db.getInstantSendLockByInput(in.getOutpoint());
                if (otherIsLock == null) {
                    continue;
                }

                if (otherIsLock.txid.equals(tx.getTxId())) {
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
        if (!isInstantSendEnabled()) {
            return;
        }

        LLMQParameters.LLMQType llmqType = context.getParams().getLlmqForInstantSend();
        if (llmqType == LLMQParameters.LLMQType.LLMQ_NONE) {
            return;
        }

        // TODO: what does Dash Core do here?
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

    WalletCoinsSentEventListener coinsSentEventListener = (wallet, tx, prevBalance, newBalance) -> syncTransaction(tx, null, -1);

    public void addWallet(Wallet wallet) {
        wallet.addCoinsSentEventListener(coinsSentEventListener);
        wallets.add(wallet);
    }

    public void removeWallet(Wallet wallet) {
        if (wallets.contains(wallet)) {
            wallet.removeCoinsSentEventListener(coinsSentEventListener);
            wallets.remove(wallet);
        }
    }
}
