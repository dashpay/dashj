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
        
        // Estimate fee first
        int estimatedInputs = 3; // rough estimate
        int txSize = 100 + (estimatedInputs * 150) + (2 * 34);
        long estimatedFee = (feePerKb.value * txSize) / 1000;
        long totalNeeded = target + estimatedFee;
        
        System.out.println("Target: " + Coin.valueOf(target).toFriendlyString() + 
                          ", Estimated fee: " + Coin.valueOf(estimatedFee).toFriendlyString() +
                          ", Total needed: " + Coin.valueOf(totalNeeded).toFriendlyString());
        
        // Sort denominations largest to smallest
        denoms.sort(Comparator.reverseOrder());
        
        ArrayList<TransactionOutput> selection = new ArrayList<>();
        long remaining = totalNeeded;
        
        // Simple greedy: start with largest denom that's <= remaining amount
        for (Coin denom : denoms) {
            if (remaining <= 0) break;
            
            ArrayList<TransactionOutput> availableOutputs = denomMap.get(denom);
            if (availableOutputs == null || availableOutputs.isEmpty()) continue;
            
            // Only use this denomination if it's <= remaining amount
            if (denom.value <= remaining) {
                int neededCount = (int) (remaining / denom.value);
                int availableCount = availableOutputs.size();
                int useCount = Math.min(neededCount, availableCount);
                
                System.out.println("Using " + useCount + " of " + denom.toFriendlyString() + 
                                 " (available: " + availableCount + ", needed: " + neededCount + ")");
                
                for (int i = 0; i < useCount; i++) {
                    selection.add(availableOutputs.get(i));
                    remaining -= denom.value;
                }
            }
        }
        
        // If we still need more, try to use smallest possible denominations first
        if (remaining > 0) {
            System.out.println("Still need: " + Coin.valueOf(remaining).toFriendlyString() + 
                             ", trying smallest denominations first...");
            
            // Sort denominations smallest to largest to use smallest possible first
            denoms.sort(Coin::compareTo);
            
            // First try to satisfy with smallest denominations
            for (Coin denom : denoms) {
                if (remaining <= 0) break;
                
                ArrayList<TransactionOutput> availableOutputs = denomMap.get(denom);
                if (availableOutputs == null || availableOutputs.isEmpty()) continue;
                
                // Use all available of this small denomination if it helps
                while (remaining > 0 && !availableOutputs.isEmpty() /*&& denom.value <= remaining*/) {
                    System.out.println("Using 1 more of " + denom.toFriendlyString() + 
                                     " for remaining " + Coin.valueOf(remaining).toFriendlyString());
                    selection.add(availableOutputs.remove(0)); // Remove to avoid reuse
                    remaining -= denom.value;
                }
            }
            
            // If still need more, use exactly 1 of the next larger denomination
            if (remaining > 0) {
                System.out.println("Small denoms insufficient, using 1 larger denomination...");
                
                for (Coin denom : denoms) {
                    ArrayList<TransactionOutput> availableOutputs = denomMap.get(denom);
                    if (availableOutputs == null || availableOutputs.isEmpty()) continue;
                    
                    if (denom.value > remaining) {
                        System.out.println("Using 1 of " + denom.toFriendlyString() + 
                                         " to cover remaining " + Coin.valueOf(remaining).toFriendlyString());
                        selection.clear();
                        selection.add(availableOutputs.get(0));
                        remaining = 0;
                        break;
                    }
                }
            }
        }
        
        if (remaining > 0) {
            System.out.println("WARNING: Could not satisfy target, remaining: " + 
                             Coin.valueOf(remaining).toFriendlyString());
            return null;
        }
        
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
