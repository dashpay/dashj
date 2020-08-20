package org.bitcoinj.evolution.listeners;

import org.bitcoinj.evolution.SimplifiedMasternodeListDiff;

public interface MasternodeListDownloadedListener {
    void onMasterNodeListDiffDownloaded(SimplifiedMasternodeListDiff mnlistdiff);
}
