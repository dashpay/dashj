package org.bitcoinj.evolution;

public class MasternodeListDiffException extends Exception {
    private final boolean requireReset;
    private final boolean findNewPeer;

    private final boolean sameHeight;
    private final boolean merkleRootMismatch;
    public MasternodeListDiffException(String message, boolean requireReset, boolean findNewPeer, boolean sameHeight, boolean merkleRootMismatch) {
        super(message);
        this.requireReset = requireReset;
        this.findNewPeer = findNewPeer;
        this.sameHeight = sameHeight;
        this.merkleRootMismatch = merkleRootMismatch;
    }

    public boolean isRequiringReset() {
        return requireReset;
    }

    public boolean isRequiringNewPeer() {
        return findNewPeer;
    }

    public boolean hasMerkleRootMismatch() {
        return merkleRootMismatch;
    }

    public boolean isSameHeight() {
        return sameHeight;
    }
}
