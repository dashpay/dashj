package org.bitcoinj.core;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.darkcoinj.DarkSendSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Hash Engineering on 2/20/2016.
 */
public class MasternodeManager extends Message {
    private static final Logger log = LoggerFactory.getLogger(MasternodeManager.class);

    public static final int MASTERNODES_DUMP_SECONDS =              (15*60);
    public static final int MASTERNODES_DSEG_SECONDS    =           (3*60*60);
    // critical section to protect the inner data structures
    //mutable CCriticalSection cs;
    ReentrantLock lock = Threading.lock("MasternodeManager");

    // critical section to protect the inner data structures specifically on messaging
    //mutable CCriticalSection cs_process_message;
    ReentrantLock lock_messages = Threading.lock("MasternodeManager-Messages");

    // map to hold all MNs
    ArrayList<Masternode> vMasternodes;// = new ArrayList<Masternode>();
    // who's asked for the Masternode list and the last time
    HashMap<NetAddress, Long> mAskedUsForMasternodeList;// = new HashMap<NetAddress, Long>();
    // who we asked for the Masternode list and the last time
        HashMap<NetAddress, Long> mWeAskedForMasternodeList;// = new HashMap<NetAddress, Long>();
    // which Masternodes we've asked for
    HashMap<TransactionOutPoint, Long> mWeAskedForMasternodeListEntry;// = new HashMap<TransactionOutPoint, Long>();

    // Keep track of all broadcasts I've seen
    public HashMap<Sha256Hash, MasternodeBroadcast> mapSeenMasternodeBroadcast;// = new HashMap<Sha256Hash, MasternodeBroadcast>();
    // Keep track of all pings I've seen
    public HashMap<Sha256Hash, MasternodePing> mapSeenMasternodePing;// = new HashMap<Sha256Hash, MasternodePing>();

    // keep track of dsq count to prevent masternodes from gaming darksend queue
    long nDsqCount;

    //internal parameters
    AbstractBlockChain blockChain;
    void setBlockChain(AbstractBlockChain blockChain) { this.blockChain = blockChain; }

    Context context;

    public MasternodeManager(Context context)
    {
        super(context.getParams());
        nDsqCount = 0;

        // map to hold all MNs
        vMasternodes = new ArrayList<Masternode>();
        // who's asked for the Masternode list and the last time
        mAskedUsForMasternodeList = new HashMap<NetAddress, Long>();
        // who we asked for the Masternode list and the last time
        mWeAskedForMasternodeList = new HashMap<NetAddress, Long>();
        // which Masternodes we've asked for
        mWeAskedForMasternodeListEntry = new HashMap<TransactionOutPoint, Long>();

        // Keep track of all broadcasts I've seen
        mapSeenMasternodeBroadcast = new HashMap<Sha256Hash, MasternodeBroadcast>();
        // Keep track of all pings I've seen
        mapSeenMasternodePing = new HashMap<Sha256Hash, MasternodePing>();


        context = Context.get();

        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeManagerListener>>();
    }

