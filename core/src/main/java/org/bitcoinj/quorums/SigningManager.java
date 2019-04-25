package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.quorums.listeners.RecoveredSignatureListener;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

public class SigningManager {
    static final long DEFAULT_MAX_RECOVERED_SIGS_AGE = 60 * 60 * 24 * 7; // keep them for a week

    // when selecting a quorum for signing and verification, we use CQuorumManager::SelectQuorum with this offset as
    // starting height for scanning. This is because otherwise the resulting signatures would not be verifiable by nodes
    // which are not 100% at the chain tip.
    static final int SIGN_HEIGHT_OFFSET = 8;

    Context context;

    private static final Logger log = LoggerFactory.getLogger(SigningManager.class);
    ReentrantLock lock = Threading.lock("SigningManager");
    RecoveredSignaturesDatabase db;

    HashMap<Integer, ArrayList<RecoveredSignature>> pendingRecoveredSigs;
    ArrayList<Pair<RecoveredSignature, Quorum>> pendingReconstructedRecoveredSigs;

    //FastRandomContext rnd;

    QuorumManager quorumManager;
    AbstractBlockChain blockChain;

    long lastCleanupTime;

    public SigningManager(Context context, RecoveredSignaturesDatabase db) {
        this.context = context;
        this.db = db;
        this.lastCleanupTime = 0;
        this.quorumManager = context.quorumManager;
        this.recoveredSigsListeners = new CopyOnWriteArrayList<ListenerRegistration<RecoveredSignatureListener>>();
        this.pendingReconstructedRecoveredSigs = new ArrayList<Pair<RecoveredSignature, Quorum>>();
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
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

    Quorum selectQuorumForSigning(LLMQParameters.LLMQType llmqType, long signHeight, Sha256Hash selectionHash)
    {
        LLMQParameters llmqParams = context.getParams().getLlmqs().get(llmqType);
        int poolSize = (int)llmqParams.signingActiveQuorumCount;


        StoredBlock startBlock = null;
        long startBlockHeight = signHeight - SIGN_HEIGHT_OFFSET;
        if(startBlockHeight > blockChain.getBestChainHeight())
            return null;
        //startBlock = blockChain.getAncestor(startBlockHeight);
        {
            /*LOCK(cs_main);
            int startBlockHeight = signHeight - SIGN_HEIGHT_OFFSET;
            if (startBlockHeight > chainActive.Height()) {
                return nullptr;
            }
            pindexStart = chainActive[startBlockHeight];*/
        }

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
        Collections.sort(scores, new Comparator<Pair<Sha256Hash, Integer>>() {
            @Override
            public int compare(Pair<Sha256Hash, Integer> o1, Pair<Sha256Hash, Integer> o2) {
                int firstResult = o1.getFirst().compareTo(o2.getFirst());
                return firstResult != 0 ? firstResult : o1.getSecond().compareTo(o2.getSecond());
            }
        });
        return quorums.get(scores.get(0).getSecond());
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

    boolean verifyRecoveredSig(LLMQParameters.LLMQType llmqType, long signedAtHeight, Sha256Hash id, Sha256Hash msgHash, BLSSignature sig)
    {
        LLMQParameters llmqParams = context.getParams().getLlmqs().get(context.getParams().getLlmqChainLocks());

        Quorum quorum = selectQuorumForSigning(llmqParams.type, signedAtHeight, id);
        if (quorum == null) {
            return false;
        }

        Sha256Hash signHash = LLMQUtils.buildSignHash(llmqParams.type, quorum.commitment.quorumHash, id, msgHash);
        return sig.verifyInsecure(quorum.commitment.quorumPublicKey, signHash);
    }
}
