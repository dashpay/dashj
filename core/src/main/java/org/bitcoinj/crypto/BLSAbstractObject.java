package org.bitcoinj.crypto;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.*;
import org.dashj.bls.BLSObject;
import org.dashj.bls.JNI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public abstract class BLSAbstractObject extends ChildMessage {
    protected static final Logger log = LoggerFactory.getLogger(BLSAbstractObject.class);

    protected Sha256Hash hash;
    protected int serializedSize;
    boolean valid;
    BLSObject blsObject;

    static {
        try {
            System.loadLibrary(JNI.LIBRARY_NAME);
        } catch (UnsatisfiedLinkError x) {
            throw new RuntimeException(x.getMessage());
        }
    }

    abstract boolean internalSetBuffer(final byte [] buffer);
    abstract boolean internalGetBuffer(byte [] buffer);

    BLSAbstractObject(int serializedSize) {
        this.serializedSize = serializedSize;
        this.valid = false;
        updateHash();
    }

    BLSAbstractObject(byte [] buffer, int serializedSize) {
        Preconditions.checkArgument(buffer.length == serializedSize);
        this.serializedSize = serializedSize;
        setBuffer(buffer);
        updateHash();
    }

    BLSAbstractObject(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
        updateHash();
    }

    byte [] getBuffer() {
        return getBuffer(serializedSize);
    }

    byte [] getBuffer(int size) {
        Preconditions.checkArgument(size == serializedSize);
        byte [] buffer = new byte [serializedSize];
        if(valid) {
            boolean ok = internalGetBuffer(buffer);
            Preconditions.checkState(ok);
        }
        return buffer;
    }

    void setBuffer(byte [] buffer)
    {
        if(buffer.length != serializedSize) {
            reset();
        }

        int countZeros = 0;
        if(buffer[0] == 0) {

            for(byte b : buffer) {
                countZeros += (b == 0) ? 1 : 0;
            }

        }
        if(countZeros == serializedSize)
        {
            reset();
        }
        else {
            valid = internalSetBuffer(buffer);
            if(!valid)
                reset();
        }
        updateHash();
    }

    protected void reset() {
        valid = internalSetBuffer(new byte[serializedSize]);
        updateHash();
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(getBuffer(serializedSize));
    }

    protected void updateHash() {
        byte [] buffer = isValid() ? getBuffer(serializedSize) : new byte[serializedSize];
        hash = Sha256Hash.twiceOf(buffer);
    }

    @Override
    public Sha256Hash getHash() {
        if(hash == null) {
            updateHash();
        }
        return hash;
    }

    public boolean isValid() {
        return valid;
    }

    @Override
    public String toString() {
        return Utils.HEX.encode(getBuffer(serializedSize));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if(!(obj instanceof BLSAbstractObject))
            return false;
        return Arrays.equals(((BLSAbstractObject) obj).getBuffer(), getBuffer());
    }

    @Override
    public int hashCode() {
        return getHash().hashCode();
    }
}
