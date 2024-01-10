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


import com.google.common.collect.Lists;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static org.bitcoinj.core.Transaction.Type.TRANSACTION_ASSET_LOCK;
import static org.bitcoinj.core.Utils.HEX;

public class AssetLockPayload extends SpecialTxPayload {

    public static final int CURRENT_VERSION = 1;
    public static final Transaction.Type SPECIALTX_TYPE = TRANSACTION_ASSET_LOCK;
    private ArrayList<TransactionOutput> creditOutputs;

    public AssetLockPayload(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public AssetLockPayload(NetworkParameters params, List<TransactionOutput> creditOutputs) {
        this(params, CURRENT_VERSION, creditOutputs);
    }

    public AssetLockPayload(NetworkParameters params, int version, List<TransactionOutput> creditOutputs) {
        super(params, version);
        this.creditOutputs = new ArrayList<>(creditOutputs);
        length = new VarInt(creditOutputs.size()).getSizeInBytes();
        creditOutputs.forEach(output -> length += output.getMessageSize());
    }
    @Override
    protected void parse() throws ProtocolException {
        version = readBytes(1)[0];
        int size = (int) readVarInt();
        creditOutputs = Lists.newArrayList();
        for (int i = 0; i < size; ++i) {
            TransactionOutput output = new TransactionOutput(params, null, payload, cursor);
            cursor += output.getMessageSize();
            creditOutputs.add(output);
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(version);
        stream.write(new VarInt(creditOutputs.size()).encode());
        for (int i = 0; i < creditOutputs.size(); ++i) {
            creditOutputs.get(i).bitcoinSerialize(stream);
        }
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        StringBuilder s = new StringBuilder("AssetLockPayload");
        creditOutputs.forEach(output -> {
            Script scriptPubKey = output.getScriptPubKey();
            s.append("\n   out  ");
            s.append(scriptPubKey.getChunks().size() > 0 ? scriptPubKey.toString() : "<no scriptPubKey>");
            s.append("  ");
            s.append(output.getValue().toFriendlyString());
            s.append('\n');
            s.append("        ");
            Script.ScriptType scriptType = scriptPubKey.getScriptType();
            s.append(scriptType).append(" addr:").append(scriptPubKey.getToAddress(params));
        });
        return s.toString();
    }

    @Override
    public Transaction.Type getType() {
        return TRANSACTION_ASSET_LOCK;
    }

    @Override
    public String getName() {
        return "AssetLock";
    }

    public List<TransactionOutput> getCreditOutputs() {
        return creditOutputs;
    }

    @Override
    public JSONObject toJson() {
        JSONObject result = super.toJson();
        creditOutputs.forEach(output -> result.append("creditOutputs", output.toString()));
        return result;
    }
}
