package org.bitcoinj.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.bitcoinj.core.InstantSend.INSTANTSEND_TIMEOUT_SECONDS;

/**
 * Created by Eric on 2/7/2017.
 */
public class TransactionLockCandidate {
    NetworkParameters params;
    int confirmedHeight;
    long timeCreated;

    public Transaction txLockRequest;
    public HashMap<TransactionOutPoint, TransactionOutPointLock> mapOutPointLocks;

    public TransactionLockCandidate(NetworkParameters params, Transaction txLockRequest)
    {
        this.params = params;
        this.confirmedHeight = -1;
        timeCreated = Utils.currentTimeSeconds();
        this.txLockRequest = txLockRequest;
        mapOutPointLocks = new HashMap<TransactionOutPoint, TransactionOutPointLock>();
    }

    public Sha256Hash getHash() { return txLockRequest != null ? txLockRequest.getHash() : Sha256Hash.ZERO_HASH; }

    public boolean hasMasternodeVoted(TransactionOutPoint outpoint, TransactionOutPoint outpointMasternode)
    {
        TransactionOutPointLock it = mapOutPointLocks.get(outpoint);
        return it != null && it.hasMasternodeVoted(outpointMasternode);
    }

    public boolean addVote(TransactionLockVote vote)
    {
        TransactionOutPointLock it = mapOutPointLocks.get(vote.getOutpoint());
        if(it == null) return false;
        return it.addVote(vote);
    }

    public int countVotes()
    {
        // Note: do NOT use vote count to figure out if tx is locked, use IsAllOutPointsReady() instead
        int nCountVotes = 0;

        Iterator<Map.Entry<TransactionOutPoint, TransactionOutPointLock>> it = mapOutPointLocks.entrySet().iterator();

        while(it.hasNext()) {
            Map.Entry<TransactionOutPoint, TransactionOutPointLock> tt = it.next();

            nCountVotes += tt.getValue().countVotes();
        }
        return nCountVotes;
    }

    public boolean isAllOutPointsReady()
    {
        if(mapOutPointLocks.size() == 0) return false;

        Iterator<Map.Entry<TransactionOutPoint, TransactionOutPointLock>> it = mapOutPointLocks.entrySet().iterator();

        while(it.hasNext()) {
            Map.Entry<TransactionOutPoint, TransactionOutPointLock> tt = it.next();
            if(!tt.getValue().isReady()) return false;
        }
        return true;
    }
    public void addOutPointLock(TransactionOutPoint outpoint)
    {
        mapOutPointLocks.put(outpoint, new TransactionOutPointLock(params, outpoint));
    }
    public void setConfirmedHeight(int confirmedHeight) { this.confirmedHeight = confirmedHeight; }
    public boolean isExpired(int height)
    {
        // Locks and votes expire nInstantSendKeepLock blocks after the block corresponding tx was included into.
        return (confirmedHeight != -1) && (height - confirmedHeight > params.getInstantSendKeepLock());
    }

    public boolean isTimedOut()
    {
        return Utils.currentTimeSeconds() - timeCreated > INSTANTSEND_TIMEOUT_SECONDS;
    }
    public void markOutpointAsAttacked(TransactionOutPoint outPoint)
    {
        TransactionOutPointLock lock = mapOutPointLocks.get(outPoint);
        if(lock != null)
            lock.markAsAttacked();
    }
}
