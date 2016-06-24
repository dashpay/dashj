package org.darkcoinj;

import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by Eric on 2/8/2015.
 */
public class InstantXSystem {
    private static final Logger log = LoggerFactory.getLogger(InstantXSystem.class);
    public static final int MIN_INSTANTX_PROTO_VERSION = 70047;

    public HashMap<Sha256Hash, Transaction> mapTxLockReq;
    public HashMap<Sha256Hash, Transaction> mapTxLockReqRejected;
    public HashMap<Sha256Hash, ConsensusVote> mapTxLockVote;
    public HashMap<Sha256Hash, TransactionLock> mapTxLocks;
    public HashMap<TransactionOutPoint, Sha256Hash> mapLockedInputs;
    public HashMap<Sha256Hash, Long> mapUnknownVotes; //track votes with no tx for DOS
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

    //static InstantXSystem instantXSystem;

    boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /*public static InstantXSystem get(AbstractBlockChain blockChain)
    {
        if(instantXSystem == null)
            instantXSystem = new InstantXSystem(blockChain);

        return instantXSystem;
    }*/

    public InstantXSystem(Context context)
    {
        this.context = context;
        this.mapTxLockReq = new HashMap<Sha256Hash, Transaction>();
        this.mapTxLockReqRejected = new HashMap<Sha256Hash, Transaction>();
        this.mapTxLockVote = new HashMap<Sha256Hash, ConsensusVote>();
        this.mapTxLocks = new HashMap<Sha256Hash, TransactionLock>();
        this.mapUnknownVotes = new HashMap<Sha256Hash, Long>();
        this.mapLockedInputs = new HashMap<TransactionOutPoint, Sha256Hash>();
        this.mapAcceptedLockReq = new HashMap<Sha256Hash, Peer>();

        masterNodes = MasterNodeSystem.get();


    }

    /*public InstantXSystem(DarkCoinSystem system)
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
        if(!system.fMasterNode) {
            log.warn("InstantX::DoConsensusVote - Not masternode\n");
            return;
        }
    }

    boolean canProcessInstantXMessages()
    {
        if(context.isLiteMode() && !context.allowInstantXinLiteMode()) return false; //disable all darksend/masternode related functionality
        if(!context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTX)) return false;
        if(!context.masternodeSync.isBlockchainSynced()) return false;

        return true;
    }

    public void processTransactionLockRequest(Peer pfrom, TransactionLockRequest tx) {
        if (!canProcessInstantXMessages())
            return;
        //LogPrintf("ProcessMessageInstantX::ix\n");


        //CInv inv(MSG_TXLOCK_REQUEST, tx.GetHash());
        //pfrom->AddInventoryKnown(inv);

        if (mapTxLockReq.containsKey(tx.getHash()) || mapTxLockReqRejected.containsKey(tx.getHash())) {
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


        /*bool fAccepted = false;
        {
            LOCK(cs_main);
            fAccepted = AcceptToMemoryPool(mempool, state, tx, true, &fMissingInputs);
        }*/

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

            mapTxLockReq.put(tx.getHash(), tx);

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
            mapTxLockReqRejected.put(tx.getHash(), tx);

            // can we get the conflicting transaction as proof?

            log.info("ProcessMessageInstantX::ix - Transaction Lock Request: rejected {}",

                    tx.getHash().toString()
            );

            lockTransactionInputs(tx);
            resolveConflicts(tx);

