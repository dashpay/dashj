package org.bitcoinj.governance;

import org.bitcoinj.core.*;
import org.bitcoinj.governance.listeners.GovernanceObjectAddedEventListener;
import org.bitcoinj.governance.listeners.GovernanceVoteConfidenceEventListener;
import org.bitcoinj.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.governance.GovernanceException.Type.GOVERNANCE_EXCEPTION_PERMANENT_ERROR;
import static org.bitcoinj.governance.GovernanceException.Type.GOVERNANCE_EXCEPTION_WARNING;
import static org.bitcoinj.governance.GovernanceObject.*;

/**
 * Created by HashEngineering on 5/11/2018.
 */
public class GovernanceManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(GovernanceManager.class);
    // critical section to protect the inner data structures
    ReentrantLock lock = Threading.lock("GovernanceManager");

    public static final int MAX_GOVERNANCE_OBJECT_DATA_SIZE = 16 * 1024;
    public static final int MIN_GOVERNANCE_PEER_PROTO_VERSION = 70208;
    public static final int GOVERNANCE_FILTER_PROTO_VERSION = 70208;

    public static int nSubmittedFinalBudget;
    public static final int MAX_TIME_FUTURE_DEVIATION = 60*60;
    public static final int RELIABLE_PROPAGATION_TIME = 60;

    public static final String SERIALIZATION_VERSION_STRING = "CGovernanceManager-Version-12";


    private static final int MAX_CACHE_SIZE = 1000000;

    private long nTimeLastDiff;

    // keep track of current block height
    private int nCachedBlockHeight;

    // keep track of the scanning errors
    private HashMap<Sha256Hash, GovernanceObject> mapObjects;

    // mapErasedGovernanceObjects contains key-value pairs, where
    //   key   - governance object's hash
    //   value - expiration time for deleted objects
    private HashMap<Sha256Hash, Long> mapErasedGovernanceObjects;

    private HashMap<Sha256Hash, Pair<GovernanceObject, ExpirationInfo>> mapMasternodeOrphanObjects;
    private HashMap<TransactionOutPoint, Integer> mapMasternodeOrphanCounter;

    private HashMap<Sha256Hash, GovernanceObject> mapPostponedObjects;
    private HashSet<Sha256Hash> setAdditionalRelayObjects;

    private HashMap<Sha256Hash, Long> mapWatchdogObjects;

    private Sha256Hash nHashWatchdogCurrent;

    private long nTimeWatchdogCurrent;

    private CacheMap<Sha256Hash, GovernanceObject> mapVoteToObject;

    private CacheMap<Sha256Hash, GovernanceVote> mapInvalidVotes;

    private CacheMultiMap<Sha256Hash, Pair<GovernanceVote, Long>> mapOrphanVotes;

    private HashMap<TransactionOutPoint, LastObjectRecord> mapLastMasternodeObject;

    private HashSet<Sha256Hash> setRequestedObjects;

    private HashSet<Sha256Hash> setRequestedVotes;

    private boolean fRateChecksEnabled;

    public GovernanceManager(Context context) {
        super(context);
        this.nTimeLastDiff = 0;
        this.nCachedBlockHeight = 0;
        this.mapObjects = new HashMap<Sha256Hash, GovernanceObject>();
        this.mapErasedGovernanceObjects = new HashMap<Sha256Hash, Long>();
        this.mapMasternodeOrphanObjects = new HashMap<Sha256Hash, Pair<GovernanceObject, ExpirationInfo>>();
        this.mapWatchdogObjects = new HashMap<Sha256Hash, Long>();
        this.nHashWatchdogCurrent = Sha256Hash.ZERO_HASH;
        this.nTimeWatchdogCurrent = 0;
        this.mapVoteToObject = new CacheMap<Sha256Hash, GovernanceObject>(MAX_CACHE_SIZE);
        this.mapInvalidVotes = new CacheMap<Sha256Hash, GovernanceVote>(MAX_CACHE_SIZE);
        this.mapOrphanVotes = new CacheMultiMap<Sha256Hash, Pair<GovernanceVote, Long>>(MAX_CACHE_SIZE);
        this.mapLastMasternodeObject = new HashMap<TransactionOutPoint, LastObjectRecord>();
        this.setRequestedObjects = new HashSet<Sha256Hash>();
        this.setRequestedVotes = new HashSet<Sha256Hash>();
        this.fRateChecksEnabled = true;

        this.mapPostponedObjects = new HashMap<Sha256Hash, GovernanceObject>();
        this.mapMasternodeOrphanCounter = new HashMap<TransactionOutPoint, Integer>();

        this.governanceObjectAddedListeners = new CopyOnWriteArrayList<ListenerRegistration<GovernanceObjectAddedEventListener>>();
    }

    public GovernanceManager(NetworkParameters params, byte [] payload, int cursor) {
        super(params, payload, cursor);
    }

    public int calculateMessageSizeInBytes() {
        int size = 0;
        lock.lock();
        try {
            size += 1;
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

        //READWRITE(mapErasedGovernanceObjects);
        int size = (int)readVarInt();
        mapErasedGovernanceObjects = new HashMap<Sha256Hash, Long>(size);
        for(int i = 0; i < size; ++i) {
            Sha256Hash hash = readHash();
            long time = readInt64();
            mapErasedGovernanceObjects.put(hash, time);
        }

        //READWRITE(mapInvalidVotes);
        mapInvalidVotes = new CacheMap<Sha256Hash, GovernanceVote>(params, payload, cursor);
        cursor += mapInvalidVotes.getMessageSize();
        //READWRITE(mapOrphanVotes);
        mapOrphanVotes = new CacheMultiMap<Sha256Hash, Pair<GovernanceVote, Long>>(params, payload, cursor);
        cursor += mapOrphanVotes.getMessageSize();
        //READWRITE(mapObjects);
        size = (int)readVarInt();
        mapObjects = new HashMap<Sha256Hash, GovernanceObject>(size);
        for(int i = 0; i < size; ++i) {
            Sha256Hash hash = readHash();
            GovernanceObjectFromFile govobj = new GovernanceObjectFromFile(params, payload, cursor);
            cursor += govobj.getMessageSize();
            mapObjects.put(hash, govobj);
        }
        //READWRITE(mapWatchdogObjects);
        size = (int)readVarInt();
        mapWatchdogObjects = new HashMap<Sha256Hash, Long>(size);
        for(int i = 0; i < size; ++i) {
            Sha256Hash hash = readHash();
            long time = readInt64();
            mapWatchdogObjects.put(hash, time);
        }
        //READWRITE(nHashWatchdogCurrent);
        nHashWatchdogCurrent = readHash();
        //READWRITE(nTimeWatchdogCurrent);
        nTimeWatchdogCurrent = readInt64();
        //READWRITE(mapLastMasternodeObject);
        size = (int)readVarInt();
        mapLastMasternodeObject = new HashMap<TransactionOutPoint, LastObjectRecord>(size);
        for(int i = 0; i < size; ++i) {
            TransactionOutPoint outPoint = new TransactionOutPoint(params, payload, cursor);
            cursor += outPoint.getMessageSize();
            LastObjectRecord record = new LastObjectRecord(params, payload, cursor);
            cursor += record.getMessageSize();
            mapLastMasternodeObject.put(outPoint, record);
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

            //READWRITE(mapErasedGovernanceObjects);
            stream.write(new VarInt(mapErasedGovernanceObjects.size()).encode());
            for(Map.Entry<Sha256Hash, Long> entry : mapErasedGovernanceObjects.entrySet()) {
                stream.write(entry.getKey().getReversedBytes());
                Utils.int64ToByteStreamLE(entry.getValue(), stream);
            }
            //READWRITE(mapInvalidVotes);
            mapInvalidVotes.bitcoinSerialize(stream);
            //READWRITE(mapOrphanVotes);
            mapOrphanVotes.bitcoinSerialize(stream);
            //READWRITE(mapObjects);
            stream.write(new VarInt(mapObjects.size()).encode());
            for(Map.Entry<Sha256Hash, GovernanceObject> entry : mapObjects.entrySet()) {
                stream.write(entry.getKey().getReversedBytes());
                entry.getValue().bitcoinSerialize(stream);
                entry.getValue().serializeToDisk(stream);
            }
            //READWRITE(mapWatchdogObjects);
            stream.write(new VarInt(mapWatchdogObjects.size()).encode());
            for(Map.Entry<Sha256Hash, Long> entry : mapWatchdogObjects.entrySet()) {
                stream.write(entry.getKey().getReversedBytes());
                Utils.int64ToByteStreamLE(entry.getValue(), stream);
            }
            //READWRITE(nHashWatchdogCurrent);
            stream.write(nHashWatchdogCurrent.getReversedBytes());
            //READWRITE(nTimeWatchdogCurrent);
            Utils.int64ToByteStreamLE(nTimeWatchdogCurrent, stream);
            //READWRITE(mapLastMasternodeObject);
            stream.write(new VarInt(mapLastMasternodeObject.size()).encode());
            for(Map.Entry<TransactionOutPoint, LastObjectRecord> entry : mapLastMasternodeObject.entrySet()) {
                entry.getKey().bitcoinSerialize(stream);
                entry.getValue().bitcoinSerialize(stream);
            }
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            unCache();
            log.info("gobject--Governance object manager was cleared");
            mapObjects.clear();
            mapErasedGovernanceObjects.clear();
            mapWatchdogObjects.clear();
            nHashWatchdogCurrent = Sha256Hash.ZERO_HASH;
            nTimeWatchdogCurrent = 0;
            mapVoteToObject.clear();
            mapInvalidVotes.clear();
            mapOrphanVotes.clear();
            mapLastMasternodeObject.clear();

        } finally {
            lock.unlock();
        }
    }

    @Override
    public AbstractManager createEmpty() {
        return new GovernanceManager(Context.get());
    }

    public void processGovernanceObject(Peer peer, GovernanceObject govobj) {
        Sha256Hash nHash = govobj.getHash();

        peer.setAskFor.remove(nHash);

        if(!context.masternodeSync.isBlockchainSynced()) {
            log.info("gobject--MNGOVERNANCEOBJECT -- masternode list not synced");
            return;
        }

        String strHash = nHash.toString();

        log.info("gobject--MNGOVERNANCEOBJECT -- Received object: {}", strHash);

        if(!acceptObjectMessage(nHash)) {
            log.info("MNGOVERNANCEOBJECT -- Received unrequested object: {}", strHash);
            return;
        }

        lock.lock();
        try {

            if (mapObjects.containsKey(nHash) || mapPostponedObjects.containsKey(nHash) ||
                    mapErasedGovernanceObjects.containsKey(nHash) || mapMasternodeOrphanObjects.containsKey(nHash)) {
                // TODO - print error code? what if it's GOVOBJ_ERROR_IMMATURE?
                log.info("gobject--MNGOVERNANCEOBJECT -- Received already seen object: {}", strHash);
                return;
            }

            boolean fRateCheckBypassed = false;
            if (!masternodeRateCheck(govobj, true, false).getFirst()) {
                log.info("MNGOVERNANCEOBJECT -- masternode rate check failed - {} - (current block height {})", strHash, nCachedBlockHeight);
                return;
            }

            StringBuilder strError = new StringBuilder();
            // CHECK OBJECT AGAINST LOCAL BLOCKCHAIN

            GovernanceObject.Validity validity = new GovernanceObject.Validity();
            boolean fIsValid = govobj.isValidLocally(validity, true);

            if (fRateCheckBypassed && (fIsValid || validity.fMissingMasternode)) {
                if (!masternodeRateCheck(govobj, true)) {
                    log.info("MNGOVERNANCEOBJECT -- masternode rate check failed (after signature verification) - {} - (current block height {})", strHash, nCachedBlockHeight);
                    return;
                }
            }

            if (!fIsValid) {
                if (validity.fMissingMasternode) {

                int count = mapMasternodeOrphanCounter.get(govobj.getMasternodeOutpoint());
                    if (count >= 10) {
                        log.info("gobject--MNGOVERNANCEOBJECT -- Too many orphan objects, missing masternode={}", govobj.getMasternodeOutpoint().toStringShort());
                        // ask for this object again in 2 minutes
                        InventoryItem inv = new InventoryItem(InventoryItem.Type.GovernanceObject, govobj.getHash());
                        peer.askFor(inv);
                        return;
                    }

                    count++;
                    mapMasternodeOrphanCounter.put(govobj.getMasternodeOutpoint(), count);
                    ExpirationInfo info = new ExpirationInfo(peer.hashCode(), Utils.currentTimeSeconds() + GOVERNANCE_ORPHAN_EXPIRATION_TIME);
                    mapMasternodeOrphanObjects.put(nHash, new Pair<GovernanceObject, ExpirationInfo>(govobj, info));
                    log.info("MNGOVERNANCEOBJECT -- Missing masternode for: {}, strError = {}", strHash, strError);
                } else if (validity.fMissingConfirmations) {
                    addPostponedObject(govobj);
                    log.info("MNGOVERNANCEOBJECT -- Not enough fee confirmations for: {}, strError = {}", strHash, strError);
                } else {
                    log.info("MNGOVERNANCEOBJECT -- Governance object is invalid - {}", strError);
                    // apply node's ban score
                    //Misbehaving(pfrom -> GetId(), 20);
                }

                return;
            }

            addGovernanceObject(govobj, peer);
        } finally {
            lock.unlock();
        }
    }

    public void processGovernanceObjectVote(Peer peer, GovernanceVote vote) {
        Sha256Hash nHash = vote.getHash();

        peer.setAskFor.remove(nHash);

        // Ignore such messages until masternode list is synced
        if (!context.masternodeSync.isBlockchainSynced()) {
            log.info("gobject--MNGOVERNANCEOBJECTVOTE -- masternode list not synced");
            return;
        }

        log.info("gobject--MNGOVERNANCEOBJECTVOTE -- Received vote: {}", vote.toString());

        String strHash = nHash.toString();

        if (!acceptVoteMessage(nHash)) {
            log.info("gobject--MNGOVERNANCEOBJECTVOTE -- Received unrequested vote object: {}, hash: {}, peer = {}", vote.toString(), strHash, peer.hashCode());
            return;
        }

        GovernanceException exception = new GovernanceException();
        if (processVote(peer, vote, exception)) {
            log.info("gobject--MNGOVERNANCEOBJECTVOTE -- {} new", strHash);
            context.masternodeSync.bumpAssetLastTime("processGovernanceObjectVote");
            vote.relay();
        } else {
            log.info("gobject--MNGOVERNANCEOBJECTVOTE -- Rejected vote, error = {}", exception.getMessage());
            if ((exception.getNodePenalty() != 0) && context.masternodeSync.isSynced()) {
                //Misbehaving(pfrom.GetId(), exception.GetNodePenalty());
            }
            return;
        }

    }

    public boolean processVote(Peer pfrom, GovernanceVote vote, GovernanceException exception) {
        lock.lock();
        try {
            Sha256Hash nHashVote = vote.getHash();
            if (mapInvalidVotes.hasKey(nHashVote)) {
                String message = "CGovernanceManager::ProcessVote -- Old invalid vote, MN outpoint = " + vote.getMasternodeOutpoint().toStringShort() +
                        ", governance object hash = " + vote.getParentHash().toString();
                log.info(message);
                exception.setException(message, GOVERNANCE_EXCEPTION_PERMANENT_ERROR, 20);
                return false;
            }

            Sha256Hash nHashGovobj = vote.getParentHash();
            GovernanceObject govobj = mapObjects.get(nHashGovobj);
            if (govobj == null) {
                String message = "CGovernanceManager::ProcessVote -- Unknown parent object, MN outpoint = " + vote.getMasternodeOutpoint().toStringShort() +
                        ", governance object hash = " + vote.getParentHash().toString();
                exception.setException(message, GOVERNANCE_EXCEPTION_WARNING);
                if (mapOrphanVotes.insert(nHashGovobj, new Pair<GovernanceVote, Long>(vote, Utils.currentTimeSeconds() + GOVERNANCE_ORPHAN_EXPIRATION_TIME))) {
                    requestGovernanceObject(pfrom, nHashGovobj, false);
                    log.info(message);
                    return false;
                }

                log.info("gobject--{}", message);
                return false;
            }

            if (govobj.isSetCachedDelete() || govobj.isSetExpired()) {
                log.info("gobject--CGovernanceObject::ProcessVote -- ignoring vote for expired or deleted object, hash = {}", nHashGovobj.toString());
                return false;
            }

            boolean fOk = govobj.processVote(pfrom, vote, exception);
            if (fOk) {
                mapVoteToObject.insert(nHashVote, govobj);

                /* TODO:  Fix Governance Objects
                if (govobj.getObjectType() == GOVERNANCE_OBJECT_WATCHDOG) {
                    context.masternodeManager.updateLastSentinelPingTime(vote.getMasternodeOutpoint());
                    log.info("gobject--CGovernanceObject::ProcessVote -- GOVERNANCE_OBJECT_WATCHDOG vote for {}", vote.getParentHash());
                }*/
            }
            return fOk;
        } finally {
            lock.unlock();
        }
    }


    public boolean acceptObjectMessage(Sha256Hash nHash) {
        lock.lock();
        try {
            return acceptMessage(nHash, setRequestedObjects);
        } finally {
            lock.unlock();
        }
    }
    public boolean acceptVoteMessage(Sha256Hash nHash) {
        lock.lock();
        try {
            return acceptMessage(nHash, setRequestedVotes);
        } finally {
            lock.unlock();
        }
    }
    public boolean acceptMessage(Sha256Hash nHash, HashSet<Sha256Hash> setHash) {
        if(!setHash.contains(nHash)) {
            // We never requested this
            return false;
        }
        // Only accept one response
        setHash.remove(nHash);
        return true;
    }

    public void masternodeRateUpdate(GovernanceObject govobj) {
        int nObjectType = govobj.getObjectType();
        if ((nObjectType != GOVERNANCE_OBJECT_TRIGGER) && (nObjectType != GOVERNANCE_OBJECT_WATCHDOG)) {
            return;
        }

        final TransactionOutPoint outpoint = govobj.getMasternodeOutpoint();
        LastObjectRecord it = mapLastMasternodeObject.get(outpoint);

        if (it == null) {
            it = new LastObjectRecord(params, true);
            mapLastMasternodeObject.put(outpoint, it);
        }

        long nTimestamp = govobj.getCreationTime();
        if (GOVERNANCE_OBJECT_TRIGGER == nObjectType) {
            it.triggerBuffer.addTimestamp(nTimestamp);
        } else if (GOVERNANCE_OBJECT_WATCHDOG == nObjectType) {
            it.watchdogBuffer.addTimestamp(nTimestamp);
        }

        if (nTimestamp > Utils.currentTimeSeconds() + MAX_TIME_FUTURE_DEVIATION - RELIABLE_PROPAGATION_TIME) {
            // schedule additional relay for the object
            setAdditionalRelayObjects.add(govobj.getHash());
        }

        it.fStatusOK = true;
    }


    public boolean masternodeRateCheck(GovernanceObject govobj, boolean fUpdateFailStatus) {
        Pair<Boolean, Boolean> result =  masternodeRateCheck(govobj, fUpdateFailStatus, true);
        return result.getFirst();
    }


    /**
     * Masternode rate check.
     *
     * @param govobj            the govobj
     * @param fUpdateFailStatus the f update fail status
     * @param fForce            the f force ok
     * @return the pair success, fRateCheckBypassed
     */
    public Pair<Boolean, Boolean> masternodeRateCheck(GovernanceObject govobj, boolean fUpdateFailStatus, boolean fForce) {
        lock.lock();
        Pair<Boolean, Boolean> result = new Pair<Boolean, Boolean>(false, false);
        try {

            result.setSecond(false);

            if (!context.masternodeSync.isSynced()) {
                result.setFirst(true);
                return result;
            }

            if (!fRateChecksEnabled) {
                result.setFirst(true);
                return result;
            }

            int nObjectType = govobj.getObjectType();
            if ((nObjectType != GOVERNANCE_OBJECT_TRIGGER) && (nObjectType != GOVERNANCE_OBJECT_WATCHDOG)) {
                result.setFirst(true);
                return result;
            }

            final TransactionOutPoint outpoint = govobj.getMasternodeOutpoint();
            long nTimestamp = govobj.getCreationTime();
            long nNow = Utils.currentTimeSeconds();
            long nSuperblockCycleSeconds = (long)params.getSuperblockCycle() * params.TARGET_SPACING;

            String strHash = govobj.getHash().toString();

            if (nTimestamp < nNow - 2 * nSuperblockCycleSeconds) {
                log.info("CGovernanceManager::MasternodeRateCheck -- object {} rejected due to too old timestamp, masternode vin = {}, timestamp = {}, current time = {}", strHash, outpoint.toStringShort(), nTimestamp, nNow);
                result.setFirst(false);
                return result;
            }

            if (nTimestamp > nNow + MAX_TIME_FUTURE_DEVIATION) {
                log.info("CGovernanceManager::MasternodeRateCheck -- object {} rejected due to too new (future) timestamp, masternode vin = {}, timestamp = {}d, current time = {}", strHash, outpoint.toStringShort(), nTimestamp, nNow);
                result.setFirst(false);
                return result;
            }

            LastObjectRecord it = mapLastMasternodeObject.get(outpoint);
            if (it ==null) {
                result.setFirst(true);
                return result;
            }

            if (it.fStatusOK && !fForce) {
                result.setSecond(true);
                result.setFirst(true);
                return result;
            }

            double dMaxRate = 1.1 / nSuperblockCycleSeconds;
            double dRate = 0.0;
            RateCheckBuffer buffer = new RateCheckBuffer(params);
            switch (nObjectType) {
                case GOVERNANCE_OBJECT_TRIGGER:
                    // Allow 1 trigger per mn per cycle, with a small fudge factor
                    buffer = it.triggerBuffer;
                    dMaxRate = 2 * 1.1 / (double) nSuperblockCycleSeconds;
                    break;
                case GOVERNANCE_OBJECT_WATCHDOG:
                    buffer = it.watchdogBuffer;
                    dMaxRate = 2 * 1.1 / 3600.0;
                    break;
                default:
                    break;
            }

            buffer.addTimestamp(nTimestamp);
            dRate = buffer.getRate();

            boolean fRateOK = (dRate < dMaxRate);

            if (!fRateOK) {
                log.info("CGovernanceManager::MasternodeRateCheck -- Rate too high: object hash = {}, " +
                         "masternode vin = {}, object timestamp = {}, rate = {}, max rate = {}",
                          strHash, outpoint.toStringShort(), nTimestamp, dRate, dMaxRate);

                if (fUpdateFailStatus) {
                    it.fStatusOK = false;
                }
            }

            result.setFirst(fRateOK);
            return result;
        } finally {
            lock.unlock();
        }
    }

    void addPostponedObject(final GovernanceObject govobj)
    {
        lock.lock();
        try {
            mapPostponedObjects.put(govobj.getHash(), govobj);
        } finally {
            lock.unlock();
        }
    }

    long getLastDiffTime() { return nTimeLastDiff; }
    void updateLastDiffTime(long nTimeIn) { nTimeLastDiff = nTimeIn; }

    int getCachedBlockHeight() { return nCachedBlockHeight; }

    public void addInvalidVote(final GovernanceVote vote)
    {
        mapInvalidVotes.insert(vote.getHash(), vote);
    }

    void addOrphanVote(final GovernanceVote vote)
    {
        mapOrphanVotes.insert(vote.getHash(), new Pair<GovernanceVote, Long>(vote, Utils.currentTimeSeconds() + GOVERNANCE_ORPHAN_EXPIRATION_TIME));
    }

    public boolean areRateChecksEnabled() {
        lock.lock();
        try {
            return fRateChecksEnabled;
        } finally {
            lock.unlock();
        }
    }

    public void checkOrphanVotes(GovernanceObject govobj, GovernanceException exception) {
        Sha256Hash nHash = govobj.getHash();
        ArrayList<Pair<GovernanceVote, Long>> vecVotePairs = new ArrayList<Pair<GovernanceVote, Long>>();
        mapOrphanVotes.getAll(nHash, vecVotePairs);

        lock.lock();
        boolean _fRateChecksEnabled = fRateChecksEnabled;
        fRateChecksEnabled = false;
        try {

            long nNow = Utils.currentTimeSeconds();
            for (int i = 0; i < vecVotePairs.size(); ++i) {
                boolean fRemove = false;
                Pair<GovernanceVote, Long> pairVote = vecVotePairs.get(i);
                GovernanceVote vote = pairVote.getFirst();

                if (pairVote.getSecond() < nNow) {
                    fRemove = true;
                } else if (govobj.processVote(null, vote, exception)) {
                    vote.relay();
                    fRemove = true;
                }
                if (fRemove) {
                    mapOrphanVotes.erase(nHash, pairVote);
                }
            }
        } finally {
            fRateChecksEnabled = _fRateChecksEnabled;
            lock.unlock();
        }
    }


    void addGovernanceObject(GovernanceObject govobj, Peer pfrom)
    {
        log.info("CGovernanceManager::AddGovernanceObject START");

        Sha256Hash nHash = govobj.getHash();
        String strHash = nHash.toString();

        // UPDATE CACHED VARIABLES FOR THIS OBJECT AND ADD IT TO OUR MANANGED DATA

        govobj.updateSentinelVariables(); //this sets local vars in object

        //LOCK2(cs_main, cs);
        lock.lock();
        try {
            GovernanceObject.Validity validity = new GovernanceObject.Validity();

            // MAKE SURE THIS OBJECT IS OK

            if(!govobj.isValidLocally(validity, true)) {
                log.info("CGovernanceManager::AddGovernanceObject -- invalid governance object - {} - (nCachedBlockHeight {})", validity.strError, nCachedBlockHeight);
                return;
            }

            // IF WE HAVE THIS OBJECT ALREADY, WE DON'T WANT ANOTHER COPY

            if(mapObjects.containsKey(nHash)) {
                log.info("CGovernanceManager::AddGovernanceObject -- already have governance object {}", nHash.toString());
                return;
            }

            log.info("gobject--CGovernanceManager::AddGovernanceObject -- Adding object: hash = {}, type = {}", nHash.toString(), govobj.getObjectType());

            if(govobj.getObjectType() == GOVERNANCE_OBJECT_WATCHDOG) {
                // If it's a watchdog, make sure it fits required time bounds
                if((govobj.getCreationTime() < Utils.currentTimeSeconds() - GOVERNANCE_WATCHDOG_EXPIRATION_TIME ||
                        govobj.getCreationTime() > Utils.currentTimeSeconds() + GOVERNANCE_WATCHDOG_EXPIRATION_TIME)
                        ) {
                    // drop it
                    log.info("gobject--CGovernanceManager::AddGovernanceObject -- CreationTime is out of bounds: hash = {}", nHash.toString());
                    return;
                }

                if(!updateCurrentWatchdog(govobj)) {
                    // Allow wd's which are not current to be reprocessed
                    if(pfrom != null && !nHashWatchdogCurrent.isZero()) {
                        pfrom.pushInventory(new InventoryItem(InventoryItem.Type.GovernanceObject, nHashWatchdogCurrent));
                    }
                    log.info("gobject--CGovernanceManager::AddGovernanceObject -- Watchdog not better than current: hash = {}", nHash.toString());
                    return;
                }
            }

            // INSERT INTO OUR GOVERNANCE OBJECT MEMORY
            mapObjects.put(nHash, govobj);
            queueOnGovernanceObjectAdded(nHash, govobj);
            unCache();

            // SHOULD WE ADD THIS OBJECT TO ANY OTHER MANANGERS?

                log.info( "CGovernanceManager::AddGovernanceObject Before trigger block, strData = "
                    + govobj.getDataAsPlainString() +
                    ", nObjectType = " + govobj.getObjectType());

            switch(govobj.getObjectType()) {
                case GOVERNANCE_OBJECT_TRIGGER:
                    log.info("CGovernanceManager::AddGovernanceObject Before AddNewTrigger");
                    context.triggerManager.addNewTrigger(nHash);
                    log.info("CGovernanceManager::AddGovernanceObject After AddNewTrigger");
                    break;
                case GOVERNANCE_OBJECT_WATCHDOG:
                    mapWatchdogObjects.put(nHash, govobj.getCreationTime() + GOVERNANCE_WATCHDOG_EXPIRATION_TIME);
                    log.info("gobject--CGovernanceManager::AddGovernanceObject -- Added watchdog to map: hash = {}", nHash.toString());
                    break;
                default:
                    break;
            }

            log.info("AddGovernanceObject -- {} new, received from {}", strHash, pfrom != null ? pfrom.getAddress().getHostname() : "NULL");
            govobj.relay();

            // Update the rate buffer
            masternodeRateUpdate(govobj);

            context.masternodeSync.bumpAssetLastTime("addGovernanceObject");

            // WE MIGHT HAVE PENDING/ORPHAN VOTES FOR THIS OBJECT

            GovernanceException exception = new GovernanceException();
            checkOrphanVotes(govobj, exception);

            log.info("CGovernanceManager::AddGovernanceObject END");
        } finally {
            lock.unlock();
        }
    }

    public boolean updateCurrentWatchdog(GovernanceObject watchdogNew) {
        boolean fAccept = false;

        BigInteger nHashNew = new BigInteger(watchdogNew.getHash().getBytes());
        BigInteger nHashCurrent = new BigInteger(nHashWatchdogCurrent.getBytes());

        long nExpirationDelay = GOVERNANCE_WATCHDOG_EXPIRATION_TIME / 2;
        long nNow = Utils.currentTimeSeconds();

        if (nHashWatchdogCurrent.isZero() || ((nNow - watchdogNew.getCreationTime() < nExpirationDelay) && ((nNow - nTimeWatchdogCurrent > nExpirationDelay) || (nHashNew.compareTo(nHashCurrent) > 0))))
        { //  (current is expired OR -  (new one is NOT expired AND -  no known current OR
            //   its hash is lower))
            lock.lock();
            try {
                GovernanceObject it = mapObjects.get(nHashWatchdogCurrent);
                if (it != null) {
                    log.info("gobject--CGovernanceManager::UpdateCurrentWatchdog -- Expiring previous current watchdog, hash = {}", nHashWatchdogCurrent.toString());
                    it.setExpired(true);
                    if (it.getDeletionTime() == 0) {
                        it.setDeletionTime(nNow);
                    }
                }
                nHashWatchdogCurrent = watchdogNew.getHash();
                nTimeWatchdogCurrent = watchdogNew.getCreationTime();
                fAccept = true;
                log.info("gobject--CGovernanceManager::UpdateCurrentWatchdog -- Current watchdog updated to: hash = {}", Sha256Hash.wrap(nHashNew.toByteArray()).toString());
            } finally {
                lock.unlock();
            }
        }

        return fAccept;
    }

    public GovernanceObject findGovernanceObject(Sha256Hash nHash)
    {
        lock.lock();
        try {
            return mapObjects.get(nHash);
        } finally {
            lock.unlock();
        }
    }

    public void doMaintenance()
    {
        if(context.isLiteMode() || !context.masternodeSync.isSynced()) return;

        // CHECK OBJECTS WE'VE ASKED FOR, REMOVE OLD ENTRIES

        cleanOrphanObjects();

        requestOrphanObjects();

        // CHECK AND REMOVE - REPROCESS GOVERNANCE OBJECTS

        updateCachesAndClean();
    }

    public boolean confirmInventoryRequest(InventoryItem inv) {

        lock.lock();
        try {

            //log.info("gobject--CGovernanceManager::ConfirmInventoryRequest inv = {}", inv);

            // First check if we've already recorded this object
            switch (inv.type) {
                case GovernanceObject:
                    if (mapObjects.containsKey(inv.hash) || mapPostponedObjects.containsKey(inv.hash)) {
                        log.info("gobject--CGovernanceManager::ConfirmInventoryRequest already have governance object, returning false, object = {}", inv);
                        return false;
                    }
                    break;
                case GovernanceObjectVote:
                    if (mapVoteToObject.hasKey(inv.hash)) {
                        log.info("gobject--CGovernanceManager::ConfirmInventoryRequest already have governance vote, returning false, vote = {}", inv);
                        return false;
                    }
                    break;
                default:
                    log.info("gobject--CGovernanceManager::ConfirmInventoryRequest unknown type, returning false");
                    return false;
            }


            HashSet<Sha256Hash> setHash = null;
            switch (inv.type) {
                case GovernanceObject:
                    setHash = setRequestedObjects;
                    break;
                case GovernanceObjectVote:
                    setHash = setRequestedVotes;
                    break;
                default:
                    return false;
            }

            boolean hasHash = setHash.contains(inv.hash);
            if (!hasHash) {
                setHash.add(inv.hash);
                log.info("gobject--CGovernanceManager::ConfirmInventoryRequest added inv to requested set, {}, object = {}", inv.type, inv);
            }

            //log.info("gobject--CGovernanceManager::ConfirmInventoryRequest reached end, returning true");
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void checkAndRemove() {
        updateCachesAndClean();
    }

    public void updateCachesAndClean() {
        log.info("gobject--CGovernanceManager::UpdateCachesAndClean");

        ArrayList<Sha256Hash> vecDirtyHashes = context.masternodeMetaDataManager.getAndClearDirtyGovernanceObjectHashes();
        
        lock.lock();
        try {

            // Flag expired watchdogs for removal
            long nNow = Utils.currentTimeSeconds();
            log.info("gobject--CGovernanceManager::UpdateCachesAndClean -- Number watchdogs in map: {}, current time = {}", mapWatchdogObjects.size(), nNow);
            if (mapWatchdogObjects.size() > 1) {
                Iterator<Map.Entry<Sha256Hash, Long>> it = mapWatchdogObjects.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Sha256Hash, Long> entry = it.next();
                    log.info("gobject--CGovernanceManager::UpdateCachesAndClean -- Checking watchdog: {}, expiration time = {}", entry.getKey(), entry.getValue());
                    if (entry.getValue() < nNow) {
                        log.info("gobject--CGovernanceManager::UpdateCachesAndClean -- Attempting to expire watchdog: {}, expiration time = {}", entry.getKey(), entry.getValue());
                        GovernanceObject governanceObject = mapObjects.get(entry.getKey());
                        if (governanceObject != null) {
                            log.info("gobject--CGovernanceManager::UpdateCachesAndClean -- Expiring watchdog: {}, expiration time = {}", entry.getValue(), entry.getValue());
                            governanceObject.setExpired(true);
                            if (governanceObject.getDeletionTime() == 0) {
                                governanceObject.setDeletionTime(nNow);
                            }
                        }
                        if (entry.getKey().equals(nHashWatchdogCurrent)) {
                            nHashWatchdogCurrent = Sha256Hash.ZERO_HASH;
                        }
                        it.remove();
                    }
                }
            }

            for (int i = 0; i < vecDirtyHashes.size(); ++i) {
                GovernanceObject it = mapObjects.get(vecDirtyHashes.get(i));
                if (it == null) {
                    continue;
                }
                it.clearMasternodeVotes();
                it.setDirtyCache(true);
            }

            //ScopedLockBool guard = new ScopedLockBool(cs, fRateChecksEnabled, false);
            lock.lock();
            boolean _fRateChecksEnabled = fRateChecksEnabled;
            fRateChecksEnabled = false;
            try {

                // UPDATE CACHE FOR EACH OBJECT THAT IS FLAGGED DIRTYCACHE=TRUE

                Iterator<Map.Entry<Sha256Hash, GovernanceObject>> it = mapObjects.entrySet().iterator();

                // Clean up any expired or invalid triggers
                context.triggerManager.cleanAndRemove();

                while (it.hasNext()) {
                    Map.Entry<Sha256Hash, GovernanceObject> entry = it.next();
                    GovernanceObject pObj = entry.getValue();

                    if (pObj == null) {
                        continue;
                    }

                    Sha256Hash nHash = entry.getKey();
                    String strHash = nHash.toString();

                    // IF CACHE IS NOT DIRTY, WHY DO THIS?
                    if (pObj.isSetDirtyCache()) {
                        // UPDATE LOCAL VALIDITY AGAINST CRYPTO DATA
                        pObj.updateLocalValidity();

                        // UPDATE SENTINEL SIGNALING VARIABLES
                        pObj.updateSentinelVariables();
                    }

                    if (pObj.isSetCachedDelete() && (nHash == nHashWatchdogCurrent)) {
                        nHashWatchdogCurrent = Sha256Hash.ZERO_HASH;
                    }

                    // IF DELETE=TRUE, THEN CLEAN THE MESS UP!

                    long nTimeSinceDeletion = Utils.currentTimeSeconds() - pObj.getDeletionTime();

                    log.info("gobject--CGovernanceManager::UpdateCachesAndClean -- Checking object for deletion: {}, deletion time = {}, time since deletion = {}, delete flag = {}, expired flag = {}",
                            strHash, pObj.getDeletionTime(), nTimeSinceDeletion, pObj.isSetCachedDelete(), pObj.isSetExpired());

                    if ((pObj.isSetCachedDelete() || pObj.isSetExpired()) && (nTimeSinceDeletion >= GOVERNANCE_DELETION_DELAY)) {
                        log.info("CGovernanceManager::UpdateCachesAndClean -- erase obj {}", entry.getValue());
                        context.masternodeMetaDataManager.removeGovernanceObject(pObj.getHash());

                        // Remove vote references
                        final LinkedList<CacheItem<Sha256Hash, GovernanceObject>> listItems = mapVoteToObject.getItemList();
                        Iterator<CacheItem<Sha256Hash, GovernanceObject>> lit = listItems.iterator();
                        while (lit.hasNext()) {
                            CacheItem<Sha256Hash, GovernanceObject> item = lit.next();
                            if (item.value == pObj) {
                                Sha256Hash nKey = item.key;
                                //mapVoteToObject.erase(nKey);//TODO: crash here?
                                lit.remove();
                            }
                        }

                        long nSuperblockCycleSeconds = (long)params.getSuperblockCycle() * params.TARGET_SPACING;
                        long nTimeExpired = pObj.getCreationTime() + 2 * nSuperblockCycleSeconds + GOVERNANCE_DELETION_DELAY;

                        if (pObj.getObjectType() == GOVERNANCE_OBJECT_WATCHDOG) {
                            mapWatchdogObjects.remove(nHash);
                        } else if (pObj.getObjectType() != GOVERNANCE_OBJECT_TRIGGER) {
                            // keep hashes of deleted proposals forever
                            nTimeExpired = Long.MAX_VALUE;
                        }

                        mapErasedGovernanceObjects.put(nHash, nTimeExpired);
                        it.remove();
                    }
                }

                // forget about expired deleted objects
                Iterator<Map.Entry<Sha256Hash, Long>> sIt = mapErasedGovernanceObjects.entrySet().iterator();
                while (sIt.hasNext()) {
                    if (sIt.next().getValue() < nNow) {
                        sIt.remove();
                    }
                }
            } finally {
                fRateChecksEnabled = _fRateChecksEnabled;
                lock.unlock();
            }

            log.info("CGovernanceManager::UpdateCachesAndClean -- {}", toString());
            unCache();
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        lock.lock();
        try {
            int nProposalCount = 0;
            int nTriggerCount = 0;
            int nWatchdogCount = 0;
            int nOtherCount = 0;

            Iterator<Map.Entry<Sha256Hash, GovernanceObject>> it = mapObjects.entrySet().iterator();

            while (it.hasNext()) {
                switch (it.next().getValue().getObjectType()) {
                    case GOVERNANCE_OBJECT_PROPOSAL:
                        nProposalCount++;
                        break;
                    case GOVERNANCE_OBJECT_TRIGGER:
                        nTriggerCount++;
                        break;
                    case GOVERNANCE_OBJECT_WATCHDOG:
                        nWatchdogCount++;
                        break;
                    default:
                        nOtherCount++;
                        break;
                }
            }

            return String.format("Governance Objects: %d (Proposals: %d, Triggers: %d, Watchdogs: %d/%d, Other: %d; Erased: %d), Votes: %d",
                    mapObjects.size(), nProposalCount, nTriggerCount, nWatchdogCount, mapWatchdogObjects.size(), nOtherCount,
                    mapErasedGovernanceObjects.size(), (int) mapVoteToObject.getSize());
        } finally {
            lock.unlock();
        }
    }

    public void updatedBlockTip(StoredBlock newBlock) {
        // Note this gets called from ActivateBestChain without cs_main being held
        // so it should be safe to lock our mutex here without risking a deadlock
        // On the other hand it should be safe for us to access pindex without holding a lock
        // on cs_main because the CBlockIndex objects are dynamically allocated and
        // presumably never deleted.
        if (newBlock == null) {
            return;
        }

        nCachedBlockHeight = newBlock.getHeight();
        log.info("gobject--CGovernanceManager::UpdatedBlockTip -- nCachedBlockHeight: {}\n", nCachedBlockHeight);

        checkPostponedObjects();
    }
    public void requestOrphanObjects() {


        ArrayList<Sha256Hash> vecHashesFiltered = new ArrayList<Sha256Hash>();
        ArrayList<Sha256Hash> vecHashes = new ArrayList<Sha256Hash>();
        lock.lock();
        try {
            mapOrphanVotes.getKeys(vecHashes);
            for (int i = 0; i < vecHashes.size(); ++i) {
                final Sha256Hash nHash = vecHashes.get(i);
                if (!mapObjects.containsKey(nHash)) {
                    vecHashesFiltered.add(nHash);
                }
            }
        } finally {
            lock.unlock();
        }

        log.info("gobject--CGovernanceObject::RequestOrphanObjects -- number objects = {}\n", vecHashesFiltered.size());

        PeerGroup peerGroup = context.peerGroup;
        peerGroup.getLock().lock();
        try {
            for (int i = 0; i < vecHashesFiltered.size(); ++i) {
                final Sha256Hash nHash = vecHashesFiltered.get(i);
                for (int j = 0; j < peerGroup.getConnectedPeers().size(); ++j) {
                    Peer node = peerGroup.getConnectedPeers().get(j);
                    if (node.isMasternode()) {
                        continue;
                    }
                    requestGovernanceObject(node, nHash, false);
                }
            }
        } finally {
            peerGroup.getLock().unlock();
        }
    }
    public void cleanOrphanObjects() {

        lock.lock();
        try {
            final LinkedList<CacheItem<Sha256Hash, Pair<GovernanceVote, Long>>> items = mapOrphanVotes.getItemList();

            long nNow = Utils.currentTimeSeconds();

            Iterator<CacheItem<Sha256Hash, Pair<GovernanceVote, Long>>> it = items.iterator();
            while (it.hasNext()) {
                CacheItem<Sha256Hash, Pair<GovernanceVote, Long>> item = it.next();

                final Pair<GovernanceVote, Long> pairVote = item.value;
                if (pairVote.getSecond() < nNow) {
                    mapOrphanVotes.erase(item.key, item.value);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void requestGovernanceObject(Peer pfrom, Sha256Hash nHash, boolean fUseFilter) {
        if (pfrom == null) {
            return;
        }

        log.info("gobject--CGovernanceObject::RequestGovernanceObject -- hash = {} (peer={})", nHash.toString(), pfrom.hashCode());

        if (pfrom.getVersionMessage().clientVersion < GOVERNANCE_FILTER_PROTO_VERSION) {
            pfrom.sendMessage(new GovernanceSyncMessage(params, nHash));
            return;
        }
        BloomFilter filter = null;

        int nVoteCount = 0;
        if (fUseFilter) {
            lock.lock();
            try {
                GovernanceObject pObj = findGovernanceObject(nHash);

                if (pObj != null) {
                    filter = new BloomFilter(params.getGovernanceFilterElements(), GOVERNANCE_FILTER_FP_RATE, new Random().nextInt(999999), BloomFilter.BloomUpdate.UPDATE_ALL);
                    ArrayList<GovernanceVote> vecVotes = pObj.getVoteFile().getVotes();
                    nVoteCount = vecVotes.size();
                    for (int i = 0; i < vecVotes.size(); ++i) {
                        filter.insert(vecVotes.get(i).getHash().getReversedBytes());
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        log.info("gobject--CGovernanceManager::RequestGovernanceObject -- nHash {} nVoteCount {} peer={}", nHash.toString(), nVoteCount, pfrom.hashCode());
        pfrom.sendMessage(fUseFilter ? new GovernanceSyncMessage(params, nHash, filter) :
                new GovernanceSyncMessage(params, nHash));
    }

    public void checkPostponedObjects() {
        if (!context.masternodeSync.isSynced()) {
            return;
        }

        //LOCK2(cs_main, cs);
        lock.lock();
        try {

            // Check postponed proposals
            Iterator<Map.Entry<Sha256Hash, GovernanceObject>> it = mapPostponedObjects.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Sha256Hash, GovernanceObject> entry = it.next();
                final Sha256Hash nHash = entry.getKey();
                GovernanceObject govobj = entry.getValue();

                assert govobj.getObjectType() != GOVERNANCE_OBJECT_WATCHDOG && govobj.getObjectType() != GOVERNANCE_OBJECT_TRIGGER;

                Validity validity = new Validity();
                if (govobj.isCollateralValid(validity)) {
                    if (govobj.isValidLocally(validity, false)) {
                        addGovernanceObject(govobj, null);
                    } else {
                        log.info("CGovernanceManager::CheckPostponedObjects -- {} invalid", nHash.toString());
                    }

                } else if (validity.fMissingConfirmations) {
                    // wait for more confirmations
                    continue;
                }

                // remove processed or invalid object from the queue
                it.remove();
            }


            // Perform additional relays for triggers/watchdogs
            long nNow = Utils.currentTimeSeconds();
            long nSuperblockCycleSeconds = (long)params.getSuperblockCycle() * params.TARGET_SPACING;

            Iterator<Sha256Hash> it2 = setAdditionalRelayObjects.iterator();
            while (it.hasNext()) {

                Sha256Hash hash = it2.next();
                GovernanceObject govobj = mapObjects.get(hash);
                if (govobj != null) {

                    long nTimestamp = govobj.getCreationTime();

                    boolean fValid = (nTimestamp <= nNow + MAX_TIME_FUTURE_DEVIATION) && (nTimestamp >= nNow - 2 * nSuperblockCycleSeconds);
                    boolean fReady = (nTimestamp <= nNow + MAX_TIME_FUTURE_DEVIATION - RELIABLE_PROPAGATION_TIME);

                    if (fValid) {
                        if (fReady) {
                            log.info("CGovernanceManager::CheckPostponedObjects -- additional relay: hash = {}", govobj.getHash().toString());
                            govobj.relay();
                        } else {
                            continue;
                        }
                    }

                } else {
                    log.info("CGovernanceManager::CheckPostponedObjects -- additional relay of unknown object: {}", hash);
                }

                it.remove();
            }
        } finally {
            lock.unlock();
        }
    }

    public int requestGovernanceObjectVotes(Peer pnode) {
        if (pnode.getVersionMessage().clientVersion < MIN_GOVERNANCE_PEER_PROTO_VERSION) {
            return -3;
        }
        return requestGovernanceObjectVotes();
    }
    public int requestGovernanceObjectVotes() {
        //C++ TO JAVA CONVERTER NOTE: This static local variable declaration (not allowed in Java) has been moved just prior to the method:
        //	static ClassicMap<uint256, ClassicMap<CService, long>> mapAskedRecently;
        HashMap<Sha256Hash, HashMap<InetAddress, Long>> mapAskedRecently = new HashMap<Sha256Hash, HashMap<InetAddress, Long>>();

        long nNow = Utils.currentTimeSeconds();
        int nTimeout = 60 * 60;
        int nPeersPerHashMax = 3;

        ArrayList<GovernanceObject> vpGovObjsTmp = new ArrayList<GovernanceObject>();
        ArrayList<GovernanceObject> vpGovObjsTriggersTmp = new ArrayList<GovernanceObject>();

        // This should help us to get some idea about an impact this can bring once deployed on mainnet.
        // Testnet is ~40 times smaller in masternode count, but only ~1000 masternodes usually vote,
        // so 1 obj on mainnet == ~10 objs or ~1000 votes on testnet. However we want to test a higher
        // number of votes to make sure it's robust enough, so aim at 2000 votes per masternode per request.
        // On mainnet nMaxObjRequestsPerNode is always set to 1.
        int nMaxObjRequestsPerNode = 1;
        int nProjectedVotes = 2000;
        if (params.getId() != NetworkParameters.ID_MAINNET) {
            nMaxObjRequestsPerNode = Math.max(1, (int)(nProjectedVotes / Math.max(1, context.masternodeListManager.getListAtChainTip().size())));
        }


        //LOCK2(cs_main, cs);
        lock.lock();
        try {

            if (mapObjects.isEmpty()) {
                return -2;
            }

            for (Map.Entry<Sha256Hash, GovernanceObject> it : mapObjects.entrySet()) {
                if (mapAskedRecently.containsKey(it.getKey())) {
                    Iterator<Map.Entry<InetAddress, Long>> it1 = mapAskedRecently.get(it.getKey()).entrySet().iterator();
                    while (it1.hasNext()) {
                        if (it1.next().getValue() < nNow) {
                            it1.remove();
                            //mapAskedRecently.get(it.getKey()).remove(it1++);
                        } else {
                        }
                    }
                    if (mapAskedRecently.get(it.getKey()).size() >= nPeersPerHashMax) {
                        continue;
                    }
                }
                if (it.getValue().getObjectType() == GOVERNANCE_OBJECT_TRIGGER) {
                    vpGovObjsTriggersTmp.add((it.getValue()));
                } else {
                    vpGovObjsTmp.add((it.getValue()));
                }
            }
        } finally {
            lock.unlock();
        }


        log.info("gobject--CGovernanceManager::RequestGovernanceObjectVotes -- start: vpGovObjsTriggersTmp {} vpGovObjsTmp {} mapAskedRecently {}", vpGovObjsTriggersTmp.size(), vpGovObjsTmp.size(), mapAskedRecently.size());

        Random insecureRand = new Random();
        // shuffle pointers
        Collections.shuffle(vpGovObjsTriggersTmp, insecureRand);
        Collections.shuffle(vpGovObjsTmp, insecureRand);

        for (int i = 0; i < nMaxObjRequestsPerNode; ++i) {
            Sha256Hash nHashGovobj = Sha256Hash.ZERO_HASH;

            // ask for triggers first
            if (vpGovObjsTriggersTmp.size() > 0) {
                nHashGovobj = vpGovObjsTriggersTmp.get(vpGovObjsTriggersTmp.size() - 1).getHash();
            } else {
                if (vpGovObjsTmp.isEmpty()) {
                    break;
                }
                nHashGovobj = vpGovObjsTmp.get(vpGovObjsTmp.size() - 1).getHash();
            }
            boolean fAsked = false;
            for (Peer pnode : context.peerGroup.getConnectedPeers()) {
                // Only use regular peers, don't try to ask from outbound "masternode" connections -
                // they stay connected for a short period of time and it's possible that we won't get everything we should.
                // Only use outbound connections - inbound connection could be a "masternode" connection
                // initiated from another node, so skip it too.
                if (pnode.isMasternode()) {
                    continue;
                }
                // only use up to date peers
                if (pnode.getVersionMessage().clientVersion < MIN_GOVERNANCE_PEER_PROTO_VERSION) {
                    continue;
                }
                // stop early to prevent setAskFor overflow
                int nProjectedSize = pnode.setAskFor.size() + nProjectedVotes;
                if (nProjectedSize > Peer.SETASKFOR_MAX_SZ / 2) {
                    continue;
                }
                // to early to ask the same node
                HashMap<InetAddress, Long> map = mapAskedRecently.get(nHashGovobj);
                if (map != null && map.containsKey(pnode.getAddress().getAddr())) {
                    continue;
                }

                requestGovernanceObject(pnode, nHashGovobj, true);
                if(map == null) {
                    map = new HashMap<InetAddress, Long>();
                }
                map.put(pnode.getAddress().getAddr(), nNow + nTimeout);
                mapAskedRecently.put(nHashGovobj, map);

                fAsked = true;
                // stop loop if max number of peers per obj was asked
                if (mapAskedRecently.get(nHashGovobj).size() >= nPeersPerHashMax) {
                    break;
                }
            }
            // NOTE: this should match `if` above (the one before `while`)
            if (vpGovObjsTriggersTmp.size() > 0) {
                vpGovObjsTriggersTmp.remove(vpGovObjsTriggersTmp.size() - 1);
            } else {
                vpGovObjsTmp.remove(vpGovObjsTmp.size() - 1);
            }
            if (!fAsked) {
                i--;
            }
        }
        log.info("gobject--CGovernanceManager::RequestGovernanceObjectVotes -- end: vpGovObjsTriggersTmp {} vpGovObjsTmp {} mapAskedRecently {}", vpGovObjsTriggersTmp.size(), vpGovObjsTmp.size(), mapAskedRecently.size());

        return (int)(vpGovObjsTriggersTmp.size() + vpGovObjsTmp.size());
    }

    public boolean processVoteAndRelay(GovernanceVote vote, GovernanceException exception) {
        boolean fOK = processVote(null, vote, exception);
        if(fOK) {
            vote.relay();
        }
        return fOK;
    }

    public void processGovernanceSyncMessage(Peer peer, GovernanceSyncMessage message) {
        if(peer.getVersionMessage().clientVersion < MIN_GOVERNANCE_PEER_PROTO_VERSION) {
            log.warn("gobject--MNGOVERNANCESYNC -- peer={} using obsolete version {}", peer.hashCode(), peer.getVersionMessage().clientVersion);
            peer.sendMessage(new RejectMessage(params, RejectMessage.RejectCode.OBSOLETE, Sha256Hash.ZERO_HASH, "obsolete-peer",
                    String.format("Version must be %d or greater", MIN_GOVERNANCE_PEER_PROTO_VERSION)));
            return;
        }

        // Ignore such requests until we are fully synced.
        // We could start processing this after masternode list is synced
        // but this is a heavy one so it's better to finish sync first.
        if (!context.masternodeSync.isSynced()) return;

        if(message.prop.isZero()) {
            syncAll(peer);
        } else {
            syncSingleObjAndItsVotes(peer, message.prop, message.bloomFilter);
        }
        log.info("gobject--MNGOVERNANCESYNC -- syncing governance objects to our peer at {}", peer.getAddress());
    }

    public void syncSingleObjAndItsVotes(Peer pnode, Sha256Hash nProp, BloomFilter filter) {
        // do not provide any data until our node is synced
        if (!context.masternodeSync.isSynced()) {
            return;
        }

        int nVoteCount = 0;

        // SYNC GOVERNANCE OBJECTS WITH OTHER CLIENT

        log.info("gobject--CGovernanceManager::syncSingleObjAndItsVotes -- syncing single object to peer={}, nProp = {}", pnode.hashCode(), nProp.toString());

        lock.lock();
        try {

            // single valid object and its valid votes
            GovernanceObject govobj = mapObjects.get(nProp);
            if (govobj == null) {
                log.info("gobject--CGovernanceManager:: -- no matching object for hash {}, peer={}", nProp.toString(), pnode.hashCode());
                return;
            }
            String strHash = nProp.toString();

            log.info("gobject--CGovernanceManager:: -- attempting to sync govobj: {}, peer={}", strHash, pnode.hashCode());

            if (govobj.isSetCachedDelete() || govobj.isSetExpired()) {
                log.info("CGovernanceManager:: -- not syncing deleted/expired govobj: {}, peer={}", strHash, pnode.hashCode());
                return;
            }

            // Push the govobj inventory message over to the other client
            log.info("gobject--CGovernanceManager:: -- syncing govobj: {}, peer={}\n", strHash, pnode.hashCode());
            pnode.pushInventory(new InventoryItem(InventoryItem.Type.GovernanceObject, nProp));

            //C++ TO JAVA CONVERTER TODO TASK: There is no equivalent to implicit typing in Java unless the Java 10 inferred typing option is selected:
            GovernanceObjectVoteFile fileVotes = govobj.getVoteFile();

            //C++ TO JAVA CONVERTER TODO TASK: There is no equivalent to implicit typing in Java unless the Java 10 inferred typing option is selected:
            for (GovernanceVote vote : fileVotes.getVotes()) {
                Sha256Hash nVoteHash = vote.getHash();
                if (filter.contains(nVoteHash.getReversedBytes()) || !vote.isValid(true)) {
                    continue;
                }
                pnode.pushInventory(new InventoryItem(InventoryItem.Type.GovernanceObjectVote, nVoteHash));
                ++nVoteCount;
            }

            pnode.sendMessage(new SyncStatusCount(MasternodeSync.MASTERNODE_SYNC_GOVOBJ, 1));
            pnode.sendMessage(new SyncStatusCount(MasternodeSync.MASTERNODE_SYNC_GOVOBJ_VOTE, nVoteCount));
            log.info("CGovernanceManager:: -- sent 1 object and {} votes to peer={}", nVoteCount, pnode.hashCode());
        } finally {
            lock.unlock();
        }
    }

    public void syncAll(Peer pnode) {
        // do not provide any data until our node is synced
        if (!context.masternodeSync.isSynced()) {
            return;
        }

        if (context.netFullfilledRequestManager.hasFulfilledRequest(pnode.getAddress(), "govsync")) {
            //LOCK(cs_main);
            // Asking for the whole list multiple times in a short period of time is no good
            log.info("gobject--CGovernanceManager:: -- peer already asked me for the list");
            //Misbehaving(pnode.GetId(), 20);
            return;
        }
        context.netFullfilledRequestManager.addFulfilledRequest(pnode.getAddress(), "govsync");

        int nObjCount = 0;
        int nVoteCount = 0;

        // SYNC GOVERNANCE OBJECTS WITH OTHER CLIENT

        log.info("gobject--CGovernanceManager:: -- syncing all objects to peer={}", pnode.hashCode());

        lock.lock();
        try {

            // all valid objects, no votes
            for (Map.Entry<Sha256Hash, GovernanceObject> it : mapObjects.entrySet()) {
                final GovernanceObject govobj = it.getValue();
                String strHash = it.getKey().toString();

                log.info("gobject--CGovernanceManager:: -- attempting to sync govobj: {}, peer={}\n", strHash, pnode.hashCode());

                if (govobj.isSetCachedDelete() || govobj.isSetExpired()) {
                    log.info("CGovernanceManager:: -- not syncing deleted/expired govobj: {}, peer={}\n", strHash, pnode.hashCode());
                    continue;
                }

                // Push the inventory budget proposal message over to the other client
                log.info("gobject--CGovernanceManager:: -- syncing govobj: {}, peer={}", strHash, pnode.hashCode());
                pnode.pushInventory(new InventoryItem(InventoryItem.Type.GovernanceObject, it.getKey()));
                ++nObjCount;
            }

            pnode.sendMessage(new SyncStatusCount(MasternodeSync.MASTERNODE_SYNC_GOVOBJ, nObjCount));
            pnode.sendMessage(new SyncStatusCount(MasternodeSync.MASTERNODE_SYNC_GOVOBJ_VOTE, nVoteCount));
            log.info("CGovernanceManager:: -- sent {} objects and {} votes to peer={}", nObjCount, nVoteCount, pnode.hashCode());
        } finally {
            lock.unlock();
        }
    }

    public boolean haveVoteForHash(Sha256Hash voteHash)
    {
        lock.lock();
        try {

            CacheItem<Sha256Hash, GovernanceObject> item = mapVoteToObject.get(voteHash);
            if(item == null)
                return false;
            return item.value.getVoteFile().hasVote(voteHash);
        } finally {
            lock.unlock();
        }
    }

    public GovernanceVote getVoteForHash(Sha256Hash voteHash)
    {
        lock.lock();
        try {

            CacheItem<Sha256Hash, GovernanceObject> item = mapVoteToObject.get(voteHash);
            if(item == null)
                return null;
            return item.value.getVoteFile().getVote(voteHash);
        } finally {
            lock.unlock();
        }
    }

    public ArrayList<GovernanceObject> getAllNewerThan(long nMoreThanTime) {
        lock.lock();
        try {
            ArrayList<GovernanceObject> vGovObjs = new ArrayList<GovernanceObject>();

            Iterator<Map.Entry<Sha256Hash, GovernanceObject>> it = mapObjects.entrySet().iterator();
            while (it.hasNext()) {
                // IF THIS OBJECT IS OLDER THAN TIME, CONTINUE
                Map.Entry<Sha256Hash, GovernanceObject> entry = it.next();

                if (entry.getValue().getCreationTime() < nMoreThanTime) {
                    continue;
                }

                // ADD GOVERNANCE OBJECT TO LIST

                GovernanceObject pGovObj = entry.getValue();
                vGovObjs.add(pGovObj);
            }

            return vGovObjs;
        } finally {
            lock.unlock();
        }
    }
    private final CopyOnWriteArrayList<ListenerRegistration<GovernanceVoteConfidenceEventListener>> voteConfidenceListeners
            = new CopyOnWriteArrayList<ListenerRegistration<GovernanceVoteConfidenceEventListener>>();

    /**
     * Adds an event listener object. Methods on this object are called when confidence
     * of a vote changes. Runs the listener methods in the user thread.
     */
    public void addVoteConfidenceEventListener(GovernanceVoteConfidenceEventListener listener) {
        addVoteConfidenceEventListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when confidence
     * of a vote changes. The listener is executed by the given executor.
     */
    public void addVoteConfidenceEventListener(Executor executor, GovernanceVoteConfidenceEventListener listener) {
        // This is thread safe, so we don't need to take the lock.
        voteConfidenceListeners.add(new ListenerRegistration<GovernanceVoteConfidenceEventListener>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeVoteConfidenceEventListener(GovernanceVoteConfidenceEventListener listener) {
        return ListenerRegistration.removeFromList(listener, voteConfidenceListeners);
    }

    private void queueOnTransactionConfidenceChanged(final GovernanceVote vote) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<GovernanceVoteConfidenceEventListener> registration : voteConfidenceListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onVoteConfidenceChanged(vote);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onVoteConfidenceChanged(vote);
                    }
                });
            }
        }
    }

    private transient CopyOnWriteArrayList<ListenerRegistration<GovernanceObjectAddedEventListener>> governanceObjectAddedListeners;

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addGovernanceObjectAddedListener(GovernanceObjectAddedEventListener listener) {
        addGovernanceObjectAddedListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addGovernanceObjectAddedListener(GovernanceObjectAddedEventListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        governanceObjectAddedListeners.add(new ListenerRegistration<GovernanceObjectAddedEventListener>(listener, executor));
        //keychain.addEventListener(listener, executor);
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeGovernanceObjectAddedListener(GovernanceObjectAddedEventListener listener) {
        return ListenerRegistration.removeFromList(listener, governanceObjectAddedListeners);
    }

    private void queueOnGovernanceObjectAdded(final Sha256Hash nHash, final GovernanceObject object) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<GovernanceObjectAddedEventListener> registration : governanceObjectAddedListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onGovernanceObjectAdded(nHash, object);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onGovernanceObjectAdded(nHash, object);
                    }
                });
            }
        }
    }

    @Override
    public void close() {

    }
}
