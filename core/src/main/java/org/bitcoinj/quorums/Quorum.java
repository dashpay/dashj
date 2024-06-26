package org.bitcoinj.quorums;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSPublicKey;

public class Quorum {

    private LLMQParameters llmqParameters;
    private FinalCommitment commitment;

    public Quorum(FinalCommitment commitment) {
        this.commitment = commitment;
        this.llmqParameters = LLMQParameters.fromType(commitment.llmqType);
    }

    public Quorum(LLMQParameters llmqParameters, FinalCommitment commitment) {
        this.llmqParameters = llmqParameters;
        this.commitment = commitment;
    }

    public Quorum(NetworkParameters params, LLMQParameters llmqParameters, Sha256Hash quorumHash, BLSPublicKey publicKey) {
        this.llmqParameters = llmqParameters;
        this.commitment = new FinalCommitment(params, llmqParameters, quorumHash);
        this.commitment.quorumPublicKey = new BLSLazyPublicKey(publicKey);
    }

    @Override
    public String toString() {
        return String.format("Quorum(type=%d, quorumHash=%s, qfc=%s)", llmqParameters.type.value, commitment.quorumHash, commitment);
    }

    public FinalCommitment getCommitment() {
        return commitment;
    }

    public LLMQParameters getLlmqParameters() {
        return llmqParameters;
    }

    public Sha256Hash getQuorumHash() {
        return commitment.quorumHash;
    }

    public int getQuorumIndex() {
        return commitment.quorumIndex;
    }

    public boolean isVerified() {
        return commitment.isVerified();
    }
}
