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
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.evolution.*;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.manager.DashSystem;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.MerkleRoot;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class SimplifiedQuorumList extends Message {

    // critical section to protect the inner data structures
    ReentrantLock lock = Threading.lock("SimplifiedQuorumList");
    private static final Logger log = LoggerFactory.getLogger(SimplifiedQuorumList.class);


    private Sha256Hash blockHash;
    private long height;
    private boolean isFirstQuorumCheck;
    HashMap<Pair<Integer, Sha256Hash>, Sha256Hash> minableCommitmentsByQuorum;
    LinkedHashMap<Sha256Hash, FinalCommitment> minableCommitments;
    private CoinbaseTx coinbaseTxPayload;

    public SimplifiedQuorumList(NetworkParameters params) {
        super(params);
        blockHash = params.getGenesisBlock().getHash();
        height = -1;
        minableCommitmentsByQuorum = new HashMap<>(10);
        minableCommitments = new LinkedHashMap<>(10);
        isFirstQuorumCheck = true;
    }

    public SimplifiedQuorumList(NetworkParameters params, byte [] payload, int offset, int protocolVersion) {
        super(params, payload, offset, protocolVersion);
    }

    public SimplifiedQuorumList(SimplifiedQuorumList other) {
        super(other.params);
        this.blockHash = other.blockHash;
        this.height = other.height;
        minableCommitmentsByQuorum = new HashMap<>(other.minableCommitmentsByQuorum);
        minableCommitments = new LinkedHashMap<>(other.minableCommitments);
        this.isFirstQuorumCheck = other.isFirstQuorumCheck;
    }

    @Override
    protected void parse() throws ProtocolException {
        blockHash = readHash();
        height = (int)readUint32();
        int size = (int)readVarInt();
        minableCommitmentsByQuorum = new HashMap<>(size);
        for(int i = 0; i < size; ++i)
        {
            int type = readBytes(1)[0];
            Sha256Hash hash = readHash();
            Sha256Hash hash2 = readHash();
            minableCommitmentsByQuorum.put(new Pair<>(type, hash), hash2);
        }

        size = (int)readVarInt();
        minableCommitments = new LinkedHashMap<>(size);
        for(long i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            FinalCommitment commitment = new FinalCommitment(params, payload, cursor);
            cursor += commitment.getMessageSize();
            minableCommitments.put(hash, commitment);
        }
        isFirstQuorumCheck = true;
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(blockHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(height, stream);

        stream.write(new VarInt(minableCommitmentsByQuorum.size()).encode());
        for(Map.Entry<Pair<Integer, Sha256Hash>, Sha256Hash> entry : minableCommitmentsByQuorum.entrySet()) {
            stream.write(entry.getKey().getFirst());
            stream.write(entry.getKey().getSecond().getReversedBytes());
            stream.write(entry.getValue().getReversedBytes());
        }
        stream.write(new VarInt(minableCommitments.size()).encode());
        for(Map.Entry<Sha256Hash, FinalCommitment> entry : minableCommitments.entrySet()) {
            stream.write(entry.getKey().getReversedBytes());
            entry.getValue().bitcoinSerializeToStream(stream);
        }
    }

    public int size() {
        return minableCommitments.size();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SimplifiedQuorumList(count: ").append(size()).append("; ").append(height).append(")");

        if (Context.get().isDebugMode()) {
            for (Map.Entry<Sha256Hash, FinalCommitment> entry : minableCommitments.entrySet()) {
                builder.append("\n ").append(entry.getValue().llmqType).append(": ").append(entry.getValue().quorumHash)
                        .append(":").append(entry.getValue().quorumIndex);
            }
        }

        return builder.toString();
    }

    BLSSignature getSignatureForIndex(Map<BLSSignature, HashSet<Integer>> quorumsCLSigs, int index) {
        Optional<Map.Entry<BLSSignature, HashSet<Integer>>> answer = quorumsCLSigs.entrySet().stream().filter(entry -> entry.getValue().contains(index)).findFirst();
        return answer.map(Map.Entry::getKey).orElse(null);
    }

    public SimplifiedQuorumList applyDiff(SimplifiedMasternodeListDiff diff, boolean isLoadingBootstrap, DualBlockChain chain, MasternodeListManager masternodeListManager, ChainLocksHandler chainLocksHandler, boolean doDIP24, boolean validateOldQuorums) throws MasternodeListDiffException{
        lock.lock();
        try {
            CoinbaseTx cbtx = (CoinbaseTx) diff.getCoinBaseTx().getExtraPayloadObject();
            if(!diff.getPrevBlockHash().equals(blockHash))
                throw new MasternodeListDiffException("The mnlistdiff does not connect to this quorum.  height: " +
                        height + " vs " + cbtx.getHeight(), false, false, height == cbtx.getHeight(), false);

            SimplifiedQuorumList result = new SimplifiedQuorumList(this);
            result.blockHash = diff.getBlockHash();
            result.height = cbtx.getHeight();
            result.coinbaseTxPayload = cbtx;

            for (Pair<Integer, Sha256Hash> quorum : diff.getDeletedQuorums()) {
                result.removeCommitment(quorum);
            }
            for (int i = 0; i < diff.getNewQuorums().size(); ++i) {
                FinalCommitment entry = diff.getNewQuorums().get(i);
                BLSSignature signature = diff.getQuorumsCLSigs() != null ?
                        getSignatureForIndex(diff.getQuorumsCLSigs(), i) : null;
                if (signature != null)
                   chainLocksHandler.addCoinbaseChainLock(entry.quorumHash, 8, signature);

                // find a better way to do this
                if ((doDIP24 && entry.llmqType == params.getLlmqDIP0024InstantSend().value) || (!doDIP24 && entry.llmqType != params.getLlmqDIP0024InstantSend().value)) {
                    // for now, don't use the return value
                    verifyQuorum(isLoadingBootstrap, chain, masternodeListManager, validateOldQuorums, entry);
                }
                result.addCommitment(entry);
            }
            return result;
        } catch (BlockStoreException x) {
            throw new ProtocolException(x);
        } finally {
            lock.unlock();
        }
    }

    public void verifyQuorums(boolean isLoadingBootstrap, DualBlockChain chain, MasternodeListManager masternodeListManager, boolean validateOldQuorums) throws BlockStoreException, ProtocolException{
        lock.lock();
        try {
            int verifiedCount = 0;
            for (FinalCommitment entry : minableCommitments.values()) {
                if (verifyQuorum(isLoadingBootstrap, chain, masternodeListManager, validateOldQuorums, entry)) {
                    verifiedCount++;
                }
            }
            log.info("verified {} of {} quorums", verifiedCount, minableCommitments.size());
        } finally {
            lock.unlock();
        }
    }

    private boolean verifyQuorum(boolean isLoadingBootstrap, DualBlockChain chain,
                                 MasternodeListManager masternodeListManager,
                                 boolean validateOldQuorums, FinalCommitment entry) throws BlockStoreException {
        StoredBlock block = chain.getBlock(entry.getQuorumHash());
        if (block != null) {
            LLMQParameters llmqParameters = params.getLlmqs().get(entry.getLlmqType());
            if (llmqParameters == null)
                throw new ProtocolException("Quorum llmqType is invalid: " + entry.llmqType);
            // only check the dgkInterval on pre DIP24 blocks
            if (llmqParameters.type != params.getLlmqDIP0024InstantSend()) {
                int dkgInterval = llmqParameters.dkgInterval;
                if (block.getHeight() % dkgInterval != 0)
                    throw new ProtocolException("Quorum block height does not match interval for " + entry.quorumHash);
            }
            boolean isVerified = checkCommitment(entry, block, masternodeListManager, chain, validateOldQuorums);
            isFirstQuorumCheck = false;
            return isVerified;
        } else {
            int chainHeight = chain.getBestChainHeight();
            //if quorum was created before DIP8 activation, then allow it to be added
            if (chainHeight >= params.getDIP0008BlockHeight() || !isLoadingBootstrap) {
                //for some reason llmqType 2 quorumHashs are from block 72000, which is before DIP8 on testnet.
                if (!params.getId().equals(NetworkParameters.ID_TESTNET) && !isFirstQuorumCheck &&
                        (entry.llmqType != LLMQParameters.LLMQType.LLMQ_400_60.value && entry.llmqType != LLMQParameters.LLMQType.LLMQ_400_85.value) &&
                        !params.getAssumeValidQuorums().contains(entry.quorumHash)) {
                    throw new ProtocolException("QuorumHash not found: " + entry.quorumHash);
                }
            }
            return false;
        }
    }

    void addCommitment(FinalCommitment commitment)
    {
        Sha256Hash commitmentHash = commitment.getHash();

        lock.lock();
        try {
            Pair<Integer, Sha256Hash> pair = new Pair<>(commitment.llmqType, commitment.quorumHash);
            minableCommitmentsByQuorum.put(pair, commitmentHash);
            minableCommitments.put(commitmentHash, commitment);
        } finally {
            lock.unlock();
        }

    }

    void removeCommitment(Pair<Integer, Sha256Hash> quorum)
    {
        lock.lock();
        try {
            if (minableCommitmentsByQuorum.containsKey(quorum)) {
                Sha256Hash commitmentHash = minableCommitmentsByQuorum.get(quorum);
                minableCommitments.remove(commitmentHash);
                minableCommitmentsByQuorum.remove(quorum);
            }
        } finally {
            lock.unlock();
        }
    }

    public FinalCommitment getCommitment(Sha256Hash commitmentHash)
    {
        lock.lock();
        try {
            return minableCommitments.get(commitmentHash);
        } finally {
            lock.unlock();
        }
    }


    public Quorum getQuorum(Sha256Hash quorumHash) {
        lock.lock();
        FinalCommitment finalCommitment = null;
        try {
            for (FinalCommitment commitment : minableCommitments.values()) {
                if (commitment.quorumHash.equals(quorumHash)) {
                    finalCommitment = commitment;
                    break;
                }
            }

            if (finalCommitment == null) {
                return null;
            }
            LLMQParameters llmqParameters = LLMQParameters.fromType(LLMQParameters.LLMQType.fromValue(finalCommitment.llmqType));
            return new Quorum(llmqParameters, finalCommitment);
        } finally {
            lock.unlock();
        }
    }


    public boolean verify(Transaction coinbaseTx, SimplifiedMasternodeListDiff mnlistdiff,
                          SimplifiedQuorumList prevList, SimplifiedMasternodeList mnList) throws MasternodeListDiffException {
        lock.lock();

        try {

            if (!(coinbaseTx.getExtraPayloadObject() instanceof CoinbaseTx))
                throw new VerificationException("transaction is not a coinbase transaction");

            CoinbaseTx cbtx = (CoinbaseTx) coinbaseTx.getExtraPayloadObject();

            if (mnlistdiff.getNewQuorums().isEmpty() && mnlistdiff.getDeletedQuorums().isEmpty() &&
                    prevList != null && prevList.coinbaseTxPayload != null &&
                    cbtx.getMerkleRootQuorums().equals(prevList.coinbaseTxPayload.getMerkleRootQuorums())) {
                return true;
            }

            ArrayList<Sha256Hash> commitmentHashes = new ArrayList<>();

            for (FinalCommitment commitment : minableCommitments.values()) {
                commitmentHashes.add(commitment.getHash());
            }

            commitmentHashes.sort(Comparator.naturalOrder());

            if (!cbtx.getMerkleRootQuorums().isZero() &&
                    !commitmentHashes.isEmpty() &&
                    !cbtx.getMerkleRootQuorums().equals(MerkleRoot.calculateMerkleRoot(commitmentHashes)))
                throw new MasternodeListDiffException("MerkleRoot of quorum list does not match coinbaseTx - " + commitmentHashes.size(), true, false, false, true);

            return true;
        } finally {
            lock.unlock();
        }
    }

    public static boolean verifyMerkleRoot(List<FinalCommitment> minableCommitments, Sha256Hash merkleRootQuorums) {

        ArrayList<Sha256Hash> commitmentHashes = new ArrayList<>();

        for (FinalCommitment commitment : minableCommitments) {
            commitmentHashes.add(commitment.getHash());
        }

        commitmentHashes.sort(Comparator.naturalOrder());

        return merkleRootQuorums.isZero() ||
                commitmentHashes.isEmpty() ||
                merkleRootQuorums.equals(MerkleRoot.calculateMerkleRoot(commitmentHashes));
    }

    public Sha256Hash calculateMerkleRoot() {
        lock.lock();
        try {
            ArrayList<Sha256Hash> commitmentHashes = new ArrayList<>();

            for(FinalCommitment commitment : minableCommitments.values()) {
                commitmentHashes.add(commitment.getHash());
            }

            commitmentHashes.sort(Comparator.naturalOrder());

            return MerkleRoot.calculateMerkleRoot(commitmentHashes);
        } finally {
            lock.unlock();
        }
    }

    public void addQuorum(Quorum quorum) {
        addCommitment(quorum.getCommitment());
    }

    public void setBlock(StoredBlock block) {
        height = block.getHeight();
        blockHash = block.getHeader().getHash();
    }

    public interface ForeachQuorumCallback {
        void processQuorum(FinalCommitment finalCommitment);
    }

    public void forEachQuorum(boolean onlyValid, ForeachQuorumCallback callback) {
        for(Map.Entry<Sha256Hash, FinalCommitment> entry : minableCommitments.entrySet()) {
            if(!onlyValid || isCommitmentValid(entry.getValue())) {
                callback.processQuorum(entry.getValue());
            }
        }
    }

    public int getCount() {
        return minableCommitments.size();
    }

    public int getValidCount() {
        int count = 0;
        for (Map.Entry<Sha256Hash, FinalCommitment> p : minableCommitments.entrySet()) {
            if (isCommitmentValid(p.getValue())) {
                count++;
            }
        }
        return count;
    }

    public boolean isCommitmentValid(FinalCommitment entry) {
        return !entry.isNull();
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public long getHeight() {
        return height;
    }

    public void syncWithMasternodeList(SimplifiedMasternodeList masternodeList) {
        height = masternodeList.getHeight();
        blockHash = masternodeList.getBlockHash();
    }

    private boolean checkCommitment(FinalCommitment commitment, StoredBlock quorumBlock, MasternodeListManager manager,
                         DualBlockChain chain, boolean validateQuorums) throws BlockStoreException
    {
        if (commitment.getVersion() == 0 || commitment.getVersion() > FinalCommitment.MAX_VERSION) {
            throw new VerificationException("invalid quorum commitment version: " + commitment.getVersion());
        }

        if(quorumBlock == null)
            throw new VerificationException("invalid quorum hash: " + commitment.quorumHash);

        if (!params.getLlmqs().containsKey(LLMQParameters.LLMQType.fromValue(commitment.llmqType))) {
            throw new VerificationException("invalid LLMQType: " + commitment.llmqType);
        }

        LLMQParameters llmqParameters = params.getLlmqs().get(LLMQParameters.LLMQType.fromValue(commitment.llmqType));


        if (commitment.isNull() && !commitment.verifyNull()) {
            throw new VerificationException("invalid commitment: null value");
        }

        if (validateQuorums) {
            List<Masternode> members = manager.getAllQuorumMembers(llmqParameters.type, commitment.quorumHash);

            if (members == null) {
                //no information about this quorum because it is before we were downloading
                log.warn("masternode list is missing to verify quorum: {}", commitment.quorumHash/*, manager.getBlockHeight(commitment.quorumHash)*/);
                return false;
            }

            if (Context.get().isDebugMode()) {
                StringBuilder builder = new StringBuilder();
                for (Masternode mn : members) {
                    builder.append("\n ").append(mn.getProTxHash());
                }
                log.info("{}", builder);
            }

            if (!commitment.verify(quorumBlock, members, true)) {
                // TODO: originally, the exception was thrown here.  For now, report the error to the logs
                // throw new VerificationException("invalid quorum commitment: " + commitment);
                log.info("invalid quorum commitment: {}:{}: quorumPublicKey = {}, membersSignature = {}", commitment.quorumHash, commitment.quorumIndex, commitment.quorumPublicKey, commitment.membersSignature);
                return false;
            } else {
                log.info("valid quorum commitment: {}:{}: quorumPublicKey = {}, membersSignature = {}", commitment.quorumHash, commitment.quorumIndex, commitment.quorumPublicKey, commitment.membersSignature);
                return true;
            }
        }
        // skip validation, but return ture
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimplifiedQuorumList that = (SimplifiedQuorumList) o;

        return Objects.equals(minableCommitments, that.minableCommitments);
    }

    @Override
    public int hashCode() {
        return minableCommitments != null ? minableCommitments.hashCode() : 0;
    }
}
