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

import com.google.common.base.Preconditions;
import org.bitcoinj.core.*;
import org.dashj.bls.BLSJniLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public abstract class BLSAbstractObject extends ChildMessage {
    protected static final Logger log = LoggerFactory.getLogger(BLSAbstractObject.class);
    protected boolean legacy;
    protected Sha256Hash hash;
    protected int serializedSize;
    protected boolean valid;

    static {
        BLSJniLibrary.init();
    }

    abstract boolean internalSetBuffer(final byte [] buffer);
    abstract boolean internalGetBuffer(byte [] buffer);

    BLSAbstractObject(int serializedSize) {
        this.serializedSize = serializedSize;
        this.valid = false;
        this.legacy = BLSScheme.legacyDefault;
        updateHash();
    }

    BLSAbstractObject(byte [] buffer, int serializedSize) {
        this(serializedSize);
        Preconditions.checkArgument(buffer.length == serializedSize);
        setBuffer(buffer);
    }

    BLSAbstractObject(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
        this.legacy = BLSScheme.legacyDefault;
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
