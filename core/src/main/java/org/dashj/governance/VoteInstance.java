package org.bitcoinj.governance;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 5/24/2018.
 */
public class VoteInstance extends Message {

    public GovernanceVote.VoteOutcome eOutcome;
    public long nTime;
    public long nCreationTime;

    public VoteInstance(NetworkParameters params) {
        super(params);
        eOutcome = GovernanceVote.VoteOutcome.VOTE_OUTCOME_NONE;
    }

    public VoteInstance(NetworkParameters params, GovernanceVote.VoteOutcome eOutcomeIn, long nTimeIn, long nCreationTimeIn) {
        super(params);
        this.eOutcome = eOutcomeIn;
        this.nTime = nTimeIn;
        this.nCreationTime = nCreationTimeIn;
    }

    public VoteInstance(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    protected void parse() throws ProtocolException {
        eOutcome = GovernanceVote.VoteOutcome.fromValue((int)readUint32());
        nTime = readInt64();
        nCreationTime = readInt64();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(eOutcome.getValue(), stream);
        Utils.int64ToByteStreamLE(nTime, stream);
        Utils.int64ToByteStreamLE(nCreationTime, stream);
    }

    public String toString() {
        return eOutcome.toString();
    }
}
