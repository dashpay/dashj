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

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.ed25519.Ed25519DeterministicKey;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
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
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumSet;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AuthenticationGroupExtensionTest {
    Wallet wallet;
    AuthenticationGroupExtension mnext;
    UnitTestParams UNITTEST = UnitTestParams.get();
    Context context;
    String seedCode = "enemy check owner stumble unaware debris suffer peanut good fabric bleak outside";
    String passphrase = "";
    long creationtime = 1547463771L;

    @Before
    public void setUp() throws UnreadableWalletException {
        context = new Context(UNITTEST);

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
        mnext.addKeyChain(seed, factory.blockchainIdentityECDSADerivationPath(), AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY);
        mnext.addKeyChain(seed, factory.blockchainIdentityRegistrationFundingDerivationPath(), AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING);
        mnext.addKeyChain(seed, factory.blockchainIdentityTopupFundingDerivationPath(), AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP);
        mnext.addKeyChain(seed, factory.identityInvitationFundingDerivationPath(), AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING);
        wallet.addExtension(mnext);
    }

    @Test
    public void processTransactionsTest() {
        byte[] providerRegTxData = HEX.decode("0300010001ca9a43051750da7c5f858008f2ff7732d15691e48eb7f845c791e5dca78bab58010000006b483045022100fe8fec0b3880bcac29614348887769b0b589908e3f5ec55a6cf478a6652e736502202f30430806a6690524e4dd599ba498e5ff100dea6a872ebb89c2fd651caa71ed012103d85b25d6886f0b3b8ce1eef63b720b518fad0b8e103eba4e85b6980bfdda2dfdffffffff018e37807e090000001976a9144ee1d4e5d61ac40a13b357ac6e368997079678c888ac00000000fd1201010000000000ca9a43051750da7c5f858008f2ff7732d15691e48eb7f845c791e5dca78bab580000000000000000000000000000ffff010205064e1f3dd03f9ec192b5f275a433bfc90f468ee1a3eb4c157b10706659e25eb362b5d902d809f9160b1688e201ee6e94b40f9b5062d7074683ef05a2d5efb7793c47059c878dfad38a30fafe61575db40f05ab0a08d55119b0aad300001976a9144fbc8fb6e11e253d77e5a9c987418e89cf4a63d288ac3477990b757387cb0406168c2720acf55f83603736a314a37d01b135b873a27b411fb37e49c1ff2b8057713939a5513e6e711a71cff2e517e6224df724ed750aef1b7f9ad9ec612b4a7250232e1e400da718a9501e1d9a5565526e4b1ff68c028763");
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
        byte[] providerRegTxData = HEX.decode("0300010001ca9a43051750da7c5f858008f2ff7732d15691e48eb7f845c791e5dca78bab58010000006b483045022100fe8fec0b3880bcac29614348887769b0b589908e3f5ec55a6cf478a6652e736502202f30430806a6690524e4dd599ba498e5ff100dea6a872ebb89c2fd651caa71ed012103d85b25d6886f0b3b8ce1eef63b720b518fad0b8e103eba4e85b6980bfdda2dfdffffffff018e37807e090000001976a9144ee1d4e5d61ac40a13b357ac6e368997079678c888ac00000000fd1201010000000000ca9a43051750da7c5f858008f2ff7732d15691e48eb7f845c791e5dca78bab580000000000000000000000000000ffff010205064e1f3dd03f9ec192b5f275a433bfc90f468ee1a3eb4c157b10706659e25eb362b5d902d809f9160b1688e201ee6e94b40f9b5062d7074683ef05a2d5efb7793c47059c878dfad38a30fafe61575db40f05ab0a08d55119b0aad300001976a9144fbc8fb6e11e253d77e5a9c987418e89cf4a63d288ac3477990b757387cb0406168c2720acf55f83603736a314a37d01b135b873a27b411fb37e49c1ff2b8057713939a5513e6e711a71cff2e517e6224df724ed750aef1b7f9ad9ec612b4a7250232e1e400da718a9501e1d9a5565526e4b1ff68c028763");
        Transaction proRegTx = new Transaction(UNITTEST, providerRegTxData);
        assertTrue(mnext.isTransactionRevelant(proRegTx));
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

    @Test
    public void keyTest() throws UnreadableWalletException {
        DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);
        AuthenticationGroupExtension mnext = new AuthenticationGroupExtension(MainNetParams.get());
        mnext.addKeyChains(MainNetParams.get(), seed, EnumSet.of(
                AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR,
                AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER,
                AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING,
                AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR,
                AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY,
                AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING,
                AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP,
                AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING));
        IDeterministicKey votingKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING).getKey(0);
        IDeterministicKey ownerKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER).getKey(0);
        IDeterministicKey operatorKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR).getKey(0);
        IDeterministicKey platformKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR).getKey(0, true);
        IDeterministicKey identityKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY).getKey(0, true);
        IDeterministicKey identityAssetLockKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING).getKey(0);
        IDeterministicKey topupAssetLockKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP).getKey(0);
        IDeterministicKey inviteAssetLockKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING).getKey(0);

        // owner keys
        assertArrayEquals(Utils.HEX.decode("9e2dd89b24a63cc1e8b6d63d651c4bf5f90a4acb83cd5b2028f042c5a8653d05"), ownerKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("bfefac8ea784dc6261348efd847c99863c4707427bf02e3ca97eb356b7e3a3ad"), ownerKey.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("03877e1b244bea3d8428bb1d295b1a17a7970ed0c11b735b07536ef2e48b77c0fc"), ownerKey.getPubKey());
        assertArrayEquals(Utils.HEX.decode("9660052877ef6f3b5d1ed740b594a67f351352f3"), ownerKey.getPubKeyHash());
        assertEquals("XpPxDdCV5is57YvZyDejDchDH4GjUHrBc8", Address.fromKey(MainNetParams.get(), ownerKey).toBase58());

        // voting keys
        assertArrayEquals(Utils.HEX.decode("b41896d14cd59908b08f45c544ac9b565c7a225f3ba14178e2f89abe959f9c93"), votingKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("7d236be7e7cdbd44a679ff39028521fda0e7e026cf553fdb0eb355fe861c09b1"), votingKey.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("0231687b08194c86deab2092959165935be25cf9f45563c80d62662f0ce3f61d0a"), votingKey.getPubKey());
        assertArrayEquals(Utils.HEX.decode("0a2efcd4f98231bef5176d112c8db9315360f5ad"), votingKey.getPubKeyHash());
        assertEquals("XbcgsvqyH7FGYCcQ7wTL9MXTgUpADsrq6J", Address.fromKey(MainNetParams.get(), votingKey).toBase58());


        // operator keys
        assertArrayEquals(Utils.HEX.decode("ee116f2addea1d05dafb6ee24d81da8f2fd8d74ae94ae809493a3e3f8499a207"), operatorKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("0461b8370ac28e7d7c453ab769d6edc97e9a79e400ae100bfbbf7ea9d834fd2c"), operatorKey.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("8efa464e7d58cfd90f3bb1bac18598928ffc7c112d4c64455c0ec72915fcab770a402c439ccc728288f3369ebbd530a5"), operatorKey.getPubKey());

        // platform keys
        // from rust dash-core-shared
        // m/9'/5'/3'/4'/0' fingerprint 2497558984
        // m/9'/5'/3'/4'/0' chaincode 587dc2c7de6d36e06c6de0a2989cd8cb112c1e41b543002a5ff422f3eb1e8cd6
        // m/9'/5'/3'/4'/0' secret_key 7898dbaa7ab9b550e3befcd53dc276777ffc8a27124f830c04e17fcf74b9e071
        // m/9'/5'/3'/4'/0' public_key_data 08e2698fdcaa0af8416966ba9349b0c8dfaa80ed7f4094e032958a343e45f4b6
        // m/9'/5'/3'/4'/0' key_id c9bbba6a3ad5e87fb11af4f10458a52d3160259c
        // m/9'/5'/3'/4'/0' base64_keys eJjbqnq5tVDjvvzVPcJ2d3/8iicST4MMBOF/z3S54HEI4mmP3KoK+EFpZrqTSbDI36qA7X9AlOAylYo0PkX0tg==
        assertArrayEquals(Utils.HEX.decode("587dc2c7de6d36e06c6de0a2989cd8cb112c1e41b543002a5ff422f3eb1e8cd6"), platformKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("7898dbaa7ab9b550e3befcd53dc276777ffc8a27124f830c04e17fcf74b9e071"), platformKey.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("0008e2698fdcaa0af8416966ba9349b0c8dfaa80ed7f4094e032958a343e45f4b6"), platformKey.getPubKey());
        assertArrayEquals(Utils.HEX.decode("c9bbba6a3ad5e87fb11af4f10458a52d3160259c"), platformKey.getPubKeyHash());
        String privatePublicBase64 = ((Ed25519DeterministicKey)platformKey).getPrivatePublicBase64();
        assertEquals("eJjbqnq5tVDjvvzVPcJ2d3/8iicST4MMBOF/z3S54HEI4mmP3KoK+EFpZrqTSbDI36qA7X9AlOAylYo0PkX0tg==", privatePublicBase64);

        // identity keys
        assertArrayEquals(Utils.HEX.decode("4faf0c59063fb862e051b0691ced1403b93c49090333125e777801afd982523f"), identityKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("e9d761e24bfac24a226814e514ae8edb32d153c343dd9c97a4711e28374af45b"), identityKey.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("0274406ba086e5cd5627cdc86f0ddd26208b01a431d2f3bc85adfebfb986427068"), identityKey.getPubKey());
        assertArrayEquals(Utils.HEX.decode("b7f3790853a273580035f5073eb32d0f6f0d8cd7"), identityKey.getPubKeyHash());
        assertEquals("XsTVA9JcVatZNZaDFQLyiU6esnSJctMk61", Address.fromKey(MainNetParams.get(), identityKey).toBase58());

        // identity funding keys
        assertArrayEquals(Utils.HEX.decode("e0e19e2f2737248d52c83da8b4975bde14c8ed0b4791b7761abe4c752df1e04d"), identityAssetLockKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("7373b7b3972f7a6aaf724f4e995848964aa64f352a1e176ef112a875d132bcc2"), identityAssetLockKey.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("0385e1776ea0b87ffbc22ddfb43b23bb55d172589f7d3658d81d3031409f952c54"), identityAssetLockKey.getPubKey());
        assertArrayEquals(Utils.HEX.decode("e02011931ed41d1d723e1e6499eb08c1a33cfad5"), identityAssetLockKey.getPubKeyHash());
        assertEquals("Xw7ucPiM7wsWFbQaj4W4DmXqtceJGPhA92", Address.fromKey(MainNetParams.get(), identityAssetLockKey).toBase58());

        // topup funding keys
        assertArrayEquals(Utils.HEX.decode("2ee94cdfc5dba90ceb6dd89e79fce8a949fbdf8daedfe25889c3d5a287930662"), topupAssetLockKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("e54482d56dd010d166c933af6a45424be897d9ea1079da2ab8238fad9e25fc4b"), topupAssetLockKey.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("03e674116b1f9b79fe0f84c5668c195d2f84bce98f549a3b7e5e2b3019d4975673"), topupAssetLockKey.getPubKey());
        assertArrayEquals(Utils.HEX.decode("68498b3f25500320becdfd0c7867d7dcc79903eb"), topupAssetLockKey.getPubKeyHash());
        assertEquals("XkCGD8FX7HMQ1yYnHswNMikHu4sLJgVfWG", Address.fromKey(MainNetParams.get(), topupAssetLockKey).toBase58());

        // invite funding keys
        assertArrayEquals(Utils.HEX.decode("89deb042babf138e2e3c08dae5612d3f24464642af13159547fe6665d0217457"), inviteAssetLockKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("f176cb92188a8fa6364c3f5459ea2513d424d24c4707685a355f0a60c9a4c288"), inviteAssetLockKey.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("03c6c4b06e4b0899d8e80f8ccb24a6294277d913835cbd6615ea665b8969535e1e"), inviteAssetLockKey.getPubKey());
        assertArrayEquals(Utils.HEX.decode("b347814060b91175e609b5262fcc83a5c722110c"), inviteAssetLockKey.getPubKeyHash());
        assertEquals("Xs2nSqYpwb7cvyWikp5j8LHwEVo7ovCp96", Address.fromKey(MainNetParams.get(), inviteAssetLockKey).toBase58());
    }

    @Test
    public void encryptedSeedTest() throws UnreadableWalletException {
        KeyCrypterScrypt keyCrypterScrypt = new KeyCrypterScrypt(16);
        KeyParameter aesKey = keyCrypterScrypt.deriveKey("password");
        DeterministicSeed seed = new DeterministicSeed(seedCode, null, passphrase, creationtime);
        seed = seed.encrypt(keyCrypterScrypt, aesKey);

        wallet.encrypt(keyCrypterScrypt, aesKey);

        AuthenticationGroupExtension mnext = new AuthenticationGroupExtension(wallet);
        mnext.addEncryptedKeyChains(MainNetParams.get(), seed, aesKey, EnumSet.of(
                AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR,
                AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER,
                AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING,
                AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR,
                AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY,
                AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING,
                AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP,
                AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING));

        // owner keys
        IDeterministicKey ownerKey = mnext.getKeyChain(AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER).getKey(0);
        assertArrayEquals(Utils.HEX.decode("9e2dd89b24a63cc1e8b6d63d651c4bf5f90a4acb83cd5b2028f042c5a8653d05"), ownerKey.getChainCode());
        assertArrayEquals(Utils.HEX.decode("03877e1b244bea3d8428bb1d295b1a17a7970ed0c11b735b07536ef2e48b77c0fc"), ownerKey.getPubKey());
        assertArrayEquals(Utils.HEX.decode("9660052877ef6f3b5d1ed740b594a67f351352f3"), ownerKey.getPubKeyHash());
        assertEquals("XpPxDdCV5is57YvZyDejDchDH4GjUHrBc8", Address.fromKey(MainNetParams.get(), ownerKey).toBase58());
    }

    @Test
    public void loadWallet() throws IOException, UnreadableWalletException {
        TestNet3Params TESTNET = TestNet3Params.get();
        context = new Context(TESTNET);
        try (InputStream walletStream = Files.newInputStream(Paths.get(getClass().getResource("enemy-seed.wallet").getPath()))) {
            AuthenticationGroupExtension authenticationGroupExtension = new AuthenticationGroupExtension(TESTNET);
            WalletExtension[] walletExtensions = new WalletExtension[]{authenticationGroupExtension};
            Wallet wallet = new WalletProtobufSerializer().readWallet(walletStream, walletExtensions);

            // make sure this wallet has an authenticationGroupExtension
            assertTrue(wallet.getKeyChainExtensions().containsKey(AuthenticationGroupExtension.EXTENSION_ID));

            // check that rescanWallet() preserves the usage count
            int usageBefore = authenticationGroupExtension.getKeyUsage().size();
            authenticationGroupExtension.rescanWallet();
            int usageAfter = authenticationGroupExtension.getKeyUsage().size();
            assertEquals(usageBefore, usageAfter);
        }
    }

    @Test
    public void missingChainTypesTest() {
        assertFalse(mnext.missingAnyKeyChainTypes(EnumSet.of(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING)));
        assertFalse(mnext.missingAnyKeyChainTypes(EnumSet.of(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY,
                AuthenticationKeyChain.KeyChainType.INVITATION_FUNDING,
                AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_FUNDING,
                AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY_TOPUP)));
        assertTrue(mnext.missingAnyKeyChainTypes(EnumSet.of(AuthenticationKeyChain.KeyChainType.INVALID_KEY_CHAIN)));
    }

    @Test
    public void hasKeyChainTest() {
        assertFalse(mnext.hasKeyChain(ImmutableList.of())); // path: m
        assertFalse(mnext.hasKeyChain(DerivationPathFactory.get(UNITTEST).masternodeHoldingsDerivationPath()));
        assertTrue(mnext.hasKeyChain(DerivationPathFactory.get(UNITTEST).masternodeOperatorDerivationPath()));
    }

    @Test
    public void supportsBloomFiltersTest() {
        assertTrue(mnext.supportsBloomFilters());
    }
}
