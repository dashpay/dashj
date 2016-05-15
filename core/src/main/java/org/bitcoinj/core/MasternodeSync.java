package org.bitcoinj.core;

import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by Eric on 2/21/2016.
 */
public class MasternodeSync {
    private static final Logger log = LoggerFactory.getLogger(MasternodeSync.class);
    public static final int MASTERNODE_SYNC_INITIAL       =    0;
    public static final int MASTERNODE_SYNC_SPORKS        =    1;
    public static final int  MASTERNODE_SYNC_LIST         =     2;
    public static final int  MASTERNODE_SYNC_MNW          =     3;
    public static final int  MASTERNODE_SYNC_BUDGET       =     4;
    public static final int  MASTERNODE_SYNC_BUDGET_PROP  =     10;
    public static final int  MASTERNODE_SYNC_BUDGET_FIN   =     11;
    public static final int  MASTERNODE_SYNC_FAILED       =     998;
    public static final int  MASTERNODE_SYNC_FINISHED     =     999;

    public static final int  MASTERNODE_SYNC_TIMEOUT      =    5;
    public static final int  MASTERNODE_SYNC_THRESHOLD    =    2;
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

    //NetworkParameters params;
    AbstractBlockChain blockChain;
    Context context;

    public int masterNodeCountFromNetwork() { return countMasternodeList != 0 ? sumMasternodeList / countMasternodeList : 0; }

    void setBlockChain(AbstractBlockChain blockChain) { this.blockChain = blockChain; }

    public MasternodeSync(Context context)
    {
        this.context = context;

        this.mapSeenSyncBudget = new HashMap<Sha256Hash, Integer>();
        this.mapSeenSyncMNB = new HashMap<Sha256Hash, Integer>();
        this.mapSeenSyncMNW = new HashMap<Sha256Hash, Integer>();

        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<MasternodeSyncListener>>();

        reset();
    }

    void reset()
    {
        lastMasternodeList = 0;
        lastMasternodeWinner = 0;
        lastBudgetItem = 0;
        mapSeenSyncMNB.clear();
        mapSeenSyncMNW.clear();
        mapSeenSyncBudget.clear();
        lastFailure = 0;
        nCountFailures = 0;
        sumMasternodeList = 0;
        sumMasternodeWinner = 0;
        sumBudgetItemProp = 0;
        sumBudgetItemFin = 0;
        countMasternodeList = 0;
        countMasternodeWinner = 0;
        countBudgetItemProp = 0;
        countBudgetItemFin = 0;
        RequestedMasternodeAssets = MASTERNODE_SYNC_INITIAL;
        RequestedMasternodeAttempt = 0;
        nAssetSyncStarted = Utils.currentTimeSeconds();//GetTime();
    }

    void addedMasternodeList(Sha256Hash hash)
    {
        if(context.masternodeManager.mapSeenMasternodeBroadcast.containsKey(hash)) {
            Integer count = mapSeenSyncMNB.get(hash);
            if(count != null && count < MASTERNODE_SYNC_THRESHOLD) {
                lastMasternodeList = Utils.currentTimeSeconds();
                mapSeenSyncMNB.put(hash, mapSeenSyncMNB.get(hash)+1);
            }
            else {
                mapSeenSyncMNB.put(hash, 1);
                lastMasternodeList = Utils.currentTimeSeconds();
            }
        } else {
            lastMasternodeList = Utils.currentTimeSeconds();
            mapSeenSyncMNB.put(hash, 1);
        }
    }

