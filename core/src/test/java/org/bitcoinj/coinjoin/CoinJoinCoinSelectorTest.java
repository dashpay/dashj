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

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.testing.TestWithWallet;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.bitcoinj.wallet.Wallet;
import org.junit.Test;

import static org.bitcoinj.core.Coin.COIN;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoinJoinCoinSelectorTest extends TestWithWallet {
    private static final NetworkParameters UNITTEST = UnitTestParams.get();

    private static final Context context = Context.getOrCreate(UNITTEST);

    @Test
    public void selectable() {
        Wallet wallet = Wallet.createDeterministic(context, Script.ScriptType.P2PKH);
        wallet.addCoinJoinKeyChain(DerivationPathFactory.get(context.getParams()).coinJoinDerivationPath());
        DeterministicKey key = wallet.freshCoinJoinKey();

        CoinJoinCoinSelector coinSelector = new CoinJoinCoinSelector(wallet);

        Transaction txCoinJoin;
        txCoinJoin = new Transaction(UNITTEST);
        txCoinJoin.addOutput(CoinJoin.getSmallestDenomination(), key);
        txCoinJoin.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);

        assertTrue(coinSelector.shouldSelect(txCoinJoin));

        Transaction txNotCoinJoin = new Transaction(UNITTEST);
        txNotCoinJoin.addOutput(COIN, key);
        txCoinJoin.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);

        assertFalse(coinSelector.shouldSelect(txNotCoinJoin));

    }
}
