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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.ZeroConfCoinSelector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

public class CoinJoinCoinSelector extends ZeroConfCoinSelector {
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
        // candidates.forEach(System.out::println);
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
        // System.out.println("--------------------------");
        // selected.forEach(System.out::println);
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
            selected.forEach(output -> {
                if (denomMap.containsKey(output.getValue())) {
                    ArrayList<TransactionOutput> outputs = denomMap.get(output.getValue());
                    outputs.add(output);
                } else {
                    ArrayList<TransactionOutput> outputs = Lists.newArrayList(output);
                    denomMap.put(output.getValue(), outputs);
                }
            });
            // randomly order each list
            denomMap.forEach((denom, outputs) -> {
                Collections.shuffle(outputs);
            });

            // Fee calculation constant: 0.00001000 DASH per kB
            Coin feePerKb = Coin.valueOf(1000L); // 0.00001000 DASH = 1000 satoshis
            
            // Create a working copy of denomMap to avoid modifying the original
            HashMap<Coin, ArrayList<TransactionOutput>> workingDenomMap = new HashMap<>();
            denomMap.forEach((denom, outputs) -> workingDenomMap.put(denom, new ArrayList<>(outputs)));
            
            // Sort denominations from largest to smallest
            List<Coin> denoms = new ArrayList<>(workingDenomMap.keySet());
            denoms.sort(Comparator.reverseOrder());
            
            ArrayList<TransactionOutput> bestSelection = findBestCombination(workingDenomMap, denoms, target.value, feePerKb);
            
