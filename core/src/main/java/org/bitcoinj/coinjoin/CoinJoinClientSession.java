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
import org.bitcoinj.coinjoin.listeners.CoinJoinTransactionListener;
import org.bitcoinj.coinjoin.listeners.SessionCompleteListener;
import org.bitcoinj.coinjoin.listeners.SessionStartedListener;
import org.bitcoinj.coinjoin.utils.CoinJoinManager;
import org.bitcoinj.coinjoin.utils.CoinJoinTransactionType;
import org.bitcoinj.coinjoin.utils.CompactTallyItem;
import org.bitcoinj.coinjoin.utils.KeyHolderStorage;
import org.bitcoinj.coinjoin.utils.MasternodeGroup;
import org.bitcoinj.coinjoin.utils.ReserveDestination;
import org.bitcoinj.coinjoin.utils.TransactionBuilder;
import org.bitcoinj.coinjoin.utils.TransactionBuilderOutput;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionDestination;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.MasternodeMetaDataManager;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptException;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.signers.CoinJoinTransactionSigner;
import org.bitcoinj.signers.TransactionSigner;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Balance;
import org.bitcoinj.wallet.CoinControl;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.WalletEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_DENOM_OUTPUTS_THRESHOLD;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_ENTRY_MAX_SIZE;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_EXTRA;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_QUEUE_TIMEOUT;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_SIGNING_TIMEOUT;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_CONNECTION_TIMEOUT;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_SESSION;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_TIMEOUT;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_POOL_MAX;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_POOL_MIN;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_SUCCESS;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_ACCEPTING_ENTRIES;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_ERROR;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_IDLE;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_MAX;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_MIN;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_QUEUE;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_SIGNING;
import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;
import static org.bitcoinj.wallet.CoinType.ONLY_COINJOIN_COLLATERAL;

