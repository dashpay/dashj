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
 * Created by Hash Engineering on 2/20/2015.
 *
 * MasternodeSignature holds a signature and serializes it
 * to the wire protocol
 */
public class MasternodeSignature extends ChildMessage {

    private byte [] bytes;

    public MasternodeSignature(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    public MasternodeSignature(byte [] signature)
    {
        bytes = new byte[signature.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        length = VarInt.sizeOf(bytes.length) + bytes.length;
    }

    public MasternodeSignature(MasternodeSignature other) {
        super(other.getParams());
        bytes = new byte[other.bytes.length];
        System.arraycopy(other.bytes, 0, bytes, 0, other.bytes.length);
        length = other.getMessageSize();
    }

    public static MasternodeSignature createEmpty() {
        return new MasternodeSignature(new byte[0]);
    }

    public int calculateMessageSizeInBytes() {
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
        return "Signature(" + Utils.HEX.encode(bytes) +")";
    }

    public byte [] getBytes() { return bytes; }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MasternodeSignature key = (MasternodeSignature)o;
        return Arrays.equals(key.bytes, this.bytes);
    }

    /**
     * Returns the first four bytes of the signature.
     */
    @Override
    public int hashCode() {
        return Ints.fromBytes(bytes[0], bytes[1], bytes[2], bytes[3]);
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }
}
