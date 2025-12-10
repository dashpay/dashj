package org.bitcoinj.core.listeners;

import org.bitcoinj.core.PeerAddress;

public interface TimeoutErrorListener {
    void onTimeout(TimeoutError error, PeerAddress peer);
}
