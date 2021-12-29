package org.bitcoinj.crypto;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;

public class BLSId extends BLSAbstractObject {
    public static int BLS_CURVE_ID_SIZE  = 32;
    Sha256Hash hash;

    BLSId() {
        super(BLS_CURVE_ID_SIZE);
    }

    BLSId(Sha256Hash hash) {
        super(BLS_CURVE_ID_SIZE);
        valid = true;
        this.hash = Sha256Hash.wrap(hash.getBytes());
        updateHash();
    }

    public BLSId(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    boolean internalSetBuffer(byte[] buffer) {
        try {
            hash = Sha256Hash.wrap(buffer);
            return true;
        } catch (Exception x) {
            return false;
        }
    }

    public static BLSId fromHash(Sha256Hash hash) {
        return new BLSId(hash);
    }

    @Override
    boolean internalGetBuffer(byte[] buffer) {
        System.arraycopy(hash.getBytes(), 0, buffer, 0, buffer.length);
        return true;
    }

    @Override
    protected void parse() throws ProtocolException {
        byte[] buffer = readBytes(BLS_CURVE_ID_SIZE);
        internalSetBuffer(buffer);
        serializedSize = BLS_CURVE_ID_SIZE;
        length = cursor - offset;
    }

    public byte [] getBytes() {
        return hash.getBytes();
    }
}
