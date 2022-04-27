package org.bitcoinj.crypto;

import org.bitcoinj.core.ChildMessage;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

public class BLSLazySignature extends ChildMessage {
    ReentrantLock lock = Threading.lock("BLSLazySignature");
    Logger log = LoggerFactory.getLogger(BLSLazySignature.class);

    byte [] buffer;
    BLSSignature signature;
    boolean isSingatureInitialized;

    public BLSLazySignature() {
    }

    public BLSLazySignature(NetworkParameters params) {
        super(params);
    }

    public BLSLazySignature(BLSLazySignature signature) {
        super(signature.params);
        this.signature = signature.signature;
        this.isSingatureInitialized = signature.isSingatureInitialized;
    }

    public BLSLazySignature(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    protected void parse() throws ProtocolException {
        buffer = readBytes(BLSSignature.BLS_CURVE_SIG_SIZE);
        isSingatureInitialized = false;
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        lock.lock();
        try {
            if (!isSingatureInitialized && buffer == null) {
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
            isSingatureInitialized = blsLazySignature.isSingatureInitialized;
            if(isSingatureInitialized) {
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
            isSingatureInitialized = true;
            this.signature = signature;
        } finally {
            lock.unlock();
        }
    }

    public BLSSignature getSignature() {
        lock.lock();
        try {
            if(buffer == null && !isSingatureInitialized)
                return invalidSignature;
            if(!isSingatureInitialized) {
                signature = new BLSSignature(buffer);
                if(!signature.checkMalleable(buffer, BLSSignature.BLS_CURVE_SIG_SIZE)) {
                    buffer = null;
                    isSingatureInitialized = false;
                    signature = invalidSignature;
                } else {
                    isSingatureInitialized = true;
                }
            }
            return signature;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return isSingatureInitialized ? signature.toString() : (buffer == null ? invalidSignature.toString() : Utils.HEX.encode(buffer));
    }
}
