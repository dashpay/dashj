package org.bitcoinj.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * Created by Eric on 2/7/2017.
 */
public class TransactionOutPointLock { //COutPointLock

    public static final int SIGNATURES_REQUIRED        = 6;
    public static final int SIGNATURES_TOTAL           = 10;

    TransactionOutPoint outpoint;
    HashMap<TransactionOutPoint, TransactionLockVote> mapMasternodeVotes;
    boolean attacked = false;

    TransactionOutPointLock(NetworkParameters params, TransactionOutPoint outpoint)
    {
        this.outpoint = new TransactionOutPoint(params, outpoint.getIndex(), outpoint.getHash());
        mapMasternodeVotes = new HashMap<TransactionOutPoint, TransactionLockVote>(10);
    }

    public TransactionOutPoint getOutpoint() { return outpoint; }

    public boolean hasMasternodeVoted(TransactionOutPoint outpointMasternodeIn)
    {
        return mapMasternodeVotes.containsKey(outpointMasternodeIn);
    }

    public boolean addVote(TransactionLockVote vote)
    {
        if(mapMasternodeVotes.containsKey(vote.getOutpointMasternode()))
            return false;
        mapMasternodeVotes.put(vote.getOutpointMasternode(), vote);
        return true;
    }

    public Collection<TransactionLockVote> getVotes()
    {
        return mapMasternodeVotes.values();
    }

    public int countVotes() { return attacked ? 0 : mapMasternodeVotes.size(); }

    public boolean isReady() {
        return !attacked && countVotes() >= SIGNATURES_REQUIRED;
    }

    void markAsAttacked() { attacked = true; }
}
