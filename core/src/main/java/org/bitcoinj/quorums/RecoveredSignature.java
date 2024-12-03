package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazySignature;
import org.bitcoinj.crypto.BLSSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

public class RecoveredSignature extends Message {
    public static final int CURRENT_VERSION = 1;

    private static final Logger log = LoggerFactory.getLogger(RecoveredSignature.class);


    int llmqType; //short
    Sha256Hash quorumHash;
    Sha256Hash id;
    Sha256Hash msgHash;
    BLSLazySignature signature;

    // in memory
    Sha256Hash hash;

    RecoveredSignature() {
        length = 1 + 32 + 32 + 32 + BLSSignature.BLS_CURVE_SIG_SIZE;
    }

    public RecoveredSignature(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    protected void parse() throws ProtocolException {

        llmqType = readBytes(1)[0];
        quorumHash = readHash();
        id = readHash();
        msgHash = readHash();

        signature = new BLSLazySignature(params, payload, cursor);
        cursor += signature.getMessageSize();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException{
        stream.write(llmqType);
        stream.write(quorumHash.getReversedBytes());
        stream.write(id.getReversedBytes());
        stream.write(msgHash.getReversedBytes());
        signature.bitcoinSerialize(stream);
    }

    public String toString() {
        return String.format("RecoveredSignature(llmqType=%d, quorumHash=%s, id=%s, msgHash=%s",
                llmqType, quorumHash, id, msgHash);
    }

    public void updateHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            hash = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public Sha256Hash getHash() {
        if(hash == null)
            updateHash();
        return hash;
    }

    public Sha256Hash getId() {
        return id;
    }

    public Sha256Hash getMsgHash() {
        return msgHash;
    }

    public Sha256Hash getQuorumHash() {
        return quorumHash;
    }

    public BLSLazySignature getSignature() {
        return signature;
    }

    public int getLlmqType() {
        return llmqType;
    }
}
