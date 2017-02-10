package org.darkcoinj;

import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Eric on 2/8/2015.
 */
public class DarkSendPool {
    private static final Logger log = LoggerFactory.getLogger(DarkSendPool.class);
    ReentrantLock lock = Threading.lock("darksendpool");

    // pool states for mixing
    public static final int POOL_STATUS_UNKNOWN             =       0; // waiting for update
    public static final int POOL_STATUS_IDLE                 =      1; // waiting for update
    public static final int POOL_STATUS_QUEUE                 =     2; // waiting in a queue
    public static final int POOL_STATUS_ACCEPTING_ENTRIES      =    3; // accepting entries
    public static final int POOL_STATUS_FINALIZE_TRANSACTION    =   4; // master node will broadcast what it accepted
    public static final int  POOL_STATUS_SIGNING                 =   5; // check inputs/outputs, sign final tx
    public static final int  POOL_STATUS_TRANSMISSION            =   6; // transmit transaction
    public static final int  POOL_STATUS_ERROR                   =   7; // error
    public static final int  POOL_STATUS_SUCCESS                 =   8; // success

    static final int MIN_PEER_PROTO_VERSION = 70054;

    // masternode entries
    ArrayList<DarkSendEntry> entries;
    // the finalized transaction ready for signing
    Transaction finalTransaction;

    long lastTimeChanged;

    int state;
    int entriesCount;
    int lastEntryAccepted;
    int countEntriesAccepted;



    ArrayList<TransactionInput> lockedCoins;

    String lastMessage;
    boolean unitTest;

    int sessionID;
    int sessionUsers; //N Users have said they'll join
    boolean sessionFoundMasternode; //If we've found a compatible masternode
     ArrayList<Transaction> vecSessionCollateral;

    int cachedLastSuccess;
    int minBlockSpacing; //required blocks between mixes
    Transaction txCollateral;

    long lastNewBlock;

    //debugging data
    String strAutoDenomResult;

    enum ErrorMessage {
        ERR_ALREADY_HAVE,
        ERR_DENOM,
        ERR_ENTRIES_FULL,
        ERR_EXISTING_TX,
        ERR_FEES,
        ERR_INVALID_COLLATERAL,
        ERR_INVALID_INPUT,
        ERR_INVALID_SCRIPT,
        ERR_INVALID_TX,
        ERR_MAXIMUM,
        ERR_MN_LIST,
        ERR_MODE,
        ERR_NON_STANDARD_PUBKEY,
        ERR_NOT_A_MN,
        ERR_QUEUE_FULL,
        ERR_RECENT,
        ERR_SESSION,
        ERR_MISSING_TX,
        ERR_VERSION,
        MSG_NOERR,
        MSG_SUCCESS,
        MSG_ENTRIES_ADDED
    };

    // where collateral should be made out to
    public Script collateralPubKey;

    public Masternode submittedToMasternode;
    int sessionDenom; //Users must submit an denom matching this
    int cachedNumBlocks; //used for the overview screen

    Context context;

    public DarkSendPool(Context context)
    {
        this.context = context;
        /* DarkSend uses collateral addresses to trust parties entering the pool
            to behave themselves. If they don't it takes their money. */

        cachedLastSuccess = 0;
        cachedNumBlocks = Integer.MAX_VALUE; //std::numeric_limits<int>::max();
        unitTest = false;
        txCollateral = new Transaction(context.getParams());
        minBlockSpacing = 0;
        lastNewBlock = 0;

        vecSessionCollateral = new ArrayList<Transaction>();
        entries = new ArrayList<DarkSendEntry>();
        finalTransaction = new Transaction(context.getParams());

        setNull();
    }
    /*
    void InitCollateralAddress(){
        String strAddress = "";
        if(system.context.getId() == NetworkParameters.ID_MAINNET) {
            strAddress = "Xq19GqFvajRrEdDHYRKGYjTsQfpV5jyipF";
        } else {
            strAddress = "y1EZuxhhNMAUofTBEeLqGE1bJrpC2TWRNp";
        }
        SetCollateralAddress(strAddress);
    }

    void SetMinBlockSpacing(int minBlockSpacingIn){
        minBlockSpacing = minBlockSpacingIn;
    }

    bool SetCollateralAddress(std::string strAddress);
    void Reset();
    */
    static SecureRandom secureRandom = new SecureRandom();

