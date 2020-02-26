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
package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptPattern;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;

public class CreditFundingTransaction extends Transaction {

    TransactionOutput lockedOutput;
    TransactionOutPoint lockedOutpoint;
    Coin fundingAmount;
    Sha256Hash creditBurnIdentityIdentifier;
    ECKey creditBurnPublicKey;
    KeyId creditBurnPublicKeyId;
    int usedDerivationPathIndex;


    public CreditFundingTransaction(NetworkParameters params) {
        super(params);
    }

    public CreditFundingTransaction(Transaction tx) {
        super(tx.getParams(), tx.bitcoinSerialize(), 0);
    }

    public CreditFundingTransaction(NetworkParameters params, ECKey creditBurnKey, Coin fundingAmount) {
        super(params);
        this.fundingAmount = fundingAmount;
        this.creditBurnPublicKey = creditBurnKey;
        this.creditBurnPublicKeyId = new KeyId(creditBurnPublicKey.getPubKeyHash());
        this.creditBurnIdentityIdentifier = Sha256Hash.ZERO_HASH;
        if (creditBurnKey instanceof DeterministicKey) {
            this.usedDerivationPathIndex = ((DeterministicKey)creditBurnKey).getChildNumber().num();
        } else this.usedDerivationPathIndex = -1;
        ScriptBuilder builder = new ScriptBuilder().addChunk(new ScriptChunk(OP_RETURN, null)).data(creditBurnPublicKey.getPubKeyHash());
        lockedOutput = new TransactionOutput(params, null, fundingAmount, builder.build().getProgram());
        addOutput(lockedOutput);
    }

    public CreditFundingTransaction(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        parseTransaction();
    }

    @Override
    protected void unCache() {
        super.unCache();
        lockedOutpoint = null;
        lockedOutpoint = null;
        creditBurnIdentityIdentifier = Sha256Hash.ZERO_HASH;
        fundingAmount = Coin.ZERO;
        creditBurnPublicKeyId = KeyId.KEYID_ZERO;
    }

    private void parseTransaction() {
        getLockedOutput();
        getLockedOutpoint();
        fundingAmount = lockedOutput.getValue();
        getCreditBurnPublicKeyId();
        creditBurnIdentityIdentifier = getCreditBurnIdentityIdentifier();
    }

    public TransactionOutput getLockedOutput() {
        if(lockedOutput != null)
            return lockedOutput;

        for(TransactionOutput output : getOutputs()) {
            Script script = output.getScriptPubKey();
            if(ScriptPattern.isOpReturn(script)) {
                ScriptChunk scriptId = script.getChunks().get(1);
                if(scriptId.data.length == 20) {
                    lockedOutput = output;
                    return output;
                }
            }
        }
        return null;
    }

    public TransactionOutPoint getLockedOutpoint() {
        if(lockedOutpoint != null)
            return lockedOutpoint;

        for(int i = 0; i < getOutputs().size(); ++i) {
            Script script = getOutput(i).getScriptPubKey();
            if(ScriptPattern.isOpReturn(script)) {
                ScriptChunk scriptId = script.getChunks().get(1);
                if(scriptId.data.length == 20) {
                    lockedOutpoint = new TransactionOutPoint(params, i, Sha256Hash.wrap(getTxId().getReversedBytes()));
                }
            }
        }

        return lockedOutpoint;
    }

    public Coin getFundingAmount() {
        return fundingAmount;
    }

    public Sha256Hash getCreditBurnIdentityIdentifier() {
        if(creditBurnIdentityIdentifier == null || creditBurnIdentityIdentifier.equals(Sha256Hash.ZERO_HASH)) {
            try {
                ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(36);
                getLockedOutpoint().bitcoinSerialize(bos);
                creditBurnIdentityIdentifier = Sha256Hash.twiceOf(bos.toByteArray());
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        }
        return creditBurnIdentityIdentifier;
    }

    public ECKey getCreditBurnPublicKey() {
        return creditBurnPublicKey;
    }

    public KeyId getCreditBurnPublicKeyId() {
        if(creditBurnPublicKeyId == null || creditBurnPublicKeyId.equals(KeyId.KEYID_ZERO)) {
            byte [] opReturnBytes = lockedOutput.getScriptPubKey().getChunks().get(1).data;
            if(opReturnBytes.length == 20)
                creditBurnPublicKeyId = new KeyId(opReturnBytes);
        }
        return creditBurnPublicKeyId;
    }

    public int getUsedDerivationPathIndex() {
        return usedDerivationPathIndex;
    }

    public void setCreditBurnPublicKeyAndIndex(ECKey creditBurnPublicKey, int usedDerivationPathIndex) {
        this.creditBurnPublicKey = creditBurnPublicKey;
        this.usedDerivationPathIndex = usedDerivationPathIndex;
    }

    public static boolean isCreditFundingTransaction(Transaction tx) {
        for(TransactionOutput output : tx.getOutputs()) {
            Script script = output.getScriptPubKey();
            if(ScriptPattern.isOpReturn(script)) {
                ScriptChunk scriptId = script.getChunks().get(1);
                if(scriptId.data.length == 20) {
                    return true;
                }
            }
        }
        return false;
    }
}
