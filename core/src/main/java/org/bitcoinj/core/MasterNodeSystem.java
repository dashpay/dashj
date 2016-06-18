package org.bitcoinj.core;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.darkcoinj.ActiveMasterNode;
import org.darkcoinj.DarkSendSigner;
import org.darkcoinj.MasterNodePayments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.*;

/**
 * Created by Eric on 2/8/2015.
 */
public class MasterNodeSystem {
    private static final Logger log = LoggerFactory.getLogger(MasterNodeSystem.class);
    public final static int MASTERNODE_NOT_PROCESSED              = 0; // initial state
    public final static int MASTERNODE_IS_CAPABLE                 = 1;
    public final static int MASTERNODE_NOT_CAPABLE                = 2;
    public final static int MASTERNODE_STOPPED                    = 3;
    public final static int MASTERNODE_INPUT_TOO_NEW              = 4;
    public final static int MASTERNODE_PORT_NOT_OPEN              = 6;
    public final static int MASTERNODE_PORT_OPEN                  = 7;
    public final static int MASTERNODE_SYNC_IN_PROCESS            = 8;
    public final static int MASTERNODE_REMOTELY_ENABLED           = 9;

    public final static int MASTERNODE_MIN_CONFIRMATIONS          = 15;
    public final static int MASTERNODE_MIN_DSEEP_SECONDS           =(30*60);
    public final static int MASTERNODE_MIN_DSEE_SECONDS            =(5*60)    ;
    public final static int MASTERNODE_PING_SECONDS                =(1*60)   ;
    public final static int MASTERNODE_EXPIRATION_SECONDS          =(65*60) ;
    public final static int MASTERNODE_REMOVAL_SECONDS             =(70*60);



    /** The list of active masternodes */
    //std::vector<CMasterNode> vecMasternodes;
    public ArrayList<Masternode> vecMasternodes;
    /** Object for who's going to get paid on which blocks */
    //CMasternodePayments masternodePayments;
    MasterNodePayments masternodePayments;
    // keep track of masternode votes I've seen
    //map<uint256, CMasternodePaymentWinner> mapSeenMasternodeVotes;
    HashMap<Sha256Hash, MasterNodePaymentWinner> mapSeenMasternodeVotes;
    // keep track of the scanning errors I've seen
   // map<uint256, int> mapSeenMasternodeScanningErrors;
    HashMap<Sha256Hash, Integer> mapSeenMasternodeScanningErrors;
// who's asked for the masternode list and the last time
    //std::map<CNetAddr, int64_t> askedForMasternodeList;
    HashMap<InetAddress, Long> askedForMasternodeList;
    TreeMap<InetAddress, Long> a2;
// which masternodes we've asked for
    //std::map<COutPoint, int64_t> askedForMasternodeListEntry;
    HashMap<TransactionOutPoint, Long> askedForMasternodeListEntry;
// cache block hashes as we calculate them
    //std::map<int64_t, uint256> mapCacheBlockHashes;
    HashMap<Long, Sha256Hash> mapCacheBlockHashes;

