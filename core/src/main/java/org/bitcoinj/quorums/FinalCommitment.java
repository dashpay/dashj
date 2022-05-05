/*
 * Copyright 2019 Dash Core Group
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

import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSLazySignature;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.SpecialTxPayload;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

public class FinalCommitment extends SpecialTxPayload {
    public static final int CURRENT_VERSION = 1;
    public static final int INDEXED_QUORUM_VERSION = 2;

    private static final Logger log = LoggerFactory.getLogger(FinalCommitment.class);


    int llmqType; //short
    Sha256Hash quorumHash;
    int quorumIndex; //uint16
    ArrayList<Boolean> signers;
    ArrayList<Boolean> validMembers;

    BLSPublicKey quorumPublicKey;
    Sha256Hash quorumVvecHash;

    BLSSignature quorumSignature;
    BLSSignature membersSignature;

    public FinalCommitment(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public FinalCommitment(NetworkParameters params, LLMQParameters llmqParameters, Sha256Hash quorumHash) {
        super(0);
        this.llmqType = llmqParameters.type.getValue();
        this.quorumHash = quorumHash;
        this.signers = new ArrayList<Boolean>(llmqParameters.size);
        this.validMembers = new ArrayList<Boolean>(llmqParameters.size);
    }

    public FinalCommitment(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public FinalCommitment(NetworkParameters params, int version,
                           int llmqType, Sha256Hash quorumHash,
                           int quorumIndex, int signersCount, byte [] signers, int validMembersCount, byte [] validMembers,
                           byte [] quorumPublicKey, Sha256Hash quorumVvecHash, BLSLazySignature signature, BLSLazySignature membersSignature) {
        super(version);
        this.llmqType = llmqType;
        this.quorumHash = quorumHash;
        this.quorumIndex = quorumIndex;
        this.quorumPublicKey = new BLSPublicKey(params, quorumPublicKey, 0);
        this.quorumVvecHash = quorumVvecHash;
        this.quorumSignature = signature.getSignature();
        this.membersSignature = membersSignature.getSignature();
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();

        llmqType = readBytes(1)[0];
        quorumHash = readHash();
        if (version >= INDEXED_QUORUM_VERSION) {
            quorumIndex = readUint16();
        } else {
            quorumIndex = 0;
        }
        signers = readBooleanArrayList();
        validMembers = readBooleanArrayList();

        quorumPublicKey = new BLSPublicKey(params, payload, cursor);
        cursor += quorumPublicKey.getMessageSize();

        quorumVvecHash = readHash();

        quorumSignature = new BLSSignature(params, payload, cursor);
        cursor += quorumSignature.getMessageSize();

        membersSignature = new BLSSignature(params, payload, cursor);
        cursor += membersSignature.getMessageSize();

        length = cursor - offset;
    }

    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException{
        super.bitcoinSerializeToStream(stream);
        stream.write(llmqType);

        stream.write(quorumHash.getReversedBytes());
        if (version >= INDEXED_QUORUM_VERSION) {
            Utils.uint16ToByteStreamLE(quorumIndex, stream);
        }
        Utils.booleanArrayListToStream(signers, stream);
        Utils.booleanArrayListToStream(validMembers, stream);

        quorumPublicKey.bitcoinSerialize(stream);
        stream.write(quorumVvecHash.getReversedBytes());
        quorumSignature.bitcoinSerialize(stream);
        membersSignature.bitcoinSerialize(stream);
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        return String.format("FinalCommitment(version=%d, llmqType=%d, quorumHash=%s, signers=%d, validMembers=%d, quorumPublicKey=%s",
                getVersion(), llmqType, quorumHash, countSigners(), countValidMembers(),
                quorumPublicKey.toString());
    }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_UNKNOWN;
    }

    @Override
    public String getName() {
        return "finalCommitment";
    }

    @Override
    public JSONObject toJson() {
        JSONObject result = new JSONObject();

        result.append("version", getVersion());

        return result;
    }

    public int countSigners() {
        return Collections.frequency(signers, Boolean.TRUE);
    }

    public int countValidMembers() {
        return Collections.frequency(validMembers, Boolean.TRUE);
    }

    public boolean verify(ArrayList<Masternode> members, boolean checkSigs) {
        if(getVersion() == 0 || getVersion() > INDEXED_QUORUM_VERSION)
            return false;

        if(!params.getLlmqs().containsKey(LLMQParameters.LLMQType.fromValue(llmqType))) {
            log.error("invalid llmqType " + llmqType);
            return false;
        }

        LLMQParameters llmqParameters = params.getLlmqs().get(LLMQParameters.LLMQType.fromValue(llmqType));

        if(!verifySizes(llmqParameters))
            return false;

        if(countValidMembers() < llmqParameters.minSize) {
            log.error("invalid validMembers count. validMembersCount={} < {}", countValidMembers(), llmqParameters.minSize);
            return false;
        }
        if (countSigners() < llmqParameters.minSize) {
            log.error("invalid signers count. signersCount={} < {}", countSigners(), llmqParameters.minSize);
            return false;
        }
        if (!quorumPublicKey.isValid()) {
            log.error("invalid quorumPublicKey");
            return false;
        }
        if (quorumVvecHash.equals(Sha256Hash.ZERO_HASH)) {
            log.error("invalid quorumVvecHash");
            return false;
        }
        if (!membersSignature.isValid()) {
            log.error("invalid membersSig");
            return false;
        }
        if (!quorumSignature.isValid()) {
            log.error("invalid quorumSig");
            return false;
        }

        for (int i = members.size(); i < llmqParameters.size; i++) {
            if (validMembers.get(i)) {
                log.error("invalid validMembers bitset. bit {} should not be set", i);
                return false;
            }
            if (signers.get(i)) {
                log.error("invalid signers bitset. bit {} should not be set", i);
                return false;
            }
        }

        // sigs are only checked when the block is processed
        if (checkSigs) {
            Sha256Hash commitmentHash = LLMQUtils.buildCommitmentHash(llmqParameters.type, quorumHash, validMembers, quorumPublicKey, quorumVvecHash);

            ArrayList<BLSPublicKey> memberPubKeys = Lists.newArrayList();
            for (int i = 0; i < members.size(); i++) {
                if (!signers.get(i)) {
                    continue;
                }
                memberPubKeys.add(members.get(i).getPubKeyOperator());
            }

            if (!membersSignature.verifySecureAggregated(memberPubKeys, commitmentHash)) {
                log.error("invalid aggregated members signature");
                return false;
            }

            Context.get().signingManager.logSignature("QUORUM", quorumPublicKey, commitmentHash, quorumSignature);

            if(Context.get().masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.BLS_SIGNATURES)) {
                if (!quorumSignature.verifyInsecure(quorumPublicKey, commitmentHash)) {
                    log.error("invalid quorum signature");
                    return false;
                }
            }
        }

        return true;
    }

    public boolean isNull()  {
        if (countSigners() > 0 ||
            countValidMembers() > 0) {
            return false;
        }
        if (quorumPublicKey.isValid() ||
                !quorumVvecHash.isZero() ||
                membersSignature.isValid() ||
                quorumSignature.isValid()) {
            return false;
        }
        return true;
    }

    public boolean verifyNull()
    {
        if (params.getLlmqs().containsKey(LLMQParameters.LLMQType.fromValue(llmqType))) {
            log.error("invalid llmqType={}", llmqType);
            return false;
        }
        LLMQParameters llmqParameters = params.getLlmqs().get(LLMQParameters.LLMQType.fromValue(llmqType));

        if (!isNull() || !verifySizes(llmqParameters)) {
            return false;
        }

        return true;
    }

    public boolean verifySizes(LLMQParameters llmqParameters) {
        if(signers.size() != llmqParameters.size) {
            log.error("invalid signers.size: {} != {}", signers.size(), llmqParameters.size);
            return false;
        }
        if(validMembers.size() != llmqParameters.size) {
            log.error("invalid validMembers.size: {} != {}", validMembers.size(), llmqParameters.size);
            return false;
        }
        return true;
    }

    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public Sha256Hash getQuorumHash() {
        return quorumHash;
    }

    public int getLlmqType() {
        return llmqType;
    }

    public BLSPublicKey getQuorumPublicKey() {
        return quorumPublicKey;
    }

    public Sha256Hash getQuorumVvecHash() {
        return quorumVvecHash;
    }

    public BLSSignature getMembersSignature() {
        return membersSignature;
    }

    public BLSSignature getQuorumSignature() {
        return quorumSignature;
    }

    public ArrayList<Boolean> getSigners() {
        return signers;
    }

    public ArrayList<Boolean> getValidMembers() {
        return validMembers;
    }
}
