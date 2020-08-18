package org.bitcoinj.core.listeners;

import org.bitcoinj.core.Peer;

public interface PreBlocksDownloadListener {
    void onPreBlocksDownload(Peer peer);
}
