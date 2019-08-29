package org.dashj.evolution;

public class MasternodeListDiffException extends Exception {
    boolean requireReset;
    boolean findNewPeer;
    public MasternodeListDiffException(String message, boolean requireReset, boolean findNewPeer) {
        super(message);
        this.requireReset = requireReset;
        this.findNewPeer = findNewPeer;
    }

    public boolean isRequiringReset() {
        return requireReset;
    }

    public boolean isRequiringNewPeer() {
        return findNewPeer;
    }
}
