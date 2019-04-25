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
        return String.format("Quorum(type=%d, qc=%s)", llmqParameters.type.value, commitment.quorumHash);
    }
}
