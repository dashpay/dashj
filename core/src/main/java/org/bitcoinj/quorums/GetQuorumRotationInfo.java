/*
 * Copyright 2021 Dash Core Group
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

import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VarInt;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class GetQuorumRotationInfo extends Message {

    private long baseBlockHashesCount;
    private ArrayList<Sha256Hash> baseBlockHashes;
    private Sha256Hash blockRequestHash;

    public GetQuorumRotationInfo(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    public GetQuorumRotationInfo(NetworkParameters params, long baseBlockHashesCount,
                                 ArrayList<Sha256Hash> baseBlockHashes, Sha256Hash blockRequestHash) {
        super(params);
        this.baseBlockHashesCount = baseBlockHashesCount;
        this.baseBlockHashes = new ArrayList<>(baseBlockHashes.size());
        this.baseBlockHashes.addAll(baseBlockHashes);
        this.blockRequestHash = blockRequestHash;
    }

    @Override
    protected void parse() throws ProtocolException {
        baseBlockHashesCount = readUint32();
        int count = (int)readVarInt();
        baseBlockHashes = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            baseBlockHashes.add(readHash());
        }
        blockRequestHash = readHash();
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(baseBlockHashesCount, stream);
        stream.write(new VarInt(baseBlockHashes.size()).encode());
        for (Sha256Hash hash : baseBlockHashes) {
            stream.write(hash.getReversedBytes());
        }
        stream.write(blockRequestHash.getReversedBytes());
    }

    @Override
    public String toString() {
        return "GetQuorumRotationInfo{" +
                "baseBlockHashesCount=" + baseBlockHashesCount +
                ", baseBlockHashes=" + baseBlockHashes +
                ", blockRequestHash=" + blockRequestHash +
                '}';
    }
}
