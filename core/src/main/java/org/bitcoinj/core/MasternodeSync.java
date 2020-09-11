package org.bitcoinj.core;

import org.bitcoinj.governance.GovernanceSyncMessage;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.max;
import static org.bitcoinj.core.MasternodeSync.SYNC_FLAGS.*;

/**
 * Created by Eric on 2/21/2016.
 */
public class MasternodeSync {
    private static final Logger log = LoggerFactory.getLogger(MasternodeSync.class);
    public static final int MASTERNODE_SYNC_FAILED          = -1;
    public static final int MASTERNODE_SYNC_INITIAL         = 0; // sync just started, was reset recently or still in IDB
    public static final int MASTERNODE_SYNC_WAITING         = 1; // waiting after initial to see if we can get more headers/blocks
    public static final int MASTERNODE_SYNC_LIST            = 2;
    public static final int MASTERNODE_SYNC_MNW             = 3;
    public static final int MASTERNODE_SYNC_GOVERNANCE      = 4;
    public static final int MASTERNODE_SYNC_GOVOBJ          = 10;
    public static final int MASTERNODE_SYNC_GOVOBJ_VOTE     = 11;
    public static final int MASTERNODE_SYNC_FINISHED        = 999;

    public enum SYNC_FLAGS {
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
    public static final EnumSet<VERIFY_FLAGS> VERIFY_DEFAULT_SPV = EnumSet.of(VERIFY_FLAGS.BLS_SIGNATURES,
            VERIFY_FLAGS.MNLISTDIFF_MNLIST,
            VERIFY_FLAGS.MNLISTDIFF_QUORUM,
            VERIFY_FLAGS.CHAINLOCK,
            VERIFY_FLAGS.INSTANTSENDLOCK);
    public static final EnumSet<FEATURE_FLAGS> FEATURES_SPV = EnumSet.noneOf(FEATURE_FLAGS.class);


    static final int MASTERNODE_SYNC_TICK_SECONDS    = 6;
    static final int MASTERNODE_SYNC_TIMEOUT_SECONDS = 30; // our blocks are 2.5 minutes so 30 seconds should be fine -- changed to 300 for test purposes (java)

    static final int MASTERNODE_SYNC_ENOUGH_PEERS    = 6;

    public static final int  MASTERNODE_SYNC_TIMEOUT      =    30;

    long nTimeAssetSyncStarted;
    long nTimeLastBumped;
    long nTimeLastFailure;

    public HashMap<Sha256Hash, Integer> mapSeenSyncMNB;
    public HashMap<Sha256Hash, Integer> mapSeenSyncMNW;
    public HashMap<Sha256Hash, Integer> mapSeenSyncBudget;

    long lastMasternodeList;
    long lastMasternodeWinner;
    long lastBudgetItem;
    long lastFailure;
    int nCountFailures;

    // sum of all counts
    int sumMasternodeList;
    int sumMasternodeWinner;
    int sumBudgetItemProp;
    int sumBudgetItemFin;
    // peers that reported counts
    int countMasternodeList;
    int countMasternodeWinner;
    int countBudgetItemProp;
    int countBudgetItemFin;

    // Count peers we've requested the list from
    int RequestedMasternodeAssets;
    int RequestedMasternodeAttempt;

    // Time when current masternode asset sync started
    long nAssetSyncStarted;

    StoredBlock currentBlock;

    //NetworkParameters params;
    AbstractBlockChain blockChain;
    Context context;

    public int masterNodeCountFromNetwork() { return countMasternodeList != 0 ? sumMasternodeList / countMasternodeList : 0; }

    void setBlockChain(AbstractBlockChain blockChain) { this.blockChain = blockChain; updateBlockTip(blockChain.chainHead, true);}

    public void close() {

    }

