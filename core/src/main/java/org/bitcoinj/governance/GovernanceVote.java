/**
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

//
// CGovernanceVote - Allow a masternode node to vote and broadcast throughout the network
//

public class GovernanceVote extends ChildMessage implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(GovernanceVote.class);

    // INTENTION OF MASTERNODES REGARDING ITEM
    enum VoteOutcome {
        VOTE_OUTCOME_NONE(0),
        VOTE_OUTCOME_YES(1),
        VOTE_OUTCOME_NO(2),
        VOTE_OUTCOME_ABSTAIN(3);

        int value;
        VoteOutcome(int value) {
            this.value = value;
            getMappings().put(value, this);
        }

        private static java.util.HashMap<Integer, VoteOutcome> mappings;
        private static java.util.HashMap<Integer, VoteOutcome> getMappings() {
            if (mappings == null) {
                synchronized (VoteOutcome.class) {
                    if (mappings == null) {
                        mappings = new java.util.HashMap<Integer, VoteOutcome>();
                    }
                }
            }
            return mappings;
        }

        public int getValue() {
            return value;
        }

        public static VoteOutcome fromValue(int value) {
            return getMappings().get(value);
        }
    };


    // SIGNAL VARIOUS THINGS TO HAPPEN:
    enum VoteSignal  {
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

        int value;
        VoteSignal(int value) {
            this.value = value;
            getMappings().put(value, this);
        }

        private static java.util.HashMap<Integer, VoteSignal> mappings;
        private static java.util.HashMap<Integer, VoteSignal> getMappings() {
            if (mappings == null) {
                synchronized (VoteSignal.class) {
                    if (mappings == null) {
                        mappings = new java.util.HashMap<Integer, VoteSignal>();
                    }
                }
            }
            return mappings;
        }

        public int getValue() {
            return value;
        }

        public static VoteSignal fromValue(int value) {
            return getMappings().get(value);
        }
    };




//C++ TO JAVA CONVERTER TODO TASK: Java has no concept of a 'friend' function:
//ORIGINAL LINE: friend boolean operator ==(const CGovernanceVote& vote1, const CGovernanceVote& vote2);
//C++ TO JAVA CONVERTER TODO TASK: The implementation of the following method could not be found:
//	boolean operator ==(CGovernanceVote vote1, CGovernanceVote vote2);

//C++ TO JAVA CONVERTER TODO TASK: Java has no concept of a 'friend' function:
//ORIGINAL LINE: friend boolean operator <(const CGovernanceVote& vote1, const CGovernanceVote& vote2);
//C++ TO JAVA CONVERTER TODO TASK: The implementation of the following method could not be found:
//	boolean operator <(CGovernanceVote vote1, CGovernanceVote vote2);

    private boolean fValid; //if the vote is currently valid / counted
    private boolean fSynced; //if we've sent this to our peers
    private int nVoteSignal; // see VOTE_ACTIONS above
    private TransactionInput vinMasternode;
    private Sha256Hash nParentHash;
    private int nVoteOutcome; // see VOTE_OUTCOMES above
    private long nTime;
    private MasternodeSignature vchSig;

//C++ TO JAVA CONVERTER TODO TASK: The implementation of the following method could not be found:
//	CGovernanceVote();
//C++ TO JAVA CONVERTER TODO TASK: The implementation of the following method could not be found:
//	CGovernanceVote(COutPoint outpointMasternodeIn, Sha256Hash nParentHashIn, vote_signal_enum_t eVoteSignalIn, vote_outcome_enum_t eVoteOutcomeIn);

    //C++ TO JAVA CONVERTER WARNING: 'const' methods are not available in Java:
//ORIGINAL LINE: boolean IsValid() const
    public final boolean isValid() {
        return fValid;
    }

    //C++ TO JAVA CONVERTER WARNING: 'const' methods are not available in Java:
//ORIGINAL LINE: boolean IsSynced() const
    public final boolean isSynced() {
        return fSynced;
    }

    //C++ TO JAVA CONVERTER WARNING: 'const' methods are not available in Java:
//ORIGINAL LINE: long GetTimestamp() const
    public final long getTimestamp() {
        return nTime;
    }

    //C++ TO JAVA CONVERTER WARNING: 'const' methods are not available in Java:
//ORIGINAL LINE: vote_signal_enum_t GetSignal() const
    public final VoteSignal getSignal() {
        return VoteSignal.fromValue(nVoteSignal);
    }

    //C++ TO JAVA CONVERTER WARNING: 'const' methods are not available in Java:
//ORIGINAL LINE: vote_outcome_enum_t GetOutcome() const
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

    public final TransactionOutPoint getMasternodeOutpoint() {
        return vinMasternode.getOutpoint();
    }

    /**
     *   GetHash()
     *
     *   GET UNIQUE HASH WITH DETERMINISTIC VALUE OF THIS SPECIFIC VOTE
     */

    public final Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            vinMasternode.bitcoinSerialize(bos);
            bos.write(nParentHash.getReversedBytes());
            Utils.uint32ToByteStreamLE(nVoteSignal, bos);
            Utils.uint32ToByteStreamLE(nVoteOutcome, bos);
            Utils.int64ToByteStreamLE(nTime, bos);
            return Sha256Hash.twiceOf(bos.toByteArray());
        } catch(IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    public final String toString() {
        return vinMasternode.toString() + ":"  + nTime + ":" +  getOutcome() + ":" + getSignal();

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
    public final Sha256Hash getTypeHash() {
        // CALCULATE HOW TO STORE VOTE IN governance.mapVotes
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            vinMasternode.bitcoinSerialize(bos);
            bos.write(nParentHash.getReversedBytes());
            Utils.uint32ToByteStreamLE(nVoteSignal, bos);
            return Sha256Hash.twiceOf(bos.toByteArray());
        } catch(IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }


    GovernanceVote(NetworkParameters params, byte[] payload, int cursor)
    {
        super(params, payload, cursor);
    }

    protected static int calcLength(byte[] buf, int offset) {
        int cursor = offset;
        return cursor - offset;
    }
    @Override
    protected void parse() throws ProtocolException {
        vinMasternode = new TransactionInput(params, null, payload, cursor);
        cursor += vinMasternode.getMessageSize();
        nParentHash = readHash();
        nVoteOutcome = (int)readUint32();
        nVoteSignal = (int)readUint32();
        nTime = readInt64();
        vchSig = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        vinMasternode.bitcoinSerialize(stream);
        stream.write(nParentHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(nVoteSignal, stream);
        Utils.uint32ToByteStreamLE(nVoteOutcome, stream);
        Utils.int64ToByteStreamLE(nTime, stream);
        vchSig.bitcoinSerialize(stream);
    }
}
