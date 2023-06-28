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

/**
 * This class extends Transaction and is used to create a funding
 * transaction for an identity.  It also can store other information
 * that is not stored in the blockchain transaction which includes
 * the public or private key's associated with this transaction.
 */
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

    /**
     * Create a credit funding transaction from an existing transaction.
     * This should only be called if {@link CreditFundingTransaction#isCreditFundingTransaction(Transaction)}
     * returns true.
     * @param tx this transaction should be a credit funding transaction
     */
    public CreditFundingTransaction(Transaction tx) {
        super(tx.getParams(), tx.bitcoinSerialize(), 0);
    }

    /**
     * Creates a credit funding transaction.
     * @param params
     * @param creditBurnKey The key from which the hash160 will be placed in the OP_RETURN output
     * @param fundingAmount The amount of dash that will be locked in the OP_RETURN output
     */
    public CreditFundingTransaction(NetworkParameters params, ECKey creditBurnKey, Coin fundingAmount) {
        super(params);
        this.fundingAmount = fundingAmount;
        this.creditBurnPublicKey = creditBurnKey;
        this.creditBurnPublicKeyId = KeyId.fromBytes(creditBurnPublicKey.getPubKeyHash());
        this.creditBurnIdentityIdentifier = Sha256Hash.ZERO_HASH;
        if (creditBurnKey instanceof DeterministicKey) {
            this.usedDerivationPathIndex = ((DeterministicKey)creditBurnKey).getChildNumber().num();
        } else this.usedDerivationPathIndex = -1;
        ScriptBuilder builder = new ScriptBuilder().addChunk(new ScriptChunk(OP_RETURN, null)).data(creditBurnPublicKey.getPubKeyHash());
        lockedOutput = new TransactionOutput(params, null, fundingAmount, builder.build().getProgram());
        addOutput(lockedOutput);
    }

    /**
     * Creates a credit funding transaction by reading payload.
     * Length of a transaction is fixed.
     */

    public CreditFundingTransaction(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    /**
     * Deserialize and initialize some fields from the credit burn output
     */
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

    /**
     * Initializes lockedOutput, lockedOutpoint, fundingAmount and the hash160
     * credit burn key
     */
    private void parseTransaction() {
        getLockedOutput();
        getLockedOutpoint();
        fundingAmount = lockedOutput.getValue();
        getCreditBurnPublicKeyId();
        creditBurnIdentityIdentifier = getCreditBurnIdentityIdentifier();
    }

    /**
     * Sets lockedOutput and returns output that has the OP_RETURN script
     */
    public TransactionOutput getLockedOutput() {
        if(lockedOutput != null)
            return lockedOutput;

        for(TransactionOutput output : getOutputs()) {
            Script script = output.getScriptPubKey();
            if(ScriptPattern.isCreditBurn(script)) {
                lockedOutput = output;
                return output;
            }
        }
        return null;
    }

    /**
     * Sets lockedOutpoint and returns outpoint that has the OP_RETURN script
     */
    public TransactionOutPoint getLockedOutpoint() {
        if(lockedOutpoint != null)
            return lockedOutpoint;

        for(int i = 0; i < getOutputs().size(); ++i) {
            Script script = getOutput(i).getScriptPubKey();
            if(ScriptPattern.isCreditBurn(script)) {
                // The lockedOutpoint must be in little endian to match Platform
                // having a reversed txid will not allow it to be searched or matched.
                lockedOutpoint = new TransactionOutPoint(params, i, Sha256Hash.wrap(getTxId().getReversedBytes()));
            }
        }

        return lockedOutpoint;
    }

    public Coin getFundingAmount() {
        return fundingAmount;
    }

    /**
     * Returns the credit burn identifier, which is the sha256(sha256(outpoint))
     */
    public Sha256Hash getCreditBurnIdentityIdentifier() {
        if(creditBurnIdentityIdentifier == null || creditBurnIdentityIdentifier.isZero()) {
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
                creditBurnPublicKeyId = KeyId.fromBytes(opReturnBytes);
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

    /**
     * Determines if a transaction has one or more credit burn outputs
     * and therefore is a is credit funding transaction
     */
    public static boolean isCreditFundingTransaction(Transaction tx) {
        for(TransactionOutput output : tx.getOutputs()) {
            Script script = output.getScriptPubKey();
            if(ScriptPattern.isCreditBurn(script)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines the first output that is a credit burn output
     * or returns -1.
     */
    public long getOutputIndex() {
        int outputCount = getOutputs().size();
        for (int i = 0; i < outputCount; ++i) {
            if (ScriptPattern.isCreditBurn(getOutput(i).getScriptPubKey()))
                return i;
        }
        return -1;
    }
}
