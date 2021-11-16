package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSPublicKey;

import java.io.IOException;
import java.io.OutputStream;

public class SimplifiedMasternodeListEntry extends Masternode {

    Sha256Hash proRegTxHash;
    Sha256Hash confirmedHash;
    MasternodeAddress service;
    BLSLazyPublicKey pubKeyOperator;
    KeyId keyIdVoting;
    boolean isValid;
    static int MESSAGE_SIZE = 151;
    //In Memory
    Sha256Hash confirmedHashWithProRegTxHash;

    public SimplifiedMasternodeListEntry(NetworkParameters params) {
        super(params);
        length = MESSAGE_SIZE;
    }

    public SimplifiedMasternodeListEntry(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public SimplifiedMasternodeListEntry(NetworkParameters params, SimplifiedMasternodeListEntry other) {
        super(params);
        proRegTxHash = other.proRegTxHash;
        confirmedHash = other.confirmedHash;
        service = other.service.duplicate();
        keyIdVoting = other.keyIdVoting;
        pubKeyOperator = new BLSLazyPublicKey(other.pubKeyOperator);
        updateConfirmedHashWithProRegTxHash();
        length = MESSAGE_SIZE;
    }

    @Override
    protected void parse() throws ProtocolException {
        proRegTxHash = readHash();
        confirmedHash = readHash();
        service = new MasternodeAddress(params, payload, cursor, 0);
        cursor += service.getMessageSize();
        pubKeyOperator = new BLSLazyPublicKey(params, payload, cursor);
        cursor += pubKeyOperator.getMessageSize();
        keyIdVoting = new KeyId(params, payload, cursor);
        cursor += keyIdVoting.getMessageSize();
        isValid = readBytes(1)[0] == 1;

        updateConfirmedHashWithProRegTxHash();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(proRegTxHash.getReversedBytes());
        stream.write(confirmedHash.getReversedBytes());
        service.bitcoinSerialize(stream);
        pubKeyOperator.bitcoinSerialize(stream);
        keyIdVoting.bitcoinSerialize(stream);
        stream.write(isValid ? 1 : 0);
    }

    public Sha256Hash calculateHash() {
        return getHash();
    }

    @Override
    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public String toString() {
        return String.format("SimplifiedMNListEntry(proRegTxHash=%s, service=%s, keyIDOperator=%s, keyIDVoting=%s, isValid="+isValid+")",
            proRegTxHash.toString(), service.toString(),
                pubKeyOperator, keyIdVoting);
    }

    public Sha256Hash getProRegTxHash() {
        return proRegTxHash;
    }

    public Sha256Hash getConfirmedHash() {
        return confirmedHash;
    }

    public MasternodeAddress getService() {
        return service;
    }

    public KeyId getKeyIdOwner() { return null; }

    public BLSPublicKey getPubKeyOperator() {
        return pubKeyOperator.getPublicKey();
    }

    public KeyId getKeyIdVoting() {
        return keyIdVoting;
    }

    public boolean isValid() {
        return isValid;
    }

    public Sha256Hash getConfirmedHashWithProRegTxHash() {
        return confirmedHashWithProRegTxHash;
    }

    void updateConfirmedHashWithProRegTxHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(64);
            bos.write(proRegTxHash.getReversedBytes());
            bos.write(confirmedHash.getReversedBytes());
            confirmedHashWithProRegTxHash = Sha256Hash.wrapReversed(Sha256Hash.hash(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
}
