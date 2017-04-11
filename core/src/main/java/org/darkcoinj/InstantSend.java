package org.darkcoinj;

import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.core.DarkCoinSystem.fMasterNode;

/**
 * Created by Eric on 2/8/2015.
 */
public class InstantSend {
    private static final Logger log = LoggerFactory.getLogger(InstantSend.class);
    public static final int MIN_INSTANTSEND_PROTO_VERSION = 70206;
    private static final int ORPHAN_VOTE_SECONDS            = 60;

    ReentrantLock lock = Threading.lock("InstantSend");

    StoredBlock currentBlock;

    public HashMap<Sha256Hash, TransactionLockRequest> mapLockRequestAccepted;
    public HashMap<Sha256Hash, TransactionLockRequest> mapLockRequestRejected;
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

    /*
    At 15 signatures, 1/2 of the masternode network can be owned by
    one party without comprimising the security of InstantX
    (1000/2150.0)**10 = 0.00047382219560689856
    (1000/2900.0)**10 = 2.3769498616783657e-05

    ### getting 5 of 10 signatures w/ 1000 nodes of 2900
    (1000/2900.0)**5 = 0.004875397277841433
    */

    public static int INSTANTX_SIGNATURES_REQUIRED = 6;
    public static int INSTANTX_SIGNATURES_TOTAL = 10;

    AbstractBlockChain blockChain;
    public void setBlockChain(AbstractBlockChain blockChain)
    {
        this.blockChain = blockChain;
    }
    DarkCoinSystem system;
    MasterNodeSystem masterNodes;
    Context context;

    //static InstantSend instantXSystem;

    boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /*public static InstantSend get(AbstractBlockChain blockChain)
    {
        if(instantXSystem == null)
            instantXSystem = new InstantSend(blockChain);

        return instantXSystem;
    }*/

    public InstantSend(Context context)
    {
        this.context = context;
        this.mapLockRequestAccepted = new HashMap<Sha256Hash, TransactionLockRequest>();
        this.mapLockRequestRejected = new HashMap<Sha256Hash, TransactionLockRequest>();
        this.mapTxLockVotes = new HashMap<Sha256Hash, TransactionLockVote>();
        this.mapTxLocks = new HashMap<Sha256Hash, TransactionLock>();
        this.mapMasternodeOrphanVotes = new HashMap<TransactionOutPoint, Long>();
        this.mapLockedOutpoints = new HashMap<TransactionOutPoint, Sha256Hash>();
        this.mapAcceptedLockReq = new HashMap<Sha256Hash, Peer>();

        mapTxLockCandidates = new HashMap<Sha256Hash, TransactionLockCandidate>();
        mapTxLockVotesOrphan = new HashMap<Sha256Hash, TransactionLockVote>();
        mapVotedOutpoints = new HashMap<TransactionOutPoint, Set<Sha256Hash>>();

        masterNodes = MasterNodeSystem.get();


    }

    /*public InstantSend(DarkCoinSystem system)
    {
        this.system = system;
        blockChain = system.blockChain;
        //mapTxLocks = new Map<Sha256Hash, TransactionLock>();

    }*/

