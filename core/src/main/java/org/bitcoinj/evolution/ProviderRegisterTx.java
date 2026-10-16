package org.bitcoinj.evolution;


import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ProviderRegisterTx extends SpecialTxPayload {
    public static final int CURRENT_VERSION = 1;
    public static final int LEGACY_BLS_VERSION = 1;
    public static final int BASIC_BLS_VERSION = 2;
    /** addresses are an {@link ExtNetInfo} and the payout is a list of {@link MasternodePayoutShare} */
    public static final int EXT_ADDR_VERSION = 3;
    public static final int COMPACT_SIGNATURE_SIZE = 65;

    public static final int MESSAGE_SIZE = 274;
    public static final int MESSAGE_SIZE_WITHOUT_SIGNATURE = 209;
    public static final String SEPARATOR_CHAR = "|";


    int type; //short
    int mode;  //short
    TransactionOutPoint collateralOutpoint;
    // the primary Core P2P address; for version 3+ it is derived from netInfo
    MasternodeAddress address;
    // version 3+
    ExtNetInfo netInfo;
    KeyId platformNodeID;
    int platformP2PPort;
    int platformHTTPPort;
    KeyId keyIDOwner;
    BLSPublicKey pubkeyOperator;
    KeyId keyIDVoting;
    int operatorReward;
    // before version 3
    Script scriptPayout;
    // version 3+, not initialized here, because parse() runs from the superclass constructor before field initializers
    List<MasternodePayoutShare> payouts;
    List<CollateralShare> shares;
    List<byte[]> joinSigs;
    long earlyPeriodBlocks;
    long earlyPenalty;
    Sha256Hash inputsHash;  //replay protection
    MasternodeSignature signature;

    public ProviderRegisterTx(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public ProviderRegisterTx(NetworkParameters params, int version, int type, int mode, TransactionOutPoint collateralOutpoint,
            MasternodeAddress address, KeyId keyIDOwner, BLSPublicKey pubkeyOperator,
            KeyId keyIDVoting, int operatorReward,
            Script scriptPayout, Sha256Hash inputsHash) {
        super(params, version);
        this.type = type;
        this.mode = mode;
        this.collateralOutpoint = collateralOutpoint;
        this.address = address.duplicate();
        this.keyIDVoting = keyIDVoting;
        this.keyIDOwner = keyIDOwner;
        this.pubkeyOperator = pubkeyOperator;
        this.operatorReward = operatorReward;
        this.scriptPayout = scriptPayout;
        this.inputsHash = inputsHash;
        length = MESSAGE_SIZE_WITHOUT_SIGNATURE;
    }

    public ProviderRegisterTx(NetworkParameters params, int version, int type, int mode, TransactionOutPoint collateralOutpoint,
                              MasternodeAddress address, KeyId keyIDOwner, BLSPublicKey pubkeyOperator,
                              KeyId keyIDVoting, int operatorReward,
                              Script scriptPayout, Sha256Hash inputsHash, MasternodeSignature signature) {
        this(params, version, type, mode, collateralOutpoint, address, keyIDOwner, pubkeyOperator, keyIDVoting, operatorReward, scriptPayout, inputsHash);
        this.signature = signature;
        length = MESSAGE_SIZE;
    }

    public ProviderRegisterTx(NetworkParameters params, int version, int type, int mode, TransactionOutPoint collateralOutpoint,
                              MasternodeAddress address, KeyId keyIDOwner, BLSPublicKey pubkeyOperator,
                              KeyId keyIDVoting, int operatorReward,
                              Script scriptPayout, Sha256Hash inputsHash, ECKey signingKey) {
        this(params, version, type, mode, collateralOutpoint, address, keyIDOwner, pubkeyOperator, keyIDVoting, operatorReward, scriptPayout, inputsHash);
        sign(signingKey);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        type = readUint16();
        mode = readUint16();
        collateralOutpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += collateralOutpoint.getMessageSize();
        if (version >= EXT_ADDR_VERSION) {
            netInfo = new ExtNetInfo(params, payload, cursor);
            cursor += netInfo.getMessageSize();
            address = primaryOrEmpty(netInfo);
        } else {
            address = new MasternodeAddress(params, payload, cursor, params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT));
            cursor += address.getMessageSize();
        }

        keyIDOwner = new KeyId(params, payload, cursor);
        cursor += keyIDOwner.getMessageSize();
        pubkeyOperator = new BLSPublicKey(params, payload, cursor, version == LEGACY_BLS_VERSION);
        cursor += pubkeyOperator.getMessageSize();
        keyIDVoting = new KeyId(params, payload, cursor);
        cursor += keyIDVoting.getMessageSize();

        operatorReward = readUint16();
        if (version >= EXT_ADDR_VERSION) {
            payouts = readPayouts();
            int sharesCount = readBytes(1)[0] & 0xff;
            List<CollateralShare> shareList = new ArrayList<>(sharesCount);
            for (int i = 0; i < sharesCount; i++) {
                Coin amount = Coin.valueOf(readInt64());
                Script scriptRefund = new Script(readByteArray());
                Script scriptReward = new Script(readByteArray());
                KeyId keyIdOwner = new KeyId(params, payload, cursor);
                cursor += keyIdOwner.getMessageSize();
                shareList.add(new CollateralShare(amount, scriptRefund, scriptReward, keyIdOwner));
            }
            shares = Collections.unmodifiableList(shareList);
            // one raw compact signature per share, without a length prefix
            List<byte[]> sigList = new ArrayList<>(sharesCount);
            for (int i = 0; i < sharesCount; i++) {
                sigList.add(readBytes(COMPACT_SIGNATURE_SIZE));
            }
            joinSigs = Collections.unmodifiableList(sigList);
            earlyPeriodBlocks = readUint32();
            earlyPenalty = readInt64();
            scriptPayout = payouts.isEmpty() ? new Script(new byte[0]) : payouts.get(0).getScriptPayout();
        } else {
            scriptPayout = new Script(readByteArray());
        }
        inputsHash = readHash();
        if (version >= BASIC_BLS_VERSION && type == MasternodeType.HIGHPERFORMANCE.index) {
            platformNodeID = new KeyId(params, payload, cursor);
            cursor += platformNodeID.getMessageSize();
            if (version >= EXT_ADDR_VERSION) {
                platformP2PPort = netInfo.getPlatformP2PPort();
                platformHTTPPort = netInfo.getPlatformHTTPPort();
            } else {
                platformP2PPort = readUint16();
                platformHTTPPort = readUint16();
            }
        }
        signature = new MasternodeSignature(params, payload, cursor);
        cursor += signature.getMessageSize();

        length = cursor - offset;
    }

    protected void bitcoinSerializeWithoutSignature(OutputStream stream) throws IOException{
        super.bitcoinSerializeToStream(stream);
        Utils.uint16ToByteStreamLE(type, stream);
        Utils.uint16ToByteStreamLE(mode, stream);
        collateralOutpoint.bitcoinSerialize(stream);
        if (version >= EXT_ADDR_VERSION) {
            netInfo.bitcoinSerialize(stream);
        } else {
            address.bitcoinSerialize(stream);
        }

        keyIDOwner.bitcoinSerialize(stream);
        pubkeyOperator.bitcoinSerialize(stream, version == LEGACY_BLS_VERSION);
        keyIDVoting.bitcoinSerialize(stream);

        Utils.uint16ToByteStreamLE(operatorReward, stream);
        if (version >= EXT_ADDR_VERSION) {
            writePayouts(payouts, stream);
            stream.write(shares.size());
            for (CollateralShare share : shares) {
                share.serialize(stream);
            }
            for (byte[] joinSig : joinSigs) {
                stream.write(joinSig);
            }
            Utils.uint32ToByteStreamLE(earlyPeriodBlocks, stream);
            Utils.int64ToByteStreamLE(earlyPenalty, stream);
        } else {
            Utils.bytesToByteStream(scriptPayout.getProgram(), stream);
        }
        stream.write(inputsHash.getReversedBytes());

        if (version >= BASIC_BLS_VERSION && type == MasternodeType.HIGHPERFORMANCE.index) {
            platformNodeID.bitcoinSerialize(stream);
            if (version < EXT_ADDR_VERSION) {
                Utils.uint16ToByteStreamLE(platformP2PPort, stream);
                Utils.uint16ToByteStreamLE(platformHTTPPort, stream);
            }
        }
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        bitcoinSerializeWithoutSignature(stream);
        if(signature != null)
            signature.bitcoinSerialize(stream);
        else MasternodeSignature.createEmpty().bitcoinSerialize(stream);
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        String payee = "unknown";
        try {
            payee = scriptPayout.getToAddress(params).toString();
        } catch (AddressFormatException x) {
            //swallow
        } catch (ScriptException x) {
            //swallow
        }
        if (version >= EXT_ADDR_VERSION) {
            payee = MasternodePayoutShare.toString(params, payouts);
        }
        return String.format("ProRegTx(version=%d, collateralOutpoint=%s, address=%s, operatorReward=%f, ownerAddress=%s, pubKeyOperator=%s, votingAddress=%s, scriptPayout=%s)",
                version, collateralOutpoint.toStringShort(), netInfo != null ? netInfo : address, (double)operatorReward / 100,
                Address.fromPubKeyHash(params, keyIDOwner.getBytes()), pubkeyOperator,
                Address.fromPubKeyHash(params, keyIDVoting.getBytes()), payee);

    }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_PROVIDER_REGISTER;
    }

    @Override
    public String getName() {
        return "providerRegisterTx";
    }

    @Override
    public JSONObject toJson() {
        JSONObject result = new JSONObject();

        result.append("version", version);
        result.append("collateralHash", collateralOutpoint.getHash());
        result.append("collateralIndex", (int)collateralOutpoint.getIndex());
        result.append("service", address.toString());
        result.append("ownerAddress", Address.fromPubKeyHash(params, keyIDOwner.getBytes()));
        result.append("votingAddress", Address.fromPubKeyHash(params, keyIDVoting.getBytes()));

        try {
            Address destination = scriptPayout.getToAddress(params);
            result.append("payoutAddress", destination);
        } catch (AddressFormatException x) {
            //swallow
        }
        result.append("pubKeyOperator", pubkeyOperator);
        result.append("operatorReward", (double)operatorReward / 100);

        result.append("inputsHash", inputsHash);

        return result;
    }

    String makeSignString()
    {
        StringBuilder s = new StringBuilder();

        // We only include the important stuff in the string form...

        String strPayout;
        if (version >= EXT_ADDR_VERSION) {
            strPayout = MasternodePayoutShare.toString(params, payouts);
        } else {
            try {
                strPayout = scriptPayout.getToAddress(params).toString();
            } catch (ScriptException x) {
                strPayout = Utils.HEX.encode(scriptPayout.getProgram());
            }
        }

        s.append(strPayout).append(SEPARATOR_CHAR);
        s.append(String.format(Locale.US, "%d", operatorReward)).append(SEPARATOR_CHAR);
        s.append(Address.fromPubKeyHash(params, keyIDOwner.getBytes())).append(SEPARATOR_CHAR);
        s.append(Address.fromPubKeyHash(params, keyIDVoting.getBytes())).append(SEPARATOR_CHAR);

        // ... and also the full hash of the payload as a protection against malleability and replays
        s.append(Utils.HEX.encode(getHash().getBytes()));

        return s.toString();
    }

    @Override
    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bitcoinSerializeWithoutSignature(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x.getMessage());
        }
    }

    void sign(ECKey signingKey) {
        signature = MessageSigner.signMessage(makeSignString(), signingKey);
        length = MESSAGE_SIZE;
        unCache();
    }

    /** the primary Core P2P address, which for version 3+ is the first {@link NetInfoPurpose#CORE_P2P} entry */
    public MasternodeAddress getAddress() {
        return address;
    }

    /** all the addresses of a version 3+ registration, otherwise null */
    public ExtNetInfo getNetInfo() {
        return netInfo;
    }

    public int getMasternodeType() {
        return type;
    }

    public int getOperatorReward() {
        return operatorReward;
    }

    /** the owner payout script; for version 3+ the first of {@link #getPayouts()} */
    public Script getScriptPayout() {
        return scriptPayout;
    }

    /** the owner payouts of a version 3+ registration, otherwise a list holding the single scriptPayout */
    public List<MasternodePayoutShare> getPayouts() {
        if (version >= EXT_ADDR_VERSION) {
            return payouts;
        }
        return Collections.singletonList(new MasternodePayoutShare(scriptPayout, MasternodePayoutShare.MAX_REWARD));
    }

    /** the collateral shares of a shared masternode registration; empty if it is not shared */
    public List<CollateralShare> getShares() {
        return shares != null ? shares : Collections.emptyList();
    }

    public boolean isShared() {
        return !getShares().isEmpty();
    }

    public long getEarlyPeriodBlocks() {
        return earlyPeriodBlocks;
    }

    public Coin getEarlyPenalty() {
        return Coin.valueOf(earlyPenalty);
    }

    public int getPlatformP2PPort() {
        return platformP2PPort;
    }

    public int getPlatformHTTPPort() {
        return platformHTTPPort;
    }

    public TransactionOutPoint getCollateralOutpoint() {
        return collateralOutpoint;
    }

    public Sha256Hash getInputsHash() {
        return inputsHash;
    }

    public KeyId getKeyIDOwner() {
        return keyIDOwner;
    }
    public Address getOwnerAddress() {
        return Address.fromPubKeyHash(params, keyIDOwner.getBytes());
    }

    public KeyId getKeyIDVoting() {
        return keyIDVoting;
    }
    public Address getVotingAddress() {
        return Address.fromPubKeyHash(params, keyIDVoting.getBytes());
    }

    public BLSPublicKey getPubkeyOperator() {
        return pubkeyOperator;
    }

    public KeyId getPlatformNodeID() {
        return platformNodeID;
    }

    public String getPayloadCollateralString() {
        return String.format("%s|%d|%s|%s|%s", scriptPayout.getToAddress(params), operatorReward, getOwnerAddress(), getVotingAddress(), getHash());
    }

    public MasternodeSignature getSignature() {
        return signature;
    }
}
