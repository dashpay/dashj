package org.bitcoinj.evolution;


import org.bitcoinj.core.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class CoinbaseTx extends SpecialTxPayload {
    public static final int CURRENT_VERSION = 2;
    private static final int PAYLOAD_SIZE = 2 + 4 + 32 + 32;

    long height;
    Sha256Hash merkleRootMasternodeList;
    Sha256Hash merkleRootQuorums; //v2

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
        if(version >= 2)
            merkleRootQuorums = readHash();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
        Utils.uint32ToByteStreamLE(height, stream);
        stream.write(merkleRootMasternodeList.getReversedBytes());
        if(version >= 2)
            stream.write(merkleRootQuorums.getReversedBytes());
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
