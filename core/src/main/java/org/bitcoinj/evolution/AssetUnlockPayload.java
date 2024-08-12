/*
 * Copyright 2023 Dash Core Group
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

package org.bitcoinj.evolution;


import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSLazySignature;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

import static org.bitcoinj.core.Transaction.Type.TRANSACTION_ASSET_UNLOCK;

public class AssetUnlockPayload extends SpecialTxPayload {

    public static final int CURRENT_VERSION = 1;
    public static final Transaction.Type SPECIALTX_TYPE = TRANSACTION_ASSET_UNLOCK;

    private long index;
    private long fee;
    private long requestedHeight;
    private Sha256Hash quorumHash;
    BLSLazySignature quorumSig;
    public AssetUnlockPayload(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }


    public AssetUnlockPayload(NetworkParameters params, int version, long index, long fee, int requestedHeight, Sha256Hash quorumHash, BLSLazySignature quorumSig) {
        super(params, version);
        this.index = index;
        this.fee = fee;
        this.requestedHeight = requestedHeight;
        this.quorumHash = quorumHash;
        this.quorumSig = quorumSig;
        length = 8 + 4 + 4 + 32 + quorumSig.getMessageSize();
    }

    @Override
    protected void parse() throws ProtocolException {
        version = readBytes(1)[0]; // only one byte
        index = readInt64();
        fee = readUint32();
        requestedHeight = readUint32();
        quorumHash = readHash();
        quorumSig = new BLSLazySignature(params, payload, cursor);
        cursor += quorumSig.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(version); // only one byte
        Utils.uint64ToByteStreamLE(BigInteger.valueOf(index), stream);
        Utils.uint32ToByteStreamLE(fee, stream);
        Utils.uint32ToByteStreamLE(requestedHeight, stream);
        stream.write(quorumHash.getReversedBytes());
        quorumSig.bitcoinSerialize(stream);
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        return String.format("AssetUnlockPayload(index: %d, fee: %d, requestedHeight: %d, quorumHash: %s, quorumSig: %s)",
                index, fee, requestedHeight, quorumHash, quorumSig);
    }

    @Override
    public Transaction.Type getType() {
        return TRANSACTION_ASSET_UNLOCK;
    }

    @Override
    public String getName() {
        return "AssetUnlock";
    }

    @Override
    public JSONObject toJson() {
        JSONObject result = super.toJson();
        result.put("index", index);
        result.put("fee", fee);
        result.put("requestedHeight", requestedHeight);
        result.put("quorumHash", quorumHash);
        result.put("quorumSig", quorumSig);
        return result;
    }

    public long getIndex() {
        return index;
    }

    public long getFee() {
        return fee;
    }

    public long getRequestedHeight() {
        return requestedHeight;
    }

    public Sha256Hash getQuorumHash() {
        return quorumHash;
    }

    public BLSLazySignature getQuorumSig() {
        return quorumSig;
    }
}
