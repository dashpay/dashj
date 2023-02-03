/*
 * Copyright (c) 2022 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.coinjoin;

import com.google.common.collect.Lists;
import net.jcip.annotations.GuardedBy;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_ENTRY_MAX_SIZE;

public class CoinJoin {
    private static final Logger log = LoggerFactory.getLogger(CoinJoin.class);
    private static final List<Coin> standardDenominations = Lists.newArrayList(
            Coin.COIN.multiply(10).add(Coin.valueOf(10000)),
            Coin.COIN.add(Coin.valueOf(1000)),
            Coin.COIN.div(10).add(Coin.valueOf(100)),
            Coin.COIN.div(100).add(Coin.valueOf(10)),
            Coin.COIN.div(1000).add(Coin.valueOf(1))
    );

    private static final HashMap<Sha256Hash, CoinJoinBroadcastTx> mapDSTX = new HashMap<>();
    private static final ReentrantLock mapdstxLock = Threading.lock("mapdstx");

    public static List<Coin> getStandardDenominations() {
        return standardDenominations;
    }

    public static Coin getSmallestDenomination() {
        return standardDenominations.get(standardDenominations.size() -1);
    }

    public static boolean isDenominatedAmount(Coin nInputAmount) { return amountToDenomination(nInputAmount) > 0; }
    public static boolean isValidDenomination(int nDenom) { return denominationToAmount(nDenom).isPositive(); }


    /**
        Return a bitshifted integer representing a denomination in vecStandardDenominations
        or 0 if none was found
    */
    public static int amountToDenomination(Coin nInputAmount) {
        for (int i = 0; i < standardDenominations.size(); ++i) {
            if (nInputAmount.equals(standardDenominations.get(i))) {
                return 1 << i;
            }
        }
        return 0;
    }

    /**
    Returns:
    - one of standard denominations from vecStandardDenominations based on the provided bitshifted integer
    - 0 for non-initialized sessions (denom = 0)
    - a value below 0 if an error occurred while converting from one to another
    */
    public static Coin denominationToAmount(int denom)
    {
        if (denom == 0) {
            // not initialized
            return Coin.ZERO;
        }

        int maxDenoms = standardDenominations.size();

        if (denom >= (1 << maxDenoms) || denom < 0) {
            // out of bounds
            return Coin.valueOf(-1);
        }

        if ((denom & (denom - 1)) != 0) {
            // non-denom
            return Coin.valueOf(-2);
        }

        Coin denomAmount = Coin.valueOf(-3);

        for (int i = 0; i < maxDenoms; ++i) {
            if ((denom & (1 << i)) != 0) {
                denomAmount = standardDenominations.get(i);
                break;
            }
        }

        return denomAmount;
    }
    
    public static String denominationToString(int denom) {
        Coin denomAmount = denominationToAmount(denom);

        switch ((int)denomAmount.value) {
            case  0: return "N/A";
            case -1: return "out-of-bounds";
            case -2: return "non-denom";
            case -3: return "to-amount-error";
            default: return denomAmount.toPlainString();
        }
    }

    /// Get the minimum/maximum number of participants for the pool
    public static int getMinPoolParticipants(NetworkParameters params) {
        return params.getPoolMinParticipants();
    }
    public static int getMaxPoolParticipants(NetworkParameters params) {
        return params.getPoolMaxParticipants();
    }

    public static Coin getMaxPoolAmount() { return standardDenominations.get(0).multiply(COINJOIN_ENTRY_MAX_SIZE); }


    /// If the collateral is valid given by a client
    public static boolean isCollateralValid(Transaction txCollateral) {
        return isCollateralValid(txCollateral, true);
    }

    public static boolean isCollateralValid(Transaction txCollateral, boolean checkInputs) {
        if (txCollateral.getOutputs().isEmpty())
            return false;
        if (txCollateral.getLockTime() != 0)
            return false;

        Coin nValueIn = Coin.ZERO;
        Coin nValueOut = Coin.ZERO;

        for (TransactionOutput txout : txCollateral.getOutputs()) {
            nValueOut = nValueOut.add(txout.getValue());

            if (!ScriptPattern.isP2PKH(txout.getScriptPubKey()) && !txout.getScriptPubKey().isUnspendable()) {
                log.info("coinjoin: Invalid Script, txCollateral={}", txCollateral); /* Continued */
                return false;
            }
        }

        if (checkInputs) {

            for (TransactionInput txin : txCollateral.getInputs()) {
                Transaction tx = txin.getConnectedTransaction();
                if (tx != null) {
                    nValueIn = nValueIn.add(tx.getOutput(txin.getOutpoint().getIndex()).getValue());
                } else {
                    log.info("coinjoin: -- Unknown inputs in collateral transaction, txCollateral={}", txCollateral); /* Continued */
                    return false;
                }
            }

            //collateral transactions are required to pay out a small fee to the miners
            if (nValueIn.minus(nValueOut).isLessThan(getCollateralAmount())) {
                log.info("coinjoin:  did not include enough fees in transaction: fees: {}, txCollateral={}", nValueOut.minus(nValueIn), txCollateral); /* Continued */
                return false;
            }
        }

        log.info("coinjoin: collateral: {}", txCollateral); /* Continued */

        // the collateral tx must not have been seen on the network
        return txCollateral.getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.UNKNOWN;
    }
    public static Coin getCollateralAmount() { return getSmallestDenomination().div(10); }
    public static Coin getMaxCollateralAmount() { return getCollateralAmount().multiply(4); }


    public static boolean isCollateralAmount(Coin nInputAmount) {
        // collateral input can be anything between 1x and "max" (including both)
        return nInputAmount.isGreaterThanOrEqualTo(getCollateralAmount()) &&
                nInputAmount.isLessThanOrEqualTo(getMaxCollateralAmount());
    }

    public static long calculateAmountPriority(Coin inputAmount) {
        Coin optDenom = null;
        for (Coin denom : getStandardDenominations()) {
            if (inputAmount == denom) {
                optDenom = denom;
            }
        }

        if (optDenom != null) {
            return (int) ((float)Coin.COIN.value / optDenom.value * 10000);
        }

        if (inputAmount.isLessThan(Coin.COIN)) {
            return 20000;
        }

        //nondenom return largest first
        return -1L * inputAmount.div(Coin.COIN.value).value;
    }

    private static void checkDSTXes(StoredBlock block) {
        mapdstxLock.lock();
        try {
            Iterator<Map.Entry<Sha256Hash, CoinJoinBroadcastTx>> it = mapDSTX.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Sha256Hash, CoinJoinBroadcastTx> entry = it.next();
                if (entry.getValue().isExpired(block)) {
                    it.remove();
                }
            }
        } finally {
            mapdstxLock.unlock();
        }
    }

    public static void addDSTX(CoinJoinBroadcastTx dstx) {
        mapDSTX.put(dstx.getTx().getTxId(), dstx);
    }
    public static CoinJoinBroadcastTx getDSTX(Sha256Hash hash) {
        return mapDSTX.get(hash);
    }

    public static void updatedBlockTip(StoredBlock block){
        checkDSTXes(block);
    }
    public static void notifyChainLock(StoredBlock block) {
        checkDSTXes(block);
    }

    public static void updateDSTXConfirmedHeight(Transaction tx, int nHeight) {
        CoinJoinBroadcastTx broadcastTx = mapDSTX.get(tx.getTxId());
        if (broadcastTx == null) {
            return;
        }

        broadcastTx.setConfirmedHeight(nHeight);
        log.info("coinjoin: txid={}, nHeight={}", tx.getTxId(), nHeight);

    }
    public static void transactionAddedToMempool(Transaction tx) {
        mapdstxLock.lock();
        try {
            updateDSTXConfirmedHeight(tx, -1);
        } finally {
            mapdstxLock.unlock();
        }
    }
    public static void blockConnected(Block block, StoredBlock storedBlock, List<Transaction> vtxConflicted) {
        mapdstxLock.lock();
        try {
            for (Transaction tx : vtxConflicted){
                updateDSTXConfirmedHeight(tx, -1);
            }

            for (Transaction tx : block.getTransactions()){
                updateDSTXConfirmedHeight(tx, storedBlock.getHeight());
            }
        } finally {
            mapdstxLock.unlock();
        }
    }
    public static void blockDisconnected(Block block, StoredBlock storedBlock) {
        mapdstxLock.lock();
        try {
            for (Transaction tx : block.getTransactions()){
                updateDSTXConfirmedHeight(tx, -1);
            }
        } finally {
            mapdstxLock.unlock();
        }
    }

    public static String getMessageByID(PoolMessage nMessageID) {
        switch (nMessageID) {
            case ERR_ALREADY_HAVE:
                return "Already have that input.";
            case ERR_DENOM:
                return "No matching denominations found for mixing.";
            case ERR_ENTRIES_FULL:
                return "Entries are full.";
            case ERR_EXISTING_TX:
                return "Not compatible with existing transactions.";
            case ERR_FEES:
                return "Transaction fees are too high.";
            case ERR_INVALID_COLLATERAL:
                return "Collateral not valid.";
            case ERR_INVALID_INPUT:
                return "Input is not valid.";
            case ERR_INVALID_SCRIPT:
                return "Invalid script detected.";
            case ERR_INVALID_TX:
                return "Transaction not valid.";
            case ERR_MAXIMUM:
                return "Entry exceeds maximum size.";
            case ERR_MN_LIST:
                return "Not in the Masternode list.";
            case ERR_MODE:
                return "Incompatible mode.";
            case ERR_QUEUE_FULL:
                return "Masternode queue is full.";
            case ERR_RECENT:
                return "Last queue was created too recently.";
            case ERR_SESSION:
                return "Session not complete!";
            case ERR_MISSING_TX:
                return "Missing input transaction information.";
            case ERR_VERSION:
                return "Incompatible version.";
            case MSG_NOERR:
                return "No errors detected.";
            case MSG_SUCCESS:
                return "Transaction created successfully.";
            case MSG_ENTRIES_ADDED:
                return "Your entries added successfully.";
            case ERR_SIZE_MISMATCH:
                return "Inputs vs outputs size mismatch.";
            case ERR_TIMEOUT:
                return "Session has timed out.";
            default:
                return "Unknown response.";
        }
    }

    private static final HashMap<PoolStatus, String> statusMessageMap = new HashMap<>();
    static {
        statusMessageMap.put(PoolStatus.WARMUP, "Warming up...");
        statusMessageMap.put(PoolStatus.CONNECTING, "Trying to connect...");
        statusMessageMap.put(PoolStatus.MIXING, "Mixing in progress...");
        statusMessageMap.put(PoolStatus.FINISHED, "Mixing Finished");

        statusMessageMap.put(PoolStatus.ERR_NO_MASTERNODES_DETECTED, "No masternodes detected");
        statusMessageMap.put(PoolStatus.ERR_MASTERNODE_NOT_FOUND, "Can't find random Masternode");
        statusMessageMap.put(PoolStatus.ERR_WALLET_LOCKED, "Wallet is locked");
        statusMessageMap.put(PoolStatus.ERR_NOT_ENOUGH_FUNDS, "Not enough funds");
        statusMessageMap.put(PoolStatus.ERR_NO_INPUTS, "Can't mix: no compatible inputs found!");

        statusMessageMap.put(PoolStatus.WARN_NO_MIXING_QUEUES, "Failed to find mixing queue to join");
        statusMessageMap.put(PoolStatus.WARN_NO_COMPATIBLE_MASTERNODE, "No compatible Masternode found");
    }
    public static String getStatusById(PoolStatus status) {
        return statusMessageMap.get(status);
    }

    @GuardedBy("mapdstxLock")
    public static boolean hasDSTX(Sha256Hash hash) {
        return mapDSTX.containsKey(hash);
    }
}