    /*public void processMessageInstantX(Peer from, Message m)
    {

    }*/

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
        if(!context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTSEND_ENABLED)) return false;
        if(!context.masternodeSync.isBlockchainSynced()) return false;

        return true;
    }

    /*public void processTransactionLockRequest(Peer pfrom, TransactionLockRequest tx) {
        if (!canProcessInstantXMessages())
            return;
        //LogPrintf("ProcessMessageInstantX::ix\n");


        //CInv inv(MSG_TXLOCK_REQUEST, tx.GetHash());
        //pfrom->AddInventoryKnown(inv);

        if (mapLockRequestAccepted.containsKey(tx.getHash()) || mapLockRequestRejected.containsKey(tx.getHash())) {
            return;
        }

        if (!isIXTXValid(tx)) {
            return;
        }

        //BOOST_FOREACH(const CTxOut o, tx.vout)
        for (TransactionOutput o : tx.getOutputs()) {
            // IX supports normal scripts and unspendable scripts (used in DS collateral and Budget collateral).
            // TODO: Look into other script types that are normal and can be included
            if (!o.getScriptPubKey().isSentToAddress() && !o.getScriptPubKey().isUnspendable()) {
                log.info("ProcessMessageInstantX::ix - Invalid Script {}", tx.toString());
                return;
            }
        }


        //CValidationState state;

        //TODO:  How to handle this?



        mapAcceptedLockReq.put(tx.getHash(), pfrom);
    }
    public void processTransactionLockRequestAccepted(TransactionLockRequest tx)
    {

        int nBlockHeight = createNewLock(tx);
        boolean fMissingInputs = false;

        boolean fAccepted = mapAcceptedLockReq.containsKey(tx.getHash());

        if (fAccepted)
        {
            Peer pfrom = mapAcceptedLockReq.get(tx.getHash());
            //RelayInv(inv);
            mapAcceptedLockReq.remove(tx.getHash());


            doConsensusVote(tx, nBlockHeight);

            mapLockRequestAccepted.put(tx.getHash(), tx);

            log.info("ProcessMessageInstantX::ix - Transaction Lock Request: {} {} : accepted {}",
                    pfrom.getAddress().toString(), pfrom.getPeerVersionMessage().subVer,
                    tx.getHash().toString()
            );

            tx.getConfidence().setIXType(TransactionConfidence.IXType.IX_REQUEST);

            // Masternodes will sometimes propagate votes before the transaction is known to the client.
            // If this just happened - update transaction status, try forcing external script notification,
            // lock inputs and resolve conflicting locks
            if(isLockedIXTransaction(tx.getHash())) {
                updateLockedTransaction(tx, true);
                lockTransactionInputs(tx);
                resolveConflicts(tx);
            }

            return;

        } else {
            mapLockRequestRejected.put(tx.getHash(), tx);

            // can we get the conflicting transaction as proof?

            log.info("ProcessMessageInstantX::ix - Transaction Lock Request: rejected {}",

                    tx.getHash().toString()
            );

            lockTransactionInputs(tx);
            resolveConflicts(tx);

            return;
        }
    }
*/
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


        /*if(mapTxLockVotes.containsKey(vote.getHash())){
            return;
        }

        mapTxLockVotes.put(vote.getHash(), vote);


        if(processTxLockVote(pfrom, vote)){
            //Spam/Dos protection

            //    Masternodes will sometimes propagate votes before the transaction is known to the client.
            //    This tracks those messages and allows it at the same rate of the rest of the network, if
            //    a peer violates it, it will simply be ignored

            if(!mapLockRequestAccepted.containsKey(vote.txHash) && !mapLockRequestRejected.containsKey(vote.txHash)){
                if(!mapMasternodeOrphanVotes.containsKey(vote.vinMasternode.getOutpoint().getHash())){
                    mapMasternodeOrphanVotes.put(vote.vinMasternode.getOutpoint().getHash(), Utils.currentTimeSeconds()+(60*10));
                }

                if(mapMasternodeOrphanVotes.get(vote.vinMasternode.getOutpoint().getHash()) > Utils.currentTimeSeconds() &&
                        mapMasternodeOrphanVotes.get(vote.vinMasternode.getOutpoint().getHash()) - getAverageVoteTime() > 60*10){
                    log.info("ProcessMessageInstantX::ix - masternode is spamming transaction votes: {} {}",
                            vote.vinMasternode.toString(),
                            vote.txHash.toString()
                    );
                    return;
                } else {
                    mapMasternodeOrphanVotes.put(vote.vinMasternode.getOutpoint().getHash(), Utils.currentTimeSeconds()+(60*10));
                }
            }
            //RelayInv(inv);
        }
*/
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

        //BOOST_FOREACH(const CTxOut o, txCollateral.vout)
        for(TransactionOutput o: txCollateral.getOutputs())
            valueOut = valueOut.add(o.getValue());



        //BOOST_FOREACH(const CTxIn i, txCollateral.vin)
        for(TransactionInput i : txCollateral.getInputs()){

            //CTransaction tx2;
            //uint256 hash;
            //if(GetTransaction(i.prevout.hash, tx2, hash, true)){
                //if(tx2.vout.size() > i.prevout.n) {
            Coin value = i.getValue();
            if(value == null)
                missingTx = true;
            else valueIn = valueIn.add(value);// += tx2.vout[i.prevout.n].nValue;
                //}
            /*} else{
                missingTx = true;
            }*/
        }

        if(valueOut.isGreaterThan(Coin.valueOf((int) context.sporkManager.getSporkValue(SporkManager.SPORK_5_INSTANTSEND_MAX_VALUE), 0))){
            log.info("instantsend-IsIXTXValid - Transaction value too high - {}\n", txCollateral.toString());
            return false;
        }

        if(missingTx){
            log.info("instantsend-IsIXTXValid - Unknown inputs in IX transaction - {}\n", txCollateral.toString());
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
        Wallet wallet;
        Iterator<WalletTransaction> wtx = wallet.getWalletTransactions();

        wtx.next().getTransaction().getHash().equals(tx.getInputs())

        *
        //BOOST_REVERSE_FOREACH(CTxIn i, tx.vin)
        /*for(TransactionInput i :tx.getInputs())
        {
        nTxAge = GetInputAge(i);
        if(nTxAge < 6)
        {
            LogPrintf("CreateNewLock - Transaction not found / too new: %d / %s\n", nTxAge, tx.GetHash().ToString().c_str());
            return 0;
        }
        }*/

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
        //std::map<uint256, int64_t>::iterator it = mapMasternodeOrphanVotes.begin();
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
        //Since we don't have access to the blockchain, we will not calculate the rankings.
        // if n = -1, then masternodes are loaded, but this masternode cannot be found
        // if n = -2, then the block hash cannot be found in the Block Store
        // if n = -3, then Lite Mode is ON - we will not verify any thing.
        try {
            lock.lock();

            Sha256Hash txHash = vote.getTxHash();

            if(!vote.isValid(pnode))
            {
                // could be because of missing MN
                log.info("instantsend - CInstantSend::ProcessTxLockVote -- Vote is invalid, txid="+ txHash.toString());
                return false;
            }

            // Masternodes will sometimes propagate votes before the transaction is known to the client,
            // will actually process only after the lock request itself has arrived

            TransactionLockCandidate it = mapTxLockCandidates.get(txHash);
            if(!mapTxLockCandidates.containsKey(txHash)) {
                if(!mapTxLockVotesOrphan.containsKey(vote.getHash())) {
                    mapTxLockVotesOrphan.put(vote.getHash(), vote);
                    log.info("instantsend--CInstantSend::ProcessTxLockVote -- Orphan vote: txid="+txHash.toString()+"  masternode="+vote.getOutpointMasternode().toString()+" new\n");
                    boolean fReprocess = true;

                    TransactionLockRequest lockRequest = mapLockRequestAccepted.get(txHash);
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

            log.info("instantsend--CInstantSend::ProcessTxLockVote -- Transaction Lock Vote, txid="+txHash.toString());

            Set<Sha256Hash> it1 = mapVotedOutpoints.get(vote.getOutpoint());

            if(it1 != null) {
                for(Sha256Hash hash : it1)
                {
                    if(hash != txHash) {
                        // same outpoint was already voted to be locked by another tx lock request,
                        // find out if the same mn voted on this outpoint before
                        TransactionLockCandidate it2 = mapTxLockCandidates.get(hash);
                        if(it2.hasMasternodeVoted(vote.getOutpoint(), vote.getOutpointMasternode())) {
                            // yes, it did, refuse to accept a vote to include the same outpoint in another tx
                            // from the same masternode.
                            // TODO: apply pose ban score to this masternode?
                            // NOTE: if we decide to apply pose ban score here, this vote must be relayed further
                            // to let all other nodes know about this node's misbehaviour and let them apply
                            // pose ban score too.
                            log.info("CInstantSend::ProcessTxLockVote -- masternode sent conflicting votes! "+ vote.getOutpointMasternode().toStringShort());
                            return false;
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

            TransactionLockCandidate txLockCandidate = it;

            if(!txLockCandidate.addVote(vote)) {
                // this should never happen
                return false;
            }

            int nSignatures = txLockCandidate.countVotes();
            int nSignaturesMax = txLockCandidate.txLockRequest.getMaxSignatures();
            log.info("instantsend--CInstantSend::ProcessTxLockVote -- Transaction Lock signatures count: "+nSignatures+"/"+nSignaturesMax+", vote hash="+ vote.getHash());

            tryToFinalizeLockCandidate(txLockCandidate);

            //vote.relay();

            return true;

        }
        finally {
            lock.unlock();
        }

        /*int n = context.masternodeManager.getMasternodeRank(vote.vinMasternode, vote.blockHeight, MIN_INSTANTX_PROTO_VERSION, true);

        Masternode pmn = context.masternodeManager.find(vote.vinMasternode);
        if(pmn != null)
            log.info("instantsend-InstantX::ProcessConsensusVote - Masternode ADDR {} {}", pmn.address.toString(), n);

        if(n == -1)
        {
            //can be caused by past versions trying to vote with an invalid protocol
            log.info("instantsend - InstantX::ProcessConsensusVote - Unknown Masternode - requesting...");
            context.masternodeManager.askForMN(pnode, vote.vinMasternode);
            return false;
        }

        if(n == -2 || n == -3)
        {
            //We can't determine the hash for blockHeight, but we will proceed anyways;
        }
        else if(n > INSTANTX_SIGNATURES_TOTAL)
        {
            //we have enough information to determine the masternode rank, we will make sure it is correct or we will return false.
            log.info("instantsend-InstantX::ProcessConsensusVote - Masternode not in the top {} ({}) - {}", INSTANTX_SIGNATURES_TOTAL, n, vote.getHash().toString());
            return false;
        } else
            log.info("instantsend-InstantX::ProcessConsensusVote - Masternode is the top {} ({}) - {}", INSTANTX_SIGNATURES_TOTAL, n, vote.getHash());


        if(n != -3 && !vote.isValid()) {
            log.info("InstantX::ProcessConsensusVote - Signature invalid");
            // don't ban, it could just be a non-synced masternode
            context.masternodeManager.askForMN(pnode, vote.vinMasternode);
            return false;
        }

        if (!mapTxLocks.containsKey(vote.txHash)){
            log.info("InstantX::ProcessConsensusVote - New Transaction Lock {} !\n", vote.txHash.toString());

            TransactionLock newLock = new TransactionLock();
            newLock.blockHeight = 0;
            newLock.expiration = (int)(Utils.currentTimeSeconds()+(60*60));
            newLock.timeout = (int)(Utils.currentTimeSeconds()+(60*5));
            newLock.txHash = vote.txHash;
            mapTxLocks.put(vote.txHash, newLock);
        } else
            log.info("instantsend - InstantX::ProcessConsensusVote - Transaction Lock Exists {} !", vote.txHash.toString());

        //compile consensus vote
        TransactionLock i = mapTxLocks.get(vote.txHash);
        if (i != null){
            i.addSignature(vote);

            int nSignatures = i.countSignatures();
            log.info("instantsend - InstantX::ProcessConsensusVote - Transaction Lock Votes {} - {} !", nSignatures, vote.getHash().toString());

            if(nSignatures >= INSTANTX_SIGNATURES_REQUIRED){
                log.info("instantsend - InstantX::ProcessConsensusVote - Transaction Lock Is Complete {} !", vote.txHash.toString());

                // Masternodes will sometimes propagate votes before the transaction is known to the client,
                // will check for conflicting locks and update transaction status on a new vote message
                // only after the lock itself has arrived
                if(!mapLockRequestAccepted.containsKey(vote.txHash) && !mapLockRequestRejected.containsKey(vote.txHash)) return true;

                if(!findConflictingLocks(mapLockRequestAccepted.get(vote.txHash))) { //?????
                    if(mapLockRequestAccepted.containsKey(vote.txHash)) {
                        updateLockedTransaction(mapLockRequestAccepted.get(vote.txHash));
                        lockTransactionInputs(mapLockRequestAccepted.get(vote.txHash));
                    } else if(mapLockRequestRejected.containsKey(vote.txHash)) {
                        resolveConflicts(mapLockRequestRejected.get(vote.txHash)); ///?????
                    } else {
                        log.info("instantsend - InstantX::ProcessConsensusVote - Transaction Lock Request is missing {} ! votes {}", vote.getHash().toString(), nSignatures);
                    }
                }
            }
            return true;
        }


        return false;
        */
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

    public boolean processTxLockRequest(TransactionLockRequest txLockRequest)
    {
        try {
            lock.lock();


            Sha256Hash txHash = txLockRequest.getHash();

            // Check to see if we conflict with existing completed lock,
            // fail if so, there can't be 2 completed locks for the same outpoint
            for(TransactionInput txin : txLockRequest.getInputs())
            {
                Sha256Hash it = mapLockedOutpoints.get(txin.getOutpoint());
                if (it != null) {
                    // Conflicting with complete lock, ignore this one
                    // (this could be the one we have but we don't want to try to lock it twice anyway)
                    log.info("CInstantSend::ProcessTxLockRequest -- WARNING: Found conflicting completed Transaction Lock, skipping current one, txid="+txLockRequest.getHash()+", completed lock txid="+
                            it.toString());
                    return false;
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
                            log.info("instantsend", "CInstantSend::ProcessTxLockRequest -- Double spend attempt! %s"+ txin.getOutpoint().toStringShort());
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

    boolean createTxLockCandidate(TransactionLockRequest txLockRequest)
    {
        // Normally we should require all outpoints to be unspent, but in case we are reprocessing
        // because of a lot of legit orphan votes we should also check already spent outpoints.
        Sha256Hash txHash = txLockRequest.getHash();
        if(!txLockRequest.isValid(!isEnoughOrphanVotesForTx(txLockRequest))) return false;

        try {
            lock.lock();


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
            } else {
                log.info("instantsend--CInstantSend::CreateTxLockCandidate -- seen, txid="+ txHash.toString());
            }

            return true;
        }
        finally {
            lock.unlock();
        }
    }

    void vote(TransactionLockCandidate txLockCandidate) {
        if (!fMasterNode) return;
        else return;
    }

    void tryToFinalizeLockCandidate(TransactionLockCandidate txLockCandidate)
    {
         try {
            lock.lock();

            Sha256Hash txHash = txLockCandidate.txLockRequest.getHash();
            if (txLockCandidate.isAllOutPointsReady() && !isLockedInstantSendTransaction(txHash)) {
                // we have enough votes now
                log.info("instantsend--CInstantSend::TryToFinalizeLockCandidate -- Transaction Lock is ready to complete, txid=", txHash);
                if (resolveConflicts(txLockCandidate, nInstantSendKeepLock)) {
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
                !context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTSEND_ENABLED)) return false;

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


    boolean isEnoughOrphanVotesForTx(TransactionLockRequest txLockRequest)
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
        if(currentBlock == null) return;

        try {
            lock.lock();

            Iterator<Map.Entry<Sha256Hash, TransactionLockCandidate>> itLockCandidate = mapTxLockCandidates.entrySet().iterator();


            // remove expired candidates
            while(itLockCandidate.hasNext()) {
                Map.Entry<Sha256Hash, TransactionLockCandidate> txLockCandidateEntry = itLockCandidate.next();

                TransactionLockCandidate txLockCandidate = txLockCandidateEntry.getValue();

                Sha256Hash txHash = txLockCandidate.getHash();
                if (txLockCandidate.isExpired(currentBlock.getHeight())) {
                    log.info("CInstantSend::CheckAndRemove -- Removing expired Transaction Lock Candidate: txid="+ txHash);

                    Iterator<Map.Entry<TransactionOutPoint, TransactionOutPointLock>> itOutpointLock = txLockCandidate.mapOutPointLocks.entrySet().iterator();

                    while(itOutpointLock.hasNext())
                    {
                        TransactionOutPoint outpoint = itOutpointLock.next().getKey();
                        mapLockedOutpoints.remove(outpoint);
                        mapVotedOutpoints.remove(outpoint);
                        //++itOutpointLock;
                    }
                    mapLockRequestAccepted.remove(txHash);
                    mapLockRequestRejected.remove(txHash);
                    mapTxLockCandidates.remove(itLockCandidate);
                } else {
                    //++itLockCandidate;
                }
            }

            // remove expired votes
            Iterator<Map.Entry<Sha256Hash, TransactionLockVote>> itVote = mapTxLockVotes.entrySet().iterator();
            while (itVote.hasNext())
            {
                Map.Entry<Sha256Hash, TransactionLockVote> vote = itVote.next();
                if (vote.getValue().isExpired(currentBlock.getHeight())) {
                    log.info("instantsend--CInstantSend::CheckAndRemove -- Removing expired vote: txid="+vote.getValue().getTxHash()+"  masternode=" + vote.getValue().getOutpointMasternode().toStringShort());
                    mapTxLockVotes.remove(itVote);
                } else {
                    //++itVote;
                }
            }

            // remove expired orphan votes
            Iterator<Map.Entry<Sha256Hash, TransactionLockVote>> itOrphanVote = mapTxLockVotes.entrySet().iterator();
            while (itOrphanVote.hasNext())
            {
                Map.Entry<Sha256Hash, TransactionLockVote> vote = itOrphanVote.next();
                if (Utils.currentTimeSeconds() - vote.getValue().getTimeCreated() > ORPHAN_VOTE_SECONDS) {
                    log.info("instantsend--CInstantSend::CheckAndRemove -- Removing expired orphan vote: txid="+vote.getValue().getTxHash()+"  masternode="+ vote.getValue().getOutpointMasternode().toStringShort());
                    mapTxLockVotes.remove(vote.getKey());
                    mapTxLockVotesOrphan.remove(itOrphanVote);
                } else {
                    //++itOrphanVote;
                }
            }

            // remove expired masternode orphan votes (DOS protection)
            Iterator<Map.Entry<TransactionOutPoint, Long>> itMasternodeOrphan = mapMasternodeOrphanVotes.entrySet().iterator();
            while (itMasternodeOrphan.hasNext()) {
                Map.Entry<TransactionOutPoint, Long> masterNodeOrphan = itMasternodeOrphan.next();
                if (masterNodeOrphan.getValue() < Utils.currentTimeSeconds()) {
                    log.info("instantsend", "CInstantSend::CheckAndRemove -- Removing expired orphan masternode vote: masternode=%s\n",
                            masterNodeOrphan.getKey().toString());
                    mapMasternodeOrphanVotes.remove(itMasternodeOrphan);
                } else {
                    //++itMasternodeOrphan;
                }
            }
        }
        finally {
            lock.unlock();
        }
    }
    public void updatedChainHead(StoredBlock chainHead)
    {
        currentBlock = chainHead;
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
            //CBlockIndex * pblockindex = pblock ? mapBlockIndex[pblock -> GetHash()] : NULL;
            int nHeightNew = block != null ? block.getHeight() : -1;//pblockindex ? pblockindex -> nHeight : -1;

            log.info("instantsend--CInstantSend::SyncTransaction -- txid="+txHash+" nHeightNew="+ nHeightNew);

            // Check lock candidates

            TransactionLockCandidate txLockCandidate = mapTxLockCandidates.get(txHash);
            if (txLockCandidate != null) {
                log.info("instantsend", "CInstantSend::SyncTransaction -- txid="+txHash+" nHeightNew="+nHeightNew+" lock candidate updated");
                txLockCandidate.setConfirmedHeight(nHeightNew);
                // Loop through outpoint locks

                Iterator<Map.Entry<TransactionOutPoint, TransactionOutPointLock>> itOutpointLock = txLockCandidate.mapOutPointLocks.entrySet().iterator();
                while (itOutpointLock.hasNext()) {
                    // Check corresponding lock votes
                    Collection<TransactionLockVote> vVotes = itOutpointLock.next().getValue().getVotes();

                    //Map.Entry<Sha256Hash, TransactionLockVote> it = null;
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
                //++itOrphanVote;
            }
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
        if(!context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTSEND_ENABLED)) return -3;
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

        TransactionLock tlock = mapTxLocks.get(txHash);
        if (tlock != null){
            return Utils.currentTimeSeconds() > tlock.timeout;
        }

        return false;
    }



    void lockTransactionInputs(TransactionLockCandidate txLockCandidate) {
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

    boolean resolveConflicts(TransactionLockCandidate txLockCandidate, int nMaxBlocks) {
        // resolve conflicts
        if(nMaxBlocks < 1) return false;

        try {
            lock.lock();

            Sha256Hash txHash = txLockCandidate.getHash();

            // make sure the lock is ready
            if (!txLockCandidate.isAllOutPointsReady()) return true; // not an error


            //LOCK(mempool.cs); // protect mempool.mapNextTx, mempool.mapTx
/*
            boolean fMempoolConflict = false;

            for(TransactionInput txin : txLockCandidate.txLockRequest.getInputs())
            {
                Sha256Hash hashConflicting = getLockedOutPointTxHash(txin.getOutpoint());
                if (hashConflicting != null && txHash != hashConflicting) {
                    // conflicting with complete lock, ignore current one
                    log.info("CInstantSend::ResolveConflicts -- WARNING: Found conflicting completed Transaction Lock, skipping current one, txid="+txHash+", conflicting txid=%s"+
                            hashConflicting.toString());
                    return false; // can't/shouldn't do anything
                } else if (mempool.mapNextTx.count(txin.prevout)) {
                    // check if it's in mempool
                    hashConflicting = mempool.mapNextTx[txin.prevout].ptx->GetHash();
                    if (txHash == hashConflicting) continue; // matches current, not a conflict, skip to next txin
                    // conflicting with tx in mempool
                    fMempoolConflict = true;
                    if (HasTxLockRequest(hashConflicting)) {
                        // There can be only one completed lock, the other lock request should never complete
                        LogPrintf("CInstantSend::ResolveConflicts -- WARNING: Found conflicting Transaction Lock Request, replacing by completed Transaction Lock, txid=%s, conflicting txid=%s\n",
                                txHash.ToString(), hashConflicting.ToString());
                    } else {
                        // If this lock is completed, we don't really care about normal conflicting txes.
                        LogPrintf("CInstantSend::ResolveConflicts -- WARNING: Found conflicting transaction, replacing by completed Transaction Lock, txid=%s, conflicting txid=%s\n",
                                txHash.ToString(), hashConflicting.ToString());
                    }
                }
            } // FOREACH
            if (fMempoolConflict) {
                std::list < CTransaction > removed;
                // remove every tx conflicting with current Transaction Lock Request
                mempool.removeConflicts(txLockCandidate.txLockRequest, removed);
                // and try to accept it in mempool again
                CValidationState state;
                bool fMissingInputs = false;
                if (!AcceptToMemoryPool(mempool, state, txLockCandidate.txLockRequest, true, & fMissingInputs)){
                    LogPrintf("CInstantSend::ResolveConflicts -- ERROR: Failed to accept completed Transaction Lock to mempool, txid=%s\n", txHash.ToString());
                    return false;
                }
                LogPrintf("CInstantSend::ResolveConflicts -- Accepted completed Transaction Lock, txid=%s\n", txHash.ToString());
                return true;
            }
            */
            // No conflicts were found so far, check to see if it was already included in block
            /*
            CTransaction txTmp;
            uint256 hashBlock;
            if (GetTransaction(txHash, txTmp, Params().GetConsensus(), hashBlock, true) && hashBlock != uint256()) {
                LogPrint("instantsend", "CInstantSend::ResolveConflicts -- Done, %s is included in block %s\n", txHash.ToString(), hashBlock.ToString());
                return true;
            }*/
            // Not in block yet, make sure all its inputs are still unspent
            /*
            BOOST_FOREACH(const CTxIn & txin, txLockCandidate.txLockRequest.vin){
                CCoins coins;
                if (!pcoinsTip -> GetCoins(txin.prevout.hash, coins) ||
                        (unsigned int)txin.prevout.n >= coins.vout.size() ||
                        coins.vout[txin.prevout.n].IsNull()){
                    // Not in UTXO anymore? A conflicting tx was mined while we were waiting for votes.
                    // Reprocess tip to make sure tx for this lock is included.
                    LogPrintf("CTxLockRequest::ResolveConflicts -- Failed to find UTXO %s - disconnecting tip...\n", txin.prevout.ToStringShort());
                    if (!DisconnectBlocks(1)) {
                        return false;
                    }
                    // Recursively check at "new" old height. Conflicting tx should be rejected by AcceptToMemoryPool.
                    ResolveConflicts(txLockCandidate, nMaxBlocks - 1);
                    LogPrintf("CTxLockRequest::ResolveConflicts -- Failed to find UTXO %s - activating best chain...\n", txin.prevout.ToStringShort());
                    // Activate best chain, block which includes conflicting tx should be rejected by ConnectBlock.
                    CValidationState state;
                    if (!ActivateBestChain(state, Params()) || !state.IsValid()) {
                        LogPrintf("CTxLockRequest::ResolveConflicts -- ActivateBestChain failed, txid=%s\n", txin.prevout.ToStringShort());
                        return false;
                    }
                    LogPrintf("CTxLockRequest::ResolveConflicts -- Failed to find UTXO %s - fixed!\n", txin.prevout.ToStringShort());
                }
            }*/
            //log.info("instantsend--CInstantSend::ResolveConflicts -- Done, txid=%s"+ txHash.toString());

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
/*
#ifdef ENABLE_WALLET
            if(pwalletMain && pwalletMain->UpdatedTransaction(txHash)) {
                // bumping this to update UI
                nCompleteTXLocks++;
                // notify an external script once threshold is reached
                std::string strCmd = GetArg("-instantsendnotify", "");
                if(!strCmd.empty()) {
                    boost::replace_all(strCmd, "%s", txHash.GetHex());
                    boost::thread t(runCommand, strCmd); // thread runs free
                }
            }
#endif

            GetMainSignals().NotifyTransactionLock(txLockCandidate.txLockRequest);
*/

            TransactionLockRequest tx = txLockCandidate.txLockRequest;
            tx.getConfidence().setIXType(TransactionConfidence.IXType.IX_LOCKED);
            tx.getConfidence().queueListeners(TransactionConfidence.Listener.ChangeReason.IX_TYPE);

            log.info("instantsend", "CInstantSend::UpdateLockedTransaction -- done, txid="+ txHash);

        }
        finally {
            lock.unlock();
        }
    }
}

