package org.bitcoinj.core;

import org.bitcoinj.core.listeners.PreMessageReceivedEventListener;
import org.bitcoinj.governance.GovernanceManager;
import org.bitcoinj.governance.GovernanceSyncMessage;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.max;
import static org.bitcoinj.core.MasternodeSync.SYNC_FLAGS.*;
import static org.bitcoinj.governance.GovernanceManager.MIN_GOVERNANCE_PEER_PROTO_VERSION;
import static org.bitcoinj.utils.Threading.SAME_THREAD;

/**
 * Created by Eric on 2/21/2016.
 */
public class MasternodeSync {
    private static final Logger log = LoggerFactory.getLogger(MasternodeSync.class);
    public static final int MASTERNODE_SYNC_BLOCKCHAIN      = 1;
    public static final int MASTERNODE_SYNC_GOVERNANCE      = 4;
    public static final int MASTERNODE_SYNC_GOVOBJ          = 10;
    public static final int MASTERNODE_SYNC_GOVOBJ_VOTE     = 11;
    public static final int MASTERNODE_SYNC_FINISHED        = 999;

    public static final int MASTERNODE_SYNC_TICK_SECONDS    = 6;
    public static final int MASTERNODE_SYNC_TIMEOUT_SECONDS = 30; // our blocks are 2.5 minutes so 30 seconds should be fine
    public static final int MASTERNODE_SYNC_RESET_SECONDS   = 600; // Reset fReachedBestHeader in CMasternodeSync::Reset if UpdateBlockTip hasn't been called for this seconds


    public enum SYNC_FLAGS {
        @Deprecated
        SYNC_MASTERNODE_LIST, //obsolete
        SYNC_OWNED_MASTERNODES,
        SYNC_MNW,
        SYNC_GOVERNANCE,
        SYNC_PROPOSALS,
        SYNC_TRIGGERS,
        SYNC_GOVERNANCE_VOTES,
        SYNC_DMN_LIST,
        SYNC_QUORUM_LIST,
        SYNC_CHAINLOCKS,
        SYNC_INSTANTSENDLOCKS,
        SYNC_SPORKS,
        SYNC_HEADERS_MN_LIST_FIRST,
        SYNC_BLOCKS_AFTER_PREPROCESSING
    }

    public enum VERIFY_FLAGS {
        BLS_SIGNATURES,
        MNLISTDIFF_MNLIST,
        MNLISTDIFF_QUORUM,
        CHAINLOCK,
        INSTANTSENDLOCK
    }

    public enum FEATURE_FLAGS {
        LOG_SIGNATURES,
    }

    public Set<SYNC_FLAGS> syncFlags;
    public Set<VERIFY_FLAGS> verifyFlags;
    public Set<FEATURE_FLAGS> featureFlags;
    public static final EnumSet<SYNC_FLAGS> SYNC_ALL_OBJECTS = EnumSet.allOf(SYNC_FLAGS.class);
    public static final EnumSet<VERIFY_FLAGS> VERIFY_ALL_OBJECTS = EnumSet.allOf(VERIFY_FLAGS.class);
    public static final EnumSet<FEATURE_FLAGS> ACTIVATE_ALL_FEATURES = EnumSet.allOf(FEATURE_FLAGS.class);

    public static final EnumSet<SYNC_FLAGS> SYNC_LITE_MODE = EnumSet.of(SYNC_DMN_LIST, SYNC_SPORKS);
    public static final EnumSet<VERIFY_FLAGS> VERIFY_LITE_MODE = EnumSet.of(VERIFY_FLAGS.MNLISTDIFF_MNLIST);
    public static final EnumSet<FEATURE_FLAGS> FEATURES_LITE_MODE = EnumSet.noneOf(FEATURE_FLAGS.class);

    public static final EnumSet<SYNC_FLAGS> SYNC_DEFAULT_SPV = EnumSet.of(SYNC_MASTERNODE_LIST,
            SYNC_QUORUM_LIST, SYNC_CHAINLOCKS, SYNC_INSTANTSENDLOCKS, SYNC_SPORKS);

