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

import com.google.common.collect.Lists;
import org.bitcoinj.core.DualBlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.evolution.AbstractQuorumRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class GetQuorumRotationInfo extends AbstractQuorumRequest {

    private ArrayList<Sha256Hash> baseBlockHashes;
    private Sha256Hash blockRequestHash;
    private boolean extraShare;

    public GetQuorumRotationInfo(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    public GetQuorumRotationInfo(NetworkParameters params,
                                 List<Sha256Hash> baseBlockHashes, Sha256Hash blockRequestHash, boolean extraShare) {
        super(params);
        this.baseBlockHashes = new ArrayList<>(baseBlockHashes.size());
        this.baseBlockHashes.addAll(baseBlockHashes);
        this.blockRequestHash = blockRequestHash;
        this.extraShare = extraShare;
    }

    @Override
    protected void parse() throws ProtocolException {
        int count = (int)readVarInt();
        baseBlockHashes = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            baseBlockHashes.add(readHash());
        }
        blockRequestHash = readHash();
        extraShare = readBytes(1)[0] == 1;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(baseBlockHashes.size()).encode());
        for (Sha256Hash hash : baseBlockHashes) {
            stream.write(hash.getReversedBytes());
        }
        stream.write(blockRequestHash.getReversedBytes());
        stream.write(extraShare ? 1 : 0);
    }

    public Sha256Hash getBlockRequestHash() {
        return blockRequestHash;
    }

    public List<Sha256Hash> getBaseBlockHashes() {
        return baseBlockHashes;
    }

    @Override
    public String toString() {
        return "GetQuorumRotationInfo{" +
                ", baseBlockHashes=" + baseBlockHashes +
                ", blockRequestHash=" + blockRequestHash +
                ", extraShare=" + extraShare +
                '}';
    }

    @Override
    public String toString(DualBlockChain blockChain) {
        List<Integer> baseHeights = Lists.newArrayList();
        int blockHeight = -1;
        try {
            for (Sha256Hash baseBlockHash : baseBlockHashes) {
                baseHeights.add(blockChain.getBlock(baseBlockHash).getHeight());
            }
            blockHeight = blockChain.getBlock(blockRequestHash).getHeight();
        } catch (NullPointerException x) {
            // swallow
        }

        return "GetQuorumRotationInfo{" +
                ", baseBlockHashes=" + baseHeights + " / " + baseBlockHashes +
                ", blockRequestHash=" + blockHeight + " / " + blockRequestHash +
                ", extraShare=" + extraShare +
                '}';
    }
}
