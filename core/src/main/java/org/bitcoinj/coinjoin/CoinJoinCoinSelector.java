/*
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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.ZeroConfCoinSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class CoinJoinCoinSelector extends ZeroConfCoinSelector {
    private final Logger log = LoggerFactory.getLogger(CoinJoinCoinSelector.class);
    private final TransactionBag transactionBag;
    private final boolean onlyConfirmed;
    private final boolean useGreedyAlgorithm;

    public CoinJoinCoinSelector(TransactionBag transactionBag) {
        this(transactionBag, false, false);
    }
    public CoinJoinCoinSelector(TransactionBag transactionBag, boolean onlyConfirmed, boolean useGreedyAlgorithm) {
        checkArgument(transactionBag != null, "transactionBag cannot be null");
        this.transactionBag = transactionBag;
        this.onlyConfirmed = onlyConfirmed;
        this.useGreedyAlgorithm = useGreedyAlgorithm;
    }

    @Override
    public CoinSelection select(Coin target, List<TransactionOutput> candidates) {
        Stopwatch watch = Stopwatch.createStarted();
        ArrayList<TransactionOutput> selected = new ArrayList<>();
        // Sort the inputs by age*value so we get the highest "coindays" spent.
        // TODO: Consider changing the wallets internal format to track just outputs and keep them ordered.
        ArrayList<TransactionOutput> sortedOutputs = new ArrayList<>(candidates);
        // When calculating the wallet balance, we may be asked to select all possible coins, if so, avoid sorting
        // them in order to improve performance.
        // TODO: Take in network parameters when instantiated, and then test against the current network. Or just have a boolean parameter for "give me everything"
        if (!target.equals(NetworkParameters.MAX_MONEY)) {
            sortOutputs(sortedOutputs);
        }
        // Now iterate over the sorted outputs until we have got as close to the target as possible or a little
        // bit over (excessive value will be change).
        long total = 0;
        if (useGreedyAlgorithm) {
            for (TransactionOutput output : sortedOutputs) {
                // don't bother with shouldSelect, since we have to check all the outputs
                // with isCoinJoin anyways.
                if (output.isCoinJoin(transactionBag) && !transactionBag.isLockedOutput(output.getOutPointFor())) {
                    selected.add(output);
                    total += output.getValue().value;
                }
            }
            HashMap<Coin, ArrayList<TransactionOutput>> denomMap = Maps.newHashMap();
            selected.forEach(output ->
                denomMap.computeIfAbsent(output.getValue(), k -> new ArrayList<>()).add(output)
            );
            // randomly order each list
            denomMap.values().forEach(Collections::shuffle);

            // Fee calculation constant: 0.00001000 DASH per kB
            Coin feePerKb = Transaction.DEFAULT_TX_FEE;
            
            // Create a working copy of denomMap to avoid modifying the original
            HashMap<Coin, ArrayList<TransactionOutput>> workingDenomMap = new HashMap<>();
            denomMap.forEach((denom, outputs) -> workingDenomMap.put(denom, new ArrayList<>(outputs)));
            
            // Sort denominations from largest to smallest
            List<Coin> denoms = new ArrayList<>(workingDenomMap.keySet());
            denoms.sort(Comparator.reverseOrder());
            
            ArrayList<TransactionOutput> bestSelection = findBestCombination(workingDenomMap, denoms, target.value, feePerKb);
            
            if (bestSelection != null) {
                long bestTotal = bestSelection.stream().mapToLong(o -> o.getValue().value).sum();
                log.info("found candidates({}): {}, {}", useGreedyAlgorithm, watch, bestSelection.size());
                return new CoinSelection(Coin.valueOf(bestTotal), bestSelection);
            } else {
                // Fallback to all available outputs (use original denomMap, not depleted workingDenomMap)
                ArrayList<TransactionOutput> allOutputs = new ArrayList<>();
                denomMap.values().forEach(allOutputs::addAll);
                long bestTotal = allOutputs.stream().mapToLong(o -> o.getValue().value).sum();
                log.info("found candidates({}): {}, {}", useGreedyAlgorithm, watch, allOutputs.size());
                return new CoinSelection(Coin.valueOf(bestTotal), allOutputs);
            }
        } else {
            for (TransactionOutput output : sortedOutputs) {
                if (total >= target.value)
                    break;
                // don't bother with shouldSelect, since we have to check all the outputs
                // with isCoinJoin anyways.
                if (output.isCoinJoin(transactionBag) && !transactionBag.isLockedOutput(output.getOutPointFor())) {
                    selected.add(output);
                    total += output.getValue().value;
                }
            }
            log.info("found candidates({}): {}, {}", useGreedyAlgorithm, watch, selected.size());
            return new CoinSelection(Coin.valueOf(total), selected);
        }
    }

    private ArrayList<TransactionOutput> findBestCombination(HashMap<Coin, ArrayList<TransactionOutput>> workingDenomMap,
                                                           List<Coin> denomsDescending, long target, Coin feePerKb) {

        log.info("Target: " + Coin.valueOf(target).toFriendlyString());

        // Create ascending sorted list (reuse descending, just iterate backwards when needed)
        List<Coin> denomsAscending = new ArrayList<>(denomsDescending);
        Collections.reverse(denomsAscending);

        ArrayList<TransactionOutput> selection = new ArrayList<>();
        long selectionTotal = 0; // Track running total instead of repeated stream operations
        long remaining = target; // Start with just the target amount

        log.info("Starting greedy selection for target: " + Coin.valueOf(remaining).toFriendlyString());
        
        // Phase 1: Use largest denominations that are <= remaining amount
        for (Coin denom : denomsDescending) {
            if (remaining <= 0) break;
            
            ArrayList<TransactionOutput> availableOutputs = workingDenomMap.get(denom);
            if (availableOutputs == null || availableOutputs.isEmpty()) continue;
            
            // Only use this denomination if it's <= remaining amount
            if (denom.value <= remaining) {
                int neededCount = (int) (remaining / denom.value);
                int availableCount = availableOutputs.size();
                int useCount = Math.min(neededCount, availableCount);

                log.info("Phase 1 - Using {} of {} (available: {}, needed: {})", useCount, denom.toFriendlyString(), availableCount, neededCount);
                
                for (int i = 0; i < useCount; i++) {
                    selection.add(availableOutputs.remove(availableOutputs.size() - 1));
                    remaining -= denom.value;
                    selectionTotal += denom.value;
                }
            }
        }
        
        // Calculate fee based on actual transaction structure:
        // Transaction header: version(4) + input_count(1) + output_count(1) + locktime(4) = 10 bytes
        // Input: outpoint(36) + script_len(1) + script_sig(~107) + sequence(4) = ~148 bytes  
        // Output: value(8) + script_len(1) + script_pubkey(25) = 34 bytes
        // Based on your measurement: 1 input + 1 output = 193 bytes
        
        int numInputs = selection.size();
        
        // Transaction structure breakdown:
        // Header: 10 bytes (version + varint counts + locktime)
        // Each input: ~148 bytes (36 + 1 + ~107 + 4) for P2PKH signed input
        // Each output: 34 bytes (8 + 1 + 25) for P2PKH output
        
        int txSize;
        if (numInputs == 0) {
            // Minimum case: need at least 1 input
            txSize = 10 + 148 + 34; // header + 1_input + 1_output = 192 bytes (close to your 193)
        } else {
            // Formula based on actual structure
            txSize = 10 + (numInputs * 148) + 34; // header + inputs + 1_output
        }
        
        long calculatedFee = (feePerKb.value * txSize) / 1000;
        remaining += calculatedFee; // Add fee to remaining needed

        log.info("After Phase 1: {} inputs selected, fee needed: {}, still need: {}",
                selection.size(), Coin.valueOf(calculatedFee).toFriendlyString(), Coin.valueOf(remaining).toFriendlyString());
        
        // Phase 2: If we still need more (for fee), use smallest denominations first
        if (remaining > 0) {
            log.info("Phase 2 - Using smallest denominations first for fee coverage");

            for (Coin denom : denomsAscending) {
                if (remaining <= 0) break;
                
                ArrayList<TransactionOutput> availableOutputs = workingDenomMap.get(denom);
                if (availableOutputs == null || availableOutputs.isEmpty()) continue;
                
                // Use available denominations to cover remaining amount (fee)
                while (remaining > 0 && !availableOutputs.isEmpty()) {
                    log.info("Phase 2 - Using 1 of {} for remaining {}", denom.toFriendlyString(), Coin.valueOf(remaining).toFriendlyString());
                    selection.add(availableOutputs.remove(availableOutputs.size() - 1));
                    remaining -= denom.value;
                    selectionTotal += denom.value;
                    
                    // Recalculate fee as we add more inputs using the formula
                    numInputs = selection.size();
                    txSize = 10 + (numInputs * 148) + 34; // header + inputs + 1_output
                    long newFee = (feePerKb.value * txSize) / 1000;
                    long additionalFee = newFee - calculatedFee;
                    if (additionalFee > 0) {
                        remaining += additionalFee;
                        calculatedFee = newFee;
                        log.info("Fee increased to: {}", Coin.valueOf(calculatedFee).toFriendlyString());
                    }
                }
            }
            
            // Phase 3: If still need more, use exactly 1 of the next larger denomination
            if (remaining > 0) {
                log.info("Phase 3 - Small denoms insufficient, using 1 larger denomination...");
                
                for (Coin denom : denomsDescending) {
                    ArrayList<TransactionOutput> availableOutputs = workingDenomMap.get(denom);
                    if (availableOutputs == null || availableOutputs.isEmpty()) continue;

                    if (denom.value >= remaining) {
                        log.info("Using 1 of {} to cover remaining {}", denom.toFriendlyString(), Coin.valueOf(remaining).toFriendlyString());
                        selection.add(availableOutputs.remove(availableOutputs.size() - 1));
                        remaining -= denom.value;

                        // Recalculate fee as we add more inputs
                        numInputs = selection.size();
                        txSize = 10 + (numInputs * 148) + 34;
                        long newFee = (feePerKb.value * txSize) / 1000;
                        long additionalFee = newFee - calculatedFee;
                        if (additionalFee > 0) {
                            remaining += additionalFee;
                            calculatedFee = newFee;
                            log.info("Fee increased to: {}", Coin.valueOf(calculatedFee).toFriendlyString());
                        }

                        if (remaining <= 0) {
                            break;
                        }
                        // Continue loop if still need more after fee adjustment
                    }
                }
            }
        }

        // Phase 4: Optimize by consolidating 10x smaller denominations with 1x larger denomination
        log.info("Phase 4 - Optimizing denomination consolidation");
        
        // Create a copy of selection to test optimizations
        ArrayList<TransactionOutput> optimizedSelection = new ArrayList<>(selection);
        HashMap<Coin, ArrayList<TransactionOutput>> optimizedWorkingMap = new HashMap<>();
        workingDenomMap.forEach((denom, outputs) -> optimizedWorkingMap.put(denom, new ArrayList<>(outputs)));
        
        // Count outputs by denomination and calculate total in one pass
        HashMap<Coin, Integer> selectionCounts = new HashMap<>();
        long optimizedTotal = 0;
        for (TransactionOutput output : optimizedSelection) {
            selectionCounts.merge(output.getValue(), 1, Integer::sum);
            optimizedTotal += output.getValue().value;
        }

        // Use ascending order for consolidation (smallest to largest)
        for (int i = 0; i < denomsAscending.size() - 1; i++) {
            Coin smallDenom = denomsAscending.get(i);
            Coin largeDenom = denomsAscending.get(i + 1);

            // Check if we have 10+ of the small denomination and the large denom is exactly 10x
            if (largeDenom.value == smallDenom.value * 10) {
                int smallCount = selectionCounts.getOrDefault(smallDenom, 0);
                ArrayList<TransactionOutput> availableLarge = optimizedWorkingMap.get(largeDenom);

                if (smallCount >= 10 && availableLarge != null && !availableLarge.isEmpty()) {
                    log.info("Consolidating 10x{} with 1x{}", smallDenom.toFriendlyString(), largeDenom.toFriendlyString());

                    // Remove 10 small denominations from optimized selection
                    int removed = 0;
                    for (int j = optimizedSelection.size() - 1; j >= 0 && removed < 10; j--) {
                        if (optimizedSelection.get(j).getValue().equals(smallDenom)) {
                            optimizedSelection.remove(j);
                            removed++;
                        }
                    }

                    // Add 1 large denomination (total value unchanged: 10 * small = 1 * large)
                    optimizedSelection.add(availableLarge.remove(availableLarge.size() - 1));

                    // Update counts
                    selectionCounts.put(smallDenom, selectionCounts.get(smallDenom) - 10);
                    selectionCounts.put(largeDenom, selectionCounts.getOrDefault(largeDenom, 0) + 1);

                    // Recalculate fee with new input count (fewer inputs = lower fee)
                    int newNumInputs = optimizedSelection.size();
                    int newTxSize = 10 + (newNumInputs * 148) + 34;
                    long newCalculatedFee = (feePerKb.value * newTxSize) / 1000;

                    // Total value is unchanged, but fee is reduced
                    long optimizedRemaining = target + newCalculatedFee - optimizedTotal;

                    log.info("After consolidation: {} inputs, fee: {}, remaining: {}", newNumInputs,
                            Coin.valueOf(newCalculatedFee).toFriendlyString(), Coin.valueOf(optimizedRemaining).toFriendlyString());
                    
                    // If optimization is valid (we still have enough value), use it
                    if (optimizedRemaining <= 0) {
                        selection = optimizedSelection;
                        workingDenomMap.clear();
                        workingDenomMap.putAll(optimizedWorkingMap);
                        calculatedFee = newCalculatedFee;
                        log.info("Optimization successful - using consolidated selection");
                    } else {
                        log.info("Optimization failed - insufficient value, reverting");
                        // Revert the changes
                        selectionCounts.put(smallDenom, selectionCounts.get(smallDenom) + 10);
                        selectionCounts.put(largeDenom, selectionCounts.get(largeDenom) - 1);
                        break; // Stop trying further consolidations for this iteration
                    }
                }
            }
        }

        // Phase 5: Remove unnecessary smallest denomination inputs
        log.info("Phase 5 - Removing unnecessary smallest denomination inputs");

        // Build count map and calculate total in one pass
        HashMap<Coin, Integer> removalCounts = new HashMap<>();
        long currentTotal = 0;
        for (TransactionOutput output : selection) {
            removalCounts.merge(output.getValue(), 1, Integer::sum);
            currentTotal += output.getValue().value;
        }

        long totalNeeded = target + calculatedFee;
        long excess = currentTotal - totalNeeded;

        log.info("Current total: {}, needed: {}, excess: {}", Coin.valueOf(currentTotal).toFriendlyString(),
                Coin.valueOf(totalNeeded).toFriendlyString(), Coin.valueOf(excess).toFriendlyString());

        if (excess > 0) {
            // Try to remove smallest denominations that we don't need
            for (Coin denom : denomsAscending) {
                if (excess <= 0) break;

                int denomCount = removalCounts.getOrDefault(denom, 0);
                long canRemove = Math.min(excess / denom.value, denomCount);

                if (canRemove > 0) {
                    log.info("Removing {} unnecessary {} inputs", canRemove, denom.toFriendlyString());

                    // Remove the specified number of this denomination
                    long removed = 0;
                    for (int i = selection.size() - 1; i >= 0 && removed < canRemove; i--) {
                        if (selection.get(i).getValue().equals(denom)) {
                            selection.remove(i);
                            currentTotal -= denom.value;
                            excess -= denom.value;
                            removed++;
                        }
                    }
                    removalCounts.put(denom, (int) (denomCount - removed));

                    // Recalculate fee with new input count
                    int newNumInputs = selection.size();
                    int newTxSize = 10 + (newNumInputs * 148) + 34;
                    long newCalculatedFee = (feePerKb.value * newTxSize) / 1000;

                    long newTotalNeeded = target + newCalculatedFee;

                    log.info("After removing {} inputs: {} total inputs, fee: {}, total: {}, needed: {}", removed,
                            newNumInputs, Coin.valueOf(newCalculatedFee).toFriendlyString(),
                            Coin.valueOf(currentTotal).toFriendlyString(), Coin.valueOf(newTotalNeeded).toFriendlyString());

                    // Verify we still have enough (should always be true, but safety check)
                    if (currentTotal >= newTotalNeeded) {
                        calculatedFee = newCalculatedFee;
                        excess = currentTotal - newTotalNeeded;
                        log.info("Successfully removed unnecessary inputs");
                    } else {
                        log.info("ERROR: Removal created insufficient funds - this shouldn't happen");
                        break;
                    }
                }
            }

            long finalNeeded = target + calculatedFee;
            log.info("Phase 5 complete - Final: {} inputs, total: {}, needed: {}, excess: {}", selection.size(),
                    Coin.valueOf(currentTotal).toFriendlyString(), Coin.valueOf(finalNeeded).toFriendlyString(),
                    Coin.valueOf(currentTotal - finalNeeded).toFriendlyString());
        } else {
            log.info("No excess to remove - selection is optimal");
        }
        
        if (remaining > 0) {
            log.info("WARNING: Could not satisfy target + fee, remaining: {}", Coin.valueOf(remaining).toFriendlyString());
            return null;
        }

        log.info("Final selection: {} inputs, total fee: {}", selection.size(), Coin.valueOf(calculatedFee).toFriendlyString());
        
        return selection;
    }

    @Override
    protected boolean shouldSelect(Transaction tx) {
        if (tx != null) {
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.isCoinJoin(transactionBag)) {
                    TransactionConfidence confidence = tx.getConfidence();
                    if (onlyConfirmed)
                        return confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING;
                    return super.shouldSelect(tx);
                }
            }
            return false;
        }
        return true;
    }
}