    public static final EnumSet<SYNC_FLAGS> SYNC_DEFAULT_SPV_HEADERS_FIRST = EnumSet.of(SYNC_MASTERNODE_LIST,
            SYNC_QUORUM_LIST, SYNC_CHAINLOCKS, SYNC_INSTANTSENDLOCKS, SYNC_SPORKS, SYNC_HEADERS_MN_LIST_FIRST);
    public static final EnumSet<VERIFY_FLAGS> VERIFY_DEFAULT_SPV = EnumSet.of(VERIFY_FLAGS.BLS_SIGNATURES,
            VERIFY_FLAGS.MNLISTDIFF_MNLIST,
            VERIFY_FLAGS.MNLISTDIFF_QUORUM,
            VERIFY_FLAGS.CHAINLOCK,
            VERIFY_FLAGS.INSTANTSENDLOCK);
    public static final EnumSet<FEATURE_FLAGS> FEATURES_SPV = EnumSet.noneOf(FEATURE_FLAGS.class);

    AtomicInteger currentAsset = new AtomicInteger(MASTERNODE_SYNC_BLOCKCHAIN);
    AtomicInteger triedPeerCount = new AtomicInteger(0);
    AtomicLong timeAssetSyncStarted = new AtomicLong(0);
    AtomicLong timeLastBumped = new AtomicLong(0);
    AtomicBoolean reachedBestHeader = new AtomicBoolean(false);
    AtomicLong timeLastUpdateBlockTip = new AtomicLong(0);

    AbstractBlockChain blockChain;
    GovernanceManager governanceManager;
    PeerGroup peerGroup;
    Context context;
    NetFullfilledRequestManager netFullfilledRequestManager;

    @Deprecated
    private boolean isLiteMode;
    @Deprecated
    private boolean allowInstantSendInLiteMode;

    public void setBlockChain(AbstractBlockChain blockChain, PeerGroup peerGroup, NetFullfilledRequestManager netFullfilledRequestManager, GovernanceManager governanceManager) {
        this.blockChain = blockChain;
        this.peerGroup = peerGroup;
        if (peerGroup != null) {
            peerGroup.addPreMessageReceivedEventListener(SAME_THREAD, preMessageReceivedEventListener);
        }
        this.netFullfilledRequestManager = netFullfilledRequestManager;
        this.governanceManager = governanceManager;
        updateBlockTip(blockChain.chainHead, true);
    }

    public void close() {
        if (peerGroup != null) {
            peerGroup.removePreMessageReceivedEventListener(preMessageReceivedEventListener);
        }
    }

    public MasternodeSync(Context context, boolean isLiteMode, boolean allowInstantSendInLiteMode) {
        this.context = context;
        this.eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeSyncListener>>();
        if (isLiteMode && allowInstantSendInLiteMode) {
            this.syncFlags = SYNC_DEFAULT_SPV;
            this.verifyFlags = VERIFY_DEFAULT_SPV;
            this.featureFlags = FEATURES_SPV;
        } else if (isLiteMode) {
            this.syncFlags = SYNC_LITE_MODE;
            this.verifyFlags = VERIFY_LITE_MODE;
            this.featureFlags = FEATURES_LITE_MODE;
            //TODO:add other flags here to get other information such as governance messsages, by default
        } else {
            this.syncFlags = SYNC_ALL_OBJECTS;
            this.verifyFlags = VERIFY_ALL_OBJECTS;
            this.featureFlags = EnumSet.noneOf(FEATURE_FLAGS.class);
        }
        reset();
    }

    public MasternodeSync(Context context, @Nullable EnumSet<SYNC_FLAGS> syncFlags)
    {
        this.context = context;
        this.syncFlags = syncFlags == null ? EnumSet.noneOf(SYNC_FLAGS.class) : syncFlags;
        this.verifyFlags = EnumSet.noneOf(VERIFY_FLAGS.class);
        this.featureFlags = EnumSet.noneOf(FEATURE_FLAGS.class);
        if (this.syncFlags.contains(SYNC_FLAGS.SYNC_DMN_LIST)) {
            verifyFlags.add(VERIFY_FLAGS.MNLISTDIFF_MNLIST);
            verifyFlags.add(VERIFY_FLAGS.BLS_SIGNATURES);
        }
        if (this.syncFlags.contains(SYNC_FLAGS.SYNC_QUORUM_LIST))
            verifyFlags.add(VERIFY_FLAGS.MNLISTDIFF_QUORUM);
        if (this.syncFlags.contains(SYNC_FLAGS.SYNC_CHAINLOCKS))
            verifyFlags.add(VERIFY_FLAGS.CHAINLOCK);
        if (this.syncFlags.contains(SYNC_FLAGS.SYNC_INSTANTSENDLOCKS))
            verifyFlags.add(VERIFY_FLAGS.INSTANTSENDLOCK);

        reset();
    }

