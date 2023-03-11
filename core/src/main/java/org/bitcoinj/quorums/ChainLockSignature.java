package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSSignature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ChainLockSignature extends Message {

    static final String CLSIG_REQUESTID_PREFIX = "clsig";

    long height;
    Sha256Hash blockHash;
    BLSSignature signature;

    public ChainLockSignature(long height, Sha256Hash blockHash, BLSSignature signature) {
        this.height = height;
        this.blockHash = blockHash;
        this.signature = signature;
        length = 4 + 32 + BLSSignature.BLS_CURVE_SIG_SIZE;
    }

    public ChainLockSignature(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        height = readUint32();
        blockHash = readHash();
        signature = new BLSSignature(params, payload, cursor);
        cursor += signature.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(height, stream);
        stream.write(blockHash.getReversedBytes());
        signature.bitcoinSerialize(stream);
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

    public Sha256Hash getRequestId() {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            Utils.stringToByteStream(CLSIG_REQUESTID_PREFIX, bos);
            Utils.uint32ToByteStreamLE(height, bos);
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public String toString() {
        return String.format("ChainLockSignature{height=%d, blockHash=%s, sig=%s}", height, blockHash, signature);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChainLockSignature clsig = (ChainLockSignature)o;

        return getHash().equals(clsig.getHash());
    }

    @Override
    public int hashCode() {
        return getHash().hashCode();
    }

    public long getHeight() {
        return height;
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public BLSSignature getSignature() {
        return signature;
    }
}