            if (bestSelection != null) {
                long bestTotal = bestSelection.stream().mapToLong(o -> o.getValue().value).sum();
                return new CoinSelection(Coin.valueOf(bestTotal), bestSelection);
            } else {
                // Fallback to all available outputs
                ArrayList<TransactionOutput> allOutputs = new ArrayList<>();
                workingDenomMap.values().forEach(allOutputs::addAll);
                long bestTotal = allOutputs.stream().mapToLong(o -> o.getValue().value).sum();
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
            return new CoinSelection(Coin.valueOf(total), selected);
        }
    }

    private ArrayList<TransactionOutput> findBestCombination(HashMap<Coin, ArrayList<TransactionOutput>> denomMap, 
                                                           List<Coin> denoms, long target, Coin feePerKb) {
        
        System.out.println("Target: " + Coin.valueOf(target).toFriendlyString());
        
        // Create working copies to avoid modifying original
        final HashMap<Coin, ArrayList<TransactionOutput>> workingDenomMap = new HashMap<>();
        denomMap.forEach((denom, outputs) -> workingDenomMap.put(denom, new ArrayList<>(outputs)));
        
        // Sort denominations largest to smallest
        denoms.sort(Comparator.reverseOrder());
        
        ArrayList<TransactionOutput> selection = new ArrayList<>();
        long remaining = target; // Start with just the target amount
        
        System.out.println("Starting greedy selection for target: " + Coin.valueOf(remaining).toFriendlyString());
        
        // Phase 1: Use largest denominations that are <= remaining amount
        for (Coin denom : denoms) {
            if (remaining <= 0) break;
            
            ArrayList<TransactionOutput> availableOutputs = workingDenomMap.get(denom);
            if (availableOutputs == null || availableOutputs.isEmpty()) continue;
            
            // Only use this denomination if it's <= remaining amount
            if (denom.value <= remaining) {
                int neededCount = (int) (remaining / denom.value);
                int availableCount = availableOutputs.size();
                int useCount = Math.min(neededCount, availableCount);
                
                System.out.println("Phase 1 - Using " + useCount + " of " + denom.toFriendlyString() + 
                                 " (available: " + availableCount + ", needed: " + neededCount + ")");
                
                for (int i = 0; i < useCount; i++) {
                    selection.add(availableOutputs.remove(0)); // Remove to avoid reuse
                    remaining -= denom.value;
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
        
        System.out.println("After Phase 1: " + selection.size() + " inputs selected, fee needed: " + 
                          Coin.valueOf(calculatedFee).toFriendlyString() + 
                          ", still need: " + Coin.valueOf(remaining).toFriendlyString());
        
        // Phase 2: If we still need more (for fee), use smallest denominations first
        if (remaining > 0) {
            // Sort denominations smallest to largest for efficient fee coverage
            List<Coin> suitableDenoms = denoms.stream()
                .sorted(Coin::compareTo)  // Smallest first
                .collect(Collectors.toList());
            
            System.out.println("Phase 2 - Using smallest denominations first for fee coverage");
            
            for (Coin denom : suitableDenoms) {
                if (remaining <= 0) break;
                
                ArrayList<TransactionOutput> availableOutputs = workingDenomMap.get(denom);
                if (availableOutputs == null || availableOutputs.isEmpty()) continue;
                
                // Use available denominations to cover remaining amount (fee)
                while (remaining > 0 && !availableOutputs.isEmpty()) {
                    System.out.println("Phase 2 - Using 1 of " + denom.toFriendlyString() + 
                                     " for remaining " + Coin.valueOf(remaining).toFriendlyString());
                    selection.add(availableOutputs.remove(0));
                    remaining -= denom.value;
                    
                    // Recalculate fee as we add more inputs using the formula
                    numInputs = selection.size();
                    txSize = 10 + (numInputs * 148) + 34; // header + inputs + 1_output
                    long newFee = (feePerKb.value * txSize) / 1000;
                    long additionalFee = newFee - calculatedFee;
                    if (additionalFee > 0) {
                        remaining += additionalFee;
                        calculatedFee = newFee;
                        System.out.println("Fee increased to: " + Coin.valueOf(calculatedFee).toFriendlyString());
                    }
                }
            }
            
            // Phase 3: If still need more, use exactly 1 of the next larger denomination
            if (remaining > 0) {
                System.out.println("Phase 3 - Small denoms insufficient, using 1 larger denomination...");
                
                for (Coin denom : denoms) {
                    ArrayList<TransactionOutput> availableOutputs = workingDenomMap.get(denom);
                    if (availableOutputs == null || availableOutputs.isEmpty()) continue;
                    
                    if (denom.value >= remaining) {
                        System.out.println("Using 1 of " + denom.toFriendlyString() + 
                                         " to cover remaining " + Coin.valueOf(remaining).toFriendlyString());
                        selection.add(availableOutputs.get(0));
                        remaining = 0;
                        break;
                    }
                }
            }
        }

        // Phase 4: Optimize by consolidating 10x smaller denominations with 1x larger denomination
        System.out.println("Phase 4 - Optimizing denomination consolidation");
        
        // Create a copy of selection to test optimizations
        ArrayList<TransactionOutput> optimizedSelection = new ArrayList<>(selection);
        HashMap<Coin, ArrayList<TransactionOutput>> optimizedWorkingMap = new HashMap<>();
        workingDenomMap.forEach((denom, outputs) -> optimizedWorkingMap.put(denom, new ArrayList<>(outputs)));
        
        // Add back any unused outputs from our selection to the working map for optimization
        HashMap<Coin, Integer> selectionCounts = new HashMap<>();
        for (TransactionOutput output : optimizedSelection) {
            Coin denom = output.getValue();
            selectionCounts.put(denom, selectionCounts.getOrDefault(denom, 0) + 1);
        }
        
        // Sort denominations smallest to largest for consolidation
        List<Coin> consolidationDenoms = denoms.stream()
            .sorted(Coin::compareTo)
            .collect(Collectors.toList());
        
        for (int i = 0; i < consolidationDenoms.size() - 1; i++) {
            Coin smallDenom = consolidationDenoms.get(i);
            Coin largeDenom = consolidationDenoms.get(i + 1);
            
            // Check if we have 10+ of the small denomination and the large denom is exactly 10x
            if (largeDenom.value == smallDenom.value * 10) {
                int smallCount = selectionCounts.getOrDefault(smallDenom, 0);
                ArrayList<TransactionOutput> availableLarge = optimizedWorkingMap.get(largeDenom);
                
                if (smallCount >= 10 && availableLarge != null && !availableLarge.isEmpty()) {
                    System.out.println("Consolidating 10x" + smallDenom.toFriendlyString() + 
                                     " with 1x" + largeDenom.toFriendlyString());
                    
                    // Remove 10 small denominations from optimized selection
                    int removed = 0;
                    for (int j = optimizedSelection.size() - 1; j >= 0 && removed < 10; j--) {
                        if (optimizedSelection.get(j).getValue().equals(smallDenom)) {
                            optimizedSelection.remove(j);
                            removed++;
                        }
                    }
                    
                    // Add 1 large denomination
                    optimizedSelection.add(availableLarge.remove(0));
                    
                    // Update counts
                    selectionCounts.put(smallDenom, selectionCounts.get(smallDenom) - 10);
                    selectionCounts.put(largeDenom, selectionCounts.getOrDefault(largeDenom, 0) + 1);
                    
                    // Recalculate fee with new input count
                    int newNumInputs = optimizedSelection.size();
                    int newTxSize = 10 + (newNumInputs * 148) + 34;
                    long newCalculatedFee = (feePerKb.value * newTxSize) / 1000;
                    
                    // Calculate total value of optimized selection
                    long optimizedTotal = optimizedSelection.stream().mapToLong(o -> o.getValue().value).sum();
                    long optimizedRemaining = target + newCalculatedFee - optimizedTotal;
                    
                    System.out.println("After consolidation: " + newNumInputs + " inputs, fee: " + 
                                     Coin.valueOf(newCalculatedFee).toFriendlyString() + 
                                     ", remaining: " + Coin.valueOf(optimizedRemaining).toFriendlyString());
                    
                    // If optimization is valid (we still have enough value), use it
                    if (optimizedRemaining <= 0) {
                        selection = optimizedSelection;
                        workingDenomMap.clear();
                        workingDenomMap.putAll(optimizedWorkingMap);
                        calculatedFee = newCalculatedFee;
                        System.out.println("Optimization successful - using consolidated selection");
                    } else {
                        System.out.println("Optimization failed - insufficient value, reverting");
                        // Revert the changes
                        selectionCounts.put(smallDenom, selectionCounts.get(smallDenom) + 10);
                        selectionCounts.put(largeDenom, selectionCounts.get(largeDenom) - 1);
                        break; // Stop trying further consolidations for this iteration
                    }
                }
            }
        }

        // Phase 5: Remove unnecessary smallest denomination inputs
        System.out.println("Phase 5 - Removing unnecessary smallest denomination inputs");
        
        // Calculate current total value and what we actually need
        long currentTotal = selection.stream().mapToLong(o -> o.getValue().value).sum();
        long totalNeeded = target + calculatedFee;
        long excess = currentTotal - totalNeeded;
        
        System.out.println("Current total: " + Coin.valueOf(currentTotal).toFriendlyString() + 
                          ", needed: " + Coin.valueOf(totalNeeded).toFriendlyString() + 
                          ", excess: " + Coin.valueOf(excess).toFriendlyString());
        
        if (excess > 0) {
            // Sort denominations smallest to largest to remove smallest first
            List<Coin> removalDenoms = denoms.stream()
                .sorted(Coin::compareTo)
                .collect(Collectors.toList());
            
            // Try to remove smallest denominations that we don't need
            for (Coin denom : removalDenoms) {
                if (excess <= 0) break;
                
                // Count how many of this denomination we can remove
                long canRemove = Math.min(excess / denom.value, 
                    selection.stream().mapToLong(o -> o.getValue().equals(denom) ? 1 : 0).sum());
                
                if (canRemove > 0) {
                    System.out.println("Removing " + canRemove + " unnecessary " + denom.toFriendlyString() + " inputs");
                    
                    // Remove the specified number of this denomination
                    long removed = 0;
                    for (int i = selection.size() - 1; i >= 0 && removed < canRemove; i--) {
                        if (selection.get(i).getValue().equals(denom)) {
                            selection.remove(i);
                            excess -= denom.value;
                            removed++;
                        }
                    }
                    
                    // Recalculate fee with new input count
                    int newNumInputs = selection.size();
                    int newTxSize = 10 + (newNumInputs * 148) + 34;
                    long newCalculatedFee = (feePerKb.value * newTxSize) / 1000;
                    
                    // Update totals
                    long newTotal = selection.stream().mapToLong(o -> o.getValue().value).sum();
                    long newTotalNeeded = target + newCalculatedFee;
                    
                    System.out.println("After removing " + removed + " inputs: " + newNumInputs + " total inputs, " +
                                     "fee: " + Coin.valueOf(newCalculatedFee).toFriendlyString() + 
                                     ", total: " + Coin.valueOf(newTotal).toFriendlyString() +
                                     ", needed: " + Coin.valueOf(newTotalNeeded).toFriendlyString());
                    
                    // Verify we still have enough (should always be true, but safety check)
                    if (newTotal >= newTotalNeeded) {
                        calculatedFee = newCalculatedFee;
                        excess = newTotal - newTotalNeeded;
                        System.out.println("Successfully removed unnecessary inputs");
                    } else {
                        System.out.println("ERROR: Removal created insufficient funds - this shouldn't happen");
                        break;
                    }
                }
            }
            
            // Final summary
            long finalTotal = selection.stream().mapToLong(o -> o.getValue().value).sum();
            long finalNeeded = target + calculatedFee;
            System.out.println("Phase 5 complete - Final: " + selection.size() + " inputs, " +
                             "total: " + Coin.valueOf(finalTotal).toFriendlyString() + 
                             ", needed: " + Coin.valueOf(finalNeeded).toFriendlyString() + 
                             ", excess: " + Coin.valueOf(finalTotal - finalNeeded).toFriendlyString());
        } else {
            System.out.println("No excess to remove - selection is optimal");
        }
        
        if (remaining > 0) {
            System.out.println("WARNING: Could not satisfy target + fee, remaining: " + 
                             Coin.valueOf(remaining).toFriendlyString());
            return null;
        }
        
        System.out.println("Final selection: " + selection.size() + " inputs, total fee: " + 
                          Coin.valueOf(calculatedFee).toFriendlyString());
        
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
