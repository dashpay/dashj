package org.bitcoinj.quorums;

import org.bitcoinj.core.Sha256Hash;

public interface RecoveredSignaturesDatabase {

    boolean hasRecoveredSig(LLMQParameters.LLMQType llmqType, Sha256Hash id, Sha256Hash msgHash);
    boolean hasRecoveredSigForId(LLMQParameters.LLMQType llmqType, Sha256Hash id);
    boolean hasRecoveredSigForSession(Sha256Hash signHash);
    boolean hasRecoveredSigForHash(Sha256Hash hash);
    RecoveredSignature getRecoveredSigByHash(Sha256Hash hash);
    RecoveredSignature getRecoveredSigById(LLMQParameters.LLMQType llmqType, Sha256Hash id);
    void writeRecoveredSig(RecoveredSignature recSig);

    void cleanupOldRecoveredSignatures(long maxAge);

    // votes are removed when the recovered sig is written to the db
    boolean hasVotedOnId(LLMQParameters.LLMQType llmqType, Sha256Hash id);
    Sha256Hash getVoteForId(LLMQParameters.LLMQType llmqType, Sha256Hash id);
    void writeVoteForId(LLMQParameters.LLMQType llmqType, Sha256Hash id, Sha256Hash msgHash);
}
