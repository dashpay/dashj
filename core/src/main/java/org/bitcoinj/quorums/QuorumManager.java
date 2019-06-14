package org.bitcoinj.quorums;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public abstract class QuorumManager {
    Context context;
    AbstractBlockChain blockChain;

    private static final Logger log = LoggerFactory.getLogger(QuorumManager.class);
    protected ReentrantLock lock = Threading.lock("QuorumManager");

    public QuorumManager(Context context) {
        this.context = context;
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
    }

    // all these methods will lock cs_main for a short period of time
    public Quorum getQuorum(LLMQParameters.LLMQType llmqType, Sha256Hash quorumHash) {
        return null;
    }
    public Quorum getNewestQuorum(LLMQParameters.LLMQType llmqType) {
        return null;
    }
    public ArrayList<Quorum> scanQuorums(LLMQParameters.LLMQType llmqType, long maxCount) {
        return null;
    }

    // this one is cs_main-free
    public ArrayList<Quorum> scanQuorums(LLMQParameters.LLMQType llmqType, StoredBlock storedBlocktart, long maxCount) {
        return null;
    }

    // all private methods here are cs_main-free
    void ensureQuorumConnections(LLMQParameters.LLMQType llmqType, StoredBlock newBlock) {

    }

    boolean buildQuorumFromCommitment(FinalCommitment qc, StoredBlock pindexQuorum, Sha256Hash minedBlockHash, Quorum quorum) {
        return false;
    }
    boolean buildQuorumContributions(FinalCommitment fqc, Quorum quorum) {
        return false;
    }

    Quorum getQuorum(LLMQParameters.LLMQType llmqType, StoredBlock block) {
        return null;
    }

    boolean isQuorumActive(LLMQParameters.LLMQType llmqType, Sha256Hash quorumHash) {
        return false;
    }

    public void close() { }
}
