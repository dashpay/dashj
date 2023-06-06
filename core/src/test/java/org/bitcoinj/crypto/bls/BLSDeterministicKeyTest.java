package org.bitcoinj.crypto.bls;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.crypto.factory.BLSKeyFactory;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.AnyDeterministicKeyChain;
import org.dashj.bls.ExtendedPrivateKey;
import org.dashj.bls.ExtendedPublicKey;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BLSDeterministicKeyTest {
    private final NetworkParameters UNITTEST = UnitTestParams.get();
    @Test
    public void legacyBLSFingerprintFromSeedTest() {
        BLSScheme.setLegacyDefault(true);
        byte[] seed = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        BLSKey keyPair = new BLSKey(seed);
        assertEquals(0xddad59bbL, keyPair.pub.getFingerprint(true));

        seed = new byte[]{1, 50, 6, (byte)244, 24, (byte)199, 1, 25};
        BLSKey keyPair2 = new BLSDeterministicKey(seed);
        assertEquals(0xa4700b27L, keyPair2.pub.getFingerprint(true));
    }
    @Test
    public void legacyBLSDerivationTest() {
        BLSScheme.setLegacyDefault(false);
        byte[] seed = new byte[]{1, 50, 6, (byte)244, 24, (byte)199, 1, 25};
        BLSDeterministicKey rootKey = new BLSDeterministicKey(seed);
        assertEquals(0xa4700b27L, rootKey.getPublicKey().getFingerprint(true));

        byte [] privateRoot = rootKey.serializePrivate(UNITTEST);
        byte [] publicRoot = rootKey.serializePublic(UNITTEST);

        byte [] expectedPrivateRoot = Utils.HEX.decode("00000001000000000000000000d8b12555b4cc5578951e4a7c80031e22019cc0dce168b3ed88115311b8feb1e33e9f7b3846c1803703f94c764b51f5ace513b2f02c4d6b2c452d8ce66e5975bd");
        byte [] expectedPublicRoot  = Utils.HEX.decode("00000001000000000000000000d8b12555b4cc5578951e4a7c80031e22019cc0dce168b3ed88115311b8feb1e30aa55db214bc456de83f84caf117d25fb76eafbcf21159571cdbc76627f629b6dc937128c259cae6ebaa180e45de957f");

        // skip the first 4 bytes (version) which will not match
        // the bls library doesn't know which network it is on.
        for (int i = 4; i < expectedPublicRoot.length; ++i) {
            if (expectedPublicRoot[i] != publicRoot[i])
                fail("privateRoot mismatch at index " + i);
        }
        for (int i = 4; i < expectedPrivateRoot.length; ++i) {
            if (expectedPrivateRoot[i] != privateRoot[i])
                fail("privateRoot mismatch at index " + i);
        }

        byte[] chainCode = rootKey.getChainCode();
        assertEquals("d8b12555b4cc5578951e4a7c80031e22019cc0dce168b3ed88115311b8feb1e3", Utils.HEX.encode(chainCode));

        BLSDeterministicKey derived77 = rootKey.derive(77);
        byte[] chainCode1 = derived77.getChainCode();
        assertEquals("f2c8e4269bb3e54f8179a5c6976d92ca14c3260dd729981e9d15f53049fd698b", Utils.HEX.encode(chainCode1));
        assertEquals(0xa8063dcfL, derived77.getPublicKey().getFingerprint(true));

        assertEquals(0xff26a31fL, rootKey.deriveSoftened(3).deriveSoftened(17).getPublicKey().getFingerprint(true));
        BLSDeterministicKey publicRootKey = rootKey.dropPrivateBytes();
        assertEquals(0xff26a31fL, publicRootKey.deriveSoftened(3).deriveSoftened(17).getPublicKey().getFingerprint(true));
    }

    @Test
    public void derivationDIP9Test() throws UnreadableWalletException {
        BLSScheme.setLegacyDefault(true);
        DeterministicSeed seed = new DeterministicSeed("bundle liberty enlist super solution elder minor shell fold vivid multiply throw", null, "", Utils.currentTimeSeconds());

        AnyDeterministicKeyChain chain = AnyDeterministicKeyChain.builder()
                .seed(seed)
                .accountPath(DeterministicKeyChain.PROVIDER_OPERATOR_PATH_TESTNET)
                .keyFactory(BLSKeyFactory.get())
                .build();

        BLSDeterministicKey firstOperatorKey = (BLSDeterministicKey) chain.getKeyByPath(HDUtils.append(DeterministicKeyChain.PROVIDER_OPERATOR_PATH_TESTNET, ChildNumber.ZERO));
        assertEquals("0cb6db5b5827e24138abb32e3476c5c7768bda0431c78030d62e9c5b107d3d73f5c78043401c5e8ad8f21a460a02bbbe", firstOperatorKey.pub.toStringHex(true));
        assertEquals("392e5db8a1fb59608723c9d77320909924dbb06e397ce195429a1c4a7fcefb45", firstOperatorKey.priv.toStringHex(true));
    }

    @Test
    public void publicPrivateDerivationTest() {
        ExtendedPrivateKey extendedPrivateKey = ExtendedPrivateKey.fromSeed(new byte[]{10, 9, 8});
        ExtendedPrivateKey hardenedChild = extendedPrivateKey.privateChild(0, true);
        ExtendedPrivateKey privateChild = hardenedChild.privateChild(1, true);
        ExtendedPublicKey publicChild = hardenedChild.publicChild(1);
        assertArrayEquals(privateChild.getChainCode().serialize(), publicChild.getChainCode().serialize());

        // derive another layer
        ExtendedPrivateKey privateSecondLevel = privateChild.privateChild(2, false);
        ExtendedPublicKey publicSecondLevel = publicChild.publicChild(2, false);
        assertArrayEquals(privateSecondLevel.getChainCode().serialize(), publicSecondLevel.getChainCode().serialize());
        assertArrayEquals(privateSecondLevel.getPublicKey().serialize(false), publicSecondLevel.getPublicKey().serialize(false));

        BLSDeterministicKey root = new BLSDeterministicKey(new byte []{5,4,3});
    }
}
