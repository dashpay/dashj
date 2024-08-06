/*
 * Copyright 2019 Dash Core Group
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

    boolean legacy;

    public ChainLockSignature(long height, Sha256Hash blockHash, BLSSignature signature) {
        this.height = height;
        this.blockHash = blockHash;
        this.signature = signature;
        this.legacy = signature.isLegacy();
        length = 4 + 32 + BLSSignature.BLS_CURVE_SIG_SIZE;
    }

    public ChainLockSignature(NetworkParameters params, byte [] payload, boolean legacy) {
        super(params, payload, 0, (legacy ? NetworkParameters.ProtocolVersion.BLS_LEGACY : NetworkParameters.ProtocolVersion.BLS_BASIC).getBitcoinProtocolVersion());
        this.legacy = legacy;
    }

    @Override
    protected void parse() throws ProtocolException {
        height = readUint32();
        blockHash = readHash();
        signature = new BLSSignature(params, payload, cursor, protocolVersion == NetworkParameters.ProtocolVersion.BLS_LEGACY.getBitcoinProtocolVersion());
        cursor += signature.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(height, stream);
        stream.write(blockHash.getReversedBytes());
        signature.bitcoinSerialize(stream);
    }

    @Override
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
