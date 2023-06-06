/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

package org.bitcoinj.crypto.ed25519;

import com.google.protobuf.ByteString;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.factory.Ed25519KeyFactory;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.Protos.ScryptParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.util.encoders.Base64;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class Ed25519KeyTest {
    private static final Logger log = LoggerFactory.getLogger(Ed25519KeyTest.class);

    private KeyCrypter keyCrypter;

    private static CharSequence PASSWORD1 = "my hovercraft has eels";
    private static CharSequence WRONG_PASSWORD = "it is a snowy day today";
    private static final NetworkParameters TESTNET = TestNet3Params.get();
    private static final NetworkParameters MAINNET = MainNetParams.get();
    private static final NetworkParameters UNITTEST = UnitTestParams.get();

    private static final byte[] privateKeyBytes = HEX.decode("171cb88b1b3c1db25add599712e36245d75bc65a1a5c9e18d76f9f2b1eab4012");
    private static final byte[] publicKeyBytes = HEX.decode("008fe9693f8fa62a4305a140b9764c5ee01e455963744fe18204b4fb948249308a");

    @Before
    public void setUp() throws Exception {
        ScryptParameters.Builder scryptParametersBuilder = ScryptParameters.newBuilder().setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt()));
        ScryptParameters scryptParameters = scryptParametersBuilder.build();
        keyCrypter = new KeyCrypterScrypt(scryptParameters);

        BriefLogFormatter.init();
    }

    @Test
    public void keyTest() {

        SecureRandom random = new SecureRandom();

        // Create a key pair generator for Ed25519
        Ed25519KeyPairGenerator generator = new Ed25519KeyPairGenerator();

        // Initialize the key pair generator with a random seed
        generator.init(new Ed25519KeyGenerationParameters(random));

        // Generate a new key pair
        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

        // Get the public and private key parameters
        Ed25519PublicKeyParameters publicKey = (Ed25519PublicKeyParameters) keyPair.getPublic();
        Ed25519PrivateKeyParameters privateKey = (Ed25519PrivateKeyParameters) keyPair.getPrivate();

        // Print the public and private keys in hexadecimal format
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(privateKey.getEncoded(), 0);
        Ed25519PublicKeyParameters pub = priv.generatePublicKey();
        assertArrayEquals(publicKey.getEncoded(), pub.getEncoded());

        byte[] privateKey2 = HEX.decode("171cb88b1b3c1db25add599712e36245d75bc65a1a5c9e18d76f9f2b1eab4012");
        byte[] publicKey2 = HEX.decode("008fe9693f8fa62a4305a140b9764c5ee01e455963744fe18204b4fb948249308a");

        Ed25519Key fromPrivateKey = Ed25519Key.fromPrivate(privateKey2, true);
        Ed25519Key fromPublicKey = Ed25519Key.fromPublicOnly(publicKey2);
        Ed25519Key fromBothKeys = Ed25519Key.fromPrivateAndPrecalculatedPublic(privateKey2, publicKey2);

        assertArrayEquals(privateKey2, fromPrivateKey.getPrivKeyBytes());
        assertArrayEquals(publicKey2, fromPrivateKey.getPubKey());
        assertArrayEquals(publicKey2, fromPublicKey.getPubKey());
        assertArrayEquals(privateKey2, fromBothKeys.getPrivKeyBytes());
        assertArrayEquals(publicKey2, fromBothKeys.getPubKey());
    }

    @Test
    public void base58Encoding() throws Exception {
        String addr = "yZfP1PDBWobBScQ87A6rkNy5sQQcYYRDHT";
        String privkey = "cUZyChqZZkcdMGvq126fceYdNxyq812hji2LdPoapgb3FHSNVhjw";
        IKey key = DumpedPrivateKey.fromBase58(TestNet3Params.get(), privkey).getKey(Ed25519KeyFactory.get());
        assertEquals(privkey, key.getPrivateKeyEncoded(TestNet3Params.get()).toString());
        assertEquals(addr, Address.fromKey(TestNet3Params.get(), key).toString());
    }

    @Test
    public void base58Encoding_stress() throws Exception {
        // Replace the loop bound with 1000 to get some keys with leading zero byte
        for (int i = 0 ; i < 20 ; i++) {
            Ed25519Key key = new Ed25519Key();
            IKey key1 = DumpedPrivateKey.fromBase58(TESTNET,
                    key.getPrivateKeyEncoded(TESTNET).toString()).getKey(key.getKeyFactory());
            assertEquals(Utils.HEX.encode(key.getPrivKeyBytes()),
                    Utils.HEX.encode(key1.getPrivKeyBytes()));
        }
    }

    @Test
    public void signTextMessage() throws Exception {
        Ed25519Key key = new Ed25519Key();
        String message = "聡中本";
        String signatureBase64 = key.signMessage(message);
        log.info("Message signed with " + Address.fromKey(MAINNET, key) + ": " + signatureBase64);
        // Should verify correctly.
        key.verifyMessage(message, signatureBase64);
        try {
            key.verifyMessage("Evil attacker says hello!", signatureBase64);
            fail();
        } catch (SignatureException e) {
            // OK.
        }
    }

    @Test
    public void verifyMessage() throws Exception {
        String message = "hello";
        byte[] publicKeyBytes = HEX.decode("00b5095e3f545e3f2b3699c600037d1c2ae49e79d1ecca15e3cd9d057bfa498a13");
        Ed25519Key key = Ed25519Key.fromPublicOnly(publicKeyBytes);
        String sigBase64 = "4rjByyJ9rA3drt1Ctw4wfO4TjQZ5oTfDciK7vPaio04j6tZYd/umvg9qB/L1QAIQ/TyvKxxfjjpH15z+3AQiCg==";
        key.verifyMessage(message, sigBase64);
    }

    @Test
    public void testUnencryptedCreate() throws Exception {
        Utils.setMockClock();
        Ed25519Key key = new Ed25519Key();
        long time = key.getCreationTimeSeconds();
        assertNotEquals(0, time);
        assertFalse(key.isEncrypted());
        byte[] originalPrivateKeyBytes = key.getPrivKeyBytes();
        Ed25519Key encryptedKey = key.encrypt(keyCrypter, keyCrypter.deriveKey(PASSWORD1));
        assertEquals(time, encryptedKey.getCreationTimeSeconds());
        assertTrue(encryptedKey.isEncrypted());
        assertNull(encryptedKey.getSecretBytes());
        key = encryptedKey.decrypt(keyCrypter.deriveKey(PASSWORD1));
        assertFalse(key.isEncrypted());
        assertArrayEquals(originalPrivateKeyBytes, key.getPrivKeyBytes());
    }

    @Test
    public void testEncryptedCreate() throws Exception {
        Ed25519Key unencryptedKey = new Ed25519Key();
        byte[] originalPrivateKeyBytes = checkNotNull(unencryptedKey.getPrivKeyBytes());
        log.info("Original private key = " + Utils.HEX.encode(originalPrivateKeyBytes));
        EncryptedData encryptedPrivateKey = keyCrypter.encrypt(unencryptedKey.getPrivKeyBytes(), keyCrypter.deriveKey(PASSWORD1));
        Ed25519Key encryptedKey = Ed25519Key.fromEncrypted(encryptedPrivateKey, keyCrypter, unencryptedKey.getPubKey());
        assertTrue(encryptedKey.isEncrypted());
        assertNull(encryptedKey.getSecretBytes());
        Ed25519Key rebornUnencryptedKey = encryptedKey.decrypt(keyCrypter.deriveKey(PASSWORD1));
        assertFalse(rebornUnencryptedKey.isEncrypted());
        assertArrayEquals(originalPrivateKeyBytes, rebornUnencryptedKey.getPrivKeyBytes());
    }

    @Test
    public void testEncryptionIsReversible() throws Exception {
        Ed25519Key originalUnencryptedKey = new Ed25519Key();
        EncryptedData encryptedPrivateKey = keyCrypter.encrypt(originalUnencryptedKey.getPrivKeyBytes(), keyCrypter.deriveKey(PASSWORD1));
        Ed25519Key encryptedKey = Ed25519Key.fromEncrypted(encryptedPrivateKey, keyCrypter, originalUnencryptedKey.getPubKey());

        // The key should be encrypted
        assertTrue("Key not encrypted at start",  encryptedKey.isEncrypted());

        // Check that the key can be successfully decrypted back to the original.
        assertTrue("Key encryption is not reversible but it should be", Ed25519Key.encryptionIsReversible(originalUnencryptedKey, encryptedKey, keyCrypter, keyCrypter.deriveKey(PASSWORD1)));

        // Check that key encryption is not reversible if a password other than the original is used to generate the AES key.
        assertFalse("Key encryption is reversible with wrong password", Ed25519Key.encryptionIsReversible(originalUnencryptedKey, encryptedKey, keyCrypter, keyCrypter.deriveKey(WRONG_PASSWORD)));

        // Change one of the encrypted key bytes (this is to simulate a faulty keyCrypter).
        // Encryption should not be reversible
        byte[] goodEncryptedPrivateKeyBytes = encryptedPrivateKey.encryptedBytes;

        // Break the encrypted private key and check it is broken.
        byte[] badEncryptedPrivateKeyBytes = new byte[goodEncryptedPrivateKeyBytes.length];
        encryptedPrivateKey = new EncryptedData(encryptedPrivateKey.initialisationVector, badEncryptedPrivateKeyBytes);
        Ed25519Key badEncryptedKey = Ed25519Key.fromEncrypted(encryptedPrivateKey, keyCrypter, originalUnencryptedKey.getPubKey());
        assertFalse("Key encryption is reversible with faulty encrypted bytes", Ed25519Key.encryptionIsReversible(originalUnencryptedKey, badEncryptedKey, keyCrypter, keyCrypter.deriveKey(PASSWORD1)));
    }

    @Test
    public void testToString() throws Exception {
        Ed25519Key key = Ed25519Key.fromPrivate(privateKeyBytes); // An example private key.
        NetworkParameters params = MAINNET;
        IKey key2 = DumpedPrivateKey.fromBase58(MAINNET, "7qYrzJZWqnyCWMYswFcqaRJypGdVceudXPSxmZKsngN7h64GgGy").getKey(Ed25519KeyFactory.get());
        assertEquals("Ed25519Key{pub HEX=008fe9693f8fa62a4305a140b9764c5ee01e455963744fe18204b4fb948249308a, isEncrypted=false, isPubKeyOnly=false}", key.toString());
        assertEquals("Ed25519Key{pub HEX=008fe9693f8fa62a4305a140b9764c5ee01e455963744fe18204b4fb948249308a, priv HEX=171cb88b1b3c1db25add599712e36245d75bc65a1a5c9e18d76f9f2b1eab4012, priv WIF=XC4ZQcjpa8DtVwyreXWDKx7kvMeko6aV1GGmpznyFrUwuekwsY9w, isEncrypted=false, isPubKeyOnly=false}", key.toStringWithPrivate(null, params));
    }

    @Test
    public void testGetPrivateKeyAsHex() throws Exception {
        Ed25519Key key = Ed25519Key.fromPrivate(privateKeyBytes); // An example private key.
        assertEquals("171cb88b1b3c1db25add599712e36245d75bc65a1a5c9e18d76f9f2b1eab4012", key.getPrivateKeyAsHex());
    }

    @Test
    public void testGetPublicKeyAsHex() throws Exception {
        Ed25519Key key = Ed25519Key.fromPrivate(privateKeyBytes, true); // An example private key.
        assertEquals("008fe9693f8fa62a4305a140b9764c5ee01e455963744fe18204b4fb948249308a", key.getPublicKeyAsHex());
    }

    @Test
    public void roundTripDumpedPrivKey() throws Exception {
        Ed25519Key key = new Ed25519Key();
        assertTrue(key.isCompressed());
        String base58 = key.getPrivateKeyEncoded(UNITTEST).toString();
        IKey key2 = DumpedPrivateKey.fromBase58(UNITTEST, base58).getKey(key.getKeyFactory());
        assertTrue(key2.isCompressed());
        assertArrayEquals(key.getPrivKeyBytes(), key2.getPrivKeyBytes());
        assertArrayEquals(key.getPubKey(), key2.getPubKey());
    }