    public MasternodeManager(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
        context = Context.get();

        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeManagerListener>>();
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

    public int calculateMessageSizeInBytes()
    {
        int size = 0;

        lock.lock();
        try {
            size += VarInt.sizeOf(vMasternodes.size());

            for (Masternode mn : vMasternodes) {
                size += mn.calculateMessageSizeInBytes();
            }
            size += VarInt.sizeOf(mAskedUsForMasternodeList.size());
            for (NetAddress na : mAskedUsForMasternodeList.keySet()) {
                size += na.MESSAGE_SIZE;
                size += 8;
            }
            return size;
        }
        finally {
            lock.unlock();
        }
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
            cursor += mn.getMessageSize();
            vMasternodes.add(mn);

            //mn.calculateScore(0, Sha256Hash.twiceOf(mn.pubkey.getBytes()));
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
        //READWRITE(mWeAskedForMasternodeList);
        size = (int)readVarInt();
        mWeAskedForMasternodeList = new HashMap<NetAddress, Long>(size);
        for(int i = 0; i < size; ++i)
        {
            NetAddress ma = new NetAddress(params, payload, cursor, 0);
            cursor += ma.getMessageSize();
            long x = readInt64();
            mAskedUsForMasternodeList.put(ma, x);
        }
        //READWRITE(mWeAskedForMasternodeListEntry);
        size = (int)readVarInt();
        mWeAskedForMasternodeListEntry = new HashMap<TransactionOutPoint, Long>(size);
        for(int i = 0; i < size; ++i)
        {
            TransactionOutPoint out = new TransactionOutPoint(params, payload, cursor);
            cursor += out.getMessageSize();
            long x = readInt64();
            mWeAskedForMasternodeListEntry.put(out, x);
        }

        //READWRITE(nDsqCount);
        nDsqCount = readUint32();

        //READWRITE(mapSeenMasternodeBroadcast);
        size = (int)readVarInt();
        mapSeenMasternodeBroadcast = new HashMap<Sha256Hash, MasternodeBroadcast>(size);
        for(int i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            MasternodeBroadcast mb = new MasternodeBroadcast(params, payload, cursor);
            cursor += mb.getMessageSize();
            mapSeenMasternodeBroadcast.put(hash, mb);
        }
        //READWRITE(mapSeenMasternodePing);
        size = (int)readVarInt();
        mapSeenMasternodePing = new HashMap<Sha256Hash, MasternodePing>(size);
        for(int i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            MasternodePing mb = new MasternodePing(params, payload, cursor);
            cursor += mb.getMessageSize();
            mapSeenMasternodePing.put(hash, mb);
        }

        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        lock.lock();
        try {
            stream.write(new VarInt(vMasternodes.size()).encode());
            for (Masternode mn : vMasternodes) {
                mn.bitcoinSerialize(stream);
            }
            stream.write(new VarInt(mAskedUsForMasternodeList.size()).encode());
            for(Iterator<Map.Entry<NetAddress, Long>> it1= mAskedUsForMasternodeList.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<NetAddress, Long> entry = it1.next();
                entry.getKey().bitcoinSerialize(stream);
                Utils.int64ToByteStreamLE(entry.getValue(), stream);
            }
            //READWRITE(mWeAskedForMasternodeList);
            stream.write(new VarInt(mWeAskedForMasternodeList.size()).encode());

            for(Iterator<Map.Entry<NetAddress, Long>> it1= mWeAskedForMasternodeList.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<NetAddress, Long> entry = it1.next();
                entry.getKey().bitcoinSerialize(stream);
                Utils.int64ToByteStreamLE(entry.getValue(), stream);
            }
            //READWRITE(mWeAskedForMasternodeListEntry);
            stream.write(new VarInt(mWeAskedForMasternodeListEntry.size()).encode());
            for(Iterator<Map.Entry<TransactionOutPoint, Long>> it1= mWeAskedForMasternodeListEntry.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<TransactionOutPoint, Long> entry = it1.next();
                entry.getKey().bitcoinSerialize(stream);
                Utils.int64ToByteStreamLE(entry.getValue(), stream);
            }
            //READWRITE(nDsqCount);
            Utils.uint32ToByteStreamLE(nDsqCount, stream);
            //READWRITE(mapSeenMasternodeBroadcast);
            stream.write(new VarInt(mapSeenMasternodeBroadcast.size()).encode());
            for(Iterator<Map.Entry<Sha256Hash, MasternodeBroadcast>> it1= mapSeenMasternodeBroadcast.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<Sha256Hash, MasternodeBroadcast> entry = it1.next();
                stream.write(entry.getKey().getReversedBytes());
                entry.getValue().bitcoinSerialize(stream);
            }
            //READWRITE(mapSeenMasternodePing);
            stream.write(new VarInt(mapSeenMasternodePing.size()).encode());
            for(Iterator<Map.Entry<Sha256Hash, MasternodePing>> it1= mapSeenMasternodePing.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<Sha256Hash, MasternodePing> entry = it1.next();
                stream.write(entry.getKey().getReversedBytes());
                entry.getValue().bitcoinSerialize(stream);
            }
            //TODO: add the rest
        } finally {
            lock.unlock();
        }
    }

    void processMasternodeBroadcast(MasternodeBroadcast mnb)
    {
        //log.info("processMasternodeBroadcast:  hash={}", mnb.getHash());
        lock.lock();
        try {
            if (mapSeenMasternodeBroadcast.containsKey(mnb.getHash())) { //seen
                context.masternodeSync.addedMasternodeList(mnb.getHash());
                return;
            }

            mapSeenMasternodeBroadcast.put(mnb.getHash(), mnb);
        } finally {
            lock.unlock();
        }

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
            //context.masternodeSync.addedMasternodeList(mnb.getHash());
        } else {
            log.info("mnb - Rejected Masternode entry "+ mnb.address.toString());

            //if (nDoS > 0)
//                Misbehaving(pfrom->GetId(), nDoS);
        }
    }
    void processMasternodePing(Peer peer, MasternodePing mnp)
    {
        //log.info("masternode - mnp - Masternode ping(hash={}, vin: {}", mnp.getHash(), mnp.vin.toString());

        if(mapSeenMasternodePing.containsKey(mnp.getHash()))
            return; //seen
        lock.lock();
        try {
            mapSeenMasternodePing.put(mnp.getHash(), mnp);
        } finally {
            lock.unlock();
        }

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
    public void updateMasternodePing(MasternodePing lastPing)
    {
        lock.lock();
        try {
            mapSeenMasternodePing.put(lastPing.getHash(), lastPing);
        } finally {
            lock.unlock();
        }
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
                queueOnSyncStatusChanged();
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

    public int countEnabled() { return countEnabled(-1); }
    public int countEnabled(int protocolVersion)
    {
        int i = 0;
        protocolVersion = protocolVersion == -1 ? context.masternodePayments.getMinMasternodePaymentsProto() : protocolVersion;

        lock.lock();
        try {
            //BOOST_FOREACH(CMasternode& mn, vMasternodes)
            for (Masternode mn : vMasternodes) {
                mn.check();
                if (mn.protocolVersion < protocolVersion || !mn.isEnabled()) continue;
                i++;
            }
        } finally {
            lock.unlock();
        }

        return i;
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

                    //vMasternodes.remove(mn);
                    it.remove();
                    queueOnSyncStatusChanged();
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
            if((head.getHeight() - 2050) > height)
                return null;

            StoredBlock cursor = head;
            while (cursor != null && cursor.getHeight() != height) {
                cursor = cursor.getPrev(blockStore);
            }

            return cursor != null ? cursor.getHeader().getHash() : null;

        } catch(BlockStoreException x)
        {
            return null;
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

        //Added to speed things up
        if(context.isLiteMode())
            return -3; // We don't have a masternode list

        Masternode mnExisting = find(vin);
        if(mnExisting == null)
            return -1;

        Sha256Hash hash = getBlockHash(nBlockHeight);
        if(hash == null) {
            return -2;
        }
         // scan for winner
        else
        {
            lock.lock();
            try {
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
            } finally {
                lock.unlock();
            }
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

    public void checkAndRemove() { checkAndRemove(false); }
    public void checkAndRemove(boolean forceExpiredRemoval)
    {
        check();

        lock.lock();
        try {

            //remove inactive and outdated
            //vector<CMasternode>::iterator it = vMasternodes.begin();
            Iterator<Masternode> it = vMasternodes.iterator();

            while (it.hasNext()) {
                Masternode mn = it.next();
                if (mn.activeState == Masternode.MASTERNODE_REMOVE ||
                        mn.activeState == Masternode.MASTERNODE_VIN_SPENT ||
                        (forceExpiredRemoval && mn.activeState == Masternode.MASTERNODE_EXPIRED) ||
                        mn.protocolVersion < context.masternodePayments.getMinMasternodePaymentsProto()) {
                    log.info("masternode-CMasternodeMan: Removing inactive Masternode {} - {} now", mn.address.toString(), size() - 1);

                    //erase all of the broadcasts we've seen from this vin
                    // -- if we missed a few pings and the node was removed, this will allow is to get it back without them
                    //    sending a brand new mnb
                    //map<uint256, CMasternodeBroadcast>::iterator it3 = mapSeenMasternodeBroadcast.begin();
                    Iterator<Map.Entry<Sha256Hash, MasternodeBroadcast>> it3 = mapSeenMasternodeBroadcast.entrySet().iterator();
                    while (it3.hasNext()) {
                        Map.Entry<Sha256Hash, MasternodeBroadcast> mb = it3.next();
                        if (mb.getValue().vin == mn.vin) {
                            context.masternodeSync.mapSeenSyncMNB.remove(mb.getKey());
                            //mapSeenMasternodeBroadcast.remove(mb.getKey(), mb.getValue());
                            it3.remove();
                        }
                    }

                    // allow us to ask for this masternode again if we see another ping
                    //map<COutPoint, int64_t>::iterator it2 = mWeAskedForMasternodeListEntry.begin();
                    Iterator<Map.Entry<TransactionOutPoint, Long>> it2 = mWeAskedForMasternodeListEntry.entrySet().iterator();
                    while (it2.hasNext()) {
                        Map.Entry<TransactionOutPoint, Long> e = it2.next();
                        if (e.getKey() == mn.vin.getOutpoint()) {
                            //mWeAskedForMasternodeListEntry.remove(e.getKey(), e.getValue());
                            it2.remove();
                        }
                    }

                    //it = vMasternodes.erase(it);
                    it.remove();
                    queueOnSyncStatusChanged();
                } else {
                    //++it;
                }
            }

            // check who's asked for the Masternode list
            //map<CNetAddr, int64_t>::iterator it1 = mAskedUsForMasternodeList.begin();
            Iterator<Map.Entry<NetAddress, Long>> it1 = mAskedUsForMasternodeList.entrySet().iterator();
            while (it1.hasNext()) {
                Map.Entry<NetAddress, Long> e = it1.next();
                if (e.getValue() < Utils.currentTimeSeconds()) {
                    //mAskedUsForMasternodeList.erase(it1++);
                    it1.remove();
                } else {
                    //++it1;
                }
            }

            // check who we asked for the Masternode list
            //it1 = mWeAskedForMasternodeList.begin();
            it1 = mWeAskedForMasternodeList.entrySet().iterator();
            while (it1.hasNext()) {
                Map.Entry<NetAddress, Long> e = it1.next();
                if (e.getValue() < Utils.currentTimeSeconds()) {
                    //mWeAskedForMasternodeList.erase(it1++);
                    it1.remove();
                } else {
                    //++it1;
                }
            }

            // check which Masternodes we've asked for
            //map<COutPoint, int64_t>::iterator it2 = mWeAskedForMasternodeListEntry.begin();
            Iterator<Map.Entry<TransactionOutPoint, Long>> it2 = mWeAskedForMasternodeListEntry.entrySet().iterator();
            while (it2.hasNext()) {
                Map.Entry<TransactionOutPoint, Long> e = it2.next();
                if (e.getValue() < Utils.currentTimeSeconds()) {
                    //mWeAskedForMasternodeListEntry.erase(it2++);
                    it2.remove();
                } else {
                    //++it2;
                }
            }

            // remove expired mapSeenMasternodeBroadcast
            Iterator<Map.Entry<Sha256Hash, MasternodeBroadcast>> it3 = mapSeenMasternodeBroadcast.entrySet().iterator();

            while (it3.hasNext()) {
                Map.Entry<Sha256Hash, MasternodeBroadcast> mb = it3.next();
                if (mb.getValue().lastPing.sigTime < Utils.currentTimeSeconds() - (Masternode.MASTERNODE_REMOVAL_SECONDS * 2)) {
                    //mapSeenMasternodeBroadcast.erase(it3++);

                    context.masternodeSync.mapSeenSyncMNB.remove(mb.getValue().getHash());

                    it3.remove();
                } else {
                    //++it3;
                }
            }

            // remove expired mapSeenMasternodePing
            Iterator<Map.Entry<Sha256Hash, MasternodePing>> it4 = mapSeenMasternodePing.entrySet().iterator();
            while (it4.hasNext()) {
                Map.Entry<Sha256Hash, MasternodePing> mp = it4.next();
                if (mp.getValue().sigTime < Utils.currentTimeSeconds() - (Masternode.MASTERNODE_REMOVAL_SECONDS * 2)) {
                    //mapSeenMasternodePing.erase(it4++);
                    it4.remove();
                } else {
                    //++it4;
                }
            }
        }
        finally {
            lock.unlock();
        }

    }

    void dsegUpdate(Peer pnode)
    {
        lock.lock();

        try {

            if (params.getId().equals(NetworkParameters.ID_MAINNET)) {
                if (!(pnode.getAddress().getAddr().isAnyLocalAddress() || pnode.getAddress().getAddr().isLoopbackAddress())) {
                   //std::map < CNetAddr, int64_t >::iterator it = mWeAskedForMasternodeList.find(pnode -> addr);
                    Iterator<Map.Entry<NetAddress, Long>> it = mWeAskedForMasternodeList.entrySet().iterator();
                    if (it.hasNext()) {

                        if (Utils.currentTimeSeconds() < it.next().getValue()){
                            log.info("dseg - we already asked {} for the list; skipping...", pnode.getAddress().toString());
                            return;
                        }
                    }
                }
            }

            pnode.sendMessage(new DarkSendEntryGetMessage(new TransactionInput(params,null, new byte[0])));
            //pnode -> PushMessage("dseg", CTxIn());
            long askAgain = Utils.currentTimeSeconds() + MasternodeManager.MASTERNODES_DSEG_SECONDS;
            mWeAskedForMasternodeList.put(new NetAddress(pnode.getAddress().getAddr()),askAgain);
        } finally {
            lock.unlock();
        }
    }

    public void processMasternodeConnections()
    {
        //we don't care about this for regtest
        if(params.getId().equals(NetworkParameters.ID_REGTEST)) return;

        ReentrantLock nodeLock = context.peerGroup.getLock();

        nodeLock.lock();
        try {

            //BOOST_FOREACH(CNode * pnode, vNodes)
            for(Peer pnode : context.peerGroup.getConnectedPeers())
            {
                if (pnode.isDarkSendMaster()) {
                    if (context.darkSendPool.submittedToMasternode != null && pnode.getAddress().getAddr().equals(context.darkSendPool.submittedToMasternode.address.getAddr()))
                        continue;
                    log.info("Closing Masternode connection {}", pnode.getAddress());
                    //pnode -> fDisconnect = true;
                    //pnode.close();
                }

            }
        } finally {
            nodeLock.unlock();
        }
    }


    /******************************************************************************************************************/

    //region Event listeners
    private transient CopyOnWriteArrayList<ListenerRegistration<MasternodeManagerListener>> eventListeners;
    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addEventListener(MasternodeManagerListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addEventListener(MasternodeManagerListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        eventListeners.add(new ListenerRegistration<MasternodeManagerListener>(listener, executor));
        //keychain.addEventListener(listener, executor);
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeEventListener(MasternodeManagerListener listener) {
        //keychain.removeEventListener(listener);
        return ListenerRegistration.removeFromList(listener, eventListeners);
    }

    private void queueOnSyncStatusChanged() {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<MasternodeManagerListener> registration : eventListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onMasternodeCountChanged(vMasternodes.size());
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onMasternodeCountChanged(vMasternodes.size());
                    }
                });
            }
        }
    }

}
