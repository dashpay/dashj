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
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.VarInt;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

// dss
public class CoinJoinSignedInputs extends Message {
    private List<TransactionInput> inputs;

    public CoinJoinSignedInputs(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
    }

    public CoinJoinSignedInputs(NetworkParameters params, List<TransactionInput> inputs) {
        super(params);
        this.inputs = inputs;
    }

    @Override
    protected void parse() throws ProtocolException {
        long numInputs = readVarInt();
        inputs = new ArrayList<>();

        for (int i = 0; i < numInputs; i++) {
            TransactionInput input = new TransactionInput(params, null, payload, cursor);
            inputs.add(input);
            cursor += input.getMessageSize();
        }

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(inputs.size()).encode());

        for (TransactionInput input : inputs) {
            input.bitcoinSerialize(stream);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "CoinJoinSignedInputs(inputs.size=%d)",
                inputs.size()
        );
    }

    public List<TransactionInput> getInputs() {
        return inputs;
    }
}
