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
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.SpecialTxPayload;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;

public class FinalCommitment extends SpecialTxPayload {
    @Deprecated
    public static final int CURRENT_VERSION = 1;
    @Deprecated
    public static final int INDEXED_QUORUM_VERSION = 2;

    public static final int LEGACY_BLS_NON_INDEXED_QUORUM_VERSION = 1;
    public static final int LEGACY_BLS_INDEXED_QUORUM_VERSION = 2;
    public static final int BASIC_BLS_NON_INDEXED_QUORUM_VERSION = 3;
    public static final int BASIC_BLS_INDEXED_QUORUM_VERSION = 4;
    public static final int MAX_VERSION = BASIC_BLS_INDEXED_QUORUM_VERSION;

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
        int quorumSize = LLMQParameters.fromType(LLMQParameters.LLMQType.fromValue(llmqType)).size;
        this.signers = Utils.booleanArrayList(quorumSize, signers);
        this.validMembers = Utils.booleanArrayList(quorumSize, validMembers);
        this.quorumPublicKey = new BLSPublicKey(params, quorumPublicKey, 0, isLegacy());
        this.quorumVvecHash = quorumVvecHash;
        this.quorumSignature = signature.getSignature();
        this.membersSignature = membersSignature.getSignature();
        length = 1 + 32 +
                (isIndexed() ? 2 : 0) +
                VarInt.sizeOf(quorumSize) * 2 +
                signers.length + validMembers.length + quorumPublicKey.length + 32 + signature.getMessageSize() +
                membersSignature.getMessageSize();
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();

        llmqType = readBytes(1)[0];
        quorumHash = readHash();
        if (isIndexed()) {
            quorumIndex = readUint16();
        } else {
            quorumIndex = 0;
        }
        signers = readBooleanArrayList();
        validMembers = readBooleanArrayList();

        quorumPublicKey = new BLSPublicKey(params, payload, cursor, isLegacy());
        cursor += quorumPublicKey.getMessageSize();

        quorumVvecHash = readHash();

        quorumSignature = new BLSSignature(params, payload, cursor, isLegacy());
        cursor += quorumSignature.getMessageSize();

        membersSignature = new BLSSignature(params, payload, cursor, isLegacy());
        cursor += membersSignature.getMessageSize();

