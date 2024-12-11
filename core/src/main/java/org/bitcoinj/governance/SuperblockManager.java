package org.bitcoinj.governance;

import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static org.bitcoinj.governance.GovernanceVote.VoteSignal.VOTE_SIGNAL_FUNDING;

/**
 * Created by Hash Engineering on 6/6/2018.
 */
public class SuperblockManager {
    private GovernanceManager governanceManager;
    private static final Logger log = LoggerFactory.getLogger(SuperblockManager.class);

    public static boolean isSuperblockTriggered(int nBlockHeight, GovernanceManager governanceManager, GovernanceTriggerManager triggerManager) {
        log.info("gobject--CSuperblockManager::IsSuperblockTriggered -- Start nBlockHeight = {}", nBlockHeight);
        if (!Superblock.isValidBlockHeight(Context.get().getParams(), nBlockHeight)) {
            return false;
        }
        
        governanceManager.lock.lock();
        try {
            // GET ALL ACTIVE TRIGGERS
            ArrayList<Superblock> vecTriggers = triggerManager.getActiveTriggers();

            log.info("gobject--CSuperblockManager::IsSuperblockTriggered -- vecTriggers.size() = {}", vecTriggers.size());

            for (Superblock pSuperblock : vecTriggers) {
                if (pSuperblock == null) {
                    log.info("CSuperblockManager::IsSuperblockTriggered -- Non-superblock found, continuing");
                    log.info( "IsSuperblockTriggered Not a superblock, continuing ");
                    continue;
                }

                GovernanceObject pObj = pSuperblock.getGovernanceObject();

                if (pObj == null) {
                    log.info("CSuperblockManager::IsSuperblockTriggered -- pObj == NULL, continuing");
                    log.info( "IsSuperblockTriggered pObj is NULL, continuing");
                    continue;
                }

                log.info("gobject--CSuperblockManager::IsSuperblockTriggered -- data = {}", pObj.getDataAsPlainString());

                // note : 12.1 - is epoch calculation correct?

                if (nBlockHeight != pSuperblock.getBlockStart()) {
                    log.info("gobject--CSuperblockManager::IsSuperblockTriggered -- block height doesn't match nBlockHeight = {}, blockStart = {}, continuing", nBlockHeight, pSuperblock.getBlockStart());
                    continue;
                }

                // MAKE SURE THIS TRIGGER IS ACTIVE VIA FUNDING CACHE FLAG

                pObj.updateSentinelVariables();

                if (pObj.isSetCachedFunding()) {
                    log.info("gobject--CSuperblockManager::IsSuperblockTriggered -- fCacheFunding = true, returning true");
                    log.info( "IsSuperblockTriggered returning true");
                    return true;
                } else {
                    log.info("gobject--CSuperblockManager::IsSuperblockTriggered -- fCacheFunding = false, continuing");
                    log.info( "IsSuperblockTriggered No fCachedFunding, continuing");
                }
            }

            return false;
        } finally {
            governanceManager.lock.unlock();
        }
    }

    public static Superblock getBestSuperblock(int nBlockHeight, GovernanceManager governanceManager, GovernanceTriggerManager triggerManager) {
        Context context = Context.get();
        if (!Superblock.isValidBlockHeight(context.getParams(), nBlockHeight)) {
            return null;
        }


        governanceManager.lock.lock();
        try {
            Superblock superblockResult = null;
            ArrayList<Superblock> vecTriggers = triggerManager.getActiveTriggers();
            int nYesCount = 0;

            for (Superblock pSuperblock : vecTriggers) {
                if (pSuperblock == null) {
                    log.info("GetBestSuperblock Not a superblock, continuing");
                    continue;
                }

                GovernanceObject pObj = pSuperblock.getGovernanceObject();

                if (pObj == null) {
                    log.info("GetBestSuperblock pObj is NULL, continuing");
                    continue;
                }

                if (nBlockHeight != pSuperblock.getBlockStart()) {
                    log.info("GetBestSuperblock Not the target block, continuing");
                    continue;
                }

                // DO WE HAVE A NEW WINNER?

                int nTempYesCount = pObj.getAbsoluteYesCount(VOTE_SIGNAL_FUNDING);
                log.info("GetBestSuperblock nTempYesCount = " + nTempYesCount);
                if (nTempYesCount > nYesCount) {
                    nYesCount = nTempYesCount;
                    superblockResult = pSuperblock;
                    log.info("GetBestSuperblock Valid superblock found, pSuperblock set");
                }
            }

            return nYesCount > 0 ? superblockResult : null;
        } finally {
            governanceManager.lock.unlock();
        }
    }

