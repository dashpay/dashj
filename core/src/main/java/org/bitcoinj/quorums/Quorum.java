package org.bitcoinj.quorums;

import org.bitcoinj.core.Sha256Hash;

public class Quorum {

    LLMQParameters llmqParameters;
    FinalCommitment commitment;

    public Quorum(LLMQParameters llmqParameters, FinalCommitment commitment) {
        this.llmqParameters = llmqParameters;
        this.commitment = commitment;
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
}
