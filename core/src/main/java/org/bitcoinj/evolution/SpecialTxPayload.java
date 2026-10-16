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
import org.bitcoinj.script.Script;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public abstract class SpecialTxPayload extends ChildMessage {

    protected int version;

    protected SpecialTxPayload(NetworkParameters params, Transaction tx) {
        super(params, tx.getExtraPayload(), 0);
        setParent(tx);
    }

    protected SpecialTxPayload(int version) {
        this.version = version;
    }

    protected SpecialTxPayload(NetworkParameters params, int version) {
        super(params);
        this.version = version;
    }

    protected SpecialTxPayload(NetworkParameters params, byte [] payload, int offset) {
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

    /** reads the uint8 count and the owner payouts of a version 3+ ProRegTx or ProUpRegTx */
    protected List<MasternodePayoutShare> readPayouts() throws ProtocolException {
        int count = readBytes(1)[0] & 0xff;
        List<MasternodePayoutShare> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Script script = new Script(readByteArray());
            list.add(new MasternodePayoutShare(script, readUint16()));
        }
        return Collections.unmodifiableList(list);
    }

    protected static void writePayouts(List<MasternodePayoutShare> payouts, OutputStream stream) throws IOException {
        stream.write(payouts.size());
        for (MasternodePayoutShare payout : payouts) {
            payout.serialize(stream);
        }
    }

    /** the primary Core P2P address of a version 3+ ProTx, or 0.0.0.0:0 if it has no IPv4 or IPv6 one */
    protected static MasternodeAddress primaryOrEmpty(ExtNetInfo netInfo) {
        MasternodeAddress primary = netInfo.getPrimaryService();
        return primary != null ? primary : new MasternodeAddress("0.0.0.0", 0);
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
