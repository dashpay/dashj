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

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.WalletEx;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CoinJoinSelectorGreedyTest {
    
    private static final TestNet3Params PARAMS = TestNet3Params.get();
    private static final Context CONTEXT = new Context(PARAMS);

    private WalletEx wallet;
    private List<Denomination> availableOutputs;
    private Coin sendAmount;
    private List<Denomination> expectedGreedyOutputs;
    private String testName;
    public CoinJoinSelectorGreedyTest(String testName, List<Denomination> availableOutputs,
                                     Coin sendAmount, List<Denomination> expectedGreedyOutputs) {
        this.testName = testName;
        this.availableOutputs = availableOutputs;
        this.sendAmount = sendAmount;
        this.expectedGreedyOutputs = expectedGreedyOutputs;
    }
    
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        
        return Arrays.asList(new Object[][]{
            {
                "Nearly perfect match with 0.1 denominations and 0.001 for fee",
                Arrays.asList(
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.THOUSANDTH,
                    Denomination.THOUSANDTH,
                    Denomination.THOUSANDTH,
                    Denomination.ONE  // 1.0 DASH
                ),
                Coin.valueOf(50000000L),      // Send 0.5 DASH
                Arrays.asList(
                    Denomination.TENTH,  // Should use 5 × 0.1 DASH
                    Denomination.TENTH,
                    Denomination.TENTH,
                    Denomination.TENTH,
                    Denomination.TENTH,
                        Denomination.THOUSANDTH
                )
            },
            {
                "Use larger denomination when small insufficient",
                Arrays.asList(
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.ONE, // 1.0 DASH
                    Denomination.TEN // 10.0 DASH
                ),
                Coin.valueOf(50000000L),      // Send 0.5 DASH
                Collections.singletonList(
                    Denomination.ONE  // Then 1 × 1.0 DASH to cover remaining
                )
            },
            {
                "Small amount with mixed denominations",
                Arrays.asList(
                    Denomination.HUNDREDTH,   // 0.01 DASH
                    Denomination.HUNDREDTH,   // 0.01 DASH
                    Denomination.HUNDREDTH,   // 0.01 DASH
                    Denomination.HUNDREDTH,   // 0.01 DASH
                    Denomination.HUNDREDTH,   // 0.01 DASH
                    Denomination.HUNDREDTH,   // 0.01 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.TENTH,  // 0.1 DASH
                    Denomination.ONE  // 1.0 DASH
                ),
                Coin.valueOf(5000000L),       // Send 0.05 DASH
                Arrays.asList(
                    Denomination.HUNDREDTH,   // Should use 5 × 0.01 DASH
                    Denomination.HUNDREDTH,
                    Denomination.HUNDREDTH,
                    Denomination.HUNDREDTH,
                    Denomination.HUNDREDTH,
                    Denomination.HUNDREDTH
                )
            },
            {
                "Large amount requiring multiple denominations",
                Arrays.asList(
                    Denomination.TEN, // 10.0 DASH
                    Denomination.ONE,  // 1.0 DASH
                    Denomination.ONE,  // 1.0 DASH
                    Denomination.TENTH,   // 0.1 DASH
                    Denomination.TENTH,   // 0.1 DASH
                    Denomination.TENTH,   // 0.1 DASH
                    Denomination.TENTH,   // 0.1 DASH
                    Denomination.TENTH    // 0.1 DASH
                ),
                Coin.valueOf(1050000000L),     // Send 10.5 DASH
                Arrays.asList(
                        Denomination.TEN, // Use 1 × 10.0 DASH
                    Denomination.TENTH,   // Then 5 × 0.1 DASH
                    Denomination.TENTH,
                    Denomination.TENTH,
                    Denomination.TENTH,
                    Denomination.TENTH
                )
            },
            {
                "Only large denomination available",
                Arrays.asList(
                    Denomination.ONE,  // 1.0 DASH
                    Denomination.ONE,  // 1.0 DASH
                    Denomination.ONE   // 1.0 DASH
                ),
                Coin.valueOf(50000000L),       // Send 0.5 DASH
                Arrays.asList(
                    Denomination.ONE   // Must use 1 × 1.0 DASH (no smaller available)
                )
            },
            {
                "Send amount less than 0.001 denomination",
                Arrays.asList(
                        Denomination.ONE,  // 1.0 DASH
                        Denomination.THOUSANDTH,  // 1.0 DASH
                        Denomination.THOUSANDTH   // 1.0 DASH
                ),
                Coin.valueOf(50000L),       // Send 0.5 DASH
                Arrays.asList(
                        Denomination.THOUSANDTH   // Must use 1 × 1.0 DASH (no smaller available)
                )
            }
        });
    }
    
    @Before
    public void setUp() {
        Context.propagate(CONTEXT);
        wallet = WalletEx.createDeterministic(PARAMS, Script.ScriptType.P2PKH);
        wallet.initializeCoinJoin(0);

        // Create transactions with the specified outputs and add them to wallet
        for (Denomination outputValue : availableOutputs) {
            Transaction tx = createCoinJoinTransaction(outputValue.value);
            wallet.receivePending(tx, null);
            // Mark output as fully mixed by setting rounds to a high value
            TransactionOutput output = tx.getOutputs().get(0);
        }
    }
    
    @Test
    public void testGreedyVsNonGreedySelection() throws InsufficientMoneyException {
        System.out.println("\n=== Testing: " + testName + " ===");
        System.out.println("Available outputs: " + 
            availableOutputs.stream()
                .map(a -> a.value.toFriendlyString())
                .collect(Collectors.joining(", ")));
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Expected greedy outputs: " + 
            expectedGreedyOutputs.stream()
                .map(a -> a.value.toFriendlyString())
                .collect(Collectors.joining(", ")));
        
        // Test with greedy algorithm enabled
        CoinJoinCoinSelector greedySelector = new CoinJoinCoinSelector(wallet, false, true);
        wallet.getTransactionList(false).forEach(a -> a.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING));
        List<TransactionOutput> candidates = wallet.calculateAllSpendCandidates(false, false);
        
        System.out.println("Candidates found: " + candidates.size());
        candidates.forEach(c -> System.out.println("  " + c.getValue().toFriendlyString()));
        
        CoinSelection greedySelection = greedySelector.select(sendAmount, candidates);
        
        // Test with greedy algorithm disabled
        CoinJoinCoinSelector normalSelector = new CoinJoinCoinSelector(wallet, false, false);
        CoinSelection normalSelection = normalSelector.select(sendAmount, candidates);
        
        // Check if we have any candidates at all
        if (candidates.isEmpty()) {
            System.out.println("No candidates available - skipping test");
            return;
        }
        
        // Verify greedy selection
        assertNotNull("Greedy selection should not be null", greedySelection);
        if (greedySelection != null) {
            assertTrue("Greedy selection should have enough value", 
                      greedySelection.valueGathered.isGreaterThanOrEqualTo(sendAmount));
        }
        
        // Verify normal selection
        assertNotNull("Normal selection should not be null", normalSelection);
        assertTrue("Normal selection should have enough value", 
                  normalSelection.valueGathered.isGreaterThanOrEqualTo(sendAmount));
        
        // Print results
        System.out.println("\nGreedy Selection:");
        System.out.println("  Inputs: " + greedySelection.gathered.size());
        System.out.println("  Total: " + greedySelection.valueGathered.toFriendlyString());
        System.out.println("  Change: " + greedySelection.valueGathered.subtract(sendAmount).toFriendlyString());
        greedySelection.gathered.forEach(output -> 
            System.out.println("    " + output.getValue().toFriendlyString()));
        
        System.out.println("\nNormal Selection:");
        System.out.println("  Inputs: " + normalSelection.gathered.size());
        System.out.println("  Total: " + normalSelection.valueGathered.toFriendlyString());
        System.out.println("  Change: " + normalSelection.valueGathered.subtract(sendAmount).toFriendlyString());
        normalSelection.gathered.forEach(output -> 
            System.out.println("    " + output.getValue().toFriendlyString()));
        
        // Verify that greedy algorithm produces expected results
        List<Coin> actualGreedyValues = greedySelection.gathered.stream()
            .map(TransactionOutput::getValue)
            .sorted()
            .collect(Collectors.toList());
        List<Coin> expectedSorted = expectedGreedyOutputs.stream()
                .map(a -> a.value)
                .sorted()
                .collect(Collectors.toList());
        
        // Check if greedy selection minimizes change better than normal selection
        Coin greedyChange = greedySelection.valueGathered.subtract(sendAmount);
        Coin normalChange = normalSelection.valueGathered.subtract(sendAmount);
        
        System.out.println("\nComparison:");
        System.out.println("  Greedy change: " + greedyChange.toFriendlyString());
        System.out.println("  Normal change: " + normalChange.toFriendlyString());
        System.out.println("  Greedy is better: " + greedyChange.isLessThanOrEqualTo(normalChange));
        
        // Greedy should generally produce less or equal change
        assertTrue("Greedy algorithm should minimize change", 
                  greedyChange.isLessThanOrEqualTo(normalChange));
        assertEquals(expectedSorted, actualGreedyValues);
    }
    
    private Transaction createCoinJoinTransaction(Coin outputValue) {
        Transaction tx = new Transaction(PARAMS);
        
        // Create a dummy input (from coinbase for simplicity)
        TransactionInput input = new TransactionInput(PARAMS, tx, ScriptBuilder.createInputScript(new TransactionSignature(BigInteger.ONE, BigInteger.TEN), new ECKey()).getProgram());
        tx.addInput(input);
        
        // Create the output with specified value
        Address address = wallet.getCoinJoin().freshReceiveAddress();
        TransactionOutput output = new TransactionOutput(PARAMS, tx, outputValue, address);
        
        // Mark as CoinJoin output (this is what the selector looks for)
        //output.markAsCoinJoin();
        tx.addOutput(output);
        wallet.markAsFullyMixed(new TransactionOutPoint(PARAMS, output));

        // Set confidence to building (confirmed)
        tx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        tx.getConfidence().setAppearedAtChainHeight(100);
        tx.getConfidence().setDepthInBlocks(10);
        
        return tx;
    }
    

}