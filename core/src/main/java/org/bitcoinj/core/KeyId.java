/*
 * Copyright 2018 Dash Core Group
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
package org.bitcoinj.core;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Created by Hash Engineering on 8/25/2018.
 */
public class KeyId extends ChildMessage {
    public static final int MESSAGE_SIZE = 20;

    private byte [] bytes;

    public static final KeyId KEYID_ZERO = new KeyId(new byte[20]);

    public KeyId(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    public KeyId(byte [] key)
    {
        super();
        Preconditions.checkArgument(key.length == 20);
        bytes = new byte[key.length];
        System.arraycopy(key, 0, bytes, 0, key.length);
    }

    public int calculateMessageSizeInBytes()
    {
        return bytes.length;
    }

    @Override
    protected void parse() throws ProtocolException {
        bytes = readBytes(MESSAGE_SIZE);
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(bytes);
    }

    public String toString()
    {
        return "KeyId(" + Utils.HEX.encode(bytes) +")";
    }

    public byte [] getBytes() { return bytes; }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KeyId keyId = (KeyId)o;
        return Arrays.equals(keyId.bytes, this.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    Address getAddress(NetworkParameters params) {
        return Address.fromScriptHash(params, bytes);
    }

    @Override
    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(MESSAGE_SIZE);
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

}
