package org.darkcoinj;

import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by Eric on 2/8/2015.
 */
public class InstantXSystem {
    private static final Logger log = LoggerFactory.getLogger(InstantXSystem.class);
    public static final int MIN_INSTANTX_PROTO_VERSION = 70047;

    ArrayList<TransactionLock> vecTxLocks;
    Map<Sha256Hash, Transaction> mapTxLockReq;
    Map<Sha256Hash, Transaction> mapTxLockReqRejected;
    Map<Sha256Hash, TransactionLock> mapTxLocks;

    public static final int INSTANTX_SIGNATURES_REQUIRED = 2;

    BlockChain blockChain;
    DarkCoinSystem system;

    public InstantXSystem(DarkCoinSystem system)
    {
        this.system = system;
        blockChain = system.blockChain;
        //mapTxLocks = new Map<Sha256Hash, TransactionLock>();

    }

    void ProcessMessageInstantX(Peer from, Message m)
    {

    }

    //check if we need to vote on this transaction
    void DoConsensusVote(Transaction tx, boolean approved, long nBlockHeight)
    {
        if(!system.fMasterNode) {
            log.warn("InstantX::DoConsensusVote - Not masternode\n");
            return;
        }
    }

    //process consensus vote message
    void ProcessConsensusVote(ConsensusVote ctx) {
        if(!system.fMasterNode) {
            log.warn("InstantX::ProcessConsensusVote - Not masternode\n");
            return;
        }
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
                log.info("Removing old transaction lock %s\n", it.getValue().getHash().toString());
                mapTxLocks.remove(it);
            }
        }

    }
}
