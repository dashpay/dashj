package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSBatchVerifier;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.quorums.listeners.RecoveredSignatureListener;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.max;

public class SigningManager {
    static final long DEFAULT_MAX_RECOVERED_SIGS_AGE = 60 * 60 * 24 * 7; // keep them for a week

    // when selecting a quorum for signing and verification, we use CQuorumManager::SelectQuorum with this offset as
    // starting height for scanning. This is because otherwise the resulting signatures would not be verifiable by nodes
    // which are not 100% at the chain tip.
    public static final int SIGN_HEIGHT_OFFSET = 8;

    Context context;

    private static final Logger log = LoggerFactory.getLogger(SigningManager.class);
    ReentrantLock lock = Threading.lock("SigningManager");
    RecoveredSignaturesDatabase db;

    HashMap<Integer, ArrayList<RecoveredSignature>> pendingRecoveredSigs;
    ArrayList<Pair<RecoveredSignature, Quorum>> pendingReconstructedRecoveredSigs;

    //FastRandomContext rnd;

    QuorumManager quorumManager;
    AbstractBlockChain blockChain;
    AbstractBlockChain headerChain;

    long lastCleanupTime;

    File signatureLog;
    public OutputStream signatureStream;

    public SigningManager(Context context, RecoveredSignaturesDatabase db) {
        this.context = context;
        this.db = db;
        this.lastCleanupTime = 0;
        this.quorumManager = context.quorumManager;
        this.recoveredSigsListeners = new CopyOnWriteArrayList<ListenerRegistration<RecoveredSignatureListener>>();
        this.pendingReconstructedRecoveredSigs = new ArrayList<Pair<RecoveredSignature, Quorum>>();
        this.pendingRecoveredSigs = new HashMap<Integer, ArrayList<RecoveredSignature>>();
    }

    public void setBlockChain(AbstractBlockChain blockChain, @Nullable AbstractBlockChain headerChain) {
        this.blockChain = blockChain;
        this.headerChain = headerChain;
    }

    public void close() {
        try {
            if (sigLogInitialized)
                signatureStream.close();
        } catch (IOException x) {
            //do nothing
        }
    }

