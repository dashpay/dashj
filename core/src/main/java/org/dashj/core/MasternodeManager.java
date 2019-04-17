package org.bitcoinj.core;

import org.bitcoinj.net.Dos;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.core.Masternode.*;
import static org.bitcoinj.core.VersionMessage.NODE_NETWORK;

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
    public ReentrantLock lock = Threading.lock("MasternodeManager");

    // critical section to protect the inner data structures specifically on messaging
    ReentrantLock lock_messages = Threading.lock("MasternodeManager-Messages");

    // map to hold all MNs
    HashMap<TransactionOutPoint, Masternode> mapMasternodes;
    // who's asked for the Masternode list and the last time
    HashMap<NetAddress, Long> mAskedUsForMasternodeList;
    // who we asked for the Masternode list and the last time
    HashMap<NetAddress, Long> mWeAskedForMasternodeList;
    // which Masternodes we've asked for
    HashMap<TransactionOutPoint, HashMap<MasternodeAddress, Long>> mWeAskedForMasternodeListEntry;// = new HashMap<TransactionOutPoint, Long>();
    // who we asked for the masternode verification
    HashMap<NetAddress, MasternodeVerification> mWeAskedForVerification;

    // these maps are used for masternode recovery from MASTERNODE_NEW_START_REQUIRED state
    HashMap<Sha256Hash, Pair< Long, Set<MasternodeAddress> > > mMnbRecoveryRequests;
    HashMap<Sha256Hash, ArrayList<MasternodeBroadcast> > mMnbRecoveryGoodReplies;
    ArrayList< Pair<MasternodeAddress, Sha256Hash> > listScheduledMnbRequestConnections;

    /// Set when masternodes are added, cleared when CGovernanceManager is notified
    boolean fMasternodesAdded;

    /// Set when masternodes are removed, cleared when CGovernanceManager is notified
    boolean fMasternodesRemoved;

    ArrayList<Sha256Hash> vecDirtyGovernanceObjectHashes;

    long nLastSentinelPingTime;

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
    void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        nCachedBlockHeight = this.blockChain.getBestChainHeight();
    }

    //Context context;

    public MasternodeManager(Context context)
    {
        super(context);
        mapMasternodes = new HashMap<TransactionOutPoint, Masternode>();
        mAskedUsForMasternodeList = new HashMap<NetAddress, Long>();
        mWeAskedForMasternodeList = new HashMap<NetAddress, Long>();
        mWeAskedForMasternodeListEntry = new HashMap<TransactionOutPoint, HashMap<MasternodeAddress, Long>>();
        mWeAskedForVerification = new HashMap<NetAddress, MasternodeVerification>();
        mMnbRecoveryRequests = new HashMap<Sha256Hash, Pair<Long, Set<MasternodeAddress>>>();
        mMnbRecoveryGoodReplies = new HashMap<Sha256Hash, ArrayList<MasternodeBroadcast>>();
        listScheduledMnbRequestConnections = new ArrayList<Pair<MasternodeAddress, Sha256Hash>>();
        fMasternodesAdded = false;
        fMasternodesRemoved = false;
        vecDirtyGovernanceObjectHashes = new ArrayList<Sha256Hash>();
        nLastSentinelPingTime = 0;
        mapSeenMasternodeBroadcast = new HashMap<Sha256Hash, Pair<Long, MasternodeBroadcast>>();
        mapSeenMasternodePing = new HashMap<Sha256Hash, MasternodePing>();
        nDsqCount = 0;
        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeManagerListener>>();
        mapSeenMasternodeVerification = new HashMap<Sha256Hash, MasternodeVerification>();
    }

    public MasternodeManager(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
        listScheduledMnbRequestConnections = new ArrayList<Pair<MasternodeAddress, Sha256Hash>>();
        fMasternodesAdded = false;
        fMasternodesRemoved = false;
        vecDirtyGovernanceObjectHashes = new ArrayList<Sha256Hash>();
        nLastSentinelPingTime = 0;
        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeManagerListener>>();
    }

    public int calculateMessageSizeInBytes()
    {
        int size = 0;
        lock.lock();
        try {
            size += VarInt.sizeOf(mapMasternodes.size());
            size *= 1000;


            return size;
        }
        finally {
            lock.unlock();
        }
    }
    @Override
    protected void parse() throws ProtocolException {


        String version = readStr();
        int size = (int)readVarInt();

        mapMasternodes = new HashMap<TransactionOutPoint, Masternode>(size);
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
        mWeAskedForMasternodeListEntry = new HashMap<TransactionOutPoint, HashMap<MasternodeAddress, Long>>(size);
        for(int i = 0; i < size; ++i) {
            TransactionOutPoint out = new TransactionOutPoint(params, payload, cursor);
            cursor += out.getMessageSize();
            int countMap = (int)readVarInt();
            HashMap<MasternodeAddress, Long> map = new HashMap<MasternodeAddress, Long>(countMap);
            for(int j = 0; j < countMap; ++j) {
                MasternodeAddress ma = new MasternodeAddress(params, payload, cursor, 0);
                cursor += ma.getMessageSize();
                long x = readInt64();
                map.put(ma, x);
            }
            mWeAskedForMasternodeListEntry.put(out, map);
        }

        size = (int)readVarInt();
        mMnbRecoveryRequests = new HashMap<Sha256Hash, Pair<Long, Set<MasternodeAddress>>>(size);
        for(int i = 0; i < size; ++i) {
            Sha256Hash hash = readHash();
            long x = readInt64();
            int countSet = (int)readVarInt();
            Set<MasternodeAddress> addresses = new HashSet<MasternodeAddress>(countSet);
            for(int j = 0; j < countSet; ++j)
            {
                MasternodeAddress ma = new MasternodeAddress(params, payload, cursor, 0);
                cursor += ma.getMessageSize();
                addresses.add(ma);
            }
            mMnbRecoveryRequests.put(hash, new Pair<Long, Set<MasternodeAddress>>(x, addresses));
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

        if(!version.equals(SERIALIZATION_VERSION_STRING))
            clear();

        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        lock.lock();
        try {
            stream.write(new VarInt(SERIALIZATION_VERSION_STRING.length()).encode());
            stream.write(SERIALIZATION_VERSION_STRING.getBytes());

            stream.write(new VarInt(mapMasternodes.size()).encode());
            for (Map.Entry<TransactionOutPoint, Masternode> entry : mapMasternodes.entrySet()) {
                entry.getKey().bitcoinSerialize(stream);
                entry.getValue().masterNodeSerialize(stream); //masternodes are added as MasternodeBroadcasts so bitcoinSerialize will write the MNB, instead of MN
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
            for(Iterator<Map.Entry<TransactionOutPoint, HashMap<MasternodeAddress, Long>>> it1= mWeAskedForMasternodeListEntry.entrySet().iterator(); it1.hasNext();)
            {
                Map.Entry<TransactionOutPoint, HashMap<MasternodeAddress, Long>> entry = it1.next();
                entry.getKey().bitcoinSerialize(stream);
                stream.write(new VarInt(entry.getValue().size()).encode());
                for(Map.Entry<MasternodeAddress, Long> addLong: entry.getValue().entrySet()) {
                    addLong.getKey().bitcoinSerialize(stream);
                    Utils.int64ToByteStreamLE(addLong.getValue(), stream);
                }
            }

            stream.write(new VarInt(mMnbRecoveryRequests.size()).encode());
            for(Map.Entry<Sha256Hash, Pair<Long, Set<MasternodeAddress>>> entry : mMnbRecoveryRequests.entrySet())
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
            if(has(mn.info.outpoint))
                return false;

            log.info("masternode--CMasternodeMan::Add -- Adding new Masternode: addr={}, {} now", mn.info.address, size() + 1);
            mapMasternodes.put(mn.info.outpoint, mn);
            unCache();
            fMasternodesAdded = true;
            queueOnSyncStatusChanged();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void clear()
    {
        lock.lock();
        try {
            unCache();
            mapMasternodes.clear();
            mAskedUsForMasternodeList.clear();
            mWeAskedForMasternodeList.clear();
            mWeAskedForMasternodeListEntry.clear();
            mapSeenMasternodeBroadcast.clear();
            mapSeenMasternodePing.clear();
            nDsqCount = 0;
            nLastSentinelPingTime = 0;
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
            mn.info.nLastDsq = nDsqCount;
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



    boolean checkMnbAndUpdateMasternodeList(Peer pfrom, MasternodeBroadcast mnb, Dos nDos) {
        // Need to lock cs_main here to ensure consistent locking order because the SimpleCheck call below locks cs_main
        ReentrantLock pglock = context.peerGroup.getLock();
        pglock.lock();
        try {
            lock.lock();
            try {

                nDos.set(0);
                log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- masternode={}", mnb.info.outpoint.toStringShort());

                Sha256Hash hash = mnb.getHash();
                if (mapSeenMasternodeBroadcast.containsKey(hash) && !mnb.fRecovery) { //seen
                    log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- masternode={} seen", mnb.info.outpoint.toStringShort());
                    // less then 2 pings left before this MN goes into non-recoverable state, bump sync timeout
                    if (Utils.currentTimeSeconds() - mapSeenMasternodeBroadcast.get(hash).getFirst() > MASTERNODE_NEW_START_REQUIRED_SECONDS - MASTERNODE_MIN_MNP_SECONDS * 2) {
                        log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- masternode={} seen update", mnb.info.outpoint.toStringShort());
                        mapSeenMasternodeBroadcast.get(hash).setFirst(Utils.currentTimeSeconds());
                        context.masternodeSync.BumpAssetLastTime("CMasternodeMan::CheckMnbAndUpdateMasternodeList - seen");
                    }
                    // did we ask this node for it?
                    if (pfrom != null && isMnbRecoveryRequested(hash) && Utils.currentTimeSeconds() < mMnbRecoveryRequests.get(hash).getFirst()) {
                        log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- mnb={} seen request", hash);
                        if (mMnbRecoveryRequests.get(hash).getSecond().contains(pfrom.getAddress())) {
                            log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- mnb={} seen request, addr={}", hash, pfrom.getAddress());
                            // do not allow node to send same mnb multiple times in recovery mode
                            mMnbRecoveryRequests.get(hash).getSecond().remove(pfrom.getAddress());
                            // does it have newer lastPing?
                            if (mnb.lastPing.sigTime > mapSeenMasternodeBroadcast.get(hash).getSecond().lastPing.sigTime) {
                                // simulate Check
                                Masternode mnTemp = new Masternode(mnb);
                                mnTemp.check();
                                log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- mnb={} seen request, addr={}, better lastPing: %d min ago, projected mn state: %s", hash.toString(), pfrom.getAddress().toString(), (Utils.currentTimeSeconds() - mnb.lastPing.sigTime) / 60, mnTemp.getStateString());
                                if (mnTemp.isValidStateForAutoStart(mnTemp.info.activeState)) {
                                    // this node thinks it's a good one
                                    log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- masternode={} seen good", mnb.info.outpoint.toStringShort());
                                    mMnbRecoveryGoodReplies.get(hash).add(mnb);
                                }
                            }
                        }
                    }
                    return true;
                }
                mapSeenMasternodeBroadcast.put(hash, new Pair(Utils.currentTimeSeconds(), mnb));

                log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- masternode={} new", mnb.info.outpoint.toStringShort());

                if (!mnb.simpleCheck(nDos)) {
                    log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- SimpleCheck() failed, masternode={}", mnb.info.outpoint.toStringShort());
                    return false;
                }

                // search Masternode list
                Masternode mn = find(mnb.info.outpoint);
                if (mn != null) {
                    MasternodeBroadcast mnbOld = mapSeenMasternodeBroadcast.get(new MasternodeBroadcast(mn).getHash()).getSecond();
                    if (!mnb.update(mn, nDos)) {
                        log.info("masternode--CMasternodeMan::CheckMnbAndUpdateMasternodeList -- Update() failed, masternode={}", mnb.info.outpoint.toStringShort());
                        return false;
                    }
                    if (hash != mnbOld.getHash()) {
                        mapSeenMasternodeBroadcast.remove(mnbOld.getHash());
                    }
                    return true;
                }
            } finally {
                lock.unlock();
            }
        }
        finally{
            pglock.unlock();
        }


        if(mnb.checkOutpoint(nDos)) {
            add(mnb);
            context.masternodeSync.BumpAssetLastTime("CMasternodeMan::CheckMnbAndUpdateMasternodeList - new");
            // if it matches our Masternode privkey...
            if(context.fMasterNode && mnb.info.pubKeyMasternode == context.activeMasternode.pubKeyMasternode) {
                mnb.nPoSeBanScore = -MASTERNODE_POSE_BAN_MAX_SCORE;
                if(mnb.info.nProtocolVersion == CoinDefinition.PROTOCOL_VERSION) {
                    // ... and PROTOCOL_VERSION, then we've been remotely activated ...
                    log.info("CMasternodeMan::CheckMnbAndUpdateMasternodeList -- Got NEW Masternode entry: masternode={}  sigTime={}  addr={}",
                            mnb.info.outpoint.toStringShort(), mnb.info.sigTime, mnb.info.address.toString());
                    context.activeMasternode.manageState();
                } else {
                    // ... otherwise we need to reactivate our node, do not add it to the list and do not relay
                    // but also do not ban the node we get this message from
                    log.info("CMasternodeMan::CheckMnbAndUpdateMasternodeList -- wrong PROTOCOL_VERSION, re-activate your MN: message nProtocolVersion={}  PROTOCOL_VERSION={}", mnb.info.nProtocolVersion, CoinDefinition.PROTOCOL_VERSION);
                    return false;
                }
            }
            mnb.relay();
        } else {
            log.info("CMasternodeMan::CheckMnbAndUpdateMasternodeList -- Rejected Masternode entry: {}  addr={}", mnb.info.outpoint.toStringShort(), mnb.info.address.toString());
            return false;
        }

        return true;
    }



    void processMasternodeBroadcast(Peer from, MasternodeBroadcast mnb)
    {
        from.setAskFor.remove(mnb.getHash());

        if(!context.masternodeSync.isBlockchainSynced())
            return;
        log.info("masternode--MNANNOUNCE -- Masternode announce, masternode="+ mnb.info.outpoint.toStringShort());

        Dos nDos = new Dos();
        if(checkMnbAndUpdateMasternodeList(from, mnb, nDos))
        {
            // use announced Masternode as a peer
            //connman.AddNewAddress(CAddress(mnb.addr, NODE_NETWORK), pfrom->addr, 2*60*60);
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

        log.info("masternode--MNPING -- Masternode ping, masternode="+ mnp.masternodeOutpoint.toStringShort());

        lock.lock();
        try {
            if(mapSeenMasternodePing.containsKey(mnp.getHash()))
                return; //seen
            mapSeenMasternodePing.put(mnp.getHash(), mnp);
        } finally {
            lock.unlock();
        }
        log.info("masternode--MNPING -- Masternode ping, masternode={} new", mnp.masternodeOutpoint.toString());

        Masternode mn = find(mnp.masternodeOutpoint);

        // if masternode uses sentinel ping instead of watchdog
        // we shoud update nTimeLastWatchdogVote here if sentinel
        // ping flag is actual
        if(mn != null && mnp.sentinelIsCurrent)
            updateLastSentinelPingTime(mnp.masternodeOutpoint, mnp.sigTime);

        // too late, new MNANNOUNCE is required
        if(mn != null && mn.isNewStartRequired()) return;

        Dos nDos = new Dos();
        if(mnp.checkAndUpdate(mn, false, nDos)) return;

        if(nDos.get() > 0) {
            // if anything significant failed, mark that node
            //Misbehaving(pfrom->GetId(), nDoS);
        } else if(mn != null) {
            // nothing significant failed, mn is a known one too
            return;
        }

        // something significant is broken or mn is unknown,
        // we might have to ask for a masternode entry once
        askForMN(peer, mnp.masternodeOutpoint);
    }
    void processDseg(DarkSendEntryGetMessage dseg)
    {
        //for now do not process this
    }

    void processMasternodeVerify(Peer peer, MasternodeVerification mnv)
    {
        lock.lock();
        try {
            peer.setAskFor.remove(mnv.getHash());

            if (!context.masternodeSync.isMasternodeListSynced())
                return;

            if(mnv.vchSig1.isEmpty()) {
                // CASE 1: someone asked me to verify myself /IP we are using/
                //SendVerifyReply(pfrom, mnv, connman);
            } else if (mnv.vchSig2.isEmpty()) {
                // CASE 2: we _probably_ got verification we requested from some masternode
                processVerifyReply(peer, mnv);
            } else {
                // CASE 3: we _probably_ got verification broadcast signed by some masternode which verified another one
                processVerifyBroadcast(peer, mnv);
            }
        } finally {
            lock.unlock();
        }
    }

    void processVerifyReply(Peer pnode, MasternodeVerification mnv)
    {
        StringBuilder strError = new StringBuilder();

        // did we even ask for it? if that's the case we should have matching fulfilled request
        if(!context.netFullfilledRequestManager.hasFulfilledRequest(pnode.getAddress(), "mnv-request")) {
            log.info("CMasternodeMan::ProcessVerifyReply -- ERROR: we didn't ask for verification of {}, peer={}", pnode.getAddress().toString(), pnode);
            //Misbehaving(pnode->id, 20);
            return;
        }

        // Received nonce for a known address must match the one we sent
        if(mWeAskedForVerification.get(pnode.getAddress()).nonce != mnv.nonce) {
            log.info("CMasternodeMan::ProcessVerifyReply -- ERROR: wrong nounce: requested={}, received={}, peer={}",
                    mWeAskedForVerification.get(pnode.getAddress()).nonce, mnv.nonce, pnode);
            //Misbehaving(pnode->id, 20);
            return;
        }

        // Received nBlockHeight for a known address must match the one we sent
        if(mWeAskedForVerification.get(pnode.getAddress()).blockHeight != mnv.blockHeight) {
            log.info("CMasternodeMan::ProcessVerifyReply -- ERROR: wrong nBlockHeight: requested={}, received={}, peer={}",
                    mWeAskedForVerification.get(pnode.getAddress()).blockHeight, mnv.blockHeight, pnode);
            //Misbehaving(pnode->id, 20);
            return;
        }

        Sha256Hash blockHash = context.hashStore.getBlockHash(mnv.blockHeight);
        if(blockHash == null) {
            // this shouldn't happen...
            log.info("MasternodeMan::ProcessVerifyReply -- can't get block hash for unknown block height {}, peer={}", mnv.blockHeight, pnode);
            return;
        }

        // we already verified this address, why node is spamming?
        if(context.netFullfilledRequestManager.hasFulfilledRequest(pnode.getAddress(), "mnv-done")) {
            log.info("CMasternodeMan::ProcessVerifyReply -- ERROR: already verified %s recently", pnode.getAddress());
            //Misbehaving(pnode->id, 20);
            return;
        }

        lock.lock();
        try
        {
            Masternode realMasternode = null;
            ArrayList<Masternode> vpMasternodesToBan = new ArrayList<Masternode>();
            String strMessage1 = String.format("%s%d%s", pnode.getAddress().toString(), mnv.nonce, blockHash.toString());
            for (Map.Entry<TransactionOutPoint, Masternode> mnpair : mapMasternodes.entrySet()) {
            if(mnpair.getValue().info.address.getAddr() == pnode.getAddress().getAddr() &&
                    !pnode.getAddress().getServices().add(BigInteger.valueOf(NODE_NETWORK)).equals(BigInteger.ZERO)) {
                if(MessageSigner.verifyMessage(mnpair.getValue().info.pubKeyMasternode, mnv.vchSig1, strMessage1, strError)) {
                    // found it!
                    realMasternode = mnpair.getValue();
                    if(!mnpair.getValue().isPoSeVerified()) {
                        mnpair.getValue().decreasePoSeBanScore();
                    }
                    context.netFullfilledRequestManager.addFulfilledRequest(pnode.getAddress(), "mnv-done");

                    // we can only broadcast it if we are an activated masternode
                    if(context.activeMasternode.outpoint == new TransactionOutPoint(params, 0, Sha256Hash.ZERO_HASH)) continue;
                    // update ...
                    mnv.addr = mnpair.getValue().info.address;
                    mnv.masternodeOutpoint1 = mnpair.getValue().info.outpoint;
                    mnv.masternodeOutpoint2 = new TransactionOutPoint(context.getParams(), context.activeMasternode.outpoint.getIndex(), context.activeMasternode.outpoint.getHash());
                    String strMessage2 = String.format("%s%d%s%s%s", mnv.addr.toString(), mnv.nonce, blockHash.toString(),
                            mnv.masternodeOutpoint1.toStringShort(), mnv.masternodeOutpoint2.toStringShort());
                    // ... and sign it
                    if(null == (mnv.vchSig2 = MessageSigner.signMessage(strMessage2, context.activeMasternode.keyMasternode))) {
                        log.info("MasternodeMan::ProcessVerifyReply -- SignMessage() failed");
                        return;
                    }

                    if(!MessageSigner.verifyMessage(context.activeMasternode.pubKeyMasternode, mnv.vchSig2, strMessage2, strError)) {
                        log.info("MasternodeMan::ProcessVerifyReply -- VerifyMessage() failed, error: {}", strError);
                        return;
                    }

                    mWeAskedForVerification.put(new NetAddress(pnode.getAddress().getAddr()), mnv);
                    mapSeenMasternodeVerification.put(mnv.getHash(), mnv);
                    mnv.relay();

                } else {
                    vpMasternodesToBan.add(mnpair.getValue());
                }
            }
        }
            // no real masternode found?...
            if(realMasternode != null) {
                // this should never be the case normally,
                // only if someone is trying to game the system in some way or smth like that
                log.info("CMasternodeMan::ProcessVerifyReply -- ERROR: no real masternode found for addr {}", pnode.getAddress().toString());
                //Misbehaving(pnode->id, 20);
                return;
            }
            log.info("CMasternodeMan::ProcessVerifyReply -- verified real masternode {} for addr {}",
                    realMasternode.info.outpoint.toStringShort(), pnode.getAddress().toString());
            // increase ban score for everyone else
            for(Masternode mn : vpMasternodesToBan)
            {
                mn.increasePoSeBanScore();
                log.info("masternode--CMasternodeMan::ProcessVerifyReply -- increased PoSe ban score for %s addr %s, new score %d",
                        realMasternode.info.outpoint.toStringShort(), pnode.getAddress().toString(), mn.nPoSeBanScore);
            }
            if(!vpMasternodesToBan.isEmpty())
                log.info("CMasternodeMan::ProcessVerifyReply -- PoSe score increased for {} fake masternodes, addr {}",
                        vpMasternodesToBan.size(), pnode.getAddress().toString());
        } finally {
            lock.unlock();
        }
    }

    void processVerifyBroadcast(Peer pnode, MasternodeVerification mnv)
    {
        StringBuilder strError = new StringBuilder();

        if(mapSeenMasternodeVerification.containsKey(mnv.getHash())) {
            // we already have one
            return;
        }
        mapSeenMasternodeVerification.put(mnv.getHash(), mnv);

        // we don't care about history
        if(mnv.blockHeight < nCachedBlockHeight - MAX_POSE_BLOCKS) {
            log.info("masternode--CMasternodeMan::ProcessVerifyBroadcast -- Outdated: current block {}, verification block {}, peer={}",
                    nCachedBlockHeight, mnv.blockHeight, pnode);
            return;
        }

        if(mnv.masternodeOutpoint1 == mnv.masternodeOutpoint2) {
            log.info("masternode--CMasternodeMan::ProcessVerifyBroadcast -- ERROR: same vins {}, peer={}",
                    mnv.masternodeOutpoint1.toStringShort(), pnode);
            // that was NOT a good idea to cheat and verify itself,
            // ban the node we received such message from
            //Misbehaving(pnode->id, 100);
            return;
        }

        Sha256Hash blockHash = context.hashStore.getBlockHash(mnv.blockHeight);
        if(blockHash == null) {
            // this shouldn't happen...
            log.info("CMasternodeMan::ProcessVerifyBroadcast -- Can't get block hash for unknown block height {}, peer={}", mnv.blockHeight, pnode);
            return;
        }

        int nRank;

        if (-1 == (nRank =getMasternodeRank(mnv.masternodeOutpoint2, mnv.blockHeight, MIN_POSE_PROTO_VERSION))) {
            log.info("masternode--CMasternodeMan::ProcessVerifyBroadcast -- Can't calculate rank for masternode {}",
                    mnv.masternodeOutpoint2.toStringShort());
            return;
        }

        if(nRank > MAX_POSE_RANK) {
            log.info("masternode--CMasternodeMan::ProcessVerifyBroadcast -- Masternode {}} is not in top {}, current rank {}, peer={}",
                    mnv.masternodeOutpoint2.toStringShort(), (int)MAX_POSE_RANK, nRank, pnode);
            return;
        }

        lock.lock();
        try {

        String strMessage1 = String.format("%s%d%s", mnv.addr.toString(), mnv.nonce, blockHash);
        String strMessage2 = String.format("%s%d%s%s%s", mnv.addr.toString(), mnv.nonce, blockHash,
            mnv.masternodeOutpoint1.toStringShort(), mnv.masternodeOutpoint2.toStringShort());

        Masternode mn1 = find(mnv.masternodeOutpoint1);
        if(mn1 == null) {
            log.info("CMasternodeMan::ProcessVerifyBroadcast -- can't find masternode1 {}", mnv.masternodeOutpoint1.toStringShort());
            return;
        }

        Masternode mn2 = find(mnv.masternodeOutpoint2);
        if(mn2 == null) {
            log.info("CMasternodeMan::ProcessVerifyBroadcast -- can't find masternode2 {}", mnv.masternodeOutpoint2.toStringShort());
            return;
        }

        if(!mn1.info.address.equals(mnv.addr)) {
            log.info("CMasternodeMan::ProcessVerifyBroadcast -- addr {} does not match {}", mnv.addr.toString(), mn1.info.address.toString());
            return;
        }

        if(!MessageSigner.verifyMessage(mn1.info.pubKeyMasternode, mnv.vchSig1, strMessage1, strError)) {
            log.info("CMasternodeMan::ProcessVerifyBroadcast -- VerifyMessage() for masternode1 failed, error: {}", strError);
            return;
        }

        if(!MessageSigner.verifyMessage(mn2.info.pubKeyMasternode, mnv.vchSig2, strMessage2, strError)) {
            log.info("CMasternodeMan::ProcessVerifyBroadcast -- VerifyMessage() for masternode2 failed, error: {}", strError);
            return;
        }

        if(!mn1.isPoSeVerified()) {
            mn1.decreasePoSeBanScore();
        }
        mnv.relay();

        log.info("CMasternodeMan::ProcessVerifyBroadcast -- verified masternode %s for addr %s",
                mn1.info.outpoint.toStringShort(), mn1.info.address.toString());

        // increase ban score for everyone else with the same addr
        int nCount = 0;
        for (Map.Entry<TransactionOutPoint, Masternode> mnpair : mapMasternodes.entrySet()) {
            if(mnpair.getValue().info.address != mnv.addr || mnpair.getKey() == mnv.masternodeOutpoint1) continue;
            mnpair.getValue().increasePoSeBanScore();
            nCount++;
            log.info("masternode--CMasternodeMan::ProcessVerifyBroadcast -- increased PoSe ban score for {} addr {}, new score {}",
                    mnpair.getKey().toStringShort(), mnpair.getValue().info.address.toString(), mnpair.getValue().nPoSeBanScore);
        }
        if(nCount != 0)
            log.info("CMasternodeMan::ProcessVerifyBroadcast -- PoSe score increased for {} fake masternodes, addr {}",
                    nCount, mn1.info.address.toString());
       
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

    @Deprecated
    Masternode find(Script payee)
    {
        lock.lock();
        try {
            Script payee2;

            for (Map.Entry<TransactionOutPoint, Masternode> mn : mapMasternodes.entrySet()) {
                payee2 = ScriptBuilder.createOutputScript(mn.getValue().info.pubKeyCollateralAddress.getECKey());

                if (payee2 == payee)
                    return mn.getValue();
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Deprecated
    Masternode find(PublicKey pubKeyMasternode)
    {
        lock.lock();
        try {
            for (Map.Entry<TransactionOutPoint, Masternode> mne : mapMasternodes.entrySet())
            {
                if (mne.getValue().info.pubKeyMasternode.equals(pubKeyMasternode))
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

            Iterator<Map.Entry<TransactionOutPoint, Masternode>> it = mapMasternodes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<TransactionOutPoint, Masternode> entry = it.next();
                if (entry.getValue().info.outpoint.equals(vin)){
                    log.info("masternode - CMasternodeMan: Removing Masternode %s "+entry.getValue().info.address.toString()+"- "+(size()-1)+" now");
                    it.remove();
                    unCache();
                    queueOnSyncStatusChanged();
                    break;
                }

            }
        } finally {
            lock.unlock();
        }
    }

    /// Return the number of (unique) Masternodes
    public int size() { return mapMasternodes.size(); }

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
            HashMap<MasternodeAddress, Long> map = mWeAskedForMasternodeListEntry.get(outPoint);
            if(map != null)
            {
                Long time = map.get(new NetAddress(pnode.getAddress().getAddr()));
                if(time != null)
                {
                    if(Utils.currentTimeSeconds() < time) {
                        // we've asked recently, should not repeat too often or we could get banned
                        return;
                    }
                    // we asked this node for this outpoint but it's ok to ask again already
                    log.info("CMasternodeMan::AskForMN -- Asking same peer {} for missing masternode entry again: {}", pnode.getAddress(), outPoint.toStringShort());
                } else {
                    // we already asked for this outpoint but not this node
                    log.info("CMasternodeMan::AskForMN -- Asking new peer {} for missing masternode entry: {}", pnode.getAddress(), outPoint.toStringShort());
                }
            } else {
                // we never asked any node for this outpoint
                log.info("CMasternodeMan::AskForMN -- Asking peer {} for missing masternode entry for the first time: {}", pnode.getAddress(), outPoint.toStringShort());
                map = new HashMap<MasternodeAddress, Long>();
            }
            map.put(new MasternodeAddress(pnode.getAddress().getAddr()), Utils.currentTimeSeconds() + DSEG_UPDATE_SECONDS);
            pnode.sendMessage(new DarkSendEntryGetMessage(new TransactionInput(params, null, new byte[0], outPoint)));
        } finally {
            lock.unlock();
        }
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

    class CompareScoreMN<Object> implements Comparator<Object>
    {
        public int compare(Object t1, Object t2) {
            Pair<Sha256Hash, Masternode> p1 = (Pair<Sha256Hash, Masternode>)t1;
            Pair<Sha256Hash, Masternode> p2 = (Pair<Sha256Hash, Masternode>)t2;

            if(p1.getFirst().compareTo(p2.getFirst()) < 0)
                return -1;
            if(p1.getFirst().equals(p2.getFirst()))
                return 0;
            else return 1;
        }
    }

    class CompareConnection<Object> implements Comparator<Object>
    {
        public int compare(Object t1, Object t2) {
            Pair<NetAddress, Sha256Hash> p1 = (Pair<NetAddress, Sha256Hash>)t1;
            Pair<NetAddress, Sha256Hash> p2 = (Pair<NetAddress, Sha256Hash>)t2;

            if(p1.getFirst().getAddr().hashCode() < p2.getFirst().getAddr().hashCode())
                return -1;
            if(p1.getFirst() == p2.getFirst())
                return 0;
            else return 1;
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
        //Sha256Hash hash = 0;
        //if(!GetBlockHash(hash, nBlockHeight)) return -1;
        if(blockChain.getChainHead().getHeight() < nBlockHeight)
            return -2; //Blockheight is above what the store has.

        //TODO:see if the block is in the blockStore
        Sha256Hash nBlockHash = context.hashStore.getBlockHash(nBlockHeight);
        if(nBlockHash == null)
        {
            log.info("CMasternodeMan::{} -- ERROR: GetBlockHash() failed at nBlockHeight {}", "getMasternodeRank", nBlockHeight);
            return -2;
        }

        lock.lock();
        try {

            ArrayList<Pair<Sha256Hash, Masternode>> vecMasternodeScores = new ArrayList<Pair<Sha256Hash, Masternode>>(mapMasternodes.size());
            if (!getMasternodeScores(nBlockHash, vecMasternodeScores, minProtocol))
                return -1;

            rank = 0;
            for (Pair<Sha256Hash, Masternode> scorePair : vecMasternodeScores) {
                rank++;
                if (scorePair.getSecond().info.outpoint == outpoint) {
                    return rank;
                }
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }

    public boolean getMasternodeRanks(ArrayList<Pair<Integer, Masternode>> vecMasternodeScoresRet, int nBlockHeight, int minProtocol)
    {
        //Added to speed things up
        if (context.isLiteMode())
            return false; // We don't have a masternode list

        if(!context.masternodeSync.isMasternodeListSynced())
            return false;

        //make sure we know about this block
        //Sha256Hash hash = 0;
        //if(!GetBlockHash(hash, nBlockHeight)) return -1;
        if(blockChain.getChainHead().getHeight() < nBlockHeight)
            return false; //Blockheight is above what the store has.

        //TODO:see if the block is in the blockStore
        Sha256Hash nBlockHash = context.hashStore.getBlockHash(nBlockHeight);
        if(nBlockHash == null)
        {
            log.info("CMasternodeMan::{} -- ERROR: GetBlockHash() failed at nBlockHeight {}", "getMasternodeRank", nBlockHeight);
            return false;
        }

        lock.lock();
        try {

            ArrayList<Pair<Sha256Hash, Masternode>> vecMasternodeScores = new ArrayList<Pair<Sha256Hash, Masternode>>(mapMasternodes.size());
            if (!getMasternodeScores(nBlockHash, vecMasternodeScores, minProtocol))
                return false;

            int rank = 0;
            for (Pair<Sha256Hash, Masternode> scorePair : vecMasternodeScores) {
                rank++;
                vecMasternodeScoresRet.add(new Pair<Integer, Masternode>(rank, scorePair.getSecond()));

            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    void check()
    {
        lock.lock();
        try {
            log.info("masternode--CMasternodeMan::Check -- nLastSentinelPingTime={}, IsWatchdogActive()={}", nLastSentinelPingTime, isSentinelPingActive());

            for (Map.Entry<TransactionOutPoint, Masternode> entry : mapMasternodes.entrySet()) {
                Masternode mn = entry.getValue();
                if(!mn.lock.isLocked()) // prevent deadlocks
                    mn.check();
            }
        } finally {
            lock.unlock();
        }
    }
    boolean isMnbRecoveryRequested(Sha256Hash hash) { return mMnbRecoveryRequests.containsKey(hash); }


    public void checkAndRemove() {
        {
            if (!context.masternodeSync.isMasternodeListSynced())
                return;
            log.info("CMasternodeMan::CheckAndRemove");

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
                        log.info("masternode--CMasternodeMan::CheckAndRemove -- Removing Masternode: {}  addr={}  {} now", mn.getStateString(), mn.info.address, size() - 1);
                        // erase all of the broadcasts we've seen from this txin, ...
                        mapSeenMasternodeBroadcast.remove(hash);
                        mWeAskedForMasternodeListEntry.remove(entry.getKey());

                        // and finally remove it from the list
                        mn.flagGovernanceItemsAsDirty();
                        it.remove();
                        unCache();
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
                                int nRandomBlockHeight = new Random().nextInt(nCachedBlockHeight != 0 ? nCachedBlockHeight : blockChain.getBestChainHeight());
                                int lowestBlockHeight = context.hashStore.getLowestHeight();
                                if(nRandomBlockHeight < lowestBlockHeight);
                                    nRandomBlockHeight = lowestBlockHeight;
                                /*for(Map.Entry<Integer, Sha256Hash> checkpoint : params.checkpoints.entrySet()) {
                                    if(checkpoint.getKey() > nRandomBlockHeight)
                                        nRandomBlockHeight = checkpoint.getKey();
                                }
                                if(!params.checkpoints.containsKey(nRandomBlockHeight)) {

                                    Object [] array = params.checkpoints.keySet().toArray();
                                    nRandomBlockHeight = (Integer)(array[array.length]);
                                }*/
                                getMasternodeRanks(vecMasternodeRanks, nRandomBlockHeight, CoinDefinition.PROTOCOL_VERSION);
                            }
                            boolean fAskedForMnbRecovery = false;
                            // ask first MNB_RECOVERY_QUORUM_TOTAL masternodes we can connect to and we haven't asked recently
                            for (int i = 0; setRequested.size() < MNB_RECOVERY_QUORUM_TOTAL && i < (int) vecMasternodeRanks.size(); i++) {
                                // avoid banning
                                if (mWeAskedForMasternodeListEntry.containsKey(entry.getKey()) && mWeAskedForMasternodeListEntry.get(entry.getKey()).containsKey(vecMasternodeRanks.get(i).getSecond().info.address))
                                    continue;
                                // didn't ask recently, ok to ask now
                                NetAddress addr = vecMasternodeRanks.get(i).getSecond().info.address;
                                setRequested.add(addr);
                                listScheduledMnbRequestConnections.add(new Pair(addr, hash));
                                fAskedForMnbRecovery = true;
                            }
                            if (fAskedForMnbRecovery) {
                                log.info("masternode--CMasternodeMan::CheckAndRemove -- Recovery initiated, masternode={}", entry.getKey().toStringShort());
                                nAskForMnbRecovery--;
                            }
                            // wait for mnb recovery replies for MNB_RECOVERY_WAIT_SECONDS seconds
                            mMnbRecoveryRequests.put(hash, new Pair(Utils.currentTimeSeconds() + MNB_RECOVERY_WAIT_SECONDS, setRequested));
                        }
                    }

                    // proces replies for MASTERNODE_NEW_START_REQUIRED masternodes
                    log.info("masternode--CMasternodeMan::CheckAndRemove -- mMnbRecoveryGoodReplies size={}", (int) mMnbRecoveryGoodReplies.size());
                    Iterator<Map.Entry<Sha256Hash, ArrayList<MasternodeBroadcast>>> itMnbReplies = mMnbRecoveryGoodReplies.entrySet().iterator();
                    while (itMnbReplies.hasNext()) {
                        Map.Entry<Sha256Hash, ArrayList<MasternodeBroadcast>> MnbReplies = itMnbReplies.next();
                        if (mMnbRecoveryRequests.get(MnbReplies.getKey()).getFirst() < Utils.currentTimeSeconds()) {
                            // all nodes we asked should have replied now
                            if (MnbReplies.getValue().size() >= MNB_RECOVERY_QUORUM_REQUIRED) {
                                // majority of nodes we asked agrees that this mn doesn't require new mnb, reprocess one of new mnbs
                                log.info("masternode--CMasternodeMan::CheckAndRemove -- reprocessing mnb, masternode={}", MnbReplies.getValue().get(0).info.outpoint.toStringShort());
                                // mapSeenMasternodeBroadcast.erase(itMnbReplies->first);
                                Dos dos = new Dos();
                                MnbReplies.getValue().get(0).fRecovery = true;
                                checkMnbAndUpdateMasternodeList(null, MnbReplies.getValue().get(0), dos);
                            }
                            log.info("masternode--CMasternodeMan::CheckAndRemove -- removing mnb recovery reply, masternode={}, size={}",
                                    MnbReplies.getValue().get(0).info.outpoint.toStringShort(), (int) MnbReplies.getValue().size());
                            itMnbReplies.remove();
                        }
                    }

                    Iterator<Map.Entry<Sha256Hash, Pair<Long, Set<MasternodeAddress>>>> itMnbRequest = mMnbRecoveryRequests.entrySet().iterator();
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
                    Iterator<Map.Entry<TransactionOutPoint, HashMap<MasternodeAddress, Long>>> it2 = mWeAskedForMasternodeListEntry.entrySet().iterator();
                    while (it2.hasNext()) {
                        Map.Entry<TransactionOutPoint, HashMap<MasternodeAddress, Long>> e = it2.next();
                        Iterator<Map.Entry<MasternodeAddress, Long>> it3 = e.getValue().entrySet().iterator();
                        while (it3.hasNext()) {
                            Map.Entry<MasternodeAddress, Long> e1 = it3.next();
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
                        if (mp.getValue().isExpired()) {
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

            } finally {
                lock.unlock();
            }

            if (fMasternodesRemoved) {
                notifyMasternodeUpdates();
            }

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
                if (pnode.isMasternode()) {
                    if (context.darkSendPool.submittedToMasternode != null && pnode.getAddress().getAddr().equals(context.darkSendPool.submittedToMasternode.info.address.getAddr()))
                        continue;
                    log.info("Closing Masternode connection {}", pnode.getAddress());
                    pnode.masternode = false;
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


    public MasternodeInfo getMasternodeInfo(TransactionOutPoint outpoint)
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
                Script scriptCollateralAddress = ScriptBuilder.createOutputScript(new Address(params, entry.getValue().info.pubKeyCollateralAddress.getId()));
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

    public Masternode find(MasternodeAddress address)
    {
        lock.lock();
        try {
            for(Map.Entry<TransactionOutPoint, Masternode> entry : mapMasternodes.entrySet()) {
                if(entry.getValue().info.address.equals(address))
                    return entry.getValue();
            }
        } finally {
            lock.unlock();
        }
        return null;
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
    /*boolean getNextMasternodeInQueueForPayment(boolean fFilterSigTime, int& nCountRet, masternode_info_t& mnInfoRet)
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

        Sha256Hash blockHash;
        if(!GetBlockHash(blockHash, nBlockHeight - 101)) {
            log.info("CMasternode::GetNextMasternodeInQueueForPayment -- ERROR: GetBlockHash() failed at nBlockHeight %d", nBlockHeight - 101);
            return false;
        }
        // Look at 1/10 of the oldest nodes (by last payment), calculate their scores and pay the best one
        //  -- This doesn't look at who is being paid in the +8-10 blocks, allowing for double payments very rarely
        //  -- 1/100 payments should be a double payment on mainnet - (1/(3000/10))*2
        //  -- (chance per block * chances before IsScheduled will fire)
        int nTenthNetwork = nMnCount/10;
        int nCountTenth = 0;
        arith_Sha256Hash nHighest = 0;
        CMasternode *pBestMasternode = NULL;
        BOOST_FOREACH (PAIRTYPE(int, CMasternode*)& s, vecMasternodeLastPaid){
        arith_Sha256Hash nScore = s.second->CalculateScore(blockHash);
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
    //}

    MasternodeInfo findRandomNotInVec(ArrayList<TransactionOutPoint> vecToExclude, int nProtocolVersion)
    {
        lock.lock();
        try {

            nProtocolVersion = nProtocolVersion == -1 ? context.masternodePayments.getMinMasternodePaymentsProto() : nProtocolVersion;

            int nCountEnabled = countEnabled(nProtocolVersion);
            int nCountNotExcluded = nCountEnabled - vecToExclude.size();

            log.info("CMasternodeMan::FindRandomNotInVec -- {} enabled masternodes, {} masternodes to choose from", nCountEnabled, nCountNotExcluded);
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
                    if (mn.info.outpoint == outpointToExclude) {
                        fExclude = true;
                        break;
                    }
                }
                if (fExclude) continue;
                // found the one not in vecToExclude
                log.info("masternode--CMasternodeMan::FindRandomNotInVec -- found, masternode={}", mn.info.outpoint.toStringShort());
                return mn.getInfo();
            }

            log.info("masternode--CMasternodeMan::FindRandomNotInVec -- failed");
            return new MasternodeInfo();
        } finally {
            lock.unlock();
        }
    }

    boolean getMasternodeScores(final Sha256Hash nBlockHash, ArrayList<Pair<Sha256Hash, Masternode>> vecMasternodeScoresRet, int nMinProtocol) {
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
                    vecMasternodeScoresRet.add(new Pair<Sha256Hash, Masternode>(mnpair.getValue().calculateScore(nBlockHash), mnpair.getValue()));
                }
            }

            //sort(vecMasternodeScoresRet.rbegin(), vecMasternodeScoresRet.rend(), CompareScoreMN());
            Collections.sort(vecMasternodeScoresRet, Collections.reverseOrder(new CompareScoreMN()));

            return !vecMasternodeScoresRet.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    class CompareConnections<T> implements Comparator<T>
    {
        @Override
        public int compare(T o1, T o2) {
            Pair<NetAddress, Sha256Hash> a = (Pair<NetAddress, Sha256Hash>)o1;
            Pair<NetAddress, Sha256Hash> b = (Pair<NetAddress, Sha256Hash>)o2;
            return a.getFirst().getAddr().hashCode() - b.getFirst().getAddr().hashCode();
        }

    }

    Pair<MasternodeAddress, HashSet<Sha256Hash>> popScheduledMnbRequestConnection()
    {
        lock.lock();
        try {


            if (listScheduledMnbRequestConnections.isEmpty()) {
                byte [] zero = {0,0,0,0};
                return new Pair(new MasternodeAddress(params), new HashSet<Sha256Hash>());
            }

            HashSet<Sha256Hash> setResult = new HashSet<Sha256Hash>();

            Collections.sort(listScheduledMnbRequestConnections, new CompareConnections<Pair<MasternodeAddress, Sha256Hash>>());
            Pair<MasternodeAddress, Sha256Hash> pairFront = listScheduledMnbRequestConnections.get(0);

            // squash hashes from requests with the same CService as the first one into setResult
            Iterator<Pair<MasternodeAddress, Sha256Hash>> it = listScheduledMnbRequestConnections.iterator();
            while (it.hasNext()) {
                Pair<MasternodeAddress, Sha256Hash> entry = it.next();
                if (pairFront.getFirst() == entry.getFirst()) {
                    setResult.add(entry.getSecond());
                    it.remove();
                } else {
                    // since list is sorted now, we can be sure that there is no more hashes left
                    // to ask for from this addr
                    break;
                }
            }
            return new Pair<MasternodeAddress, HashSet<Sha256Hash>>(pairFront.getFirst(), setResult);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called to notify CGovernanceManager that the masternode index has been updated.
     * Must be called while not holding the CMasternodeMan::cs mutex
     */
    void notifyMasternodeUpdates() {
        // Avoid double locking
        boolean fMasternodesAddedLocal = false;
        boolean fMasternodesRemovedLocal = false;

        lock.lock();
        try {
            fMasternodesAddedLocal = fMasternodesAdded;
            fMasternodesRemovedLocal = fMasternodesRemoved;
        } finally {
            lock.unlock();
        }

        if (fMasternodesAddedLocal) {
            //context.governance.checkMasternodeOrphanObjects(connman);
            //context.governance.checkMasternodeOrphanVotes(connman);
        }
        if (fMasternodesRemovedLocal) {
            //context.governance.UpdateCachesAndClean();
        }

        lock.lock();
        try {
            fMasternodesAdded = false;
            fMasternodesRemoved = false;
        } finally {
            lock.unlock();
        }
    }

    public void addDirtyGovernanceObjectHash(Sha256Hash nHash)
    {
        lock.lock();
        try {
            vecDirtyGovernanceObjectHashes.add(nHash);
        } finally {
            lock.unlock();
        }
    }

    public ArrayList<Sha256Hash> getAndClearDirtyGovernanceObjectHashes()
    {
        lock.lock();
        try {
            ArrayList<Sha256Hash> vecTmp = (ArrayList<Sha256Hash>)vecDirtyGovernanceObjectHashes.clone();
            vecDirtyGovernanceObjectHashes.clear();
            return vecTmp;
        } finally {
            lock.unlock();
        }
    }

    static boolean IsFirstRun = true;
    void updateLastPaid(StoredBlock pindex)
    {
        lock.lock();
        try {

            if (context.isLiteMode() || !context.masternodeSync.isWinnersListSynced() || mapMasternodes.isEmpty())
                return;

            // Do full scan on first run or if we are not a masternode
            // (MNs should update this info on every block, so limited scan should be enough for them)
            int nMaxBlocksToScanBack = (IsFirstRun || !DarkCoinSystem.fMasterNode) ? context.masternodePayments.getStorageLimit() : LAST_PAID_SCAN_BLOCKS;

            // LogPrint("mnpayments", "CMasternodeMan::UpdateLastPaid -- nHeight=%d, nMaxBlocksToScanBack=%d, IsFirstRun=%s",
            //                         nCachedBlockHeight, nMaxBlocksToScanBack, IsFirstRun ? "true" : "false");

            for (Map.Entry<TransactionOutPoint, Masternode> mnpair:mapMasternodes.entrySet()){
                mnpair.getValue().updateLastPaid(pindex, nMaxBlocksToScanBack);
            }

            IsFirstRun = false;
        } finally {
            lock.unlock();
        }
    }

    public void updateLastSentinelPingTime(final TransactionOutPoint outpoint) {
        updateLastSentinelPingTime(outpoint, 0);
    }

    public void updateLastSentinelPingTime(final TransactionOutPoint outpoint, long nVoteTime)
    {
        lock.lock();
        try {
            nLastSentinelPingTime = Utils.currentTimeSeconds();
        } finally {
            lock.unlock();
        }
    }

    boolean isSentinelPingActive()
    {
//        lock.lock();
        try {
            // Check if any masternodes have voted recently, otherwise return false
            return Utils.currentTimeSeconds() - nLastSentinelPingTime <= MASTERNODE_SENTINEL_MAX_SECONDS;
        } finally {
  //          lock.unlock();
        }
    }

    public boolean addGovernanceVote(final TransactionOutPoint outpoint, Sha256Hash nGovernanceObjectHash) {
        if(!context.masternodeSync.syncFlags.contains(MasternodeSync.SYNC_FLAGS.SYNC_MASTERNODE_LIST))
            return true;
        lock.lock();
        try {
            Masternode  mn = find(outpoint);
            if (mn == null) {
                return false;
            }
            mn.addGovernanceVote(nGovernanceObjectHash);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void removeGovernanceObject(Sha256Hash nGovernanceObjectHash)
    {
        lock.lock();
        try {
            for(Map.Entry<TransactionOutPoint, Masternode> mnpair : mapMasternodes.entrySet()) {
                mnpair.getValue().removeGovernanceObject(nGovernanceObjectHash);
            }
        } finally {
            lock.unlock();
        }
    }

    void checkMasternode(PublicKey pubKeyMasternode, boolean fForce)
    {
        lock.lock();
        try {
            for (Map.Entry<TransactionOutPoint, Masternode> mnpair : mapMasternodes.entrySet()) {
                if (mnpair.getValue().info.pubKeyMasternode == pubKeyMasternode) {
                    mnpair.getValue().check(fForce);
                    return;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    boolean isMasternodePingedWithin(final TransactionOutPoint outpoint, int nSeconds, long nTimeToCheckAt)
    {
        lock.lock();
        try {
            Masternode mn = find(outpoint);
            return mn != null ? mn.isPingedWithin(nSeconds, nTimeToCheckAt) : false;
        } finally {
            lock.unlock();
        }
    }

    void setMasternodeLastPing(final TransactionOutPoint outpoint, final MasternodePing mnp)
    {
        lock.lock();
        try {
            Masternode mn = find(outpoint);
            if(mn == null) {
                return;
            }
            mn.lastPing = mnp;
            // if masternode uses sentinel ping instead of watchdog
            // we shoud update nTimeLastWatchdogVote here if sentinel
            // ping flag is actual
            if(mnp.sentinelIsCurrent) {
                updateLastSentinelPingTime(mnp.masternodeOutpoint, mnp.sigTime);
            }
            mapSeenMasternodePing.put(mnp.getHash(), mnp);

            MasternodeBroadcast mnb = new MasternodeBroadcast(mn);
            Sha256Hash hash = mnb.getHash();
            if(mapSeenMasternodeBroadcast.containsKey(hash)) {
                mapSeenMasternodeBroadcast.get(hash).getSecond().lastPing = mnp;
            }
        } finally {
            lock.unlock();
        }
    }
    private int tipCount = 0;
    void updatedBlockTip(StoredBlock block)
    {
        nCachedBlockHeight = block.getHeight();
        if(tipCount++ % 100 == 0)
            log.info("masternode--CMasternodeMan::UpdatedBlockTip -- nCachedBlockHeight={}", nCachedBlockHeight);

        checkSameAddr();

        if(DarkCoinSystem.fMasterNode) {
            // normal wallet does not need to update this every block, doing update on rpc call should be enough
            updateLastPaid(block);
        }
    }

    class CompareByAddr<T> implements Comparator<T>
    {
        public int compare(T o1, T o2)
        {
            Masternode m1 = (Masternode)o1;
            Masternode m2 = (Masternode)o2;
            if(m1.info.address.equals(m2.info.address))
                return 0;
            return -1;

        }
    }

    // This function tries to find masternodes with the same addr,
    // find a verified one and ban all the other. If there are many nodes
    // with the same addr but none of them is verified yet, then none of them are banned.
    // It could take many times to run this before most of the duplicate nodes are banned.
    void checkSameAddr() {
        if (!context.masternodeSync.isSynced() || mapMasternodes.isEmpty()) return;

        ArrayList<Masternode> vBan = new ArrayList<Masternode>();
        ArrayList<Masternode> vSortedByAddr = new ArrayList<Masternode>();

        lock.lock();
        try {

            Masternode pprevMasternode = null;
            Masternode pverifiedMasternode = null;

            for (Map.Entry<TransactionOutPoint, Masternode> mnpair : mapMasternodes.entrySet()) {
                vSortedByAddr.add(mnpair.getValue());
            }

            Collections.sort(vSortedByAddr, new CompareByAddr());
            //sort(vSortedByAddr.begin(), vSortedByAddr.end(), CompareByAddr());

            for (Masternode mn : vSortedByAddr) {
                // check only (pre)enabled masternodes
                if (mn.isEnabled() && mn.isPreEnabled()) continue;
                // initial step
                if (pprevMasternode == null) {
                    pprevMasternode = mn;
                    pverifiedMasternode = mn.isPoSeVerified() ? mn : null;
                    continue;
                }
                // second+ step
                if (mn.info.address == pprevMasternode.info.address) {
                    if (pverifiedMasternode != null) {
                        // another masternode with the same ip is verified, ban this one
                        vBan.add(mn);
                    } else if (mn.isPoSeVerified()) {
                        // this masternode with the same ip is verified, ban previous one
                        vBan.add(pprevMasternode);
                        // and keep a reference to be able to ban following masternodes with the same ip
                        pverifiedMasternode = mn;
                    }
                } else {
                    pverifiedMasternode = mn.isPoSeVerified() ? mn : null;
                }
                pprevMasternode = mn;
            }
        } finally {
            lock.unlock();
        }

        // ban duplicates
        for (Masternode mn : vBan) {
            log.info("CMasternodeMan::CheckSameAddr -- increasing PoSe ban score for masternode {}", mn.info.outpoint.toStringShort());
            mn.increasePoSeBanScore();
        }
    }

    public List<Masternode> getMasternodes() {
        lock.lock();
        try {
            List<Masternode> masternodeList = new ArrayList<Masternode>(mapMasternodes.size());
            for (Map.Entry<TransactionOutPoint, Masternode> entry : mapMasternodes.entrySet()) {
                masternodeList.add(entry.getValue());
            }
            return masternodeList;
        } finally {
            lock.unlock();
        }
    }
}
