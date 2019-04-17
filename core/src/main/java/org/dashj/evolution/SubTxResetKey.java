package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class SubTxResetKey extends SubTxForExistingUser {

    static final int CURRENT_VERSION = 1;
    static final int MESSAGE_SIZE_WITHOUT_SIG = 2 + 32 + 32 + 8 + 20;
    static final int MESSAGE_SIZE_WITH_EMPTY_SIG = MESSAGE_SIZE_WITHOUT_SIG + 1;

    KeyId newPubKeyId;

    public SubTxResetKey(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public SubTxResetKey(int version, Sha256Hash regTxId, Sha256Hash hashPrevSubTx, Coin creditFee, KeyId newPubKeyId, ECKey key) {
        super(version, regTxId, hashPrevSubTx, creditFee);
        this.newPubKeyId = newPubKeyId;
        length = MESSAGE_SIZE_WITH_EMPTY_SIG;
        sign(key);
    }

    protected SubTxResetKey(int version, Sha256Hash regTxId, Sha256Hash hashPrevSubTx, Coin creditFee, KeyId newPubKeyId) {
        super(version, regTxId, hashPrevSubTx, creditFee);
        this.newPubKeyId = newPubKeyId;
    }

    protected SubTxResetKey(int version, Sha256Hash regTxId, Sha256Hash hashPrevSubTx, Coin creditFee, MasternodeSignature signature, KeyId newPubKeyId) {
        super(version, regTxId, hashPrevSubTx, creditFee, signature);
        this.newPubKeyId = newPubKeyId;
        length = MESSAGE_SIZE_WITHOUT_SIG + signature.getMessageSize();
    }

    SubTxResetKey duplicate() {
        SubTxResetKey copy = new SubTxResetKey(version, regTxId, hashPrevSubTx, creditFee, signature, newPubKeyId);
        return copy;
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        regTxId = readHash();
        hashPrevSubTx = readHash();
        creditFee = Coin.valueOf(readInt64());
        newPubKeyId = new KeyId(params, payload, cursor);
        cursor += newPubKeyId.getMessageSize();
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
        newPubKeyId.bitcoinSerialize(stream);
        signature.bitcoinSerialize(stream);
    }

    @Override
    protected void unCache() {
        super.unCache();
        length = 2 + 32 + 32 + 8 + 20 + signature.getMessageSize();
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.append("txType", getName());
        result.append("version", version);
        result.append("regTxId", regTxId);
        result.append("hashPrevSubTx", hashPrevSubTx);
        result.append("creditFee", creditFee);
        result.append("newPubKeyId", newPubKeyId);
        return result;
    }

    @Override
    public int getCurrentVersion() { return CURRENT_VERSION; }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_SUBTX_RESETKEY;
    }

    @Override
    public String getName() {
        return "subTxResetKey";
    }

    public KeyId getNewPubKeyId() {
        return newPubKeyId;
    }
}