    private transient CopyOnWriteArrayList<ListenerRegistration<RecoveredSignatureListener>> recoveredSigsListeners;

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addRecoveredSignatureListener(RecoveredSignatureListener listener) {
        addRecoveredSignatureListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addRecoveredSignatureListener(RecoveredSignatureListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        recoveredSigsListeners.add(new ListenerRegistration<RecoveredSignatureListener>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeRecoveredSignatureListener(RecoveredSignatureListener listener) {
        return ListenerRegistration.removeFromList(listener, recoveredSigsListeners);
    }

    private void queueRecoveredSignatureListeners(final RecoveredSignature signature) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<RecoveredSignatureListener> registration : recoveredSigsListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onNewRecoveredSignature(signature);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onNewRecoveredSignature(signature);
                    }
                });
            }
        }
    }

    boolean hasRecoveredSig(LLMQParameters.LLMQType llmqType, Sha256Hash id, Sha256Hash msgHash)
    {
        return db.hasRecoveredSig(llmqType, id, msgHash);
    }

    boolean hasRecoveredSigForId(LLMQParameters.LLMQType llmqType, Sha256Hash id)
    {
        return db.hasRecoveredSigForId(llmqType, id);
    }

    boolean hasRecoveredSigForSession(Sha256Hash signHash)
    {
        return db.hasRecoveredSigForSession(signHash);
    }

    boolean isConflicting(LLMQParameters.LLMQType llmqType, Sha256Hash id, Sha256Hash msgHash)
    {
        if (!db.hasRecoveredSigForId(llmqType, id)) {
            // no recovered sig present, so no conflict
            return false;
        }

        if (!db.hasRecoveredSig(llmqType, id, msgHash)) {
            // recovered sig is present, but not for the given msgHash. That's a conflict!
            return true;
        }

        // all good
        return false;
    }

    boolean hasVotedOnId(LLMQParameters.LLMQType llmqType, Sha256Hash id)
    {
        return db.hasVotedOnId(llmqType, id);
    }

    Sha256Hash getVoteForId(LLMQParameters.LLMQType llmqType, Sha256Hash id)
    {
        return db.getVoteForId(llmqType, id);
    }

    private int getBestChainHeight() {
        if (headerChain != null) {
            return max(blockChain.getBestChainHeight(), headerChain.getBestChainHeight());
        } else {
            return blockChain.getBestChainHeight();
        }
    }

    Quorum selectQuorumForSigning(LLMQParameters.LLMQType llmqType, long signHeight, Sha256Hash selectionHash) throws BlockStoreException
    {
        LLMQParameters llmqParams = context.getParams().getLlmqs().get(llmqType);
        int poolSize = (int)llmqParams.signingActiveQuorumCount;

        if (signHeight == -1) {
            signHeight = getBestChainHeight();
        }

        StoredBlock startBlock = null;
        long startBlockHeight = signHeight - SIGN_HEIGHT_OFFSET;
        if(startBlockHeight > getBestChainHeight() || startBlockHeight < 0)
            return null;


        startBlock = blockChain.getBlockStore().get((int) startBlockHeight);
        if(startBlock == null)
            return null;


        ArrayList<Quorum> quorums = quorumManager.scanQuorums(llmqType, startBlock, poolSize);
        if (quorums.isEmpty()) {
            return null;
        }

        ArrayList<Pair<Sha256Hash, Integer>> scores = new ArrayList<Pair<Sha256Hash, Integer>>(quorums.size());
        for (int i = 0; i < quorums.size(); i++) {
            try {
                UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(1 + 32 + 32);
                bos.write(llmqType.getValue());
                bos.write(quorums.get(i).commitment.quorumHash.getReversedBytes());
                bos.write(selectionHash.getBytes());
                Sha256Hash hash = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
                scores.add(new Pair(hash, i));
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }
        scores.sort(new Comparator<Pair<Sha256Hash, Integer>>() {
            @Override
            public int compare(Pair<Sha256Hash, Integer> o1, Pair<Sha256Hash, Integer> o2) {
                int firstResult = o1.getFirst().compareTo(o2.getFirst());
                return firstResult != 0 ? firstResult : o1.getSecond().compareTo(o2.getSecond());
            }
        });
        return quorums.get(scores.get(0).getSecond());
    }

    void collectPendingRecoveredSigsToVerify(long maxUniqueSessions,
            HashMap<Integer, ArrayList<RecoveredSignature>> retSigShares,
            HashMap<Pair<LLMQParameters.LLMQType, Sha256Hash>, Quorum> retQuorums)
    {
        lock.lock();
        try {
            if (pendingRecoveredSigs.isEmpty()) {
                return;
            }

            HashSet<Pair<Integer, Sha256Hash>> uniqueSignHashes = new HashSet<Pair<Integer, Sha256Hash>>();

            for (Map.Entry<Integer, ArrayList<RecoveredSignature>> entry : pendingRecoveredSigs.entrySet()) {
                if (uniqueSignHashes.size() < maxUniqueSessions)
                    break;
                if (entry.getValue().isEmpty())
                    continue;

                RecoveredSignature recSig = entry.getValue().get(0);

                boolean alreadyHave = db.hasRecoveredSigForHash(recSig.getHash());
                if (!alreadyHave) {
                    uniqueSignHashes.add(new Pair(entry.getKey(), LLMQUtils.buildSignHash(recSig)));
                    if (retSigShares.containsKey(entry.getKey())) {
                        retSigShares.get(entry.getKey()).add(recSig);
                    } else {
                        ArrayList<RecoveredSignature> recSigs = new ArrayList<RecoveredSignature>();
                        recSigs.add(recSig);
                        retSigShares.put(entry.getKey(), recSigs);
                    }
                }

            }
        } finally {
            lock.unlock();
        }


        for (Map.Entry<Integer, ArrayList<RecoveredSignature>> p : retSigShares.entrySet()) {
            int nodeId = p.getKey();
            ArrayList<RecoveredSignature> v = p.getValue();

            Iterator<RecoveredSignature> it = v.iterator();
            while (it.hasNext()) {

                RecoveredSignature recSig = it.next();
                LLMQParameters.LLMQType llmqType = LLMQParameters.LLMQType.fromValue(recSig.llmqType);
                Pair<LLMQParameters.LLMQType, Sha256Hash> quorumKey = new Pair(LLMQParameters.LLMQType.fromValue(recSig.llmqType), recSig.quorumHash);
                if (!retQuorums.containsKey(quorumKey)) {
                    Quorum quorum = quorumManager.getQuorum(llmqType, recSig.quorumHash);
                    if (quorum == null) {
                        log.error("quorum {} not found, node={}",
                                recSig.quorumHash.toString(), nodeId);
                        it.remove();
                        continue;
                    }
                    if (quorumManager.isQuorumActive(llmqType, quorum.commitment.quorumHash)) {
                        log.info("quorum {} not active anymore, node={}",
                                recSig.quorumHash.toString(), nodeId);
                        it.remove();
                        continue;
                    }

                    retQuorums.put(quorumKey, quorum);
                }
            }
        }
    }

    boolean processPendingReconstructedRecoveredSigs()
    {
        ArrayList<Pair<RecoveredSignature, Quorum>> listCopy;
        lock.lock();
        try {
            if(pendingReconstructedRecoveredSigs.isEmpty())
                return false;
            listCopy = new ArrayList<Pair<RecoveredSignature, Quorum>>(pendingReconstructedRecoveredSigs);
            pendingReconstructedRecoveredSigs = new ArrayList<Pair<RecoveredSignature, Quorum>>();
        } finally {
            lock.unlock();
        }
        for (Pair<RecoveredSignature, Quorum> pair : listCopy) {
            processRecoveredSig(-1, pair.getFirst(), pair.getSecond());
        }
        return true;
    }

    boolean processPendingRecoveredSigs()
    {
        if(!processPendingReconstructedRecoveredSigs())
            return false;

        HashMap<Integer, ArrayList<RecoveredSignature>> recSigsByNode = new HashMap<>();
        HashMap<Pair<LLMQParameters.LLMQType, Sha256Hash>, Quorum> quorums = new HashMap<>();

        collectPendingRecoveredSigsToVerify(32, recSigsByNode, quorums);
        if (recSigsByNode.isEmpty()) {
            return false;
        }

        // It's ok to perform insecure batched verification here as we verify against the quorum public keys, which are not
        // craftable by individual entities, making the rogue public key attack impossible
        BLSBatchVerifier<Integer, Sha256Hash> batchVerifier = new BLSBatchVerifier<Integer, Sha256Hash>(false, false);

        long verifyCount = 0;
        for (Map.Entry<Integer, ArrayList<RecoveredSignature>> p : recSigsByNode.entrySet()) {
            int nodeId = p.getKey();
            ArrayList<RecoveredSignature> v = p.getValue();

            for (RecoveredSignature recSig : v) {
                // we didn't verify the lazy signature until now
                if (!recSig.signature.getSignature().isValid()) {
                    batchVerifier.getBadSources().add(nodeId);
                    break;
                }

                Quorum quorum = quorums.get(new Pair<>(recSig.llmqType, recSig.quorumHash));
                batchVerifier.pushMessage(nodeId, recSig.getHash(), LLMQUtils.buildSignHash(recSig), recSig.signature.getSignature(), quorum.commitment.quorumPublicKey);
                verifyCount++;

                logSignature("RECSIG", quorum.commitment.quorumPublicKey, LLMQUtils.buildSignHash(recSig), recSig.signature.getSignature());
            }
        }

        //cxxtimer::Timer verifyTimer(true);
        long start = Utils.currentTimeMillis();
        if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.BLS_SIGNATURES))
            batchVerifier.verify();
        long end = Utils.currentTimeMillis();
        //verifyTimer.stop();

        log.info("verified recovered sig(s). count={}, vt={}, nodes={}", verifyCount, end-start, recSigsByNode.size());

        HashSet<Sha256Hash> processed = new HashSet<Sha256Hash>();
        for (Map.Entry<Integer, ArrayList<RecoveredSignature>> p : recSigsByNode.entrySet()) {
            int nodeId = p.getKey();
            ArrayList<RecoveredSignature> v = p.getValue();

            if (batchVerifier.getBadSources().contains(nodeId)) {
                log.info("processPendingRecoveredSigs -- invalid recSig from other node, banning peer={}", nodeId);
                // TODO: Dash Core increases ban score of the peer by 100
                continue;
            }

            for (RecoveredSignature recSig : v) {
                if (!processed.add(recSig.getHash())) {
                    continue;
                }

                Quorum quorum = quorums.get(new Pair(recSig.llmqType, recSig.quorumHash));
                processRecoveredSig(nodeId, recSig, quorum);
            }
        }

        return true;
    }

    // signature must be verified already
    void processRecoveredSig(int nodeId, RecoveredSignature recoveredSig, Quorum quorum)
    {
        LLMQParameters.LLMQType llmqType = LLMQParameters.LLMQType.fromValue(recoveredSig.llmqType);

        lock.lock();
        try {
            

            Sha256Hash signHash = LLMQUtils.buildSignHash(recoveredSig);

            log.info("valid recSig. signHash={}, id={}, msgHash={}, node={}",
                    signHash.toString(), recoveredSig.id.toString(), recoveredSig.msgHash.toString(), nodeId);

            if (db.hasRecoveredSigForId(llmqType, recoveredSig.id)) {
                RecoveredSignature otherRecoveredSig = db.getRecoveredSigById(llmqType, recoveredSig.id);
                if (otherRecoveredSig != null) {
                    Sha256Hash otherSignHash = LLMQUtils.buildSignHash(recoveredSig);
                    if (!signHash.equals(otherSignHash)) {
                        // this should really not happen, as each masternode is participating in only one vote,
                        // even if it's a member of multiple quorums. so a majority is only possible on one quorum and one msgHash per id
                        log.info("CSigningManager::processRecoveredSig -- conflicting recoveredSig for signHash={}, id={}, msgHash={}, otherSignHash={}",
                                signHash.toString(), recoveredSig.id.toString(), recoveredSig.msgHash.toString(), otherSignHash.toString());
                    } else {
                        // Looks like we're trying to process a recSig that is already known. This might happen if the same
                        // recSig comes in through regular QRECSIG messages and at the same time through some other message
                        // which allowed to reconstruct a recSig (e.g. ISLOCK). In this case, just bail out.
                    }
                    return;
                } else {
                    // This case is very unlikely. It can only happen when cleanup caused this specific recSig to vanish
                    // between the HasRecoveredSigForId and GetRecoveredSigById call. If that happens, treat it as if we
                    // never had that recSig
                }
            }
            db.writeRecoveredSig(recoveredSig);

            queueRecoveredSignatureListeners(recoveredSig);
        } finally {
            lock.unlock();
        }
    }

    public void pushReconstructedRecoveredSig(RecoveredSignature recoveredSig, Quorum quorum)
    {
        lock.lock();
        try {
            pendingReconstructedRecoveredSigs.add(new Pair(recoveredSig, quorum));
        } finally {
            lock.unlock();
        }
    }

    boolean verifyRecoveredSig(LLMQParameters.LLMQType llmqType, long signedAtHeight, Sha256Hash id, Sha256Hash msgHash, BLSSignature sig) throws BlockStoreException, QuorumNotFoundException
    {
        LLMQParameters llmqParams = context.getParams().getLlmqs().get(context.getParams().getLlmqChainLocks());

        Quorum quorum = selectQuorumForSigning(llmqParams.type, signedAtHeight, id);
        if (quorum == null) {
            boolean missingBlockAtTip = getBestChainHeight() < signedAtHeight;
            throw new QuorumNotFoundException(missingBlockAtTip ?
                    QuorumNotFoundException.Reason.BLOCKCHAIN_NOT_SYNCED :
                    QuorumNotFoundException.Reason.MISSING_QUORUM);
        }

        Sha256Hash signHash = LLMQUtils.buildSignHash(llmqParams.type, quorum.commitment.quorumHash, id, msgHash);

        logSignature("RECSIG", quorum.commitment.quorumPublicKey, signHash, sig);

        if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.BLS_SIGNATURES))
            return sig.verifyInsecure(quorum.commitment.quorumPublicKey, signHash);
        else return true; //if we don't verify BLS_SIGNATUREs, then assume true
    }

    void cleanup()
    {
        long now = Utils.currentTimeMillis();
        if (now - lastCleanupTime < 5000) {
            return;
        }

        long maxAge = DEFAULT_MAX_RECOVERED_SIGS_AGE;

        db.cleanupOldRecoveredSignatures(maxAge);

        lastCleanupTime = Utils.currentTimeMillis();
    }

    public void logSignature(String type, BLSPublicKey publicKey, Sha256Hash messageHash, BLSSignature signature) {
        if(context.masternodeSync.hasFeatureFlag(MasternodeSync.FEATURE_FLAGS.LOG_SIGNATURES)) {
            if(!sigLogInitialized)
                initializeSignatureLog();
            try {
                String line = type + "|" + publicKey + "|" + messageHash + "|" + signature + "\n";
                signatureStream.write(line.getBytes());
                signatureStream.flush();
            } catch (IOException x) {

            }
        }
    }

    private String directory = null;
    private boolean sigLogInitialized = false;

    public void initializeSignatureLog(String directory) {
        this.directory = directory;
        initializeSignatureLog();
    }

    public void initializeSignatureLog() {
        if(!context.masternodeSync.hasFeatureFlag(MasternodeSync.FEATURE_FLAGS.LOG_SIGNATURES))
            return;
        try {
            boolean exists = false;
            signatureLog = new File(directory, "signature-log.txt");
            if(!signatureLog.exists()) {
                exists = true;
            }
            signatureStream = new FileOutputStream(signatureLog, true);
            if(!exists) {
                signatureStream.write("Type|PublicKey|Message|Signature\n".getBytes());
            }
            sigLogInitialized = true;
        } catch (IOException x) {
            // swallow
        }
    }
}