            return;
        }
    }

    //process consensus vote message
    public void processConsensusVoteMessage(Peer pfrom, ConsensusVote ctx) {

        if (!canProcessInstantXMessages())
            return;

        //Adding to known inventory in Dash Core, we will skip that.

        if(mapTxLockVote.containsKey(ctx.getHash())){
            return;
        }

        mapTxLockVote.put(ctx.getHash(), ctx);


        if(processConsensusVote(pfrom, ctx)){
            //Spam/Dos protection
            /*
                Masternodes will sometimes propagate votes before the transaction is known to the client.
                This tracks those messages and allows it at the same rate of the rest of the network, if
                a peer violates it, it will simply be ignored
            */
            if(!mapTxLockReq.containsKey(ctx.txHash) && !mapTxLockReqRejected.containsKey(ctx.txHash)){
                if(!mapUnknownVotes.containsKey(ctx.vinMasternode.getOutpoint().getHash())){
                    mapUnknownVotes.put(ctx.vinMasternode.getOutpoint().getHash(), Utils.currentTimeSeconds()+(60*10));
                }

                if(mapUnknownVotes.get(ctx.vinMasternode.getOutpoint().getHash()) > Utils.currentTimeSeconds() &&
                        mapUnknownVotes.get(ctx.vinMasternode.getOutpoint().getHash()) - getAverageVoteTime() > 60*10){
                    log.info("ProcessMessageInstantX::ix - masternode is spamming transaction votes: {} {}",
                            ctx.vinMasternode.toString(),
                            ctx.txHash.toString()
                    );
                    return;
                } else {
                    mapUnknownVotes.put(ctx.vinMasternode.getOutpoint().getHash(), Utils.currentTimeSeconds()+(60*10));
                }
            }
            //RelayInv(inv);
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

        if(valueOut.isGreaterThan(Coin.valueOf((int) context.sporkManager.getSporkValue(SporkManager.SPORK_5_MAX_VALUE), 0))){
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
        if(mapLockedInputs.containsKey(in.getOutpoint())){
            if(!mapLockedInputs.get(in.getOutpoint()).equals(tx.getHash())){
                log.info("InstantX::CheckForConflictingLocks - found two complete conflicting locks - removing both. "+ tx.getHash().toString() +" "+ mapLockedInputs.get(in.getOutpoint()).toString());
                if(mapTxLocks.containsKey(tx.getHash()))
                    mapTxLocks.get(tx.getHash()).expiration = (int)Utils.currentTimeSeconds();
                if(mapTxLocks.containsKey(mapLockedInputs.get(in.getOutpoint())))
                    mapTxLocks.get(mapLockedInputs.get(in.getOutpoint())).expiration = (int)Utils.currentTimeSeconds();
                return true;
            }
        }
    }

        return false;
    }

    public long getAverageVoteTime()
    {
        //std::map<uint256, int64_t>::iterator it = mapUnknownVotes.begin();
        long total = 0;
        long count = 0;

        for(Map.Entry<Sha256Hash, Long> it : mapUnknownVotes.entrySet()) {
            total += it.getValue();
            count++;
        }

        return total / count;
    }

    //received a consensus vote
    boolean processConsensusVote(Peer pnode, ConsensusVote ctx)
    {
        //Since we don't have access to the blockchain, we will not calculate the rankings.
        // if n = -1, then masternodes are loaded, but this masternode cannot be found
        // if n = -2, then the block hash cannot be found in the Block Store
        // if n = -3, then Lite Mode is ON - we will not verify any thing.

        int n = context.masternodeManager.getMasternodeRank(ctx.vinMasternode, ctx.blockHeight, MIN_INSTANTX_PROTO_VERSION, true);

        Masternode pmn = context.masternodeManager.find(ctx.vinMasternode);
        if(pmn != null)
            log.info("instantsend-InstantX::ProcessConsensusVote - Masternode ADDR {} {}", pmn.address.toString(), n);

        if(n == -1)
        {
            //can be caused by past versions trying to vote with an invalid protocol
            log.info("instantsend - InstantX::ProcessConsensusVote - Unknown Masternode - requesting...");
            context.masternodeManager.askForMN(pnode, ctx.vinMasternode);
            return false;
        }

        if(n == -2 || n == -3)
        {
            //We can't determine the hash for blockHeight, but we will proceed anyways;
        }
        else if(n > INSTANTX_SIGNATURES_TOTAL)
        {
            //we have enough information to determine the masternode rank, we will make sure it is correct or we will return false.
            log.info("instantsend-InstantX::ProcessConsensusVote - Masternode not in the top {} ({}) - {}", INSTANTX_SIGNATURES_TOTAL, n, ctx.getHash().toString());
            return false;
        } else
            log.info("instantsend-InstantX::ProcessConsensusVote - Masternode is the top {} ({}) - {}", INSTANTX_SIGNATURES_TOTAL, n, ctx.getHash());


        if(n != -3 && !ctx.signatureValid()) {
            log.info("InstantX::ProcessConsensusVote - Signature invalid");
            // don't ban, it could just be a non-synced masternode
            context.masternodeManager.askForMN(pnode, ctx.vinMasternode);
            return false;
        }

        if (!mapTxLocks.containsKey(ctx.txHash)){
            log.info("InstantX::ProcessConsensusVote - New Transaction Lock {} !\n", ctx.txHash.toString());

            TransactionLock newLock = new TransactionLock();
            newLock.blockHeight = 0;
            newLock.expiration = (int)(Utils.currentTimeSeconds()+(60*60));
            newLock.timeout = (int)(Utils.currentTimeSeconds()+(60*5));
            newLock.txHash = ctx.txHash;
            mapTxLocks.put(ctx.txHash, newLock);
        } else
            log.info("instantsend - InstantX::ProcessConsensusVote - Transaction Lock Exists {} !", ctx.txHash.toString());

        //compile consensus vote
        TransactionLock i = mapTxLocks.get(ctx.txHash);
        if (i != null){
            i.addSignature(ctx);

            int nSignatures = i.countSignatures();
            log.info("instantsend - InstantX::ProcessConsensusVote - Transaction Lock Votes {} - {} !", nSignatures, ctx.getHash().toString());

            if(nSignatures >= INSTANTX_SIGNATURES_REQUIRED){
                log.info("instantsend - InstantX::ProcessConsensusVote - Transaction Lock Is Complete {} !", ctx.txHash.toString());

                // Masternodes will sometimes propagate votes before the transaction is known to the client,
                // will check for conflicting locks and update transaction status on a new vote message
                // only after the lock itself has arrived
                if(!mapTxLockReq.containsKey(ctx.txHash) && !mapTxLockReqRejected.containsKey(ctx.txHash)) return true;

                if(!findConflictingLocks(mapTxLockReq.get(ctx.txHash))) { //?????
                    if(mapTxLockReq.containsKey(ctx.txHash)) {
                        updateLockedTransaction(mapTxLockReq.get(ctx.txHash));
                        lockTransactionInputs(mapTxLockReq.get(ctx.txHash));
                    } else if(mapTxLockReqRejected.containsKey(ctx.txHash)) {
                        resolveConflicts(mapTxLockReqRejected.get(ctx.txHash)); ///?????
                    } else {
                        log.info("instantsend - InstantX::ProcessConsensusVote - Transaction Lock Request is missing {} ! votes {}", ctx.getHash().toString(), nSignatures);
                    }
                }
            }
            return true;
        }


        return false;
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

                if(mapTxLockReq.containsKey(tl.getValue().txHash)){
                    Transaction tx = mapTxLockReq.get(tl.getValue().txHash);

                    //BOOST_FOREACH(const CTxIn& in, tx.vin)
                    for(TransactionInput in : tx.getInputs())
                        mapLockedInputs.remove(in.getOutpoint());

                    mapTxLockReq.remove(tl.getValue().txHash);
                    mapTxLockReqRejected.remove(tl.getValue().txHash);

                    //BOOST_FOREACH(CConsensusVote& v, it->second.vecConsensusVotes)
                    for(ConsensusVote v : tl.getValue().vecConsensusVotes)
                        mapTxLockVote.remove(v.getHash());

                    //Remove transaction confidence information, after 1 hour this should be a regular transaction
                    //tx.getConfidence().setIX(false);
                    //tx.getConfidence().setConsensusVotes(0);
                }
                it.remove();
            } else {

            }
        }

    }

    public boolean isLockedIXTransaction(Sha256Hash txHash) {
        // there must be a successfully verified lock request...
        if (!mapTxLockReq.containsKey(txHash)) return false;
        // ...and corresponding lock must have enough signatures
        TransactionLock tlock = mapTxLocks.get(txHash);
        return tlock != null && tlock.countSignatures() > INSTANTX_SIGNATURES_REQUIRED;
    }

    public int getTransactionLockSignatures(Sha256Hash txHash)
    {
        //if(fLargeWorkForkFound || fLargeWorkInvalidChainFound) return -2;
        if(!context.sporkManager.isSporkActive(SporkManager.SPORK_2_INSTANTX)) return -3;
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



    void lockTransactionInputs(Transaction tx) {
        if(mapTxLockReq.containsKey(tx.getHash())){
            for(TransactionInput in : tx.getInputs()) {
                if(!mapLockedInputs.containsKey(in.getOutpoint())){
                    mapLockedInputs.put(in.getOutpoint(), tx.getHash());
                }
            }
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
            if(mapLockedInputs.containsKey(in.getOutpoint())){
                if(mapLockedInputs.get(in.getOutpoint()) != tx.getHash()){
                    log.info("InstantX::FindConflictingLocks - found two complete conflicting locks - removing both. {} {}", tx.getHash().toString(), mapLockedInputs.get(in.getOutpoint()).toString());
                    if(mapTxLocks.containsKey(tx.getHash()))
                        mapTxLocks.get(tx.getHash()).expiration = (int)Utils.currentTimeSeconds();
                    if(mapTxLocks.containsKey(mapLockedInputs.get(in.getOutpoint())))
                        mapTxLocks.get(mapLockedInputs.get(in.getOutpoint())).expiration = (int)Utils.currentTimeSeconds();
                    return true;
                }
            }
        }

        return false;
    }

    void resolveConflicts(Transaction tx) {
        // resolve conflicts
        if (isLockedIXTransaction(tx.getHash()) && !findConflictingLocks(tx)){ //?????
            log.info("ResolveConflicts - Found Existing Complete IX Lock, resolving...");

            //reprocess the last 15 blocks
            //ReprocessBlocks(15);
            if(!mapTxLockReq.containsKey(tx.getHash()))
                mapTxLockReq.put(tx.getHash(), tx); //?????
        }
    }

    void updateLockedTransaction(Transaction tx) { updateLockedTransaction(tx, true);}

    void updateLockedTransaction(Transaction tx, boolean fForceNotification) {
        // there should be no conflicting locks
        if(findConflictingLocks(tx)) return;
        Sha256Hash txHash = tx.getHash();
        // there must be a successfully verified lock request
        if (!mapTxLockReq.containsKey(txHash)) return;

        //#ifdef ENABLE_WALLET
        //if(pwalletMain && pwalletMain->UpdatedTransaction(txHash)){
            // bumping this to update UI
        nCompleteTXLocks++;
        int nSignatures = getTransactionLockSignatures(txHash);
        // a transaction lock must have enough signatures to trigger this notification
        if(nSignatures == INSTANTX_SIGNATURES_REQUIRED || (fForceNotification && nSignatures > INSTANTX_SIGNATURES_REQUIRED)) {


            tx.getConfidence().setIXType(TransactionConfidence.IXType.IX_LOCKED);
            tx.getConfidence().queueListeners(TransactionConfidence.Listener.ChangeReason.IX_TYPE);
            // notify an external script once threshold is reached

        }
    }
}

