package org.bitcoinj.governance;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Superblock extends GovernanceObject {
    private static final Logger log = LoggerFactory.getLogger(Superblock.class);

    private Sha256Hash nGovObjHash;

    private int nEpochStart;
    private int nStatus;
    private ArrayList<GovernancePayment> vecPayments;;

    Superblock(NetworkParameters params) {
        super(params);
        nGovObjHash = Sha256Hash.ZERO_HASH;
        nEpochStart = 0;
        nStatus = SEEN_OBJECT_UNKNOWN;
        vecPayments = new ArrayList<GovernancePayment>();
    }

    Superblock(NetworkParameters params, Sha256Hash nHash) throws SuperblockException {
        super(params);
        nGovObjHash = nHash;
        nEpochStart = 0;
        nStatus = SEEN_OBJECT_UNKNOWN;
        vecPayments = new ArrayList<GovernancePayment>();

        log.info("CSuperblock Constructor Start");

        GovernanceObject pGovObj = getGovernanceObject();

        if(pGovObj == null) {
            log.info("CSuperblock Constructor pGovObjIn is NULL, returning");
            throw new SuperblockException("CSuperblock: Failed to find Governance Object");
        }

        log.info("CSuperblock Constructor pGovObj : "
                + pGovObj.getDataAsString()
                + ", nObjectType = " + pGovObj.getObjectType());

        if (pGovObj.getObjectType() != GOVERNANCE_OBJECT_TRIGGER) {
            log.debug("CSuperblock Constructor pHoObj not a trigger, returning");
            throw new SuperblockException("CSuperblock: Governance Object not a trigger");
        }

        UniValue obj = pGovObjgetJSONObject();

        // FIRST WE GET THE START EPOCH, THE DATE WHICH THE PAYMENT SHALL OCCUR
        nEpochStart = obj["event_block_height"].get_int();

        // NEXT WE GET THE PAYMENT INFORMATION AND RECONSTRUCT THE PAYMENT VECTOR
        String strAddresses = obj["payment_addresses"].get_str();
        String strAmounts = obj["payment_amounts"].get_str();
        parsePaymentSchedule(strAddresses, strAmounts);

        log.info("gobject--CSuperblock -- nEpochStart = {}, strAddresses = {}, strAmounts = {}, vecPayments.size() = {}",
                nEpochStart, strAddresses, strAmounts, vecPayments.size());

        log.info("CSuperblock Constructor End");
    }

    public final int getStatus() {
        return nStatus;
    }
    public final void setStatus(int nStatusIn) {
        nStatus = nStatusIn;
    }

    // IS THIS TRIGGER ALREADY EXECUTED?
    public final boolean isExecuted() {
        return nStatus == SEEN_OBJECT_EXECUTED;
    }
    // TELL THE ENGINE WE EXECUTED THIS EVENT
    public final void setExecuted() {
        nStatus = SEEN_OBJECT_EXECUTED;
    }

    public final GovernanceObject getGovernanceObject() {
        context.governanceManager.lock.lock();
        try {
            return  context.governanceManager.findGovernanceObject(nGovObjHash);
        } finally {
            context.governanceManager.lock.unlock();
        }
    }

    public final int getBlockStart() {
		/* // 12.1 TRIGGER EXECUTION */
		/* // NOTE : Is this over complicated? */

		/* //int nRet = 0; */
		/* int nTipEpoch = 0; */
		/* int nTipBlock = chainActive.Tip()->nHeight+1; */

		/* // GET TIP EPOCK / BLOCK */

		/* // typically it should be more than the current time */
		/* int nDiff = nEpochStart - nTipEpoch; */
		/* int nBlockDiff = nDiff / (2.6*60); */

		/* // calculate predicted block height */
		/* int nMod = (nTipBlock + nBlockDiff) % Params().GetConsensus().nSuperblockCycle; */

		/* return (nTipBlock + nBlockDiff)-nMod; */
        return nEpochStart;
    }

    public final int countPayments() {
        return (int)vecPayments.size();
    }

}