        length = cursor - offset;
    }

    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException{
        bitcoinSerializeToStream(stream, isLegacy());
    }

    protected void bitcoinSerializeToStream(OutputStream stream, boolean legacy) throws IOException{
        super.bitcoinSerializeToStream(stream);
        stream.write(llmqType);

        stream.write(quorumHash.getReversedBytes());
        if (isIndexed()) {
            Utils.uint16ToByteStreamLE(quorumIndex, stream);
        }
        Utils.booleanArrayListToStream(signers, stream);
        Utils.booleanArrayListToStream(validMembers, stream);

        quorumPublicKey.bitcoinSerialize(stream, legacy);
        log.info("quorumPublicKey: {}", quorumPublicKey.toStringHex(isLegacy()));
        stream.write(quorumVvecHash.getReversedBytes());
        quorumSignature.bitcoinSerialize(stream, legacy);
        membersSignature.bitcoinSerialize(stream, legacy);
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

    /**
     *
     * @return
     *
     *     {
     *       version: 1,
     *       llmqType: 101,
     *       quorumHash: '00000235acaad85cb429d3d3738380fdbad54a62c778243329ac0e85e9fe6246',
     *       quorumIndex: 0,
     *       signersCount: 11,
     *       signers: 'df0f',
     *       validMembersCount: 12,
     *       validMembers: 'ff0f',
     *       quorumPublicKey: '171454d87dbed06c1d21d015360520bf597aa0680aad9634ca26531fe7d562db1359287ec15f6aef7f95d958d1b6053f',
     *       quorumVvecHash: 'cf1cbb60e77248fb069849bd4dae3e008c1be6bb3ef8f443e740f2bcd46e2740',
     *       quorumSig: '0025d7a0ce8af1e9aa973b5377aa126ec64575cac9e8a76523dc8392d54a5a86ee2a2c77e76e1a3a58da7481171199b5155e700869948472dbec15c83d21386f8e975231e80edf675e47c7dbdddfb500c30e2f464156396cb1736ae6a97f67fc',
     *       membersSig: '06cf0c148ada4f3edf7e4f6fd7c85f9583917c69ebe2623604a62a4623017d0b407af2b862c1863bcd07ae983af2876316fdf8ca089fec414db735627ca8299433d5cfcbcb381745231190009e47e2542f2038f251ad530c8f3eb7ac1d768cd8',
     *     }
     */

    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.put("version", getVersion());
        result.put("llmqType", getLlmqType());
        result.put("quorumHash", quorumHash.toString());
        result.put("quorumIndex", quorumIndex);
        result.put("signersCount", signers.size());

        String signersBytes = "00";
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Utils.booleanArrayListToStreamWithOutSize(signers, stream);
            signersBytes = Utils.HEX.encode(stream.toByteArray());
        } catch (IOException x) {
            //swallow
        }


        result.put("signers", signersBytes);
        result.put("validMembersCount", validMembers.size());

        String validMemberBytes = "00";
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Utils.booleanArrayListToStreamWithOutSize(validMembers, stream);
            validMemberBytes = Utils.HEX.encode(stream.toByteArray());
        } catch (IOException x) {
            //swallow
        }
        result.put("validMembers", validMemberBytes);
        result.put("quorumPublicKey", quorumPublicKey);
        result.put("quorumVvecHash", quorumVvecHash);
        result.put("quorumSig", quorumSignature);
        result.put("membersSig", membersSignature);
        return result;
    }

    public int countSigners() {
        return Collections.frequency(signers, Boolean.TRUE);
    }

    public int countValidMembers() {
        return Collections.frequency(validMembers, Boolean.TRUE);
    }

    public boolean verify(StoredBlock block, ArrayList<Masternode> members, boolean checkSigs) {
        int expectedVersion = LEGACY_BLS_NON_INDEXED_QUORUM_VERSION;
        if (LLMQUtils.isQuorumRotationEnabled(block, params, LLMQParameters.LLMQType.fromValue(llmqType))) {
            expectedVersion = params.isBasicBLSSchemeActive(block.getHeight()) ? BASIC_BLS_INDEXED_QUORUM_VERSION : LEGACY_BLS_INDEXED_QUORUM_VERSION;
        } else {
            expectedVersion = params.isBasicBLSSchemeActive(block.getHeight()) ? BASIC_BLS_NON_INDEXED_QUORUM_VERSION : LEGACY_BLS_NON_INDEXED_QUORUM_VERSION;
        }
        if(getVersion() == 0 || getVersion() != expectedVersion)
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

    public boolean isIndexed() {
        return version == LEGACY_BLS_INDEXED_QUORUM_VERSION || version == BASIC_BLS_INDEXED_QUORUM_VERSION;
    }

    public boolean isLegacy() {
        return version == LEGACY_BLS_INDEXED_QUORUM_VERSION || version == LEGACY_BLS_NON_INDEXED_QUORUM_VERSION;
    }

    public int getLlmqTypeInt() {
        return llmqType;
    }

    public LLMQParameters.LLMQType getLlmqType() {
        return LLMQParameters.LLMQType.fromValue(llmqType);
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

    @Override
    public boolean equals(Object o) {
        if (o instanceof FinalCommitment) {
            FinalCommitment fc = (FinalCommitment) o;
            if (version == fc.version &&
                    llmqType == fc.llmqType &&
                    quorumHash.equals(fc.quorumHash) &&
                    quorumIndex == fc.quorumIndex &&
                    countSigners() == fc.countSigners() &&
                    countValidMembers() == fc.countValidMembers() &&
                    signers.equals(fc.signers) &&
                    validMembers.equals(fc.validMembers) &&
                    quorumPublicKey.equals(fc.quorumPublicKey) &&
                    quorumVvecHash.equals(fc.quorumVvecHash) &&
                    quorumSignature.equals(fc.quorumSignature) &&
                    membersSignature.equals(fc.membersSignature)
            ) {
                return true;
            }
        }
        return false;
    }
}
