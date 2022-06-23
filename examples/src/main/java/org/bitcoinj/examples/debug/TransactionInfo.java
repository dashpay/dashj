package org.bitcoinj.examples.debug;

import org.bitcoinj.core.Transaction;

public class TransactionInfo {
    long timeReceived;
    int blockRecieved;
    Transaction tx;

    TransactionInfo(long timeReceived, int blockRecieved, Transaction tx) {
        this.timeReceived = timeReceived;
        this.blockRecieved = blockRecieved;
        this.tx = tx;
    }

    @Override
    public int hashCode() {
        return tx.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransactionInfo that = (TransactionInfo) o;

        if (timeReceived != that.timeReceived) return false;
        return tx != null ? tx.equals(that.tx) : that.tx == null;
    }
}
