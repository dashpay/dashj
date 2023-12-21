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

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.testing.TestWithWallet;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.WalletEx;
import org.bitcoinj.wallet.WalletTransaction;
import org.junit.After;
import org.junit.Before;

import java.net.InetSocketAddress;

import static org.bitcoinj.core.Coin.COIN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestWithCoinJoinWallet extends TestWithWallet {

    WalletEx walletEx;

    Transaction txDeposit;
    Transaction txDenomination;
    Transaction lastTxCoinJoin;
    Transaction txReceiveZeroConf;
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        walletEx = WalletEx.fromSeed(wallet.getParams(), wallet.getKeyChainSeed(), Script.ScriptType.P2PKH);
        CoinJoinClientOptions.setRounds(1);
        setupWallet();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        CoinJoinClientOptions.setRounds(CoinJoinConstants.DEFAULT_COINJOIN_ROUNDS);
        super.tearDown();
    }

    public void setupWallet() {
        walletEx.initializeCoinJoin(0);
        DeterministicKey key = (DeterministicKey) walletEx.getCoinJoin().freshReceiveKey();

        txDeposit = new Transaction(UNITTEST);
        txDeposit.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        txDeposit.addOutput(COIN, wallet.freshAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS));
        txDeposit.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        walletEx.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.SPENT, txDeposit));

        Transaction txCoinJoin;
        txDenomination = new Transaction(UNITTEST);
        txDenomination.addInput(txDeposit.getOutput(0));
        txDenomination.addOutput(CoinJoin.getSmallestDenomination(), (DeterministicKey) walletEx.getCoinJoin().freshReceiveKey());
        txDenomination.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        walletEx.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.SPENT, txDenomination));

        txCoinJoin = new Transaction(UNITTEST);
        txCoinJoin.addInput(txDenomination.getOutput(0));
        txCoinJoin.addOutput(CoinJoin.getSmallestDenomination(), (DeterministicKey) walletEx.getCoinJoin().freshReceiveKey());
        txCoinJoin.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        walletEx.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, txCoinJoin));

        // mix for the max number of required rounds to make sure that the coinselecter chooses the transaction
        int requiredRounds = CoinJoinClientOptions.getRounds() + CoinJoinClientOptions.getRandomRounds();
        lastTxCoinJoin = txCoinJoin;
        for (int i = 1; i < requiredRounds; ++i) {
            Transaction txNextCoinJoin = new Transaction(UNITTEST);
            txNextCoinJoin.addInput(lastTxCoinJoin.getOutput(0));
            txNextCoinJoin.addOutput(CoinJoin.getSmallestDenomination(), (DeterministicKey) walletEx.getCoinJoin().freshReceiveKey());
            txNextCoinJoin.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
            walletEx.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, txNextCoinJoin));
            lastTxCoinJoin = txNextCoinJoin;
        }

        // a transaction with no confirmations, but IS lock and seen by two peers
        txReceiveZeroConf = new Transaction(UNITTEST);
        txReceiveZeroConf.addInput(Sha256Hash.ZERO_HASH, 0, new Script(new byte[0]));
        txReceiveZeroConf.addOutput(Coin.FIFTY_COINS, wallet.freshAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS));
        txReceiveZeroConf.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        txReceiveZeroConf.getConfidence().setIXType(TransactionConfidence.IXType.IX_LOCKED);
        txReceiveZeroConf.getConfidence().markBroadcastBy(new PeerAddress(UNITTEST, new InetSocketAddress("127.0.0.1", 19999)));
        txReceiveZeroConf.getConfidence().markBroadcastBy(new PeerAddress(UNITTEST, new InetSocketAddress("127.0.0.1", 19998)));
        walletEx.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.SPENT, txReceiveZeroConf));
    }
}
