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

import com.google.common.collect.Maps;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class extends Transaction and is used to create a funding
 * transaction for an identity.  It also can store other information
 * that is not stored in the blockchain transaction which includes
 * the public or private key's associated with this transaction.
 */
public class AssetLockTransaction extends Transaction {

    private ArrayList<TransactionOutPoint> lockedOutpoints;
    private ArrayList<Sha256Hash> identityIds;
    private TreeMap<Integer, ECKey> assetLockPublicKeys;
    private ArrayList<KeyId> assetLockPublicKeyIds;
    private AssetLockPayload assetLockPayload;



    public AssetLockTransaction(NetworkParameters params) {
        super(params);
    }

    /**
     * Create an asset lock transaction from an existing transaction.
     * This should only be called if {@link AssetLockTransaction#isAssetLockTransaction(Transaction)}
     * returns true.
     * @param tx this transaction should be a credit funding transaction
     */
    public AssetLockTransaction(Transaction tx) {
        super(tx.getParams(), tx.bitcoinSerialize(), 0);
    }

    /**
     * Creates a credit funding transaction with a single credit output.
     * @param params
     * @param assetLockPublicKey The key from which the hash160 will be placed in the OP_RETURN output
     * @param fundingAmount The amount of dash that will be locked in the OP_RETURN output
     */
    public AssetLockTransaction(NetworkParameters params, ECKey assetLockPublicKey, Coin fundingAmount) {
        super(params);
        setVersionAndType(SPECIAL_VERSION, Type.TRANSACTION_ASSET_LOCK);
        this.assetLockPublicKeys = Maps.newTreeMap();
        assetLockPublicKeys.put(0, assetLockPublicKey);
        this.assetLockPublicKeyIds = new ArrayList<>();
        assetLockPublicKeyIds.add(KeyId.fromBytes(assetLockPublicKey.getPubKeyHash()));
        this.identityIds = new ArrayList<>();
        identityIds.add(Sha256Hash.ZERO_HASH);

        TransactionOutput realOutput = new TransactionOutput(params, this, fundingAmount, Address.fromKey(params, assetLockPublicKey));

        lockedOutpoints = new ArrayList<>();
        TransactionOutput assetLockOutput = new TransactionOutput(params, null, fundingAmount, ScriptBuilder.createAssetLockOutput().getProgram());
        assetLockPayload = new AssetLockPayload(params, new ArrayList<>(Collections.singletonList(realOutput)));
        setExtraPayload(assetLockPayload);
        addOutput(assetLockOutput);
    }

    /**
     * Creates a credit funding transaction by reading payload.
     * Length of a transaction is fixed.
     */

    public AssetLockTransaction(NetworkParameters params, byte [] payload) {
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
        lockedOutpoints.clear();
        identityIds.clear();
        assetLockPublicKeyIds.clear();
    }

    /**
     * Initializes lockedOutpoints and the hash160
     * assetlock key
     */
    private void parseTransaction() {
        assetLockPayload = (AssetLockPayload) getExtraPayloadObject();
        lockedOutpoints = new ArrayList<>();
        assetLockPublicKeyIds = new ArrayList<>();
        assetLockPublicKeys = Maps.newTreeMap();
        identityIds = new ArrayList<>();
        getLockedOutpoint();
        getAssetLockPublicKeyId();
        getIdentityId();
    }

    /**
     * Sets lockedOutput and returns output that has the OP_RETURN script
     */

    public TransactionOutput getLockedOutput() {
        return getLockedOutput(0);
    }

    public TransactionOutput getLockedOutput(int outputIndex) {
        return assetLockPayload.getCreditOutputs().get(outputIndex);
    }

    public TransactionOutPoint getLockedOutpoint() {
        return getLockedOutpoint(0);
    }

    public AssetLockPayload getAssetLockPayload() {
        return assetLockPayload;
    }

    /**
     * Sets lockedOutpoint and returns outpoint that has the OP_RETURN script
     */



    public TransactionOutPoint getLockedOutpoint(int outputIndex) {
        if (lockedOutpoints.isEmpty()) {
            for (int i = 0; i < assetLockPayload.getCreditOutputs().size(); ++i) {
               lockedOutpoints.add(new TransactionOutPoint(params, i, Sha256Hash.wrap(getTxId().getBytes())));
            }
        }
        return lockedOutpoints.get(outputIndex);
    }

    public Coin getFundingAmount() {
        return assetLockPayload.getFundingAmount();
    }

    /**
     * Returns the credit burn identifier, which is the sha256(sha256(outpoint))
     */
    public Sha256Hash getIdentityId() {
        return getIdentityId(0);
    }

    public Sha256Hash getIdentityId(int outputIndex) {
        if(identityIds.isEmpty()) {
            assetLockPayload.getCreditOutputs().forEach(transactionOutput -> {
                try {
                    ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(36);
                    getLockedOutpoint(outputIndex).bitcoinSerialize(bos);
                    identityIds.add(Sha256Hash.twiceOf(bos.toByteArray()));

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return identityIds.get(0);
    }

    public ECKey getAssetLockPublicKey() {
        return getAssetLockPublicKey(0);
    }

    public ECKey getAssetLockPublicKey(int outputIndex) {
        return assetLockPublicKeys.get(outputIndex);
    }


    public KeyId getAssetLockPublicKeyId() {
        return getAssetLockPublicKeyId(0);
    }
    public KeyId getAssetLockPublicKeyId(int outputIndex) {
        if(assetLockPublicKeyIds.isEmpty()) {
            assetLockPayload.getCreditOutputs().forEach(transactionOutput -> assetLockPublicKeyIds.add(KeyId.fromBytes(ScriptPattern.extractHashFromP2PKH(assetLockPayload.getCreditOutputs().get(0).getScriptPubKey()))));
        }
        return assetLockPublicKeyIds.get(outputIndex);
    }

    public int getUsedDerivationPathIndex(int outputIndex) {
        ECKey key = getAssetLockPublicKey(0);
        if (key instanceof IDeterministicKey) {
            IDeterministicKey deterministicKey = (IDeterministicKey) key;
            return deterministicKey.getPath().get(deterministicKey.getDepth() - 1).num();
        }
        return -1;
    }

    public void addAssetLockPublicKey(ECKey assetLockPublicKey) {
        int index = assetLockPublicKeyIds.indexOf(KeyId.fromBytes(assetLockPublicKey.getPubKeyHash()));
        checkState(index != -1, "cannot find public key hash for " + assetLockPublicKey);
        assetLockPublicKeys.put(index, assetLockPublicKey);
    }

    /**
     * Determines if a transaction has one or more credit burn outputs
     * and therefore is a is credit funding transaction
     */
    public static boolean isAssetLockTransaction(Transaction tx) {
        return tx.getVersionShort() == SPECIAL_VERSION && tx.getType() == Type.TRANSACTION_ASSET_LOCK &&
                tx.getOutputs().stream().anyMatch(output -> ScriptPattern.isAssetLock(output.getScriptPubKey()));
    }

    /**
     * Determines the first output that is a credit burn output
     * or returns -1.
     */
    public long getAssetLockOutputIndex() {
        int outputCount = getOutputs().size();
        for (int i = 0; i < outputCount; ++i) {
            if (ScriptPattern.isAssetLock(getOutput(i).getScriptPubKey()))
                return i;
        }
        return -1;
    }
}
