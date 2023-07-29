package org.bitcoinj.evolution;


import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSSignature;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

public class CoinbaseTx extends SpecialTxPayload {
    public static final int CB_V19_VERSION = 2;
    public static final int CB_V20_VERSION = 3;
    public static final int CURRENT_VERSION = CB_V19_VERSION;
    private static final int PAYLOAD_SIZE = 2 + 4 + 32 + 32;

    long height;
    Sha256Hash merkleRootMasternodeList;
    Sha256Hash merkleRootQuorums; //v2
    long bestCLHeightDiff;
    BLSSignature bestCLSignature;
    Coin assetLockedAmount;

    public CoinbaseTx(long height, Sha256Hash merkleRootMasternodeList, Sha256Hash merkleRootQuorums) {
        super(CURRENT_VERSION);
        this.height = height;
        this.merkleRootMasternodeList = merkleRootMasternodeList;
        this.merkleRootQuorums = merkleRootQuorums;
        length = PAYLOAD_SIZE;
    }

    public CoinbaseTx(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        height = readUint32();
        merkleRootMasternodeList = readHash();
        if(version >= CB_V19_VERSION) {
            merkleRootQuorums = readHash();
            if (version >= CB_V20_VERSION) {
                bestCLHeightDiff = readVarInt();
                bestCLSignature = new BLSSignature(params, payload, cursor);
                cursor += bestCLSignature.getMessageSize();
                assetLockedAmount = Coin.valueOf(readInt64());
            }
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
        Utils.uint32ToByteStreamLE(height, stream);
        stream.write(merkleRootMasternodeList.getReversedBytes());
        if(version >= CB_V19_VERSION) {
            stream.write(merkleRootQuorums.getReversedBytes());
            if (version >= CB_V20_VERSION) {
                stream.write(new VarInt(bestCLHeightDiff).encode());
                bestCLSignature.bitcoinSerialize(stream);
                Utils.uint64ToByteStreamLE(BigInteger.valueOf(assetLockedAmount.getValue()), stream);
            }
        }
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        return String.format("CoinbaseTx(height=%d, merkleRootMNList=%s, merkleRootQuorums=%s)",
                height, merkleRootMasternodeList.toString(), merkleRootQuorums);
    }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_COINBASE;
    }

    @Override
    public String getName() {
        return "coinbaseTx";
    }

    @Override
    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.append("height", height);
        result.append("merkleRootMNList", merkleRootMasternodeList);
        result.append("merkleRootQuorums", merkleRootQuorums);
        if (version >= CB_V19_VERSION) {
            result.append("merkleRootQuorums", merkleRootQuorums);
            if (version >= CB_V20_VERSION) {
                result.append("bestCLHeightDiff", bestCLHeightDiff);
                result.append("bestCLSignature", bestCLSignature.toString());
            }
        }
        return result;
    }

    public long getHeight() { return height; }

    public Sha256Hash getMerkleRootMasternodeList() {
        return merkleRootMasternodeList;
    }

    public Sha256Hash getMerkleRootQuorums() {
        return merkleRootQuorums;
    }
}
