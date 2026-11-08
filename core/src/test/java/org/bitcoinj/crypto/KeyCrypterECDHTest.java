/*
 * Copyright 2013 Jim Burton.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.crypto;

import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.FriendKeyChain;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KeyCrypterECDHTest {

    ECKey aliceKey;
    ECKey bobKey;

    static String secret = "my little secret is a pony that never sleeps";

    @Before
    public void setup() {
        aliceKey = ECKey.fromPrivate(Utils.HEX.decode("7337920e97ebfe34ee82137a26ef7bd296a3132f3c6494235e1f17820770aa01"));
        bobKey = ECKey.fromPrivate(Utils.HEX.decode("0086b1dd5edf5e19e17e086dbf296d8d697438fb11ef0dd044e186e634d3c99ff7"));
    }

    @Test
    public void testEncryptionAndDecryption() {
        //Alice is sending to Bob
        KeyCrypterECDH aliceKeyExchangeCrypter = new KeyCrypterECDH();
        KeyParameter aliceKeyParameter = aliceKeyExchangeCrypter.deriveKey(aliceKey, bobKey);

        EncryptedData encryptedData = aliceKeyExchangeCrypter.encrypt(secret.getBytes(), aliceKeyParameter);
        assertNotNull(encryptedData);

        //Bob is receiving from Alice
        KeyCrypterECDH bobKeyExchangeCrypter = new KeyCrypterECDH();
        KeyParameter bobKeyParameter = bobKeyExchangeCrypter.deriveKey(bobKey, aliceKey);
        byte [] decryptedData = bobKeyExchangeCrypter.decrypt(encryptedData, bobKeyParameter);
        assertNotNull(decryptedData);

        assertEquals("they should be the same string", new String(decryptedData), secret );
    }

    @Test
    public void deriveECDHwithPublicKeysTest() {
        ECKey alicePublicKey = ECKey.fromPublicOnly(aliceKey.getPubKey());
        ECKey bobPublicKey = ECKey.fromPublicOnly(bobKey.getPubKey());

        try {
            KeyCrypterECDH bobKeyExchangeCrypter = new KeyCrypterECDH();
            bobKeyExchangeCrypter.deriveKey(bobPublicKey, alicePublicKey);
            fail();
        } catch (IllegalArgumentException x) {
            // swallow, this is the correct exception
        } catch (Exception x) {
            fail();
        }
    }

    @Test
    public void ecdhDecryptionTest() {
        byte [] alicePrivateKey = Utils.HEX.decode("0000000000000000000000000000000000000000000000000000000000000001");
        byte [] alicePublicKey = Utils.HEX.decode("0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798");
        ECKey bobKeyPair = ECKey.fromPrivate(Utils.HEX.decode("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364140"));
        byte [] encryptedSecret = Utils.HEX.decode("eac5bcd6eb85074759e0261497428c9b3725d3b9ec4d739a842116277c6ace81549089be0d11a54ee09a99dcf7ac695a8ea56d41bf0b62def90b6f78f8b0aca9");
        //Alice is sending to Bob
        KeyCrypterECDH aliceKeyExchangeCrypter = new KeyCrypterECDH();

        KeyParameter aliceKeyParameter = aliceKeyExchangeCrypter.deriveKey(alicePrivateKey, bobKeyPair.getPubKey());
        assertEquals(32, aliceKeyParameter.getKeyLength());

        assertEquals("fbd27dbb9e7f471bf3de3704a35e884e37d35c676dc2cc8c3cc574c3962376d2", Utils.HEX.encode(aliceKeyParameter.getKey()));
        EncryptedData encryptedAliceData = aliceKeyExchangeCrypter.encrypt(secret.getBytes(),
                Arrays.copyOfRange(encryptedSecret, 0, 16), aliceKeyParameter);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(encryptedAliceData.encryptedBytes.length + encryptedAliceData.initialisationVector.length);

        assertArrayEquals(Arrays.copyOfRange(encryptedSecret, 0, 16), encryptedAliceData.initialisationVector);
        try {
            baos.write(encryptedAliceData.initialisationVector);
            baos.write(encryptedAliceData.encryptedBytes);
        } catch (IOException x) {
            fail();
        }
        assertArrayEquals("they should be the same data", encryptedSecret, baos.toByteArray());

        //Bob is receiving from Alice

        KeyCrypterECDH bobKeyExchangeCrypter = new KeyCrypterECDH();
        KeyParameter bobKeyParameter = bobKeyExchangeCrypter.deriveKey(bobKeyPair.getPrivKeyBytes(), alicePublicKey);
        assertEquals(32, bobKeyParameter.getKeyLength());

        EncryptedData encryptedData = new EncryptedData(Arrays.copyOfRange(encryptedSecret, 0, 16),
                Arrays.copyOfRange(encryptedSecret, 16, encryptedSecret.length));

        byte[] encryptedBobData = bobKeyExchangeCrypter.decrypt(encryptedData, bobKeyParameter);

        String decryptedSecret = new String(encryptedBobData);

        assertEquals("they should be the same string", secret, decryptedSecret);
    }

    /* this test uses data from this site, which explained the difference in ECDH implementations
      https://crypto.stackexchange.com/questions/57695/varying-ecdh-key-output-on-different-ecc-implementations-on-the-secp256k1-curve
     */
    @Test
    public void ecdhTest() {
        String privkey1 = "82fc9947e878fc7ed01c6c310688603f0a41c8e8704e5b990e8388343b0fd465";
        String privkey2 = "5f706787ac72c1080275c1f398640fb07e9da0b124ae9734b28b8d0f01eda586";

        ECKey aliceKeyPair = ECKey.fromPrivate(Utils.HEX.decode(privkey1), true);
        ECKey bobKeyPair = ECKey.fromPrivate(Utils.HEX.decode(privkey2), true);

        KeyCrypterECDH keyExchangeCrypter = new KeyCrypterECDH();
        KeyParameter keyParameter = keyExchangeCrypter.deriveKey(aliceKeyPair, bobKeyPair);

        assertEquals("5935d0476af9df2998efb60383adf2ff23bc928322cfbb738fca88e49d557d7e", Utils.HEX.encode(keyParameter.getKey()));
    }

    // ---------------------------------------------------------------------------------------------
    // Standard vs legacy (#295) key.
    //
    // The pairs below are aliceKey (see setup) with a Bob key = SHA256("dashj-ecdh-test-" + i), for the
    // first i (searching from 0) whose standard shared hash has the given shape. They were found by an
    // independent pure-Python secp256k1 implementation, which also produced the expected hashes.
    // ---------------------------------------------------------------------------------------------

    /** first i whose hash starts with FF followed by a byte >= 0x80: the only shape where #295 changed the key */
    private static final int FF_HI_SEED = 498;
    private static final String FF_HI_STANDARD = "ffc083b997bab8c1018c1655d06009f783c6e54ec5393b5e3aa047cd4c5f7e74";
    private static final String FF_HI_LEGACY = "00c083b997bab8c1018c1655d06009f783c6e54ec5393b5e3aa047cd4c5f7e74";
    /** first i whose hash starts with FF followed by a byte < 0x80 (negative BigInteger, but no change) */
    private static final int FF_LO_SEED = 63;
    private static final String FF_LO_STANDARD = "ff69b678c43982fa534c90dfcc7fe384ed9499326a97bec7d7fb504d7e2c14dd";
    /** first i whose hash starts with 00 followed by a byte < 0x80 (BigInteger drops the 00, padding restores it) */
    private static final int ZERO_LO_SEED = 132;
    private static final String ZERO_LO_STANDARD = "005fc60c6e256e6a0d2df727da02d1cb9f1d4739986d4fd4ff74a10816ad977b";
    /** first i whose hash starts with 00 followed by a byte >= 0x80 */
    private static final int ZERO_HI_SEED = 187;
    private static final String ZERO_HI_STANDARD = "009520aa656360c3cb8854ef8ed7485f1dc792a2bab23aa0e98c334762a25f0b";

    private static final byte[] FIXED_IV = Utils.HEX.decode("000102030405060708090a0b0c0d0e0f");

    private static ECKey seedKey(int i) {
        return ECKey.fromPrivate(Sha256Hash.hash(("dashj-ecdh-test-" + i).getBytes(StandardCharsets.UTF_8)));
    }

    /** SHA256 of the compressed shared point = SHA256((y & 1 | 0x02) || x), computed without KeyCrypterECDH */
    private static byte[] rawSharedHash(ECKey priv, ECKey pub) {
        return Sha256Hash.hash(pub.getPubKeyPoint().multiply(priv.getPrivKey()).normalize().getEncoded(true));
    }

    @Test
    public void standardKeyIsRawSha256ForFfHiPair() {
        ECKey bob = seedKey(FF_HI_SEED);
        KeyCrypterECDH crypter = new KeyCrypterECDH();

        assertEquals(FF_HI_STANDARD, Utils.HEX.encode(rawSharedHash(aliceKey, bob)));
        assertEquals(FF_HI_STANDARD, Utils.HEX.encode(crypter.deriveKey(aliceKey, bob).getKey()));
        assertEquals(FF_HI_STANDARD, Utils.HEX.encode(crypter.deriveKey(bob, aliceKey).getKey()));

        // the #295 form: leading FF replaced by 00
        assertEquals(FF_HI_LEGACY, Utils.HEX.encode(crypter.deriveLegacyKey(aliceKey, bob).getKey()));
        assertEquals(FF_HI_LEGACY, Utils.HEX.encode(crypter.deriveLegacyKey(bob, aliceKey).getKey()));

        List<KeyParameter> candidates = crypter.deriveCandidateKeys(bob, aliceKey);
        assertEquals(2, candidates.size());
        assertEquals(FF_HI_STANDARD, Utils.HEX.encode(candidates.get(0).getKey()));
        assertEquals(FF_HI_LEGACY, Utils.HEX.encode(candidates.get(1).getKey()));
    }

    @Test
    public void legacyEncryptedDataDecryptsViaFallback() {
        ECKey bob = seedKey(FF_HI_SEED);
        KeyCrypterECDH crypter = new KeyCrypterECDH();

        // what dashj (#295) wrote for this pair: encrypted with the legacy key
        EncryptedData legacyData = crypter.encrypt(secret.getBytes(), FIXED_IV, crypter.deriveLegacyKey(aliceKey, bob));

        // the standard key cannot read it ...
        try {
            crypter.decrypt(legacyData, crypter.deriveKey(bob, aliceKey));
            fail("standard key should not decrypt legacy data");
        } catch (KeyCrypterException.InvalidCipherText x) {
            // expected
        }
        // ... the fallback can
        assertEquals(secret, new String(crypter.decryptWithFallback(legacyData, bob, aliceKey)));
        assertEquals(secret, new String(crypter.decryptWithFallback(legacyData, aliceKey, bob)));
    }

    @Test
    public void standardEncryptedDataDecryptsForFfHiPair() {
        ECKey bob = seedKey(FF_HI_SEED);
        KeyCrypterECDH crypter = new KeyCrypterECDH();

        // what libsecp256k1 / iOS / the Rust SDK (and dashj from now on) write: encrypted with the standard key
        EncryptedData standardData = crypter.encrypt(secret.getBytes(), FIXED_IV, crypter.deriveKey(aliceKey, bob));

        assertEquals(secret, new String(crypter.decrypt(standardData, crypter.deriveKey(bob, aliceKey))));
        assertEquals(secret, new String(crypter.decryptWithFallback(standardData, bob, aliceKey)));

        // the legacy key could not read it, which was the bug
        try {
            crypter.decrypt(standardData, crypter.deriveLegacyKey(bob, aliceKey));
            fail("legacy key should not decrypt standard data");
        } catch (KeyCrypterException.InvalidCipherText x) {
            // expected
        }

        // random IV round trip through the public API
        EncryptedData randomIv = crypter.encrypt(secret.getBytes(), crypter.deriveKey(aliceKey, bob));
        assertEquals(secret, new String(crypter.decryptWithFallback(randomIv, bob, aliceKey)));
    }

    @Test
    public void legacyKeyEqualsStandardForOtherPairs() {
        KeyCrypterECDH crypter = new KeyCrypterECDH();
        int[] seeds = {FF_LO_SEED, ZERO_LO_SEED, ZERO_HI_SEED};
        String[] expected = {FF_LO_STANDARD, ZERO_LO_STANDARD, ZERO_HI_STANDARD};
        for (int i = 0; i < seeds.length; i++) {
            ECKey bob = seedKey(seeds[i]);
            assertEquals(expected[i], Utils.HEX.encode(rawSharedHash(aliceKey, bob)));
            assertEquals(expected[i], Utils.HEX.encode(crypter.deriveKey(aliceKey, bob).getKey()));
            assertEquals(expected[i], Utils.HEX.encode(crypter.deriveLegacyKey(aliceKey, bob).getKey()));
            List<KeyParameter> candidates = crypter.deriveCandidateKeys(bob, aliceKey);
            assertEquals("no second attempt when the keys agree", 1, candidates.size());
            assertEquals(expected[i], Utils.HEX.encode(candidates.get(0).getKey()));
        }

        // the original pair and the known-answer pair are ordinary too
        assertEquals(1, crypter.deriveCandidateKeys(aliceKey, bobKey).size());
        ECKey kat1 = ECKey.fromPrivate(Utils.HEX.decode("82fc9947e878fc7ed01c6c310688603f0a41c8e8704e5b990e8388343b0fd465"));
        ECKey kat2 = ECKey.fromPrivate(Utils.HEX.decode("5f706787ac72c1080275c1f398640fb07e9da0b124ae9734b28b8d0f01eda586"));
        assertArrayEquals(crypter.deriveKey(kat1, kat2).getKey(), crypter.deriveLegacyKey(kat1, kat2).getKey());
    }

    @Test
    public void ordinaryPairDoesNotRetryWithAnotherKey() {
        ECKey bob = seedKey(ZERO_LO_SEED);
        KeyCrypterECDH crypter = new KeyCrypterECDH();
        // data for a different pair: the single candidate fails and the standard key's error is thrown
        EncryptedData otherData = crypter.encrypt(secret.getBytes(), FIXED_IV, crypter.deriveKey(aliceKey, seedKey(FF_LO_SEED)));
        try {
            crypter.decryptWithFallback(otherData, bob, aliceKey);
            fail("should not decrypt");
        } catch (KeyCrypterException.InvalidCipherText x) {
            // expected
        }
    }

    @Test
    public void legacyKeyFromSharedSecretShapes() {
        byte[] ffHi = Utils.HEX.decode("ff80" + repeat("11", 30));
        assertEquals("0080" + repeat("11", 30), Utils.HEX.encode(KeyCrypterECDH.legacyKeyFromSharedSecret(ffHi)));
        byte[] ffffHi = Utils.HEX.decode("ffff80" + repeat("11", 29));
        assertEquals("000080" + repeat("11", 29), Utils.HEX.encode(KeyCrypterECDH.legacyKeyFromSharedSecret(ffffHi)));
        byte[] ffff7f = Utils.HEX.decode("ffff7f" + repeat("11", 29));
        assertEquals("00ff7f" + repeat("11", 29), Utils.HEX.encode(KeyCrypterECDH.legacyKeyFromSharedSecret(ffff7f)));

        // shapes where #295 and the standard key agree
        for (String unchanged : new String[] {"ff7f", "fe80", "8000", "0080", "007f", "0000", "7fff"}) {
            byte[] hash = Utils.HEX.decode(unchanged + repeat("11", 30));
            assertArrayEquals(unchanged, hash, KeyCrypterECDH.legacyKeyFromSharedSecret(hash));
        }
        byte[] allZero = new byte[32];
        assertArrayEquals(allZero, KeyCrypterECDH.legacyKeyFromSharedSecret(allZero));
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}
