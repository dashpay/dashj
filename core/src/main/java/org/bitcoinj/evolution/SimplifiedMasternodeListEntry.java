package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSPublicKey;

import java.io.IOException;
import java.io.OutputStream;

public class SimplifiedMasternodeListEntry extends Masternode {
    public static final short LEGACY_BLS_VERSION = 1;
    public static final short BASIC_BLS_VERSION = 2;
    short version;
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

    public SimplifiedMasternodeListEntry(NetworkParameters params, byte [] payload, int offset, int protocolVersion) {
        super(params, payload, offset, protocolVersion);
    }

    public SimplifiedMasternodeListEntry(NetworkParameters params,
        Sha256Hash proRegTxHash, Sha256Hash confirmedHash,
        MasternodeAddress service, KeyId keyIdVoting,
        BLSLazyPublicKey pubKeyOperator, boolean isValid) {
        super(params);
        this.proRegTxHash = proRegTxHash;
        this.confirmedHash = confirmedHash;
        this.service = service.duplicate();
        this.keyIdVoting = keyIdVoting;
        this.pubKeyOperator = new BLSLazyPublicKey(pubKeyOperator);
        this.isValid = isValid;
        updateConfirmedHashWithProRegTxHash();
        length = MESSAGE_SIZE;
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
        if (protocolVersion >= params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_BASIC)) {
            version = BASIC_BLS_VERSION;
        } else {
            version = LEGACY_BLS_VERSION;
        }
        proRegTxHash = readHash();
        confirmedHash = readHash();
        service = new MasternodeAddress(params, payload, cursor, 0);
        cursor += service.getMessageSize();
        pubKeyOperator = new BLSLazyPublicKey(params, payload, cursor, version == LEGACY_BLS_VERSION);
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
        return String.format("SimplifiedMNListEntry{proTxHash=%s, service=%s, keyIDOperator=%s, keyIDVoting=%s, isValid="+isValid+"}",
            proRegTxHash.toString(), service.toString(),
                pubKeyOperator, keyIdVoting);
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
