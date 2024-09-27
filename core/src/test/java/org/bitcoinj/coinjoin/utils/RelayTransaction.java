package org.bitcoinj.coinjoin.utils;

import org.bitcoinj.core.Transaction;

public interface RelayTransaction {
    void relay(Transaction tx);
}
