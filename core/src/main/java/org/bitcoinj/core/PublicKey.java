/*
 * Copyright 2015 Hash Engineering Solutions
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

import com.google.common.primitives.Ints;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Created by Hash Engineering on 2/10/2015.
 *
 * PublicKey holds an ECDSA public key
 */
public class PublicKey extends ChildMessage {

    private byte [] bytes;
    private ECKey key;

    public PublicKey(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    public PublicKey(byte [] key)
    {
        super();
        bytes = new byte[key.length];
        System.arraycopy(key, 0, bytes, 0, key.length);
        this.key = ECKey.fromPublicOnly(bytes);
    }

    public int calculateMessageSizeInBytes()
    {
        return VarInt.sizeOf(bytes.length) + bytes.length;
    }

    @Override
    protected void parse() throws ProtocolException {
        cursor = offset;
        bytes = readByteArray();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.bytesToByteStream(bytes, stream);
    }

    public String toString()
    {
        return "PublicKey(" + Utils.HEX.encode(bytes) +")";
    }

    public byte [] getBytes() { return bytes; }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PublicKey key = (PublicKey)o;
        return Arrays.equals(key.bytes, this.bytes);
    }

    /**
     * Returns the first four bytes of the public key.
     */
    @Override
    public int hashCode() {
        return Ints.fromBytes(bytes[0], bytes[1], bytes[2], bytes[3]);
    }

    public byte [] getId()
    {
        return getKey().getPubKeyHash();
    }

    public ECKey getKey()
    {
        if(key == null) {
            key = ECKey.fromPublicOnly(bytes);
        }
        return key;
    }
}
