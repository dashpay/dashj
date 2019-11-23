package org.bitcoinj.quorums;

public class QuorumNotFoundException extends Exception {
    enum Reason {
        MISSING_QUORUM,
        BLOCKCHAIN_NOT_SYNCED
    }

    Reason reason;

    public QuorumNotFoundException(Reason reason) {
        this.reason = reason;
    }
}
