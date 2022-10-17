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

import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSignature;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.script.ScriptPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_ENTRY_MAX_SIZE;

// dstx
public class CoinJoinBroadcastTx extends Message {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinQueue.class);

    private Transaction tx;
    private TransactionOutPoint masternodeOutpoint;
    private MasternodeSignature signature;
    private long signatureTime;

    // memory only
    // when corresponding tx is 0-confirmed or conflicted, nConfirmedHeight is -1
    int confirmedHeight = -1;

    public CoinJoinBroadcastTx(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
    }

    public CoinJoinBroadcastTx(
        NetworkParameters params,
        Transaction tx,
        TransactionOutPoint masternodeOutpoint,
        MasternodeSignature signature,
        long signatureTime
    ) {
        super(params);
        this.tx = tx;
        this.masternodeOutpoint = masternodeOutpoint;
        this.signature = signature;
        this.signatureTime = signatureTime;
    }

    @Override
    protected void parse() throws ProtocolException {
        tx = new Transaction(params, payload, cursor);
        cursor += tx.getMessageSize();
        masternodeOutpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += masternodeOutpoint.getMessageSize();
        signature = new MasternodeSignature(params, payload, cursor);
        cursor += signature.getMessageSize();
        signatureTime = readInt64();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        tx.bitcoinSerialize(stream);
        masternodeOutpoint.bitcoinSerialize(stream);
        signature.bitcoinSerialize(stream);
        Utils.int64ToByteStreamLE(signatureTime, stream);
    }

    public Sha256Hash getSignatureHash() {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            tx.bitcoinSerialize(bos);
            masternodeOutpoint.bitcoinSerialize(bos);
            Utils.int64ToByteStreamLE(signatureTime, bos);

            return Sha256Hash.twiceOf(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean checkSignature(BLSPublicKey pubKey) {
        Sha256Hash hash = getSignatureHash();
        BLSSignature sig = new BLSSignature(signature.getBytes());

        if (!sig.verifyInsecure(pubKey, hash)) {
            log.info("CoinJoinBroadcastTx-CheckSignature -- VerifyInsecure() failed\n");
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return String.format(
                "CoinJoinBroadcastTx(tx=%s, masternodeOutpoint=%s, signatureTime=%d)",
                tx.getTxId(),
                masternodeOutpoint.toString(),
                signatureTime
        );
    }

    public Transaction getTx() {
        return tx;
    }

    public TransactionOutPoint getMasternodeOutpoint() {
        return masternodeOutpoint;
    }

    public MasternodeSignature getSignature() {
        return signature;
    }

    public long getSignatureTime() {
        return signatureTime;
    }

    public void setConfirmedHeight(int confirmedHeight) {
        this.confirmedHeight = confirmedHeight;
    }

    public boolean isExpired(StoredBlock block) {
        // expire confirmed DSTXes after ~1h since confirmation or chainlocked confirmation
        if (confirmedHeight == -1 || block.getHeight() < confirmedHeight) return false; // not mined yet
        if (block.getHeight() - confirmedHeight > 24) return true; // mined more than an hour ago
        // TODO: this may crash
        return Context.get().chainLockHandler.hasChainLock(block.getHeight(), block.getHeader().getHash());
    }

    public boolean isValidStructure() {
        // some trivial checks only
        if (tx.getInputs().size() != tx.getOutputs().size()) {
            return false;
        }
        if (tx.getInputs().size() < CoinJoin.getMinPoolParticipants(params)) {
            return false;
        }
        if (tx.getInputs().size() > CoinJoin.getMaxPoolParticipants(params) * COINJOIN_ENTRY_MAX_SIZE) {
            return false;
        }

        boolean allOf = true;
        for (TransactionOutput txOut : tx.getOutputs()) {
            allOf = allOf && CoinJoin.isDenominatedAmount(txOut.getValue()) && ScriptPattern.isP2PKH(txOut.getScriptPubKey());
        }
        return allOf;
    }
}
