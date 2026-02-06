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
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.WalletEx;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class CoinJoinGreedyCustomTest {
    
    private static final TestNet3Params PARAMS = TestNet3Params.get();
    private static final Context CONTEXT = new Context(PARAMS);
    
    private WalletEx wallet;
    
    @Before
    public void setUp() {
        Context.propagate(CONTEXT);
        wallet = WalletEx.createDeterministic(PARAMS, Script.ScriptType.P2PKH);
        wallet.initializeCoinJoin(null, 0);
    }
    
    /**
     * Creates a CoinJoin transaction with specified denomination outputs and adds it to the wallet.
     * Marks all outputs as fully mixed by manipulating mapOutpointRoundsCache.
     */
    private void addCoinJoinTransaction(Coin... denominations) {
        Transaction tx = new Transaction(PARAMS);
        
        // Create a dummy coinbase input
        TransactionInput input = new TransactionInput(PARAMS, tx, new byte[0]);
        ECKey key = wallet.currentKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);

        // Create outputs with specified denominations using CoinJoin addresses
        List<TransactionOutput> outputs = new ArrayList<>();
        for (Coin denomination : denominations) {
            Address address = wallet.getCoinJoin().getKeyChainGroup().freshAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS);
            TransactionOutput output = new TransactionOutput(PARAMS, tx, denomination, address);
            tx.addOutput(output);
            outputs.add(output);
        }

        Script inputScript = ScriptBuilder.createOutputScript(Address.fromKey(PARAMS, key));
        int index = 0;
        for (Coin denomination : denominations) {
            tx.addSignedInput(new TransactionOutPoint(PARAMS, 0, Sha256Hash.of(new byte[index++])), inputScript, key, Transaction.SigHash.ALL, true);
        }
        
        // Add transaction to wallet
        wallet.receivePending(tx, null);

        // Set confidence to building (confirmed)
        tx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        tx.getConfidence().setAppearedAtChainHeight(100);
        tx.getConfidence().setDepthInBlocks(10);
        
        // Mark all outputs as fully mixed by setting high round count
        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutPoint outPoint = new TransactionOutPoint(PARAMS, i, tx.getTxId());
            setOutputRounds(outPoint, 10); // 10 rounds = fully mixed
        }
        
        System.out.println("Added CoinJoin transaction with denominations:");
        for (Coin denom : denominations) {
            System.out.println("  " + denom.toFriendlyString());
        }
    }
    
    /**
     * Helper method to set the round count for an output using reflection to access mapOutpointRoundsCache
     */
    private void setOutputRounds(TransactionOutPoint outPoint, int rounds) {
        try {
            // Access the private mapOutpointRoundsCache field
            java.lang.reflect.Field field = WalletEx.class.getDeclaredField("mapOutpointRoundsCache");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<TransactionOutPoint, Integer> cache = 
                (java.util.Map<TransactionOutPoint, Integer>) field.get(wallet);
            cache.put(outPoint, rounds);
            System.out.println("Set rounds for " + outPoint + " to " + rounds);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set output rounds", e);
        }
    }
    
    /**
     * Creates a transaction using the greedy algorithm with returnChange = false
     */
    private Transaction createGreedyTransaction(Coin sendAmount) throws InsufficientMoneyException {
        Address toAddress = wallet.freshReceiveAddress();
        SendRequest req = SendRequest.to(toAddress, sendAmount);
        req.coinSelector = new CoinJoinCoinSelector(wallet, false, true); // Use greedy algorithm
        req.returnChange = false; // No change output
        req.feePerKb = Coin.valueOf(1000L); // Set fee rate
        
        System.out.println("\n=== Creating Greedy Transaction ===");
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Return change: " + req.returnChange);
        
        wallet.completeTx(req);
        return req.tx;
    }
    
    @Test
    public void testGreedyWithSpecificDenominations() throws InsufficientMoneyException {
        System.out.println("=== Test: Greedy with Specific Denominations ===");
        
        // Add CoinJoin transaction with various denominations
        addCoinJoinTransaction(
            Denomination.THOUSANDTH.value,   // 0.001 DASH
            Denomination.THOUSANDTH.value,   // 0.001 DASH
            Denomination.THOUSANDTH.value,   // 0.001 DASH
            Denomination.THOUSANDTH.value,   // 0.001 DASH
            Denomination.THOUSANDTH.value,   // 0.001 DASH
            Denomination.HUNDREDTH.value,  // 0.01 DASH
            Denomination.HUNDREDTH.value,  // 0.01 DASH
            Denomination.TENTH.value, // 0.1 DASH
            Denomination.TENTH.value, // 0.1 DASH
            Coin.valueOf(100000000L) // 1.0 DASH
        );
        
        // Test sending 0.05 DASH (should use optimal combination)
        Coin sendAmount = Coin.valueOf(5000000L); // 0.05 DASH
        Transaction tx = createGreedyTransaction(sendAmount);
        
        // Print transaction details
        System.out.println("\n=== Transaction Results ===");
        System.out.println("Transaction ID: " + tx.getTxId());
        System.out.println("Number of inputs: " + tx.getInputs().size());
        System.out.println("Number of outputs: " + tx.getOutputs().size());
        
        System.out.println("\nInputs used:");
        Coin totalInput = Coin.ZERO;
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            Coin inputValue = input.getConnectedOutput().getValue();
            totalInput = totalInput.add(inputValue);
            System.out.println("  Input " + (i+1) + ": " + inputValue.toFriendlyString());
        }
        
        System.out.println("\nOutputs created:");
        for (int i = 0; i < tx.getOutputs().size(); i++) {
            TransactionOutput output = tx.getOutputs().get(i);
            System.out.println("  Output " + (i+1) + ": " + output.getValue().toFriendlyString());
        }
        
        System.out.println("\nTransaction summary:");
        System.out.println("Total input: " + totalInput.toFriendlyString());
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Transaction fee: " + tx.getFee().toFriendlyString());
        
        // Verify transaction properties
        assertTrue("Should have at least one input", tx.getInputs().size() > 0);
        assertEquals("Should have exactly one output (no change)", 1, tx.getOutputs().size());
        assertEquals("Output should equal send amount", sendAmount, tx.getOutput(0).getValue());
        assertTrue("Total input should be >= send amount", totalInput.isGreaterThanOrEqualTo(sendAmount));
    }

    @Test
    public void testGreedyWithOneDenominations() throws InsufficientMoneyException {
        System.out.println("=== Test: Greedy with Specific Denominations ===");

        // Add CoinJoin transaction with various denominations
        addCoinJoinTransaction(
                Denomination.ONE.value // 1.0 DASH
        );

        // Test sending 1 DASH (should use optimal combination)
        Coin sendAmount = Coin.COIN; // 1.0 DASH
        Transaction tx = createGreedyTransaction(sendAmount);

        // Print transaction details
        System.out.println("\n=== Transaction Results ===");
        System.out.println("Transaction ID: " + tx.getTxId());
        System.out.println("Number of inputs: " + tx.getInputs().size());
        System.out.println("Number of outputs: " + tx.getOutputs().size());

        System.out.println("\nInputs used:");
        Coin totalInput = Coin.ZERO;
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            Coin inputValue = input.getConnectedOutput().getValue();
            totalInput = totalInput.add(inputValue);
            System.out.println("  Input " + (i+1) + ": " + inputValue.toFriendlyString());
        }

        System.out.println("\nOutputs created:");
        for (int i = 0; i < tx.getOutputs().size(); i++) {
            TransactionOutput output = tx.getOutputs().get(i);
            System.out.println("  Output " + (i+1) + ": " + output.getValue().toFriendlyString());
        }

        System.out.println("\nTransaction summary:");
        System.out.println("Total input: " + totalInput.toFriendlyString());
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Transaction fee: " + tx.getFee().toFriendlyString());

        // Verify transaction properties
        assertTrue("Should have at least one input", tx.getInputs().size() > 0);
        assertEquals("Should have exactly one output (no change)", 1, tx.getOutputs().size());
        assertEquals("Output should equal send amount", sendAmount, tx.getOutput(0).getValue());
        assertTrue("Total input should be >= send amount", totalInput.isGreaterThanOrEqualTo(sendAmount));
    }
    
    @Test
    public void testGreedyWithManySmallDenominations() throws InsufficientMoneyException {
        System.out.println("\n=== Test: Greedy with Many Small Denominations ===");
        
        // Add transaction with many small denominations to test consolidation
        Coin[] denominations = new Coin[15];
        for (int i = 0; i < 15; i++) {
            denominations[i] = Denomination.THOUSANDTH.value; // 15 × 0.001 DASH
        }
        // Add some larger denominations
        addCoinJoinTransaction(denominations);
        addCoinJoinTransaction(
            Denomination.HUNDREDTH.value,  // 0.01 DASH
                Denomination.HUNDREDTH.value,  // 0.01 DASH
                Denomination.TENTH.value  // 0.1 DASH
        );
        
        // Test sending amount that should trigger consolidation
        Coin sendAmount = Coin.valueOf(12000000L); // 0.012 DASH
        Transaction tx = createGreedyTransaction(sendAmount);
        
        // Print results
        System.out.println("\n=== Transaction Results ===");
        System.out.println("Number of inputs: " + tx.getInputs().size());
        System.out.println("Number of outputs: " + tx.getOutputs().size());
        
        System.out.println("\nInputs used:");
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            System.out.println("  Input " + (i+1) + ": " + input.getConnectedOutput().getValue().toFriendlyString());
        }
        
        // Verify the greedy algorithm optimized the selection
        assertTrue("Should use fewer than all available small denominations", tx.getInputs().size() < 15);
        assertEquals("Should have exactly one output (no change)", 1, tx.getOutputs().size());
    }
    
    @Test
    public void testGreedyWithLargeDenominations() throws InsufficientMoneyException {
        System.out.println("\n=== Test: Greedy with Large Denominations ===");
        
        // Add transaction with larger denominations
        addCoinJoinTransaction(
            Denomination.TEN.value, // 10.0 DASH
                Denomination.ONE.value,  // 1.0 DASH
                Denomination.ONE.value,  // 1.0 DASH
            Denomination.TENTH.value,   // 0.1 DASH
            Denomination.TENTH.value,   // 0.1 DASH
            Denomination.HUNDREDTH.value,    // 0.01 DASH
            Denomination.THOUSANDTH.value      // 0.001 DASH
        );
        
        // Test sending amount requiring exact denomination selection
        Coin sendAmount = Coin.valueOf(211000000L); // 2.11 DASH
        Transaction tx = createGreedyTransaction(sendAmount);
        
        // Print results
        System.out.println("\n=== Transaction Results ===");
        System.out.println("Number of inputs: " + tx.getInputs().size());
        
        System.out.println("\nInputs used:");
        Coin totalInput = Coin.ZERO;
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInputs().get(i);
            Coin inputValue = input.getConnectedOutput().getValue();
            totalInput = totalInput.add(inputValue);
            System.out.println("  Input " + (i+1) + ": " + inputValue.toFriendlyString());
        }
        
        System.out.println("\nTotal input: " + totalInput.toFriendlyString());
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Fee: " + tx.getFee().toFriendlyString());
        
        // Verify transaction
        assertEquals("Should have exactly one output (no change)", 1, tx.getOutputs().size());
        assertEquals("Output should equal send amount", sendAmount, tx.getOutput(0).getValue());
    }

    @Test(timeout = 3000) // guards against the pre-fix infinite loop
    public void recipientsPayFees_noChange_exactMatch_standardDenom_doesNotLoop() throws Exception {
        // 1) Fund wallet with exactly one standard denom UTXO (1 DASH + 1000 satoshis)
        addCoinJoinTransaction(Denomination.ONE.value); // 1.00001000 DASH

        // 2) Build a send that exactly matches inputs, with no change and recipients paying fees
        Address dest = Address.fromKey(PARAMS, new ECKey());
        SendRequest req = SendRequest.to(dest, Denomination.ONE.value); // 1.00001 DASH
        req.returnChange = false;
        req.recipientsPayFees = true;
        req.feePerKb = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE; // any non-zero fee works
        req.shuffleOutputs = false;
        req.sortByBIP69 = false;

        wallet.completeTx(req); // before fix: loops; after fix: completes

        // 3) Assertions: single recipient output, no change, fee > 0
        assertEquals(1, req.tx.getOutputs().size());
        Coin inputSum = req.tx.getInputSum();
        Coin outputSum = req.tx.getOutputSum();
        assertEquals(Denomination.ONE.value, inputSum); // spent exactly the ONE denom
        assertTrue(inputSum.isGreaterThan(outputSum));  // recipient paid the fee
    }
}