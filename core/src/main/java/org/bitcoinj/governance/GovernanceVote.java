/*
 * Copyright 2014 Hash Engineering Solutions
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
package org.bitcoinj.governance;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.evolution.Masternode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static org.bitcoinj.governance.GovernanceVote.VoteSignal.VOTE_SIGNAL_ENDORSED;

//
// GovernanceVote - Allow a masternode node to vote and broadcast throughout the network
//

public class GovernanceVote extends ChildMessage implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(GovernanceVote.class);

    // INTENTION OF MASTERNODES REGARDING ITEM
    public enum VoteOutcome {
        VOTE_OUTCOME_NONE(0),
        VOTE_OUTCOME_YES(1),
        VOTE_OUTCOME_NO(2),
        VOTE_OUTCOME_ABSTAIN(3);

        final int value;
        VoteOutcome(int value) {
            this.value = value;
            getMappings().put(value, this);
        }

        private static final class MappingsHolder {
            static final java.util.HashMap<Integer, VoteOutcome> mappings = new java.util.HashMap<>();
        }

        private static java.util.HashMap<Integer, VoteOutcome> getMappings() {
            return MappingsHolder.mappings;
        }

        public int getValue() {
            return value;
        }

        public static VoteOutcome fromValue(int value) {
            return getMappings().get(value);
        }
    }


    // SIGNAL VARIOUS THINGS TO HAPPEN:
    public enum VoteSignal  {
        VOTE_SIGNAL_NONE(0),
        VOTE_SIGNAL_FUNDING(1), //   -- fund this object for it's stated amount
        VOTE_SIGNAL_VALID(2), //   -- this object checks out in sentinel engine
        VOTE_SIGNAL_DELETE(3), //   -- this object should be deleted from memory entirely
        VOTE_SIGNAL_ENDORSED(4), //   -- officially endorsed by the network somehow (delegation)
        VOTE_SIGNAL_NOOP1(5), // FOR FURTHER EXPANSION
        VOTE_SIGNAL_NOOP2(6),
        VOTE_SIGNAL_NOOP3(7),
        VOTE_SIGNAL_NOOP4(8),
        VOTE_SIGNAL_NOOP5(9),
        VOTE_SIGNAL_NOOP6(10),
        VOTE_SIGNAL_NOOP7(11),
        VOTE_SIGNAL_NOOP8(12),
        VOTE_SIGNAL_NOOP9(13),
        VOTE_SIGNAL_NOOP10(14),
        VOTE_SIGNAL_NOOP11(15),
        VOTE_SIGNAL_CUSTOM1(16), // SENTINEL CUSTOM ACTIONS
        VOTE_SIGNAL_CUSTOM2(17), //        16-35
        VOTE_SIGNAL_CUSTOM3(18),
        VOTE_SIGNAL_CUSTOM4(19),
        VOTE_SIGNAL_CUSTOM5(20),
        VOTE_SIGNAL_CUSTOM6(21),
        VOTE_SIGNAL_CUSTOM7(22),
        VOTE_SIGNAL_CUSTOM8(23),
        VOTE_SIGNAL_CUSTOM9(24),
        VOTE_SIGNAL_CUSTOM10(25),
        VOTE_SIGNAL_CUSTOM11(26),
        VOTE_SIGNAL_CUSTOM12(27),
        VOTE_SIGNAL_CUSTOM13(28),
        VOTE_SIGNAL_CUSTOM14(29),
        VOTE_SIGNAL_CUSTOM15(30),
        VOTE_SIGNAL_CUSTOM16(31),
        VOTE_SIGNAL_CUSTOM17(32),
        VOTE_SIGNAL_CUSTOM18(33),
        VOTE_SIGNAL_CUSTOM19(34),
        VOTE_SIGNAL_CUSTOM20(35);

        final int value;
        VoteSignal(int value) {
            this.value = value;
            getMappings().put(value, this);
        }

        private static final class MappingsHolder {
            static final java.util.HashMap<Integer, VoteSignal> mappings = new java.util.HashMap<>();
        }

        private static java.util.HashMap<Integer, VoteSignal> getMappings() {
            return MappingsHolder.mappings;
        }

        public int getValue() {
            return value;
        }

        public static VoteSignal fromValue(int value) {
            return getMappings().get(value);
        }
    }


    static final int MAX_SUPPORTED_VOTE_SIGNAL = VOTE_SIGNAL_ENDORSED.getValue();


    Context context;
    
    
    
    private boolean fValid; //if the vote is currently valid / counted
    private boolean fSynced; //if we've sent this to our peers
    private int nVoteSignal; // see VOTE_ACTIONS above
    private TransactionOutPoint masternodeOutpoint;
    private Sha256Hash nParentHash;
    private int nVoteOutcome; // see VOTE_OUTCOMES above
    private long nTime;
    private MasternodeSignature vchSig;

    /* memory only */
    Sha256Hash hash;

    @Nullable GovernanceVoteConfidence confidence;

    public GovernanceVoteConfidence getConfidence() {
        return getConfidence(context);
    }

    /**
     * Returns the confidence object for this transaction from the {@link org.bitcoinj.core.TxConfidenceTable}
     * referenced by the given {@link Context}.
     */
    public GovernanceVoteConfidence getConfidence(Context context) {
        return getConfidence(context.getVoteConfidenceTable());
    }

    /**
     * Returns the confidence object for this transaction from the {@link org.bitcoinj.core.TxConfidenceTable}
     */
    public GovernanceVoteConfidence getConfidence(VoteConfidenceTable table) {
        if (confidence == null)
            confidence = table.getOrCreate(getHash()) ;
        return confidence;
    }

    public final boolean isValid() {
        return fValid;
    }

    public final boolean isSynced() {
        return fSynced;
    }

    public final long getTimestamp() {
        return nTime;
    }

    public final VoteSignal getSignal() {
        return VoteSignal.fromValue(nVoteSignal);
    }

    public final VoteOutcome getOutcome() {
        return VoteOutcome.fromValue(nVoteOutcome);
    }

    public final Sha256Hash getParentHash() {
        return nParentHash;
    }

    public final void setTime(long nTimeIn) {
        nTime = nTimeIn;
    }

    public final void setSignature(MasternodeSignature vchSigIn) {
        vchSig = new MasternodeSignature(vchSigIn);
    }

    public final String getVoteString() {
        return getOutcome().toString();
    }

    public final TransactionOutPoint getMasternodeOutpoint() { return masternodeOutpoint; }

    /**
     *   GetHash()
     *
     *   GET UNIQUE HASH WITH DETERMINISTIC VALUE OF THIS SPECIFIC VOTE
     */

    public final Sha256Hash getHash() {
        return hash;
    }

    public void updateHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            new TransactionInput(params, null, new byte[0], masternodeOutpoint).bitcoinSerialize(bos);
            bos.write(nParentHash.getReversedBytes());
            Utils.uint32ToByteStreamLE(nVoteSignal, bos);
            Utils.uint32ToByteStreamLE(nVoteOutcome, bos);
            Utils.int64ToByteStreamLE(nTime, bos);
            hash = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch(IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    public Sha256Hash getSignatureHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            masternodeOutpoint.bitcoinSerialize(bos);
            bos.write(nParentHash.getReversedBytes());
            Utils.uint32ToByteStreamLE(nVoteSignal, bos);
            Utils.uint32ToByteStreamLE(nVoteOutcome, bos);
            Utils.int64ToByteStreamLE(nTime, bos);
            return Sha256Hash.twiceOf(bos.toByteArray());
        } catch(IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    public String toString() {
        return masternodeOutpoint.toString() + ":"  + nTime + ":" +
                GovernanceVoting.convertOutcomeToString(getOutcome()) + ":" +
                GovernanceVoting.convertSignalToString(getSignal());
    }

    /**
     *   GetTypeHash()
     *
     *   GET HASH WITH DETERMINISTIC VALUE OF MASTERNODE-VIN/PARENT-HASH/VOTE-SIGNAL
     *
     *   This hash collides with previous masternode votes when they update their votes on governance objects.
     *   With 12.1 there's various types of votes (funding, valid, delete, etc), so this is the deterministic hash
     *   that will collide with the previous vote and allow the system to update.
     *
     *   --
     *
     *   We do not include an outcome, because that can change when a masternode updates their vote from yes to no
     *   on funding a specific project for example.
     *   We do not include a time because it will be updated each time the vote is updated, changing the hash
     */
    public Sha256Hash getTypeHash() {
        // CALCULATE HOW TO STORE VOTE IN governance.mapVotes
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            masternodeOutpoint.bitcoinSerialize(bos);
            bos.write(nParentHash.getReversedBytes());
            Utils.uint32ToByteStreamLE(nVoteSignal, bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch(IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }


    public GovernanceVote(NetworkParameters params, byte[] payload, int cursor)
    {
        super(params, payload, cursor);
        context = Context.get();
    }

    public GovernanceVote(NetworkParameters params, TransactionOutPoint outpoint, Sha256Hash parentHash, VoteSignal eVoteSignal, VoteOutcome eVoteOutcome) {
        super(params);
        context = Context.get();
        masternodeOutpoint = outpoint;
        nParentHash = parentHash;
        nVoteSignal = eVoteSignal.getValue();
        nVoteOutcome = eVoteOutcome.getValue();
        fValid = true;
        fSynced = false;
        nTime = Utils.currentTimeSeconds();
        updateHash();
    }

    protected static int calcLength(byte[] buf, int offset) {
        int cursor = offset;
        return cursor - offset;
    }
    @Override
    protected void parse() throws ProtocolException {
        masternodeOutpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += masternodeOutpoint.getMessageSize();
        nParentHash = readHash();
        nVoteOutcome = (int)readUint32();
        nVoteSignal = (int)readUint32();
        nTime = readInt64();
        vchSig = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig.getMessageSize();
        updateHash();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        masternodeOutpoint.bitcoinSerialize(stream);
        stream.write(nParentHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(nVoteSignal, stream);
        Utils.uint32ToByteStreamLE(nVoteOutcome, stream);
        Utils.int64ToByteStreamLE(nTime, stream);
        vchSig.bitcoinSerialize(stream);
    }

    public boolean isValid(boolean useVotingKey) {
        if (nTime > Utils.currentTimeSeconds() + (60 * 60)) {
            log.info("gobject--CGovernanceVote::IsValid -- vote is too far ahead of current time - {} - nTime {} - Max Time {}", getHash().toString(), nTime, Utils.currentTimeSeconds() + (60 * 60));
            return false;
        }

        // support up to 50 actions (implemented in sentinel)
        if (nVoteSignal > MAX_SUPPORTED_VOTE_SIGNAL) {
            log.info("gobject--CGovernanceVote::IsValid -- Client attempted to vote on invalid signal({}) - {}", nVoteSignal, getHash().toString());
            return false;
        }

        // 0=none, 1=yes, 2=no, 3=abstain. Beyond that reject votes
        if (nVoteOutcome > 3) {
            log.info("gobject--CGovernanceVote::IsValid -- Client attempted to vote on invalid outcome({}) - {}", nVoteSignal, getHash().toString());
            return false;
        }

        if(context.masternodeSync.syncFlags.contains(MasternodeSync.SYNC_FLAGS.SYNC_MASTERNODE_LIST)) {
            Masternode dmn = context.masternodeListManager.getListAtChainTip().getMNByCollateral(masternodeOutpoint);
            if (dmn == null) {
                log.info("gobject--CGovernanceVote::IsValid -- Unknown Masternode - {}", masternodeOutpoint.toStringShort());
                return false;
            }

            if (useVotingKey) {
                return checkSignature(dmn.getKeyIdVoting());
            } else {
                return checkSignature(dmn.getPubKeyOperator());
            }
        }

        return true;
    }

    void relay () {
        // Do not relay until fully synced
        if(!context.masternodeSync.isSynced()) {
            log.info("gobject--CGovernanceVote::Relay -- won't relay until fully synced");
            return;
        }

        //InventoryItem inv = new InventoryItem(InventoryItem.Type.GovernanceObjectVote, getHash());
        //context.peerGroup.relayInventory(inv, MIN_GOVERNANCE_PEER_PROTO_VERSION);

        //TODO:Leave Here for now, but later this should be a part of the user interface
        //context.peerGroup.broadcastGovernanceVote(this);
    }

    public boolean sign(ECKey keyMasternode, PublicKey pubKeyMasternode) {
        StringBuilder strError = new StringBuilder();

        String strMessage = masternodeOutpoint.toStringShort() + "|" + nParentHash.toString() + "|" + nVoteSignal + "|" + nVoteOutcome + "|" + nTime;

        if ((vchSig = MessageSigner.signMessage(strMessage, keyMasternode)) == null) {
            log.error("CGovernanceVote::Sign -- SignMessage() failed");
            return false;
        }

        if (!MessageSigner.verifyMessage(pubKeyMasternode, vchSig, strMessage, strError)) {
            log.error("CGovernanceVote::Sign -- VerifyMessage() failed, error: {}", strError);
            return false;
        }

        return true;
    }
    public boolean checkSignature(PublicKey pubKeyMasternode) {
        StringBuilder strError = new StringBuilder();

        String strMessage = masternodeOutpoint.toStringShort() + "|" + nParentHash.toString() + "|" + nVoteSignal + "|" + nVoteOutcome + "|" + nTime;

        if (!MessageSigner.verifyMessage(pubKeyMasternode, vchSig, strMessage, strError)) {
            log.info("CGovernanceVote::IsValid -- VerifyMessage() failed, error: {}", strError);
            return false;
        }

        return true;
    }

    public boolean checkSignature(KeyId pubKeyMasternode) {
        StringBuilder strError = new StringBuilder();

        String strMessage = masternodeOutpoint.toStringShort() + "|" + nParentHash.toString() + "|" + nVoteSignal + "|" + nVoteOutcome + "|" + nTime;

        if (!MessageSigner.verifyMessage(pubKeyMasternode, vchSig, strMessage, strError)) {
            log.info("gobject--CGovernanceVote::IsValid -- VerifyMessage() failed, error: {}", strError);
            return false;
        }

        return true;
    }

    public boolean checkSignature(BLSPublicKey pubKey)
    {
        Sha256Hash hash = getSignatureHash();
        BLSSignature sig = new BLSSignature(vchSig.getBytes());
        if (!sig.verifyInsecure(pubKey, hash)) {
            log.info("GovernanceVote-CheckSignature -- VerifyInsecure() failed\n");
            return false;
        }
        return true;
    }

}
