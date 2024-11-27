package org.bitcoinj.governance;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.BtcFormat;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.*;

public class Superblock extends GovernanceObject {
    private static final Logger log = LoggerFactory.getLogger(Superblock.class);

    private Sha256Hash nGovObjHash;

    private int nEpochStart;
    private int nStatus;
    private ArrayList<GovernancePayment> vecPayments;

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
                + pGovObj.getDataAsPlainString()
                + ", nObjectType = " + pGovObj.getObjectType());

        if (pGovObj.getObjectType() != GOVERNANCE_OBJECT_TRIGGER) {
            log.debug("CSuperblock Constructor pHoObj not a trigger, returning");
            throw new SuperblockException("CSuperblock: Governance Object not a trigger");
        }

        JSONObject obj = pGovObj.getJSONObject();

        // FIRST WE GET THE START EPOCH, THE DATE WHICH THE PAYMENT SHALL OCCUR
        nEpochStart = obj.getInt("event_block_height");

        // NEXT WE GET THE PAYMENT INFORMATION AND RECONSTRUCT THE PAYMENT VECTOR
        String strAddresses = obj.getString("payment_addresses");
        String strAmounts = obj.getString("payment_amounts");
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
        governanceManager.lock.lock();
        try {
            return  governanceManager.findGovernanceObject(nGovObjHash);
        } finally {
            governanceManager.lock.unlock();
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
        return vecPayments.size();
    }

    public Coin getPaymentsLimit(int nBlockHeight) {

        if (!isValidBlockHeight(nBlockHeight)) {
            return Coin.ZERO;
        }

        // min subsidy for high diff networks and vice versa
        int nBits = !params.getId().equals(NetworkParameters.ID_MAINNET) ? (int)Utils.encodeCompactBits(params.getMaxTarget()) : 1;
        // some part of all blocks issued during the cycle goes to superblock, see GetBlockSubsidy
        Coin nSuperblockPartOfSubsidy = Block.getBlockInflation(params, nBlockHeight - 1, nBits, true);
        Coin nPaymentsLimit = nSuperblockPartOfSubsidy.multiply(params.getSuperblockCycle());
        log.info("gobject--CSuperblock::GetPaymentsLimit -- Valid superblock height {}, payments max {}", nBlockHeight, nPaymentsLimit);

        return nPaymentsLimit;
    }

    private Coin parsePaymentAmount(String strAmount) throws ParseException {
        log.info("ParsePaymentAmount Start: strAmount = " + strAmount);

        Coin nAmount = Coin.ZERO;
        if (strAmount.length() == 0) {
            throw new ParseException("ParsePaymentAmount: Amount is empty", 0);
        }
        if (strAmount.length() > 20) {
            // String is much too long, the functions below impose stricter
            // requirements
            throw new ParseException("ParsePaymentAmount: Amount string too long", 21);
        }
        // Make sure the string makes sense as an amount
        // Note: No spaces allowed
        // Also note: No scientific notation
        int pos = Utils.findFirstNotOf(strAmount, "0123456789.", 0);
        if (pos != -1) {
            throw new ParseException("ParsePaymentAmount: Amount string contains invalid character", pos);
        }

        pos = strAmount.indexOf(".");
        if (pos == 0) {
            // JSON doesn't allow values to start with a decimal point
            throw new ParseException("ParsePaymentAmount: Invalid amount string, leading decimal point not allowed", pos);
        }

        // Make sure there's no more than 1 decimal point
        if ((pos != -1) && (strAmount.indexOf(".", pos + 1) != -1)) {
            throw new ParseException("ParsePaymentAmount: Invalid amount string, too many decimal points", strAmount.lastIndexOf("."));
        }

        // Note this code is taken from AmountFromValue in rpcserver.cpp
        // which is used for parsing the amounts in createrawtransaction.
        try {
            BtcFormat usFormat = BtcFormat.getSymbolInstance(Locale.US);
            nAmount = usFormat.parse(strAmount);
        } catch (ParseException x) {
            throw new ParseException("ParsePaymentAmount: ParseFixedPoint failed for string: " + strAmount, x.getErrorOffset());
        }

        if (nAmount.isGreaterThan(params.getMaxMoney())) {
            throw new ParseException("ParsePaymentAmount: Invalid amount string, value outside of valid money range", 0);
        }

        log.info("ParsePaymentAmount Returning true nAmount = " + nAmount);

        return nAmount;
    }


    public void parsePaymentSchedule(String strPaymentAddresses, String strPaymentAmounts) throws SuperblockException {
        // SPLIT UP ADDR/AMOUNT STRINGS AND PUT IN VECTORS

        ArrayList<String> vecParsed1 = Utils.split(strPaymentAddresses, "\\|");
        ArrayList<String> vecParsed2 = Utils.split(strPaymentAmounts, "\\|");

        // IF THESE DONT MATCH, SOMETHING IS WRONG

        if (vecParsed1.size() != vecParsed2.size()) {
            String message = "CSuperblock::ParsePaymentSchedule -- Mismatched payments and amounts";
            log.info(message);
            throw new SuperblockException(message);
        }

        if (vecParsed1.size() == 0) {
            String message = "CSuperblock::ParsePaymentSchedule -- Error no payments";
            log.info(message);
            throw new SuperblockException(message);
        }

        // LOOP THROUGH THE ADDRESSES/AMOUNTS AND CREATE PAYMENTS
		/*
		  ADDRESSES = [ADDR1|2|3|4|5|6]
		  AMOUNTS = [AMOUNT1|2|3|4|5|6]
		*/

        log.info("CSuperblock::ParsePaymentSchedule vecParsed1.size() = " + vecParsed1.size());

        for (int i = 0; i < (int)vecParsed1.size(); i++) {
            Address address = null;
            Coin nAmount = Coin.ZERO;
            try {
                address = Address.fromBase58(params, vecParsed1.get(i));
            } catch (AddressFormatException x) {
                String message = "CSuperblock::ParsePaymentSchedule -- Invalid Dash Address : " + vecParsed1.get(i);
                log.info(message);
                throw new SuperblockException(message);
            }

            try {
                log.info("CSuperblock::ParsePaymentSchedule i = " + i + ", vecParsed2[i] = " + vecParsed2.get(i));
                nAmount = parsePaymentAmount(vecParsed2.get(i));
                log.info("CSuperblock::ParsePaymentSchedule: amount string = " + vecParsed2.get(i) + ", nAmount = " + nAmount);
            } catch (ParseException x) {
                throw new SuperblockException(x.getMessage());
            }

            GovernancePayment payment = new GovernancePayment(address, nAmount);
            if (payment.isValid()) {
                vecPayments.add(payment);
            } else {
                vecPayments.clear();
                String message = "CSuperblock::ParsePaymentSchedule -- Invalid payment found: address = " + address + ", amount = " + nAmount;
                log.info(message);
                throw new SuperblockException(message);
            }
        }
    }


    GovernancePayment getPayment(int nPaymentIndex)
    {
        if((nPaymentIndex<0) || (nPaymentIndex >= (int)vecPayments.size())) {
            return null;
        }

        return vecPayments.get(nPaymentIndex);
    }

    Coin getPaymentsTotalAmount()
    {
        Coin nPaymentsTotalAmount = Coin.ZERO;
        int nPayments = countPayments();

        for(int i = 0; i < nPayments; i++) {
            nPaymentsTotalAmount = nPaymentsTotalAmount.add(vecPayments.get(i).nAmount);
        }

        return nPaymentsTotalAmount;
    }

    public boolean isValidBlockHeight(int nBlockHeight)
    {
        // SUPERBLOCKS CAN HAPPEN ONLY after hardfork and only ONCE PER CYCLE
        return nBlockHeight >= params.getSuperblockStartBlock() &&
                ((nBlockHeight % params.getSuperblockCycle()) == 0);
    }

    public static boolean isValidBlockHeight(NetworkParameters params, int nBlockHeight)
    {
        // SUPERBLOCKS CAN HAPPEN ONLY after hardfork and only ONCE PER CYCLE
        return nBlockHeight >= params.getSuperblockStartBlock() &&
                ((nBlockHeight % params.getSuperblockCycle()) == 0);
    }

    public static boolean isValidBudgetBlockHeight(NetworkParameters params, int blockHeight) {
        if(blockHeight < params.getBudgetPaymentsStartBlock())
            return false;
        if(blockHeight < params.getSuperblockStartBlock()) {
            //use 12.0 budge system rules
            int offset = blockHeight % params.getBudgetPaymentsCycleBlocks();
            if(blockHeight >= params.getBudgetPaymentsStartBlock() &&
                    offset < params.getBudgetPaymentsWindowBlocks())
                return true;
            else return false;
        } else {
            //use Superblock rules
            return isValidBlockHeight(params, blockHeight);
        }
    }

    public boolean isValid(Transaction txNew, int nBlockHeight, Coin blockReward) {
        // TODO : LOCK(cs);
        // No reason for a lock here now since this method only accesses data
        // internal to *this and since CSuperblock's are accessed only through
        // shared pointers there's no way our object can get deleted while this
        // code is running.
        if (!isValidBlockHeight(nBlockHeight)) {
            log.info("CSuperblock::IsValid -- ERROR: Block invalid, incorrect block height");
            return false;
        }

        String strPayeesPossible = "";

        // CONFIGURE SUPERBLOCK OUTPUTS

        int nOutputs = txNew.getOutputs().size();
        int nPayments = countPayments();
        int nMinerPayments = nOutputs - nPayments;

        log.info("gobject--CSuperblock::IsValid nOutputs = {}, nPayments = {}, strData = {}", nOutputs, nPayments, getGovernanceObject().getDataAsHexString());

        // We require an exact match (including order) between the expected
        // superblock payments and the payments actually in the block.

        if (nMinerPayments < 0) {
            // This means the block cannot have all the superblock payments
            // so it is not valid.
            // TODO: could that be that we just hit coinbase size limit?
            log.info("CSuperblock::IsValid -- ERROR: Block invalid, too few superblock payments");
            return false;
        }

        // payments should not exceed limit
        Coin nPaymentsTotalAmount = getPaymentsTotalAmount();
        Coin nPaymentsLimit = getPaymentsLimit(nBlockHeight);
        if (nPaymentsTotalAmount.compareTo(nPaymentsLimit) > 0) {
            log.info("CSuperblock::IsValid -- ERROR: Block invalid, payments limit exceeded: payments {}, limit {}", nPaymentsTotalAmount, nPaymentsLimit);
            return false;
        }

        // miner should not get more than he would usually get
        Coin nBlockValue = txNew.getOutputSum();
        if (nBlockValue.compareTo(blockReward.add(nPaymentsTotalAmount)) > 0) {
            log.info("CSuperblock::IsValid -- ERROR: Block invalid, block value limit exceeded: block {}, limit {}", nBlockValue, blockReward.add(nPaymentsTotalAmount));
            return false;
        }

        int nVoutIndex = 0;
        for (int i = 0; i < nPayments; i++) {
            GovernancePayment payment = getPayment(i);
            if (payment == null) {
                // This shouldn't happen so log a warning
                log.info("CSuperblock::IsValid -- WARNING: Failed to find payment: {} of {} total payments", i, nPayments);
                continue;
            }

            boolean fPaymentMatch = false;

            for (int j = nVoutIndex; j < nOutputs; j++) {
                // Find superblock payment
                fPaymentMatch = ((payment.script == txNew.getOutput(j).getScriptPubKey()) && (payment.nAmount.equals(txNew.getOutput(j).getValue())));

                if (fPaymentMatch) {
                    nVoutIndex = j;
                    break;
                }
            }

            if (!fPaymentMatch) {
                // Superblock payment not found!

                Address address = payment.script.getToAddress(params);
                log.info("CSuperblock::IsValid -- ERROR: Block invalid: {} payment {} to {} not found", i, payment.nAmount, address.toString());

                return false;
            }
        }

        return true;
    }


}
