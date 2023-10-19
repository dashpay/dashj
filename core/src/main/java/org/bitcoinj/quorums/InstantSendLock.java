/*
 * Copyright 2020 Dash Core Group
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

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazySignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * This message is used to provide details of transactions that
 * have been locked by LLMQ-based InstantSend.
 */
public class InstantSendLock extends Message {

    private static final Logger log = LoggerFactory.getLogger(InstantSendLock.class);
    static final String ISLOCK_REQUESTID_PREFIX = "islock";
    public static final int ISLOCK_VERSION = 0;
    public static final int ISDLOCK_VERSION = 1;

    private int version;
    List<TransactionOutPoint> inputs;
    Sha256Hash txid;
    Sha256Hash cycleHash;
    BLSLazySignature signature;

    public InstantSendLock(NetworkParameters params, List<TransactionOutPoint> inputs, Sha256Hash txid, BLSLazySignature signature) {
        super(params);
        this.version = ISLOCK_VERSION;
        this.protocolVersion = NetworkParameters.ProtocolVersion.ISDLOCK.getBitcoinProtocolVersion() - 1;
        this.inputs = inputs;
        this.txid = txid;
        this.signature = signature;
    }

    public InstantSendLock(NetworkParameters params, List<TransactionOutPoint> inputs, Sha256Hash txid, Sha256Hash cycleHash, BLSLazySignature signature) {
        this(params, inputs, txid, signature);
        this.version = ISDLOCK_VERSION;
        this.protocolVersion = NetworkParameters.ProtocolVersion.ISDLOCK.getBitcoinProtocolVersion();
        this.cycleHash = cycleHash;
    }

    public InstantSendLock(NetworkParameters params, byte [] payload, int version) {
        super(params, payload, 0, version == ISDLOCK_VERSION ?
                params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.ISDLOCK) : 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        if (protocolVersion >= NetworkParameters.ProtocolVersion.ISDLOCK.getBitcoinProtocolVersion()) {
            version = readBytes(1)[0];
        }
        int countInputs = (int)readVarInt();
        inputs = new ArrayList<>(countInputs);
        for(int i = 0; i < countInputs; ++i) {
            TransactionOutPoint outpoint = new TransactionOutPoint(params, payload, cursor);
            cursor += outpoint.getMessageSize();
            inputs.add(outpoint);
        }

        txid = readHash();

        if (protocolVersion >= NetworkParameters.ProtocolVersion.ISDLOCK.getBitcoinProtocolVersion()) {
            cycleHash = readHash();
        }
        signature = new BLSLazySignature(params, payload, cursor);
        cursor += signature.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        if (protocolVersion >= NetworkParameters.ProtocolVersion.ISDLOCK.getBitcoinProtocolVersion()) {
            stream.write(version);
        }
        stream.write(new VarInt(inputs.size()).encode());
        for (TransactionOutPoint input : inputs) {
            input.bitcoinSerialize(stream);
        }
        stream.write(txid.getReversedBytes());
        if (protocolVersion >= NetworkParameters.ProtocolVersion.ISDLOCK.getBitcoinProtocolVersion()) {
            stream.write(cycleHash.getReversedBytes());
        }
        signature.bitcoinSerialize(stream);
    }

    public Sha256Hash getRequestId() {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            Utils.stringToByteStream(ISLOCK_REQUESTID_PREFIX, bos);
            bos.write(new VarInt(inputs.size()).encode());
            for(TransactionOutPoint outpoint : inputs) {
                outpoint.bitcoinSerialize(bos);
            }
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            log.error("invalid islock object {}", this, x);
            return Sha256Hash.ZERO_HASH;
        }
    }

    @Override
    public String toString() {
        return String.format("InstantSendLock(version=%d, %d inputs, txid=%s, cycleHash=%s, sig=%s)",
                version, inputs.size(), txid, cycleHash, signature);
    }

    @Override
    public int hashCode() {
        return txid.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InstantSendLock islock = (InstantSendLock)o;

        return getHash().equals(islock.getHash());
    }

    public List<TransactionOutPoint> getInputs() {
        return inputs;
    }

    public int getVersion() {
        return version;
    }

    public BLSLazySignature getSignature() {
        return signature;
    }

    public Sha256Hash getTxId() {
        return txid;
    }

    public boolean isDeterministic() {
        return version != ISLOCK_VERSION;
    }

    public Sha256Hash getCycleHash() {
        return cycleHash;
    }

    // in memory, for debugging only
    private Sha256Hash quorumHash;

    private int quorumIndex;

    public Sha256Hash getQuorumHash() {
        return quorumHash;
    }

    public int getQuorumIndex() {
        return quorumIndex;
    }

    public void setQuorum(Sha256Hash quorumHash, int quorumIndex) {
        this.quorumHash = quorumHash;
        this.quorumIndex = quorumIndex;
    }
}
