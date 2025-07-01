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
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class SPVRecoveredSignaturesDatabase extends AbstractManager implements RecoveredSignaturesDatabase {
    private ReentrantLock lock = Threading.lock("SPVRecoveredSignaturesDatabase");

    HashMap<Sha256Hash, RecoveredSignature> mapByMessageHash;
    HashMap<Sha256Hash, RecoveredSignature> mapBySignHash;
    HashMap<Pair<Integer, Sha256Hash>, RecoveredSignature> mapById;
    HashMap<Pair<Integer, Sha256Hash>, Sha256Hash> mapVotes;
    HashMap<Pair<Integer, Sha256Hash>, Boolean> hasSigForIdCache;
    HashMap<Sha256Hash, Boolean> hasSigForSessionCache;
    HashMap<Sha256Hash, Boolean> hasSigForHashCache;
    HashMap<Sha256Hash, Long> mapTimeStamps;


    public SPVRecoveredSignaturesDatabase(Context context) {
        super(context);
        hasSigForHashCache = new HashMap<>();
        hasSigForIdCache = new HashMap<>();
        hasSigForSessionCache = new HashMap<>();
        mapByMessageHash = new HashMap<>();
        mapBySignHash = new HashMap<>();
        mapById = new HashMap<>();
        mapVotes = new HashMap<>();
        mapTimeStamps = new HashMap<>();
    }

    public boolean hasRecoveredSig(LLMQParameters.LLMQType llmqType, Sha256Hash id, Sha256Hash msgHash){
        lock.lock();
        try {
            return mapByMessageHash.containsKey(msgHash);
        } finally {
            lock.unlock();
        }
    }

    public boolean hasRecoveredSigForId(LLMQParameters.LLMQType llmqType, Sha256Hash id) {
        lock.lock();
        try {
            return hasSigForIdCache.containsKey(new Pair<>(llmqType.getValue(), id));
        } finally {
            lock.unlock();
        }
    }

    public boolean hasRecoveredSigForSession(Sha256Hash signHash) {
        lock.lock();
        try {
            return hasSigForSessionCache.containsKey(signHash);
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
            return mapById.get(new Pair<>(llmqType.getValue(), id));
        } finally {
            lock.unlock();
        }
    }

    public void writeRecoveredSig(RecoveredSignature recSig) {
        lock.lock();
        try {
            mapByMessageHash.put(recSig.msgHash, recSig);
            Sha256Hash signHash = LLMQUtils.buildSignHash(recSig.llmqType, recSig.quorumHash, recSig.id, recSig.id);
            mapBySignHash.put(signHash, recSig);
            Pair<Integer, Sha256Hash> idPair = new Pair<>(recSig.llmqType, recSig.id);
            mapById.put(idPair, recSig);
            hasSigForIdCache.put(idPair, true);
            hasSigForSessionCache.put(signHash, true);
            hasSigForHashCache.put(recSig.getHash(), true);
            mapTimeStamps.put(signHash, Utils.currentTimeMillis());
        } finally {
            lock.unlock();
        }
    }

    public void cleanupOldRecoveredSignatures(long maxAge) {
        lock.lock();
        try {
            ArrayList<Sha256Hash> toDelete = new ArrayList<>();

            for(Map.Entry<Sha256Hash, Long> entry : mapTimeStamps.entrySet()) {
                if(entry.getValue() < maxAge)
                    toDelete.add(entry.getKey());
            }

            for(Sha256Hash signHash : toDelete) {
                RecoveredSignature recSig = mapBySignHash.get(signHash);
                mapByMessageHash.remove(recSig.msgHash);
                hasSigForIdCache.remove(new Pair<>(recSig.llmqType, recSig.id));
                hasSigForSessionCache.remove(signHash);
                hasSigForHashCache.remove(recSig.getHash());
                mapTimeStamps.remove(signHash);
                mapBySignHash.remove(signHash);
                mapById.remove(new Pair<>(recSig.llmqType, recSig.id));
            }
        } finally {
            lock.unlock();
        }
    }

    // votes are removed when the recovered sig is written to the db
    public boolean hasVotedOnId(LLMQParameters.LLMQType llmqType, Sha256Hash id) {
        lock.lock();
        try {
            return mapVotes.containsKey(new Pair<>(llmqType.getValue(), id));
        } finally {
            lock.unlock();
        }
    }
    public Sha256Hash getVoteForId(LLMQParameters.LLMQType llmqType, Sha256Hash id) {
        lock.lock();
        try {
            return mapVotes.get(new Pair<>(llmqType.getValue(), id));
        } finally {
            lock.unlock();
        }
    }
    public void writeVoteForId(LLMQParameters.LLMQType llmqType, Sha256Hash id, Sha256Hash msgHash) {
        lock.lock();
        try {
            mapVotes.put(new Pair<>(llmqType.getValue(), id), msgHash);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void parse() throws ProtocolException {
        // there is nothing to load
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        // there is nothing to save
    }

    @Override
    public AbstractManager createEmpty() {
        return new SPVRecoveredSignaturesDatabase(Context.get());
    }

    @Override
    public void checkAndRemove() {
        // there is nothing to check and remove
    }

    @Override
    public void clear() {
        hasSigForHashCache.clear();
        hasSigForIdCache.clear();
        hasSigForSessionCache.clear();
        mapBySignHash.clear();
        mapByMessageHash.clear();
        mapById.clear();
        mapVotes.clear();
        mapTimeStamps.clear();
    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }

    @Override
    public void close() {
        // there is nothing to close
    }
}
