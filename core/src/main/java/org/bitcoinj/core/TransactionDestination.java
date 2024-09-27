/*
 * Copyright 2022 Dash Core Group
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
import com.google.common.primitives.Ints;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public abstract class TransactionDestination extends Message {
    public static final int MESSAGE_SIZE = 20;

    protected byte [] bytes;


    public TransactionDestination(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    public TransactionDestination(byte [] key)
    {
        super();
        Preconditions.checkArgument(key.length == 20);
        bytes = new byte[key.length];
        System.arraycopy(key, 0, bytes, 0, key.length);
    }

    @Nullable
    public static TransactionDestination fromScript(Script script) {
        if (ScriptPattern.isP2PKH(script)) {
            return KeyId.fromBytes(ScriptPattern.extractHashFromP2PKH(script));
        } else if (ScriptPattern.isP2SH(script)) {
            return new ScriptId(ScriptPattern.extractHashFromP2SH(script));
        } else if (ScriptPattern.isP2PK(script)) {
            return KeyId.fromBytes(ECKey.fromPublicOnly(ScriptPattern.extractKeyFromP2PK(script)).getPubKeyHash());
        } else {
            return null;
        }
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

    public byte [] getBytes() { return bytes; }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionDestination keyId = (TransactionDestination)o;
        return Arrays.equals(keyId.bytes, this.bytes);
    }

    public abstract Address getAddress(NetworkParameters params);

    public abstract Script getScript();

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

    @Override
    public int hashCode() {
        return Ints.fromBytes(bytes[MESSAGE_SIZE - 4], bytes[MESSAGE_SIZE - 3], bytes[MESSAGE_SIZE - 2], bytes[MESSAGE_SIZE - 1]);
    }
}
