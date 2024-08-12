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
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_EXTRA;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_ALREADY_HAVE;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_DENOM;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_FEES;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_INVALID_INPUT;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_INVALID_SCRIPT;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_SIZE_MISMATCH;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_NOERR;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_IDLE;

public class CoinJoinBaseSession {
    private final Logger log = LoggerFactory.getLogger(CoinJoinBaseSession.class);
    protected final Context context;

    protected final ReentrantLock lock = Threading.lock("coinjoin_base_session");
    @GuardedBy("lock")
    protected final ArrayList<CoinJoinEntry> entries; // Masternode/clients entries

    protected final AtomicReference<PoolState> state = new AtomicReference<>(POOL_STATE_IDLE); // should be one of the POOL_STATE_XXX values
    protected final AtomicReference<PoolStatus> status = new AtomicReference<>(PoolStatus.WARMUP);
    protected final AtomicLong timeLastSuccessfulStep = new AtomicLong(0); // the time when last successful mixing step was performed
    protected final AtomicInteger sessionID = new AtomicInteger(0); // 0 if no mixing session is active

    @GuardedBy("lock")
    protected Transaction finalMutableTransaction; // the finalized transaction ready for signing

    protected void setNull() {
        // Both sides
        lock.lock();
        try {
            state.set(POOL_STATE_IDLE);
            sessionID.set(0);
            sessionDenom = 0;
            entries.clear();
            finalMutableTransaction.clearInputs();
            finalMutableTransaction.clearOutputs();
            timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
        } finally {
            lock.unlock();
        }
    }

    interface CheckTxOutputs {
        ValidInOuts check(TransactionOutput txout);
    }

    protected ValidInOuts isValidInOuts(List<TransactionInput> vin, List<TransactionOutput> vout) {

        HashSet<Script> setScripPubKeys = new HashSet<>();
        final ValidInOuts result = new ValidInOuts();
        result.messageId = MSG_NOERR;
        result.consumeCollateral = false;

        if (vin.size() != vout.size()) {
            log.info("ERROR: inputs vs outputs size mismatch! {} vs {}", vin.size(), vout.size());
            result.setMessageId(ERR_SIZE_MISMATCH);
            result.consumeCollateral = true;
            return result.setResult(false);
        }

        CheckTxOutputs checkTxOut = new CheckTxOutputs() {
            @Override
            public ValidInOuts check(TransactionOutput txout) {
                int denom = CoinJoin.amountToDenomination(txout.getValue());
                if (denom != sessionDenom) {
                    log.info("ERROR: incompatible denom {} ({}) != sessionDenom {} ({})",
                            denom, CoinJoin.denominationToString(denom), sessionDenom, CoinJoin.denominationToString(sessionDenom));
                    result.setMessageId(ERR_DENOM);
                    result.consumeCollateral = true;
                    return result.setResult(false);
                }
                if (!ScriptPattern.isP2PKH(txout.getScriptPubKey())) {
                    log.info("ERROR: invalid script! scriptPubKey={}", txout.getScriptPubKey());
                    result.setMessageId(ERR_INVALID_SCRIPT);
                    result.consumeCollateral = true;
                    return result.setResult(false);
                }
                if (!setScripPubKeys.add(txout.getScriptPubKey())) {
                    log.info("ERROR: already have this script! scriptPubKey={}", txout.getScriptPubKey());
                    result.setMessageId(ERR_ALREADY_HAVE);
                    result.consumeCollateral = true;
                    return result.setResult(false);
                }
                // IsPayToPublicKeyHash() above already checks for scriptPubKey size,
                // no need to double-check, hence no usage of ERR_NON_STANDARD_PUBKEY
                return result.setResult(true);
            }
        };

        // Dash Core checks that the fee's are zero, but we cannot since we don't have access to all of the inputs
        Coin fees = Coin.ZERO;

        for (TransactionOutput txout : vout) {
            ValidInOuts outputResult = checkTxOut.check(txout);
            if (!outputResult.result) {
                return result.setResult(false);
            }
        }

        for (TransactionInput txin :vin){
            log.info(COINJOIN_EXTRA, " txin={}", txin);

            if (txin.getOutpoint() == null) {
                log.info("coinjoin: ERROR: invalid input!");
                result.setMessageId(ERR_INVALID_INPUT);
                result.consumeCollateral = true;
                return result.setResult(false);
            }
        }

        // The same size and denom for inputs and outputs ensures their total value is also the same,
        // no need to double-check. If not, we are doing something wrong, bail out.
        if (!fees.isZero()) {
            log.info("ERROR: non-zero fees! fees: {}", fees.toFriendlyString());
            result.setMessageId(ERR_FEES);
            return result.setResult(false);
        }

        return result.setResult(true);
    }


    protected int sessionDenom = 0; // Users must submit a denom matching this

    public CoinJoinBaseSession(Context context) {
        this.context = context;
        entries = Lists.newArrayList();
        finalMutableTransaction = new Transaction(context.getParams());
    }

    public PoolState getState() {
        return state.get();
    }
    public PoolStatus getStatus() {
        return status.get();
    }

    String getStateString() {
        switch (state.get()) {
            case POOL_STATE_IDLE:
                return "IDLE";
            case POOL_STATE_QUEUE:
                return "QUEUE";
            case POOL_STATE_ACCEPTING_ENTRIES:
                return "ACCEPTING_ENTRIES";
            case POOL_STATE_SIGNING:
                return "SIGNING";
            case POOL_STATE_ERROR:
                return "ERROR";
            default:
                return "UNKNOWN";
        }
    }

    public int getEntriesCount() {
        return entries.size();
    }

    public int getSessionDenom() {
        return sessionDenom;
    }

    public int getSessionID() {
        return sessionID.get();
    }
}
