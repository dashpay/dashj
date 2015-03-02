package org.darkcoinj;

import org.bitcoinj.core.*;

import java.util.ArrayList;
import java.util.Comparator;
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
    final static int MASTERNODE_EXPIRATION_SECONDS          =(65*60) ;
    final static int MASTERNODE_REMOVAL_SECONDS             =(70*60);

    public ArrayList<MasterNode> vecMasternodes;
    MasterNodePayments masternodePayments;
    ArrayList<TransactionInput> vecMasternodeAskedFor;
    Map<Sha256Hash, Integer> mapSeenMasternodeVotes;

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
        ArrayList<Map.Entry<Integer, TransactionInput>> vecMasternodeScores = new ArrayList<Map.Entry<Integer, TransactionInput>>(vecMasternodes.size());

        for(MasterNode mn : vecMasternodes) {
            mn.Check();
            if(mn.protocolVersion < minProtocol) continue;
            if(!mn.IsEnabled()) {
                continue;
            }

            Sha256Hash n = mn.CalculateScore(1, nBlockHeight);
            int n2 = getScore(n);
            //memcpy(&n2, &n, sizeof(n2));

            vecMasternodeScores.add(new MyEntry<Integer, TransactionInput>(n2, mn.vin));
        }
        Collections.sort(vecMasternodeScores, new CompareValueOnly());
        //sort(vecMasternodeScores.rbegin(), vecMasternodeScores.rend(), CompareValueOnly());

        int rank = 0;
        for(Map.Entry<Integer,TransactionInput> s : vecMasternodeScores)
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
        ArrayList<Map.Entry<Integer, Integer>> vecMasternodeScores = new ArrayList<Map.Entry<Integer, Integer>>(vecMasternodes.size());
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
        vecMasternodeScores.add(new MyEntry<Integer, Integer>(n2, i));
        i++;
    }

        Collections.sort(vecMasternodeScores, new CompareValueOnly());
        //sort(vecMasternodeScores.rbegin(), vecMasternodeScores.rend(), CompareValueOnly2());

        int rank = 0;
        for(Map.Entry<Integer,Integer> s : vecMasternodeScores)
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

    public static MasterNodeSystem mns;
    public MasterNodeSystem get() {
        if(mns == null)
            mns = new MasterNodeSystem();
        return mns;
    }
}
