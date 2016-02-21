/**
 * Copyright 2014 Hash Engineering Solutions
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static org.bitcoinj.core.Utils.int64ToByteStreamLE;

public class MasternodePing extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(MasternodePing.class);

    TransactionInput vin;
    Sha256Hash blockHash;
    long sigTime;
    byte[] vchSig;

    DarkCoinSystem system;

    MasternodePing(NetworkParameters params) {

    }
    MasternodePing(NetworkParameters params, TransactionInput newVin)
    {
        super(params);
    }

    MasternodePing(NetworkParameters params, byte[] bytes)
    {
        super(params, bytes, 0);
    }

    MasternodePing(NetworkParameters params, byte[] bytes, int cursor) {
        super(params, bytes, cursor);
    }

    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
            length = calcLength(payload, offset);
            cursor = offset + length;
        }
    }

    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;

        //vin
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        //blockHash
        cursor += 32;
        //sigTime
        cursor += 8;
        //vchSig
        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        return cursor - offset;
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;

        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        blockHash = readHash();

        sigTime = readInt64();

        vchSig = readByteArray();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        stream.write(blockHash.getBytes());
        int64ToByteStreamLE(sigTime, stream);

        stream.write(new VarInt(vchSig.length).encode());
        stream.write(vchSig);
    }

    int checkAndUpdate(boolean fRequireEnabled)
    {
        return -1;
    }

    boolean sign()
    {
        return false;
    }

}
