/*
 * Copyright 2023 Dash Core Group
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

package org.bitcoinj.wallet.authentication;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletExtension;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AuthenticationGroupExtensionTest {
    Wallet wallet;
    AuthenticationGroupExtension mnext;
    UnitTestParams UNITTEST = UnitTestParams.get();
    Context context = new Context(UNITTEST);

    @Before
    public void setUp() throws UnreadableWalletException {
        String seedCode = "enemy check owner stumble unaware debris suffer peanut good fabric bleak outside";
        String passphrase = "";
        long creationtime = 1547463771L;//1409478661L;

        DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);
        DerivationPathFactory factory = DerivationPathFactory.get(UNITTEST);
        KeyChainGroup keyChainGroup = KeyChainGroup.builder(UNITTEST)
                .addChain(DeterministicKeyChain.builder()
                        .seed(seed)
                        .accountPath(factory.bip32DerivationPath(0))
                        .build())
                .addChain(DeterministicKeyChain.builder()
                        .seed(seed)
                        .accountPath(factory.bip44DerivationPath(0))
                        .build())
                .build();

        // The wallet class provides a easy fromSeed() function that loads a new wallet from a given seed.
        wallet = new Wallet(context.getParams(), keyChainGroup);
        mnext = new AuthenticationGroupExtension(wallet);
        mnext.addKeyChain(seed, factory.masternodeOwnerDerivationPath(), AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER);
        mnext.addKeyChain(seed, factory.masternodeVotingDerivationPath(), AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING);
        mnext.addKeyChain(seed, factory.masternodeOperatorDerivationPath(), AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR);
        mnext.addKeyChain(seed, factory.masternodePlatformDerivationPath(), AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR);
        wallet.addExtension(mnext);
    }

    @Test
    public void processTransactionsTest() {
        byte[] providerRegTxData = Utils.HEX.decode("0300010001ca9a43051750da7c5f858008f2ff7732d15691e48eb7f845c791e5dca78bab58010000006b483045022100fe8fec0b3880bcac29614348887769b0b589908e3f5ec55a6cf478a6652e736502202f30430806a6690524e4dd599ba498e5ff100dea6a872ebb89c2fd651caa71ed012103d85b25d6886f0b3b8ce1eef63b720b518fad0b8e103eba4e85b6980bfdda2dfdffffffff018e37807e090000001976a9144ee1d4e5d61ac40a13b357ac6e368997079678c888ac00000000fd1201010000000000ca9a43051750da7c5f858008f2ff7732d15691e48eb7f845c791e5dca78bab580000000000000000000000000000ffff010205064e1f3dd03f9ec192b5f275a433bfc90f468ee1a3eb4c157b10706659e25eb362b5d902d809f9160b1688e201ee6e94b40f9b5062d7074683ef05a2d5efb7793c47059c878dfad38a30fafe61575db40f05ab0a08d55119b0aad300001976a9144fbc8fb6e11e253d77e5a9c987418e89cf4a63d288ac3477990b757387cb0406168c2720acf55f83603736a314a37d01b135b873a27b411fb37e49c1ff2b8057713939a5513e6e711a71cff2e517e6224df724ed750aef1b7f9ad9ec612b4a7250232e1e400da718a9501e1d9a5565526e4b1ff68c028763");
        Transaction proRegTx = new Transaction(UNITTEST, providerRegTxData);

        mnext.processTransaction(proRegTx, null, AbstractBlockChain.NewBlockType.BEST_CHAIN);

        // the voting and owner key should have been found
        IKey votingKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING).getKey(0);
        IKey ownerKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER).getKey(0);
        IKey operatorKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR).getKey(0);
        IKey platformKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR).getKey(0);

        assertEquals(3, mnext.getKeyUsage().size());
        AuthenticationKeyUsage votingUsage = mnext.getKeyUsage().get(votingKey);
        assertNotNull(votingUsage);
        assertEquals(votingUsage.getWhereUsed(), proRegTx.getTxId());
        assertEquals(AuthenticationKeyStatus.CURRENT, votingUsage.getStatus());

        AuthenticationKeyUsage ownerUsage = mnext.getKeyUsage().get(ownerKey);
        assertNotNull(ownerUsage);
        assertEquals(ownerUsage.getWhereUsed(), proRegTx.getTxId());
        assertEquals(AuthenticationKeyStatus.CURRENT, ownerUsage.getStatus());

        AuthenticationKeyUsage operatorUsage = mnext.getKeyUsage().get(operatorKey);
        assertNotNull(operatorUsage);
        assertEquals(operatorUsage.getWhereUsed(), proRegTx.getTxId());
        assertEquals(AuthenticationKeyStatus.CURRENT, ownerUsage.getStatus());

        AuthenticationKeyUsage platformUsage = mnext.getKeyUsage().get(platformKey);
        assertNull(null, platformUsage);
    }

    @Test
    public void roundTrip() throws UnreadableWalletException {
        // process the ProRegTx
        byte[] providerRegTxData = Utils.HEX.decode("0300010001ca9a43051750da7c5f858008f2ff7732d15691e48eb7f845c791e5dca78bab58010000006b483045022100fe8fec0b3880bcac29614348887769b0b589908e3f5ec55a6cf478a6652e736502202f30430806a6690524e4dd599ba498e5ff100dea6a872ebb89c2fd651caa71ed012103d85b25d6886f0b3b8ce1eef63b720b518fad0b8e103eba4e85b6980bfdda2dfdffffffff018e37807e090000001976a9144ee1d4e5d61ac40a13b357ac6e368997079678c888ac00000000fd1201010000000000ca9a43051750da7c5f858008f2ff7732d15691e48eb7f845c791e5dca78bab580000000000000000000000000000ffff010205064e1f3dd03f9ec192b5f275a433bfc90f468ee1a3eb4c157b10706659e25eb362b5d902d809f9160b1688e201ee6e94b40f9b5062d7074683ef05a2d5efb7793c47059c878dfad38a30fafe61575db40f05ab0a08d55119b0aad300001976a9144fbc8fb6e11e253d77e5a9c987418e89cf4a63d288ac3477990b757387cb0406168c2720acf55f83603736a314a37d01b135b873a27b411fb37e49c1ff2b8057713939a5513e6e711a71cff2e517e6224df724ed750aef1b7f9ad9ec612b4a7250232e1e400da718a9501e1d9a5565526e4b1ff68c028763");
        Transaction proRegTx = new Transaction(UNITTEST, providerRegTxData);
        mnext.processTransaction(proRegTx, null, AbstractBlockChain.NewBlockType.BEST_CHAIN);

        // save and load the wallet
        Protos.Wallet protos = new WalletProtobufSerializer().walletToProto(wallet);
        AuthenticationGroupExtension oldExt = mnext;
        mnext = new AuthenticationGroupExtension(UNITTEST);
        Wallet walletCopy = new WalletProtobufSerializer().readWallet(UNITTEST, new WalletExtension[]{mnext}, protos);
        assertNotNull(mnext.getActiveKeyChain());


        // the voting and owner key should have been found
        IKey votingKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING).getKey(0);
        IKey ownerKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER).getKey(0);
        IKey operatorKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR).getKey(0);
        IKey platformKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR).getKey(0);

        assertEquals(3, mnext.getKeyUsage().size());
        AuthenticationKeyUsage votingUsage = mnext.getKeyUsage().get(votingKey);
        assertNotNull(votingUsage);
        assertEquals(votingUsage.getWhereUsed(), proRegTx.getTxId());
        assertEquals(AuthenticationKeyStatus.CURRENT, votingUsage.getStatus());

        AuthenticationKeyUsage ownerUsage = mnext.getKeyUsage().get(ownerKey);
        assertNotNull(ownerUsage);
        assertEquals(ownerUsage.getWhereUsed(), proRegTx.getTxId());
        assertEquals(AuthenticationKeyStatus.CURRENT, ownerUsage.getStatus());

        // is something wrong with some hashCode's why is it not found...
        AuthenticationKeyUsage operatorUsage = mnext.getKeyUsage().get(operatorKey);
        assertNotNull(operatorUsage);
        assertEquals(operatorUsage.getWhereUsed(), proRegTx.getTxId());
        assertEquals(AuthenticationKeyStatus.CURRENT, ownerUsage.getStatus());

        AuthenticationKeyUsage platformUsage = mnext.getKeyUsage().get(platformKey);
        assertNull(null, platformUsage);
    }
}
