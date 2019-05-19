package org.bitcoinj.quorums;

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
}