public class CoinJoinClientSession extends CoinJoinBaseSession {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinClientSession.class);
    private static final Random random = new Random();
    private final ArrayList<TransactionOutPoint> outPointLocked = Lists.newArrayList();
    private String strLastMessage;
    private String strAutoDenomResult;
    private Boolean lastCreateDenominatedResult = true;

    private Masternode mixingMasternode;
    private boolean joined; // did we join a session (true), or start a session (false)
    private Transaction txMyCollateral; // client side collateral
    private boolean isMyCollateralValid = false;
    private PendingDsaRequest pendingDsaRequest;

    private final KeyHolderStorage keyHolderStorage; // storage for keys used in PrepareDenominate

    private final MasternodeSync masternodeSync;
    private final CoinJoinManager coinJoinManager;
    private final SimplifiedMasternodeListManager masternodeListManager;
    private final MasternodeMetaDataManager masternodeMetaDataManager;
    private final WalletEx mixingWallet;

    private final AtomicBoolean hasNothingToDo = new AtomicBoolean(false); // is mixing finished?

    private final HashMap<Sha256Hash, Integer> collateralSessionMap = new HashMap<>();
    private final CopyOnWriteArrayList<ListenerRegistration<SessionStartedListener>> sessionStartedListeners
            = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListenerRegistration<SessionCompleteListener>> sessionCompleteListeners
            = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ListenerRegistration<CoinJoinTransactionListener>> transactionListeners
            = new CopyOnWriteArrayList<>();

    /// Create denominations
    private boolean createDenominated(Coin balanceToDenominate, boolean dryRun) {

        if (!CoinJoinClientOptions.isEnabled()) return false;

        // NOTE: We do not allow txes larger than 100 kB, so we have to limit number of inputs here.
        // We still want to consume a lot of inputs to avoid creating only smaller denoms though.
        // Knowing that each CTxIn is at least 148 B big, 400 inputs should take 400 x ~148 B = ~60 kB.
        // This still leaves more than enough room for another data of typical CreateDenominated tx.
        List<CompactTallyItem> vecTally = mixingWallet.selectCoinsGroupedByAddresses(true, true, true, 400);
        if (vecTally.isEmpty()) {
            log.info( "coinjoin: selectCoinsGroupedByAddresses can't find any inputs!");
            return false;
        }

        // Start from the largest balances first to speed things up by creating txes with larger/largest denoms included
        vecTally.sort((a, b) -> {
            if (a.amount.isGreaterThan(b.amount))
                return 1;
            if (a.amount.equals(b.amount))
                return 0;
            return -1;
        });

        boolean fCreateMixingCollaterals = !mixingWallet.hasCollateralInputs();

        for (CompactTallyItem item : vecTally) {
            if (!createDenominated(balanceToDenominate, item, fCreateMixingCollaterals, dryRun)) continue;
            return true;
        }

        log.info("coinjoin: createDenominated({}) failed! ", balanceToDenominate.toFriendlyString());
        return false;
    }

    public void processTransaction(Transaction tx) {
        log.info(COINJOIN_EXTRA, "processing tx of {}: {}", tx.getValue(mixingWallet), tx.getTxId());
        if (collateralSessionMap.containsKey(tx.getTxId())) {
            queueTransactionListeners(tx, collateralSessionMap.get(tx.getTxId()), CoinJoinTransactionType.MixingFee);
        }
    }

    static class Result<T> {
        private T result;

        public Result(T result) {
            this.result = result;
        }

        public T get() {
            return result;
        }

        public void set(T result) {
            this.result = result;
        }
    }

    interface NeedMoreOutputs {
        boolean process(Coin balanceToDenominate, int outputs, Result<Boolean> addFinal);
    }

    interface CountPossibleOutputs {
        int process(Coin amount);
    }

    private boolean createDenominated(Coin balanceToDenominate, CompactTallyItem tallyItem, boolean fCreateMixingCollaterals, boolean dryRun) {

        if (!CoinJoinClientOptions.isEnabled())
            return false;

        // denominated input is always a single one, so we can check its amount directly and return early
        if (tallyItem.inputCoins.size() == 1 && CoinJoin.isDenominatedAmount(tallyItem.amount)) {
            return false;
        }

        try (TransactionBuilder txBuilder = new TransactionBuilder(mixingWallet, coinJoinManager, tallyItem, dryRun)) {
            log.info("coinjoin: Start {}", txBuilder);

            // ****** Add an output for mixing collaterals ************ /

            if (fCreateMixingCollaterals && txBuilder.addOutput(CoinJoin.getMaxCollateralAmount()) == null) {
                log.info("coinjoin: Failed to add collateral output");
                return false;
            }

            // ****** Add outputs for denoms ************ /

            final Result<Boolean> addFinal = new Result<>(true);
            List<Coin> denoms = CoinJoinClientOptions.getDenominations();

            HashMap<Coin, Integer> mapDenomCount = new HashMap<>();
            for (Coin denomValue : denoms) {
                mapDenomCount.put(denomValue, mixingWallet.countInputsWithAmount(denomValue));
            }

            // Will generate outputs for the createdenoms up to coinjoinmaxdenoms per denom

            // This works in the way creating PS denoms has traditionally worked, assuming enough funds,
            // it will start with the smallest denom then create 11 of those, then go up to the next biggest denom create 11
            // and repeat. Previously, once the largest denom was reached, as many would be created were created as possible and
            // then any remaining was put into a change address and denominations were created in the same manner a block later.
            // Now, in this system, so long as we don't reach COINJOIN_DENOM_OUTPUTS_THRESHOLD outputs the process repeats in
            // the same transaction, creating up to nCoinJoinDenomsHardCap per denomination in a single transaction.

            while (txBuilder.couldAddOutput(CoinJoin.getSmallestDenomination()) && txBuilder.countOutputs() < COINJOIN_DENOM_OUTPUTS_THRESHOLD) {
                for (int it = denoms.size() - 1; it >= 0; --it) {
                    Coin denomValue = denoms.get(it);
                    Integer currentDenomIt = mapDenomCount.get(denomValue);

                    int nOutputs = 0;


                    NeedMoreOutputs needMoreOutputs = new NeedMoreOutputs() {
                        @Override
                        public boolean process(Coin balanceToDenominate, int outputs, Result<Boolean> addFinal) {
                            if (txBuilder.couldAddOutput(denomValue)) {
                                if (addFinal.get() && balanceToDenominate.isPositive() && balanceToDenominate.isLessThan(denomValue)) {
                                    addFinal.set(false); // add final denom only once, only the smalest possible one
                                    log.info("coinjoin: 1 - FINAL - {}, toDenominate: {}, outputs: {}, {}",
                                            denomValue.toFriendlyString(), balanceToDenominate.toFriendlyString(), outputs, txBuilder);
                                    return true;
                                } else if (balanceToDenominate.isGreaterThanOrEqualTo(denomValue)) {
                                    return true;
                                }
                            }
                            return false;
                        }
                    };

                    // add each output up to 11 times or until it can't be added again or until we reach nCoinJoinDenomsGoal
                    while (needMoreOutputs.process(balanceToDenominate, nOutputs, addFinal) && nOutputs <= 10 && currentDenomIt < CoinJoinClientOptions.getDenomsGoal()) {
                        // Add output and subtract denomination amount
                        if (txBuilder.addOutput(denomValue) != null) {
                            ++nOutputs;
                            mapDenomCount.put(denomValue, ++currentDenomIt);
                            balanceToDenominate = balanceToDenominate.subtract(denomValue);
                            log.info("coinjoin: 1 - {}, toDenominate: {}, outputs: {}, {}",
                                    denomValue.toFriendlyString(), balanceToDenominate.toFriendlyString(), nOutputs, txBuilder);
                        } else {
                            log.info("coinjoin: 1 - Error: AddOutput failed for {}, toDenominate: {}, outputs: {}, {}",
                                    denomValue.toFriendlyString(), balanceToDenominate.toFriendlyString(), nOutputs, txBuilder);
                            return false;
                        }

                    }

                    if (txBuilder.getAmountLeft().isZero() || balanceToDenominate.isLessThanOrEqualTo(Coin.ZERO)) break;
                }

                boolean finished = true;
                for (Map.Entry<Coin, Integer> entry : mapDenomCount.entrySet()) {
                    // Check if this specific denom could use another loop, check that there aren't nCoinJoinDenomsGoal of this
                    // denom and that our nValueLeft/balanceToDenominate is enough to create one of these denoms, if so, loop again.
                    Coin denom = entry.getKey();
                    Integer count = entry.getValue();
                    if (count < CoinJoinClientOptions.getDenomsGoal() && txBuilder.couldAddOutput(denom) &&
                            balanceToDenominate.isGreaterThan(CoinJoin.getSmallestDenomination())) {
                        finished = false;
                        log.info("coinjoin: 1 - NOT finished - denomValue: {}, count: {}, toDenominate: {}, {}",
                                entry.getKey().toFriendlyString(), entry.getValue(), balanceToDenominate.toFriendlyString(), txBuilder);
                        break;
                    }
                    log.info("coinjoin: 1 - FINISHED - {}, count: {}, toDenominate: {}, {}",
                            entry.getKey().toFriendlyString(), entry.getValue(), balanceToDenominate.toFriendlyString(), txBuilder);
                }

                if (finished) break;
            }

            // Now that nCoinJoinDenomsGoal worth of each denom have been created or the max number of denoms given the value of the input, do something with the remainder.
            if (txBuilder.couldAddOutput(CoinJoin.getSmallestDenomination()) && balanceToDenominate.isGreaterThanOrEqualTo(CoinJoin.getSmallestDenomination()) && txBuilder.countOutputs() < COINJOIN_DENOM_OUTPUTS_THRESHOLD) {
                Coin largestDenomValue = denoms.get(0);

                log.info("coinjoin: 2 - Process remainder: {}", txBuilder);

                CountPossibleOutputs countPossibleOutputs = new CountPossibleOutputs() {
                    @Override
                    public int process(Coin amount) {
                        ArrayList<Coin> vecOutputs = new ArrayList<>();
                        while (true) {
                            // Create a potential output
                            vecOutputs.add(amount);
                            if (!txBuilder.couldAddOutputs(vecOutputs) || txBuilder.countOutputs() + vecOutputs.size() > COINJOIN_DENOM_OUTPUTS_THRESHOLD) {
                                // If it's not possible to add it due to insufficient amount left or total number of outputs exceeds
                                // COINJOIN_DENOM_OUTPUTS_THRESHOLD drop the output again and stop trying.
                                vecOutputs.remove(vecOutputs.size() - 1);
                                break;
                            }
                        }
                        return vecOutputs.size();
                    }
                };

                // Go big to small
                for (Coin denomValue : denoms) {
                    if (balanceToDenominate.isLessThanOrEqualTo(Coin.ZERO)) break;
                    int nOutputs = 0;

                    // Number of denoms we can create given our denom and the amount of funds we have left
                    int denomsToCreateValue = countPossibleOutputs.process(denomValue);
                    // Prefer overshooting the target balance by larger denoms (hence `+1`) instead of a more
                    // accurate approximation by many smaller denoms. This is ok because when we get here we
                    // should have nCoinJoinDenomsGoal of each smaller denom already. Also, without `+1`
                    // we can end up in a situation when there is already nCoinJoinDenomsHardCap of smaller
                    // denoms, yet we can't mix the remaining balanceToDenominate because it's smaller than
                    // denomValue (and thus denomsToCreateBal == 0), so the target would never get reached
                    // even when there is enough funds for that.
                    int denomsToCreateBal = (int) (balanceToDenominate.value / denomValue.value + 1);
                    // Use the smaller value
                    int denomsToCreate = Math.min(denomsToCreateValue, denomsToCreateBal);
                    log.info("coinjoin: 2 - toDenominate: {}, {}, denomsToCreateValue: {}, denomsToCreateBal: {}",
                            balanceToDenominate.toFriendlyString(), denomValue.toFriendlyString(), denomsToCreateValue, denomsToCreateBal);
                    Integer it = mapDenomCount.get(denomValue);
                    for (int i = 0; i < denomsToCreate; ++i) {
                        // Never go above the cap unless it's the largest denom
                        if (denomValue != largestDenomValue && it >= CoinJoinClientOptions.getDenomsHardCap()) break;

                        // Increment helpers, add output and subtract denomination amount
                        if (txBuilder.addOutput(denomValue) != null) {
                            nOutputs++;
                            mapDenomCount.put(denomValue, ++it);
                            balanceToDenominate = balanceToDenominate.subtract(denomValue);
                        } else {
                            log.info("coinjoin: 2 - Error: AddOutput failed at {}/{}, {}", i + 1, denomsToCreate, txBuilder);
                            break;
                        }
                        log.info("coinjoin: 2 - {}, toDenominate: {}, nOutputs: {}, {}",
                                denomValue.toFriendlyString(), balanceToDenominate.toFriendlyString(), nOutputs, txBuilder);
                        if (txBuilder.countOutputs() >= COINJOIN_DENOM_OUTPUTS_THRESHOLD) break;
                    }
                    if (txBuilder.countOutputs() >= COINJOIN_DENOM_OUTPUTS_THRESHOLD) break;
                }
            }

            log.info("coinjoin: 3 - toDenominate: {}, {}", balanceToDenominate.toFriendlyString(), txBuilder);

            for (Map.Entry<Coin, Integer> it : mapDenomCount.entrySet()) {
                log.info("coinjoin: 3 - DONE - {}, count: {}", it.getKey().toFriendlyString(), it.getValue());
            }

            // No reasons to create mixing collaterals if we can't create denoms to mix
            if ((fCreateMixingCollaterals && txBuilder.countOutputs() == 1) || txBuilder.countOutputs() == 0) {
                return false;
            }

            if (!dryRun) {
                StringBuilder strResult = new StringBuilder();
                if (!txBuilder.commit(strResult)) {
                    log.info("coinjoin: Commit failed: {}", strResult);
                    return false;
                }

                // use the same nCachedLastSuccessBlock as for DS mixing to prevent race
                coinJoinManager.coinJoinClientManagers.get(mixingWallet.getDescription()).updatedSuccessBlock();

                log.info("coinjoin: txid: {}", strResult);

                queueTransactionListeners(txBuilder.getTransaction(), CoinJoinTransactionType.CreateDenomination);
            }
        }
        return true;
    }

    /// Split up large inputs or make fee sized inputs
    private boolean makeCollateralAmounts() {

        if (!CoinJoinClientOptions.isEnabled()) return false;

        // NOTE: We do not allow txes larger than 100 kB, so we have to limit number of inputs here.
        // We still want to consume a lot of inputs to avoid creating only smaller denoms though.
        // Knowing that each CTxIn is at least 148 B big, 400 inputs should take 400 x ~148 B = ~60 kB.
        // This still leaves more than enough room for another data of typical MakeCollateralAmounts tx.
        List<CompactTallyItem> vecTally = mixingWallet.selectCoinsGroupedByAddresses(false, false, true, 400);
        if (vecTally.isEmpty()) {
            log.info("coinjoin: selectCoinsGroupedByAddresses can't find any inputs!\n");
            return false;
        }

        // Start from the smallest balances first to consume tiny amounts and cleanup UTXO a bit
        Collections.sort(vecTally, new Comparator<CompactTallyItem>() {
            @Override
            public int compare(CompactTallyItem a, CompactTallyItem b) {
                return a.amount.compareTo(b.amount);
            }
        });

        // First try to use only non-denominated funds
        for (CompactTallyItem item : vecTally) {
            if (!makeCollateralAmounts(item, false)) continue;
            return true;
        }

        // There should be at least some denominated funds we should be able to break in pieces to continue mixing
        for (CompactTallyItem item : vecTally) {
            if (!makeCollateralAmounts(item, true)) continue;
            return true;
        }

        // If we got here then something is terribly broken actually
        log.error("coinjoin: ERROR: Can't make collaterals!\n");
        return false;
    }
    private boolean makeCollateralAmounts(CompactTallyItem tallyItem, boolean fTryDenominated) {

        if (!CoinJoinClientOptions.isEnabled()) return false;

        // Denominated input is always a single one, so we can check its amount directly and return early
        if (!fTryDenominated && tallyItem.inputCoins.size() == 1 && CoinJoin.isDenominatedAmount(tallyItem.amount)) {
            return false;
        }

        // Skip single inputs that can be used as collaterals already
        if (tallyItem.inputCoins.size() == 1 && CoinJoin.isCollateralAmount(tallyItem.amount)) {
            return false;
        }

        //const auto pwallet = GetWallet(mixingWallet.GetName());
        final WalletEx wallet = mixingWallet;

        if (wallet == null) {
            log.info("coinjoin: Couldn't get wallet pointer");
            return false;
        }

        try (TransactionBuilder txBuilder = new TransactionBuilder(wallet, coinJoinManager, tallyItem, false)) {
            log.info("coinjoin: Start {}", txBuilder);

            // Skip way too tiny amounts. Smallest we want is minimum collateral amount in a one output tx
            if (!txBuilder.couldAddOutput(CoinJoin.getCollateralAmount())) {
                return false;
            }

            int nCase; // Just for debug logs
            if (txBuilder.couldAddOutputs(Arrays.asList(CoinJoin.getMaxCollateralAmount(), CoinJoin.getCollateralAmount()))) {
                nCase = 1;
                // <case1>, see TransactionRecord::decomposeTransaction
                // Out1 == CoinJoin.GetMaxCollateralAmount()
                // Out2 >= CoinJoin.GetCollateralAmount()

                txBuilder.addOutput(CoinJoin.getMaxCollateralAmount());
                // Note, here we first add a zero amount output to get the remainder after all fees and then assign it
                TransactionBuilderOutput out = txBuilder.addOutput();
                Coin nAmountLeft = txBuilder.getAmountLeft();
                // If remainder is denominated add one duff to the fee
                out.updateAmount(CoinJoin.isDenominatedAmount(nAmountLeft) ? nAmountLeft.subtract(Coin.valueOf(1, 0)) : nAmountLeft);

            } else if (txBuilder.couldAddOutputs(Arrays.asList(CoinJoin.getCollateralAmount(), CoinJoin.getCollateralAmount()))) {
                nCase = 2;
                // <case2>, see TransactionRecord::decomposeTransaction
                // Out1 CoinJoin.isCollateralAmount()
                // Out2 CoinJoin.isCollateralAmount()

                // First add two outputs to get the available value after all fees
                TransactionBuilderOutput out1 = txBuilder.addOutput();
                TransactionBuilderOutput out2 = txBuilder.addOutput();

                // Create two equal outputs from the available value. This adds one duff to the fee if txBuilder.GetAmountLeft() is odd.
                Coin nAmountOutputs = txBuilder.getAmountLeft().div(2);

                assert (CoinJoin.isCollateralAmount(nAmountOutputs));

                out1.updateAmount(nAmountOutputs);
                out2.updateAmount(nAmountOutputs);

            } else { // still at least possible to add one CoinJoin.GetCollateralAmount() output
                nCase = 3;
                // <case3>, see TransactionRecord::decomposeTransaction
                // Out1 CoinJoin.isCollateralAmount()
                // Out2 Skipped
                TransactionBuilderOutput out = txBuilder.addOutput();
                out.updateAmount(txBuilder.getAmountLeft());

                assert (CoinJoin.isCollateralAmount(out.getAmount()));
            }

            log.info("coinjoin: Done with case {}: {}", nCase, txBuilder);

            assert (txBuilder.isDust(txBuilder.getAmountLeft()));

            StringBuilder strResult = new StringBuilder();
            if (!txBuilder.commit(strResult)) {
                log.info("coinjoin: Commit failed: {}", strResult);
                return false;
            }

            coinJoinManager.coinJoinClientManagers.get(mixingWallet.getDescription()).updatedSuccessBlock();

            log.info("coinjoin: txid: {}", strResult);
            queueTransactionListeners(txBuilder.getTransaction(), CoinJoinTransactionType.MakeCollateralInputs);
            return true;
        }
    }

    private boolean createCollateralTransaction(Transaction txCollateral, StringBuilder strReason){
        ArrayList<TransactionOutput> vCoins = new ArrayList<>();
        CoinControl coin_control = new CoinControl();
        coin_control.setCoinType(ONLY_COINJOIN_COLLATERAL);

        mixingWallet.availableCoins(vCoins, true, coin_control);

        if (vCoins.isEmpty()) {
            strReason.append("CoinJoin requires a collateral transaction and could not locate an acceptable input!");
            return false;
        }

        TransactionOutput output = vCoins.get(random.nextInt(vCoins.size()));
        final TransactionOutput txout = output.getParentTransaction().getOutput(output.getIndex());

        txCollateral.clearInputs();
        txCollateral.addInput(output);
        txCollateral.clearOutputs();

        // pay collateral charge in fees
        // NOTE: no need for protobump patch here,
        // CoinJoin.isCollateralAmount in GetCollateralTxDSIn should already take care of this
        if (txout.getValue().isGreaterThanOrEqualTo(CoinJoin.getCollateralAmount().multiply(2))) {
            // make our change address
            Script scriptChange;
            ReserveDestination reserveDest = new ReserveDestination(mixingWallet);
            TransactionDestination dest = reserveDest.getReservedDestination(true);

            scriptChange = dest.getScript();
            reserveDest.keepDestination();
            // return change
            txCollateral.addOutput(txout.getValue().subtract(CoinJoin.getCollateralAmount()), scriptChange);
        } else { // txout.nValue < CoinJoin.GetCollateralAmount() * 2
            // create dummy data output only and pay everything as a fee
            txCollateral.addOutput(Coin.ZERO, new ScriptBuilder().op(OP_RETURN).build());
        }

        try {
            // don't set the source to TransactionConfidence.Source.SELF on these collateral transactions
            // otherwise when the masternodes do submit these transactions to the network, they will be
            // ignored by DashJ and when they are received
            SendRequest req = SendRequest.forTx(txCollateral);
            req.aesKey = coinJoinManager.requestKeyParameter(mixingWallet);
            mixingWallet.signTransaction(req);
        } catch (ScriptException x) {
            strReason.append("Unable to sign collateral transaction!");
            return false;
        }
        isMyCollateralValid = true;
        if (!collateralSessionMap.containsKey(txCollateral.getTxId())) {
            collateralSessionMap.put(txCollateral.getTxId(), 0);
        }
        return true;
    }

    private boolean joinExistingQueue(Coin balanceNeedsAnonymized) {
        if (!CoinJoinClientOptions.isEnabled()) return false;
        if (coinJoinManager.getCoinJoinClientQueueManager() == null) return false;

        SimplifiedMasternodeList mnList = masternodeListManager.getListAtChainTip();

        // Dash Core checks for recent winners, but we cannot do that

        // Look through the queues and see if anything matches
        CoinJoinQueue dsq;
        while ((dsq = coinJoinManager.getCoinJoinClientQueueManager().getQueueItemAndTry()) != null) {
            Masternode dmn = mnList.getMN(dsq.getProTxHash());

            if (dmn == null) {
                log.info("coinjoin: dsq masternode is not in masternode list, masternode={}", dsq.getProTxHash());
                continue;
            }

            // mixing rate limit i.e. nLastDsq check should already pass in DSQUEUE ProcessMessage
            // in order for dsq to get into veCoinJoinQueue, so we should be safe to mix already,
            // no need for additional verification here

            log.info("coinjoin: trying existing queue: {}", dsq);

            ArrayList <CoinJoinTransactionInput> vecTxDSInTmp = new ArrayList<>();

            // Try to match their denominations if possible, select exact number of denominations
            if (!mixingWallet.selectTxDSInsByDenomination(dsq.getDenomination(), balanceNeedsAnonymized, vecTxDSInTmp)) {
                log.info("coinjoin: couldn't match denom {} ({})", dsq.getDenomination(), CoinJoin.denominationToString(dsq.getDenomination()));
                continue;
            }

            coinJoinManager.coinJoinClientManagers.get(mixingWallet.getDescription()).addUsedMasternode(dsq.getProTxHash());

            if (coinJoinManager.isMasternodeOrDisconnectRequested(dmn.getService())) {
                log.info("coinjoin: skipping masternode connection, addr={}", dmn.getService());
                continue;
            }

            sessionDenom = dsq.getDenomination();
            mixingMasternode = dmn;
            pendingDsaRequest = new PendingDsaRequest(dmn.getService(), new CoinJoinAccept(mixingWallet.getContext().getParams(), sessionDenom, txMyCollateral));
            coinJoinManager.addPendingMasternode(this);
            setState(POOL_STATE_QUEUE);
            timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
            log.info("coinjoin: join existing queue -> pending connection, {} ({}), addr={}",
                    sessionDenom, CoinJoin.denominationToString(sessionDenom), dmn.getService());
            setStatus(PoolStatus.CONNECTING);
            joined = true;
            return true;
        }
        setStatus(PoolStatus.WARN_NO_MIXING_QUEUES);
        return false;
    }

    private boolean startNewQueue(Coin balanceNeedsAnonymized) {
        if (!CoinJoinClientOptions.isEnabled()) 
            return false;
        if (!balanceNeedsAnonymized.isPositive()) 
            return false;

        int nTries = 0;
        SimplifiedMasternodeList mnList = masternodeListManager.getListAtChainTip();
        int nMnCount = mnList.getValidMNsCount();

        // find available denominated amounts
        HashSet<Coin> setAmounts = new HashSet<>();
        if (!mixingWallet.selectDenominatedAmounts(balanceNeedsAnonymized, setAmounts)) {
            // this should never happen according to Dash Core
            // but this does happen if there is are coins that can be denominated, but cannot be cause of the denom goals
            // so if the last denom result was false and there are no inputs, lets trigger the end.
            if (!lastCreateDenominatedResult)
                setStatus(PoolStatus.ERR_NO_INPUTS);
            return false;
        }

        // otherwise, try one randomly
        while (nTries < 10) {
            Masternode dmn = coinJoinManager.coinJoinClientManagers.get(mixingWallet.getDescription()).getRandomNotUsedMasternode();

            if (dmn == null) {
                setStatus(PoolStatus.ERR_MASTERNODE_NOT_FOUND);
                return false;
            }

            coinJoinManager.coinJoinClientManagers.get(mixingWallet.getDescription()).addUsedMasternode(dmn.getProTxHash());

            long nLastDsq = masternodeMetaDataManager.getMetaInfo(dmn.getProTxHash()).getLastDsq();
            long nDsqThreshold = masternodeMetaDataManager.getDsqThreshold(dmn.getProTxHash(), nMnCount);
            if (nLastDsq != 0 && nDsqThreshold > masternodeMetaDataManager.getDsqCount()) {
                log.info("coinjoin: warning: Too early to mix on this masternode!" + /* Continued */
                        " masternode={} addr={} lastDsq={}  dsqThreshold={}  nDsqCount={}",
                        dmn.getProTxHash(), dmn.getService(), nLastDsq,
                        nDsqThreshold, masternodeMetaDataManager.getDsqCount());
                nTries++;
                continue;
            }

            if (coinJoinManager.isMasternodeOrDisconnectRequested(dmn.getService())) {
                log.info("coinjoin: warning: skipping masternode connection, addr={}", dmn.getService());
                nTries++;
                continue;
            }

            log.info("coinjoin: attempt {} connection to mn {}, protx: {}", nTries + 1, dmn.getService(), dmn.getProTxHash().toString().substring(0, 16));

            // try to get a single random denom out of setAmounts
            while (sessionDenom == 0) {
                for (Coin it : setAmounts) {
                    if (setAmounts.size() > 1 && random.nextInt(2) != 0)
                        continue;
                    sessionDenom = CoinJoin.amountToDenomination(it);
                    break;
                }
            }

            mixingMasternode = dmn;
            coinJoinManager.addPendingMasternode(this);
            pendingDsaRequest = new PendingDsaRequest(dmn.getService(), new CoinJoinAccept(context.getParams(), sessionDenom, txMyCollateral));
            setState(POOL_STATE_QUEUE);
            timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
            log.info("coinjoin: start new queue -> pending connection, nSessionDenom: {} ({}), addr={}",
                    sessionDenom, CoinJoin.denominationToString(sessionDenom), dmn.getService());
            coinJoinManager.startAsync();
            setStatus(PoolStatus.CONNECTING);
            joined = false;
            return true;
        }
        strAutoDenomResult = "Failed to start a new mixing queue";
        return false;
    }

    /// step 0: select denominated inputs and txouts
    private boolean selectDenominate(StringBuilder strErrorRet, List<CoinJoinTransactionInput> vecTxDSInRet) {

        if (!CoinJoinClientOptions.isEnabled()) return false;

        if (mixingWallet.isEncrypted() && coinJoinManager.requestKeyParameter(mixingWallet) == null) {
            strErrorRet.append("Wallet locked, unable to create transaction!");
            return false;
        }

        if (getEntriesCount() > 0) {
            strErrorRet.append("Already have pending entries in the CoinJoin pool");
            return false;
        }

        vecTxDSInRet.clear();

        boolean fSelected = mixingWallet.selectTxDSInsByDenomination(sessionDenom, CoinJoin.getMaxPoolAmount(), vecTxDSInRet);
        if (!fSelected) {
            strErrorRet.append("Can't select current denominated inputs: ").append(CoinJoin.denominationToAmount(sessionDenom).toFriendlyString()).append(" for session ").append(sessionID.get());
            vecTxDSInRet.forEach(input -> strErrorRet.append("\n").append(input));
            return false;
        }

        return true;
    }
    /// step 1: prepare denominated inputs and outputs
    private boolean prepareDenominate(int minRounds, int maxRounds, StringBuilder strErrorRet, List<CoinJoinTransactionInput> vecTxDSIn, List<Pair<CoinJoinTransactionInput, TransactionOutput>> vecPSInOutPairsRet) {
        return prepareDenominate(minRounds, maxRounds, strErrorRet, vecTxDSIn, vecPSInOutPairsRet, false);
    }
    private boolean prepareDenominate(int minRounds, int maxRounds, StringBuilder strErrorRet, List<CoinJoinTransactionInput> vecTxDSIn, List<Pair<CoinJoinTransactionInput, TransactionOutput>> vecPSInOutPairsRet, boolean fDryRun) {

        if (!CoinJoin.isValidDenomination(sessionDenom)) {
            strErrorRet.append("Incorrect session denom");
            return false;
        }
        Coin nDenomAmount = CoinJoin.denominationToAmount(sessionDenom);

        // NOTE: No need to randomize order of inputs because they were
        // initially shuffled in CWallet::SelectTxDSInsByDenomination already.
        int nSteps = 0;
        vecPSInOutPairsRet.clear();

        // Try to add up to COINJOIN_ENTRY_MAX_SIZE of every needed denomination
        for (CoinJoinTransactionInput entry : vecTxDSIn) {
            if (nSteps >= COINJOIN_ENTRY_MAX_SIZE) break;
            if (entry.getRounds() < minRounds || entry.getRounds() > maxRounds) continue;

            Script scriptDenom;
            if (fDryRun) {
                scriptDenom = new Script(new byte[0]);
            } else {
                // randomly skip some inputs when we have at least one of the same denom already
                // TODO: make it adjustable via options/cmd-line params
                if (nSteps >= 1 && random.nextInt(5) == 0) {
                    // still count it as a step to randomize number of inputs
                    // if we have more than (or exactly) COINJOIN_ENTRY_MAX_SIZE of them
                    ++nSteps;
                    continue;
                }
                scriptDenom = keyHolderStorage.addKey(mixingWallet);
            }
            vecPSInOutPairsRet.add(new Pair<>(entry, new TransactionOutput(context.getParams(), null, nDenomAmount, scriptDenom.getProgram())));
            // step is complete
            ++nSteps;
        }

        if (vecPSInOutPairsRet.isEmpty()) {
            keyHolderStorage.returnAll();
            if (strErrorRet.length() == 0)
                strErrorRet.append("Can't prepare current denominated outputs");
            return false;
        }

        if (fDryRun) {
            return true;
        }

        for (Pair<CoinJoinTransactionInput, TransactionOutput> pair : vecPSInOutPairsRet) {
            mixingWallet.lockCoin(pair.getFirst().getOutpoint());
            outPointLocked.add(pair.getFirst().getOutpoint());
        }

        return true;
    }
    /// step 2: send denominated inputs and outputs prepared in step 1
    private boolean sendDenominate(List<Pair<CoinJoinTransactionInput, TransactionOutput>> vecPSInOutPairs) {

        if (txMyCollateral.getInputs().isEmpty()) {
            log.info("coinjoin: CoinJoin collateral not set");
            return false;
        }

        // we should already be connected to a Masternode
        if (sessionID.get() == 0) {
            log.info("coinjoin: No Masternode has been selected yet.");
            unlockCoins();
            keyHolderStorage.returnAll();
            lock.lock();
            try {
                setNull();
            } finally {
                lock.unlock();
            }
            return false;
        }

        setState(POOL_STATE_ACCEPTING_ENTRIES);
        strLastMessage = "";

        log.info("coinjoin: Added transaction to pool.");

        Transaction tx = new Transaction(context.getParams()); // for debug purposes only
        ArrayList<CoinJoinTransactionInput> vecTxDSInTmp = Lists.newArrayList();
        ArrayList<TransactionOutput> vecTxOutTmp = Lists.newArrayList();

        for (Pair<CoinJoinTransactionInput, TransactionOutput> pair : vecPSInOutPairs) {
            vecTxDSInTmp.add(pair.getFirst());
            vecTxOutTmp.add(pair.getSecond());
            tx.addInput(pair.getFirst());
            tx.addOutput(pair.getSecond());
        }

        log.info(COINJOIN_EXTRA, "coinjoin:  Submitting partial tx {} to #{}", tx, sessionID.get()); /* Continued */

        // store our entry for later use
        lock.lock();
        try {
            CoinJoinEntry entry = new CoinJoinEntry(context.getParams(), vecTxDSInTmp, vecTxOutTmp, txMyCollateral);
            entries.add(entry);
            relay(entry);
            timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
        } finally {
            lock.unlock();
        }

        return true;
    }

    private void relay(CoinJoinEntry entry) {
        if (mixingMasternode != null) {
            log.info("Sending {} to {}", entry.toString(true), sessionID);
           if(!coinJoinManager.forPeer(mixingMasternode.getService(), new MasternodeGroup.ForPeer() {
               @Override
               public boolean process(Peer peer) {
                   peer.sendMessage(entry);
                   return true;
               }
           }, true)) {
               log.info("coinjoin: failed to send to {} CoinJoinEntry: {}", mixingMasternode.getService().getSocketAddress(), entry);
           }
        }
    }

    /// Process Masternode updates about the progress of mixing
    private void processPoolStateUpdate(Peer peer, CoinJoinStatusUpdate statusUpdate) {
        log.info("status update received: {} from {}", statusUpdate, peer.getAddress().getSocketAddress());
        // do not update state when mixing client state is one of these
        if (state.get() == POOL_STATE_IDLE || state.get() == POOL_STATE_ERROR) return;

        if (statusUpdate.getState().value < POOL_STATE_MIN.value || statusUpdate.getState().value > POOL_STATE_MAX.value) {
            log.info("coinjoin session: statusUpdate.state is out of bounds: {}", statusUpdate.getState());
            return;
        }

        if (statusUpdate.getMessageID().value < MSG_POOL_MIN.value || statusUpdate.getMessageID().value > MSG_POOL_MAX.value) {
            log.info("coinjoin session: statusUpdate.nMessageID is out of bounds: {}", statusUpdate.getMessageID());
            return;
        }

        String strMessageTmp = CoinJoin.getMessageByID(statusUpdate.getMessageID());
        strAutoDenomResult = "Masternode:" + " " + strMessageTmp;

        switch (statusUpdate.getStatusUpdate()) {
            case STATUS_REJECTED: {
                log.error("coinjoin session: rejected by Masternode {}: {}", peer.getAddress().getSocketAddress(), strMessageTmp);
                setState(POOL_STATE_ERROR);
                unlockCoins();
                keyHolderStorage.returnAll();
                switch (statusUpdate.getMessageID()) {
                    case ERR_INVALID_COLLATERAL:
                        log.error("coinjoin: collateral invalid: {}", CoinJoin.isCollateralValid(txMyCollateral));
                        isMyCollateralValid = false;
                        setNull(); // for now lets disconnect.  TODO: Why is the collateral invalid?
                        break;
                    default:
                        log.warn("coinjoin: rejected for other reasons");
                        break;
                }
                timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
                strLastMessage = strMessageTmp;
                break;
            }
            case STATUS_ACCEPTED: {
                if (state.get() == statusUpdate.getState() && statusUpdate.getState() == POOL_STATE_QUEUE && sessionID.get() == 0 && statusUpdate.getSessionID() != 0) {
                    // new session id should be set only in POOL_STATE_QUEUE state
                    sessionID.set(statusUpdate.getSessionID());
                    collateralSessionMap.put(txMyCollateral.getTxId(), statusUpdate.getSessionID());
                    timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
                    strMessageTmp = strMessageTmp + " Set SID to " + sessionID.get();
                    queueSessionStartedListeners(MSG_SUCCESS);
                }
                log.info("coinjoin session: accepted by MN: {}", strMessageTmp);
                break;
            }
            default: {
                log.info("coinjoin session: statusUpdate.statusUpdate is out of bounds: {}", statusUpdate.getStatusUpdate());
                break;
            }
        }
    }
    // Set the 'state' value, with some logging and capturing when the state changed
    private void setState(PoolState state) {
        this.state.set(state);
    }

    private void completedTransaction(PoolMessage messageID) {
        if (messageID == MSG_SUCCESS) {
            log.info("coinjoin: completedTransaction -- success");
            queueSessionCompleteListeners(getState(), MSG_SUCCESS);
            coinJoinManager.coinJoinClientManagers.get(mixingWallet.getDescription()).updatedSuccessBlock();
            keyHolderStorage.keepAll();
        } else {
            log.info("coinjoin: completedTransaction -- error");
            keyHolderStorage.returnAll();
        }
        unlockCoins();
        lock.lock();
        try {
            setNull(); // this will also disconnect the masternode
            setStatus(PoolStatus.COMPLETE);
        } finally {
            lock.unlock();
        }
        strLastMessage = CoinJoin.getMessageByID(messageID);
    }

    /// As a client, check and sign the final transaction
    private boolean signFinalTransaction(Transaction finalTransactionNew, Peer node) {

        if (!CoinJoinClientOptions.isEnabled())
            return false;

        if (mixingMasternode == null)
            return false;

        lock.lock();
        try {

            finalMutableTransaction = new Transaction(finalTransactionNew.getParams());//finalTransactionNew;
            finalMutableTransaction.setVersionAndType(finalTransactionNew.getVersionShort(), finalTransactionNew.getType());
            finalMutableTransaction.setLockTime(finalTransactionNew.getLockTime());
            // we need to connect inputs
            for (TransactionInput input : finalTransactionNew.getInputs()) {
                Transaction tx = mixingWallet.getTransaction(input.getOutpoint().getHash());
                if (tx != null) {
                    // this is our input, so connect to our transaction
                    TransactionOutPoint outPoint = new TransactionOutPoint(input.getParams(), input.getOutpoint().getIndex(), tx);
                    byte[] pubKeyHash = ScriptPattern.extractHashFromP2PKH(tx.getOutput(outPoint.getIndex()).getScriptPubKey());
                    ECKey key = mixingWallet.findKeyFromPubKeyHash(pubKeyHash, Script.ScriptType.P2PKH);
                    if (key != null) {
                        Script inputScript = ScriptBuilder.createInputScript(null, key);
                        TransactionInput connectedInput = new TransactionInput(input.getParams(), finalMutableTransaction, inputScript.getProgram(), outPoint);
                        finalMutableTransaction.addInput(connectedInput);
                        continue; // go to the next input
                    }
                }
                // this is not our input
                finalMutableTransaction.addInput(input);
            }
            for (TransactionOutput output : finalTransactionNew.getOutputs()) {
                finalMutableTransaction.addOutput(output);
            }
            log.info(COINJOIN_EXTRA, "coinjoin:  finalMutableTx={}", finalMutableTransaction); /* Continued */

            // STEP 1: check final transaction general rules

            // Make sure it's BIP69 compliant
            // TODO: do these actually work?, do we have a unit test?
            finalMutableTransaction.sortInputs();
            finalMutableTransaction.sortOutputs();

            if (!finalMutableTransaction.getTxId().equals(finalTransactionNew.getTxId())) {
                log.info(COINJOIN_EXTRA, "coinjoin:  ERROR! Masternode {} is not BIP69 compliant!", mixingMasternode.getProTxHash());
                //unlockCoins();
                //keyHolderStorage.returnAll();
                //setNull();
                //return false;
            }

            // Make sure all inputs/outputs are valid
            ValidInOuts validResult = isValidInOuts(finalMutableTransaction.getInputs(), finalMutableTransaction.getOutputs());
            if (!validResult.result) {
                log.info("coinjoin:  ERROR! isValidInOuts() failed: {}", CoinJoin.getMessageByID(validResult.messageId));
                unlockCoins();
                keyHolderStorage.returnAll();
                setNull();
                return false;
            }

            // STEP 2: make sure our own inputs/outputs are present, otherwise refuse to sign

            ArrayList<TransactionInput> sigs = Lists.newArrayList();

            for (CoinJoinEntry entry: entries){
                // Check that the final transaction has all our outputs
                for (TransactionOutput txout : entry.getMixingOutputs()) {
                    boolean found = false;
                    for (TransactionOutput output : finalMutableTransaction.getOutputs()) {
                        found = txout.getValue().equals(output.getValue())
                                && Arrays.equals(txout.getScriptBytes(), output.getScriptBytes());
                        if (found) {
                            break;
                        }
                    }
                    if (!found) {
                        // Something went wrong and we'll refuse to sign. It's possible we'll be charged collateral. But that's
                        // better than signing if the transaction doesn't look like what we wanted.
                        log.info("coinjoin: an output is missing, refusing to sign! txout={}", txout);
                        unlockCoins();
                        keyHolderStorage.returnAll();
                        setNull();
                        return false;
                    }
                }

                for (CoinJoinTransactionInput txdsin : entry.getMixingInputs()){
                    /* Sign my transaction and all outputs */
                    int nMyInputIndex = -1;
                    //Script prevPubKey = null;

                    for (int i = 0; i < finalMutableTransaction.getInputs().size(); ++i){
                        // cppcheck-suppress useStlAlgorithm
                        if (finalMutableTransaction.getInput(i).equalsWithoutParent(txdsin)) {
                            nMyInputIndex = i;
                            //prevPubKey = txdsin.getPrevPubKey();
                            break;
                        }
                    }

                    if (nMyInputIndex == -1) {
                        // Can't find one of my own inputs, refuse to sign. It's possible we'll be charged collateral. But that's
                        // better than signing if the transaction doesn't look like what we wanted.
                        log.info("coinjoin: missing input! txdsin={}\n", txdsin);
                        unlockCoins();
                        keyHolderStorage.returnAll();
                        setNull();
                        return false;
                    }

                    sigs.add(finalMutableTransaction.getInput(nMyInputIndex));
                    log.info("coinjoin: myInputIndex: {}, sigs.size: {}, scriptSig={}",
                            nMyInputIndex, sigs.size(), finalMutableTransaction.getInput(nMyInputIndex).getScriptSig());
                }
            }

            TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(finalMutableTransaction);
            CoinJoinTransactionSigner signer = new CoinJoinTransactionSigner(coinJoinManager, sigs, true);

            if (!signer.signInputs(proposal, mixingWallet))
                log.info("{} returned false for the tx", signer.getClass().getName());


            if (sigs.isEmpty()) {
                log.info("coinjoin:  can't sign anything!");
                unlockCoins();
                keyHolderStorage.returnAll();
                setNull();
                return false;
            }

            // push all of our signatures to the Masternode
            log.info("coinjoin:  pushing sigs to the masternode, finalMutableTx={}", finalMutableTransaction); /* Continued */
            node.sendMessage(new CoinJoinSignedInputs(mixingWallet.getContext().getParams(), sigs));
            setState(POOL_STATE_SIGNING);
            timeLastSuccessfulStep.set(Utils.currentTimeSeconds());

            return true;
        } finally {
            lock.unlock();
        }
    }

    @GuardedBy("lock")
    protected void setNull() {
        // Client side
        if (mixingMasternode != null) {
            if (coinJoinManager.isMasternodeOrDisconnectRequested(mixingMasternode.getService())) {
                if (!coinJoinManager.disconnectMasternode(mixingMasternode)) {
                    log.info("not closing existing masternode: {}", mixingMasternode.getService().getSocketAddress());
                }
            } else {
                log.info("not closing masternode since it is not found: {}", mixingMasternode.getService().getSocketAddress());
            }
        }
        mixingMasternode = null;
        pendingDsaRequest = null;
        log.info("session zeroed out {}; {}", state, status);
        super.setNull();
    }

    // internal session id
    static int nextId = 0;
    private final int id;

    public int getId() {
        return id;
    }

    public CoinJoinClientSession(WalletEx mixingWallet, CoinJoinManager coinJoinManager,
                                 SimplifiedMasternodeListManager masternodeListManager,
                                 MasternodeMetaDataManager masternodeMetaDataManager, MasternodeSync masternodeSync) {
        super(mixingWallet.getContext());
        this.coinJoinManager = coinJoinManager;
        this.masternodeListManager = masternodeListManager;
        this.masternodeMetaDataManager = masternodeMetaDataManager;
        this.masternodeSync = masternodeSync;
        this.mixingWallet = mixingWallet;
        this.keyHolderStorage = new KeyHolderStorage();
        this.txMyCollateral = new Transaction(context.getParams());
        this.id = nextId++;
    }

    public void processMessage(Peer peer, Message message, boolean enable_bip61) {
        if (message instanceof CoinJoinStatusUpdate) {
            processStatusUpdate(peer, (CoinJoinStatusUpdate) message);
        } else if (message instanceof CoinJoinFinalTransaction) {
            processFinalTransaction(peer, (CoinJoinFinalTransaction) message);
        } else if (message instanceof CoinJoinComplete) {
            processComplete(peer, (CoinJoinComplete) message);
        }
    }

    private void processStatusUpdate(Peer peer, CoinJoinStatusUpdate statusUpdate) {
        if (mixingMasternode == null) {
            log.info(COINJOIN_EXTRA, "mixingMaster node is null, ignoring status update");
            return;
        }
        if (!mixingMasternode.getService().getAddr().equals(peer.getAddress().getAddr()))
            return;

        processPoolStateUpdate(peer, statusUpdate);
    }

    private void processFinalTransaction(Peer peer, CoinJoinFinalTransaction finalTransaction) {
        if (mixingMasternode == null)
            return;
        if (!mixingMasternode.getService().getAddr().equals(peer.getAddress().getAddr()))
            return;

        if (sessionID.get() != finalTransaction.getMsgSessionID()) {
            log.info("DSFINALTX: message doesn't match current CoinJoin session: #{}  msgSessionID: {}",
                    sessionID, finalTransaction.getMsgSessionID());
            return;
        }

        log.info(COINJOIN_EXTRA, "DSFINALTX: txNew {}", finalTransaction.getTransaction()); /* Continued */

        // check to see if input is spent already? (and probably not confirmed)
        signFinalTransaction(finalTransaction.getTransaction(), peer);
    }

    private void processComplete(Peer peer, CoinJoinComplete completeMessage) {
        if (mixingMasternode == null)
            return;
        if (!mixingMasternode.getService().getAddr().equals(peer.getAddress().getAddr()))
            return;

        if (completeMessage.getMsgMessageID().value < MSG_POOL_MIN.value || completeMessage.getMsgMessageID().value > MSG_POOL_MAX.value) {
            log.info("DSCOMPLETE: msg is out of bounds: {}", completeMessage.getMsgMessageID());
            return;
        }

        if (sessionID.get() != completeMessage.getMsgSessionID()) {
            log.info("DSCOMPLETE: message doesn't match current CoinJoin session: #{}  msg: {} ({})",
                    sessionID.get(), completeMessage.getMsgSessionID(),
                    CoinJoin.getMessageByID(completeMessage.getMsgMessageID()));
            return;
        }

        log.info("DSCOMPLETE: #{} msg {} ({})", completeMessage.getMsgSessionID(),
                completeMessage.getMsgMessageID(), CoinJoin.getMessageByID(completeMessage.getMsgMessageID()));

        completedTransaction(completeMessage.getMsgMessageID());
    }

    public void unlockCoins() {
        if (!CoinJoinClientOptions.isEnabled())
            return;

        // TODO: should we wait here? check Dash Core code

        for (TransactionOutPoint outpoint : outPointLocked)
            mixingWallet.unlockCoin(outpoint);

        outPointLocked.clear();
    }

    public void resetPool() {
        txMyCollateral = new Transaction(mixingWallet.getParams());
        unlockCoins();
        keyHolderStorage.returnAll();
        setNull();
    }

    private static int nStatusMessageProgress = 0;

    public String getStatus(boolean fWaitForBlock) {
        nStatusMessageProgress += 10;
        String strSuffix;

        if (fWaitForBlock || (masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) && !masternodeSync.isBlockchainSynced())) {
            return strAutoDenomResult;
        }

        switch (state.get()) {
            case POOL_STATE_IDLE:
                return "CoinJoin is idle.";
            case POOL_STATE_QUEUE:
                if (nStatusMessageProgress % 70 <= 30)
                    strSuffix = ".";
                else if (nStatusMessageProgress % 70 <= 50)
                    strSuffix = "..";
                else
                    strSuffix = "...";
                return String.format("Submitted to masternode, waiting in queue %s", strSuffix);
            case POOL_STATE_ACCEPTING_ENTRIES:
                return strAutoDenomResult;
            case POOL_STATE_SIGNING:
                if (nStatusMessageProgress % 70 <= 40)
                    return "Found enough users, signing ...";
                else if (nStatusMessageProgress % 70 <= 50)
                    strSuffix = ".";
                else if (nStatusMessageProgress % 70 <= 60)
                    strSuffix = "..";
                else
                    strSuffix = "...";
                return String.format("Found enough users, signing ( waiting %s )", strSuffix);
            case POOL_STATE_ERROR:
                return "CoinJoin request incomplete: " + strLastMessage + " Will retry...";
            default:
                return String.format("Unknown state: id = %s", state.get());
        }
    }

    public Masternode getMixingMasternodeInfo() {
        return mixingMasternode;
    }

    /// Passively run mixing in the background according to the configuration in settings
    public boolean doAutomaticDenominating() {
        return doAutomaticDenominating(false);
    }
    public boolean doAutomaticDenominating(boolean fDryRun) {
        if (state.get() != POOL_STATE_IDLE) return false;

        if (masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) && !masternodeSync.isBlockchainSynced()) {
            strAutoDenomResult = "Can't mix while sync in progress.";
            return false;
        }

        if (!CoinJoinClientOptions.isEnabled()) return false;

        Coin balanceNeedsAnonymized;

        if (!fDryRun && mixingWallet.isEncrypted() && coinJoinManager.requestKeyParameter(mixingWallet) == null) {
            setStatus(PoolStatus.ERR_WALLET_LOCKED);
            return false;
        }

        if (getEntriesCount() > 0) {
            if (!fDryRun)
                setStatus(PoolStatus.MIXING);
            return false;
        }

        boolean hasLock = lock.tryLock();
        if (!hasLock) {
            strAutoDenomResult = "Lock is already in place.";
            return false;
        }
        try {
            if (!fDryRun && masternodeListManager.getListAtChainTip().getValidMNsCount() == 0 &&
                    !mixingWallet.getContext().getParams().getId().equals(NetworkParameters.ID_REGTEST)) {
                strAutoDenomResult = "No Masternodes detected.";
                log.info("coinjoin: {}", strAutoDenomResult);
                setStatus(PoolStatus.ERR_NO_MASTERNODES_DETECTED);
                queueSessionCompleteListeners(getState(), ERR_SESSION);
                return false;
            }

            Balance bal = mixingWallet.getBalanceInfo();

            // check if there is anything left to do
            Coin nBalanceAnonymized = bal.getAnonymized();
            balanceNeedsAnonymized = CoinJoinClientOptions.getAmount().subtract(nBalanceAnonymized);

            if (balanceNeedsAnonymized.isLessThanOrEqualTo(Coin.ZERO)) {
                log.info("coinjoin: Nothing to do {}", fDryRun);
                // nothing to do, just keep it in idle mode
                if (!fDryRun) {
                    setStatus(PoolStatus.FINISHED);
                }
                return false;
            }
            hasNothingToDo.set(false);

            Coin nValueMin = CoinJoin.getSmallestDenomination();

            // if there are no confirmed DS collateral inputs yet
            if (!mixingWallet.hasCollateralInputs()) {
                // should have some additional amount for them
                nValueMin = nValueMin.add(CoinJoin.getMaxCollateralAmount());
            }

            // including denoms but applying some restrictions
            Coin nBalanceAnonymizable = mixingWallet.getAnonymizableBalance();

            // mixable balance is way too small
            if (nBalanceAnonymizable.isLessThan(nValueMin)) {
                //TODO: let us leave the previous code in this block for now
                // we don't yet have a way to precalculate if we have funds that can be anonomized
                // reliably
                //setStatus(PoolStatus.ERR_NOT_ENOUGH_FUNDS);
                //queueSessionCompleteListeners(getState(), ERR_SESSION);
                //return false;
                Coin balanceLeftToMix = mixingWallet.getAnonymizableBalance(false, false);
                if (!fDryRun && !balanceLeftToMix.isGreaterThanOrEqualTo(nValueMin)) {
                    setStatus(PoolStatus.ERR_NOT_ENOUGH_FUNDS);
                    queueSessionCompleteListeners(getState(), ERR_SESSION);
                }
                log.info("coinjoin: balance too low: dryRun: {}", fDryRun);
                return false;
            }

            // excluding denoms
            Coin nBalanceAnonimizableNonDenom = mixingWallet.getAnonymizableBalance(true);
            // denoms
            Coin nBalanceDenominatedConf = bal.getDenominatedTrusted();
            Coin nBalanceDenominatedUnconf = bal.getDenominatedUntrustedPending();
            Coin nBalanceDenominated = nBalanceDenominatedConf.add(nBalanceDenominatedUnconf);
            Coin nBalanceToDenominate = CoinJoinClientOptions.getAmount().subtract(nBalanceDenominated);

            // adjust balanceNeedsAnonymized to consume final denom
            if (nBalanceDenominated.subtract(nBalanceAnonymized).isGreaterThan(balanceNeedsAnonymized)) {
                List<Coin> denoms = CoinJoinClientOptions.getDenominations();
                Coin nAdditionalDenom = Coin.ZERO;
                for (Coin denom :denoms){
                    if (balanceNeedsAnonymized.isLessThan(denom)) {
                        nAdditionalDenom = denom;
                    } else {
                        break;
                    }
                }
                balanceNeedsAnonymized = balanceNeedsAnonymized.add(nAdditionalDenom);
            }

            log.info(COINJOIN_EXTRA, "coinjoin: wallet stats:\n{}", bal);

            log.info(COINJOIN_EXTRA, "coinjoin: current stats:\n" +
                    "    min: {}\n" +
                    "    myTrusted: {}\n" +
                    "    balanceAnonymizable: {}\n" +
                    "    balanceAnonymized: {}\n" +
                    "    balanceNeedsAnonymized: {}\n" +
                    "    balanceAnonimizableNonDenom: {}\n" +
                    "    balanceDenominatedConf: {}\n" +
                    "    balanceDenominatedUnconf: {}\n" +
                    "    balanceDenominated: {}\n" +
                    "    balanceToDenominate: {}\n",
                    nValueMin.toFriendlyString(),
                    bal.getMyTrusted().toFriendlyString(),
                    nBalanceAnonymizable.toFriendlyString(),
                    nBalanceAnonymized.toFriendlyString(),
                    balanceNeedsAnonymized.toFriendlyString(),
                    nBalanceAnonimizableNonDenom.toFriendlyString(),
                    nBalanceDenominatedConf.toFriendlyString(),
                    nBalanceDenominatedUnconf.toFriendlyString(),
                    nBalanceDenominated.toFriendlyString(),
                    nBalanceToDenominate.toFriendlyString()
            );

            // Check if we have should create more denominated inputs i.e.
            // there are funds to denominate and denominated balance does not exceed
            // max amount to mix yet.
            lastCreateDenominatedResult = true;
            if (nBalanceAnonimizableNonDenom.isGreaterThanOrEqualTo(nValueMin.add(CoinJoin.getCollateralAmount())) && nBalanceToDenominate.isGreaterThan(Coin.ZERO)) {
                lastCreateDenominatedResult = createDenominated(nBalanceToDenominate, fDryRun);
            }

            if (fDryRun) {
                log.info("coinjoin: create denominations {}, dryRun={}", lastCreateDenominatedResult, fDryRun);
                return lastCreateDenominatedResult;
            }

            //check if we have the collateral sized inputs
            if (!mixingWallet.hasCollateralInputs()) {
                return !mixingWallet.hasCollateralInputs(false) && makeCollateralAmounts();
            }

            if (sessionID.get() != 0) {
                setStatus(PoolStatus.MIXING);
                return false;
            }

            // Initial phase, find a Masternode
            // Clean if there is anything left from previous session
            unlockCoins();
            keyHolderStorage.returnAll();
            setNull();

            // should be no unconfirmed denoms in non-multi-session mode
            if (!CoinJoinClientOptions.isMultiSessionEnabled() && nBalanceDenominatedUnconf.isGreaterThan(Coin.ZERO)){
                strAutoDenomResult = "Found unconfirmed denominated outputs, will wait till they confirm to continue.";
                log.info("coinjoin: {}", strAutoDenomResult);
                return false;
            }

            //check our collateral and create new if needed
            StringBuilder strReason = new StringBuilder();
            if (txMyCollateral.isEmpty()) {
                if (!createCollateralTransaction(txMyCollateral, strReason)) {
                    log.info("coinjoin: create collateral error: {}", strReason);
                    return false;
                }
            } else {
                if (!isMyCollateralValid || !CoinJoin.isCollateralValid(txMyCollateral)) {
                    log.info("coinjoin: invalid collateral, recreating... [id: {}] ", id);
                    TransactionOutput output = txMyCollateral.getOutput(0);
                    if (ScriptPattern.isP2PKH(output.getScriptPubKey())) {
                        mixingWallet.getCoinJoin().addUnusedKey(KeyId.fromBytes(ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey()), false));
                    }
                    if (!createCollateralTransaction(txMyCollateral, strReason)) {
                        log.info("coinjoin: create collateral error: {}", strReason);
                        return false;
                    }
                }
            }
            // lock the funds we're going to use for our collateral
            for (TransactionInput txin :txMyCollateral.getInputs()){
                mixingWallet.lockCoin(txin.getOutpoint());
                outPointLocked.add(txin.getOutpoint());
            }
        } finally {
            lock.unlock();
        }

        // Always attempt to join an existing queue
        if (joinExistingQueue(balanceNeedsAnonymized)) {
            return true;
        }

        // If we were unable to find/join an existing queue then start a new one.
        if (startNewQueue(balanceNeedsAnonymized)) {
            return true;
        }

        setStatus(PoolStatus.WARN_NO_COMPATIBLE_MASTERNODE);
        return false;
    }

    private void setStatus(PoolStatus poolStatus) {
        strAutoDenomResult = CoinJoin.getStatusById(poolStatus);
        if (poolStatus.isError())
            log.error("coinjoin: {}", strAutoDenomResult);
        else if (poolStatus.isWarning())
            log.warn("coinjoin: {}", strAutoDenomResult);
        else
            log.info("coinjoin: {}", strAutoDenomResult);

        status.set(poolStatus);
        if (poolStatus.shouldStop()) {
            log.info("Session has nothing to do: {}", poolStatus);
            if (poolStatus.isError())
                log.error("Session has an error: {}", poolStatus);
            hasNothingToDo.set(true);
        }
    }

    /// As a client, submit part of a future mixing transaction to a Masternode to start the process
    public boolean submitDenominate() {
        StringBuilder strError = new StringBuilder();
        ArrayList<CoinJoinTransactionInput> vecTxDSIn = Lists.newArrayList();
        ArrayList<Pair<CoinJoinTransactionInput, TransactionOutput>> vecPSInOutPairsTmp = Lists.newArrayList();

        if (!selectDenominate(strError, vecTxDSIn)) {
           log.info("coinjoin:  SelectDenominate failed, error: {}", strError);
            return false;
        }

        ArrayList<Pair<Integer, Integer>> vecInputsByRounds = Lists.newArrayList();

        for (int i = 0; i < CoinJoinClientOptions.getRounds() + CoinJoinClientOptions.getRandomRounds(); ++i) {
            if (prepareDenominate(i, i, strError, vecTxDSIn, vecPSInOutPairsTmp, true)) {
               log.info("coinjoin: Running CoinJoin denominate for {} rounds, success", i);
                vecInputsByRounds.add(new Pair<>(i, vecPSInOutPairsTmp.size()));
            } else {
               log.info(COINJOIN_EXTRA, "coinjoin: Running CoinJoin denominate for {} rounds, error: {}", i, strError);
            }
        }

        // more inputs first, for equal input count prefer the one with fewer rounds
        vecInputsByRounds.sort((a, b) -> {
            int inputCompare = Integer.compare(b.getSecond(), a.getSecond());
            return inputCompare != 0 ? inputCompare : Integer.compare(a.getFirst(), b.getFirst());
        });

        log.info("coinjoin: vecInputsByRounds(size={}) for denom {}", vecInputsByRounds.size(), sessionDenom);
        for (Pair<Integer, Integer> pair : vecInputsByRounds) {
            log.info(COINJOIN_EXTRA, "coinjoin: vecInputsByRounds: rounds: {}, inputs: {}", pair.getFirst(), pair.getSecond());
        }

        int nRounds = vecInputsByRounds.get(0).getFirst();
        if (prepareDenominate(nRounds, nRounds, strError, vecTxDSIn, vecPSInOutPairsTmp)) {
            log.info("coinjoin: Running CoinJoin denominate for {} rounds, success", nRounds);
            return sendDenominate(vecPSInOutPairsTmp);
        }

        // We failed? That's strange but let's just make final attempt and try to mix everything
        if (prepareDenominate(0, CoinJoinClientOptions.getRounds() - 1, strError, vecTxDSIn, vecPSInOutPairsTmp)) {
            log.info("coinjoin: Running CoinJoin denominate for all rounds, success\n");
            return sendDenominate(vecPSInOutPairsTmp);
        }

        // Should never actually get here but just in case
        log.info("coinjoin: Running CoinJoin denominate for all rounds, error: {}", strError);
        strAutoDenomResult = strError.toString();
        return false;
    }

    public boolean processPendingDsaRequest() {
        if (pendingDsaRequest == null)
            return false;

        // should be masternode peers
        boolean sentMessage = coinJoinManager.forPeer(pendingDsaRequest.getAddress(), new MasternodeGroup.ForPeer() {
            @Override
            public boolean process(Peer peer) {
                timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
                peer.sendMessage(pendingDsaRequest.getDsa());
                log.info(COINJOIN_EXTRA, "coinjoin: valid collateral before sending: {}", CoinJoin.isCollateralValid(pendingDsaRequest.getDsa().getTxCollateral()));
                log.info("sending {} to {}", pendingDsaRequest.getDsa(), peer);
                return true;
            }
        }, false);

        if (sentMessage) {
            pendingDsaRequest = null;
        } else if (pendingDsaRequest.isExpired()) {
            log.info("coinjoin: failed to connect to {}; reason: cannot find peer", pendingDsaRequest.getAddress());
            setStatus(PoolStatus.CONNECTION_TIMEOUT);
            queueSessionCompleteListeners(getState(), ERR_CONNECTION_TIMEOUT);
            setNull();
        }

        return sentMessage;
    }

    public boolean checkTimeout() {
        if (state.get() == POOL_STATE_IDLE)
            return false;

        if (state.get() == POOL_STATE_ERROR) {
            if (Utils.currentTimeSeconds() - timeLastSuccessfulStep.get() >= 10) {
                // reset after being in POOL_STATE_ERROR for 10 or more seconds
                log.info("coinjoin: resetting session {}", sessionID);
                setNull();

            }
            return false;
        }

        int nLagTime = 10; // give the server a few extra seconds before resetting.
        int nTimeout = (state.get() == POOL_STATE_SIGNING) ? COINJOIN_SIGNING_TIMEOUT : COINJOIN_QUEUE_TIMEOUT;
        boolean fTimeout = Utils.currentTimeSeconds() - timeLastSuccessfulStep.get() >= nTimeout + nLagTime;

        if (!fTimeout)
            return false;

        log.info("coinjoin: {} {} timed out ({})", (state.get() == POOL_STATE_SIGNING) ? "Signing at session" : "Session", sessionID.get(), nTimeout);

        queueSessionCompleteListeners(getState(), ERR_TIMEOUT);
        setState(POOL_STATE_ERROR);
        setStatus(PoolStatus.TIMEOUT);
        unlockCoins();
        keyHolderStorage.returnAll();
        timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
        strLastMessage = CoinJoin.getMessageByID(ERR_SESSION);

        return true;
    }

    @Override
    public String toString() {
        return "ClientSession{id=" + id +
                ", mixer=" + (mixingMasternode != null ? mixingMasternode.getService().getSocketAddress() : "none") +
                ", joined=" + joined +
                ", msg='" + strLastMessage + '\'' +
                ", dsa=" + pendingDsaRequest +
                ", entries=" + entries.size() +
                ", state=" + state +
                ", #" + sessionID +
                ", " + CoinJoin.denominationToString(sessionDenom) + "[" + sessionDenom + "]" +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CoinJoinClientSession that = (CoinJoinClientSession) o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    public boolean hasNothingToDo() {
        return hasNothingToDo.get();
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addSessionStartedListener(SessionStartedListener listener) {
        addSessionStartedListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addSessionStartedListener(Executor executor, SessionStartedListener listener) {
        // This is thread safe, so we don't need to take the lock.
        sessionStartedListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeSessionStartedListener(SessionStartedListener listener) {
        return ListenerRegistration.removeFromList(listener, sessionStartedListeners);
    }

    protected void queueSessionStartedListeners(PoolMessage message) {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<SessionStartedListener> registration : sessionStartedListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onSessionStarted(mixingWallet, getSessionID(), getSessionDenom(), message);
                }
            });
        }
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addSessionCompleteListener(SessionCompleteListener listener) {
        addSessionCompleteListener(Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addSessionCompleteListener(Executor executor, SessionCompleteListener listener) {
        // This is thread safe, so we don't need to take the lock.
        sessionCompleteListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeSessionCompleteListener(SessionCompleteListener listener) {
        return ListenerRegistration.removeFromList(listener, sessionCompleteListeners);
    }

    protected void queueSessionCompleteListeners(PoolState state, PoolMessage message) {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<SessionCompleteListener> registration : sessionCompleteListeners) {
            registration.executor.execute(() -> {
                MasternodeAddress address = mixingMasternode != null ? mixingMasternode.getService() : null;
                registration.listener.onSessionComplete(mixingWallet, getSessionID(), getSessionDenom(), state, message, address, joined);
            });
        }
    }

    public void addTransationListener(CoinJoinTransactionListener listener) {
        addTransationListener (Threading.USER_THREAD, listener);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addTransationListener(Executor executor, CoinJoinTransactionListener listener) {
        // This is thread safe, so we don't need to take the lock.
        transactionListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeTransactionListener(CoinJoinTransactionListener listener) {
        return ListenerRegistration.removeFromList(listener, transactionListeners);
    }

    protected void queueTransactionListeners(Transaction tx, CoinJoinTransactionType type) {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<CoinJoinTransactionListener> registration : transactionListeners) {
            registration.executor.execute(() -> registration.listener.onTransactionProcessed(tx, type, getSessionID()));
        }
    }

    /**
     *
     * @param tx Transaction used for some purpose
     * @param sessionId the session ID when the tx was used
     * @param type CoinJoinTransactionType of tx
     */

    protected void queueTransactionListeners(Transaction tx, int sessionId, CoinJoinTransactionType type) {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<CoinJoinTransactionListener> registration : transactionListeners) {
            registration.executor.execute(() -> registration.listener.onTransactionProcessed(tx, type, sessionId));
        }
    }
}
