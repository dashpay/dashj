package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;

public abstract class Masternode extends ChildMessage {

    Sha256Hash proTxHash;

    Masternode(NetworkParameters params) {
        super(params);
    }

    Masternode(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public Sha256Hash getProRegTxHash() {
        return proTxHash;
    }

    public Sha256Hash getProTxHash() {
        return proTxHash;
    }

    public abstract Sha256Hash getConfirmedHash();

    public abstract MasternodeAddress getService();

    public abstract KeyId getKeyIdOwner();

    public abstract BLSPublicKey getPubKeyOperator();

    public abstract KeyId getKeyIdVoting();

    public abstract boolean isValid();

    public abstract Sha256Hash getConfirmedHashWithProRegTxHash();

}
