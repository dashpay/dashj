package org.bitcoinj.core;

import com.google.common.collect.Lists;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.core.DarkCoinSystem.fMasterNode;
import static org.bitcoinj.core.SporkManager.SPORK_16_INSTANTSEND_AUTOLOCKS;
import static org.bitcoinj.core.SporkManager.SPORK_2_INSTANTSEND_ENABLED;
import static org.bitcoinj.core.SporkManager.SPORK_3_INSTANTSEND_BLOCK_FILTERING;

/**
 * Created by HashEngineering on 2/8/2015.
 */
public class InstantSend {
    private static final Logger log = LoggerFactory.getLogger(InstantSend.class);
    public static final int MIN_INSTANTSEND_PROTO_VERSION           = 70208;
    public static final int INSTANTSEND_TIMEOUT_SECONDS             = 65;
    public static final long INSTANTSEND_LOCK_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);


    ReentrantLock lock = Threading.lock("InstantSend");

    int cachedBlockHeight;

    public HashMap<Sha256Hash, Transaction> mapLockRequestAccepted;
    public HashMap<Sha256Hash, Transaction> mapLockRequestRejected;
    public HashMap<Sha256Hash, TransactionLockVote> mapTxLockVotes;
    public HashMap<Sha256Hash, TransactionLockVote> mapTxLockVotesOrphan;
    public HashMap<Sha256Hash, TransactionLockCandidate> mapTxLockCandidates;


    public HashMap<Sha256Hash, TransactionLock> mapTxLocks;
    public HashMap<TransactionOutPoint, Set<Sha256Hash>> mapVotedOutpoints; // utxo - tx hash set
    public HashMap<TransactionOutPoint, Sha256Hash> mapLockedOutpoints;
    public HashMap<TransactionOutPoint, Long> mapMasternodeOrphanVotes; //track votes with no tx for DOS

    public static int nInstantSendKeepLock = 24;
    int nCompleteTXLocks;

    //our internal stuff
    HashMap<Sha256Hash, Peer> mapAcceptedLockReq;

    public static int INSTANTX_SIGNATURES_REQUIRED = 6;
    public static int INSTANTX_SIGNATURES_TOTAL = 10;

    AbstractBlockChain blockChain;
    public void setBlockChain(AbstractBlockChain blockChain)
    {
        this.blockChain = blockChain;
        this.blockChain.addTransactionReceivedListener(transactionReceivedInBlockListener);
    }

    public void close() {
        this.blockChain.removeTransactionReceivedListener(transactionReceivedInBlockListener);
    }

    Context context;

    boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public InstantSend(Context context)
    {
        this.context = context;
        this.mapLockRequestAccepted = new HashMap<Sha256Hash, Transaction>();
        this.mapLockRequestRejected = new HashMap<Sha256Hash, Transaction>();
        this.mapTxLockVotes = new HashMap<Sha256Hash, TransactionLockVote>();
        this.mapTxLocks = new HashMap<Sha256Hash, TransactionLock>();
        this.mapMasternodeOrphanVotes = new HashMap<TransactionOutPoint, Long>();
        this.mapLockedOutpoints = new HashMap<TransactionOutPoint, Sha256Hash>();
        this.mapAcceptedLockReq = new HashMap<Sha256Hash, Peer>();

        mapTxLockCandidates = new HashMap<Sha256Hash, TransactionLockCandidate>();
        mapTxLockVotesOrphan = new HashMap<Sha256Hash, TransactionLockVote>();
        mapVotedOutpoints = new HashMap<TransactionOutPoint, Set<Sha256Hash>>();
    }

    //check if we need to vote on this transaction
    public void doConsensusVote(TransactionLockRequest tx, long blockHeight)
    {
        if(!fMasterNode) {
            log.warn("InstantX::DoConsensusVote - Not masternode\n");
            return;
        }
    }

    boolean canProcessInstantXMessages()
    {
        if(context.isLiteMode() && !context.allowInstantXinLiteMode()) return false; //disable all darksend/masternode related functionality
        if(!context.sporkManager.isSporkActive(SPORK_2_INSTANTSEND_ENABLED)) return false;
        if(!context.masternodeSync.isBlockchainSynced()) return false;

        return true;
    }

    //process consensus vote message
    public void processTransactionLockVoteMessage(Peer pfrom, TransactionLockVote vote) {

        if (!canProcessInstantXMessages() && pfrom.getPeerVersionMessage().clientVersion < MIN_INSTANTSEND_PROTO_VERSION)
            return;

        //Adding to known inventory in Dash Core, we will skip that.

        try {
            lock.lock();
            Sha256Hash voteHash = vote.getHash();

            if(mapTxLockVotes.containsKey(voteHash))
                return;

            mapTxLockVotes.put(voteHash, vote);

            processTxLockVote(pfrom, vote);

        }
        finally {
            lock.unlock();
        }
    }

    boolean isIXTXValid(Transaction txCollateral){
        if(txCollateral.getOutputs().size() < 1) return false;
        if(txCollateral.getLockTime() != 0)
        {
            log.info("instantsend - IsIXTXValid - Transaction is not final - {}", txCollateral.toString());
            return false;
        }

        Coin valueIn = Coin.ZERO;
        Coin valueOut = Coin.ZERO;
        boolean missingTx = false;

        for(TransactionOutput o: txCollateral.getOutputs())
            valueOut = valueOut.add(o.getValue());

        for(TransactionInput i : txCollateral.getInputs()) {

            Coin value = i.getValue();
            if(value == null)
                missingTx = true;
            else valueIn = valueIn.add(value);// += tx2.vout[i.prevout.n].nValue;
        }

        if(valueOut.isGreaterThan(Coin.valueOf((int) context.sporkManager.getSporkValue(SporkManager.SPORK_5_INSTANTSEND_MAX_VALUE), 0))){
            log.info("instantsend-IsIXTXValid - Transaction value too high - {}", txCollateral.toString());
            return false;
        }

        if(missingTx){
            log.info("instantsend-IsIXTXValid - Unknown inputs in IX transaction - {}", txCollateral.toString());
        /*
            This happens sometimes for an unknown reason, so we'll return that it's a valid transaction.
            If someone submits an invalid transaction it will be rejected by the network anyway and this isn't
            very common, but we don't want to block IX just because the client can't figure out the fee.
        */
            return true;
        }

        if(valueIn.subtract(valueOut).isLessThan(Coin.CENT)) {
            log.info("instantsend-IsIXTXValid - did not include enough fees in transaction {}\n{}", valueOut.subtract(valueIn).toFriendlyString(), txCollateral.toString());
            return false;
        }

        return true;
    }

    public int createNewLock(TransactionLockRequest tx)
    {
        //We don't have the ability to determine the age of the inputs.
        //We will assume that the other nodes have rejected the txlreq first
        int txAge = 6;

        /*
        TODO:  Get all wallet transactions, search each transaction for tx.getInputs()
           After finding, then getConfidence.getDepth and that is the age.

           InstantXCoinSelector ixcs = new InstantXCoinSelector(); can help a little
         */

        if(tx.getConfidence().getSource() == TransactionConfidence.Source.SELF)
        {


        }

    /*
        Use a blockheight newer than the input.
        This prevents attackers from using transaction mallibility to predict which masternodes
        they'll use.
    */

        int blockHeight = blockChain.getBestChainHeight() - txAge + 4; //(chainActive.Tip()->nHeight - nTxAge)+4;

        if (!mapTxLocks.containsKey(tx.getHash())){
            log.info("CreateNewLock - New Transaction Lock "+ tx.getHash().toString());

            TransactionLock newLock = new TransactionLock(
                    blockHeight,
                    (int)Utils.currentTimeSeconds()+(60*60), //locks expire after 15 minutes (6 confirmations)
                    (int)Utils.currentTimeSeconds()+(60*5),
                    tx.getHash());
            mapTxLocks.put(tx.getHash(), newLock);
        } else {
            //mapTxLocks.get(tx.getHash()).blockHeight = blockHeight; - the Core Client can do this, but we won't since we don't know the txAge.
            log.info("CreateNewLock - Transaction Lock Exists "+ tx.getHash().toString());
        }

        return blockHeight;
    }
    @Deprecated
    boolean checkForConflictingLocks(Transaction tx)
    {
    /*
        It's possible (very unlikely though) to get 2 conflicting transaction locks approved by the network.
        In that case, they will cancel each other out.

        Blocks could have been rejected during this time, which is OK. After they cancel out, the client will
        rescan the blocks and find they're acceptable and then take the chain with the most work.
    */
        if(tx.getInputs() == null)
            return false;

        for(TransactionInput in : tx.getInputs())
        {
        if(mapLockedOutpoints.containsKey(in.getOutpoint())){
            if(!mapLockedOutpoints.get(in.getOutpoint()).equals(tx.getHash())){
                log.info("InstantX::CheckForConflictingLocks - found two complete conflicting locks - removing both. "+ tx.getHash().toString() +" "+ mapLockedOutpoints.get(in.getOutpoint()).toString());
                if(mapTxLocks.containsKey(tx.getHash()))
                    mapTxLocks.get(tx.getHash()).expiration = (int)Utils.currentTimeSeconds();
                if(mapTxLocks.containsKey(mapLockedOutpoints.get(in.getOutpoint())))
                    mapTxLocks.get(mapLockedOutpoints.get(in.getOutpoint())).expiration = (int)Utils.currentTimeSeconds();
                return true;
            }
        }
    }

        return false;
    }

    public long getAverageVoteTime()
    {
        long total = 0;
        long count = 0;

        for(Map.Entry<TransactionOutPoint, Long> it : mapMasternodeOrphanVotes.entrySet()) {
            total += it.getValue();
            count++;
        }

        return total / count;
    }

    //received a consensus vote
    boolean processTxLockVote(Peer pnode, TransactionLockVote vote)
    {
        try {
            lock.lock();

            Sha256Hash txHash = vote.getTxHash();

            if(!vote.isValid(pnode))
            {
                // could be because of missing MN
                log.info("instantsend - CInstantSend::ProcessTxLockVote -- Vote is invalid, txid="+ txHash.toString());
                return false;
            }

            // relay valid, vote asap
            vote.relay();

            // Masternodes will sometimes propagate votes before the transaction is known to the client,
            // will actually process only after the lock request itself has arrived

            TransactionLockCandidate it = mapTxLockCandidates.get(txHash);
            if(it == null || it.txLockRequest == null) {
                if(!mapTxLockVotesOrphan.containsKey(vote.getHash())) {
                    // start timeout countdown after the very first vote
                    createEmptyTxLockCandidate(txHash);

                    mapTxLockVotesOrphan.put(vote.getHash(), vote);
                    log.info("instantsend--CInstantSend::ProcessTxLockVote -- Orphan vote: txid="+txHash.toString()+"  masternode="+vote.getOutpointMasternode().toString()+" new\n");
                    boolean fReprocess = true;

                    Transaction lockRequest = mapLockRequestAccepted.get(txHash);
                    if(lockRequest == null) {
                        lockRequest = mapLockRequestRejected.get(txHash);
                        if(lockRequest == null) {
                            // still too early, wait for tx lock request
                            fReprocess = false;
                        }
                    }
                    if(fReprocess && isEnoughOrphanVotesForTx(lockRequest)) {
                        // We have enough votes for corresponding lock to complete,
                        // tx lock request should already be received at this stage.
                        log.info("instantsend--CInstantSend::ProcessTxLockVote -- Found enough orphan votes, reprocessing Transaction Lock Request: txid="+ txHash.toString());
                        processTxLockRequest(lockRequest);
                        return true;
                    }
                } else {
                    log.info("instantsend--CInstantSend::ProcessTxLockVote -- Orphan vote: txid="+txHash.toString()+"  masternode="+vote.getOutpointMasternode().toString()+" seen");
                }

                // This tracks those messages and allows only the same rate as of the rest of the network
                // TODO: make sure this works good enough for multi-quorum

                long nMasternodeOrphanExpireTime = Utils.currentTimeSeconds() + 60*10; // keep time data for 10 minutes
                if(!mapMasternodeOrphanVotes.containsKey(vote.getOutpointMasternode())) {
                    mapMasternodeOrphanVotes.put(vote.getOutpointMasternode(), nMasternodeOrphanExpireTime);
                } else {
                    long nPrevOrphanVote = mapMasternodeOrphanVotes.get(vote.getOutpointMasternode());
                    if(nPrevOrphanVote > Utils.currentTimeSeconds() && nPrevOrphanVote > getAverageMasternodeOrphanVoteTime()) {
                        log.info("instantsend--CInstantSend::ProcessTxLockVote -- masternode is spamming orphan Transaction Lock Votes: txid="+txHash+"  masternode=", vote.getOutpointMasternode().toStringShort());
                        // Misbehaving(pfrom->id, 1);
                        return false;
                    }
                    // not spamming, refresh
                    mapMasternodeOrphanVotes.put(vote.getOutpointMasternode(),nMasternodeOrphanExpireTime);
                }

                return true;
            }

            TransactionLockCandidate txLockCandidate = it;
            if(txLockCandidate == null)
            {
                log.info("instantsend--CInstantSend::ProcessTxLockVote -- txLockCandidate does not exist for txid="+ txHash.toString());
                return false;
            }
            if(txLockCandidate.isTimedOut())
            {
                log.info("instantsend--CInstantSend::ProcessTxLockVote -- too late, Transaction Lock timed out, txid="+ txHash.toString());
                return false;
            }

            log.info("instantsend--CInstantSend::ProcessTxLockVote -- Transaction Lock Vote, txid="+txHash.toString());

            Set<Sha256Hash> it1 = mapVotedOutpoints.get(vote.getOutpoint());

            if(it1 != null) {
                for(Sha256Hash hash : it1)
                {
                    if(hash != txHash) {
                        // same outpoint was already voted to be locked by another tx lock request,
                        // let's see if it was the same masternode who voted on this outpoint
                        // for another tx lock request
                        TransactionLockCandidate it2 = mapTxLockCandidates.get(hash);
                        if(it2 != null && it2.hasMasternodeVoted(vote.getOutpoint(), vote.getOutpointMasternode())) {
                            // yes, it did, refuse to accept a vote to include the same outpoint in another tx
                            // from the same masternode.
                            // TODO: apply pose ban score to this masternode?
                            // NOTE: if we decide to apply pose ban score here, this vote must be relayed further
                            // to let all other nodes know about this node's misbehaviour and let them apply
                            // pose ban score too.
                            log.info("CInstantSend::ProcessTxLockVote -- masternode sent conflicting votes! "+ vote.getOutpointMasternode().toStringShort());

                            // mark both Lock Candidates as attacked, none of them should complete,
                            // or at least the new (current) one shouldn't even
                            // if the second one was already completed earlier
                            txLockCandidate.markOutpointAsAttacked(vote.getOutpoint());
                            it2.markOutpointAsAttacked(vote.getOutpoint());
                            // apply maximum PoSe ban score to this masternode i.e. PoSe-ban it instantly
                            context.masternodeManager.poSeBan(vote.getOutpointMasternode());
                            // NOTE: This vote must be relayed further to let all other nodes know about such
                            // misbehaviour of this masternode. This way they should also be able to construct
                            // conflicting lock and PoSe-ban this masternode.
                        }
                    }
                }
                // we have votes by other masternodes only (so far), let's continue and see who will win
                it1.add(txHash);
            } else {
                HashSet<Sha256Hash>  setHashes = new HashSet<Sha256Hash>();
                setHashes.add(txHash);
                mapVotedOutpoints.put(vote.getOutpoint(), setHashes);
            }

            if(!txLockCandidate.addVote(vote)) {
                // this should never happen
                return false;
            }

            int nSignatures = txLockCandidate.countVotes();
            int nSignaturesMax = TransactionLockRequest.getMaxSignatures(txLockCandidate.txLockRequest.getInputs().size());
            log.info("instantsend--CInstantSend::ProcessTxLockVote -- Transaction Lock signatures count: "+nSignatures+"/"+nSignaturesMax+", vote hash="+ vote.getHash());

            tryToFinalizeLockCandidate(txLockCandidate);

            return true;

        }
        finally {
            lock.unlock();
        }
    }

    public void acceptLockRequest(TransactionLockRequest txLockRequest)
    {
        try {
            lock.lock();

            mapLockRequestAccepted.put(txLockRequest.getHash(), txLockRequest);
        }
        finally {
            lock.unlock();
        }
    }

    public boolean processTxLockRequest(Transaction txLockRequest)
    {
        try {
            lock.lock();


            Sha256Hash txHash = txLockRequest.getHash();

            // Check to see if we conflict with existing completed lock
            for(TransactionInput txin : txLockRequest.getInputs())
            {
                Sha256Hash it = mapLockedOutpoints.get(txin.getOutpoint());
                if (it != null && it.equals(txLockRequest.getHash())) {
                    // Conflicting with complete lock, proceed to see if we should cancel them both
                    // (this could be the one we have but we don't want to try to lock it twice anyway)
                    log.info("CInstantSend::ProcessTxLockRequest -- WARNING: Found conflicting completed Transaction Lock, txid="+txLockRequest.getHash()+", completed lock txid="+
                            it.toString());
                }
            }

            // Check to see if there are votes for conflicting request,
            // if so - do not fail, just warn user

            for(TransactionInput txin : txLockRequest.getInputs()){
                Set<Sha256Hash> it = mapVotedOutpoints.get(txin.getOutpoint());
                if (it != null) {
                    for(Sha256Hash hash : it)
                    {
                        if (!hash.equals(txLockRequest.getHash())) {
                            log.info("instantsend--CInstantSend::ProcessTxLockRequest -- Double spend attempt! %s"+ txin.getOutpoint().toStringShort());
                            // do not fail here, let it go and see which one will get the votes to be locked
                        }
                    }
                }
            }

            if (!createTxLockCandidate(txLockRequest)) {
                // smth is not right
                log.info("CInstantSend::ProcessTxLockRequest -- CreateTxLockCandidate failed, txid="+ txHash.toString());
                return false;
            }
            log.info("CInstantSend::ProcessTxLockRequest -- accepted, txid="+ txHash.toString());

            TransactionLockCandidate txLockCandidate = mapTxLockCandidates.get(txHash);
            vote(txLockCandidate);
            processOrphanTxLockVotes();

            // Masternodes will sometimes propagate votes before the transaction is known to the client.
            // If this just happened - lock inputs, resolve conflicting locks, update transaction status
            // forcing external script notification.
            tryToFinalizeLockCandidate(txLockCandidate);

            return true;
        }
        finally {
            lock.unlock();
        }
    }

    void processOrphanTxLockVotes()
    {
        try {
            lock.lock();

            //std::map < uint256, CTxLockVote >::iterator it = mapTxLockVotesOrphan.begin();
            Iterator<Map.Entry<Sha256Hash, TransactionLockVote>> it = mapTxLockVotesOrphan.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<Sha256Hash, TransactionLockVote> st = it.next();
                if (processTxLockVote(null, st.getValue())) {
                    mapTxLockVotesOrphan.remove(st);
                } else {

                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    boolean createTxLockCandidate(Transaction txLockRequest)
    {
        if(txLockRequest instanceof TransactionLockRequest) {
            if (!((TransactionLockRequest)txLockRequest).isValid(cachedBlockHeight)) return false;
        } else {
            // The autolock spork must be activated and
            // regular transactions must have less or equal to 4 inputs
            if(!canAutoLock() || !txLockRequest.isSimple())
                return false;
        }

        try {
            lock.lock();
            Sha256Hash txHash = txLockRequest.getHash();

            TransactionLockCandidate lockCandidate = mapTxLockCandidates.get(txHash);
            if (lockCandidate == null) {
                log.info("CInstantSend::CreateTxLockCandidate -- new, txid="+ txHash.toString());

                TransactionLockCandidate txLockCandidate = new TransactionLockCandidate(context.getParams(), txLockRequest);
                // all inputs should already be checked by txLockRequest.IsValid() above, just use them now


                List<TransactionInput> inputs = Lists.reverse(txLockRequest.getInputs());
                for(TransactionInput txin : inputs)
                {
                    txLockCandidate.addOutPointLock(txin.getOutpoint());
                }
                mapTxLockCandidates.put(txHash, txLockCandidate);
            }
            else if (lockCandidate.txLockRequest == null)
            {
                lockCandidate.txLockRequest = txLockRequest;
                if(lockCandidate.isTimedOut())
                {
                    log.info("CInstantSend::CreateTxLockCandidate -- timed out, txid="+ txHash.toString());
                    return false;
                }
                log.info("CInstantSend::CreateTxLockCandidate -- update empty, txid="+ txHash.toString());

                List<TransactionInput> inputs = Lists.reverse(txLockRequest.getInputs());
                for(TransactionInput txin : inputs)
                {
                    lockCandidate.addOutPointLock(txin.getOutpoint());
                }
            }
            else {
                log.info("instantsend--CInstantSend::CreateTxLockCandidate -- seen, txid="+ txHash.toString());
            }

            return true;
        }
        finally {
            lock.unlock();
        }
    }

    void createEmptyTxLockCandidate(Sha256Hash txHash)
    {
        if(mapTxLockCandidates.containsKey(txHash))
            return;
        log.info("CInstantSend::CreateEmptyTxLockCandidate -- new, txid=%s"+ txHash.toString());
        TransactionLockRequest txLockRequest = null;
        mapTxLockCandidates.put(txHash, new TransactionLockCandidate(context.getParams(), txLockRequest));

    }

    void vote(TransactionLockCandidate txLockCandidate) {
        if (!fMasterNode) return;
        else return;
    }

    void tryToFinalizeLockCandidate(TransactionLockCandidate txLockCandidate)
    {
        if(!context.sporkManager.isSporkActive(SPORK_2_INSTANTSEND_ENABLED))
            return;

         try {
            lock.lock();

            Sha256Hash txHash = txLockCandidate.txLockRequest.getHash();
            if (txLockCandidate.isAllOutPointsReady() && !isLockedInstantSendTransaction(txHash)) {
                // we have enough votes now
                log.info("instantsend--CInstantSend::TryToFinalizeLockCandidate -- Transaction Lock is ready to complete, txid="+ txHash);
                if (resolveConflicts(txLockCandidate)) {
                    lockTransactionInputs(txLockCandidate);
                    updateLockedTransaction(txLockCandidate);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    boolean isLockedInstantSendTransaction(Sha256Hash txHash)
    {
        if(!context.allowInstantXinLiteMode() /*|| fLargeWorkForkFound || fLargeWorkInvalidChainFound */||
                !context.sporkManager.isSporkActive(SPORK_3_INSTANTSEND_BLOCK_FILTERING)) return false;

       try {
           lock.lock();


           // there must be a lock candidate
           TransactionLockCandidate lockCandidate = mapTxLockCandidates.get(txHash);
           if (lockCandidate == null) return false;

           // which should have outpoints
           if (lockCandidate.mapOutPointLocks.size() == 0) return false;

           // and all of these outputs must be included in mapLockedOutpoints with correct hash
           Iterator<Map.Entry<TransactionOutPoint, TransactionOutPointLock>> it = lockCandidate.mapOutPointLocks.entrySet().iterator();



           while(it.hasNext()) {
               Map.Entry<TransactionOutPoint, TransactionOutPointLock> tt = it.next();
               Sha256Hash hashLocked = getLockedOutPointTxHash(tt.getKey());

               if (hashLocked == null || hashLocked != txHash) return false;
           }

           return true;
       }
       finally {
           lock.unlock();
       }
    }

    Sha256Hash getLockedOutPointTxHash(TransactionOutPoint outpoint)
    {
       try {
           lock.lock();

           Sha256Hash it = mapLockedOutpoints.get(outpoint);
           if(it == null) return null;

           return it;
       } finally {
           lock.unlock();
       }
    }


    boolean isEnoughOrphanVotesForTx(Transaction txLockRequest)
    {
        // There could be a situation when we already have quite a lot of votes
        // but tx lock request still wasn't received. Let's scan through
        // orphan votes to check if this is the case.
        for(TransactionInput txin : txLockRequest.getInputs())
        {
            if(!isEnoughOrphanVotesForTxAndOutPoint(txLockRequest.getHash(), txin.getOutpoint())) {
                return false;
            }
        }
        return true;
    }
    boolean isEnoughOrphanVotesForTxAndOutPoint(Sha256Hash txHash, TransactionOutPoint outpoint)
    {
        // Scan orphan votes to check if this outpoint has enough orphan votes to be locked in some tx.
        try {
            lock.lock();

            int nCountVotes = 0;
            Iterator<Map.Entry<Sha256Hash, TransactionLockVote>> it = mapTxLockVotesOrphan.entrySet().iterator();



            while(it.hasNext()) {
                Map.Entry<Sha256Hash, TransactionLockVote> tl = it.next();

                if (tl.getValue().getTxHash().equals(txHash) && tl.getValue().getOutpoint() == outpoint) {
                    nCountVotes++;
                    if (nCountVotes >= TransactionOutPointLock.SIGNATURES_REQUIRED) {
                        return true;
                    }
                }
            }
            return false;
        }
        finally {
            lock.unlock();
        }
    }

    long getAverageMasternodeOrphanVoteTime()
    {
        try {
            lock.lock();

            // NOTE: should never actually call this function when mapMasternodeOrphanVotes is empty
            if (mapMasternodeOrphanVotes.size() == 0) return 0;

            Iterator<Map.Entry<TransactionOutPoint, Long>> it = mapMasternodeOrphanVotes.entrySet().iterator();


            long total = 0;
            while(it.hasNext()) {
                Map.Entry<TransactionOutPoint, Long> tl = it.next();
                total += tl.getValue();
            }

            return total / mapMasternodeOrphanVotes.size();
        }
        finally {
            lock.unlock();
        }
    }

    public boolean isTransactionLocked(Transaction tx)
    {
        TransactionLock txLock = mapTxLocks.get(tx.getHash());
        if(txLock != null)
        {
            return txLock.countSignatures() > INSTANTX_SIGNATURES_REQUIRED;
        }
        return false;
    }

    //TODO:  add clean up code
    void cleanTransactionLocksList()
    {
        //if(chainActive.Tip() == NULL) return;
        if(blockChain.getChainHead() == null)
            return;

        //std::map<uint256, CTransactionLock>::iterator it = mapTxLocks.begin();
        Iterator<Map.Entry<Sha256Hash, TransactionLock>> it = mapTxLocks.entrySet().iterator();



        while(it.hasNext()) {
            Map.Entry<Sha256Hash, TransactionLock> tl = it.next();

            if(Utils.currentTimeSeconds() > tl.getValue().expiration){ //keep them for an hour
                log.info("Removing old transaction lock {}", tl.getValue().txHash.toString());

                if(mapLockRequestAccepted.containsKey(tl.getValue().txHash)){
                    Transaction tx = mapLockRequestAccepted.get(tl.getValue().txHash);

                    //BOOST_FOREACH(const CTxIn& in, tx.vin)
                    for(TransactionInput in : tx.getInputs())
                        mapLockedOutpoints.remove(in.getOutpoint());

                    mapLockRequestAccepted.remove(tl.getValue().txHash);
                    mapLockRequestRejected.remove(tl.getValue().txHash);

                    //BOOST_FOREACH(CConsensusVote& v, it->second.vecConsensusVotes)
                    for(TransactionLockVote v : tl.getValue().vecConsensusVotes)
                        mapTxLockVotes.remove(v.getHash());

                    //Remove transaction confidence information, after 1 hour this should be a regular transaction
                    //tx.getConfidence().setIX(false);
                    //tx.getConfidence().setConsensusVotes(0);
                }
                it.remove();
            } else {

            }
        }

    }

    public void checkAndRemove()
    {
        if(!context.masternodeSync.isMasternodeListSynced()) return;

        try {
            lock.lock();

            Iterator<Map.Entry<Sha256Hash, TransactionLockCandidate>> itLockCandidate = mapTxLockCandidates.entrySet().iterator();


            // remove expired candidates
            while(itLockCandidate.hasNext()) {
                Map.Entry<Sha256Hash, TransactionLockCandidate> txLockCandidateEntry = itLockCandidate.next();

                TransactionLockCandidate txLockCandidate = txLockCandidateEntry.getValue();

                Sha256Hash txHash = txLockCandidate.getHash();
                if (txLockCandidate.isExpired(cachedBlockHeight)) {
                    log.info("CInstantSend::CheckAndRemove -- Removing expired Transaction Lock Candidate: txid="+ txHash);

                    Iterator<Map.Entry<TransactionOutPoint, TransactionOutPointLock>> itOutpointLock = txLockCandidate.mapOutPointLocks.entrySet().iterator();

                    while(itOutpointLock.hasNext())
                    {
                        TransactionOutPoint outpoint = itOutpointLock.next().getKey();
                        itOutpointLock.remove();
                        mapVotedOutpoints.remove(outpoint);
                    }
                    mapLockRequestAccepted.remove(txHash);
                    mapLockRequestRejected.remove(txHash);
                    itLockCandidate.remove();
                } else {
                    ;
                }
            }

            // remove expired votes
            Iterator<Map.Entry<Sha256Hash, TransactionLockVote>> itVote = mapTxLockVotes.entrySet().iterator();
            while (itVote.hasNext())
            {
                Map.Entry<Sha256Hash, TransactionLockVote> vote = itVote.next();
                if (vote.getValue().isExpired(cachedBlockHeight)) {
                    log.info("instantsend--CInstantSend::CheckAndRemove -- Removing expired vote: txid="+vote.getValue().getTxHash()+"  masternode=" + vote.getValue().getOutpointMasternode().toStringShort());
                    //mapTxLockVotes.remove(itVote);
                    itVote.remove();
                } else {
                    ;
                }
            }

            // remove expired orphan votes
            Iterator<Map.Entry<Sha256Hash, TransactionLockVote>> itOrphanVote = mapTxLockVotesOrphan.entrySet().iterator();
            while (itOrphanVote.hasNext())
            {
                Map.Entry<Sha256Hash, TransactionLockVote> vote = itOrphanVote.next();
                if (vote.getValue().isTimedOut()) {
                    log.info("instantsend--CInstantSend::CheckAndRemove -- Removing timed out orphan vote: txid="+vote.getValue().getTxHash()+"  masternode="+ vote.getValue().getOutpointMasternode().toStringShort());
                    //mapTxLockVotes.remove(vote.getKey());
                    itOrphanVote.remove();
                } else {
                    ;
                }
            }

            // remove expired masternode orphan votes (DOS protection)
            Iterator<Map.Entry<TransactionOutPoint, Long>> itMasternodeOrphan = mapMasternodeOrphanVotes.entrySet().iterator();
            while (itMasternodeOrphan.hasNext()) {
                Map.Entry<TransactionOutPoint, Long> masterNodeOrphan = itMasternodeOrphan.next();
                if (masterNodeOrphan.getValue() < Utils.currentTimeSeconds()) {
                    log.info("instantsend--CInstantSend::CheckAndRemove -- Removing expired orphan masternode vote: masternode="+
                            masterNodeOrphan.getKey().toString());
                    itMasternodeOrphan.remove();
                } else {
                    ;
                }
            }

            log.info("CInstantSend::CheckAndRemove -- "+ toString());
        }
        finally {
            lock.unlock();
        }
    }

    /**
     * Check whether the outgoing simple transactions were auto locked
     * within the specific time frame, if not set the IXType to TransactionConfidence.IXType.IX_LOCK_FAILED
     */
    public void notifyLockStatus()
    {
        for(Map.Entry<Sha256Hash, Transaction> entry : mapLockRequestAccepted.entrySet()) {
            Transaction transaction = entry.getValue();
            TransactionConfidence confidence = transaction.getConfidence();
            TransactionConfidence.IXType confidenceType = confidence.getIXType();
            if (confidenceType == TransactionConfidence.IXType.IX_REQUEST &&
                    confidence.getSentAt() != null) {
                long sentAt = confidence.getSentAt().getTime();
                long now = System.currentTimeMillis();
                long txSentMillisAgo = now - sentAt;
                if (txSentMillisAgo > INSTANTSEND_LOCK_TIMEOUT_MILLIS) {
                    confidence.setIXType(TransactionConfidence.IXType.IX_LOCK_FAILED);
                    confidence.queueListeners(TransactionConfidence.Listener.ChangeReason.IX_TYPE);
                }
            }
        }
    }

    public void updatedChainHead(StoredBlock chainHead)
    {
        cachedBlockHeight = chainHead.getHeight();
    }

    public void syncTransaction(Transaction tx, StoredBlock block)
    {
        // Update lock candidates and votes if corresponding tx confirmed
        // or went from confirmed to 0-confirmed or conflicted.

        if (tx.isCoinBase()) return;

        try {
            lock.lock();

            Sha256Hash txHash = tx.getHash();

            // When tx is 0-confirmed or conflicted, pblock is NULL and nHeightNew should be set to -1
            int nHeightNew = block != null ? block.getHeight() : -1;

            log.info("instantsend--CInstantSend::SyncTransaction -- txid="+txHash+" nHeightNew="+ nHeightNew);

            // Check lock candidates

            TransactionLockCandidate txLockCandidate = mapTxLockCandidates.get(txHash);
            if (txLockCandidate != null) {
                log.info("instantsend--CInstantSend::SyncTransaction -- txid="+txHash+" nHeightNew="+nHeightNew+" lock candidate updated");
                txLockCandidate.setConfirmedHeight(nHeightNew);
                // Loop through outpoint locks

                Iterator<Map.Entry<TransactionOutPoint, TransactionOutPointLock>> itOutpointLock = txLockCandidate.mapOutPointLocks.entrySet().iterator();
                while (itOutpointLock.hasNext()) {
                    // Check corresponding lock votes
                    Collection<TransactionLockVote> vVotes = itOutpointLock.next().getValue().getVotes();

                    for (TransactionLockVote vote: vVotes)
                    {
                        Sha256Hash nVoteHash = vote.getHash();
                        log.info("instantsend--CInstantSend::SyncTransaction -- txid="+txHash+" nHeightNew="+nHeightNew+" vote "+nVoteHash+" updated");
                        TransactionLockVote it = mapTxLockVotes.get(nVoteHash);
                        if (it != null) {
                            it.setConfirmedHeight(nHeightNew);
                        }
                    }
                }
            }

            // check orphan votes
            Iterator<Map.Entry<Sha256Hash, TransactionLockVote>> itOrphanVote = mapTxLockVotesOrphan.entrySet().iterator();
            while (itOrphanVote.hasNext()) {
                Map.Entry<Sha256Hash, TransactionLockVote> orphanVote = itOrphanVote.next();
                if (orphanVote.getValue().getTxHash().equals(txHash)) {
                    log.info("instantsend--CInstantSend::SyncTransaction -- txid="+txHash+" nHeightNew="+nHeightNew+" vote "+orphanVote.getKey()+" updated");
                    mapTxLockVotes.get(orphanVote.getKey()).setConfirmedHeight(nHeightNew);
                }
            }
        }
        finally {
            lock.unlock();
        }
    }

    public String toString() {
        try {
            lock.lock();
            return "Lock Candidates: "+mapTxLockCandidates.size()+", Votes "+ mapTxLockVotes.size();
        }
        finally {
            lock.unlock();
        }
    }

    public boolean isLockedIXTransaction(Sha256Hash txHash) {
        // there must be a successfully verified lock request...
        if (!mapLockRequestAccepted.containsKey(txHash)) return false;
        // ...and corresponding lock must have enough signatures
        TransactionLock tlock = mapTxLocks.get(txHash);
        return tlock != null && tlock.countSignatures() > INSTANTX_SIGNATURES_REQUIRED;
    }

    public int getTransactionLockSignatures(Sha256Hash txHash)
    {
        //if(fLargeWorkForkFound || fLargeWorkInvalidChainFound) return -2;
        if(!context.sporkManager.isSporkActive(SPORK_2_INSTANTSEND_ENABLED)) return -3;
        if(!context.allowInstantXinLiteMode()) return -1;

        TransactionLock tlock = mapTxLocks.get(txHash);
        if (tlock != null){
            return tlock.countSignatures();
        }

        return -1;
    }

    public boolean isTransactionLockTimedOut(Sha256Hash txHash)
    {
        if(!context.allowInstantXinLiteMode()) return false;
        try {
            lock.lock();

            TransactionLockCandidate lockCandidate = mapTxLockCandidates.get(txHash);
            if (lockCandidate != null) {
                return !lockCandidate.isAllOutPointsReady() &&
                        lockCandidate.isTimedOut();
            }

            return false;
        }
        finally {
            lock.unlock();
        }
    }



    void lockTransactionInputs(TransactionLockCandidate txLockCandidate) {

        if(!context.sporkManager.isSporkActive(SPORK_2_INSTANTSEND_ENABLED))
            return;

        try {
            lock.lock();


            Sha256Hash txHash = txLockCandidate.getHash();

            if (!txLockCandidate.isAllOutPointsReady()) return;

            Iterator<Map.Entry<TransactionOutPoint, TransactionOutPointLock>> it = txLockCandidate.mapOutPointLocks.entrySet().iterator();



            while(it.hasNext()) {
                Map.Entry<TransactionOutPoint, TransactionOutPointLock> tt = it.next();

                mapLockedOutpoints.put(tt.getKey(), txHash);
            }
            log.info("instantsend--CInstantSend::LockTransactionInputs -- done, txid="+ txHash);
        }finally {
            lock.unlock();
        }
    }

    boolean findConflictingLocks(Transaction tx)
    {
    /*
        It's possible (very unlikely though) to get 2 conflicting transaction locks approved by the network.
        In that case, they will cancel each other out.

        Blocks could have been rejected during this time, which is OK. After they cancel out, the client will
        rescan the blocks and find they're acceptable and then take the chain with the most work.
    */
        for(TransactionInput in : tx.getInputs()){
            if(mapLockedOutpoints.containsKey(in.getOutpoint())){
                if(mapLockedOutpoints.get(in.getOutpoint()) != tx.getHash()){
                    log.info("InstantX::FindConflictingLocks - found two complete conflicting locks - removing both. {} {}", tx.getHash().toString(), mapLockedOutpoints.get(in.getOutpoint()).toString());
                    if(mapTxLocks.containsKey(tx.getHash()))
                        mapTxLocks.get(tx.getHash()).expiration = (int)Utils.currentTimeSeconds();
                    if(mapTxLocks.containsKey(mapLockedOutpoints.get(in.getOutpoint())))
                        mapTxLocks.get(mapLockedOutpoints.get(in.getOutpoint())).expiration = (int)Utils.currentTimeSeconds();
                    return true;
                }
            }
        }

        return false;
    }

    boolean resolveConflicts(TransactionLockCandidate txLockCandidate) {

        try {
            lock.lock();

            Sha256Hash txHash = txLockCandidate.getHash();

            // make sure the lock is ready
            if (!txLockCandidate.isAllOutPointsReady()) return false;


            //LOCK(mempool.cs); // protect mempool.mapNextTx, mempool.mapTx

            for(TransactionInput txin : txLockCandidate.txLockRequest.getInputs())
            {
                Sha256Hash hashConflicting = getLockedOutPointTxHash(txin.getOutpoint());
                if (hashConflicting != null && txHash != hashConflicting) {
                    // completed lock which conflicts with another completed one?
                    // this means that majority of MNs in the quorum for this specific tx input are malicious!
                    TransactionLockCandidate lockCandidate = mapTxLockCandidates.get(txHash);
                    TransactionLockCandidate lockCandidateConflicting = mapTxLockCandidates.get(hashConflicting);
                    if(lockCandidate == null || lockCandidateConflicting == null) {
                        // safety check, should never really happen
                        log.info("CInstantSend::ResolveConflicts -- ERROR: Found conflicting completed Transaction Lock, but one of txLockCandidate-s is missing, txid=" +
                                txHash.toString() + " conflicting txid="+ hashConflicting.toString());
                        return false;
                    }
                    log.info("CInstantSend::ResolveConflicts -- WARNING: Found conflicting completed Transaction Lock, dropping both, txid="+
                            txHash.toString() + " conflicting txid="+  hashConflicting.toString());
                    Transaction txLockRequest = lockCandidate.txLockRequest;
                    Transaction txLockRequestConflicting = lockCandidateConflicting.txLockRequest;
                    lockCandidate.setConfirmedHeight(0); // expired
                    lockCandidateConflicting.setConfirmedHeight(0); // expired
                    checkAndRemove(); // clean up
                    // AlreadyHave should still return "true" for both of them
                    mapLockRequestRejected.put(txHash, txLockRequest);
                    mapLockRequestRejected.put(hashConflicting, txLockRequestConflicting);

                    // TODO: clean up mapLockRequestRejected later somehow
                    //       (not a big issue since we already PoSe ban malicious masternodes
                    //        and they won't be able to spam)
                    // TODO: ban all malicious masternodes permanently, do not accept anything from them, ever

                    // TODO: notify zmq+script about this double-spend attempt
                    //       and let merchant cancel/hold the order if it's not too late...

                    // can't do anything else, fallback to regular txes
                    return false;
                } /*else if (mempool.mapNextTx.count(txin.prevout)) {
                    //TODO:  Does this apply to us?  Can we check the confidence table?
                    // check if it's in mempool
                    hashConflicting = mempool.mapNextTx[txin.prevout].ptx->GetHash();
                    if(txHash == hashConflicting) continue; // matches current, not a conflict, skip to next txin
                    // conflicts with tx in mempool
                    log.info("CInstantSend::ResolveConflicts -- ERROR: Failed to complete Transaction Lock, conflicts with mempool, txid="+ txHash.toString());
                    return false;
                }*/
            } // FOREACH
            //TODO:  Update this as much as possible
            // No conflicts were found so far, check to see if it was already included in block
            /*CTransaction txTmp;
            uint256 hashBlock;
            if(GetTransaction(txHash, txTmp, Params().GetConsensus(), hashBlock, true) && hashBlock != uint256()) {
                LogPrint("instantsend", "CInstantSend::ResolveConflicts -- Done, %s is included in block %s\n", txHash.ToString(), hashBlock.ToString());
                return true;
            }
            // Not in block yet, make sure all its inputs are still unspent
            BOOST_FOREACH(const CTxIn& txin, txLockCandidate.txLockRequest.vin) {
                CCoins coins;
                if(!GetUTXOCoins(txin.prevout, coins)) {
                    // Not in UTXO anymore? A conflicting tx was mined while we were waiting for votes.
                    LogPrintf("CInstantSend::ResolveConflicts -- ERROR: Failed to find UTXO %s, can't complete Transaction Lock\n", txin.prevout.ToStringShort());
                    return false;
                }
            }*/

            log.info("instantsend--CInstantSend::ResolveConflicts -- Done, txid="+ txHash.toString());

            return true;
        }
        finally {
            lock.unlock();
        }
    }

    void updateLockedTransaction(TransactionLockCandidate txLockCandidate) {
        // there should be no conflicting locks
        try {
            lock.lock();

            Sha256Hash txHash = txLockCandidate.getHash();

            if(!isLockedInstantSendTransaction(txHash)) {
                txLockCandidate.txLockRequest.getConfidence().setIXType(TransactionConfidence.IXType.IX_REQUEST);
                return; // not a locked tx, do not update/notify
            }

            Transaction tx = txLockCandidate.txLockRequest;
            tx.getConfidence().setIXType(TransactionConfidence.IXType.IX_LOCKED);
            tx.getConfidence().queueListeners(TransactionConfidence.Listener.ChangeReason.IX_TYPE);

            log.info("instantsend--CInstantSend::UpdateLockedTransaction -- done, txid="+ txHash);

        }
        finally {
            lock.unlock();
        }
    }

    static public boolean canAutoLock() {
        if(Context.get().sporkManager != null)
            return Context.get().sporkManager.isSporkActive(SPORK_16_INSTANTSEND_AUTOLOCKS);
        else return false;
    }

    TransactionReceivedInBlockListener transactionReceivedInBlockListener = new TransactionReceivedInBlockListener() {
        @Override
        public void receiveFromBlock(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {

            // Call syncTransaction to update lock candidates and votes
            if(!mapTxLockCandidates.isEmpty() && blockType == AbstractBlockChain.NewBlockType.BEST_CHAIN) {
                syncTransaction(tx, block);
            }
        }

        @Override
        public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
            return false;
        }
    };
}

