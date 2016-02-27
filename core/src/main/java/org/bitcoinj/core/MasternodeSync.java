package org.bitcoinj.core;

import java.util.HashMap;

/**
 * Created by Eric on 2/21/2016.
 */
public class MasternodeSync {

    public static int MASTERNODE_SYNC_INITIAL       =    0;
    public static int MASTERNODE_SYNC_SPORKS        =    1;
    public static int  MASTERNODE_SYNC_LIST         =     2;
    public static int  MASTERNODE_SYNC_MNW          =     3;
    public static int  MASTERNODE_SYNC_BUDGET       =     4;
    public static int  MASTERNODE_SYNC_BUDGET_PROP  =     10;
    public static int  MASTERNODE_SYNC_BUDGET_FIN   =     11;
    public static int  MASTERNODE_SYNC_FAILED       =     998;
    public static int  MASTERNODE_SYNC_FINISHED     =     999;

    public static int  MASTERNODE_SYNC_TIMEOUT      =    5;
    public static int  MASTERNODE_SYNC_THRESHOLD    =    2;
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

    NetworkParameters params;
    AbstractBlockChain blockChain;

    void setBlockChain(AbstractBlockChain blockChain) { this.blockChain = blockChain; }

    public MasternodeSync(NetworkParameters params)
    {
        this.params = params;
        this.mapSeenSyncBudget = new HashMap<Sha256Hash, Integer>();
        this.mapSeenSyncMNB = new HashMap<Sha256Hash, Integer>();
        this.mapSeenSyncMNW = new HashMap<Sha256Hash, Integer>();

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
        if(params.masternodeManager.mapSeenMasternodeBroadcast.containsKey(hash)) {
            Integer count = mapSeenSyncMNB.get(hash);
            if(count != null && count < MASTERNODE_SYNC_THRESHOLD) {
                lastMasternodeList = Utils.currentTimeSeconds();
                mapSeenSyncMNB.put(hash, mapSeenSyncMNB.get(hash)+1);
            }
        } else {
            lastMasternodeList = Utils.currentTimeSeconds();
            mapSeenSyncMNB.put(hash, 1);
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
        StoredBlock tip = blockChain.getChainHead();
        if(tip == null) return false;

        //if(pindex == NULL) return false;


        if(tip.getHeader().getTimeSeconds() + 60*60 < Utils.currentTimeSeconds())
            return false;

        fBlockchainSynced = true;

        return true;
    }
}
