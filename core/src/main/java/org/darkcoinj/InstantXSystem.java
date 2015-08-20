package org.darkcoinj;

import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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

    public static final int INSTANTX_SIGNATURES_REQUIRED = 20;
    public static final int INSTANTX_SIGNATURES_TOTAL = 30;

    AbstractBlockChain blockChain;
    DarkCoinSystem system;
    MasterNodeSystem masterNodes;

    static InstantXSystem instantXSystem;

    boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public static InstantXSystem get(AbstractBlockChain blockChain)
    {
        if(instantXSystem == null)
            instantXSystem = new InstantXSystem(blockChain);

        return instantXSystem;
    }

    public InstantXSystem(AbstractBlockChain blockChain)
    {
        this.blockChain = blockChain;
        this.mapTxLockReq = new HashMap<Sha256Hash, Transaction>();
        this.mapTxLockReqRejected = new HashMap<Sha256Hash, Transaction>();
        this.mapTxLockVote = new HashMap<Sha256Hash, ConsensusVote>();
        this.mapTxLocks = new HashMap<Sha256Hash, TransactionLock>();
        this.mapUnknownVotes = new HashMap<Sha256Hash, Long>();
        this.mapLockedInputs = new HashMap<TransactionOutPoint, Sha256Hash>();

        masterNodes = MasterNodeSystem.get();
    }

    public InstantXSystem(DarkCoinSystem system)
    {
        this.system = system;
        blockChain = system.blockChain;
        //mapTxLocks = new Map<Sha256Hash, TransactionLock>();

    }

    public void processMessageInstantX(Peer from, Message m)
    {

    }

    //check if we need to vote on this transaction
    public void doConsensusVote(TransactionLockRequest tx, long blockHeight)
    {
        if(!system.fMasterNode) {
            log.warn("InstantX::DoConsensusVote - Not masternode\n");
            return;
        }
    }

    //process consensus vote message
    public boolean processConsensusVote(ConsensusVote ctx) {

        int n = 0;//masterNodes.getMasternodeRank(ctx.vinMasterNode, ctx.blockHeight, MIN_INSTANTX_PROTO_VERSION);

        int x = 0;//masterNodes.getMasternodeByVin(ctx.vinMasterNode);  TODO:fix later
        if(x != -1){
            //log.info("InstantX::ProcessConsensusVote - Masternode ADDR %s %d\n", masterNodes.vecMasternodes.get(x).getAddress().toString(), n);
        }

        if(n == -1)
        {
            //can be caused by past versions trying to vote with an invalid protocol
            log.info("InstantX::ProcessConsensusVote - Unknown Masternode\n");
            return false;
        }

        if(n > INSTANTX_SIGNATURES_TOTAL)
        {
            log.info("InstantX::ProcessConsensusVote - Masternode not in the top %d (%d) - %s\n", INSTANTX_SIGNATURES_TOTAL, n, ctx.getHash().toString());
            return false;
        }

        if(!ctx.signatureValid()) {
            log.info("InstantX::ProcessConsensusVote - Signature invalid\n");
            //don't ban, it could just be a non-synced masternode
            return false;
        }

        if (!mapTxLocks.containsKey(ctx.txHash)) {
            log.info("InstantX::ProcessConsensusVote - New Transaction Lock "+ ctx.txHash.toString());

            TransactionLock newLock = new TransactionLock(
                                            0,
                    (int)Utils.currentTimeSeconds()+(60*60),
                    (int)Utils.currentTimeSeconds()+(60*5),
            ctx.txHash);
            mapTxLocks.put(ctx.txHash, newLock);
        } else {
            log.info("InstantX::ProcessConsensusVote - Transaction Lock Exists "+ ctx.txHash.toString());
        }

        //compile consessus vote
        //std::map<uint256, CTransactionLock>::iterator i = mapTxLocks.find(ctx.txHash);
        for(Map.Entry<Sha256Hash, TransactionLock> i : mapTxLocks.entrySet())
        {
            if(i.getKey().equals(ctx.txHash) == false)
                continue;
        //if (i != mapTxLocks.end()){
            //(*i).second.AddSignature(ctx);
            i.getValue().addSignature(ctx);

            //TODO::Update Wallet?
            /*#ifdef ENABLE_WALLET
            if(pwalletMain){
                //when we get back signatures, we'll count them as requests. Otherwise the client will think it didn't propagate.
                if(pwalletMain->mapRequestCount.count(ctx.txHash))
                    pwalletMain->mapRequestCount[ctx.txHash]++;
            }
            #endif
            */
            log.info("InstantX::ProcessConsensusVote - Transaction Lock Votes " +  i.getValue().countSignatures()+ " - "+  ctx.getHash().toString() + "!");

            if(i.getValue().countSignatures() >= INSTANTX_SIGNATURES_REQUIRED){
                log.info("InstantX::ProcessConsensusVote - Transaction Lock Is Complete %s !\n", i.getValue().getHash().toString());

                Transaction tx = mapTxLockReq.get(ctx.txHash);
                if(!CheckForConflictingLocks(tx)){
                    //TODO::Update Wallet?
                    /*#ifdef ENABLE_WALLET
                    if(pwalletMain){
                        if(pwalletMain->UpdatedTransaction((*i).second.txHash)){
                            nCompleteTXLocks++;
                        }
                    }
                    #endif
                    */

                    if(mapTxLockReq.containsKey(ctx.txHash)){
                        for( TransactionInput in : tx.getInputs())
                        {
                            if(!mapLockedInputs.containsKey(in.getOutpoint())){
                                mapLockedInputs.put(in.getOutpoint(), ctx.txHash);
                            }
                        }
                    }

                    // resolve conflicts

                    //if this tx lock was rejected, we need to remove the conflicting blocks
                    if(mapTxLockReqRejected.containsKey(i.getValue().getHash())){
                        //CValidationState state;
                        //DisconnectBlockAndInputs(state, mapTxLockReqRejected[(*i).second.txHash]);
                    }
                }
            }
            return true;
        }


        return false;
    }

    // keep transaction locks in memory for an hour
    void CleanTransactionLocksList()
    {
        if(blockChain.getChainHead() == null) return;

        //std::map<uint256, CTransactionLock>::iterator it = mapTxLocks.begin();

        for(Map.Entry<Sha256Hash, TransactionLock> it : mapTxLocks.entrySet())

        /*while(it != mapTxLocks.end())*/ {
            if((blockChain.getChainHead().getHeight() - it.getValue().blockHeight) > 3)
            /*if(chainActive.Tip()->nHeight - it->second.nBlockHeight > 3)*/{ //keep them for an hour
                log.info("Removing old transaction lock "+ it.getValue().getHash().toString());
                mapTxLocks.remove(it);
            }
        }

    }
    public int createNewLock(TransactionLockRequest tx)
    {
        //We don't have the ability to determine the age of the inputs.
        //We will assume that the other nodes have rejected the txlreq first
        int txAge = 6;
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
            mapTxLocks.get(tx.getHash()).blockHeight = blockHeight;
            log.info("CreateNewLock - Transaction Lock Exists "+ tx.getHash().toString());
        }

        return blockHeight;
    }
    boolean CheckForConflictingLocks(Transaction tx)
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
            if(mapLockedInputs.get(in.getOutpoint()) != tx.getHash()){
                log.info("InstantX::CheckForConflictingLocks - found two complete conflicting locks - removing both. "+ tx.getHash().toString() + mapLockedInputs.get(in.getOutpoint()).toString());
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

    public long GetAverageVoteTime()
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

    //TODO:  add clean up code
}
