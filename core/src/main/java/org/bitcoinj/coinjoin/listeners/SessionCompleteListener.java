package org.bitcoinj.coinjoin.listeners;

import org.bitcoinj.coinjoin.CoinJoinClientSession;

public interface SessionCompleteListener {
    void onSessionComplete(CoinJoinClientSession session);
}
