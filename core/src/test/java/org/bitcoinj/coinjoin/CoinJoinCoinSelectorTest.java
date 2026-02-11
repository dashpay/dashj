/*
 * Copyright 2022 Dash Core Group
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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.CoinSelection;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.WalletEx;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CoinJoinCoinSelectorTest extends TestWithCoinJoinWallet {


    @Test
    public void selectable() {
        CoinJoinCoinSelector coinSelector = new CoinJoinCoinSelector(walletEx);

        assertTrue(coinSelector.shouldSelect(lastTxCoinJoin));
        // txDenomination is mixed zero rounds, so it should not be selected
        assertFalse(coinSelector.shouldSelect(txDenomination));
    }

    @Test
    public void testRealWalletGreedySelection() throws IOException, UnreadableWalletException, InsufficientMoneyException {
        WalletEx realWallet;
        Context.propagate(new Context(TestNet3Params.get(), 100, Coin.ZERO, false));
        try (InputStream is = getClass().getResourceAsStream("/org/bitcoinj/wallet/coinjoin-large.wallet")) {
            if (is == null) {
                System.out.println("Wallet file not found, skipping test");
                return;
            }
            realWallet = (WalletEx) new WalletProtobufSerializer().readWallet(is);
        }

        // Test just one amount to avoid transaction size issues
        Coin sendAmount = Coin.valueOf(50000000L); // 0.5 DASH
        System.out.println("\n=== Testing Real Wallet Transaction ===");
        System.out.println("Send amount: " + sendAmount.toFriendlyString());
        System.out.println("Wallet balance: " + realWallet.getBalance().toFriendlyString());

        // Test with greedy algorithm - just coin selection without transaction creation
        CoinJoinCoinSelector greedySelector = new CoinJoinCoinSelector(realWallet, false, true);
        CoinJoinCoinSelector normalSelector = new CoinJoinCoinSelector(realWallet, false, false);

        List<TransactionOutput> candidates = realWallet.calculateAllSpendCandidates(false, false);
        System.out.println("Available candidates: " + candidates.size());

        try {
            CoinSelection greedySelection = greedySelector.select(sendAmount, candidates);
            CoinSelection normalSelection = normalSelector.select(sendAmount, candidates);
            assertNotNull( "greedy selections failed", greedySelection);
            assertNotNull( "normal selections failed", normalSelection);
            Coin greedyChange = greedySelection.valueGathered.subtract(sendAmount);
            Coin normalChange = normalSelection.valueGathered.subtract(sendAmount);

            System.out.println("Greedy Selection:");
            System.out.println("  Inputs: " + greedySelection.gathered.size());
            System.out.println("  Total: " + greedySelection.valueGathered.toFriendlyString());
            System.out.println("  Change: " + greedyChange.toFriendlyString());

            System.out.println("Normal Selection:");
            System.out.println("  Inputs: " + normalSelection.gathered.size());
            System.out.println("  Total: " + normalSelection.valueGathered.toFriendlyString());
            System.out.println("  Change: " + normalChange.toFriendlyString());

            System.out.println("Comparison:");
            System.out.println("  Greedy change: " + greedyChange.toFriendlyString());
            System.out.println("  Normal change: " + normalChange.toFriendlyString());
            System.out.println("  Greedy is better: " + greedyChange.isLessThanOrEqualTo(normalChange));

            // Verify greedy minimizes change
            assertTrue("Greedy algorithm should minimize change",
                    greedyChange.isLessThanOrEqualTo(normalChange));

        } catch (Exception e) {
            System.out.println("Exception during coin selection: " + e.getMessage());
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testRealWalletGreedySelection2() throws IOException, UnreadableWalletException, InsufficientMoneyException {
        WalletEx realWallet;
        Context.propagate(new Context(TestNet3Params.get(), 100, Coin.ZERO, false));

        try (InputStream is = getClass().getResourceAsStream("/org/bitcoinj/wallet/coinjoin-large.wallet")) {
            if (is == null) {
                System.out.println("Wallet file not found, skipping test");
                return;
            }
            realWallet = (WalletEx) new WalletProtobufSerializer().readWallet(is);
        }

        // Test multiple amounts: 0.5, 0.05, 5 DASH
        Coin[] testAmounts = {
                Coin.valueOf(50000L),    // 0.0005 DASH
                Coin.valueOf(5000000L),    // 0.05 DASH
                Coin.valueOf(50000000L),    // 0.5 DASH
                Coin.valueOf(5000000L),     // 0.05 DASH
                Coin.COIN,
                Coin.COIN.plus(Coin.CENT.multiply(27)),
                Coin.valueOf(500000000L),    // 5 DASH
                Coin.FIFTY_COINS    // 50 DASH
        };

        for (int i = 0; i < testAmounts.length; i++) {
            Coin sendAmount = testAmounts[i];
            System.out.println("\n=== Testing Real Wallet Transaction " + (i+1) + " ===");
            System.out.println("Send amount: " + sendAmount.toFriendlyString());
            System.out.println("Wallet balance: " + realWallet.getBalance().toFriendlyString());

            try {
                // Create transaction with greedy algorithm
                Address toAddress = realWallet.freshReceiveAddress();
                SendRequest greedyReq = SendRequest.to(toAddress, sendAmount);
                greedyReq.coinSelector = new CoinJoinCoinSelector(realWallet, false, true);
                greedyReq.feePerKb = Coin.valueOf(1000L); // Set fee
                greedyReq.returnChange = false;

                // Clone wallet state for parallel testing
                WalletEx greedyWallet = realWallet; // Use same wallet for now
                greedyWallet.completeTx(greedyReq);
                Transaction greedyTx = greedyReq.tx;

                // Create transaction with normal algorithm
                SendRequest normalReq = SendRequest.to(toAddress, sendAmount);
                normalReq.coinSelector = new CoinJoinCoinSelector(realWallet, false, false);
                normalReq.feePerKb = Coin.valueOf(1000L); // Set same fee

                realWallet.completeTx(normalReq);
                Transaction normalTx = normalReq.tx;

                // Calculate input totals and change
                Coin greedyInputTotal = greedyTx.getInputs().stream()
                        .map(input -> input.getConnectedOutput().getValue())
                        .reduce(Coin.ZERO, Coin::add);
                Coin normalInputTotal = normalTx.getInputs().stream()
                        .map(input -> input.getConnectedOutput().getValue())
                        .reduce(Coin.ZERO, Coin::add);

                Coin greedyChange = greedyInputTotal.subtract(sendAmount).subtract(greedyTx.getFee());
                Coin normalChange = normalInputTotal.subtract(sendAmount).subtract(normalTx.getFee());

                System.out.println("Greedy Transaction:");
                System.out.println("  Inputs: " + greedyTx.getInputs().size());
                System.out.println("  Total input: " + greedyInputTotal.toFriendlyString());
                System.out.println("  Fee: " + greedyTx.getFee().toFriendlyString());
                System.out.println("  Change: " + greedyChange.toFriendlyString());
                greedyTx.getInputs().forEach(input ->
                        System.out.println("    Input: " + input.getConnectedOutput().getValue().toFriendlyString()));

                System.out.println("Normal Transaction:");
                System.out.println("  Inputs: " + normalTx.getInputs().size());
                System.out.println("  Total input: " + normalInputTotal.toFriendlyString());
                System.out.println("  Fee: " + normalTx.getFee().toFriendlyString());
                System.out.println("  Change: " + normalChange.toFriendlyString());
                normalTx.getInputs().forEach(input ->
                        System.out.println("    Input: " + input.getConnectedOutput().getValue().toFriendlyString()));

                System.out.println("Comparison:");
                System.out.println("  Greedy change: " + greedyChange.toFriendlyString());
                System.out.println("  Normal change: " + normalChange.toFriendlyString());
                System.out.println("  Greedy fee: " + greedyTx.getFee().toFriendlyString());
                System.out.println("  Normal fee: " + normalTx.getFee().toFriendlyString());
                System.out.println("  Greedy uses more inputs: " + (greedyTx.getInputs().size() > normalTx.getInputs().size()));
                System.out.println("  Greedy has higher fee: " + greedyTx.getFee().isGreaterThan(normalTx.getFee()));

                // Verify that greedy algorithm produces efficient results
//                assertTrue("Greedy algorithm should minimize change for " + sendAmount.toFriendlyString(),
//                        greedyChange.isLessThanOrEqualTo(normalChange));

            } catch (InsufficientMoneyException e) {
                System.out.println("Insufficient funds for " + sendAmount.toFriendlyString());
                System.out.println("Available: " + realWallet.getBalance().toFriendlyString());
            } catch (Exception e) {
                System.out.println("Exception creating transaction for " + sendAmount.toFriendlyString() + ": " + e.getMessage());
                e.printStackTrace();
                // Don't fail the test, just log and continue
                fail();
            }
        }
    }
}
