package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;

public abstract class Masternode extends ChildMessage {

    Sha256Hash proRegTxHash;

    Masternode(NetworkParameters params) {
        super(params);
    }

    Masternode(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public Sha256Hash getProRegTxHash() {
        return proRegTxHash;
    }

    public Sha256Hash getProTxHash() {
        return proRegTxHash;
    }

    public abstract Sha256Hash getConfirmedHash();

    public abstract MasternodeAddress getService();

    public abstract KeyId getKeyIdOwner();

    public abstract BLSPublicKey getPubKeyOperator();

    public abstract KeyId getKeyIdVoting();

    public abstract boolean isValid();

    public abstract Sha256Hash getConfirmedHashWithProRegTxHash();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Masternode that = (Masternode) o;

        return proRegTxHash.equals(that.proRegTxHash);
    }

    @Override
    public int hashCode() {
        return proRegTxHash.hashCode();
    }
}