    public MasternodeSync(Context context) {
        this.context = context;
        this.mapSeenSyncBudget = new HashMap<Sha256Hash, Integer>();
        this.mapSeenSyncMNB = new HashMap<Sha256Hash, Integer>();
        this.mapSeenSyncMNW = new HashMap<Sha256Hash, Integer>();
        this.eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeSyncListener>>();
        if (context.isLiteMode() && context.allowInstantXinLiteMode()) {
            this.syncFlags = SYNC_DEFAULT_SPV;
            this.verifyFlags = VERIFY_DEFAULT_SPV;
            this.featureFlags = FEATURES_SPV;
        } else if(context.isLiteMode()) {
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

    public MasternodeSync(Context context, EnumSet<SYNC_FLAGS> syncFlags)
    {
        this.context = context;
        this.mapSeenSyncBudget = new HashMap<Sha256Hash, Integer>();
        this.mapSeenSyncMNB = new HashMap<Sha256Hash, Integer>();
        this.mapSeenSyncMNW = new HashMap<Sha256Hash, Integer>();
        this.eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeSyncListener>>();
        this.syncFlags = syncFlags == null ? EnumSet.noneOf(SYNC_FLAGS.class) : syncFlags;
        this.verifyFlags = EnumSet.noneOf(VERIFY_FLAGS.class);
        this.featureFlags = EnumSet.noneOf(FEATURE_FLAGS.class);
        if(syncFlags.contains(SYNC_FLAGS.SYNC_DMN_LIST)) {
            verifyFlags.add(VERIFY_FLAGS.MNLISTDIFF_MNLIST);
            verifyFlags.add(VERIFY_FLAGS.BLS_SIGNATURES);
        }
        if(syncFlags.contains(SYNC_FLAGS.SYNC_QUORUM_LIST))
            verifyFlags.add(VERIFY_FLAGS.MNLISTDIFF_QUORUM);
        if(syncFlags.contains(SYNC_FLAGS.SYNC_CHAINLOCKS))
            verifyFlags.add(VERIFY_FLAGS.CHAINLOCK);
        if(syncFlags.contains(SYNC_FLAGS.SYNC_INSTANTSENDLOCKS))
            verifyFlags.add(VERIFY_FLAGS.INSTANTSENDLOCK);
        reset();
    }

    public MasternodeSync(Context context, @Nullable EnumSet<SYNC_FLAGS> syncFlags, @Nullable EnumSet<VERIFY_FLAGS> verifyFlags)
    {
        this(context, syncFlags);
        this.verifyFlags = verifyFlags == null ? EnumSet.noneOf(VERIFY_FLAGS.class) : verifyFlags;
    }

    void fail()
    {
        nTimeLastFailure = Utils.currentTimeSeconds();
        RequestedMasternodeAssets = MASTERNODE_SYNC_FAILED;
    }

    void reset()
    {
        RequestedMasternodeAssets = MASTERNODE_SYNC_INITIAL;
        RequestedMasternodeAttempt = 0;
        nTimeAssetSyncStarted =  Utils.currentTimeSeconds();
        nTimeLastBumped =  Utils.currentTimeSeconds();
        nTimeLastFailure = 0;
    }

    public void BumpAssetLastTime(@Nullable String strFuncName)
    {
        if(isSynced() || isFailed()) return;
        nTimeLastBumped = Utils.currentTimeSeconds();
        if(strFuncName != null)
            log.info("mnsync--CMasternodeSync::BumpAssetLastTime -- "+ strFuncName);
    }

    public boolean isFailed() { return RequestedMasternodeAssets == MASTERNODE_SYNC_FAILED; }
    public boolean isBlockchainSynced() { return RequestedMasternodeAssets > MASTERNODE_SYNC_WAITING; }
    public boolean isMasternodeListSynced() { return RequestedMasternodeAssets > MASTERNODE_SYNC_LIST; }
    public boolean isWinnersListSynced() { return RequestedMasternodeAssets > MASTERNODE_SYNC_MNW; }
    public boolean isSynced() {
        return RequestedMasternodeAssets == MASTERNODE_SYNC_FINISHED;
    }

    /*void addedMasternodeWinner(Sha256Hash hash)
    {
        if(parmas.masternodePayments.mapMasternodePayeeVotes.count(hash)) {
            if(mapSeenSyncMNW[hash] < MASTERNODE_SYNC_THRESHOLD) {
                lastMasternodeWinner = GetTime();
                mapSeenSyncMNW[hash]++;
            }
        } else {
            lastMasternodeWinner = GetTime();
            mapSeenSyncMNW.insert(make_pair(hash, 1));
        }
    }*/

    /*void CMasternodeSync::AddedBudgetItem(uint256 hash)
    {
        if(budget.mapSeenMasternodeBudgetProposals.count(hash) || budget.mapSeenMasternodeBudgetVotes.count(hash) ||
                budget.mapSeenFinalizedBudgets.count(hash) || budget.mapSeenFinalizedBudgetVotes.count(hash)) {
            if(mapSeenSyncBudget[hash] < MASTERNODE_SYNC_THRESHOLD) {
                lastBudgetItem = GetTime();
                mapSeenSyncBudget[hash]++;
            }
        } else {
            lastBudgetItem = GetTime();
            mapSeenSyncBudget.insert(make_pair(hash, 1));
        }
    }*/

    boolean isBudgetPropEmpty()
    {
        return sumBudgetItemProp==0 && countBudgetItemProp>0;
    }

    boolean isBudgetFinEmpty()
    {
        return sumBudgetItemFin==0 && countBudgetItemFin>0;
    }

    void switchToNextAsset()
    {
        switch(RequestedMasternodeAssets)
        {
            case MASTERNODE_SYNC_FAILED:
                log.info("Can't switch to next asset from failed, should use Reset() first!");
                break;
            case(MASTERNODE_SYNC_INITIAL):
                clearFulfilledRequest();
                RequestedMasternodeAssets = MASTERNODE_SYNC_WAITING;
                log.info("CMasternodeSync::SwitchToNextAsset -- Starting "+ getAssetName());
                break;
            case(MASTERNODE_SYNC_WAITING):
                clearFulfilledRequest();
                log.info("CMasternodeSync::SwitchToNextAsset -- Completed "+getAssetName()+" in " + (Utils.currentTimeSeconds() - nTimeAssetSyncStarted));
                if(syncFlags.contains(SYNC_FLAGS.SYNC_MASTERNODE_LIST))
                    RequestedMasternodeAssets = MASTERNODE_SYNC_LIST;
                else if(syncFlags.contains(SYNC_FLAGS.SYNC_MNW))
                    RequestedMasternodeAssets = MASTERNODE_SYNC_MNW;
                else if(syncFlags.contains(SYNC_GOVERNANCE))
                    RequestedMasternodeAssets = MASTERNODE_SYNC_GOVERNANCE;
                else RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                log.info("CMasternodeSync::SwitchToNextAsset -- Starting "+ getAssetName());

                //If we are in lite mode and allowing InstantX, then only sync the sporks
                if(context.isLiteMode() && context.allowInstantXinLiteMode()) {
                    RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                }
                break;
            case(MASTERNODE_SYNC_LIST):
                log.info("CMasternodeSync::SwitchToNextAsset -- Completed "+getAssetName()+" in " + (Utils.currentTimeSeconds() - nTimeAssetSyncStarted));

                if(syncFlags.contains(SYNC_FLAGS.SYNC_MNW))
                    RequestedMasternodeAssets = MASTERNODE_SYNC_MNW;
                else if(syncFlags.contains(SYNC_GOVERNANCE))
                    RequestedMasternodeAssets = MASTERNODE_SYNC_GOVERNANCE;
                else RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                //RequestedMasternodeAssets = MASTERNODE_SYNC_GOVERNANCE;//MASTERNODE_SYNC_MNW;
                log.info("CMasternodeSync::SwitchToNextAsset -- Starting "+ getAssetName());
                break;
            case(MASTERNODE_SYNC_MNW):
                log.info("CMasternodeSync::SwitchToNextAsset -- Completed "+getAssetName()+" in " + (Utils.currentTimeSeconds() - nTimeAssetSyncStarted));
                //RequestedMasternodeAssets = MASTERNODE_SYNC_GOVERNANCE;
                if(syncFlags.contains(SYNC_GOVERNANCE))
                    RequestedMasternodeAssets = MASTERNODE_SYNC_GOVERNANCE;
                else RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                log.info("CMasternodeSync::SwitchToNextAsset -- Starting "+ getAssetName());
                break;
            case(MASTERNODE_SYNC_GOVERNANCE):
                log.info("CMasternodeSync::SwitchToNextAsset -- Completed "+getAssetName()+" in " + (Utils.currentTimeSeconds() - nTimeAssetSyncStarted));
                RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                //uiInterface.NotifyAdditionalDataSyncProgressChanged(1);
                //try to activate our masternode if possible
                //context.activeMasternode.manageState(connman);

                // TODO: Find out whether we can just use LOCK instead of:
                // TRY_LOCK(cs_vNodes, lockRecv);
                // if(lockRecv) { ... }

                //connman.ForEachNode(CConnman::AllNodes, [](CNode* pnode) {
                //netfulfilledman.AddFulfilledRequest(pnode->addr, "full-sync");
            log.info("CMasternodeSync::SwitchToNextAsset -- Sync has finished");

            break;
        }
        RequestedMasternodeAttempt = 0;
        nTimeAssetSyncStarted = Utils.currentTimeSeconds();
        BumpAssetLastTime("CMasternodeSync::SwitchToNextAsset");
        queueOnSyncStatusChanged(RequestedMasternodeAssets, 1.0);
    }

    public int getSyncStatusInt()
    { return RequestedMasternodeAssets; }

    public String getSyncStatus()
    {
        switch (RequestedMasternodeAssets) {
            case MASTERNODE_SYNC_INITIAL:       return ("Synchronizing blockchain...");
            case MASTERNODE_SYNC_WAITING:       return ("Synchronization pending...");
            case MASTERNODE_SYNC_LIST:          return ("Synchronizing masternodes...");
            case MASTERNODE_SYNC_MNW:           return ("Synchronizing masternode payments...");
            case MASTERNODE_SYNC_GOVERNANCE:    return ("Synchronizing governance objects...");
            case MASTERNODE_SYNC_FAILED:        return ("Synchronization failed");
            case MASTERNODE_SYNC_FINISHED:      return ("Synchronization finished");
            default:                            return "";
        }
    }

    public String getAssetName()
    {
        switch(RequestedMasternodeAssets)
        {
            case(MASTERNODE_SYNC_INITIAL):      return "MASTERNODE_SYNC_INITIAL";
            case(MASTERNODE_SYNC_WAITING):      return "MASTERNODE_SYNC_WAITING";
            case(MASTERNODE_SYNC_LIST):         return "MASTERNODE_SYNC_LIST";
            case(MASTERNODE_SYNC_MNW):          return "MASTERNODE_SYNC_MNW";
            case(MASTERNODE_SYNC_GOVERNANCE):   return "MASTERNODE_SYNC_GOVERNANCE";
            case(MASTERNODE_SYNC_FAILED):       return "MASTERNODE_SYNC_FAILED";
            case MASTERNODE_SYNC_FINISHED:      return "MASTERNODE_SYNC_FINISHED";
            default:                            return "UNKNOWN";

        }
    }


    void processSyncStatusCount(Peer peer, SyncStatusCount ssc)
    {
        //do not care about stats if sync process finished or failed
        if(isSynced() || isFailed()) return;


        log.info("SYNCSTATUSCOUNT -- got inventory count: nItemID="+ssc.itemId+"  nCount="+ssc.count+"  peer="+peer);
    }

    void clearFulfilledRequest()
    {
        //TODO:get the peergroup lock
        //TRY_LOCK(cs_vNodes, lockRecv);
        //if(!lockRecv) return;

        if(context.peerGroup == null)
            return;

        ReentrantLock nodeLock = context.peerGroup.getLock();

        if(!nodeLock.tryLock())
            return;

        try {
            for (Peer pnode : context.peerGroup.getConnectedPeers())
            //BOOST_FOREACH(CNode* pnode, vNodes)
            {
                pnode.clearFulfilledRequest("spork-sync");
                pnode.clearFulfilledRequest("masternode-list-sync");
                pnode.clearFulfilledRequest("masternode-payment-sync");
                pnode.clearFulfilledRequest("governance-sync");
                pnode.clearFulfilledRequest("full-sync");
            }
        } finally {
            nodeLock.unlock();
        }
    }

    static int tick = 0;
    static long nTimeLastProcess = Utils.currentTimeSeconds();
    static int nLastTick = 0;
    static int nLastVotes = 0;
    static long nTimeNoObjectsLeft = 0;

    public void processTick()
    {

        if(tick++ % MASTERNODE_SYNC_TICK_SECONDS != 0) return;

        // reset the sync process if the last call to this function was more than 60 minutes ago (client was in sleep mode)

        if(Utils.currentTimeSeconds() - nTimeLastProcess > 60*60) {
            log.info("CMasternodeSync::HasSyncFailures -- WARNING: no actions for too long, restarting sync...");
            reset();
            switchToNextAsset();
            nTimeLastProcess = Utils.currentTimeSeconds();
            return;
        }

        nTimeLastProcess = Utils.currentTimeSeconds();

        // reset sync status in case of any other sync failure
        if(isFailed()) {
            if(nTimeLastFailure + (1*60) < Utils.currentTimeSeconds()) { // 1 minute cooldown after failed sync
                log.info("CMasternodeSync::HasSyncFailures -- WARNING: failed to sync, trying again...");
                reset();
                switchToNextAsset();
            }
            return;
        }

        // gradually request the rest of the votes after sync finished
        if(isSynced()) {
            context.governanceManager.requestGovernanceObjectVotes();
            return;
        }

        //queueOnSyncStatusChanged(RequestedMasternodeAssets, nSyncProgress);

        if(context.peerGroup == null)
            return;

        ReentrantLock nodeLock = context.peerGroup.getLock();

        if(!nodeLock.tryLock())
            return;

        try {

            //BOOST_FOREACH(CNode* pnode, vNodes)
            for (Peer pnode : context.peerGroup.getConnectedPeers()) {
                // Don't try to sync any data from outbound "masternode" connections -
                // they are temporary and should be considered unreliable for a sync process.
                // Inbound connection this early is most likely a "masternode" connection
                // initiated from another node, so skip it too.
                //if(pnode->fMasternode || (fMasterNode && pnode->fInbound)) continue;

                // QUICK MODE (REGTEST ONLY!)
                if (context.getParams().getId().equals(NetworkParameters.ID_REGTEST)) {
                    if (RequestedMasternodeAttempt <= 2) {
                        pnode.sendMessage(new GetSporksMessage(context.getParams())); //get current network sporks
                    } else if (RequestedMasternodeAttempt < 4) {
                    } else if (RequestedMasternodeAttempt < 6) {
                        int nMnCount = context.masternodeListManager.getListAtChainTip().size();
                        pnode.sendMessage(new GetMasternodePaymentRequestSyncMessage(context.getParams(), nMnCount)); //sync payees
                        sendGovernanceSyncRequest(pnode);
                    } else {
                        RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                    }
                    RequestedMasternodeAttempt++;
                    return;
                }

                // NORMAL NETWORK MODE - TESTNET/MAINNET
                if (pnode.hasFulfilledRequest("full-sync")) {
                    // We already fully synced from this node recently,
                    // disconnect to free this connection slot for another peer.
                    //pnode->fDisconnect = true;
                    //log.info("CMasternodeSync::ProcessTick -- disconnecting from recently synced peer " + pnode.getAddress());
                    continue;
                }

                // SPORK : ALWAYS ASK FOR SPORKS AS WE SYNC (we skip this mode now)
                if (!pnode.hasFulfilledRequest("spork-sync")) {
                    pnode.fulfilledRequest("spork-sync");

                    pnode.sendMessage(new GetSporksMessage(context.getParams())); //get current network sporks
                }

                // INITIAL TIMEOUT

                if (RequestedMasternodeAssets == MASTERNODE_SYNC_WAITING) {
                    if (Utils.currentTimeSeconds() - nTimeLastBumped > MASTERNODE_SYNC_TIMEOUT_SECONDS) {
                        // At this point we know that:
                        // a) there are peers (because we are looping on at least one of them);
                        // b) we waited for at least MASTERNODE_SYNC_TIMEOUT_SECONDS since we reached
                        //    the headers tip the last time (i.e. since we switched from
                        //     MASTERNODE_SYNC_INITIAL to MASTERNODE_SYNC_WAITING and bumped time);
                        // c) there were no blocks (UpdatedBlockTip, NotifyHeaderTip) or headers (AcceptedBlockHeader)
                        //    for at least MASTERNODE_SYNC_TIMEOUT_SECONDS.
                        // We must be at the tip already, let's move to the next asset.
                        switchToNextAsset();
                    }
                }
                // MNLIST : SYNC MASTERNODE LIST FROM OTHER CONNECTED CLIENTS

                if (RequestedMasternodeAssets == MASTERNODE_SYNC_LIST) {
                    log.info("masternode--CMasternodeSync::ProcessTick -- nTick " + tick +
                            "nRequestedMasternodeAssets " + RequestedMasternodeAssets +
                            " nTimeLastBumped " + nTimeLastBumped +
                            " GetTime() " + Utils.currentTimeSeconds() +
                            " diff " + (Utils.currentTimeSeconds() - nTimeLastBumped));

                    if (Utils.currentTimeSeconds() - nTimeLastBumped > MASTERNODE_SYNC_TIMEOUT_SECONDS) {
                        log.info("CMasternodeSync::ProcessTick -- nTick "+tick+" nRequestedMasternodeAssets "+RequestedMasternodeAssets+" -- timeout");
                        if (RequestedMasternodeAttempt == 0) {
                            log.info("CMasternodeSync::ProcessTick -- ERROR: failed to sync %s\n", getAssetName());
                            // there is no way we can continue without masternode list, fail here and try later
                            fail();
                            return;
                        }
                        switchToNextAsset();
                        return;
                    }

                    // only request once from each peer
                    if (pnode.hasFulfilledRequest("masternode-list-sync")) continue;
                    pnode.fulfilledRequest("masternode-list-sync");

                    if (pnode.getVersionMessage().clientVersion < context.masternodePayments.getMinMasternodePaymentsProto())
                        continue;

                    RequestedMasternodeAttempt++;

                    return; //this will cause each peer to get one request each six seconds for the various assets we need

                }

                // MNW : SYNC MASTERNODE WINNERS FROM OTHER CONNECTED CLIENTS

                if (RequestedMasternodeAssets == MASTERNODE_SYNC_MNW) {
                    log.info("mnpayments--CMasternodeSync::ProcessTick -- nTick " + tick +
                            "nRequestedMasternodeAssets " + RequestedMasternodeAssets +
                            " nTimeLastBumped " + nTimeLastBumped +
                            " GetTime() %lld" + Utils.currentTimeSeconds() +
                            " diff \n" + (Utils.currentTimeSeconds() - nTimeLastBumped));

                    // check for timeout first
                    // This might take a lot longer than MASTERNODE_SYNC_TIMEOUT_SECONDS due to new blocks,
                    // but that should be OK and it should timeout eventually.
                    if (Utils.currentTimeSeconds() - nTimeLastBumped > MASTERNODE_SYNC_TIMEOUT_SECONDS) {
                        log.info("CMasternodeSync::ProcessTick -- nTick " + tick + " nRequestedMasternodeAssets " + RequestedMasternodeAssets + " -- timeout");
                        if (RequestedMasternodeAttempt == 0) {
                            log.info("CMasternodeSync::ProcessTick -- ERROR: failed to sync " + getAssetName());
                            // probably not a good idea to proceed without winner list
                            fail();
                            return;
                        }
                        switchToNextAsset();
                        return;
                    }

                    // check for data
                    // if mnpayments already has enough blocks and votes, switch to the next asset
                    // try to fetch data from at least two peers though
                    if (RequestedMasternodeAttempt > 1 && context.masternodePayments.isEnoughData()) {
                        log.info("CMasternodeSync::ProcessTick -- nTick " + tick + " nRequestedMasternodeAssets " + RequestedMasternodeAssets + " -- found enough data");
                        switchToNextAsset();
                        return;
                    }

                    // only request once from each peer
                    if (pnode.hasFulfilledRequest("masternode-payment-sync")) continue;
                    pnode.fulfilledRequest("masternode-payment-sync");

                    if (pnode.getVersionMessage().clientVersion < context.masternodePayments.getMinMasternodePaymentsProto())
                        continue;
                    RequestedMasternodeAttempt++;

                    // ask node for all payment votes it has (new nodes will only return votes for future payments)
                    //connman.PushMessage(pnode, NetMsgType::MASTERNODEPAYMENTSYNC, mnpayments.GetStorageLimit());
                    pnode.sendMessage(new GetMasternodePaymentRequestSyncMessage(context.getParams()));
                    // ask node for missing pieces only (old nodes will not be asked)
                    //mnpayments.RequestLowDataPaymentBlocks(pnode, connman);

                    return; //this will cause each peer to get one request each six seconds for the various assets we need                }

                }
                    // GOVOBJ : SYNC GOVERNANCE ITEMS FROM OUR PEERS
                    if (RequestedMasternodeAssets == MASTERNODE_SYNC_GOVERNANCE) {

                        log.info("gobject--CMasternodeSync::ProcessTick -- nTick " + tick +
                                "nRequestedMasternodeAssets " + RequestedMasternodeAssets +
                                " nTimeLastBumped " + nTimeLastBumped +
                                " GetTime() {}" + Utils.currentTimeSeconds() +
                                " diff" + (Utils.currentTimeSeconds() - nTimeLastBumped));

                        // check for timeout first
                        if (Utils.currentTimeSeconds() - nTimeLastBumped > MASTERNODE_SYNC_TIMEOUT_SECONDS) {
                            log.info("CMasternodeSync::ProcessTick -- nTick "+tick+" nRequestedMasternodeAssets "+RequestedMasternodeAssets+" -- timeout");
                            if (RequestedMasternodeAttempt == 0) {
                                log.info("CMasternodeSync::ProcessTick -- WARNING: failed to sync " + getAssetName());
                                // it's kind of ok to skip this for now, hopefully we'll catch up later?
                            }
                            switchToNextAsset();
                            return;
                        }

                        // only request obj sync once from each peer, then request votes on per-obj basis
                        if (pnode.hasFulfilledRequest("governance-sync")) {
                            int nObjsLeftToAsk = context.governanceManager.requestGovernanceObjectVotes(pnode);
                            // check for data
                            if (nObjsLeftToAsk == 0) {

                                if (nTimeNoObjectsLeft == 0) {
                                    // asked all objects for votes for the first time
                                    nTimeNoObjectsLeft = Utils.currentTimeSeconds();
                                }
                                // make sure the condition below is checked only once per tick
                                if (nLastTick == tick) continue;
                                if (Utils.currentTimeSeconds() - nTimeNoObjectsLeft > MASTERNODE_SYNC_TIMEOUT_SECONDS &&
                                        0 /*governance.GetVoteCount()*/ - nLastVotes < max((int) (0.0001 * nLastVotes), MASTERNODE_SYNC_TICK_SECONDS)
                                        ) {
                                    // We already asked for all objects, waited for MASTERNODE_SYNC_TIMEOUT_SECONDS
                                    // after that and less then 0.01% or MASTERNODE_SYNC_TICK_SECONDS
                                    // (i.e. 1 per second) votes were recieved during the last tick.
                                    // We can be pretty sure that we are done syncing.
                                    log.info("CMasternodeSync::ProcessTick -- nTick "+tick+" nRequestedMasternodeAssets "+RequestedMasternodeAssets+" -- asked for all objects, nothing to do");
                                    // reset nTimeNoObjectsLeft to be able to use the same condition on resync
                                    nTimeNoObjectsLeft = 0;
                                    switchToNextAsset();
                                    return;
                                }
                                nLastTick = tick;
                                nLastVotes = 0; //governance.GetVoteCount();
                            }
                            continue;
                        }
                        pnode.fulfilledRequest("governance-sync");

                        if (pnode.getVersionMessage().clientVersion < 70208/*MIN_GOVERNANCE_PEER_PROTO_VERSION*/)
                            continue;
                        RequestedMasternodeAttempt++;

                        sendGovernanceSyncRequest(pnode);


                        return; //this will cause each peer to get one request each six seconds for the various assets we need                  }

                    }


            }
        } finally {
            nodeLock.unlock();
        }
    }

    void sendGovernanceSyncRequest(Peer peer)
        {
            peer.sendMessage(new GovernanceSyncMessage(context.getParams()));
        }

    /******************************************************************************************************************/

    //region Event listeners
    private transient CopyOnWriteArrayList<ListenerRegistration<MasternodeSyncListener>> eventListeners;
    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addEventListener(MasternodeSyncListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
}

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addEventListener(MasternodeSyncListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        eventListeners.add(new ListenerRegistration<MasternodeSyncListener>(listener, executor));
        //keychain.addEventListener(listener, executor);
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeEventListener(MasternodeSyncListener listener) {
        //keychain.removeEventListener(listener);
        return ListenerRegistration.removeFromList(listener, eventListeners);
    }

    public void queueOnSyncStatusChanged(final int newStatus, final double syncStatus) {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<MasternodeSyncListener> registration : eventListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
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
    void acceptedBlockHeader(StoredBlock pindexNew)
    {
        log.info("mnsync--CMasternodeSync::AcceptedBlockHeader -- pindexNew->nHeight: " + pindexNew.getHeight());

        if (!isBlockchainSynced()) {
            // Postpone timeout each time new block header arrives while we are still syncing blockchain
            BumpAssetLastTime("CMasternodeSync::AcceptedBlockHeader");
        }
    }

    void notifyHeaderTip(StoredBlock pindexNew, boolean fInitialDownload)
    {
        log.info("mnsync--CMasternodeSync::NotifyHeaderTip -- pindexNew->nHeight: "+pindexNew.getHeight()+" fInitialDownload="+fInitialDownload);

        if (isFailed() || isSynced() /*|| !pindexBestHeader*/)
            return;

        if (!isBlockchainSynced()) {
            // Postpone timeout each time new block arrives while we are still syncing blockchain
            BumpAssetLastTime("CMasternodeSync::NotifyHeaderTip");
        }
    }
    //public void updateBlockTip(StoredBlock tip) {
    //    currentBlock = tip;
    //}


    static boolean fReachedBestHeader = false;
    void updateBlockTip(StoredBlock pindexNew, boolean fInitialDownload)
    {
        if(!fInitialDownload && pindexNew.getHeight() % 100 == 0)
            log.info("mnsync--CMasternodeSync::UpdatedBlockTip -- pindexNew->nHeight:  "+pindexNew.getHeight()+" fInitialDownload="+fInitialDownload);

        if (isFailed() || isSynced() /*|| !pindexBestHeader*/)
            return;

        if (!isBlockchainSynced()) {
            // Postpone timeout each time new block arrives while we are still syncing blockchain
            BumpAssetLastTime(null);
        }

        if (fInitialDownload) {
            // switched too early
            if (isBlockchainSynced()) {
                reset();
            }

            // no need to check any further while still in IBD mode
            return;
        }

        // Note: since we sync headers first, it should be ok to use this
        StoredBlock pindexBestHeader = blockChain.getChainHead();

        boolean fReachedBestHeaderNew = pindexNew.getHeader().getHash().equals(pindexBestHeader.getHeader().getHash());

        if (fReachedBestHeader && !fReachedBestHeaderNew) {
            // Switching from true to false means that we previousely stuck syncing headers for some reason,
            // probably initial timeout was not enough,
            // because there is no way we can update tip not having best header
            reset();
            fReachedBestHeader = false;
            return;
        }

        fReachedBestHeader = fReachedBestHeaderNew;

        if(pindexNew.getHeight() % 100 == 0)
            log.info("mnsync--CMasternodeSync::UpdatedBlockTip -- pindexNew->nHeight: "+pindexNew.getHeight()+" pindexBestHeader->nHeight: "+pindexBestHeader.getHeight()+" fInitialDownload="+fInitialDownload+" fReachedBestHeader="+
                fReachedBestHeader);

        if (!isBlockchainSynced() && fReachedBestHeader) {
            // Reached best header while being in initial mode.
            // We must be at the tip already, let's move to the next asset.
            switchToNextAsset();
        }
    }

    public boolean hasSyncFlag(SYNC_FLAGS flag) {
        return syncFlags.contains(flag);
    }

    public boolean hasVerifyFlag(VERIFY_FLAGS flag) {
        return verifyFlags.contains(flag);
    }

    public boolean hasFeatureFlag(FEATURE_FLAGS flag) { return featureFlags.contains(flag); }

    public void addFeatureFlag(FEATURE_FLAGS flag) { featureFlags.add(flag); }
}
