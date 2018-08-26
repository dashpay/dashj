package org.bitcoinj.evolution;


import org.bitcoinj.core.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class CoinbaseTx extends SpecialTxPayload {
    public static final int CURRENT_VERSION = 1;

    long height;
    Sha256Hash merkleRootMasternodeList;

    public CoinbaseTx(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        height = readUint32();
        merkleRootMasternodeList = readHash();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
        Utils.uint32ToByteStreamLE(height, stream);
        stream.write(merkleRootMasternodeList.getReversedBytes());
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        return String.format("CoinbaseTx(height=%d, merkleRootMNList=%s)",
                height, merkleRootMasternodeList.toString());
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
        return result;
    }

    public long getHeight() { return height; }

    public Sha256Hash getMerkleRootMasternodeList() {
        return merkleRootMasternodeList;
    }
}