//
    @Test
    public void clear() throws Exception {
        Ed25519Key unencryptedKey = new Ed25519Key();
        Ed25519Key encryptedKey = (new Ed25519Key()).encrypt(keyCrypter, keyCrypter.deriveKey(PASSWORD1));

        checkSomeBytesAreNonZero(unencryptedKey.getPrivKeyBytes());

        // The encryptedPrivateKey should be null in an unencrypted Ed25519Key anyhow but check all the same.
        assertTrue(unencryptedKey.getEncryptedPrivateKey() == null);

        checkSomeBytesAreNonZero(encryptedKey.getSecretBytes());
        checkSomeBytesAreNonZero(encryptedKey.getEncryptedPrivateKey().encryptedBytes);
        checkSomeBytesAreNonZero(encryptedKey.getEncryptedPrivateKey().initialisationVector);
    }

    @Test
    public void isPubKeyCompressed() {
        assertFalse(Ed25519Key.isPubKeyCompressed(HEX.decode(
                "cfb4113b3387637131ebec76871fd2760fc430dd16de0110f0eb07bb31ffac85e2607c189cb8582ea1ccaeb64ffd655409106589778f3000fdfe3263440b0350")));
        assertTrue(Ed25519Key.isPubKeyCompressed(HEX.decode(
                "006d27f617ce7b0cbdce0abebd1c7aafc147bd406276e6a08d64d7a7ed0ca68f0e")));
        assertTrue(Ed25519Key.isPubKeyCompressed(HEX.decode(
                "0038746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void isPubKeyCompressed_tooShort() {
        Ed25519Key.isPubKeyCompressed(HEX.decode("036d"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void isPubKeyCompressed_illegalSign() {
        Ed25519Key.isPubKeyCompressed(HEX.decode("0438746c59d46d5408bf8b1d0af5740fe1a6e1703fcb56b2953f0b965c740d256f"));
    }

    private static boolean checkSomeBytesAreNonZero(byte[] bytes) {
        if (bytes == null) return false;
        for (byte b : bytes) if (b != 0) return true;
        return false;
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromPrivate_exceedsSize() {
        final byte[] bytes = new byte[33];
        bytes[0] = 42;
        Ed25519Key.fromPrivate(bytes);
    }

    @Test
    public void nodeIdfromPrivatePublicKey() {
        // node_key:
        //   id: 4991b7a8fecc8f6e034a421f5486ffa20d57b5b6
        //   private_key: >-
        //     oHamk0xHFklpM5BgIyt0yXPgkImcHhyUgEk3JQD5FmdDD3UrQ76Kn3L1xrLdwao+ras/1Sl9/nPrT6BNhzLTAQ==

        byte[] tenderDashKey = Base64.decode("oHamk0xHFklpM5BgIyt0yXPgkImcHhyUgEk3JQD5FmdDD3UrQ76Kn3L1xrLdwao+ras/1Sl9/nPrT6BNhzLTAQ==");
        byte[] nodeId = HEX.decode("4991b7a8fecc8f6e034a421f5486ffa20d57b5b6");
        byte[] privateKey = Arrays.copyOfRange(tenderDashKey, 0, 32);
        byte[] publicKey = new byte[33];
        System.arraycopy(tenderDashKey, 32, publicKey, 1, 32);

        Ed25519Key fromBoth = Ed25519Key.fromPrivateAndPrecalculatedPublic(privateKey, publicKey);
        Ed25519Key fromPrivate = Ed25519Key.fromPrivate(privateKey);
        Ed25519Key fromPublic = Ed25519Key.fromPublicOnly(publicKey);
        assertArrayEquals(privateKey, fromBoth.getPrivKeyBytes());
        assertArrayEquals(privateKey, fromPrivate.getPrivKeyBytes());

        assertArrayEquals(publicKey, fromBoth.getPubKey());
        assertArrayEquals(publicKey, fromPrivate.getPubKey());
        assertArrayEquals(publicKey, fromPublic.getPubKey());

        assertArrayEquals(nodeId, fromPrivate.getPubKeyHash());
        assertArrayEquals(nodeId, fromPublic.getPubKeyHash());
        assertArrayEquals(nodeId, fromBoth.getPubKeyHash());
    }
}
