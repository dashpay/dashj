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
package org.bitcoinj.coinjoin;

import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.VarInt;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A client's transaction in the mixing pool
 */
// dsi
public class CoinJoinEntry extends Message {
    private List<CoinJoinTransactionInput> mixingInputs;
    private List<TransactionOutput> mixingOutputs;
    private Transaction txCollateral;

    public CoinJoinEntry(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
    }

    public CoinJoinEntry(
        NetworkParameters params,
        List<CoinJoinTransactionInput> mixingInputs,
        List<TransactionOutput> mixingOutputs,
        Transaction txCollateral
    ) {
        super(params);
        this.mixingInputs = mixingInputs;
        this.mixingOutputs = mixingOutputs;
        this.txCollateral = txCollateral;
    }

    @Override
    protected void parse() throws ProtocolException {
        long numInputs = readVarInt();
        mixingInputs = new ArrayList<>();

        for (int i = 0; i < numInputs; i++) {
            CoinJoinTransactionInput input = new CoinJoinTransactionInput(params, payload, cursor);
            mixingInputs.add(input);
            cursor += input.getMessageSize();
        }

        txCollateral = new Transaction(params, payload, cursor);
        cursor += txCollateral.getMessageSize();

        long numOutputs = readVarInt();
        mixingOutputs = new ArrayList<>();

        for (int i = 0; i < numOutputs; i++) {
            TransactionOutput output = new TransactionOutput(params, null, payload, cursor);
            mixingOutputs.add(output);
            cursor += output.getMessageSize();
        }

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(mixingInputs.size()).encode());

        for (TransactionInput input : mixingInputs) {
            input.bitcoinSerialize(stream);
        }

        txCollateral.bitcoinSerialize(stream);
        stream.write(new VarInt(mixingOutputs.size()).encode());

        for (TransactionOutput output : mixingOutputs) {
            output.bitcoinSerialize(stream);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "CoinJoinEntry(txCollateral=%s, mixingInputs.size=%d, mixingOutputs.size=%d)",
                txCollateral.getTxId(),
                mixingInputs.size(),
                mixingOutputs.size()
        );
    }

    public String toString(boolean includeMore) {
        StringBuilder builder = new StringBuilder();
        builder.append(this);
        if (includeMore) {
            for (CoinJoinTransactionInput input : mixingInputs) {
                builder.append("\n  input:  ").append(input);
            }
            for (TransactionOutput output : mixingOutputs) {
                builder.append("\n  output: ").append(output);
            }
        }
        return builder.toString();
    }

    public List<CoinJoinTransactionInput> getMixingInputs() {
        return mixingInputs;
    }

    public List<TransactionOutput> getMixingOutputs() {
        return mixingOutputs;
    }

    public Transaction getTxCollateral() {
        return txCollateral;
    }
}