    public MasternodeSync(Context context, @Nullable EnumSet<SYNC_FLAGS> syncFlags, @Nullable EnumSet<VERIFY_FLAGS> verifyFlags)
    {
        this(context, syncFlags);
        this.verifyFlags = verifyFlags == null ? EnumSet.noneOf(VERIFY_FLAGS.class) : verifyFlags;
    }

    void reset() {
        reset(false, true);
    }

    void reset(boolean notifyReset) {
        reset(false, notifyReset);
    }

    void reset(boolean force, boolean notifyReset)
    {
        // Avoid resetting the sync process if we just "recently" received a new block
        if (!force) {
            if (Utils.currentTimeSeconds() - timeLastUpdateBlockTip.get() < MASTERNODE_SYNC_RESET_SECONDS) {
                return;
            }
        }
        currentAsset.set(MASTERNODE_SYNC_BLOCKCHAIN);
        triedPeerCount.set(0);
        timeAssetSyncStarted.set(Utils.currentTimeSeconds());
        timeLastBumped.set(Utils.currentTimeSeconds());
        timeLastUpdateBlockTip.set(0);
        reachedBestHeader.set(false);
        if (notifyReset) {
            queueOnSyncStatusChanged(-1, 0);
        }
    }
    public void bumpAssetLastTime(@Nullable String strFuncName) {
        bumpAssetLastTime(strFuncName, true);
    }
    public void bumpAssetLastTime(@Nullable String strFuncName, boolean debug) {
        if (isSynced()) return;
        timeLastBumped.set(Utils.currentTimeSeconds());
        if (strFuncName != null && debug)
            log.info("bumpAssetLastTime -- "+ strFuncName);
    }

    public boolean isBlockchainSynced() {
        return currentAsset.get() > MASTERNODE_SYNC_BLOCKCHAIN;
    }

    public boolean isSynced() {
        return currentAsset.get() == MASTERNODE_SYNC_FINISHED;
    }

    void switchToNextAsset()
    {
        switch(currentAsset.get()) {
            case MASTERNODE_SYNC_BLOCKCHAIN:
                log.info("switchToNextAsset -- Completed {} in {}",
                        getAssetName(), Utils.currentTimeSeconds() - timeAssetSyncStarted.get());

                if (syncFlags.contains(SYNC_GOVERNANCE))
                    currentAsset.set(MASTERNODE_SYNC_GOVERNANCE);
                else currentAsset.set(MASTERNODE_SYNC_FINISHED);
                log.info("switchToNextAsset -- Starting {}", getAssetName());
                break;
            case(MASTERNODE_SYNC_GOVERNANCE):
                log.info("switchToNextAsset -- Completed {} in {}",
                        getAssetName(), Utils.currentTimeSeconds() - timeAssetSyncStarted.get());
                currentAsset.set(MASTERNODE_SYNC_FINISHED);
                log.info("switchToNextAsset -- Sync has finished");
                break;
        }
        triedPeerCount.set(0);
        timeAssetSyncStarted.set(Utils.currentTimeSeconds());
        bumpAssetLastTime("switchToNextAsset");
        queueOnSyncStatusChanged(currentAsset.get(), 1.0);
    }

    public String getSyncStatus()
    {
        {
            switch (currentAsset.get()) {
                case MASTERNODE_SYNC_BLOCKCHAIN:
                    return "Synchronizing blockchain...";
                case MASTERNODE_SYNC_GOVERNANCE:
                    return "Synchronizing governance objects...";
                case MASTERNODE_SYNC_FINISHED:
                    return "Synchronization finished";
                default:
                    return "";
            }
        }
    }

    public String getAssetName() {
        switch(currentAsset.get())
        {
            case(MASTERNODE_SYNC_BLOCKCHAIN):
                return "MASTERNODE_SYNC_BLOCKCHAIN";
            case(MASTERNODE_SYNC_GOVERNANCE):
                return "MASTERNODE_SYNC_GOVERNANCE";
            case MASTERNODE_SYNC_FINISHED:
                return "MASTERNODE_SYNC_FINISHED";
            default:
                return "UNKNOWN";
        }
    }

