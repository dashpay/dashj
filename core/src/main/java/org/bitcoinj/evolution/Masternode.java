package org.bitcoinj.evolution;

import org.bitcoinj.coinjoin.utils.ProTxToOutpoint;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Masternode extends ChildMessage {
    private static final Logger log = LoggerFactory.getLogger(Masternode.class);

    Sha256Hash proRegTxHash;
    TransactionOutPoint masternodeOutpoint;

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

    public TransactionOutPoint getCollateralOutpoint() {
        if (masternodeOutpoint == null) {
            masternodeOutpoint = ProTxToOutpoint.getMasternodeOutpoint(proRegTxHash);
        }
        if (masternodeOutpoint == null) {
            log.info("masternodeOutpoint = null for {}; {}", getService(), getProTxHash());
        }
        return masternodeOutpoint;
    }
}
