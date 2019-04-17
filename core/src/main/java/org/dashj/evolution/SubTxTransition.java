package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class SubTxTransition extends SubTxForExistingUser {

    static final int CURRENT_VERSION = 1;

    public static final Coin EVO_TS_MIN_FEE = Coin.valueOf(1000); // TODO find good min fee
    public static final Coin EVO_TS_MAX_FEE = EVO_TS_MIN_FEE.multiply(10); // TODO find good max fee


    Sha256Hash hashSTPacket;

    public SubTxTransition(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    protected SubTxTransition(int version, Sha256Hash regTxId, Sha256Hash hashPrevSubTx, Coin creditFee, MasternodeSignature signature, Sha256Hash hashSTPacket) {
        super(version, regTxId, hashPrevSubTx, creditFee, signature);
        this.hashSTPacket = hashSTPacket;
    }

    SubTxTransition duplicate() {
        SubTxTransition copy = new SubTxTransition(version, regTxId, hashPrevSubTx, creditFee, signature, hashSTPacket);
        return copy;
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        regTxId = readHash();
        hashPrevSubTx = readHash();
        creditFee = Coin.valueOf(readInt64());
        hashSTPacket = readHash();
        signature = new MasternodeSignature(params, payload, cursor);
        cursor += signature.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
        stream.write(regTxId.getReversedBytes());
        stream.write(hashPrevSubTx.getReversedBytes());
        Utils.int64ToByteStreamLE(creditFee.getValue(), stream);
        stream.write(hashSTPacket.getReversedBytes());
        signature.bitcoinSerialize(stream);
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.append("txType", getName());
        result.append("version", version);
        result.append("regTxId", regTxId);
        result.append("hashPrevSubTx", hashPrevSubTx);
        result.append("creditFee", creditFee);
        result.append("hashSTPacket", hashSTPacket);

        return result;
    }

    @Override
    public int getCurrentVersion() { return CURRENT_VERSION; }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_SUBTX_TRANSITION;
    }

    @Override
    public String getName() {
        return "subTxTransition";
    }
}
