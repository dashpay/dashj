package org.bitcoinj.core;

import org.darkcoinj.MasterNode;
import org.darkcoinj.MasterNodePayments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Eric on 2/8/2015.
 */
public class MasterNodeSystem {
    final static int MASTERNODE_NOT_PROCESSED              = 0; // initial state
    final static int MASTERNODE_IS_CAPABLE                 = 1;
    final static int MASTERNODE_NOT_CAPABLE                = 2;
    final static int MASTERNODE_STOPPED                    = 3;
    final static int MASTERNODE_INPUT_TOO_NEW              = 4;
    final static int MASTERNODE_PORT_NOT_OPEN              = 6;
    final static int MASTERNODE_PORT_OPEN                  = 7;
    final static int MASTERNODE_SYNC_IN_PROCESS            = 8;
    final static int MASTERNODE_REMOTELY_ENABLED           = 9;

    final static int MASTERNODE_MIN_CONFIRMATIONS          = 15;
    final static int MASTERNODE_MIN_DSEEP_SECONDS           =(30*60);
    final static int MASTERNODE_MIN_DSEE_SECONDS            =(5*60)    ;
    final static int MASTERNODE_PING_SECONDS                =(1*60)   ;
    public final static int MASTERNODE_EXPIRATION_SECONDS          =(65*60) ;
    final static int MASTERNODE_REMOVAL_SECONDS             =(70*60);

    public ArrayList<MasterNode> vecMasternodes;
    MasterNodePayments masternodePayments;
    ArrayList<TransactionInput> vecMasternodeAskedFor;
    HashMap<Sha256Hash, Integer> mapSeenMasternodeVotes;
    HashMap<Long, Sha256Hash> mapCacheBlockHashes;
    //void ProcessMasternodeConnections();
    //int CountMasternodesAboveProtocol(int protocolVersion);

    // Get the current winner for this block
    //default 1, 0, 0
    /*
    int GetCurrentMasterNode(int mod, long nBlockHeight, int minProtocol)
    {
        int i = 0;
        int score = 0;
        int winner = -1;

        // scan for winner
        for(MasterNode mn : vecMasternodes) {
            mn.Check();
            if(mn.protocolVersion < minProtocol) continue;
            if(!mn.IsEnabled()) {
                i++;
                continue;
            }

            // calculate the score for each masternode
            Sha256Hash n = mn.CalculateScore(mod, nBlockHeight);
            int n2 = getScore(n);
            //memcpy(&n2, &n, sizeof(n2));

            // determine the winner
            if(n2 > score){
                score = n2;
                winner = i;
            }
            i++;
        }

        return winner;
    }
    */
    /*
    int getMasternodeRank(TransactionInput vin, long nBlockHeight, int minProtocol)
    {
        //std::vector<pair<unsigned int, CTxIn> > vecMasternodeScores;
        ArrayList<Pair<Integer, TransactionInput>> vecMasternodeScores = new ArrayList<Pair<Integer, TransactionInput>>();

        for(MasterNode mn : vecMasternodes)
        {
            mn.Check();

            if(mn.protocolVersion < minProtocol) continue;
            if(!mn.IsEnabled()) {
                continue;
            }

            uint256 n = mn.CalculateScore(1, nBlockHeight);
            unsigned int n2 = 0;
            memcpy(&n2, &n, sizeof(n2));

            vecMasternodeScores.push_back(make_pair(n2, mn.vin));
        }

        sort(vecMasternodeScores.rbegin(), vecMasternodeScores.rend(), CompareValueOnly());

        unsigned int rank = 0;
        BOOST_FOREACH (PAIRTYPE(unsigned int, CTxIn)& s, vecMasternodeScores){
            rank++;
            if(s.second == vin) {
                return rank;
            }
        }

        return -1;
    }
    */
    public int getMasternodeByVin(TransactionInput vin)
    {
        int i = 0;


        for(MasterNode mn : vecMasternodes) {
            if (mn.vin == vin) return i;
            i++;
        }

        return -1;
    }
    protected static int getScore(Sha256Hash n)
    {
        int n2 = 0;
        byte [] bn2 = new byte[4];
        System.arraycopy(n.getBytes(), n.getBytes().length, bn2, 0, 4);
        n2 = Utils.readUint16BE(bn2, 0);
        return n2;
    }
    class CompareValueOnly implements Comparator
    {
        @Override
        public int compare(Object o1, Object o2) {
            Map.Entry<Integer, TransactionInput> p1 = (Map.Entry<Integer, TransactionInput>)o1;
            Map.Entry<Integer, TransactionInput> p2 = (Map.Entry<Integer, TransactionInput>)o2;
            if(p1.getKey() < p2.getKey())
                return -1;
            if(p1.getKey() > p2.getKey())
                return 1;
            return 0;
        }
    }
    //default 0, 0
    /*
    public int getMasterNodeRank(TransactionInput vin, long nBlockHeight, int minProtocol)
    {
        ArrayList<Pair<Integer, TransactionInput>> vecMasternodeScores = new ArrayList<Pair<Integer, TransactionInput>>(vecMasternodes.size());

        for(MasterNode mn : vecMasternodes) {
            mn.Check();
            if(mn.protocolVersion < minProtocol) continue;
            if(!mn.IsEnabled()) {
                continue;
            }

            Sha256Hash n = mn.CalculateScore(1, nBlockHeight);
            int n2 = getScore(n);
            //memcpy(&n2, &n, sizeof(n2));

            vecMasternodeScores.add(new Pair<Integer, TransactionInput>(n2, mn.vin));
        }
        Collections.sort(vecMasternodeScores, new CompareValueOnly());
        //sort(vecMasternodeScores.rbegin(), vecMasternodeScores.rend(), CompareValueOnly());

        int rank = 0;
        for(Pair<Integer,TransactionInput> s : vecMasternodeScores)
        {
            rank++ ;
            if(s.getValue() == vin) return rank;
        }

        return -1;
    }
    */

