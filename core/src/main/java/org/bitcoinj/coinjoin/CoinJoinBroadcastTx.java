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

import com.google.common.annotations.VisibleForTesting;
import org.bitcoinj.coinjoin.utils.ProTxToOutpoint;
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
import org.bitcoinj.crypto.BLSSecretKey;
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
    private Sha256Hash proTxHash;
    private MasternodeSignature signature;
    private long signatureTime;

    // memory only
    // when corresponding tx is 0-confirmed or conflicted, nConfirmedHeight is -1
    int confirmedHeight = -1;

    public CoinJoinBroadcastTx(NetworkParameters params, byte[] payload, int protocolVersion) {
        super(params, payload, 0, protocolVersion);
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

    @Deprecated
    public CoinJoinBroadcastTx(
            NetworkParameters params,
            Transaction tx,
            TransactionOutPoint masternodeOutpoint,
            long signatureTime
    ) {
        super(params);
        this.tx = tx;
        this.masternodeOutpoint = masternodeOutpoint;
        this.signatureTime = signatureTime;
    }

    public CoinJoinBroadcastTx(
            NetworkParameters params,
            Transaction tx,
            Sha256Hash proTxHash,
            long signatureTime
    ) {
        super(params);
        this.tx = tx;
        this.proTxHash = proTxHash;
        this.signatureTime = signatureTime;
    }

    @Override
    protected void parse() throws ProtocolException {
        tx = new Transaction(params, payload, cursor);
        cursor += tx.getMessageSize();
        if (protocolVersion >= params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.COINJOIN_PROTX_HASH)) {
            proTxHash = readHash();
        } else {
            masternodeOutpoint = new TransactionOutPoint(params, payload, cursor);
            cursor += masternodeOutpoint.getMessageSize();
        }
        signature = new MasternodeSignature(params, payload, cursor);
        cursor += signature.getMessageSize();
        signatureTime = readInt64();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        tx.bitcoinSerialize(stream);
        if (protocolVersion >= params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.COINJOIN_PROTX_HASH)) {
            stream.write(proTxHash.getReversedBytes());
        } else {
            masternodeOutpoint.bitcoinSerialize(stream);
        }
        signature.bitcoinSerialize(stream);
        Utils.int64ToByteStreamLE(signatureTime, stream);
    }

    public Sha256Hash getSignatureHash() {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            tx.bitcoinSerialize(bos);
            // this still requires the masternode output
            if (masternodeOutpoint == null)
                masternodeOutpoint = ProTxToOutpoint.getMasternodeOutpoint(proTxHash);
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
            log.info("coinjoin-checkSignature -- verifyInsecure() failed");
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        int denomination = CoinJoin.amountToDenomination(tx.getOutput(0).getValue());
        return String.format(
                "CoinJoinBroadcastTx(denomination=%s[%d], outputs=%d, tx=%s, proTxHash=%s, signatureTime=%d)",
                CoinJoin.denominationToString(denomination),
                denomination,
                tx.getOutputs().size(),
                tx.getTxId(),
                proTxHash,
                signatureTime
        );
    }

    public Transaction getTx() {
        return tx;
    }

    public TransactionOutPoint getMasternodeOutpoint() {
        if (masternodeOutpoint == null) {
            masternodeOutpoint = ProTxToOutpoint.getMasternodeOutpoint(proTxHash);
        }
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
    @VisibleForTesting
    public boolean sign(BLSSecretKey blsKeyOperator) {
        Sha256Hash hash = getSignatureHash();

        BLSSignature sig = blsKeyOperator.Sign(hash);
        if (!sig.isValid()) {
            return false;
        }
        signature = new MasternodeSignature(sig.bitcoinSerialize());

        return true;
    }
}
