package org.bitcoinj.core;

import org.bitcoinj.crypto.LinuxSecureRandom;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.darkcoinj.DarkSendSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.nio.ch.Net;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.MasterNodeSystem.MASTERNODE_REMOVAL_SECONDS;

/**
 * Created by Hash Engineering on 2/20/2016.
 */
public class MasternodeManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(MasternodeManager.class);

    public static final int MASTERNODES_DUMP_SECONDS =              (15*60);
    public static final int MASTERNODES_DSEG_SECONDS    =           (3*60*60);

    static final String SERIALIZATION_VERSION_STRING = "CMasternodeMan-Version-7";

    static final int DSEG_UPDATE_SECONDS        = 3 * 60 * 60;

    static final int LAST_PAID_SCAN_BLOCKS      = 100;

    static final int MIN_POSE_PROTO_VERSION     = 70203;
    static final int MAX_POSE_CONNECTIONS       = 10;
    static final int MAX_POSE_RANK              = 10;
    static final int MAX_POSE_BLOCKS            = 10;

    static final int MNB_RECOVERY_QUORUM_TOTAL      = 10;
    static final int MNB_RECOVERY_QUORUM_REQUIRED   = 6;
    static final int MNB_RECOVERY_MAX_ASK_ENTRIES   = 10;
    static final int MNB_RECOVERY_WAIT_SECONDS      = 60;
    static final int MNB_RECOVERY_RETRY_SECONDS     = 3 * 60 * 60;

    // Keep track of current block height
    int nCachedBlockHeight;

    // critical section to protect the inner data structures
    ReentrantLock lock = Threading.lock("MasternodeManager");

    // critical section to protect the inner data structures specifically on messaging
    ReentrantLock lock_messages = Threading.lock("MasternodeManager-Messages");

    // map to hold all MNs
    HashMap<TransactionOutPoint, Masternode> mapMasternodes;
    // who's asked for the Masternode list and the last time
    HashMap<NetAddress, Long> mAskedUsForMasternodeList;
    // who we asked for the Masternode list and the last time
    HashMap<NetAddress, Long> mWeAskedForMasternodeList;
    // which Masternodes we've asked for
    HashMap<TransactionOutPoint, HashMap<NetAddress, Long>> mWeAskedForMasternodeListEntry;// = new HashMap<TransactionOutPoint, Long>();
    // who we asked for the masternode verification
    HashMap<NetAddress, MasternodeVerification> mWeAskedForVerification;

    // these maps are used for masternode recovery from MASTERNODE_NEW_START_REQUIRED state
    HashMap<Sha256Hash, Pair< Long, Set<NetAddress> > > mMnbRecoveryRequests;
    HashMap<Sha256Hash, ArrayList<MasternodeBroadcast> > mMnbRecoveryGoodReplies;
    ArrayList< Pair<NetAddress, Sha256Hash> > listScheduledMnbRequestConnections;

    /// Set when masternodes are added, cleared when CGovernanceManager is notified
    boolean fMasternodesAdded;

    /// Set when masternodes are removed, cleared when CGovernanceManager is notified
    boolean fMasternodesRemoved;

    ArrayList<Sha256Hash> vecDirtyGovernanceObjectHashes;

    long nLastWatchdogVoteTime;

    // Keep track of all broadcasts I've seen
    public HashMap<Sha256Hash, Pair<Long, MasternodeBroadcast>> mapSeenMasternodeBroadcast;// = new HashMap<Sha256Hash, MasternodeBroadcast>();
    // Keep track of all pings I've seen
    public HashMap<Sha256Hash, MasternodePing> mapSeenMasternodePing;// = new HashMap<Sha256Hash, MasternodePing>();
    // Keep track of all verifications I've seen
    HashMap<Sha256Hash, MasternodeVerification> mapSeenMasternodeVerification;

    // keep track of dsq count to prevent masternodes from gaming darksend queue
    long nDsqCount;

    //internal parameters
    AbstractBlockChain blockChain;
    void setBlockChain(AbstractBlockChain blockChain) { this.blockChain = blockChain; }

    //Context context;

    public MasternodeManager(Context context)
    {
        super(context);
        mapMasternodes = new HashMap<TransactionOutPoint, Masternode>();
        mAskedUsForMasternodeList = new HashMap<NetAddress, Long>();
        mWeAskedForMasternodeList = new HashMap<NetAddress, Long>();
        mWeAskedForMasternodeListEntry = new HashMap<TransactionOutPoint, HashMap<NetAddress, Long>>();
        mWeAskedForVerification = new HashMap<NetAddress, MasternodeVerification>();
        mMnbRecoveryRequests = new HashMap<Sha256Hash, Pair<Long, Set<NetAddress>>>();
        mMnbRecoveryGoodReplies = new HashMap<Sha256Hash, ArrayList<MasternodeBroadcast>>();
        listScheduledMnbRequestConnections = new ArrayList<Pair<NetAddress, Sha256Hash>>();
        fMasternodesAdded = false;
        fMasternodesRemoved = false;
        vecDirtyGovernanceObjectHashes = new ArrayList<Sha256Hash>();
        nLastWatchdogVoteTime = 0;
        mapSeenMasternodeBroadcast = new HashMap<Sha256Hash, Pair<Long, MasternodeBroadcast>>();
        mapSeenMasternodePing = new HashMap<Sha256Hash, MasternodePing>();
        nDsqCount = 0;
        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeManagerListener>>();
    }

    public MasternodeManager(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
        context = Context.get();
        listScheduledMnbRequestConnections = new ArrayList<Pair<NetAddress, Sha256Hash>>();
        fMasternodesAdded = false;
        fMasternodesRemoved = false;
        vecDirtyGovernanceObjectHashes = new ArrayList<Sha256Hash>();
        nLastWatchdogVoteTime = 0;
        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeManagerListener>>();
    }

    /*public int calculateMessageSizeInBytes()
    {
        int size = 0;
        lock.lock();
        try {
            size += VarInt.sizeOf(mapMasternodes.size());

            for (Map.Entry<TransactionOutPoint, Masternode> entry : mapMasternodes.entrySet()) {
                entry.getKey().getMessageSize();
                entry.getValue().bitcoinSerialize(stream);
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
            for(Iterator<Map.Entry<TransactionOutPoint, HashMap<NetAddress, Long>>> it1= mWeAskedForMasternodeListEntry.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<TransactionOutPoint, HashMap<NetAddress, Long>> entry = it1.next();
                entry.getKey().bitcoinSerialize(stream);
                stream.write(new VarInt(entry.getValue().size()).encode());
                for(Map.Entry<NetAddress, Long> addLong: entry.getValue().entrySet()) {
                    addLong.getKey().bitcoinSerialize(stream);
                    Utils.int64ToByteStreamLE(addLong.getValue(), stream);
                }
            }

            stream.write(new VarInt(mMnbRecoveryRequests.size()).encode());
            for(Map.Entry<Sha256Hash, Pair<Long, Set<NetAddress>>> entry : mMnbRecoveryRequests.entrySet())
            {
                stream.write(entry.getKey().getReversedBytes());
                Utils.int64ToByteStreamLE(entry.getValue().getFirst(), stream);
                stream.write(new VarInt(entry.getValue().getSecond().size()).encode());
                for(NetAddress address : entry.getValue().getSecond()) {
                    address.bitcoinSerialize(stream);
                }
            }
            stream.write(new VarInt(mMnbRecoveryGoodReplies.size()).encode());
            for(Map.Entry<Sha256Hash, ArrayList<MasternodeBroadcast>> entry : mMnbRecoveryGoodReplies.entrySet())
            {
                stream.write(entry.getKey().getReversedBytes());
                stream.write(new VarInt(entry.getValue().size()).encode());
                for(MasternodeBroadcast mnb : entry.getValue()) {
                    mnb.bitcoinSerialize(stream);
                }
            }

            //READWRITE(nDsqCount);
            Utils.uint32ToByteStreamLE(nDsqCount, stream);
            //READWRITE(mapSeenMasternodeBroadcast);
            stream.write(new VarInt(mapSeenMasternodeBroadcast.size()).encode());
            for(Iterator<Map.Entry<Sha256Hash, Pair<Long, MasternodeBroadcast>>> it1= mapSeenMasternodeBroadcast.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<Sha256Hash, Pair<Long, MasternodeBroadcast>> entry = it1.next();
                stream.write(entry.getKey().getReversedBytes());
                Utils.int64ToByteStreamLE(entry.getValue().getFirst(), stream);
                entry.getValue().getSecond().bitcoinSerialize(stream);
            }
            //READWRITE(mapSeenMasternodePing);
            stream.write(new VarInt(mapSeenMasternodePing.size()).encode());
            for(Iterator<Map.Entry<Sha256Hash, MasternodePing>> it1= mapSeenMasternodePing.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<Sha256Hash, MasternodePing> entry = it1.next();
                stream.write(entry.getKey().getReversedBytes());
                entry.getValue().bitcoinSerialize(stream);
            }
            return size;
        }
        finally {
            lock.unlock();
        }
    }*/
    @Override
    protected void parse() throws ProtocolException {


        int size = (int)readVarInt();

        mapMasternodes = new HashMap<TransactionOutPoint, Masternode>();
        for (int i = 0; i < size; ++i)
        {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, payload, cursor);
            cursor += outPoint.getMessageSize();
            Masternode mn = new Masternode(params, payload, cursor);
            cursor += mn.getMessageSize();
            mapMasternodes.put(outPoint, mn);
        }

        size = (int)readVarInt();
        mAskedUsForMasternodeList = new HashMap<NetAddress, Long>();
        for(int i = 0; i < size; ++i)
        {
            NetAddress ma = new NetAddress(params, payload, cursor, 0);
            cursor += ma.getMessageSize();
            long x = readInt64();
            mAskedUsForMasternodeList.put(ma, x);
        }

        size = (int)readVarInt();
        mWeAskedForMasternodeList = new HashMap<NetAddress, Long>(size);
        for(int i = 0; i < size; ++i)
        {
            NetAddress ma = new NetAddress(params, payload, cursor, 0);
            cursor += ma.getMessageSize();
            long x = readInt64();
            mAskedUsForMasternodeList.put(ma, x);
        }

        size = (int)readVarInt();
        mWeAskedForMasternodeListEntry = new HashMap<TransactionOutPoint, HashMap<NetAddress, Long>>(size);
        for(int i = 0; i < size; ++i) {
            TransactionOutPoint out = new TransactionOutPoint(params, payload, cursor);
            cursor += out.getMessageSize();
            int countMap = (int)readVarInt();
            HashMap<NetAddress, Long> map = new HashMap<NetAddress, Long>(countMap);
            for(int j = 0; j < countMap; ++j) {
                NetAddress ma = new NetAddress(params, payload, cursor, 0);
                cursor += ma.getMessageSize();
                long x = readInt64();
                map.put(ma, x);
            }
            mWeAskedForMasternodeListEntry.put(out, map);
        }

        size = (int)readVarInt();
        mMnbRecoveryRequests = new HashMap<Sha256Hash, Pair<Long, Set<NetAddress>>>(size);
        for(int i = 0; i < size; ++i) {
            Sha256Hash hash = readHash();
            long x = readInt64();
            int countSet = (int)readVarInt();
            Set<NetAddress> addresses = new HashSet<NetAddress>(countSet);
            for(int j = 0; j < countSet; ++j)
            {
                NetAddress ma = new NetAddress(params, payload, cursor, 0);
                cursor += ma.getMessageSize();
                addresses.add(ma);
            }
            mMnbRecoveryRequests.put(hash, new Pair<Long, Set<NetAddress>>(x, addresses));
        }

        size = (int)readVarInt();
        mMnbRecoveryGoodReplies = new HashMap<Sha256Hash, ArrayList<MasternodeBroadcast>>(size);
        for(int i = 0; i < size; ++i) {
            Sha256Hash hash = readHash();
            int countList = (int)readVarInt();
            ArrayList<MasternodeBroadcast> mnbs = new ArrayList<MasternodeBroadcast>(countList);
            for(int j = 0; j < countList; ++j)
            {
                MasternodeBroadcast mnb = new MasternodeBroadcast(params, payload, cursor);
                cursor += mnb.getMessageSize();
                mnbs.add(mnb);
            }
            mMnbRecoveryGoodReplies.put(hash, mnbs);
        }

        //READWRITE(nDsqCount);
        nDsqCount = readUint32();

        //READWRITE(mapSeenMasternodeBroadcast);
        size = (int)readVarInt();
        mapSeenMasternodeBroadcast = new HashMap<Sha256Hash, Pair<Long, MasternodeBroadcast>>(size);
        for(int i = 0; i < size; ++i) {
            Sha256Hash hash = readHash();
            long x = readInt64();
            MasternodeBroadcast mnb = new MasternodeBroadcast(params, payload, cursor);
            cursor += mnb.getMessageSize();
            mapSeenMasternodeBroadcast.put(hash, new Pair<Long, MasternodeBroadcast>(x, mnb));
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
            stream.write(new VarInt(mapMasternodes.size()).encode());
            for (Map.Entry<TransactionOutPoint, Masternode> entry : mapMasternodes.entrySet()) {
                entry.getKey().bitcoinSerialize(stream);
                entry.getValue().bitcoinSerialize(stream);
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
            for(Iterator<Map.Entry<TransactionOutPoint, HashMap<NetAddress, Long>>> it1= mWeAskedForMasternodeListEntry.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<TransactionOutPoint, HashMap<NetAddress, Long>> entry = it1.next();
                entry.getKey().bitcoinSerialize(stream);
                stream.write(new VarInt(entry.getValue().size()).encode());
                for(Map.Entry<NetAddress, Long> addLong: entry.getValue().entrySet()) {
                    addLong.getKey().bitcoinSerialize(stream);
                    Utils.int64ToByteStreamLE(addLong.getValue(), stream);
                }
            }

            stream.write(new VarInt(mMnbRecoveryRequests.size()).encode());
            for(Map.Entry<Sha256Hash, Pair<Long, Set<NetAddress>>> entry : mMnbRecoveryRequests.entrySet())
            {
                stream.write(entry.getKey().getReversedBytes());
                Utils.int64ToByteStreamLE(entry.getValue().getFirst(), stream);
                stream.write(new VarInt(entry.getValue().getSecond().size()).encode());
                for(NetAddress address : entry.getValue().getSecond()) {
                    address.bitcoinSerialize(stream);
                }
            }
            stream.write(new VarInt(mMnbRecoveryGoodReplies.size()).encode());
            for(Map.Entry<Sha256Hash, ArrayList<MasternodeBroadcast>> entry : mMnbRecoveryGoodReplies.entrySet())
            {
                stream.write(entry.getKey().getReversedBytes());
                stream.write(new VarInt(entry.getValue().size()).encode());
                for(MasternodeBroadcast mnb : entry.getValue()) {
                    mnb.bitcoinSerialize(stream);
                }
            }

            //READWRITE(nDsqCount);
            Utils.uint32ToByteStreamLE(nDsqCount, stream);
            //READWRITE(mapSeenMasternodeBroadcast);
            stream.write(new VarInt(mapSeenMasternodeBroadcast.size()).encode());
            for(Iterator<Map.Entry<Sha256Hash, Pair<Long, MasternodeBroadcast>>> it1= mapSeenMasternodeBroadcast.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<Sha256Hash, Pair<Long, MasternodeBroadcast>> entry = it1.next();
                stream.write(entry.getKey().getReversedBytes());
                Utils.int64ToByteStreamLE(entry.getValue().getFirst(), stream);
                entry.getValue().getSecond().bitcoinSerialize(stream);
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
    public boolean add(Masternode mn)
    {
        lock.lock();
        try {
            if(has(mn.vin.getOutpoint()))
                return false;

            log.info("masternode--CMasternodeMan::Add -- Adding new Masternode: addr={}, {} now\n", mn.address, size() + 1);
            mapMasternodes.put(mn.vin.getOutpoint(), mn);
            fMasternodesAdded = true;
        } finally {
            lock.unlock();
        }
    }

    public void clear()
    {
        lock.lock();
        try {
            mapMasternodes.clear();
            mAskedUsForMasternodeList.clear();
            mWeAskedForMasternodeList.clear();
            mWeAskedForMasternodeListEntry.clear();
            mapSeenMasternodeBroadcast.clear();
            mapSeenMasternodePing.clear();
            nDsqCount = 0;
            nLastWatchdogVoteTime = 0;
        } finally {
            lock.unlock();
        }
    }
    boolean allowMixing(TransactionOutPoint outpoint)
    {
        lock.lock();
        try {
            Masternode mn = find(outpoint);
            if (mn == null) {
                return false;
            }
            nDsqCount++;
            mn.nLastDsq = nDsqCount;
            mn.fAllowMixingTx = true;

            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean disallowMixing(TransactionOutPoint outpoint)
    {
        lock.lock();
        try {
            Masternode mn = find(outpoint);
            if (mn == null) {
                return false;
            }
            mn.fAllowMixingTx = false;

            return true;
        } finally {
            lock.unlock();
        }
    }



    boolean checkMnbAndUpdateMasternodeList(MasternodeBroadcast mnb) {
        log.info("masternode-CMasternodeMan::CheckMnbAndUpdateMasternodeList - Masternode broadcast, vin: {}", mnb.vin.toString());

        lock.lock();
        try {
            if (mapSeenMasternodeBroadcast.containsKey(mnb.getHash())) { //seen
                context.masternodeSync.addedMasternodeList(mnb.getHash());
                return true;
            }

            mapSeenMasternodeBroadcast.put(mnb.getHash(), mnb);
        } finally {
            lock.unlock();
        }

        log.info("masternode-CMasternodeMan::CheckMnbAndUpdateMasternodeList - Masternode broadcast, vin: {} new", mnb.vin.toString());

        if(!mnb.checkAndUpdate()){
            log.info("masternode-CMasternodeMan::CheckMnbAndUpdateMasternodeList - Masternode broadcast, vin: {} CheckAndUpdate failed", mnb.vin.toString());
            return false;
        }

        // make sure it's still unspent
        //  - this is checked later by .check() in many places and by ThreadCheckDarkSendPool()
        if(mnb.checkInputsAndAdd()) {
            context.masternodeSync.addedMasternodeList(mnb.getHash());
        } else {
            log.info("CMasternodeMan::CheckMnbAndUpdateMasternodeList - Rejected Masternode entry {}", mnb.address.toString());
            return false;
        }

        return true;
    }

    void processMasternodeBroadcast(Peer from, MasternodeBroadcast mnb)
    {
        //log.info("processMasternodeBroadcast:  hash={}", mnb.getHash());
        from.setAskFor.remove(mnb.getHash());

        if(context.masternodeSync.isBlockchainSynced())
            return;
        log.info("masternode--MNANNOUNCE -- Masternode announce, masternode="+ mnb.vin.getOutpoint().toStringShort());

        if(checkMnbAndUpdateMasternodeList(from, mnb))
        {

        }
        if(fMasternodesAdded) {
            notifyMasternodeUpdates();
        }
    }

    void processMasternodePing(Peer peer, MasternodePing mnp)
    {
        Sha256Hash hash = mnp.getHash();
        peer.setAskFor.remove(hash);

        if(!context.masternodeSync.isBlockchainSynced())
            return;

        log.info("masternode--MNPING -- Masternode ping, masternode="+ mnp.vin.getOutpoint().toStringShort());

        lock.lock();
        try {
            if(mapSeenMasternodePing.containsKey(mnp.getHash()))
                return; //seen
            mapSeenMasternodePing.put(mnp.getHash(), mnp);
        } finally {
            lock.unlock();
        }
        log.info("masternode--MNPING -- Masternode ping, masternode={} new", mnp.vin.toString());

        Masternode mn = find(mnp.vin.getOutpoint());

        // if masternode uses sentinel ping instead of watchdog
        // we shoud update nTimeLastWatchdogVote here if sentinel
        // ping flag is actual
        if(mn != null && mnp.fSentinelIsCurrent)
            updateWatchdogVoteTime(mnp.vin.getOutpoint(), mnp.sigTime);

        // too late, new MNANNOUNCE is required
        if(mn != null && mn.isNewStartRequired()) return;

        int nDoS = 0;
        if(mnp.checkAndUpdate(mn, false)) return;

        if(nDoS > 0) {
            // if anything significant failed, mark that node
            //Misbehaving(pfrom->GetId(), nDoS);
        } else {
            // nothing significant failed, mn is a known one too
            return;
        }

        // something significant is broken or mn is unknown,
        // we might have to ask for a masternode entry once
        askForMN(peer, mnp.vin.getOutpoint());
    }
    void processDseg(DarkSendEntryGetMessage dseg)
    {
        //for now do not process this
    }

    void processMasternodeVerify(Peer peer, MasternodeVerification mnv)
    {
        lock.lock();
        try {
            peer.setAskFor.erase(mnv.getHash());

            if (!context.masternodeSync.isMasternodeListSynced())
                return;

            if(mnv.vchSig1.empty()) {
                // CASE 1: someone asked me to verify myself /IP we are using/
                //SendVerifyReply(pfrom, mnv, connman);
            } else if (mnv.vchSig2.empty()) {
                // CASE 2: we _probably_ got verification we requested from some masternode
                ProcessVerifyReply(pfrom, mnv);
            } else {
                // CASE 3: we _probably_ got verification broadcast signed by some masternode which verified another one
                ProcessVerifyBroadcast(pfrom, mnv);
            }
        } finally {
            lock.unlock();
        }
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

            if (!mn.isEnabled() && !mn.isPreEnabled())
                return false;

            Masternode pmn = find(mn.vin);
            if (pmn == null) {
                log.info("masternode - MasternodeMan: Adding new Masternode "+mn.address.toString()+" - "+(size() + 1)+" now");
                mapMasternodes.add(mn);
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

            //BOOST_FOREACH(CMasternode& mn, mapMasternodes)
            for (Masternode mn : mapMasternodes) {
                //payee2 = GetScriptForDestination(mn.pubkey.GetID());
                payee2 = ScriptBuilder.createOutputScript(mn.pubKeyCollateralAddress.getECKey());

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

            //BOOST_FOREACH(CMasternode & mn, mapMasternodes)
            for (Masternode mn : mapMasternodes)
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
            for (Map.Entry<TransactionOutPoint, Masternode> mne : mapMasternodes.entrySet())
            {
                if (mne.getValue().pubKeyMasternode.equals(pubKeyMasternode))
                    return mne.getValue();
            }
            return null;
        } finally {
            lock.unlock();
        }
    }
    public boolean has(TransactionOutPoint outpoint)
    {
        return mapMasternodes.containsKey(outpoint);
    }

    public int countEnabled() { return countEnabled(-1); }
    public int countEnabled(int protocolVersion)
    {
        int count = 0;
        protocolVersion = protocolVersion == -1 ? context.masternodePayments.getMinMasternodePaymentsProto() : protocolVersion;

        lock.lock();
        try {
            for (Map.Entry<TransactionOutPoint, Masternode> mne : mapMasternodes.entrySet()) {
                mne.getValue().check();
                if (mne.getValue().protocolVersion < protocolVersion || !mne.getValue().isEnabled()) continue;
                count++;
            }
        } finally {
            lock.unlock();
        }

        return count;
    }
    public int countMasternodes() { return countMasternodes(-1); }
    public int countMasternodes(int protocolVersion)
    {
        int count = 0;
        protocolVersion = protocolVersion == -1 ? context.masternodePayments.getMinMasternodePaymentsProto() : protocolVersion;

        lock.lock();
        try {
            for (Map.Entry<TransactionOutPoint, Masternode> mne : mapMasternodes.entrySet()) {
                mne.getValue().check();
                if (mne.getValue().protocolVersion < protocolVersion) continue;
                count++;
            }
        } finally {
            lock.unlock();
        }

        return count;
    }

    public void remove(TransactionInput vin)
    {
        try {
            lock.lock();


            //vector<CMasternode>::iterator it = mapMasternodes.begin();
            Iterator<Masternode> it = mapMasternodes.iterator();
            while (it.hasNext()) {
                Masternode mn = it.next();
                if (mn.vin.equals(vin)){
                    log.info("masternode - CMasternodeMan: Removing Masternode %s "+mn.address.toString()+"- "+(size()-1)+" now");

                    //mapMasternodes.remove(mn);
                    it.remove();
                    queueOnSyncStatusChanged();
                    break;
                }

            }
        } finally {
            lock.unlock();
        }
    }
    int size() { return mapMasternodes.size(); }

    public String toString()
    {
        String result;

        result = "Masternodes: " + (int) mapMasternodes.size() +
                ", peers who asked us for Masternode list: " + (int)mAskedUsForMasternodeList.size() +
                ", peers we asked for Masternode list: " + (int)mWeAskedForMasternodeList.size() +
                ", entries in Masternode list we asked for: " + (int)mWeAskedForMasternodeListEntry.size() +
                ", nDsqCount: " + (int)nDsqCount;

        return result;
    }

    public void askForMN(Peer pnode, TransactionOutPoint outPoint)
    {
        lock.lock();
        try {
            HashMap<NetAddress, Long> map = mWeAskedForMasternodeListEntry.get(outPoint);
            if(map != null)
            {
                Long time = map.get(new NetAddress(pnode.getAddress().getAddr());
                if(time != null)
                {
                    if(Utils.currentTimeSeconds() < time) {
                        // we've asked recently, should not repeat too often or we could get banned
                        return;
                    }
                    // we asked this node for this outpoint but it's ok to ask again already
                    log.info("CMasternodeMan::AskForMN -- Asking same peer %s for missing masternode entry again: {}", pnode.getAddress(), outPoint.toStringShort());
                } else {
                    // we already asked for this outpoint but not this node
                    log.info("CMasternodeMan::AskForMN -- Asking new peer %s for missing masternode entry: {}", pnode.getAddress(), outPoint.toStringShort());
                }
            } else {
                // we never asked any node for this outpoint
                log.info("CMasternodeMan::AskForMN -- Asking peer %s for missing masternode entry for the first time: %s\n", pnode.getAddress(), outPoint.toStringShort());
            }
            map.put(new NetAddress(pnode.getAddress().getAddr()), Utils.currentTimeSeconds() + DSEG_UPDATE_SECONDS);
            pnode.sendMessage(new DarkSendEntryGetMessage(new TransactionInput(params, null, null, outPoint)));
        } finally {
            lock.unlock();
        }

        = mWeAskedForMasternodeListEntry.get(outPoint);

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

    Sha256Hash _getBlockHash(long height)
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
            while (cursor != null && cursor.getHeight() != (height-1)) {
                cursor = cursor.getPrev(blockStore);
            }

            return cursor != null ? cursor.getHeader().getHash() : null;

        } catch(BlockStoreException x)
        {
            return null;
        }
    }

    class CompareScoreTxIn<Object> implements Comparator<Object>
    {
        public int compare(Object t1, Object t2) {
            Pair<Long, TransactionInput> p1 = (Pair<Long, TransactionInput>)t1;
            Pair<Long, TransactionInput> p2 = (Pair<Long, TransactionInput>)t2;

            if(p1.getFirst() < p2.getFirst())
                return -1;
            if(p1.getFirst() == p2.getFirst())
                return 0;
            else return 1;
        }
    }

    class CompareScoreMn<Object> implements Comparator<Object>
    {
        public int compare(Object t1, Object t2) {
            Pair<Long, Masternode> p1 = (Pair<Long, Masternode>)t1;
            Pair<Long, Masternode> p2 = (Pair<Long, Masternode>)t2;

            if(p1.getFirst() < p2.getFirst())
                return -1;
            if(p1.getFirst() == p2.getFirst())
                return 0;
            else return 1;
        }
    }

    class CompareConnection<Object> implements Comparator<Object>
    {
        public int compare(Object t1, Object t2) {
            Pair<NetAddress, Sha256Hash> p1 = (Pair<NetAddress, Sha256Hash>)t1;
            Pair<NetAddress, Sha256Hash> p2 = (Pair<NetAddress, Sha256Hash>)t2;

            if(p1.getFirst() < p2.getFirst())
                return -1;
            if(p1.getFirst() == p2.getFirst())
                return 0;
            else return 1;
        }
    }
}

    public int getMasternodeRank(TransactionOutPoint outpoint, int nBlockHeight, int minProtocol)
    {
        int rank = -1;
        //Added to speed things up
        if (context.isLiteMode())
            return -3; // We don't have a masternode list

        if(!context.masternodeSync.isMasternodeListSynced())
            return -1;

        Masternode mnExisting = find(outpoint);
        if (mnExisting == null)
            return -1;

        //make sure we know about this block
        //uint256 hash = 0;
        //if(!GetBlockHash(hash, nBlockHeight)) return -1;
        if(blockChain.getChainHead().getHeight() < nBlockHeight)
            return -2; //Blockheight is above what the store has.

        //TODO:see if the block is in the blockStore
        Sha256Hash nBlockHash = context.hashStore.getBlockHash(nBlockHeight);
        if(nBlockHash == null)
        {
            log.info("CMasternodeMan::{} -- ERROR: GetBlockHash() failed at nBlockHeight {}\n", "getMasternodeRank", nBlockHeight);
            return -2;
        }

        lock.lock();
        try {

            ArrayList<Pair<Integer, Masternode>> vecMasternodeScores = new ArrayList<Pair<Integer, Masternode>>(mapMasternodes.size());
            if (!getMasternodeScores(nBlockHash, vecMasternodeScores, minProtocol))
                return -1;

            rank = 0;
            for (Pair<Integer, Masternode> scorePair : vecMasternodeScores) {
                rank++;
                if (scorePair.getSecond().vin.getOutpoint() == outpoint) {
                    return rank;
                }
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }

    public int getMasternodeRank(ArrayList<Pair<Integer, Masternode>> vecMasternodeScoresRet, int nBlockHeight, int minProtocol)
    {
        //Added to speed things up
        if (context.isLiteMode())
            return -3; // We don't have a masternode list

        if(!context.masternodeSync.isMasternodeListSynced())
            return -1;

        //make sure we know about this block
        //uint256 hash = 0;
        //if(!GetBlockHash(hash, nBlockHeight)) return -1;
        if(blockChain.getChainHead().getHeight() < nBlockHeight)
            return -2; //Blockheight is above what the store has.

        //TODO:see if the block is in the blockStore
        Sha256Hash nBlockHash = context.hashStore.getBlockHash(nBlockHeight);
        if(nBlockHash == null)
        {
            log.info("CMasternodeMan::{} -- ERROR: GetBlockHash() failed at nBlockHeight {}\n", "getMasternodeRank", nBlockHeight);
            return -2;
        }

        lock.lock();
        try {

            ArrayList<Pair<Integer, Masternode>> vecMasternodeScores = new ArrayList<Pair<Integer, Masternode>>(mapMasternodes.size());
            if (!getMasternodeScores(nBlockHash, vecMasternodeScores, minProtocol))
                return -1;

            int rank = 0;
            for (Pair<Integer, Masternode> scorePair : vecMasternodeScores) {
                rank++;
                vecMasternodeScoresRet.add(new Pair<Integer, Masternode>(rank, scorePair.getSecond()));

            }
            return -1;
        } finally {
            lock.unlock();
        }
    }



    void check()
    {
        lock.lock();
        log.info("masternode--CMasternodeMan::Check -- nLastWatchdogVoteTime={}, IsWatchdogActive()={}", nLastWatchdogVoteTime, isWatchdogActive());

        for(Map.Entry<TransactionOutPoint, Masternode> entry : mapMasternodes.entrySet()){
            entry.getValue().check();
        }
        lock.unlock();
    }

    public ArrayList<Pair<Integer, Masternode>> getMasternodeRanks(int nBlockHeight, int minProtocol)
    {

            //std::vector<pair<int64_t, CMasternode> > vecMasternodeScores;
            ArrayList<Pair<Long, Masternode>> vecMasternodeScores = new ArrayList<Pair<Long, Masternode>>();
            //std::vector<pair<int, CMasternode> > vecMasternodeRanks;
            ArrayList<Pair<Integer, Masternode>> vecMasternodeRanks = new ArrayList<Pair<Integer, Masternode>>();
            //make sure we know about this block
            //uint256 hash = uint256();
            //if(!GetBlockHash(hash, nBlockHeight)) return vecMasternodeRanks;
            Sha256Hash hash = context.hashStore.getBlockHash(nBlockHeight);
            if (hash == null)
                return vecMasternodeRanks;
        lock.lock();
        try {
            // scan for winner
            for (Masternode mn : mapMasternodes) {

                mn.check();

                if (mn.protocolVersion < minProtocol) continue;
                if (!mn.isEnabled()) {
                    continue;
                }

                Sha256Hash n = mn.calculateScore(1, nBlockHeight);
                //long n2 = UintToArith256(n).GetCompact(false);
                long n2 = Utils.encodeCompactBits(n.toBigInteger(), false);

                vecMasternodeScores.add(new Pair(n2, mn));
            }
        } finally {
            lock.unlock();
        }

        //sort(vecMasternodeScores.rbegin(), vecMasternodeScores.rend(), CompareScoreMN());
        Collections.sort(vecMasternodeScores, Collections.reverseOrder(new CompareScoreMn()));
        //Arrays.sort(vecMasternodeScores.toArray(), new CompareScoreMn());
        int rank = 0;
        //BOOST_FOREACH (PAIRTYPE(int64_t, CMasternode)& s, vecMasternodeScores){
        for(Pair<Long, Masternode> s: vecMasternodeScores) {
            rank++;
            vecMasternodeRanks.add(new Pair(rank, s.getSecond()));
        }

        return vecMasternodeRanks;
    }

    public void checkAndRemove() {
    {
        if(!context.masternodeSync.isMasternodeListSynced())
            return;
        log.info("CMasternodeMan::CheckAndRemove\n");



        lock.lock();
        try {
            check();

            ArrayList<Pair<Integer, Masternode>> vecMasternodeRanks = new ArrayList<Pair<Integer, Masternode>>();
            // ask for up to MNB_RECOVERY_MAX_ASK_ENTRIES masternode entries at a time
            int nAskForMnbRecovery = MNB_RECOVERY_MAX_ASK_ENTRIES;

            Iterator<Map.Entry<TransactionOutPoint, Masternode>> it = mapMasternodes.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<TransactionOutPoint, Masternode> entry = it.next();
                Masternode mn = entry.getValue();
                MasternodeBroadcast mnb = new MasternodeBroadcast(mn);
                Sha256Hash hash = mnb.getHash();

                if (mn.isOutpointSpent()) {
                    log.info("masternode--CMasternodeMan::CheckAndRemove -- Removing Masternode: {}  addr={}  {} now", mn.getStateString(), mn.address, size() - 1);
                    // erase all of the broadcasts we've seen from this txin, ...
                    mapSeenMasternodeBroadcast.remove(hash);
                    mWeAskedForMasternodeListEntry.remove(entry.getKey());

                    // and finally remove it from the list
                    mn.flagGovernanceItemsAsDirty();
                    it.remove();
                    fMasternodesRemoved = true;
                } else {
                    boolean fAsk = (nAskForMnbRecovery > 0) &&
                            context.masternodeSync.isSynced() &&
                            mn.isNewStartRequired() &&
                            !isMnbRecoveryRequested(hash);
                    if (fAsk) {
                        // this mn is in a non-recoverable state and we haven't asked other nodes yet
                        HashSet<NetAddress> setRequested = new HashSet<NetAddress>();
                        // calulate only once and only when it's needed
                        if (vecMasternodeRanks.isEmpty()) {
                            int nRandomBlockHeight = getRandInt(nCachedBlockHeight);
                            getMasternodeRanks(vecMasternodeRanks, nRandomBlockHeight);
                        }
                        boolean fAskedForMnbRecovery = false;
                        // ask first MNB_RECOVERY_QUORUM_TOTAL masternodes we can connect to and we haven't asked recently
                        for (int i = 0; setRequested.size() < MNB_RECOVERY_QUORUM_TOTAL && i < (int) vecMasternodeRanks.size(); i++) {
                            // avoid banning
                            if (mWeAskedForMasternodeListEntry.containsKey(entry.getKey()) && mWeAskedForMasternodeListEntry.get(entry.getKey()).containsKey(vecMasternodeRanks.get(i).getSecond().address))
                                continue;
                            // didn't ask recently, ok to ask now
                            NetAddress addr = vecMasternodeRanks.get(i).getSecond().address;
                            setRequested.add(addr);
                            listScheduledMnbRequestConnections.add(new Pair(addr, hash));
                            fAskedForMnbRecovery = true;
                        }
                        if (fAskedForMnbRecovery) {
                            log.info("masternode", "CMasternodeMan::CheckAndRemove -- Recovery initiated, masternode=%s\n", entry.getKey().toStringShort());
                            nAskForMnbRecovery--;
                        }
                        // wait for mnb recovery replies for MNB_RECOVERY_WAIT_SECONDS seconds
                        mMnbRecoveryRequests.put(hash, new Pair(Utils.currentTimeSeconds() + MNB_RECOVERY_WAIT_SECONDS, setRequested));
                    }
                }

                // proces replies for MASTERNODE_NEW_START_REQUIRED masternodes
                log.info("masternode", "CMasternodeMan::CheckAndRemove -- mMnbRecoveryGoodReplies size={}", (int) mMnbRecoveryGoodReplies.size());
                Iterator<Map.Entry<Sha256Hash, ArrayList<MasternodeBroadcast>>> itMnbReplies = mMnbRecoveryGoodReplies.entrySet().iterator();
                while (itMnbReplies.hasNext()) {
                    Map.Entry<Sha256Hash, ArrayList<MasternodeBroadcast>> MnbReplies = itMnbReplies.next();
                    if (mMnbRecoveryRequests.get(MnbReplies.getKey()).getFirst() < Utils.currentTimeSeconds()) {
                        // all nodes we asked should have replied now
                        if (MnbReplies.getValue().size() >= MNB_RECOVERY_QUORUM_REQUIRED) {
                            // majority of nodes we asked agrees that this mn doesn't require new mnb, reprocess one of new mnbs
                            log.info("masternode--CMasternodeMan::CheckAndRemove -- reprocessing mnb, masternode={}", MnbReplies.getValue().get(0).vin.getOutpoint().toStringShort());
                            // mapSeenMasternodeBroadcast.erase(itMnbReplies->first);
                            int nDos;
                            MnbReplies.getValue().get(0).fRecovery = true;
                            checkMnbAndUpdateMasternodeList(null, MnbReplies.getValue().get(0));
                        }
                        log.info("masternode--CMasternodeMan::CheckAndRemove -- removing mnb recovery reply, masternode={}, size={}", MnbReplies.getValue().get(0).vin.getOutpoint().toStringShort(), (int) MnbReplies.getValue().size());
                        itMnbReplies.remove();
                    } else {
                        //++itMnbReplies;
                    }
                }

                Iterator<Map.Entry<Sha256Hash, Pair<Long, Set<NetAddress>>>> itMnbRequest = mMnbRecoveryRequests.entrySet().iterator();
                while (itMnbRequest.hasNext()) {
                    // Allow this mnb to be re-verified again after MNB_RECOVERY_RETRY_SECONDS seconds
                    // if mn is still in MASTERNODE_NEW_START_REQUIRED state.
                    if (Utils.currentTimeSeconds() - itMnbRequest.next().getValue().getFirst() > MNB_RECOVERY_RETRY_SECONDS) {
                        itMnbRequest.remove();
                    }
                }

                // check who's asked for the Masternode list
                Iterator<Map.Entry<NetAddress, Long>> it1 = mAskedUsForMasternodeList.entrySet().iterator();
                while (it1.hasNext()) {
                    Map.Entry<NetAddress, Long> e = it1.next();
                    if (e.getValue() < Utils.currentTimeSeconds()) {
                        it1.remove();
                    }
                }

                // check who we asked for the Masternode list
                it1 = mWeAskedForMasternodeList.entrySet().iterator();
                while (it1.hasNext()) {
                    Map.Entry<NetAddress, Long> e = it1.next();
                    if (e.getValue() < Utils.currentTimeSeconds()) {
                        it1.remove();
                    }
                }

                // check which Masternodes we've asked for
                //map<COutPoint, int64_t>::iterator it2 = mWeAskedForMasternodeListEntry.begin();
                Iterator<Map.Entry<TransactionOutPoint, HashMap<NetAddress, Long>>> it2 = mWeAskedForMasternodeListEntry.entrySet().iterator();
                while (it2.hasNext()) {
                    Map.Entry<TransactionOutPoint, HashMap<NetAddress, Long>> e = it2.next();
                    Iterator<Map.Entry<NetAddress, Long>> it3 = e.getValue().entrySet().iterator();
                    while (it3.hasNext()) {
                        Map.Entry<NetAddress, Long> e1 = it3.next();
                        if (e1.getValue() < Utils.currentTimeSeconds()) {
                            it3.remove();
                        }
                    }
                    if (e.getValue().isEmpty()) {
                        it2.remove();
                    }
                }

                Iterator<Map.Entry<NetAddress, MasternodeVerification>> it3 = mWeAskedForVerification.entrySet().iterator();
                while (it3.hasNext()) {
                    Map.Entry<NetAddress, MasternodeVerification> e3 = it3.next();
                    if (e3.getValue().blockHeight < nCachedBlockHeight - MAX_POSE_BLOCKS) {
                        it3.remove();
                    }
                }

                // NOTE: do not expire mapSeenMasternodeBroadcast entries here, clean them on mnb updates!

                // remove expired mapSeenMasternodePing
                Iterator<Map.Entry<Sha256Hash, MasternodePing>> it4 = mapSeenMasternodePing.entrySet().iterator();
                while (it4.hasNext()) {
                    Map.Entry<Sha256Hash, MasternodePing> mp = it4.next();
                    if (mp.getValue().sigTime < Utils.currentTimeSeconds() - (MASTERNODE_REMOVAL_SECONDS * 2)) {
                        log.info("masternode-CMasternodeMan::CheckAndRemove - Removing expired Masternode ping {}", mp.getValue().getHash().toString());
                        it4.remove();
                    }
                }

                // remove expired mapSeenMasternodeVerification
                Iterator<Map.Entry<Sha256Hash, MasternodeVerification>> itv2 = mapSeenMasternodeVerification.entrySet().iterator();
                while (itv2.hasNext()) {
                    Map.Entry<Sha256Hash, MasternodeVerification> e2 = itv2.next();
                    if (e2.getValue().blockHeight < nCachedBlockHeight - MAX_POSE_BLOCKS) {
                        log.info("masternode--CMasternodeMan::CheckAndRemove -- Removing expired Masternode verification: hash={}", entry.getKey().toString());
                        itv2.remove();
                    }
                }
            }

        }
        finally {
            lock.unlock();
        }
        if(fMasternodesRemoved)
        {
            notifyMasternodeUpdates();
        }

    }

    void dsegUpdate(Peer peer)
    {
        lock.lock();
        try {
            if (params.getId().equals(NetworkParameters.ID_MAINNET)) {
                if (!(peer.getAddress().getAddr().isAnyLocalAddress() || peer.getAddress().getAddr().isLoopbackAddress())) {
                    Iterator<Map.Entry<NetAddress, Long>> it = mWeAskedForMasternodeList.entrySet().iterator();
                    if (it.hasNext()) {
                        if (Utils.currentTimeSeconds() < it.next().getValue()){
                            log.info("dseg - we already asked {} for the list; skipping...", peer.getAddress().toString());
                            return;
                        }
                    }
                }
            }
            peer.sendMessage(new DarkSendEntryGetMessage(new TransactionInput(params,null, new byte[0])));
            long askAgain = Utils.currentTimeSeconds() + MasternodeManager.MASTERNODES_DSEG_SECONDS;
            mWeAskedForMasternodeList.put(new NetAddress(peer.getAddress().getAddr()), askAgain);
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
            for(Peer pnode : context.peerGroup.getConnectedPeers())
            {
                if (pnode.isDarkSendMaster()) {
                    if (context.darkSendPool.submittedToMasternode != null && pnode.getAddress().getAddr().equals(context.darkSendPool.submittedToMasternode.address.getAddr()))
                        continue;
                    log.info("Closing Masternode connection {}", pnode.getAddress());
                    pnode.fDarkSendMaster = false;
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
                registration.listener.onMasternodeCountChanged(mapMasternodes.size());
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onMasternodeCountChanged(mapMasternodes.size());
                    }
                });
            }
        }
    }
    public int getEstimatedMasternodes(int nBlock)
    {
    /*
        Masternodes = (Coins/1000)*X on average

        *X = nPercentage, starting at 0.52
        nPercentage goes up 0.01 each period
        Period starts at 35040, which has exponential slowing growth

    */

        int nPercentage = 52; //0.52
        int nPeriod = 35040;
        int nCollateral = 1000;

        for(int i = nPeriod; i <= nBlock; i += nPeriod)
        {
            nPercentage++;
            nPeriod*=2;
        }
        return (int)(Utils.getTotalCoinEstimate(nBlock)/100*nPercentage/nCollateral);
    }

    MasternodeInfo getMasternodeInfo(TransactionOutPoint outpoint)
    {
        lock.lock();
        try {
            Masternode mn = find(outpoint);
            return mn != null ? mn.getInfo() : null;
        } finally {
            lock.unlock();
        }
    }


    public AbstractManager createEmpty()
    {
        return new MasternodeManager(context);
    }

    public MasternodeInfo getMasternodeInfo(PublicKey pubKeyMasternode)
    {
        lock.lock();
        try {

            Masternode pMN = find(pubKeyMasternode);
            return pMN != null ? pMN.getInfo() : null;
        } finally {
            lock.unlock();
        }
    }

    public MasternodeInfo getMasternodeInfo(Script payee)
    {
        lock.lock();
        try {

            for(Map.Entry<TransactionOutPoint, Masternode> entry : mapMasternodes.entrySet()) {
                //Script scriptCollateralAddress = new ScriptBuilder().entry.getValue().pubKeyCollateralAddress
                Script scriptCollateralAddress = GetScriptForDestination(mnpair.second.pubKeyCollateralAddress.GetID());
                if (scriptCollateralAddress == payee) {
                    return entry.getValue().getInfo();
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public boolean poSeBan(TransactionOutPoint outPoint)
    {

        try {
            lock.lock();
            Masternode pmn = find(outPoint);
            if (pmn == null) {
                return false;
            }
            pmn.poSeBan();

            return true;
        }
        finally {
            lock.unlock();
        }
    }

    public Masternode find(TransactionOutPoint outPoint)
    {
        lock.lock();
        try {
            Masternode mn = mapMasternodes.get(outPoint);
            return mn;
        } finally {
            lock.unlock();
        }
    }

    public Masternode get(TransactionOutPoint outPoint)
    {
        lock.lock();
        try {
            Masternode mn = mapMasternodes.get(outPoint);
            return mn;
        } finally {
            lock.unlock();
        }
    }

    //
// Deterministically select the oldest/best masternode to pay on the network
//
    boolean getNextMasternodeInQueueForPayment(boolean fFilterSigTime, int& nCountRet, masternode_info_t& mnInfoRet)
    {
        return getNextMasternodeInQueueForPayment(nCachedBlockHeight, fFilterSigTime, nCountRet, mnInfoRet);
    }

    boolean getNextMasternodeInQueueForPayment(int nBlockHeight, boolean fFilterSigTime, int& nCountRet, MasternodeInfo mnInfoRet)
    {
        return false;
        /*mnInfoRet = masternode_info_t();
        nCountRet = 0;

        if (!masternodeSync.IsWinnersListSynced()) {
            // without winner list we can't reliably find the next winner anyway
            return false;
        }

        // Need LOCK2 here to ensure consistent locking order because the GetBlockHash call below locks cs_main
        LOCK2(cs_main,cs);

        std::vector<std::pair<int, CMasternode*> > vecMasternodeLastPaid;

    //
    //    Make a vector with all of the last paid times
    //

        int nMnCount = CountMasternodes();

        for (auto& mnpair : mapMasternodes) {
        if(!mnpair.second.IsValidForPayment()) continue;

        //check protocol version
        if(mnpair.second.nProtocolVersion < mnpayments.GetMinMasternodePaymentsProto()) continue;

        //it's in the list (up to 8 entries ahead of current block to allow propagation) -- so let's skip it
        if(mnpayments.IsScheduled(mnpair.second, nBlockHeight)) continue;

        //it's too new, wait for a cycle
        if(fFilterSigTime && mnpair.second.sigTime + (nMnCount*2.6*60) > GetAdjustedTime()) continue;

        //make sure it has at least as many confirmations as there are masternodes
        if(GetUTXOConfirmations(mnpair.first) < nMnCount) continue;

        vecMasternodeLastPaid.push_back(std::make_pair(mnpair.second.GetLastPaidBlock(), &mnpair.second));
    }

        nCountRet = (int)vecMasternodeLastPaid.size();

        //when the network is in the process of upgrading, don't penalize nodes that recently restarted
        if(fFilterSigTime && nCountRet < nMnCount/3)
            return GetNextMasternodeInQueueForPayment(nBlockHeight, false, nCountRet, mnInfoRet);

        // Sort them low to high
        sort(vecMasternodeLastPaid.begin(), vecMasternodeLastPaid.end(), CompareLastPaidBlock());

        uint256 blockHash;
        if(!GetBlockHash(blockHash, nBlockHeight - 101)) {
            LogPrintf("CMasternode::GetNextMasternodeInQueueForPayment -- ERROR: GetBlockHash() failed at nBlockHeight %d\n", nBlockHeight - 101);
            return false;
        }
        // Look at 1/10 of the oldest nodes (by last payment), calculate their scores and pay the best one
        //  -- This doesn't look at who is being paid in the +8-10 blocks, allowing for double payments very rarely
        //  -- 1/100 payments should be a double payment on mainnet - (1/(3000/10))*2
        //  -- (chance per block * chances before IsScheduled will fire)
        int nTenthNetwork = nMnCount/10;
        int nCountTenth = 0;
        arith_uint256 nHighest = 0;
        CMasternode *pBestMasternode = NULL;
        BOOST_FOREACH (PAIRTYPE(int, CMasternode*)& s, vecMasternodeLastPaid){
        arith_uint256 nScore = s.second->CalculateScore(blockHash);
        if(nScore > nHighest){
            nHighest = nScore;
            pBestMasternode = s.second;
        }
        nCountTenth++;
        if(nCountTenth >= nTenthNetwork) break;
    }
        if (pBestMasternode) {
            mnInfoRet = pBestMasternode->GetInfo();
        }
        return mnInfoRet.fInfoValid;
        */
    }

    MasternodeInfo findRandomNotInVec(ArrayList<TransactionOutPoint> vecToExclude, int nProtocolVersion)
    {
        lock.lock();
        try {

            nProtocolVersion = nProtocolVersion == -1 ? context.masternodePayments.getMinMasternodePaymentsProto() : nProtocolVersion;

            int nCountEnabled = countEnabled(nProtocolVersion);
            int nCountNotExcluded = nCountEnabled - vecToExclude.size();

            log.info("CMasternodeMan::FindRandomNotInVec -- {} enabled masternodes, {} masternodes to choose from\n", nCountEnabled, nCountNotExcluded);
            if (nCountNotExcluded < 1) return new MasternodeInfo();

            // fill a vector of pointers
            ArrayList<Masternode> vpMasternodesShuffled = new ArrayList<Masternode>(mapMasternodes.size());
            for (Map.Entry<TransactionOutPoint, Masternode> mnpair :mapMasternodes.entrySet()){
                vpMasternodesShuffled.add(mnpair.getValue());
            }

            Random insecureRand = new Random();
            // shuffle pointers
            Collections.shuffle(vpMasternodesShuffled, insecureRand);
            boolean fExclude;

            // loop through
            for(Masternode mn : vpMasternodesShuffled){
                if (mn.protocolVersion < nProtocolVersion || !mn.isEnabled()) continue;
                fExclude = false;
                for(final TransactionOutPoint outpointToExclude : vecToExclude){
                    if (mn.vin.getOutpoint() == outpointToExclude) {
                        fExclude = true;
                        break;
                    }
                }
                if (fExclude) continue;
                // found the one not in vecToExclude
                log.info("masternode--CMasternodeMan::FindRandomNotInVec -- found, masternode={}", mn.vin.getOutpoint().toStringShort());
                return mn.getInfo();
            }

            log.info("masternode--CMasternodeMan::FindRandomNotInVec -- failed");
            return new MasternodeInfo();
        } finally {
            lock.unlock();
        }
    }

    boolean getMasternodeScores(final Sha256Hash nBlockHash, ArrayList<Pair<Integer, Masternode>> vecMasternodeScoresRet, int nMinProtocol) {
        vecMasternodeScoresRet.clear();

        if (!context.masternodeSync.isMasternodeListSynced())
            return false;

        //AssertLockHeld(cs);
        lock.lock();
        try {


            if (mapMasternodes.isEmpty())
                return false;

            // calculate scores
            for (Map.Entry<TransactionOutPoint, Masternode> mnpair :mapMasternodes.entrySet()){
                if (mnpair.getValue().protocolVersion >= nMinProtocol) {
                    vecMasternodeScoresRet.add(Pair(mnpair.getValue().calculateScore(nBlockHash), mnpair.getValue()));
                }
            }

            //sort(vecMasternodeScoresRet.rbegin(), vecMasternodeScoresRet.rend(), CompareScoreMN());
            Collections.sort(vecMasternodeScoresRet, Collections.reverseOrder(new CompareScoreMN()));

            return !vecMasternodeScoresRet.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    Pair<NetAddress, HashSet<Sha256Hash>> popScheduledMnbRequestConnection()
    {
        lock.lock();
        try {


            if (listScheduledMnbRequestConnections.isEmpty()) {
                byte [] zero = {0,0,0,0};
                return new Pair(new NetAddress(params), new HashSet<Sha256Hash>());
            }

            HashSet<Sha256Hash> setResult = new HashSet<Sha256Hash>();

            Collections.sort(listScheduledMnbRequestConnections, new CompareConnections());
            Pair<NetAddress, Sha256Hash> pairFront = listScheduledMnbRequestConnections.get(0);

            // squash hashes from requests with the same CService as the first one into setResult
            Iterator<Pair<NetAddress, Sha256Hash>> it = listScheduledMnbRequestConnections.iterator();
            while (it.hasNext()) {
                Pair<NetAddress, Sha256Hash> entry = it.next();
                if (pairFront.getFirst() == entry.getFirst()) {
                    setResult.add(entry.getSecond());
                    it.remove();
                } else {
                    // since list is sorted now, we can be sure that there is no more hashes left
                    // to ask for from this addr
                    break;
                }
            }
            return new Pair<NetAddress, HashSet<Sha256Hash>>(pairFront.getFirst(), setResult);
        } finally {
            lock.unlock();
        }
    }


}
