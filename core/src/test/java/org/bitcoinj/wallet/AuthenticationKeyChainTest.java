package org.bitcoinj.wallet;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.crypto.*;
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

    DeterministicKeyChain voting;
    DeterministicKeyChain owner;
    DeterministicKeyChain bu;

    DeterministicKey ownerKeyMaster;
    DeterministicKey ownerKey;
    KeyId ownerKeyId;

    DeterministicKey votingKeyMaster;
    DeterministicKey votingKey;
    KeyId votingKeyId;

    DeterministicKey buKeyMaster;
    DeterministicKey buKey;
    KeyId buKeyId;

    ExtendedPrivateKey blsExtendedPrivateKey;
    BLSPublicKey operatorKey;
    BLSSecretKey operatorSecret;
    static {
        BLSJniLibrary.init();
    }

    @Before
    public void startup() throws UnreadableWalletException {
        PARAMS = UnitTestParams.get();
        context = new Context(PARAMS);

        seed = new DeterministicSeed(seedPhrase, null, "", 0);
        DeterministicKeyChain bip32 = new DeterministicKeyChain(seed, null, Script.ScriptType.P2PKH, DeterministicKeyChain.ACCOUNT_ZERO_PATH);
        bip32.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        bip32.getKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        DeterministicKeyChain active = new DeterministicKeyChain(seed, null,Script.ScriptType.P2PKH,  DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH_TESTNET);

        KeyChainGroup group = KeyChainGroup.builder(PARAMS).build();
        group.addAndActivateHDChain(bip32);
        group.addAndActivateHDChain(active);
        wallet = new Wallet(PARAMS, group);

        voting = new DeterministicKeyChain(seed, null, Script.ScriptType.P2PKH, DeterministicKeyChain.PROVIDER_VOTING_PATH_TESTNET);
        owner = new DeterministicKeyChain(seed, null, Script.ScriptType.P2PKH, DeterministicKeyChain.PROVIDER_OWNER_PATH_TESTNET);
        bu = new DeterministicKeyChain(seed, null, Script.ScriptType.P2PKH, DeterministicKeyChain.BLOCKCHAIN_USER_PATH_TESTNET);

        ownerKeyMaster = owner.getWatchingKey();//(ChildNumber.ZERO);
        ownerKey = HDKeyDerivation.deriveChildKey(ownerKeyMaster, ChildNumber.ZERO);
        ownerKeyId = new KeyId(ownerKey.getPubKeyHash());

        votingKeyMaster = voting.getWatchingKey();//(ChildNumber.ZERO);
        votingKey = HDKeyDerivation.deriveChildKey(votingKeyMaster, ChildNumber.ZERO);
        votingKeyId = new KeyId(votingKey.getPubKeyHash());

        buKeyMaster = bu.getWatchingKey();//(ChildNumber.ZERO);
        buKey = HDKeyDerivation.deriveChildKey(buKeyMaster, ChildNumber.ZERO);
        buKeyId = new KeyId(buKey.getPubKeyHash());

        blsExtendedPrivateKey = ExtendedPrivateKey.fromSeed(seed.getSeedBytes());

        PrivateKey operatorPrivateKey = blsExtendedPrivateKey.privateChild(new ChildNumber(9, true).getI())
                .privateChild(new ChildNumber(1, true).getI())
                .privateChild(new ChildNumber(3, true).getI())
                .privateChild(new ChildNumber(3, true).getI())
                .privateChild(0)
                .getPrivateKey();
        operatorKey = new BLSPublicKey(operatorPrivateKey.getG1Element());
        operatorSecret = new BLSSecretKey(operatorPrivateKey);
    }

    @Test
    public void authenticationKeyChainTest() {
        AuthenticationKeyChain owner = new AuthenticationKeyChain(seed, DeterministicKeyChain.PROVIDER_OWNER_PATH_TESTNET);
        DeterministicKey myOwnerKey = owner.getKey(KeyChain.KeyPurpose.AUTHENTICATION);
        assertEquals(myOwnerKey, ownerKey);

        AuthenticationKeyChain voting = new AuthenticationKeyChain(seed, DeterministicKeyChain.PROVIDER_VOTING_PATH_TESTNET);
        DeterministicKey myVotingKey = voting.getKey(KeyChain.KeyPurpose.AUTHENTICATION);
        assertEquals(myVotingKey, votingKey);

        AuthenticationKeyChain blockchainUser = new AuthenticationKeyChain(seed, DeterministicKeyChain.BLOCKCHAIN_USER_PATH_TESTNET);
        DeterministicKey myBlockchainUserKey = blockchainUser.getKey(KeyChain.KeyPurpose.AUTHENTICATION);
        assertEquals(myBlockchainUserKey, buKey);
        DeterministicKey bukeyIndexZero = blockchainUser.getKey(0);
        assertEquals(bukeyIndexZero, buKey);
    }
}