    public static Transaction createSuperblock(int nBlockHeight, ArrayList<TransactionOutput> voutSuperblockRet, GovernanceManager governanceManager, GovernanceTriggerManager triggerManager) {
        log.info( "CSuperblockManager::CreateSuperblock Start");

        Context context = Context.get();
        governanceManager.lock.lock();
        try {

            Transaction tx = new Transaction(context.getParams());
            // GET THE BEST SUPERBLOCK FOR THIS BLOCK HEIGHT

            Superblock pSuperblock = SuperblockManager.getBestSuperblock(nBlockHeight, governanceManager, triggerManager);
            if (pSuperblock == null) {
                log.info("gobject--CSuperblockManager::CreateSuperblock -- Can't find superblock for height {}", nBlockHeight);
                log.info("CSuperblockManager::CreateSuperblock Failed to get superblock for height, returning");
                return null;
            }

            // make sure it's empty, just in case
            voutSuperblockRet.clear();

            // CONFIGURE SUPERBLOCK OUTPUTS

            // Superblock payments are appended to the end of the coinbase vout vector
            log.info("CSuperblockManager::CreateSuperblock Number payments: " + pSuperblock.countPayments());

            // TODO: How many payments can we add before things blow up?
            //       Consider at least following limits:
            //          - max coinbase tx size
            //          - max "budget" available
            for (int i = 0; i < pSuperblock.countPayments(); i++) {
                GovernancePayment payment = pSuperblock.getPayment(i);
                log.info("CSuperblockManager::CreateSuperblock i = "  + i);
                if (payment != null) {
                    log.info("CSuperblockManager::CreateSuperblock Payment found");
                    // SET COINBASE OUTPUT TO SUPERBLOCK SETTING

                    TransactionOutput txout = new TransactionOutput(context.getParams(), null, payment.nAmount, payment.script.getProgram());
                    tx.addOutput(txout);
                    voutSuperblockRet.add(txout);

                    // PRINT NICE LOG OUTPUT FOR SUPERBLOCK PAYMENT

                    Address address = payment.script.getToAddress(context.getParams());

                    // TODO: PRINT NICE N.N DASH OUTPUT

                    log.info("CSuperblockManager::CreateSuperblock Before log.info call, nAmount = " + payment.nAmount);
                    log.info("NEW Superblock : output {} (addr {}, amount {})", i, address.toString(), payment.nAmount);
                    log.info("CSuperblockManager::CreateSuperblock After log.info call");
                } else {
                    log.info("CSuperblockManager::CreateSuperblock Payment not found");
                }
            }
            log.info( "CSuperblockManager::CreateSuperblock End");
            return tx;
        } finally {
            governanceManager.lock.unlock();
        }
    }

    //C++ TO JAVA CONVERTER TODO TASK: The implementation of the following method could not be found:
//	static String getRequiredPaymentsString(int nBlockHeight);
    public static boolean isValid(Transaction txNew, int nBlockHeight, Coin blockReward, GovernanceManager governanceManager, GovernanceTriggerManager triggerManager) {
        // GET BEST SUPERBLOCK, SHOULD MATCH
        governanceManager.lock.lock();
        try {

            Superblock pSuperblock = SuperblockManager.getBestSuperblock(nBlockHeight, governanceManager, triggerManager);
            if (pSuperblock != null) {
                return pSuperblock.isValid(txNew, nBlockHeight, blockReward);
            }

            return false;
        } finally {
            governanceManager.lock.unlock();
        }
    }

    static String getRequiredPaymentsString(int nBlockHeight, GovernanceManager governanceManager, GovernanceTriggerManager triggerManager) {
        governanceManager.lock.lock();
        try {
            String ret = "Unknown";

            // GET BEST SUPERBLOCK

            Superblock pSuperblock = getBestSuperblock(nBlockHeight, governanceManager, triggerManager);
            if (pSuperblock == null) {
                log.info("gobject--CSuperblockManager::GetRequiredPaymentsString -- Can't find superblock for height {}", nBlockHeight);
                return "error";
            }

            // LOOP THROUGH SUPERBLOCK PAYMENTS, CONFIGURE OUTPUT STRING

            for (int i = 0; i < pSuperblock.countPayments(); i++) {
                GovernancePayment payment = pSuperblock.getPayment(i);
                if (payment != null) {
                    // PRINT NICE LOG OUTPUT FOR SUPERBLOCK PAYMENT

                    Address address = payment.script.getToAddress(governanceManager.getParams());

                    // RETURN NICE OUTPUT FOR CONSOLE

                    if (ret != "Unknown") {
                        ret += ", " + address;
                    } else {
                        ret = address.toString();
                    }
                }
            }

            return ret;
        } finally {
            governanceManager.lock.unlock();
        }
    }


}