    void processSyncStatusCount(Peer peer, SyncStatusCount ssc)
    {
        //do not care about stats if sync process finished or failed
        if (isSynced())
            return;

        log.info("SYNCSTATUSCOUNT -- got inventory count: nItemID="+ssc.itemId+"  nCount="+ssc.count+"  peer="+peer);
    }

    int tick = 0;
    long timeLastProcess = Utils.currentTimeSeconds();
    int lastTick = 0;
    int lastVotes = 0;
    long timeNoObjectsLeft = 0;
    final long syncStart = Utils.currentTimeMillis();
    final String allow = String.format("allow-sync-%d", syncStart);

    public void processTick()
    {
        tick++;
        // reset the sync process if the last call to this function was more than 60 minutes ago (client was in sleep mode)
        if (Utils.currentTimeSeconds() - timeLastProcess > 60*60) {
            log.info("processTick -- WARNING: no actions for too long, restarting sync...");
            reset(true);
            timeLastProcess = Utils.currentTimeSeconds();
            return;
        }

        if (Utils.currentTimeSeconds() - timeLastProcess < MASTERNODE_SYNC_TICK_SECONDS) {
            // to early, nothing to do here
            return;
        }

        timeLastProcess = Utils.currentTimeSeconds();

        // gradually request the rest of the votes after sync finished
        if (isSynced()) {
            governanceManager.requestGovernanceObjectVotes();
            return;
        }

        if (peerGroup == null)
            return;

        ReentrantLock nodeLock = peerGroup.getLock();

        if (!nodeLock.tryLock())
            return;

        try {
            double nSyncProgress = (double) (triedPeerCount.get() + (currentAsset.get() - 1) * 8) / (8 * 4);
            log.info("processTick -- tick {} currentAsset {} triedPeerCount {} syncProgress {}",
                    tick, getAssetName(), triedPeerCount, nSyncProgress);

            List<Peer> nodesCopy = peerGroup.getConnectedPeers();
            for (Peer peer : nodesCopy) {

                // QUICK MODE (REGTEST ONLY!)
                if (context.getParams().getId().equals(NetworkParameters.ID_REGTEST)) {
                    if (currentAsset.get() <= MASTERNODE_SYNC_BLOCKCHAIN) {
                        peer.sendMessage(new GetSporksMessage(context.getParams())); //get current network sporks
                        switchToNextAsset();
                    } else if (currentAsset.get() == MASTERNODE_SYNC_GOVERNANCE) {
                        sendGovernanceSyncRequest(peer);
                        switchToNextAsset();
                    }
                    return;
                }

                // NORMAL NETWORK MODE - TESTNET/MAINNET
                // check original source (dash:src/masternodesync/sync.cpp) for another step
                // that should be used if we connect to noban or manual nodes

                if (netFullfilledRequestManager.hasFulfilledRequest(peer.getAddress(), allow)) {
                    // We already fully synced from this node recently,
                    // disconnect to free this connection slot for another peer.
                    log.info("processTick -- we should disconnect from recently synced peer " + peer.getAddress());
                    continue;
                }

                // SPORK : ALWAYS ASK FOR SPORKS AS WE SYNC (we skip this mode now)
                if (!netFullfilledRequestManager.hasFulfilledRequest(peer.getAddress(), "spork-sync")) {
                    netFullfilledRequestManager.addFulfilledRequest(peer.getAddress(), "spork-sync");
                    peer.sendMessage(new GetSporksMessage(context.getParams())); //get current network sporks
                }

                if (currentAsset.get() == MASTERNODE_SYNC_BLOCKCHAIN) {
                    long timeSyncTimeout = nodesCopy.size() > 3 ? MASTERNODE_SYNC_TICK_SECONDS : MASTERNODE_SYNC_TIMEOUT_SECONDS;
                    if (reachedBestHeader.get() && (Utils.currentTimeSeconds() - timeLastBumped.get() > timeSyncTimeout)) {
                        // At this point we know that:
                        // a) there are peers (because we are looping on at least one of them);
                        // b) we waited for at least MASTERNODE_SYNC_TICK_SECONDS/MASTERNODE_SYNC_TIMEOUT_SECONDS
                        //    (depending on the number of connected peers) since we reached the headers tip the last
                        //    time (i.e. since fReachedBestHeader has been set to true);
                        // c) there were no blocks (UpdatedBlockTip, NotifyHeaderTip) or headers (AcceptedBlockHeader)
                        //    for at least MASTERNODE_SYNC_TICK_SECONDS/MASTERNODE_SYNC_TIMEOUT_SECONDS (depending on
                        //    the number of connected peers).
                        // We must be at the tip already, let's move to the next asset.
                        switchToNextAsset();
                        queueOnSyncStatusChanged(currentAsset.get(), nSyncProgress);
                    }
                }
                // GOVOBJ : SYNC GOVERNANCE ITEMS FROM OUR PEERS
                if (currentAsset.get() == MASTERNODE_SYNC_GOVERNANCE) {
                    if (!syncFlags.contains(SYNC_GOVERNANCE)) {
                        switchToNextAsset();
                        return;
                    }
                    log.info("processTick -- tick {} currentAsset {} timeLastBumped {} GetTime() {} diff %{}",
                            tick, getAssetName(), timeLastBumped, Utils.currentTimeSeconds(),
                            Utils.currentTimeSeconds() - timeLastBumped.get());

                    // check for timeout first
                    if (Utils.currentTimeSeconds() - timeLastBumped.get() > MASTERNODE_SYNC_TIMEOUT_SECONDS) {
                        log.info("processTick -- tick {} currentAsset {} -- timeout", tick, currentAsset);
                        if (triedPeerCount.get() == 0) {
                            log.info("processTick - WARNING: failed to sync {}", getAssetName());
                            // it's kind of ok to skip this for now, hopefully we'll catch up later?
                        }
                        switchToNextAsset();
                        return;
                    }

                    // only request obj sync once from each peer
                    if (netFullfilledRequestManager.hasFulfilledRequest(peer.getAddress(), "governance-sync")) {
                        // will request votes on per-obj basis from each node in a separate loop below
                        // to avoid deadlocks here
                        continue;
                    }
                    netFullfilledRequestManager.addFulfilledRequest(peer.getAddress(), "governance-sync");

                    if (peer.getPeerVersionMessage().protocolVersion < MIN_GOVERNANCE_PEER_PROTO_VERSION) {
                        continue;
                    }
                    triedPeerCount.incrementAndGet();

                    sendGovernanceSyncRequest(peer);
                    break;
                }
            }

            if (currentAsset.get() != MASTERNODE_SYNC_GOVERNANCE) {
                return;
            }

            // request votes on per-obj basis from each node
            for (Peer peer : nodesCopy) {
                if (!netFullfilledRequestManager.hasFulfilledRequest(peer.getAddress(), "governance-sync")) {
                    continue;
                }
                int objsLeftToAsk = governanceManager.requestGovernanceObjectVotes(peer);
                // check for data
                if (objsLeftToAsk == 0) {
                    if (timeNoObjectsLeft == 0) {
                        // asked all objects for votes for the first time
                        timeNoObjectsLeft = Utils.currentTimeSeconds();
                    }
                    // make sure the condition below is checked only once per tick
                    if (lastTick == tick) continue;
                    if (Utils.currentTimeSeconds() - timeNoObjectsLeft > MASTERNODE_SYNC_TIMEOUT_SECONDS &&
                            0 /*governance.GetVoteCount()*/ - lastVotes < max((int) (0.0001 * lastVotes), MASTERNODE_SYNC_TICK_SECONDS)
                    ) {
                        // We already asked for all objects, waited for MASTERNODE_SYNC_TIMEOUT_SECONDS
                        // after that and less then 0.01% or MASTERNODE_SYNC_TICK_SECONDS
                        // (i.e. 1 per second) votes were recieved during the last tick.
                        // We can be pretty sure that we are done syncing.
                        log.info("processTick -- tick {}  currentAsset {} -- asked for all objects, nothing to do", tick, currentAsset);
                        // reset timeNoObjectsLeft to be able to use the same condition on resync
                        timeNoObjectsLeft = 0;
                        switchToNextAsset();
                        return;
                    }
                    lastTick = tick;
                    // TODO - this 0 must have been to fix a bug
                    lastVotes = 0; //governance.GetVoteCount();
                }
            }
        } finally {
            nodeLock.unlock();
        }
    }

