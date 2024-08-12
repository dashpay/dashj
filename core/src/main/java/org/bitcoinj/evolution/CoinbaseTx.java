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

package org.bitcoinj.evolution;


import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSSignature;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

public class CoinbaseTx extends SpecialTxPayload {
    public static final int CB_V19_VERSION = 2;
    public static final int CB_V20_VERSION = 3;
    public static final int CURRENT_VERSION = CB_V19_VERSION;

    private long height;
    private Sha256Hash merkleRootMasternodeList;
    // Version 2
    private Sha256Hash merkleRootQuorums;
    // Version 3
    private int bestCLHeightDiff;
    private BLSSignature bestCLSignature;
    private Coin creditPoolBalance;

    public CoinbaseTx(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public CoinbaseTx(NetworkParameters params, int version, long height, Sha256Hash merkleRootMasternodeList,
                      Sha256Hash merkleRootQuorums, int bestCLHeightDiff, BLSSignature bestCLSignature,
                      Coin creditPoolBalance) {
        super(params, version);
        this.height = height;
        this.merkleRootMasternodeList = merkleRootMasternodeList;
        this.merkleRootQuorums = merkleRootQuorums;
        this.bestCLSignature = bestCLSignature;
        this.bestCLHeightDiff = bestCLHeightDiff;
        this.creditPoolBalance = creditPoolBalance;
        length = 4 + 32 + 32 + new VarInt(bestCLHeightDiff).getSizeInBytes() + bestCLSignature.getMessageSize() + 8;
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        height = readUint32();
        merkleRootMasternodeList = readHash();
        if(version >= CB_V19_VERSION) {
            merkleRootQuorums = readHash();
            if (version >= CB_V20_VERSION) {
                bestCLHeightDiff = (int)readVarInt();
                bestCLSignature = new BLSSignature(params, payload, cursor);
                cursor += bestCLSignature.getMessageSize();
                creditPoolBalance = Coin.valueOf(readInt64());
            }
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
        Utils.uint32ToByteStreamLE(height, stream);
        stream.write(merkleRootMasternodeList.getReversedBytes());
        if(version >= CB_V19_VERSION) {
            stream.write(merkleRootQuorums.getReversedBytes());
            if (version >= CB_V20_VERSION) {
                stream.write(new VarInt(bestCLHeightDiff).encode());
                bestCLSignature.bitcoinSerialize(stream);
                Utils.uint64ToByteStreamLE(BigInteger.valueOf(creditPoolBalance.value), stream);
            }
        }
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        return String.format("CoinbaseTx(v%d, height=%d, merkleRootMNList=%s, merkleRootQuorums=%s)",
               version, height, merkleRootMasternodeList.toString(), merkleRootQuorums);
    }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_COINBASE;
    }

    @Override
    public String getName() {
        return "coinbaseTx";
    }

    @Override
    public JSONObject toJson() {
        JSONObject result = super.toJson();
        result.put("height", height);
        result.put("merkleRootMNList", merkleRootMasternodeList.toString());
        if (version >= CB_V19_VERSION) {
            result.put("merkleRootQuorums", merkleRootQuorums.toString());
            if (version >= CB_V20_VERSION) {
                result.put("bestCLHeightDiff", bestCLHeightDiff);
                result.put("bestCLSignature", bestCLSignature.toString());
                result.put("creditPoolBalance", Double.parseDouble(creditPoolBalance.toPlainString()));
            }
        }
        return result;
    }

    public long getHeight() { return height; }

    public Sha256Hash getMerkleRootMasternodeList() {
        return merkleRootMasternodeList;
    }

    public Sha256Hash getMerkleRootQuorums() {
        return merkleRootQuorums;
    }

    public long getBestCLHeightDiff() {
        return bestCLHeightDiff;
    }

    public BLSSignature getBestCLSignature() {
        return bestCLSignature;
    }

    public Coin getCreditPoolBalance() {
        return creditPoolBalance;
    }
}
