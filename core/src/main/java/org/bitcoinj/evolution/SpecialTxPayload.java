package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;


public abstract class SpecialTxPayload extends Message {

    protected int version;
    Transaction parentTransaction;

    public SpecialTxPayload(NetworkParameters params, Transaction tx) {
        super(params, tx.getExtraPayload(), 0);
        this.parentTransaction = tx;
    }

    public SpecialTxPayload(int version) {
        this.version = version;
    }

    SpecialTxPayload(NetworkParameters params, int version) {
        super(params);
        this.version = version;
    }

    public SpecialTxPayload(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
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

    @Override
    public Sha256Hash getHash() {
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(getPayload()));
    }

    public void check() throws VerificationException {
        if(version > getCurrentVersion())
            throw new VerificationException("Invalid special tx version:" + version);
    }

    public int getVersion() {
        return version;
    }

    public Transaction getParentTransaction() {
        return parentTransaction;
    }

    public void setParentTransaction(Transaction parentTransaction) {
        this.parentTransaction = parentTransaction;
    }

    public abstract int getCurrentVersion();

    public abstract Transaction.Type getType();

    public abstract String getName();

    public abstract JSONObject toJson();
}
