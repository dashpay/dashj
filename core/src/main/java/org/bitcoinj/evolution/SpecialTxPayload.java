package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;


public abstract class SpecialTxPayload extends Message {

    int version;
    Transaction parentTransaction;

    SpecialTxPayload(NetworkParameters params, Transaction tx) {
        super(params, tx.getExtraPayload(), 0);
        this.parentTransaction = tx;
    }

    SpecialTxPayload(int version) {
        this.version = version;
    }


    @Override
    protected void parse() throws ProtocolException {
        version = readUint16();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint16ToByteStreamLE(version, stream);
    }

    public byte [] getPayload() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return bos.toByteArray();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public boolean check() {
        if(version <= getCurrentVersion())
            return false;
        return true;
    }

    public abstract int getCurrentVersion();

    public abstract Transaction.Type getType();

    public abstract String getName();

    public abstract JSONObject toJson();
}
