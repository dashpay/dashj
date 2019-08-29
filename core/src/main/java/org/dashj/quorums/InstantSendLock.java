package org.dashj.quorums;

import org.dashj.core.*;
import org.dashj.crypto.BLSLazySignature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class InstantSendLock extends Message {

    static final String ISLOCK_REQUESTID_PREFIX = "islock";

    ArrayList<TransactionOutPoint> inputs;
    Sha256Hash txid;
    BLSLazySignature signature;

    public InstantSendLock() {

    }

    public InstantSendLock(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        int countInputs = (int)readVarInt();
        inputs = new ArrayList<TransactionOutPoint>(countInputs);
        for(int i = 0; i < countInputs; ++i) {
            TransactionOutPoint outpoint = new TransactionOutPoint(params, payload, cursor);
            cursor += outpoint.getMessageSize();
            inputs.add(outpoint);
        }

        txid = readHash();

        signature = new BLSLazySignature(params, payload, cursor);
        cursor += signature.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(inputs.size()).encode());
        for(int i = 0; i < inputs.size(); ++i) {
            inputs.get(i).bitcoinSerialize(stream);
        }
        stream.write(txid.getReversedBytes());
        signature.bitcoinSerialize(stream);
    }

    public Sha256Hash getRequestId() {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            Utils.stringToByteStream(ISLOCK_REQUESTID_PREFIX, bos);
            bos.write(new VarInt(inputs.size()).encode());
            for(TransactionOutPoint outpoint : inputs) {
                outpoint.bitcoinSerialize(bos);
            }
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public String toString() {
        return String.format("InstantSendLock(%d inputs, txid=%s, sig=%s)", inputs.size(), txid, signature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InstantSendLock islock = (InstantSendLock)o;

        return getHash().equals(islock.getHash());
    }
}
