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

/**
 * BLSPublicKey uses 152 bytes of native memory + java overhead,
 * while a serialized public key is 48 bytes
 *
 * BLSLazyPublicKey will keep the public key stored as the 48 bytes until it needs to be used
 * to perform an operation that requires BLSPublicKey
 *
 * Unlike BLSLazySignature, this class is Immutable
 */

public class BLSLazyPublicKey extends ChildMessage {

    byte [] buffer;
    BLSPublicKey publicKey;
    boolean isPublicKeyInitialized;

    public BLSLazyPublicKey(NetworkParameters params) {
        super(params);
    }

    public BLSLazyPublicKey(BLSLazyPublicKey publicKey) {
        super(publicKey.params);
        this.buffer = publicKey.buffer;
        this.publicKey = publicKey.publicKey;
        this.isPublicKeyInitialized = publicKey.isPublicKeyInitialized;
    }

    public BLSLazyPublicKey(BLSPublicKey publicKey) {
        super(Context.get().getParams());
        this.buffer = null;
        this.publicKey = publicKey;
        this.isPublicKeyInitialized = true;
    }

    public BLSLazyPublicKey(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    protected void parse() throws ProtocolException {
        buffer = readBytes(BLSPublicKey.BLS_CURVE_PUBKEY_SIZE);
        isPublicKeyInitialized = false;
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        if (!isPublicKeyInitialized && buffer == null) {
            throw new IOException("public key and buffer are not initialized");
        }
        if (buffer == null) {
            stream.write(publicKey.getBuffer(BLSPublicKey.BLS_CURVE_PUBKEY_SIZE));
        } else {
            stream.write(buffer);
        }
    }

    public static BLSPublicKey invalidSignature = new BLSPublicKey();

    public BLSPublicKey getPublicKey() {
        if(buffer == null && !isPublicKeyInitialized)
            return invalidSignature;
        if(!isPublicKeyInitialized) {
            publicKey = new BLSPublicKey(params, buffer, 0);
            buffer = null;  //save memory
            isPublicKeyInitialized = true;
        }
        return publicKey;
    }

    @Override
    public String toString() {
        return isPublicKeyInitialized ? publicKey.toString() : (buffer == null ? invalidSignature.toString() : Utils.HEX.encode(buffer));
    }

    public boolean isPublicKeyInitialized() {
        return isPublicKeyInitialized;
    }
}
