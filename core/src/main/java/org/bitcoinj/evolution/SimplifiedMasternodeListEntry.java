package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSPublicKey;

import com.google.common.net.InetAddresses;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;

import static com.google.common.base.Preconditions.checkArgument;

public class SimplifiedMasternodeListEntry extends Masternode {
    public static final short LEGACY_BLS_VERSION = 1;
    public static final short BASIC_BLS_VERSION = 2;
    /** from this version on, the address is an {@link ExtNetInfo} and Evo nodes have no inline platformHTTPPort */
    public static final short EXT_ADDR_VERSION = 3;
    short version;
    Sha256Hash confirmedHash;
    // the primary Core P2P address; for version 3+ it is derived from netInfo
    MasternodeAddress service;
    // only for version 3+
    @Nullable ExtNetInfo netInfo;
    BLSLazyPublicKey pubKeyOperator;
    KeyId keyIdVoting;
    boolean isValid;
    int type;
    int platformHTTPPort;
    KeyId platformNodeId;
    static final int MESSAGE_SIZE = 151;
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

    /**
     * Creates a version 3+ entry, which carries an {@link ExtNetInfo} instead of a single service address.
     * The Evo platform HTTP port comes from the {@link NetInfoPurpose#PLATFORM_HTTPS} entry.
     */
    public SimplifiedMasternodeListEntry(NetworkParameters params, short version, int type,
                                         Sha256Hash proRegTxHash, Sha256Hash confirmedHash,
                                         ExtNetInfo netInfo, KeyId keyIdVoting,
                                         BLSLazyPublicKey pubKeyOperator, @Nullable KeyId platformNodeId,
                                         boolean isValid) {
        super(params);
        checkArgument(version >= EXT_ADDR_VERSION, "ExtNetInfo requires version %s or higher", EXT_ADDR_VERSION);
        // a version 3 entry only exists on networks that know about versioned, typed entries
        this.protocolVersion = params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT);
        this.version = version;
        this.type = type;
        this.proRegTxHash = proRegTxHash;
        this.confirmedHash = confirmedHash;
        setNetInfo(netInfo.duplicate());
        this.keyIdVoting = keyIdVoting;
        this.pubKeyOperator = new BLSLazyPublicKey(pubKeyOperator);
        this.platformNodeId = platformNodeId;
        this.isValid = isValid;
        updateConfirmedHashWithProRegTxHash();
        length = calculateMessageSize();
    }

    public SimplifiedMasternodeListEntry(NetworkParameters params, SimplifiedMasternodeListEntry other) {
        super(params);
        version = other.version;
        type = other.type;
        proRegTxHash = other.proRegTxHash;
        confirmedHash = other.confirmedHash;
        if (other.netInfo != null) {
            setNetInfo(other.netInfo.duplicate());
        } else {
            service = other.service.duplicate();
            platformHTTPPort = other.platformHTTPPort;
        }
        keyIdVoting = other.keyIdVoting;
        pubKeyOperator = new BLSLazyPublicKey(other.pubKeyOperator);
        platformNodeId = other.platformNodeId;
        isValid = other.isValid;
        protocolVersion = other.protocolVersion;
        updateConfirmedHashWithProRegTxHash();
        length = calculateMessageSize();
    }

    private void setNetInfo(ExtNetInfo netInfo) {
        this.netInfo = netInfo;
        MasternodeAddress primary = netInfo.getPrimaryService();
        // like Core's GetPrimary(), fall back to an empty address rather than null
        service = primary != null ? primary : new MasternodeAddress(EMPTY_ADDRESS, 0);
        platformHTTPPort = netInfo.getPlatformHTTPPort();
    }

    private static final InetAddress EMPTY_ADDRESS = InetAddresses.forString("0.0.0.0");

    private int calculateMessageSize() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(MESSAGE_SIZE + 32);
            bitcoinSerializeToStream(bos);
            return bos.size();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
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
        if (version >= EXT_ADDR_VERSION) {
            ExtNetInfo extNetInfo = new ExtNetInfo(params, payload, cursor);
            cursor += extNetInfo.getMessageSize();
            setNetInfo(extNetInfo);
        } else {
            service = new MasternodeAddress(params, payload, cursor, 0);
            cursor += service.getMessageSize();
        }
        pubKeyOperator = new BLSLazyPublicKey(params, payload, cursor, version == LEGACY_BLS_VERSION);
        cursor += pubKeyOperator.getMessageSize();
        keyIdVoting = new KeyId(params, payload, cursor);
        cursor += keyIdVoting.getMessageSize();
        isValid = readBytes(1)[0] == 1;
        if (protocolVersion >= NetworkParameters.ProtocolVersion.DMN_TYPE.getBitcoinProtocolVersion()) {
            if (version >= BASIC_BLS_VERSION) {
                type = readUint16();
                if (type == MasternodeType.HIGHPERFORMANCE.index) {
                    // version 3 moved this port into netInfo
                    if (version < EXT_ADDR_VERSION) {
                        platformHTTPPort = readUint16();
                    }
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
        if (version >= EXT_ADDR_VERSION) {
            netInfo.bitcoinSerialize(stream);
        } else {
            service.bitcoinSerialize(stream);
        }
        pubKeyOperator.bitcoinSerialize(stream, version == LEGACY_BLS_VERSION);
        keyIdVoting.bitcoinSerialize(stream);
        stream.write(isValid ? 1 : 0);
        if (protocolVersion >= NetworkParameters.ProtocolVersion.DMN_TYPE.getBitcoinProtocolVersion()) {
            if (version >= BASIC_BLS_VERSION) {
                Utils.uint16ToByteStreamLE(type, stream);
                if (type == MasternodeType.HIGHPERFORMANCE.index) {
                    if (version < EXT_ADDR_VERSION) {
                        Utils.uint16ToByteStreamLE(platformHTTPPort, stream);
                    }
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
        return String.format("SimplifiedMNListEntry{version=%d, proTxHash=%s, service=%s, keyIDOperator=%s, keyIDVoting=%s, isValid=%s, platformHTTPPort=%d, platformNodeID=%s%s}",
            version, proRegTxHash.toString(), service.toString(), pubKeyOperator, keyIdVoting, isValid, platformHTTPPort, platformNodeId,
            netInfo != null ? ", netInfo=" + netInfo : "");
    }

    public short getVersion() {
        return version;
    }

    public Sha256Hash getConfirmedHash() {
        return confirmedHash;
    }

    /**
     * The primary Core P2P address. For version 3+ entries this is the first {@link NetInfoPurpose#CORE_P2P}
     * address in {@link #getNetInfo()}, or 0.0.0.0:0 if that is not an IPv4 or IPv6 address.
     */
    public MasternodeAddress getService() {
        return service;
    }

    /** all the addresses of a version 3+ entry, otherwise null */
    @Nullable
    public ExtNetInfo getNetInfo() {
        return netInfo;
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
