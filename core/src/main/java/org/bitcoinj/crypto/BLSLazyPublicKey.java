/*
 * Copyright 2019 Dash Core Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoinj.crypto;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * BLSPublicKey uses 152 bytes of native memory + java overhead,
 * while a serialized public key is 48 bytes
 *
 * BLSLazyPublicKey will keep the public key stored as the 48 bytes until it needs to be used
 * to perform an operation that requires BLSPublicKey
 *
 * Unlike BLSLazySignature, this class is Immutable
 */

public class BLSLazyPublicKey extends BLSAbstractLazyObject {
    BLSPublicKey publicKey;

    @Deprecated
    public BLSLazyPublicKey(NetworkParameters params) {
        super(params);
    }

    public BLSLazyPublicKey(BLSLazyPublicKey publicKey) {
        super(publicKey);
        this.publicKey = publicKey.publicKey;
    }

    public BLSLazyPublicKey(BLSPublicKey publicKey) {
        this(publicKey, publicKey.legacy);
    }

    public BLSLazyPublicKey(BLSPublicKey publicKey, boolean legacy) {
        super(Context.get().getParams());
        this.buffer = null;
        this.publicKey = publicKey;
        this.initialized = true;
        this.legacy = legacy;
        this.publicKey.setLegacy(legacy);
    }

    public BLSLazyPublicKey(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset, false);
    }

    public BLSLazyPublicKey(NetworkParameters params, byte [] payload, int offset, boolean legacy) {
        super(params, payload, offset, legacy);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        buffer = readBytes(BLSPublicKey.BLS_CURVE_PUBKEY_SIZE);
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        if (!initialized && buffer == null) {
            throw new IOException("public key and buffer are not initialized");
        }
        if (buffer == null) {
            stream.write(publicKey.getBuffer(BLSPublicKey.BLS_CURVE_PUBKEY_SIZE, legacy));
        } else {
            stream.write(buffer);
        }
    }

    public void bitcoinSerialize(OutputStream stream, boolean legacy) throws IOException {
        if (!initialized && buffer == null) {
            throw new IOException("public key and buffer are not initialized");
        }
        if (buffer == null) {
            stream.write(publicKey.getBuffer(BLSPublicKey.BLS_CURVE_PUBKEY_SIZE, legacy));
        } else {
            if (this.legacy == legacy) {
                stream.write(buffer);
            } else {
                getPublicKey().bitcoinSerialize(stream, legacy);
            }
        }
    }

    public static BLSPublicKey invalidSignature = new BLSPublicKey();

    public BLSPublicKey getPublicKey() {
        if(buffer == null && !initialized)
            return invalidSignature;
        if(!initialized) {
            publicKey = new BLSPublicKey(params, buffer, 0, legacy);
            buffer = null;  //save memory
            initialized = true;
        }
        return publicKey;
    }

    @Override
    public String toString() {
        return initialized ? publicKey.toString() : (buffer == null ? invalidSignature.toString() : Utils.HEX.encode(buffer));
    }

    @Deprecated
    public boolean isPublicKeyInitialized() {
        return initialized;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BLSLazyPublicKey that = (BLSLazyPublicKey) o;

        if (isInitialized()) {
            if (that.isInitialized())
                return Objects.equals(publicKey, that.publicKey);
            else
                return Objects.equals(publicKey.getBuffer(), that.buffer);
        } else {
            if (that.isInitialized())
                return Arrays.equals(buffer, that.publicKey.getBuffer());
            else
                return Arrays.equals(buffer, that.buffer);
        }
    }

    @Override
    public int hashCode() {
        return publicKey != null ? publicKey.hashCode() : 0;
    }
}
