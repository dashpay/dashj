package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;

public class SubTxTopup extends SpecialTxPayload {

    public final int CURRENT_VERSION = 1;
    public final int MESSAGE_SIZE = 38;
    public static final Coin MIN_SUBTX_TOPUP = Coin.valueOf(10000);

    Sha256Hash regTxId;

    public SubTxTopup(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public SubTxTopup(int version, Sha256Hash regTxId) {
        super(version);
        this.regTxId = regTxId;
        length = MESSAGE_SIZE;
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        regTxId = readHash();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
        stream.write(regTxId.getReversedBytes());
    }

    @Override
    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.append("txType", getName());
        result.append("version", version);
        result.append("regTxId", regTxId);
        return result;
    }

    static Transaction create(NetworkParameters params, int version, Sha256Hash regTxId, Coin amount) {
        Transaction tx = new Transaction(params);
        SubTxTopup subtx = new SubTxTopup(version, regTxId);

        tx.setExtraPayload(subtx.getPayload());

        ScriptBuilder builder = new ScriptBuilder().addChunk(new ScriptChunk(OP_RETURN, null));
        TransactionOutput output = new TransactionOutput(params, null, amount, builder.build().getProgram());

        tx.addOutput(output);

        return tx;
    }

    @Override
    public int getCurrentVersion() { return CURRENT_VERSION; }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_SUBTX_TOPUP;
    }

    @Override
    public String getName() {
        return "subTxTopup";
    }

    public Sha256Hash getRegTxId() {
        return regTxId;
    }
}