    boolean isSynced()
    {
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

    void getNextAsset()
    {
        switch(RequestedMasternodeAssets)
        {
            case(MASTERNODE_SYNC_INITIAL):
            case(MASTERNODE_SYNC_FAILED): // should never be used here actually, use Reset() instead
                clearFulfilledRequest();
                RequestedMasternodeAssets = MASTERNODE_SYNC_SPORKS;
                break;
            case(MASTERNODE_SYNC_SPORKS):
                RequestedMasternodeAssets = MASTERNODE_SYNC_LIST;

                //If we are in lite mode and allowing InstantX, then only sync the sporks
                if(context.isLiteMode() && context.allowInstantXinLiteMode())
                    RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                break;
            case(MASTERNODE_SYNC_LIST):
           /*     RequestedMasternodeAssets = MASTERNODE_SYNC_MNW;  //TODO:  Reactivate when sync needs Winners and Budget
                break;
            case(MASTERNODE_SYNC_MNW):
                RequestedMasternodeAssets = MASTERNODE_SYNC_BUDGET;
                break;
            case(MASTERNODE_SYNC_BUDGET):*/
                log.info("CMasternodeSync::GetNextAsset - Sync has finished");
                RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                break;
        }
        RequestedMasternodeAttempt = 0;
        nAssetSyncStarted = Utils.currentTimeSeconds();
        queueOnSyncStatusChanged(RequestedMasternodeAssets);
    }

    public int getSyncStatusInt()
    { return RequestedMasternodeAssets; }

    public String getSyncStatus()
    {
        switch (RequestedMasternodeAssets) {
            case MASTERNODE_SYNC_INITIAL: return ("Synchronization pending...");
            case MASTERNODE_SYNC_SPORKS: return ("Synchronizing sporks...");
            case MASTERNODE_SYNC_LIST: return ("Synchronizing masternodes...");
            case MASTERNODE_SYNC_MNW: return ("Synchronizing masternode winners...");
            case MASTERNODE_SYNC_BUDGET: return ("Synchronizing budgets...");
            case MASTERNODE_SYNC_FAILED: return ("Synchronization failed");
            case MASTERNODE_SYNC_FINISHED: return ("Synchronization finished");
        }
        return "";
    }

    void processSyncStatusCount(Peer peer, SyncStatusCount ssc)
    {
            if(RequestedMasternodeAssets >= MASTERNODE_SYNC_FINISHED) return;

            //this means we will receive no further communication
            switch(ssc.itemId)
            {
                case(MASTERNODE_SYNC_LIST):
                    if(ssc.itemId != RequestedMasternodeAssets) return;
                    sumMasternodeList += ssc.count;
                    countMasternodeList++;
                    peer.setMasternodeListCount(ssc.count);
                    break;
                case(MASTERNODE_SYNC_MNW):
                    if(ssc.itemId != RequestedMasternodeAssets) return;
                    sumMasternodeWinner += ssc.count;
                    countMasternodeWinner++;
                    break;
                case(MASTERNODE_SYNC_BUDGET_PROP):
                    if(RequestedMasternodeAssets != MASTERNODE_SYNC_BUDGET) return;
                    sumBudgetItemProp += ssc.count;
                    countBudgetItemProp++;
                    break;
                case(MASTERNODE_SYNC_BUDGET_FIN):
                    if(RequestedMasternodeAssets != MASTERNODE_SYNC_BUDGET) return;
                    sumBudgetItemFin += ssc.count;
                    countBudgetItemFin++;
                    break;
            }

            log.info("CMasternodeSync:ProcessMessage - ssc - got inventory count {} {}\n", ssc.itemId, ssc.count);
        queueOnSyncStatusChanged(getSyncStatusInt());

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
                pnode.clearFulfilledRequest("getspork");
                pnode.clearFulfilledRequest("mnsync");
                pnode.clearFulfilledRequest("mnwsync");
                pnode.clearFulfilledRequest("busync");
            }
        } finally {
            nodeLock.unlock();
        }
    }

    static boolean fBlockchainSynced = false;
    static long lastProcess = Utils.currentTimeSeconds();

    public boolean isBlockchainSynced()
    {
        // if the last call to this function was more than 60 minutes ago (client was in sleep mode) reset the sync process
        if(Utils.currentTimeSeconds() - lastProcess > 60*60) {
            reset();
            fBlockchainSynced = false;
        }
        lastProcess = Utils.currentTimeSeconds();

        if(fBlockchainSynced) return true;

        //if (fImporting || fReindex) return false;

        //TRY_LOCK(cs_main, lockMain);
        //if(!lockMain) return false;

        //CBlockIndex* pindex = chainActive.Tip();
        if(blockChain == null)
            return false;
        StoredBlock tip = blockChain.getChainHead();
        if(tip == null) return false;

        //if(pindex == NULL) return false;


        if(tip.getHeader().getTimeSeconds() + 60*60 < Utils.currentTimeSeconds())
            return false;

        fBlockchainSynced = true;

        return true;
    }
    static int tick = 0;

    public void process()
    {

        if(tick++ % MASTERNODE_SYNC_TIMEOUT != 0) return;

        if(isSynced()) {
        /*
            Resync if we lose all masternodes from sleep/wake or failure to sync originally
        */
            if(context.masternodeManager.countEnabled() == 0) {
                reset();
            } else
                return;
        }

        //try syncing again
        if(RequestedMasternodeAssets == MASTERNODE_SYNC_FAILED && lastFailure + (1*60) < Utils.currentTimeSeconds()) {
            reset();
        } else if (RequestedMasternodeAssets == MASTERNODE_SYNC_FAILED) {
            return;
        }

        if(DarkCoinSystem.fDebug) log.debug("CMasternodeSync::Process() - tick {} RequestedMasternodeAssets {}", tick, RequestedMasternodeAssets);

        if(RequestedMasternodeAssets == MASTERNODE_SYNC_INITIAL)
            getNextAsset();

        // sporks synced but blockchain is not, wait until we're almost at a recent block to continue
        if(context.getParams().getId().equals(NetworkParameters.ID_REGTEST) &&
                !isBlockchainSynced() && RequestedMasternodeAssets > MASTERNODE_SYNC_SPORKS) return;

        //TRY_LOCK(cs_vNodes, lockRecv);
        //if(!lockRecv) return;

        if(context.peerGroup == null)
            return;

        ReentrantLock nodeLock = context.peerGroup.getLock();

        if(!nodeLock.tryLock())
            return;

        try {

            //BOOST_FOREACH(CNode* pnode, vNodes)
            for (Peer pnode : context.peerGroup.getConnectedPeers()) {
                if (context.getParams().getId().equals(NetworkParameters.ID_REGTEST)) {
                    if (RequestedMasternodeAttempt <= 2) {
                        pnode.sendMessage(new GetSporksMessage(context.getParams())); //get current network sporks
                    } else if (RequestedMasternodeAttempt < 4) {
                        context.masternodeManager.dsegUpdate(pnode);
                    } else if (RequestedMasternodeAttempt < 6) {
                        int nMnCount = context.masternodeManager.countEnabled();
                        pnode.sendMessage(new GetMasternodePaymentRequestSyncMessage(context.getParams(), nMnCount)); //sync payees
                        pnode.sendMessage(new GetMasternodeVoteSyncMessage(context.getParams(), Sha256Hash.ZERO_HASH)); //sync masternode votes
                    } else {
                        RequestedMasternodeAssets = MASTERNODE_SYNC_FINISHED;
                    }
                    RequestedMasternodeAttempt++;
                    return;
                }

                //set to synced
                if (RequestedMasternodeAssets == MASTERNODE_SYNC_SPORKS) {
                    if (pnode.hasFulfilledRequest("getspork")) continue;
                    pnode.fulfilledRequest("getspork");

                    pnode.sendMessage(new GetSporksMessage(context.getParams())); //get current network sporks
                    if (RequestedMasternodeAttempt >= 2) getNextAsset();
                    RequestedMasternodeAttempt++;

                    return;
                }

                if (pnode.getPeerVersionMessage().clientVersion >= context.masternodePayments.getMinMasternodePaymentsProto()) {

                    if (RequestedMasternodeAssets == MASTERNODE_SYNC_LIST) {
                        if (DarkCoinSystem.fDebug)
                            log.debug("CMasternodeSync::Process() - lastMasternodeList {} (GetTime() - MASTERNODE_SYNC_TIMEOUT) {}", lastMasternodeList, Utils.currentTimeSeconds() - MASTERNODE_SYNC_TIMEOUT);
                        if (lastMasternodeList > 0 && lastMasternodeList < Utils.currentTimeSeconds() - MASTERNODE_SYNC_TIMEOUT * 2 && RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD) { //hasn't received a new item in the last five seconds, so we'll move to the
                            getNextAsset();
                            return;
                        }

                        if (pnode.hasFulfilledRequest("mnsync")) continue;
                        pnode.fulfilledRequest("mnsync");

                        // timeout
                        if (lastMasternodeList == 0 &&
                                (RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD * 3 || Utils.currentTimeSeconds() - nAssetSyncStarted > MASTERNODE_SYNC_TIMEOUT * 5)) {
                            if (context.sporkManager.isSporkActive(SporkManager.SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT)) {
                                log.info("CMasternodeSync::Process - ERROR - Masternode Sync has failed, will retry later");
                                RequestedMasternodeAssets = MASTERNODE_SYNC_FAILED;
                                RequestedMasternodeAttempt = 0;
                                lastFailure = Utils.currentTimeSeconds();
                                nCountFailures++;
                            } else {
                                getNextAsset();
                            }
                            return;
                        }

                        if (RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD * 3) return;

                        context.masternodeManager.dsegUpdate(pnode);
                        RequestedMasternodeAttempt++;
                        return;
                    }

                    if (RequestedMasternodeAssets == MASTERNODE_SYNC_MNW) {
                        if (lastMasternodeWinner > 0 && lastMasternodeWinner < Utils.currentTimeSeconds() - MASTERNODE_SYNC_TIMEOUT * 2 && RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD) { //hasn't received a new item in the last five seconds, so we'll move to the
                            getNextAsset();
                            return;
                        }

                        if (pnode.hasFulfilledRequest("mnwsync")) continue;
                        pnode.fulfilledRequest("mnwsync");

                        // timeout
                        if (lastMasternodeWinner == 0 &&
                                (RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD * 3 || Utils.currentTimeSeconds() - nAssetSyncStarted > MASTERNODE_SYNC_TIMEOUT * 5)) {
                            if (context.sporkManager.isSporkActive(SporkManager.SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT)) {
                                log.info("CMasternodeSync::Process - ERROR - Masternode Winner Sync has failed, will retry later");
                                RequestedMasternodeAssets = MASTERNODE_SYNC_FAILED;
                                RequestedMasternodeAttempt = 0;
                                lastFailure = Utils.currentTimeSeconds();
                                nCountFailures++;
                            } else {
                                getNextAsset();
                            }
                            return;
                        }

                        if (RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD * 3) return;

                        //CBlockIndex * pindexPrev = chainActive.Tip();
                        StoredBlock prev = blockChain.getChainHead();
                        if (prev == null) return;

                        int nMnCount = context.masternodeManager.countEnabled();
                        pnode.sendMessage(new GetMasternodePaymentRequestSyncMessage(context.getParams(), nMnCount)); //sync payees
                        RequestedMasternodeAttempt++;

                        return;
                    }
                }

                if (pnode.getPeerVersionMessage().clientVersion >= BudgetManager.MIN_BUDGET_PEER_PROTO_VERSION) {

                    if (RequestedMasternodeAssets == MASTERNODE_SYNC_BUDGET) {
                        //we'll start rejecting votes if we accidentally get set as synced too soon
                        if (lastBudgetItem > 0 && lastBudgetItem < Utils.currentTimeSeconds() - MASTERNODE_SYNC_TIMEOUT * 2 && RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD) { //hasn't received a new item in the last five seconds, so we'll move to the
                            //LogPrintf("CMasternodeSync::Process - HasNextFinalizedBudget %d nCountFailures %d IsBudgetPropEmpty %d\n", budget.HasNextFinalizedBudget(), nCountFailures, IsBudgetPropEmpty());
                            //if(budget.HasNextFinalizedBudget() || nCountFailures >= 2 || IsBudgetPropEmpty()) {
                            getNextAsset();

                            //try to activate our masternode if possible
                            context.activeMasternode.manageStatus();
                            // } else { //we've failed to sync, this state will reject the next budget block
                            //     LogPrintf("CMasternodeSync::Process - ERROR - Sync has failed, will retry later\n");
                            //     RequestedMasternodeAssets = MASTERNODE_SYNC_FAILED;
                            //     RequestedMasternodeAttempt = 0;
                            //     lastFailure = GetTime();
                            //     nCountFailures++;
                            // }
                            return;
                        }

                        // timeout
                        if (lastBudgetItem == 0 &&
                                (RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD * 3 || Utils.currentTimeSeconds() - nAssetSyncStarted > MASTERNODE_SYNC_TIMEOUT * 5)) {
                            // maybe there is no budgets at all, so just finish syncing
                            getNextAsset();
                            context.activeMasternode.manageStatus();
                            return;
                        }

                        if (pnode.hasFulfilledRequest("busync")) continue;
                        pnode.fulfilledRequest("busync");

                        if (RequestedMasternodeAttempt >= MASTERNODE_SYNC_THRESHOLD * 3) return;

                        pnode.sendMessage(new GetMasternodeVoteSyncMessage(context.getParams(), Sha256Hash.ZERO_HASH)); //sync masternode votes
                        RequestedMasternodeAttempt++;

                        return;
                    }

                }
            }
        } finally {
            nodeLock.unlock();
        }
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

    private void queueOnSyncStatusChanged(int newStatus) {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<MasternodeSyncListener> registration : eventListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onSyncStatusChanged(RequestedMasternodeAssets);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onSyncStatusChanged(RequestedMasternodeAssets);
                    }
                });
            }
        }
    }
}
