package org.bitcoinj.core.listeners;

import org.bitcoinj.core.SporkMessage;

public interface SporkUpdatedEventListener {
    void onSporkUpdated(SporkMessage sporkMessage);
}