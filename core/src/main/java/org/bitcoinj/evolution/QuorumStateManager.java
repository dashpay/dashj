package org.bitcoinj.evolution;

import org.bitcoinj.core.Peer;
import org.bitcoinj.quorums.QuorumRotationInfo;

import java.io.FileNotFoundException;

public interface QuorumStateManager {
    void save();
    boolean isLoadedFromFile();

    void processDiffMessage(Peer peer, SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap);

    void processDiffMessage(Peer peer, QuorumRotationInfo qrinfo, boolean isLoadingBootStrap);
}
