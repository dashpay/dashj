package org.bitcoinj.quorums;


import org.bitcoinj.core.*;
import org.bitcoinj.evolution.SpecialTxPayload;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

public class FinalCommitmentTxPayload extends SpecialTxPayload {
    public static final int CURRENT_VERSION = 2;

    private static final Logger log = LoggerFactory.getLogger(FinalCommitmentTxPayload.class);


    long height;
    FinalCommitment commitment;

    public FinalCommitmentTxPayload(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public FinalCommitmentTxPayload(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();

        height = readUint32();
        commitment = new FinalCommitment(params, payload, cursor);
        cursor += commitment.getMessageSize();
        length = cursor - offset;
    }

    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException{
        super.bitcoinSerializeToStream(stream);
        Utils.uint32ToByteStreamLE(height, stream);
        commitment.bitcoinSerializeToStream(stream);
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        return String.format("FinalCommitmentTxPayload(version=%d, height=%d, commitment=%s",
                getVersion(), height, commitment);
    }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_QUORUM_COMMITMENT;
    }

    @Override
    public String getName() {
        return "finalCommitment";
    }

    @Override
    public JSONObject toJson() {
        JSONObject result = new JSONObject();

        result.append("version", getVersion());

        return result;
    }

    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
}
