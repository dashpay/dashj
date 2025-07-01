package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class SPVRecoveredSignaturesDatabase extends AbstractManager implements RecoveredSignaturesDatabase {

    private static final Logger log = LoggerFactory.getLogger(SPVRecoveredSignaturesDatabase.class);
    private ReentrantLock lock = Threading.lock("SPVRecoveredSignaturesDatabase");

    SigningManager manager;
    ArrayList<RecoveredSignature> recoveredSignatures;
    HashMap<Sha256Hash, RecoveredSignature> mapBySignHash;
    HashMap<Pair<Integer, Sha256Hash>, RecoveredSignature> mapById;
    HashMap<Pair<Integer, Sha256Hash>, Sha256Hash> mapVotes;
    HashMap<Pair<Integer, Sha256Hash>, Boolean> hasSigForIdCache;
    HashMap<Sha256Hash, Boolean> hasSigForSessionCache;
    HashMap<Sha256Hash, Boolean> hasSigForHashCache;
    HashMap<Sha256Hash, Long> mapTimeStamps;


    public SPVRecoveredSignaturesDatabase(Context context) {
        super(context);
        recoveredSignatures = new ArrayList<RecoveredSignature>();
        hasSigForHashCache = new HashMap<Sha256Hash, Boolean>();
        hasSigForIdCache = new HashMap<Pair<Integer, Sha256Hash>, Boolean>();
        hasSigForSessionCache = new HashMap<Sha256Hash, Boolean>();
        mapBySignHash = new HashMap<Sha256Hash, RecoveredSignature>();
        mapById = new HashMap<Pair<Integer, Sha256Hash>, RecoveredSignature>();
        mapVotes = new HashMap<Pair<Integer, Sha256Hash>, Sha256Hash>();
        mapTimeStamps = new HashMap<Sha256Hash, Long>();
    }

    public boolean hasRecoveredSig(LLMQParameters.LLMQType llmqType, Sha256Hash id, Sha256Hash msgHash){
        lock.lock();
        try {
            for(RecoveredSignature recSig : recoveredSignatures) {
                if(recSig.llmqType == llmqType.getValue() && recSig.id.equals(id) && recSig.msgHash.equals(msgHash))
                    return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasRecoveredSigForId(LLMQParameters.LLMQType llmqType, Sha256Hash id) {
        lock.lock();
        try {
            Boolean result = hasSigForIdCache.get(new Pair(llmqType.getValue(), id));
            return result != null ? result : false;
        } finally {
            lock.unlock();
        }
    }

    public boolean hasRecoveredSigForSession(Sha256Hash signHash) {
        lock.lock();
        try {
            Boolean result = hasSigForSessionCache.get(signHash);
            return result != null ? result : false;
        } finally {
            lock.unlock();
        }
    }
    public boolean hasRecoveredSigForHash(Sha256Hash hash) {
        lock.lock();
        try {
            return hasSigForHashCache.containsKey(hash);
        } finally {
            lock.unlock();
        }
    }
    public RecoveredSignature getRecoveredSigByHash(Sha256Hash hash) {
        lock.lock();
        try {
            return mapBySignHash.get(hash);
        } finally {
            lock.unlock();
        }
    }
    public RecoveredSignature getRecoveredSigById(LLMQParameters.LLMQType llmqType, Sha256Hash id) {
        lock.lock();
        try {
            return mapById.get(new Pair(llmqType.value, id));
        } finally {
            lock.unlock();
        }
    }

    public void writeRecoveredSig(RecoveredSignature recSig) {
        lock.lock();
        try {
            Sha256Hash signHash = LLMQUtils.buildSignHash(recSig.llmqType, recSig.quorumHash, recSig.id, recSig.id);
            recoveredSignatures.add(recSig);
            mapBySignHash.put(signHash, recSig);
            Pair<Integer, Sha256Hash> idPair = new Pair(recSig.llmqType, recSig.id);
            mapById.put(idPair, recSig);
            hasSigForIdCache.put(idPair, true);
            hasSigForSessionCache.put(signHash, true);
            hasSigForHashCache.put(recSig.getHash(), true);
            mapTimeStamps.put(signHash, Utils.currentTimeMillis());
            //save();
        } finally {
            lock.unlock();
        }
    }

    public void cleanupOldRecoveredSignatures(long maxAge) {

            lock.lock();
            try {
                ArrayList<Sha256Hash> toDelete = new ArrayList<Sha256Hash>();

                for(Map.Entry<Sha256Hash, Long> entry : mapTimeStamps.entrySet()) {
                    if(entry.getValue() > maxAge)
                        toDelete.add(entry.getKey());
                }

                for(Sha256Hash signHash : toDelete) {
                    RecoveredSignature recSig = mapBySignHash.get(signHash);
                    hasSigForIdCache.remove(new Pair(recSig.llmqType, recSig.id));
                    hasSigForSessionCache.remove(signHash);
                    hasSigForHashCache.remove(recSig.getHash());
                    mapTimeStamps.remove(signHash);
                    recoveredSignatures.remove(recSig);
                    mapBySignHash.remove(signHash);
                    mapById.remove(new Pair(recSig.llmqType, recSig.id));
                }
            } finally {
                lock.unlock();
            }
        }

    // votes are removed when the recovered sig is written to the db
    public boolean hasVotedOnId(LLMQParameters.LLMQType llmqType, Sha256Hash id) {
        lock.lock();
        try {
            return mapVotes.containsKey(new Pair(llmqType.value, id));
        } finally {
            lock.unlock();
        }
    }
    public Sha256Hash getVoteForId(LLMQParameters.LLMQType llmqType, Sha256Hash id) {
        lock.lock();
        try {
            return mapVotes.get(new Pair(llmqType.value, id));
        } finally {
            lock.unlock();
        }
    }
    public void writeVoteForId(LLMQParameters.LLMQType llmqType, Sha256Hash id, Sha256Hash msgHash) {
        lock.lock();
        try {
            mapVotes.put(new Pair(llmqType.value, id), msgHash);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void parse() throws ProtocolException {

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        lock.lock();
        try {

        } finally {
            lock.unlock();
        }
    }

    @Override
    public AbstractManager createEmpty() {
        return new SPVRecoveredSignaturesDatabase(Context.get());
    }

    @Override
    public void checkAndRemove() {

    }

    @Override
    public void clear() {

    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }

    @Override
    public void close() {

    }
}