    void setNull()
    {
        // MN side
        sessionUsers = 0;
        vecSessionCollateral.clear();

        // Client side
        entriesCount = 0;
        lastEntryAccepted = 0;
        countEntriesAccepted = 0;
        sessionFoundMasternode = false;

        // Both sides
        state = POOL_STATUS_IDLE;
        sessionID = 0;
        sessionDenom = 0;
        entries.clear();
        finalTransaction.clearInputs();
        finalTransaction.clearOutputs();
        lastTimeChanged = Utils.currentTimeMillis();

        // -- seed random number generator (used for ordering output lists)
        secureRandom.setSeed(secureRandom.generateSeed(12));
        /*unsigned int seed = 0;
        RAND_bytes((unsigned char*)&seed, sizeof(seed));
        std::srand(seed);*/
    }
    static boolean oneThread = false;
    public Runnable ThreadCheckDarkSendPool = new Runnable() {
        @Override
        public void run() {
            if(context.isLiteMode() && !context.allowInstantXinLiteMode()) return; //disable all Darksend/Masternode related functionality

            if(oneThread)
                return;
            oneThread = true;
            // Make this thread recognisable as the wallet flushing thread

            //RenameThread("dash-darksend");
            log.info("--------------------------------------\nstarting dash-darksend thread");
            int tick = 0;
            try {

                while (true) {
                    Thread.sleep(1000);
                    //LogPrintf("ThreadCheckDarkSendPool::check timeout\n");

                    // try to sync from all available nodes, one step at a time
                    context.masternodeSync.processTick();

                    if(context.isLiteMode() && context.allowInstantXinLiteMode() && context.masternodeSync.getSyncStatusInt() == MasternodeSync.MASTERNODE_SYNC_FINISHED) {
                        log.info("closing thread: " + Thread.currentThread().getName());
                        oneThread = false;
                        return; // if in LiteMode and allowing instantX and the Sporks are synced, then close this thread.
                    }

                    if (context.masternodeSync.isBlockchainSynced()) {

                        tick++;

                        // check if we should activate or ping every few minutes,
                        // start right after sync is considered to be done
                        if (tick % Masternode.MASTERNODE_MIN_MNP_SECONDS == 15)
                            context.activeMasternode.manageStatus();

                        if (tick % 60 == 0) {
                            context.masternodeManager.processMasternodeConnections();
                            context.masternodeManager.checkAndRemove();
                            context.masternodePayments.checkAndRemove();
                            context.instantSend.checkAndRemove();
                        }
                        //hashengineering added this
                        if(tick % 30 == 0) {
                            log.info(context.masternodeManager.toString());
                        }

                        //if(c % MASTERNODES_DUMP_SECONDS == 0) DumpMasternodes();

                        //TODO:  Add if necessary for other DarkSend functions
                        /*
                        darkSendPool.CheckTimeout();
                        darkSendPool.CheckForCompleteQueue();

                        if(nDoAutoNextRun == nTick) {
                            darkSendPool.DoAutomaticDenominating();
                            nDoAutoNextRun = nTick + PRIVATESEND_AUTO_TIMEOUT_MIN + GetRandInt(PRIVATESEND_AUTO_TIMEOUT_MAX - PRIVATESEND_AUTO_TIMEOUT_MIN);
                        }*/
                    }
                }
            }
            catch(InterruptedException x)
            {
                x.printStackTrace();
            }
        }
    };

    Thread backgroundThread;
    //dash
    public boolean startBackgroundProcessing()
    {
        if(backgroundThread == null)
        {
            backgroundThread = new ContextPropagatingThreadFactory("dash-privatesend").newThread(ThreadCheckDarkSendPool);
            backgroundThread.start();
            return true;
        }
        else if(backgroundThread.getState() == Thread.State.TERMINATED) {
            //if the thread was stopped, start it again
            backgroundThread = new ContextPropagatingThreadFactory("dash-privatesend").newThread(ThreadCheckDarkSendPool);
            backgroundThread.start();
        }
        return false;
    }
    public boolean isBackgroundRunning() { return backgroundThread == null ? false : backgroundThread.getState() != Thread.State.TERMINATED; }
}
