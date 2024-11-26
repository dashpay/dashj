/*
 * Copyright (c) 2023 Dash Core Group
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

package org.bitcoinj.coinjoin.utils;

import org.bitcoinj.coinjoin.TestWithCoinJoinWallet;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

// TODO: we need to get a wallet file with these transactions to properly identify them.
public class CoinJoinTransactionTypeTest extends TestWithCoinJoinWallet {

    @Override
    public void setUp() throws Exception {
        BriefLogFormatter.init();
        Context.propagate(new Context(UNITTEST, 100, Coin.ZERO, false));
        wallet = new Wallet(UNITTEST, KeyChainGroup.builder(UNITTEST)
                .addChain(DeterministicKeyChain.builder()
                        .seed(new DeterministicSeed("six surround similar section increase anxiety page try mandate plate used door", null, "", 0))
                        .build())
                .build());
        myKey = wallet.currentReceiveKey();
        myAddress = Address.fromKey(UNITTEST, myKey);
        blockStore = new MemoryBlockStore(UNITTEST);
        chain = new BlockChain(UNITTEST, wallet, blockStore);
        // Context.get().initDash(false, true);
    }

    @Test
    public void checkTransactionTypesInWallet() throws IOException {
        Context context = new Context(TestNet3Params.get());
        try (InputStream inputStream = getClass().getResourceAsStream("wallet-protobuf-testnet.wallet")) {

            Wallet wallet = new WalletProtobufSerializer().readWallet(inputStream);

            Transaction mixingTx = wallet.getTransaction(Sha256Hash.wrap("430fffe6a76208db7581ff4065cb03447a6f38593bbb1dad6bcf7771ba7b744f"));
            Transaction createDenominationsTx = wallet.getTransaction(Sha256Hash.wrap("434f27ce781d350b581432631437c7959311495a8432bcbc55e825525057a4b1"));
            Transaction makeCollateralInputTx = wallet.getTransaction(Sha256Hash.wrap("6ada49e404897e884fa199e018e8ab29fa488634dac0170e24fe4888ce8264c8"));
            Transaction mixingFeeTx = wallet.getTransaction(Sha256Hash.wrap("0de347863bcd394246dff4409787a1068d91dab685b054dca77d7a9959b2b282"));
            Transaction mixingFeeTwoTx = wallet.getTransaction(Sha256Hash.wrap("2cffea126788f2b3d53592a62d1fc24705372f4d10979885210b3aec3102b014"));
            Transaction mixingFeeThreeTx = wallet.getTransaction(Sha256Hash.wrap("2294c7d23721717d1e04fdd9576273e492090f81554fd3f2f90955d7d5561738"));
            Transaction regularTx = wallet.getTransaction(Sha256Hash.wrap("f39ab7fc1d961a39ff96ee17f01bae75a3859102acf0dc6f40558f360f696130"));


            assertEquals(CoinJoinTransactionType.CreateDenomination, CoinJoinTransactionType.fromTx(createDenominationsTx, wallet));
            assertEquals(CoinJoinTransactionType.MakeCollateralInputs, CoinJoinTransactionType.fromTx(makeCollateralInputTx, wallet));
            assertEquals(CoinJoinTransactionType.MixingFee, CoinJoinTransactionType.fromTx(mixingFeeTx, wallet));
            assertEquals(CoinJoinTransactionType.MixingFee, CoinJoinTransactionType.fromTx(mixingFeeTwoTx, wallet));
            assertEquals(CoinJoinTransactionType.MixingFee, CoinJoinTransactionType.fromTx(mixingFeeThreeTx, wallet));
            assertEquals(CoinJoinTransactionType.Mixing, CoinJoinTransactionType.fromTx(mixingTx, wallet));
            // TODO: assertEquals(CoinJoinTransactionType.Send, CoinJoinTransactionType.fromTx(sendTx, wallet));
            assertEquals(CoinJoinTransactionType.None, CoinJoinTransactionType.fromTx(regularTx, wallet));


        } catch (UnreadableWalletException e) {
            throw new RuntimeException(e);
        }
    }
}
