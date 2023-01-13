package org.bitcoinj.evolution;


import org.bitcoinj.core.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class CoinbaseTx extends SpecialTxPayload {
    public static final int MNLIST_VERSION = 1;
    public static final int LLMQ_VERSION = 2;
    public static final int ASSETLOCK_VERSION = 3;
    public static final int CURRENT_VERSION = 3;

    long height;
    Sha256Hash merkleRootMasternodeList;
    Sha256Hash merkleRootQuorums; // v2
    Coin assetLockedAmount; // v3

    public CoinbaseTx(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        height = readUint32();
        merkleRootMasternodeList = readHash();
        if (version >= LLMQ_VERSION)
            merkleRootQuorums = readHash();
        if (version >= ASSETLOCK_VERSION)
            assetLockedAmount = Coin.valueOf(readInt64());
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
        Utils.uint32ToByteStreamLE(height, stream);
        stream.write(merkleRootMasternodeList.getReversedBytes());
        if (version >= LLMQ_VERSION)
            stream.write(merkleRootQuorums.getReversedBytes());
        if (version >= ASSETLOCK_VERSION)
            Utils.int64ToByteStreamLE(assetLockedAmount.getValue(), stream);
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        return String.format("CoinbaseTx(height=%d, merkleRootMNList=%s, merkleRootQuorums=%s, assetLockedAmount=%s)",
                height, merkleRootMasternodeList, merkleRootQuorums, assetLockedAmount.toPlainString());
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
        result.append("assetLockedAmount", assetLockedAmount);
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
