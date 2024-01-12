package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSPublicKey;

import javax.annotation.Nullable;
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
    int type;
    int platformHTTPPort;
    KeyId platformNodeId;
    static int MESSAGE_SIZE = 151;
    //In Memory
    Sha256Hash confirmedHashWithProRegTxHash;

    public SimplifiedMasternodeListEntry(NetworkParameters params, short version) {
        super(params);
        length = MESSAGE_SIZE;
        this.version = version;
    }

    public SimplifiedMasternodeListEntry(NetworkParameters params, byte [] payload, int offset, int protocolVersion) {
        super(params, payload, offset, protocolVersion);
    }

    @Deprecated
    public SimplifiedMasternodeListEntry(NetworkParameters params,
        Sha256Hash proRegTxHash, Sha256Hash confirmedHash,
        MasternodeAddress service, KeyId keyIdVoting,
        BLSLazyPublicKey pubKeyOperator, boolean isValid) {
        super(params);
        this.version = LEGACY_BLS_VERSION;
        this.type = 0;
        this.proRegTxHash = proRegTxHash;
        this.confirmedHash = confirmedHash;
        this.service = service.duplicate();
        this.keyIdVoting = keyIdVoting;
        this.pubKeyOperator = new BLSLazyPublicKey(pubKeyOperator);
        this.isValid = isValid;
        updateConfirmedHashWithProRegTxHash();
        length = MESSAGE_SIZE;
    }

    public SimplifiedMasternodeListEntry(NetworkParameters params, short version, int type,
                                         Sha256Hash proRegTxHash, Sha256Hash confirmedHash,
                                         MasternodeAddress service, KeyId keyIdVoting,
                                         BLSLazyPublicKey pubKeyOperator, @Nullable KeyId platformNodeId,
                                         int platformHTTPPort,
                                         boolean isValid) {
        super(params);
        this.version = version;
        this.type = type;
        this.proRegTxHash = proRegTxHash;
        this.confirmedHash = confirmedHash;
        this.service = service.duplicate();
        this.keyIdVoting = keyIdVoting;
        this.pubKeyOperator = new BLSLazyPublicKey(pubKeyOperator);
        this.platformNodeId = platformNodeId;
        this.platformHTTPPort = platformHTTPPort;
        this.isValid = isValid;
        updateConfirmedHashWithProRegTxHash();
        length = MESSAGE_SIZE + (version == BASIC_BLS_VERSION ? 2 + (type == MasternodeType.HIGHPERFORMANCE.index ? 22 : 0) : 0);
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
        if (protocolVersion >= NetworkParameters.ProtocolVersion.SMNLE_VERSIONED.getBitcoinProtocolVersion()) {
            version = (short) readUint16();
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
        if (protocolVersion >= NetworkParameters.ProtocolVersion.DMN_TYPE.getBitcoinProtocolVersion()) {
            if (version >= BASIC_BLS_VERSION) {
                type = readUint16();
                if (type == MasternodeType.HIGHPERFORMANCE.index) {
                    platformHTTPPort = readUint16();
                    // not in Beta 5
                    platformNodeId = new KeyId(params, payload, cursor);
                    cursor += platformNodeId.getMessageSize();
                }
            }
        }

        updateConfirmedHashWithProRegTxHash();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        if (protocolVersion >= NetworkParameters.ProtocolVersion.SMNLE_VERSIONED.getBitcoinProtocolVersion()) {
            Utils.uint16ToByteStreamLE(version, stream);
        }
        serializeWithoutVersionToStream(stream);
    }

    private void serializeWithoutVersionToStream(OutputStream stream) throws IOException {
        stream.write(proRegTxHash.getReversedBytes());
        stream.write(confirmedHash.getReversedBytes());
        service.bitcoinSerialize(stream);
        pubKeyOperator.bitcoinSerialize(stream, version == LEGACY_BLS_VERSION);
        keyIdVoting.bitcoinSerialize(stream);
        stream.write(isValid ? 1 : 0);
        if (protocolVersion >= NetworkParameters.ProtocolVersion.DMN_TYPE.getBitcoinProtocolVersion()) {
            if (version >= BASIC_BLS_VERSION) {
                Utils.uint16ToByteStreamLE(type, stream);
                if (type == MasternodeType.HIGHPERFORMANCE.index) {
                    Utils.uint16ToByteStreamLE(platformHTTPPort, stream);
                    // TODO: not in beta 5
                    platformNodeId.bitcoinSerialize(stream);
                }
            }
        }
    }

    public Sha256Hash calculateHash() {
        return getHash();
    }

    /**
     * The hash value doesn't include the version
     * @return
     */
    @Override
    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            serializeWithoutVersionToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public String toString() {
        return String.format("SimplifiedMNListEntry{proTxHash=%s, service=%s, keyIDOperator=%s, keyIDVoting=%s, isValid=%s, platformHTTPPort=%d, platformNodeID=%s}",
            proRegTxHash.toString(), service.toString(), pubKeyOperator, keyIdVoting, isValid, platformHTTPPort, platformNodeId);
    }

    public short getVersion() {
        return version;
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

    public int getType() {
        return type;
    }

    public boolean isHPMN() {
        return type == MasternodeType.HIGHPERFORMANCE.index;
    }

    public int getPlatformHTTPPort() {
        return platformHTTPPort;
    }

    public KeyId getPlatformNodeId() {
        return platformNodeId;
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
