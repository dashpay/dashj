package org.bitcoinj.evolution;

import org.bitcoinj.core.*;

import java.io.IOException;

public abstract class SubTxForExistingUser extends SpecialTxPayload {

    Sha256Hash regTxId;
    Sha256Hash hashPrevSubTx;
    Coin creditFee;
    MasternodeSignature signature;


    protected SubTxForExistingUser(int version, Sha256Hash regTxId, Sha256Hash hashPrevSubTx, Coin creditFee) {
        super(version);
        this.regTxId = Sha256Hash.wrap(regTxId.getBytes());
        this.hashPrevSubTx = Sha256Hash.wrap(hashPrevSubTx.getBytes());
        this.creditFee = Coin.valueOf(creditFee.value);
        this.signature = MasternodeSignature.createEmpty();
    }

    protected SubTxForExistingUser(int version, Sha256Hash regTxId, Sha256Hash hashPrevSubTx, Coin creditFee, MasternodeSignature signature) {
        super(version);
        this.regTxId = Sha256Hash.wrap(regTxId.getBytes());
        this.hashPrevSubTx = Sha256Hash.wrap(hashPrevSubTx.getBytes());
        this.creditFee = Coin.valueOf(creditFee.value);
        this.signature = signature;
    }

    protected SubTxForExistingUser(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public Sha256Hash getSignHash() {
        if(signature.isEmpty())
            return calculateSignatureHash();
        else {
            SubTxForExistingUser subtx = duplicate();
            return subtx.calculateSignatureHash();
        }
    }

    public Sha256Hash calculateSignatureHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }


    void sign(ECKey key) {
        Sha256Hash hash = getSignHash();
        signature = HashSigner.signHash(hash, key);
        unCache();
    }

    abstract SubTxForExistingUser duplicate();

    public Sha256Hash getRegTxId() {
        return regTxId;
    }

    public Coin getCreditFee() {
        return creditFee;
    }

    public Sha256Hash getHashPrevSubTx() {
        return hashPrevSubTx;
    }

    public MasternodeSignature getSignature() {
        return signature;
    }
}