    final class MyEntry<K, V> implements Map.Entry<K, V> {
        private final K key;
        private V value;

        public MyEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V old = this.value;
            this.value = value;
            return old;
        }
    }
    /*
    int getMasterNodeByRank(int findRank, long nBlockHeight, int minProtocol)
    {
        int i = 0;
        ArrayList<Pair<Integer, Integer>> vecMasternodeScores = new ArrayList<Pair<Integer, Integer>>(vecMasternodes.size());
        //std::vector<pair<unsigned int, int> > vecMasternodeScores;

        i = 0;
        for(MasterNode mn : vecMasternodes)
        {
            mn.Check();
            if(mn.protocolVersion < minProtocol) continue;
            if(!mn.IsEnabled()) {
                i++;
                continue;
        }

        Sha256Hash n = mn.CalculateScore(1, nBlockHeight);
        int n2 = getScore(n);
        //memcpy(&n2, &n, sizeof(n2));

        //vecMasternodeScores.push_back(make_pair(n2, i));
        vecMasternodeScores.add(new Pair<Integer, Integer>(n2, i));
        i++;
    }

        Collections.sort(vecMasternodeScores, new CompareValueOnly());
        //sort(vecMasternodeScores.rbegin(), vecMasternodeScores.rend(), CompareValueOnly2());

        int rank = 0;
        for(Pair<Integer,Integer> s : vecMasternodeScores)
        {
            rank++;
            if(rank == findRank) return s.getValue();
        }

        return -1;
    }
    */
    //void ProcessMessageMasternode(CNode* pfrom, std::string& strCommand, CDataStream& vRecv);
    void processMessageMasternode(Peer peer, Message m)
    {

    }
    public void processDarkSendElectionEntryPing(Peer peer, DarkSendElectionEntryPingMessage m)
    {
        if(DarkCoinSystem.fLiteMode) return; //disable all darksend/masternode related functionality
        bool fIsInitialDownload = IsInitialBlockDownload();
        if(fIsInitialDownload) return;

        CTxIn vin;
        vector<unsigned char> vchSig;
        int64_t sigTime;
        bool stop;
        vRecv >> vin >> vchSig >> sigTime >> stop;

        //LogPrintf("dseep - Received: vin: %s sigTime: %lld stop: %s\n", vin.ToString().c_str(), sigTime, stop ? "true" : "false");

        if (sigTime > GetAdjustedTime() + 60 * 60) {
            LogPrintf("dseep - Signature rejected, too far into the future %s\n", vin.ToString().c_str());
            return;
        }

        if (sigTime <= GetAdjustedTime() - 60 * 60) {
            LogPrintf("dseep - Signature rejected, too far into the past %s - %d %d \n", vin.ToString().c_str(), sigTime, GetAdjustedTime());
            return;
        }

        // see if we have this masternode

        BOOST_FOREACH(CMasterNode& mn, vecMasternodes) {
        if(mn.vin.prevout == vin.prevout) {
            // LogPrintf("dseep - Found corresponding mn for vin: %s\n", vin.ToString().c_str());
            // take this only if it's newer
            if(mn.lastDseep < sigTime){
                std::string strMessage = mn.addr.ToString() + boost::lexical_cast<std::string>(sigTime) + boost::lexical_cast<std::string>(stop);

                std::string errorMessage = "";
                if(!darkSendSigner.VerifyMessage(mn.pubkey2, vchSig, strMessage, errorMessage)){
                    LogPrintf("dseep - Got bad masternode address signature %s \n", vin.ToString().c_str());
                    //Misbehaving(pfrom->GetId(), 100);
                    return;
                }

                mn.lastDseep = sigTime;

                if(!mn.UpdatedWithin(MASTERNODE_MIN_DSEEP_SECONDS)){
                    mn.UpdateLastSeen();
                    if(stop) {
                        mn.Disable();
                        mn.Check();
                    }
                    RelayDarkSendElectionEntryPing(vin, vchSig, sigTime, stop);
                }
            }
            return;
        }
    }

        if(fDebug) LogPrintf("dseep - Couldn't find masternode entry %s\n", vin.ToString().c_str());

        std::map<COutPoint, int64_t>::iterator i = askedForMasternodeListEntry.find(vin.prevout);
        if (i != askedForMasternodeListEntry.end()){
            int64_t t = (*i).second;
            if (GetTime() < t) {
                // we've asked recently
                return;
            }
        }

        // ask for the dsee info once from the node that sent dseep

        LogPrintf("dseep - Asking source node for missing entry %s\n", vin.ToString().c_str());
        pfrom->PushMessage("dseg", vin);
        int64_t askAgain = GetTime()+(60*60*24);
        askedForMasternodeListEntry[vin.prevout] = askAgain;
    }

    public static MasterNodeSystem mns;
    public static MasterNodeSystem get() {
        if(mns == null)
            mns = new MasterNodeSystem();
        return mns;
    }
}