    ActiveMasterNode activeMasternode;
    static final int nMasternodeMinProtocol= 70051;
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
        for(Masternode mn : vecMasternodes) {
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

        for(Masternode mn : vecMasternodes)
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


        for(Masternode mn : vecMasternodes) {
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

        for(Masternode mn : vecMasternodes) {
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
        for(Masternode mn : vecMasternodes)
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

    //void ProcessMessageMasternode(CNode* pfrom, std::string& strCommand, CDataStream& vRecv);
    void processMessageMasternode(Peer peer, Message m)
    {

    }
    public void processDarkSendElectionEntryPing(Peer peer, NetworkParameters context, DarkSendElectionEntryPingMessage m)
    {
        if(DarkCoinSystem.fLiteMode) return; //disable all darksend/masternode related functionality
        //bool fIsInitialDownload = IsInitialBlockDownload();
        //if(fIsInitialDownload) return;

        //LogPrintf("dseep - Received: vin: %s sigTime: %lld stop: %s\n", vin.ToString().c_str(), sigTime, stop ? "true" : "false");

        if (m.sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            log.info("dseep - Signature rejected, too far into the future %s\n", m.vin.toString());
            return;
        }

        if (m.sigTime <= Utils.currentTimeSeconds() - 60 * 60) {
            log.info("dseep - Signature rejected, too far into the past %s - %d %d \n", m.vin.toString(), m.sigTime, Utils.currentTimeSeconds());
            return;
        }

        // see if we have this masternode

        for(Masternode mn : vecMasternodes) {
        if(mn.vin.getOutpoint() == m.vin.getOutpoint()) {
            // LogPrintf("dseep - Found corresponding mn for vin: %s\n", vin.ToString().c_str());
            // take this only if it's newer
            if(mn.lastDseep < m.sigTime){
                String strMessage = mn.address.toString() + m.sigTime + m.stop;

                StringBuilder errorMessage = new StringBuilder();
                if(!DarkSendSigner.verifyMessage(ECKey.fromPublicOnly(mn.pubkey2.getBytes()), m.vchSig, strMessage, errorMessage)){
                    log.info("dseep - Got bad masternode address signature %s \n", m.vin.toString());
                    //Misbehaving(pfrom->GetId(), 100);
                    return;
                }

                mn.lastDseep = m.sigTime;

                if(!mn.UpdatedWithin(MASTERNODE_MIN_DSEEP_SECONDS)){
                    mn.UpdateLastSeen();
                    if(m.stop) {
                        mn.Disable();
                        mn.Check();
                    }
                    //RelayDarkSendElectionEntryPing(vin, vchSig, sigTime, stop);
                }
            }
            return;
        }
    }

        if(DarkCoinSystem.fDebug) log.info("dseep - Couldn't find masternode entry %s\n", m.vin.toString());

        //std::map<COutPoint, int64_t>::iterator i = askedForMasternodeListEntry..find(m.vin.getOutpoint());
        Long i = askedForMasternodeListEntry.get(m.vin.getOutpoint());



            if (i != null) {
                long t = i;
                if (Utils.currentTimeSeconds() < t) {
                    // we've asked recently
                    return;
                }
            }


        // ask for the dsee info once from the node that sent dseep

        log.info("dseep - Asking source node for missing entry %s\n", m.vin.toString());
        //pfrom->PushMessage("dseg", m.vin);
        DarkSendEntryGetMessage dseg = new DarkSendEntryGetMessage(context, m.vin);
        peer.sendMessage(dseg);
        long askAgain = Utils.currentTimeSeconds()+(60*60*24);
        askedForMasternodeListEntry.put(m.vin.getOutpoint(),askAgain);
    }
    boolean IsRFC1918(byte [] addr)
    {
        return         addr[3] == 10 ||
                (addr[3] == 192 && addr[2] == 168) ||
                (addr[3] == 172 && (addr[2] >= 16 && addr[2] <= 31));
    }
    TransactionInput zeroInput(NetworkParameters context)
    {
       return new TransactionInput(context, null, new byte [1], new TransactionOutPoint(context, -1, Sha256Hash.ZERO_HASH));
    }
    /*
    public void processDarkSendEntryGet(Peer peer, NetworkParameters context, DarkSendEntryGetMessage m)
    {
        if(DarkCoinSystem.fLiteMode) return; //disable all darksend/masternode related functionality

        if(m.vin == zeroInput(context)) { //only should ask for this once
            //local network
            if(!peer.getAddress().getAddr().isMCSiteLocal())
            if(IsRFC1918(peer.getAddress().getAddr().getAddress()) && context.getId().equals(NetworkParameters.ID_MAINNET))
            {
                //std::map<CNetAddr, int64_t>::iterator i = askedForMasternodeList.find(pfrom->addr);
                Long i = askedForMasternodeList.get(peer.getAddress().getAddr());

                if (i != null)
                {
                    long t = i;
                    if (Utils.currentTimeSeconds() < t) {
                        //Misbehaving(pfrom->GetId(), 34);

                        log.info("dseg - peer already asked me for the list\n");
                        return;
                    }
                }

                long askAgain = Utils.currentTimeSeconds()+(60*60*3);
                askedForMasternodeList.put(peer.getAddress().getAddr(), askAgain);
            }
        } //else, asking for a specific node which is ok

        int count = vecMasternodes.size();
        int i = 0;

        for(Masternode mn : vecMasternodes)
        {

        if(IsRFC1918(mn.address.getAddr().getAddress())) continue; //local network

        if(m.vin == zeroInput(context)){
            mn.Check();
            if(mn.IsEnabled()) {
                if(DarkCoinSystem.fDebug) log.info("dseg - Sending masternode entry - "+ mn.address.toString());

                    DarkSendElectionEntryMessage dsee = new DarkSendElectionEntryMessage(context, mn.vin, mn.address, mn.sig, mn.now, mn.pubkey, mn.pubkey2, count, i, mn.lastTimeSeen, mn.protocolVersion);
                    peer.sendMessage(dsee);


                //pfrom->PushMessage("dsee", mn.vin, mn.addr, mn.sig, mn.now, mn.pubkey, mn.pubkey2, count, i, mn.lastTimeSeen, mn.protocolVersion);
            }
        } else if (m.vin == mn.vin) {
            if(DarkCoinSystem.fDebug) log.info("dseg - Sending masternode entry - "+ peer.getAddress().toString());

            DarkSendElectionEntryMessage dsee = new DarkSendElectionEntryMessage(context, mn.vin, mn.address, mn.sig, mn.now, mn.pubkey, mn.pubkey2, count, i, mn.lastTimeSeen, mn.protocolVersion);
            peer.sendMessage(dsee);
            //pfrom->PushMessage("dsee", mn.vin, mn.addr, mn.sig, mn.now, mn.pubkey, mn.pubkey2, count, i, mn.lastTimeSeen, mn.protocolVersion);

            log.info("dseg - Sent 1 masternode entries to "+  peer.getAddress().toString());
            return;
        }
        i++;
    }

        log.info("dseg - Sent "+count+" masternode entries to " + peer.getAddress().toString());
    }
    */
    /*
    void processDarkSendElectionEntry(Peer peer, NetworkParameters context, DarkSendElectionEntryMessage m)
    {

        // make sure signature isn't in the future (past is OK)
        if (m.sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            log.info("dsee - Signature rejected, too far into the future "+ m.vin.toString());
            return;
        }

        boolean isLocal = IsRFC1918(m.addr.getAddr().getAddress()) || m.addr.getAddr().isSiteLocalAddress();
        if(context.getId().equals(NetworkParameters.ID_REGTEST)) isLocal = false;

        String vchPubKey = new String(m.pubkey.getBytes());
        String vchPubKey2 = new String(m.pubkey2.getBytes());

        String strMessage = m.addr.toString() + m.sigTime + vchPubKey + vchPubKey2 + m.protocolVersion;

        if(m.protocolVersion < nMasternodeMinProtocol) {
            log.info("dsee - ignoring outdated masternode %s protocol version %d\n", m.vin.toString(), m.protocolVersion);
            return;
        }

        //CScript pubkeyScript;
        ECKey pubkey1 = ECKey.fromPublicOnly(m.pubkey.getBytes());
        Address address1 = new Address(context, pubkey1.getPubKeyHash());
        Script pubkeyScript = ScriptBuilder.createOutputScript(address1);
        //pubkeyScript.SetDestination(m.pubkey.GetID());

        if(pubkeyScript.getProgram().length != 25) {
            log.info("dsee - pubkey the wrong size\n");
           // Misbehaving(pfrom->GetId(), 100);
            return;
        }

        //CScript pubkeyScript2;
        //pubkeyScript2.SetDestination(pubkey2.GetID());
        ECKey pubkey2 = ECKey.fromPublicOnly(m.pubkey2.getBytes());
        Address address2 = new Address(context, pubkey2.getPubKeyHash());
        Script pubkeyScript2 = ScriptBuilder.createOutputScript(address2);

        if(pubkeyScript.getProgram().length != 25) {
            log.info("dsee - pubkey2 the wrong size\n");
            //Misbehaving(pfrom->GetId(), 100);
            return;
        }

        //std::string errorMessage = "";
        StringBuilder errorMessage = new StringBuilder();
        if(!DarkSendSigner.verifyMessage(ECKey.fromPublicOnly(m.pubkey.getBytes()), m.vchSig, strMessage, errorMessage)){
            log.info("dsee - Got bad masternode address signature\n");
            //Misbehaving(pfrom->GetId(), 100);
            return;
        }

        if(context.getId().equals(NetworkParameters.ID_MAINNET) == false){
            if(m.addr.getPort() != 9999) return;
        }

        //search existing masternode list, this is where we update existing masternodes with new dsee broadcasts
        for (Masternode mn : vecMasternodes)
        {
          if(mn.vin.getOutpoint() == m.vin.getOutpoint()) {
            // count == -1 when it's a new entry
            //   e.g. We don't want the entry relayed/time updated when we're syncing the list
            // mn.pubkey = pubkey, IsVinAssociatedWithPubkey is validated once below,
            //   after that they just need to match
            if(m.count == -1 && mn.pubkey == m.pubkey && !mn.UpdatedWithin(MASTERNODE_MIN_DSEE_SECONDS)){
                mn.UpdateLastSeen();

                if(mn.now < m.sigTime){ //take the newest entry
                    log.info("dsee - Got updated entry for ", m.addr.toString());
                    mn.pubkey2 = m.pubkey2;
                    mn.now = m.sigTime;
                    mn.sig = m.vchSig;
                    mn.protocolVersion = m.protocolVersion;
                    mn.address = m.addr;

                    //RelayDarkSendElectionEntry(vin, addr, vchSig, sigTime, pubkey, pubkey2, count, current, lastUpdated, protocolVersion);
                }
            }

            return;
        }
    }

        // make sure the vout that was signed is related to the transaction that spawned the masternode
        //  - this is expensive, so it's only done once per masternode
        if(!DarkSendSigner.isVinAssociatedWithPubkey(context, m.vin, m.pubkey)) {
            log.info("dsee - Got mismatched pubkey and vin\n");
            //Misbehaving(pfrom->GetId(), 100);
            return;
        }

        if(DarkCoinSystem.fDebug) log.info("dsee - Got NEW masternode entry "+ m.addr.toString());

        // make sure it's still unspent
        //  - this is checked later by .check() in many places and by ThreadCheckDarkSendPool()

        //CValidationState state;
        //Transaction tx = new Transaction(context);
        //TransactionOutput vout = new TransactionOutput(context, null, Coin.valueOf(999, 99), ECKey.fromPublicOnly(DarkSend.darkSendPool.collateralPubKey));
        //tx.vin.push_back(vin);
        //tx.addInput(m.vin);

        //tx.vout.push_back(vout);
        //tx.addOutput(vout);

        //if(true AcceptableInputs(mempool, state, tx))
        {
            if(DarkCoinSystem.fDebug) log.info("dsee - Accepted masternode entry "+ m.count + " "+ m.current);

            if(GetInputAge(vin) < MASTERNODE_MIN_CONFIRMATIONS){
                LogPrintf("dsee - Input must have least %d confirmations\n", MASTERNODE_MIN_CONFIRMATIONS);
                Misbehaving(pfrom->GetId(), 20);
                return;
            }

            // use this as a peer
            //addrman.Add(CAddress(addr), pfrom->addr, 2*60*60);

            // add our masternode
            Masternode mn = new Masternode(m.addr, m.vin, m.pubkey, m.vchSig, m.sigTime, m.pubkey2, m.protocolVersion);
            mn.UpdateLastSeen(m.lastUpdated);
            vecMasternodes.add(mn);

            // if it matches our masternodeprivkey, then we've been remotely activated
            if(m.pubkey2 == activeMasternode.pubKeyMasternode && m.protocolVersion == NetworkParameters.PROTOCOL_VERSION){
                activeMasternode.EnableHotColdMasterNode(m.vin, m.addr);
            }

           //if(count == -1 && !isLocal)
           //     RelayDarkSendElectionEntry(vin, addr, vchSig, sigTime, pubkey, pubkey2, count, current, lastUpdated, protocolVersion);

        } else {
            log.info("dsee - Rejected masternode entry %s"+ m.addr.toString());

            int nDoS = 0;
            if (state.IsInvalid(nDoS))
            {
                log.info("dsee - %s from %s %s was not accepted into the memory pool\n", tx.GetHash().ToString().c_str(),
                        pfrom -> addr.ToString().c_str(), pfrom -> cleanSubVer.c_str());
                if (nDoS > 0)
                    Misbehaving(pfrom->GetId(), nDoS);
            }
           }
    }*/

    public static MasterNodeSystem mns;
    public static MasterNodeSystem get() {
        if(mns == null)
            mns = new MasterNodeSystem();
        return mns;
    }
}
