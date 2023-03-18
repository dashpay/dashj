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

import org.bitcoinj.core.Context;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.AuthenticationKeyChain;
import org.bitcoinj.wallet.AuthenticationKeyChainGroup;
import org.bitcoinj.wallet.DerivationPathFactory;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletProtobufSerializer;
import org.bitcoinj.wallet.AnyDeterministicKeyChain;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AuthenticationKeyChainGroupTest {

    Context context;
    UnitTestParams PARAMS;

    static String seedPhrase = "enemy check owner stumble unaware debris suffer peanut good fabric bleak outside";
    DeterministicSeed seed;
    Wallet wallet;

    AnyDeterministicKeyChain voting;
    AnyDeterministicKeyChain owner;
    AnyDeterministicKeyChain bu;
    AnyDeterministicKeyChain operator;
    AnyDeterministicKeyChain platform;

    IDeterministicKey ownerKeyMaster;
    IDeterministicKey ownerKey;
    KeyId ownerKeyId;

    IDeterministicKey votingKeyMaster;
    IDeterministicKey votingKey;
    KeyId votingKeyId;

    IDeterministicKey buKeyMaster;
    IDeterministicKey buKey;
    KeyId buKeyId;

    IDeterministicKey operatorKeyMaster;
    IDeterministicKey operatorKey;
    KeyId operatorKeyId;
    IDeterministicKey platformKeyMaster;
    IDeterministicKey platformKey;
    KeyId platformKeyId;
    AuthenticationKeyChainGroup authGroup;

    DerivationPathFactory derivationPathFactory;
    @Before
    public void startup() throws UnreadableWalletException {
        PARAMS = UnitTestParams.get();
        context = new Context(PARAMS);
        derivationPathFactory = DerivationPathFactory.get(PARAMS);

        seed = new DeterministicSeed(seedPhrase, null, "", Utils.currentTimeSeconds());
        DeterministicKeyChain bip32 = DeterministicKeyChain.builder()
                .seed(seed)
                .outputScriptType(Script.ScriptType.P2PKH)
                .accountPath(DeterministicKeyChain.ACCOUNT_ZERO_PATH)
                .build();
        bip32.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        bip32.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        DeterministicKeyChain active = DeterministicKeyChain.builder()
                .seed(seed)
                .outputScriptType(Script.ScriptType.P2PKH)
                .accountPath(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH_TESTNET)
                .build();

        KeyChainGroup group = KeyChainGroup.builder(PARAMS).build();
        group.addAndActivateHDChain(bip32);
        group.addAndActivateHDChain(active);
        wallet = new Wallet(PARAMS, group);

        voting = AuthenticationKeyChain.authenticationBuilder()
                .seed(seed)
                .accountPath(AnyDeterministicKeyChain.PROVIDER_VOTING_PATH_TESTNET)
                .type(AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING)
                .build();
        owner = AuthenticationKeyChain.authenticationBuilder()
                .seed(seed)
                .accountPath(AnyDeterministicKeyChain.PROVIDER_OWNER_PATH_TESTNET)
                .type(AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER)
                .build();
        bu = AuthenticationKeyChain.authenticationBuilder()
                .seed(seed)
                .accountPath(AnyDeterministicKeyChain.BLOCKCHAIN_USER_PATH_TESTNET)
                .type(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY)
                .createHardenedChildren(true)
                .build();

        platform = AuthenticationKeyChain.authenticationBuilder()
                .seed(seed)
                .accountPath(derivationPathFactory.masternodePlatformDerivationPath())
                .type(AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR)
                .createHardenedChildren(true)
                .build();

        ownerKeyMaster = owner.getWatchingKey();
        ownerKey = ownerKeyMaster.deriveChildKey(ChildNumber.ZERO);
        ownerKeyId = KeyId.fromBytes(ownerKey.getPubKeyHash());

        votingKeyMaster = voting.getWatchingKey();
        votingKey = votingKeyMaster.deriveChildKey(ChildNumber.ZERO);
        votingKeyId = KeyId.fromBytes(votingKey.getPubKeyHash());

        buKeyMaster = bu.getWatchingKey();
        buKey = buKeyMaster.deriveChildKey(ChildNumber.ZERO_HARDENED);
        buKeyId = KeyId.fromBytes(buKey.getPubKeyHash());

        platformKeyMaster = platform.getWatchingKey();
        platformKey = platformKeyMaster.deriveChildKey(ChildNumber.ZERO_HARDENED);
        platformKeyId = KeyId.fromBytes(platformKey.getPubKeyHash());

        operator = AuthenticationKeyChain.authenticationBuilder()
                .seed(seed)
                .accountPath(AnyDeterministicKeyChain.PROVIDER_OPERATOR_PATH_TESTNET)
                .type(AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR)
                .build();

        operatorKeyMaster = operator.getWatchingKey();
        operatorKey = operatorKeyMaster.deriveChildKey(ChildNumber.ZERO);
        operatorKeyId = KeyId.fromBytes(buKey.getPubKeyHash());

        // put these in the same Authentication Group
        authGroup = AuthenticationKeyChainGroup.authenticationBuilder(PARAMS)
                .addChain(voting)
                .addChain(owner)
                .addChain(operator)
                .addChain(bu)
                .addChain(platform)
                .build();

        wallet.initializeAuthenticationKeyChains(seed, null);
    }

    @Test
    public void keyChainTest() {
        assertTrue(authGroup.hasKeyChains());
        IDeterministicKey currentOperator = authGroup.currentKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR);
        IDeterministicKey currentOwner = authGroup.currentKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER);
        IDeterministicKey currentVoter = authGroup.currentKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING);
        IDeterministicKey currentIdentity = authGroup.currentKey(AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY);
        IDeterministicKey currentPlatform = authGroup.currentKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR);

        assertEquals(currentOwner, ownerKey);
        assertEquals(currentVoter, votingKey);
        assertEquals(currentOperator, operatorKey);
        assertEquals(currentIdentity, buKey);
        assertEquals(currentPlatform, platformKey);
    }

    @Test
    public void serializationTest() throws UnreadableWalletException {
        Protos.Wallet protos = new WalletProtobufSerializer().walletToProto(wallet);
        Wallet walletCopy = new WalletProtobufSerializer().readWallet(PARAMS, null, protos);

        assertEquals(wallet.currentAuthenticationKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER),
                walletCopy.currentAuthenticationKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER));

        assertEquals(wallet.currentAuthenticationKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR),
                walletCopy.currentAuthenticationKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR));

        assertEquals(wallet.currentAuthenticationKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR),
                walletCopy.currentAuthenticationKey(AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR));
    }
}