    void sendGovernanceSyncRequest(Peer peer) {
        peer.sendMessage(new GovernanceSyncMessage(context.getParams()));
    }

    /******************************************************************************************************************/

    //region Event listeners
    private transient CopyOnWriteArrayList<ListenerRegistration<MasternodeSyncListener>> eventListeners  =
            new CopyOnWriteArrayList<ListenerRegistration<MasternodeSyncListener>>();
    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addEventListener(MasternodeSyncListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
}

    /**
     * Adds an event listener object. Methods on this object are called when the sync status changes.
     * The listener is executed by the given executor.
     */
    public void addEventListener(MasternodeSyncListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        eventListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeEventListener(MasternodeSyncListener listener) {
        return ListenerRegistration.removeFromList(listener, eventListeners);
    }

    /**
     * Notifies all listeners of the new sync status and progress
     */
    public void queueOnSyncStatusChanged(final int newStatus, final double syncStatus) {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<MasternodeSyncListener> registration : eventListeners) {
            if (registration.executor == SAME_THREAD) {
                registration.listener.onSyncStatusChanged(newStatus, syncStatus);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onSyncStatusChanged(newStatus, syncStatus);
                    }
                });
            }
        }
    }

    void acceptedBlockHeader(StoredBlock block)
    {
        log.info("acceptedBlockHeader -- height: {}", block.getHeight());

        if (!isBlockchainSynced()) {
            // Postpone timeout each time new block header arrives while we are still syncing blockchain
            bumpAssetLastTime("acceptedBlockHeader");
        }
    }

    void notifyHeaderTip(StoredBlock blockHeader, boolean initialDownload)
    {
        log.info("notifyHeaderTip -- height: {} initialDownload={}", blockHeader.getHeight(), initialDownload);

        if (isSynced())
            return;

        if (!isBlockchainSynced()) {
            // Postpone timeout each time new block arrives while we are still syncing blockchain
            bumpAssetLastTime("notifyHeaderTip");
        }
    }

    public void updateBlockTip(StoredBlock storedBlock, boolean initialDownload)
    {
        if (!initialDownload && storedBlock.getHeight() % 100 == 0)
            log.info("updateBlockTip: height:  {} initialDownload={}", storedBlock.getHeight(), initialDownload);

        if (isSynced())
            return;

        if (!isBlockchainSynced()) {
            // Postpone timeout each time new block arrives while we are still syncing blockchain
            bumpAssetLastTime("updateBlockTip", false);
        }

        if (initialDownload) {
            // switched too early
            if (isBlockchainSynced()) {
                reset(true);
            }

            // no need to check any further while still in IBD mode
            return;
        }

        // Note: since we sync headers first, it should be ok to use this
        StoredBlock bestHeader = blockChain.getChainHead();

        boolean reachedBestHeaderNew = storedBlock.getHeader().getHash().equals(bestHeader.getHeader().getHash());

        if (reachedBestHeader.get() && !reachedBestHeaderNew) {
            // Switching from true to false means that we previousely stuck syncing headers for some reason,
            // probably initial timeout was not enough,
            // because there is no way we can update tip not having best header
            reset();
        }

        reachedBestHeader.set(reachedBestHeaderNew);

        if (storedBlock.getHeight() % 100 == 0)
            log.info("updatedBlockTip -- height: {} chain height: {} initialDownload={} reachedBestHeader={}",
                storedBlock.getHeight(), bestHeader.getHeight(), initialDownload, reachedBestHeader);

    }

    public void doMaintenance() {
        processTick();
    }

    public void addSyncFlag(SYNC_FLAGS flag) {
        syncFlags.add(flag);
    }

    public boolean hasSyncFlag(SYNC_FLAGS flag) {
        return syncFlags.contains(flag);
    }

    public boolean hasVerifyFlag(VERIFY_FLAGS flag) {
        return verifyFlags.contains(flag);
    }

    public boolean hasFeatureFlag(FEATURE_FLAGS flag) {
        return featureFlags.contains(flag);
    }

    public void addFeatureFlag(FEATURE_FLAGS flag) {
        featureFlags.add(flag);
    }

    @Override
    public String toString() {
        return "MasternodeSync{"+ getAssetName()+ "}";
    }

    PreMessageReceivedEventListener preMessageReceivedEventListener = new PreMessageReceivedEventListener() {
        @Override
        public Message onPreMessageReceived(Peer peer, Message m) {
            if (m instanceof SyncStatusCount) {
                processSyncStatusCount(peer, (SyncStatusCount)m);
                return null;
            }
            return m;
        }
    };
}
