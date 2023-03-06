package org.bitcoinj.wallet;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.bls.BLSDeterministicKey;
import org.bitcoinj.crypto.factory.BLSKeyFactory;
import org.bitcoinj.crypto.factory.ECKeyFactory;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.dashj.bls.BLSJniLibrary;
import org.dashj.bls.ExtendedPrivateKey;
import org.dashj.bls.PrivateKey;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AuthenticationKeyChainTest {
    Context context;
    UnitTestParams PARAMS;

    static String seedPhrase = "enemy check owner stumble unaware debris suffer peanut good fabric bleak outside";
    DeterministicSeed seed;
    Wallet wallet;

    AnyDeterministicKeyChain voting;
    AnyDeterministicKeyChain owner;
    AnyDeterministicKeyChain bu;
    AnyDeterministicKeyChain operator;

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

    final KeyFactory EC_KEY_FACTORY = ECKeyFactory.get();
    final KeyFactory BLS_KEY_FACTORY = BLSKeyFactory.get();
    static {
        BLSJniLibrary.init();
    }

    @Before
    public void startup() throws UnreadableWalletException {
        PARAMS = UnitTestParams.get();
        context = new Context(PARAMS);

        seed = new DeterministicSeed(seedPhrase, null, "", 0);
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

        ownerKeyMaster = owner.getWatchingKey();
        ownerKey = ownerKeyMaster.deriveChildKey(ChildNumber.ZERO);
        ownerKeyId = KeyId.fromBytes(ownerKey.getPubKeyHash());

        votingKeyMaster = voting.getWatchingKey();
        votingKey = votingKeyMaster.deriveChildKey(ChildNumber.ZERO);
        votingKeyId = KeyId.fromBytes(votingKey.getPubKeyHash());

        buKeyMaster = bu.getWatchingKey();
        buKey = buKeyMaster.deriveChildKey(ChildNumber.ZERO_HARDENED);
        buKeyId = KeyId.fromBytes(buKey.getPubKeyHash());

        operator = AuthenticationKeyChain.authenticationBuilder()
                .seed(seed)
                .accountPath(AnyDeterministicKeyChain.PROVIDER_OPERATOR_PATH_TESTNET)
                .type(AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR)
                .build();

        operatorKeyMaster = operator.getWatchingKey();
        operatorKey = operatorKeyMaster.deriveChildKey(ChildNumber.ZERO);
        operatorKeyId = KeyId.fromBytes(buKey.getPubKeyHash());
    }

    @Test
    public void authenticationKeyChainTest() {
        // check that derivatation matches an alternate method
        AuthenticationKeyChain owner = new AuthenticationKeyChain(seed, DeterministicKeyChain.PROVIDER_OWNER_PATH_TESTNET, EC_KEY_FACTORY);
        IDeterministicKey myOwnerKey = owner.getKey(KeyChain.KeyPurpose.AUTHENTICATION);
        assertEquals(myOwnerKey, ownerKey);

        AuthenticationKeyChain voting = new AuthenticationKeyChain(seed, DeterministicKeyChain.PROVIDER_VOTING_PATH_TESTNET, EC_KEY_FACTORY);
        IDeterministicKey myVotingKey = voting.getKey(KeyChain.KeyPurpose.AUTHENTICATION);
        assertEquals(myVotingKey, votingKey);

        AuthenticationKeyChain blockchainUser = new AuthenticationKeyChain(seed, DeterministicKeyChain.BLOCKCHAIN_USER_PATH_TESTNET, AuthenticationKeyChain.KeyChainType.BLOCKCHAIN_IDENTITY, true);
        IDeterministicKey myBlockchainUserKey = blockchainUser.getKey(0, true);
        assertEquals(myBlockchainUserKey, buKey);
        IDeterministicKey bukeyIndexZero = blockchainUser.getKey(0, true);
        assertEquals(bukeyIndexZero, buKey);

        ExtendedPrivateKey blsExtendedPrivateKey = ExtendedPrivateKey.fromSeed(seed.getSeedBytes());

        PrivateKey operatorPrivateKey = blsExtendedPrivateKey.privateChild(new ChildNumber(9, true).getI())
                .privateChild(new ChildNumber(1, true).getI())
                .privateChild(new ChildNumber(3, true).getI())
                .privateChild(new ChildNumber(3, true).getI())
                .privateChild(0)
                .getPrivateKey();
        BLSPublicKey blsOperatorKey = new BLSPublicKey(operatorPrivateKey.getG1Element());
        BLSSecretKey blsOperatorSecret = new BLSSecretKey(operatorPrivateKey);
        assertEquals(blsOperatorKey, operatorKey.getPubKeyObject());
        assertEquals(blsOperatorSecret, ((BLSDeterministicKey)operatorKey).getPrivKey());
    }
}
