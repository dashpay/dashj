package org.bitcoinj.crypto;

import org.bitcoinj.core.ChildMessage;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

public class BLSLazySignature extends BLSAbstractLazyObject {
    ReentrantLock lock = Threading.lock("BLSLazySignature");
    Logger log = LoggerFactory.getLogger(BLSLazySignature.class);
    BLSSignature signature;

    public BLSLazySignature() {
        super(Context.get().getParams());
    }

    public BLSLazySignature(NetworkParameters params) {
        super(params);
    }

    public BLSLazySignature(BLSLazySignature signature) {
        super(signature);
        this.signature = signature.signature;
    }

    public BLSLazySignature(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset, BLSScheme.isLegacyDefault());
    }

    public BLSLazySignature(NetworkParameters params, byte [] payload, int offset, boolean legacy) {
        super(params, payload, offset, legacy);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        buffer = readBytes(BLSSignature.BLS_CURVE_SIG_SIZE);
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        lock.lock();
        try {
            if (!initialized && buffer == null) {
                log.warn("signature and buffer are not initialized");
                buffer = invalidSignature.getBuffer();
            }
            if (buffer == null) {
                buffer = signature.getBuffer(BLSSignature.BLS_CURVE_SIG_SIZE);
            }
            stream.write(buffer);
        } finally {
            lock.unlock();
        }
    }

    public BLSLazySignature assign(BLSLazySignature blsLazySignature) {
        lock.lock();
        try {
            buffer = new byte[BLSSignature.BLS_CURVE_SIG_SIZE];
            if(blsLazySignature.buffer != null) {
                System.arraycopy(blsLazySignature.buffer, 0, buffer, 0, BLSSignature.BLS_CURVE_SIG_SIZE);
            }
            initialized = blsLazySignature.initialized;
            if(initialized) {
                signature = blsLazySignature.signature;
            } else {
                signature.reset();
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    public static BLSSignature invalidSignature = new BLSSignature();

    public void setSignature(BLSSignature signature) {
        lock.lock();
        try {
            buffer = null;
            initialized = true;
            this.signature = signature;
        } finally {
            lock.unlock();
        }
    }

    public BLSSignature getSignature() {
        lock.lock();
        try {
            if(buffer == null && !initialized)
                return invalidSignature;
            if(!initialized) {
                signature = new BLSSignature(buffer, legacy);
                if(!signature.checkMalleable(buffer, BLSSignature.BLS_CURVE_SIG_SIZE)) {
                    buffer = null;
                    initialized = false;
                    signature = invalidSignature;
                } else {
                    initialized = true;
                }
            }
            return signature;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return initialized ? signature.toString() : (buffer == null ? invalidSignature.toString() : Utils.HEX.encode(buffer));
    }
}
