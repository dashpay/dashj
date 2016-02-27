package org.bitcoinj.core;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.darkcoinj.DarkSendSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Hash Engineering on 2/20/2016.
 */
public class MasternodeManager extends Message {
    private static final Logger log = LoggerFactory.getLogger(MasternodeManager.class);
    // critical section to protect the inner data structures
    //mutable CCriticalSection cs;
    ReentrantLock lock = Threading.lock("MasternodeManager");

    // critical section to protect the inner data structures specifically on messaging
    //mutable CCriticalSection cs_process_message;
    ReentrantLock lock_messages = Threading.lock("MasternodeManager-Messages");

    // map to hold all MNs
    ArrayList<Masternode> vMasternodes = new ArrayList<Masternode>();
    // who's asked for the Masternode list and the last time
    HashMap<NetAddress, Long> mAskedUsForMasternodeList = new HashMap<NetAddress, Long>();
    // who we asked for the Masternode list and the last time
        HashMap<NetAddress, Long> mWeAskedForMasternodeList = new HashMap<NetAddress, Long>();
    // which Masternodes we've asked for
    HashMap<TransactionOutPoint, Long> mWeAskedForMasternodeListEntry = new HashMap<TransactionOutPoint, Long>();

    // Keep track of all broadcasts I've seen
    public HashMap<Sha256Hash, MasternodeBroadcast> mapSeenMasternodeBroadcast = new HashMap<Sha256Hash, MasternodeBroadcast>();
    // Keep track of all pings I've seen
    public HashMap<Sha256Hash, MasternodePing> mapSeenMasternodePing = new HashMap<Sha256Hash, MasternodePing>();

    // keep track of dsq count to prevent masternodes from gaming darksend queue
    long nDsqCount;

    //internal parameters
    AbstractBlockChain blockChain;
    void setBlockChain(AbstractBlockChain blockChain) { this.blockChain = blockChain; }

    public MasternodeManager(NetworkParameters params)
    {
        super(params);
        nDsqCount = 0;
    }

    public MasternodeManager(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
    }

