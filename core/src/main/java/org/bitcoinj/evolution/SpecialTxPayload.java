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
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;


public abstract class SpecialTxPayload extends ChildMessage {

    protected int version;

    public SpecialTxPayload(NetworkParameters params, Transaction tx) {
        super(params, tx.getExtraPayload(), 0);
        setParent(tx);
    }

    public SpecialTxPayload(int version) {
        this.version = version;
    }

    SpecialTxPayload(NetworkParameters params, int version) {
        super(params);
        this.version = version;
    }

    public SpecialTxPayload(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    protected void parse() throws ProtocolException {
        version = readUint16();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint16ToByteStreamLE(version, stream);
    }

    public byte [] getPayload() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return bos.toByteArray();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public Sha256Hash getHash() {
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(getPayload()));
    }

    public void check() throws VerificationException {
        if(version > getCurrentVersion())
            throw new VerificationException("Invalid special tx version:" + version);
    }

    public int getVersion() {
        return version;
    }

    public Transaction getParentTransaction() {
        return (Transaction) parent;
    }

    public void setParentTransaction(Transaction parentTransaction) {
        setParent(parentTransaction);
    }

    public abstract int getCurrentVersion();

    public abstract Transaction.Type getType();

    public abstract String getName();

    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.put("version", version);
        return result;
    }
}