    public MasternodeManager(Masternode other)
    {

    }

    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
            length = calcLength(payload, offset);
            cursor = offset + length;
        }
    }
    protected static int calcLength(byte[] buf, int offset) {
        int cursor = 0;

        return cursor - offset;
    }
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        int size = (int)readVarInt();

        vMasternodes = new ArrayList<Masternode>(size);
        for (int i = 0; i < size; ++i)
        {
            Masternode mn = new Masternode(params, payload, cursor);
            vMasternodes.add(mn);
            cursor =+ mn.getMessageSize();
        }

        size = (int)readVarInt();
        mAskedUsForMasternodeList = new HashMap<NetAddress, Long>(size);
        for(int i = 0; i < size; ++i)
        {
            NetAddress ma = new NetAddress(params, payload, cursor, 0);
            cursor += ma.getMessageSize();
            long x = readInt64();
            mAskedUsForMasternodeList.put(ma, x);
        }
        //TODO: add the rest

        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(new VarInt(vMasternodes.size()).encode());
        for(Masternode mn : vMasternodes)
        {
            mn.bitcoinSerialize(stream);
        }
        stream.write(new VarInt(mAskedUsForMasternodeList.size()).encode());
        for(NetAddress na: mAskedUsForMasternodeList.keySet())
        {
            na.bitcoinSerialize(stream);
            Utils.int64ToByteStreamLE(mAskedUsForMasternodeList.get(na), stream);
        }
        //TODO: add the rest
    }

    void processMasternodeBroadcast(MasternodeBroadcast mnb)
    {
        if(mapSeenMasternodeBroadcast.containsKey(mnb.getHash())) { //seen
            params.masternodeSync.addedMasternodeList(mnb.getHash());
            return;
        }
        mapSeenMasternodeBroadcast.put(mnb.getHash(), mnb);

        int nDoS = 0;
        if(!mnb.checkAndUpdate()){

            //if(nDoS > 0)
            //    Misbehaving(pfrom->GetId(), nDoS);

            //failed
            return;
        }

        // make sure the vout that was signed is related to the transaction that spawned the Masternode
        //  - this is expensive, so it's only done once per Masternode
        if(!DarkSendSigner.isVinAssociatedWithPubkey(params, mnb.vin, mnb.pubkey)) {
            log.info("mnb - Got mismatched pubkey and vin");
            //Misbehaving(pfrom->GetId(), 33);
            return;
        }

        // make sure it's still unspent
        //  - this is checked later by .check() in many places and by ThreadCheckDarkSendPool()
        if(mnb.checkInputsAndAdd()) {
            // use this as a peer
            //TODO:  Is this possible?
            //addrman.Add(CAddress(mnb.addr), pfrom->addr, 2*60*60);
            params.masternodeSync.addedMasternodeList(mnb.getHash());
        } else {
            log.info("mnb - Rejected Masternode entry "+ mnb.address.toString());

            //if (nDoS > 0)
//                Misbehaving(pfrom->GetId(), nDoS);
        }
    }
    void processMasternodePing(Peer peer, MasternodePing mnp)
    {
        log.info("masternode - mnp - Masternode ping, vin: " + mnp.vin.toString());

        if(mapSeenMasternodePing.containsKey(mnp.getHash())) return; //seen
        mapSeenMasternodePing.put(mnp.getHash(), mnp);

        int nDoS = 0;
        if(mnp.checkAndUpdate()) return;

        if(nDoS > 0) {
            // if anything significant failed, mark that node
            //Misbehaving(pfrom->GetId(), nDoS);
        } else {
            // if nothing significant failed, search existing Masternode list
            Masternode pmn = find(mnp.vin);
            // if it's known, don't ask for the mnb, just return
            if(pmn != null) return;
        }

        // something significant is broken or mn is unknown,
        // we might have to ask for a masternode entry once
        askForMN(peer, mnp.vin);
    }

    boolean add(Masternode mn)
    {
        try {
            lock.lock();

            if (!mn.isEnabled())
                return false;

            Masternode pmn = find(mn.vin);
            if (pmn == null) {
                log.info("masternode - MasternodeMan: Adding new Masternode "+mn.address.toString()+" - "+(size() + 1)+" now");
                vMasternodes.add(mn);
                return true;
            }

            return false;
        } finally {
          lock.unlock();
        }
    }

    Masternode find(Script payee)
    {
        //LOCK(cs);
        lock.lock();
        try {
            Script payee2;

            //BOOST_FOREACH(CMasternode& mn, vMasternodes)
            for (Masternode mn : vMasternodes) {
                //payee2 = GetScriptForDestination(mn.pubkey.GetID());
                payee2 = ScriptBuilder.createOutputScript(mn.pubkey.getECKey());

                if (payee2 == payee)
                    return mn;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public Masternode find(TransactionInput vin)
    {
        lock.lock();
        try {

            //BOOST_FOREACH(CMasternode & mn, vMasternodes)
            for (Masternode mn : vMasternodes)
            {
                if (mn.vin.getOutpoint().equals(vin.getOutpoint()))
                    return mn;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }


    Masternode find(PublicKey pubKeyMasternode)
    {
        lock.lock();
        try {
            //BOOST_FOREACH(CMasternode & mn, vMasternodes)
            for (Masternode mn : vMasternodes)
            {
                if (mn.pubkey2.equals(pubKeyMasternode))
                    return mn;
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public void remove(TransactionInput vin)
    {
        try {
            lock.lock();


            //vector<CMasternode>::iterator it = vMasternodes.begin();
            Iterator<Masternode> it = vMasternodes.iterator();
            while (it.hasNext()) {
                Masternode mn = it.next();
                if (mn.vin.equals(vin)){
                    log.info("masternode - CMasternodeMan: Removing Masternode %s "+mn.address.toString()+"- "+(size()-1)+" now");

                    vMasternodes.remove(mn);
                    break;
                }

            }
        } finally {
            lock.unlock();
        }
    }
    int size() { return vMasternodes.size(); }

    public String toString()
    {
        String result;

        result = "Masternodes: " + (int)vMasternodes.size() +
                ", peers who asked us for Masternode list: " + (int)mAskedUsForMasternodeList.size() +
                ", peers we asked for Masternode list: " + (int)mWeAskedForMasternodeList.size() +
                ", entries in Masternode list we asked for: " + (int)mWeAskedForMasternodeListEntry.size() +
                ", nDsqCount: " + (int)nDsqCount;

        return result;
    }

    public void askForMN(Peer pnode, TransactionInput vin)
    {
        //std::map<COutPoint, int64_t>::iterator i = mWeAskedForMasternodeListEntry.find(vin.prevout);

        Long i = mWeAskedForMasternodeListEntry.get(vin.getOutpoint());

        if (i != null)
        {
            long t = i;
            if (Utils.currentTimeSeconds() < t) return; // we've asked recently
        }

        // ask for the mnb info once from the node that sent mnp

        log.info("CMasternodeMan::AskForMN - Asking node for missing entry, vin: "+ vin.toString());
        //pnode->PushMessage("dseg", vin);
        pnode.sendMessage(new DarkSendEntryGetMessage(vin));
        long askAgain = Utils.currentTimeSeconds() + MasternodePing.MASTERNODE_MIN_MNP_SECONDS;
        mWeAskedForMasternodeListEntry.put(vin.getOutpoint(), askAgain);
    }

    Sha256Hash getBlockHash(long height)
    {
        try {
            StoredBlock head = blockChain.getChainHead();

            BlockStore blockStore = blockChain.getBlockStore();

            //long heightToFind = (height - 100) - ((height-100)%100);

            //If header is not stored, then return the tip
            //TODO:  remove this or the whole function;
            if((head.getHeight() - SPVBlockStore.DEFAULT_NUM_HEADERS) > height)
                return head.getHeader().getHash();

            StoredBlock cursor = head;
            while (head.getHeight() != height) {
                cursor = cursor.getPrev(blockStore);
            }
            return cursor.getHeader().getHash();

        } catch(BlockStoreException x)
        {
            return blockChain.getChainHead().getHeader().getHash();
        }
    }

    public int getMasternodeRank(TransactionInput vin, long nBlockHeight, int minProtocol)
    {
        return getMasternodeRank(vin, nBlockHeight, minProtocol, true);
    }

    class CompareScoreTxIn<Object> implements Comparator<Object>
    {
        public int compare(Object t1, Object t2) {
            Pair<Long, TransactionInput> p1 = (Pair<Long, TransactionInput>)t1;
            Pair<Long, TransactionInput> p2 = (Pair<Long, TransactionInput>)t1;

            if(p1.getFirst() < p2.getFirst())
                return -1;
            if(p1.getFirst() == p2.getFirst())
                return 0;
            else return 1;
        }
    };

    public int getMasternodeRank(TransactionInput vin, long nBlockHeight, int minProtocol, boolean fOnlyActive)
    {
        //std::vector<pair<int64_t, CTxIn> > vecMasternodeScores;
        ArrayList<Pair<Long, TransactionInput>> vecMasternodeScores = new ArrayList<Pair<Long, TransactionInput>>(3000);

        //make sure we know about this block
        //uint256 hash = 0;
        //if(!GetBlockHash(hash, nBlockHeight)) return -1;
        if(blockChain.getChainHead().getHeight() < nBlockHeight)
            return -1;

         // scan for winner
        for(Masternode mn : vMasternodes) {
            if(mn.protocolVersion < minProtocol) continue;
            if(fOnlyActive) {
                mn.check();
                if(!mn.isEnabled()) continue;
            }
            Sha256Hash n = mn.calculateScore(1, nBlockHeight);
            //int64_t n2 = n.GetCompact(false);
            long n2 = Utils.encodeCompactBits(new BigInteger(n.getBytes()));

            vecMasternodeScores.add(new Pair<Long, TransactionInput>(n2, mn.vin));
        }


        //sort(vecMasternodeScores.rbegin(), vecMasternodeScores.rend(), CompareScoreTxIn());
        //vecMasternodeScores.sort(new CompareScoreTxIn());
        Arrays.sort(vecMasternodeScores.toArray(), new CompareScoreTxIn());



        int rank = 0;
        for (Pair<Long, TransactionInput> s : vecMasternodeScores) {
            rank++;
            if(s.getSecond().getOutpoint().equals(vin.getOutpoint())) {
                return rank;
            }
        }

        return -1;
    }

    public int getMasternodeRank(TransactionInput vin, Sha256Hash hash, int minProtocol, boolean fOnlyActive)
    {
        //std::vector<pair<int64_t, CTxIn> > vecMasternodeScores;
        ArrayList<Pair<Long, TransactionInput>> vecMasternodeScores = new ArrayList<Pair<Long, TransactionInput>>(3000);

        //make sure we know about this block
        //uint256 hash = 0;
        //if(!GetBlockHash(hash, nBlockHeight)) return -1;
        //if(blockChain.getChainHead().getHeight() < nBlockHeight)
        //   return -1;

        // scan for winner
        for(Masternode mn : vMasternodes) {
            if(mn.protocolVersion < minProtocol) continue;
            if(fOnlyActive) {
                mn.check();
                if(!mn.isEnabled()) continue;
            }
            Sha256Hash n = mn.calculateScore(1, hash);
            //int64_t n2 = n.GetCompact(false);
            long n2 = Utils.encodeCompactBits(new BigInteger(n.getBytes()));

            vecMasternodeScores.add(new Pair<Long, TransactionInput>(n2, mn.vin));
        }


        //sort(vecMasternodeScores.rbegin(), vecMasternodeScores.rend(), CompareScoreTxIn());
        //vecMasternodeScores.sort(new CompareScoreTxIn());

        Arrays.sort(vecMasternodeScores.toArray(), new CompareScoreTxIn());



        int rank = 0;
        for (Pair<Long, TransactionInput> s : vecMasternodeScores) {
            rank++;
            if(s.getSecond().getOutpoint().equals(vin.getOutpoint())) {
                return rank;
            }
        }

        return -1;
    }
    void check()
    {
        lock.lock();


        for(Masternode mn : vMasternodes){
            mn.check();
        }
        lock.unlock();

    }

    public void checkAndRemove(boolean forceExpiredRemoval)
    {
        check();

        lock.lock();

        //remove inactive and outdated
        //vector<CMasternode>::iterator it = vMasternodes.begin();
        Iterator<Masternode> it = vMasternodes.iterator();

        while(it.hasNext()){
            Masternode mn = it.next();
            if(mn.activeState == Masternode.MASTERNODE_REMOVE ||
                    mn.activeState == Masternode.MASTERNODE_VIN_SPENT ||
                    (forceExpiredRemoval && mn.activeState == Masternode.MASTERNODE_EXPIRED) ||
            mn.protocolVersion < params.masternodePayments.getMinMasternodePaymentsProto()) {
                log.info("masternode-CMasternodeMan: Removing inactive Masternode {} - {} now", mn.address.toString(), size() - 1);

                //erase all of the broadcasts we've seen from this vin
                // -- if we missed a few pings and the node was removed, this will allow is to get it back without them
                //    sending a brand new mnb
                //map<uint256, CMasternodeBroadcast>::iterator it3 = mapSeenMasternodeBroadcast.begin();
                Iterator<Map.Entry<Sha256Hash, MasternodeBroadcast>> it3 = mapSeenMasternodeBroadcast.entrySet().iterator();
                while(it3.hasNext()){
                    Map.Entry<Sha256Hash, MasternodeBroadcast> mb = it3.next();
                    if(mb.getValue().vin == mn.vin){
                        params.masternodeSync.mapSeenSyncMNB.remove(mb.getKey());
                        //mapSeenMasternodeBroadcast.remove(mb.getKey(), mb.getValue());
                        it3.remove();
                    }
                }

                // allow us to ask for this masternode again if we see another ping
                //map<COutPoint, int64_t>::iterator it2 = mWeAskedForMasternodeListEntry.begin();
                Iterator<Map.Entry<TransactionOutPoint, Long>> it2 = mWeAskedForMasternodeListEntry.entrySet().iterator();
                while(it2.hasNext()){
                    Map.Entry<TransactionOutPoint, Long> e = it2.next();
                    if(e.getKey() == mn.vin.getOutpoint()){
                        //mWeAskedForMasternodeListEntry.remove(e.getKey(), e.getValue());
                        it2.remove();
                    }
                }

                //it = vMasternodes.erase(it);
                it.remove();
            } else {
                //++it;
            }
        }

        // check who's asked for the Masternode list
        //map<CNetAddr, int64_t>::iterator it1 = mAskedUsForMasternodeList.begin();
        Iterator<Map.Entry<NetAddress, Long>> it1 = mAskedUsForMasternodeList.entrySet().iterator();
        while(it1.hasNext()){
            Map.Entry<NetAddress, Long> e = it1.next();
            if(e.getValue() < Utils.currentTimeSeconds()) {
                //mAskedUsForMasternodeList.erase(it1++);
                it.remove();
            } else {
                //++it1;
            }
        }

        // check who we asked for the Masternode list
        //it1 = mWeAskedForMasternodeList.begin();
        it1 = mWeAskedForMasternodeList.entrySet().iterator();
        while(it1.hasNext()){
            Map.Entry<NetAddress, Long> e = it1.next();
            if(e.getValue() < Utils.currentTimeSeconds()){
                //mWeAskedForMasternodeList.erase(it1++);
                it.remove();
            } else {
                //++it1;
            }
        }

        // check which Masternodes we've asked for
        //map<COutPoint, int64_t>::iterator it2 = mWeAskedForMasternodeListEntry.begin();
        Iterator<Map.Entry<TransactionOutPoint, Long>> it2 = mWeAskedForMasternodeListEntry.entrySet().iterator();
        while(it2.hasNext()){
            Map.Entry<TransactionOutPoint, Long> e = it2.next();
            if(e.getValue() < Utils.currentTimeSeconds()){
                //mWeAskedForMasternodeListEntry.erase(it2++);
                it2.remove();
            } else {
                //++it2;
            }
        }

        // remove expired mapSeenMasternodeBroadcast
        Iterator<Map.Entry<Sha256Hash, MasternodeBroadcast>> it3 = mapSeenMasternodeBroadcast.entrySet().iterator();

        while(it3.hasNext()){
            Map.Entry<Sha256Hash, MasternodeBroadcast> mb = it3.next();
            if(mb.getValue().lastPing.sigTime < Utils.currentTimeSeconds()-(Masternode.MASTERNODE_REMOVAL_SECONDS*2)){
                //mapSeenMasternodeBroadcast.erase(it3++);

                params.masternodeSync.mapSeenSyncMNB.remove(mb.getValue().getHash());

                it.remove();
            } else {
                //++it3;
            }
        }

        // remove expired mapSeenMasternodePing
        Iterator<Map.Entry<Sha256Hash, MasternodePing>> it4 = mapSeenMasternodePing.entrySet().iterator();
        while(it4.hasNext()){
            Map.Entry<Sha256Hash, MasternodePing> mp = it4.next();
            if(mp.getValue().sigTime < Utils.currentTimeSeconds()-(Masternode.MASTERNODE_REMOVAL_SECONDS*2)){
                //mapSeenMasternodePing.erase(it4++);
                it4.remove();
            } else {
                //++it4;
            }
        }
        lock.unlock();

    }

}
