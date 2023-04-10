/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

package org.bitcoinj.crypto.bls;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.bls.BLSKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.crypto.factory.BLSKeyFactory;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.Protos.ScryptParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.core.Utils.HEX;
import static org.bitcoinj.core.Utils.reverseBytes;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BLSKeyTest {
    private static final Logger log = LoggerFactory.getLogger(BLSKeyTest.class);

    private KeyCrypter keyCrypter;

    private static CharSequence PASSWORD1 = "my hovercraft has eels";
    private static CharSequence WRONG_PASSWORD = "it is a snowy day today";
    private static final NetworkParameters TESTNET = TestNet3Params.get();
    private static final NetworkParameters MAINNET = MainNetParams.get();
    private static final NetworkParameters UNITTEST = UnitTestParams.get();

    private static byte[] PRIVATE_KEY = HEX.decode("117c779e44e36d3f84445ec67eec49470b0789eb633f102209af6e0ebd9c1bf9");

    @Before
    public void setUp() throws Exception {
        ScryptParameters.Builder scryptParametersBuilder = ScryptParameters.newBuilder().setSalt(ByteString.copyFrom(KeyCrypterScrypt.randomSalt()));
        ScryptParameters scryptParameters = scryptParametersBuilder.build();
        keyCrypter = new KeyCrypterScrypt(scryptParameters);

        BriefLogFormatter.init();
        BLSScheme.setLegacyDefault(true);
    }

    @Test
    public void testSignatures() throws Exception {
        // Test that we can construct an BLSKey from a private key (deriving the public from the private), then signing
        // a message with it.
        byte[] privkey = HEX.decode("180cb41c7c600be951b5d3d0a7334acc7506173875834f7a6c4c786a28fcbb19");
        BLSKey key = BLSKey.fromPrivate(privkey);
        byte[] output = key.sign(Sha256Hash.ZERO_HASH).bitcoinSerialize();
        assertTrue(key.verify(Sha256Hash.ZERO_HASH.getBytes(), output));

        // Test interop with a signature from elsewhere.
        byte[] sig = HEX.decode(
                "097b2427b58cf9c3806efc72fd5b885b8585692a6338ac675be4b44df6d0873759d5a5af7757ff9de5a8d156460e726d180c4d0314975f49ec584cd0264f2dc4d33b372e6fa73413c8b81bbf048f0e949ce2e3d162ed690ead6f15e9e8155161");
        assertTrue(key.verify(Sha256Hash.ZERO_HASH.getBytes(), sig));
    }

    @Test
    public void testKeyPairRoundtrip() throws Exception {
        BLSKey decodedKey = BLSKey.fromPrivate(PRIVATE_KEY);

        // Now re-encode and decode the ASN.1 to see if it is equivalent (it does not produce the exact same byte
        // sequence, some integers are padded now).
        BLSKey roundtripKey =
            BLSKey.fromPrivateAndPrecalculatedPublic(decodedKey.getPrivKey().bitcoinSerialize(), decodedKey.getPubKeyObject().bitcoinSerialize());

        for (BLSKey key : new BLSKey[] {decodedKey, roundtripKey}) {
            byte[] message = reverseBytes(HEX.decode(
                    "11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"));
            byte[] output = key.sign(Sha256Hash.wrap(message)).bitcoinSerialize();
            assertTrue(key.verify(message, output));

            output = HEX.decode(
                    "0a88ee8a2528ca2a3870736f81a7578af0986136467ddf650a1ef6090e068d6d5674b123cc44d8f673eb987a9926e9210283c14db921aeca7a09c443c469902837267e7387551dc60000cfeccfa382e73b0bd900df7ebbaddb702f9b51cc1210");
            assertTrue(key.verify(message, output));
        }
        
        // Try to sign with one key and verify with the other.
        byte[] message = reverseBytes(HEX.decode(
            "11da3761e86431e4a54c176789e41f1651b324d240d599a7067bee23d328ec2a"));
        assertTrue(roundtripKey.verify(message, decodedKey.sign(Sha256Hash.wrap(message)).bitcoinSerialize()));
        assertTrue(decodedKey.verify(message, roundtripKey.sign(Sha256Hash.wrap(message)).bitcoinSerialize()));

        // Verify bytewise equivalence of public keys (i.e. compression state is preserved)
        BLSKey key = new BLSKey();
        BLSKey key2 = BLSKey.fromPrivate(key.getPrivKeyBytes());
        assertArrayEquals(key.getPubKey(), key2.getPubKey());
    }

    @Test
    public void base58Encoding() throws Exception {
        String addr = "yfBEEriL3LJP8WNAFXZf5MxN7ARzx5Eokp";
        String privkey = "92HgQXyMXsDRKyiwYwAyG1LpS4j1dDdEZeuDmg7CwhFf2W8C56Z";
        IKey key = DumpedPrivateKey.fromBase58(TestNet3Params.get(), privkey).getKey(BLSKeyFactory.get());
        assertEquals(privkey, key.getPrivateKeyEncoded(TestNet3Params.get()).toString());
        assertEquals(addr, Address.fromKey(TestNet3Params.get(), key).toString());
    }

    @Test
    public void base58Encoding_leadingZero() throws Exception {
        String privkey = "91axuYLa8xK796DnBXXsMbjuc8pDYxYgJyQMvFzrZ6UfXaGYuqL";
        IKey key = DumpedPrivateKey.fromBase58(TESTNET, privkey).getKey(BLSKeyFactory.get());
        assertEquals(privkey, key.getPrivateKeyEncoded(TESTNET).toString());
        assertEquals(0, key.getPrivKeyBytes()[0]);
    }

    @Test
    public void base58Encoding_stress() throws Exception {
        // Replace the loop bound with 1000 to get some keys with leading zero byte
        for (int i = 0 ; i < 20 ; i++) {
            BLSKey key = new BLSKey();
            IKey key1 = DumpedPrivateKey.fromBase58(TESTNET,
                    key.getPrivateKeyEncoded(TESTNET).toString()).getKey(BLSKeyFactory.get());
            assertEquals(Utils.HEX.encode(key.getPrivKeyBytes()),
                    Utils.HEX.encode(key1.getPrivKeyBytes()));
        }
    }

    @Test
    public void signTextMessage() throws Exception {
        BLSKey key = new BLSKey();
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
        BLSSecretKey secretKey = new BLSSecretKey(PRIVATE_KEY);
        BLSPublicKey publicKey = secretKey.getPublicKey();
        String message = "hello";
        Sha256Hash hashMessage = Sha256Hash.twiceOf(Utils.formatMessageForSigning(message));
        BLSSignature sig = secretKey.sign(hashMessage);

        BLSKey key = new BLSKey(secretKey, publicKey);

        String sigBase64 = "lmo8PBn6VJ5EVtuA3pEMSiU7YPxlMbQQvV3UIVC3zWxA/t2gvOJXUq0lVEcrloAVFnA9vn7Cr2QQMcRX8TqEOFaYRF0P8Wmc6or3RMm3QjSEPqLVOMnFs4jomQvNvN8Z";
        key.verifyMessage(message, sigBase64);
    }

    @Test
    public void testUnencryptedCreate() throws Exception {
        Utils.setMockClock();
        BLSKey key = new BLSKey();
        long time = key.getCreationTimeSeconds();
        assertNotEquals(0, time);
        assertTrue(!key.isEncrypted());
        byte[] originalPrivateKeyBytes = key.getPrivKeyBytes();
        BLSKey encryptedKey = key.encrypt(keyCrypter, keyCrypter.deriveKey(PASSWORD1));
        assertEquals(time, encryptedKey.getCreationTimeSeconds());
        assertTrue(encryptedKey.isEncrypted());
        assertNull(encryptedKey.getSecretBytes());
        key = encryptedKey.decrypt(keyCrypter.deriveKey(PASSWORD1));
        assertTrue(!key.isEncrypted());
        assertArrayEquals(originalPrivateKeyBytes, key.getPrivKeyBytes());
    }

    @Test
    public void testEncryptedCreate() throws Exception {
        BLSKey unencryptedKey = new BLSKey();
        byte[] originalPrivateKeyBytes = checkNotNull(unencryptedKey.getPrivKeyBytes());
        log.info("Original private key = " + Utils.HEX.encode(originalPrivateKeyBytes));
        EncryptedData encryptedPrivateKey = keyCrypter.encrypt(unencryptedKey.getPrivKeyBytes(), keyCrypter.deriveKey(PASSWORD1));
        BLSKey encryptedKey = BLSKey.fromEncrypted(encryptedPrivateKey, keyCrypter, unencryptedKey.getPubKey());
        assertTrue(encryptedKey.isEncrypted());
        assertNull(encryptedKey.getSecretBytes());
        BLSKey rebornUnencryptedKey = encryptedKey.decrypt(keyCrypter.deriveKey(PASSWORD1));
        assertTrue(!rebornUnencryptedKey.isEncrypted());
        assertArrayEquals(originalPrivateKeyBytes, rebornUnencryptedKey.getPrivKeyBytes());
    }

    @Test
    public void testEncryptionIsReversible() throws Exception {
        BLSKey originalUnencryptedKey = new BLSKey();
        EncryptedData encryptedPrivateKey = keyCrypter.encrypt(originalUnencryptedKey.getPrivKeyBytes(), keyCrypter.deriveKey(PASSWORD1));
        BLSKey encryptedKey = BLSKey.fromEncrypted(encryptedPrivateKey, keyCrypter, originalUnencryptedKey.getPubKey());

        // The key should be encrypted
        assertTrue("Key not encrypted at start",  encryptedKey.isEncrypted());

        // Check that the key can be successfully decrypted back to the original.
        assertTrue("Key encryption is not reversible but it should be", BLSKey.encryptionIsReversible(originalUnencryptedKey, encryptedKey, keyCrypter, keyCrypter.deriveKey(PASSWORD1)));

        // Check that key encryption is not reversible if a password other than the original is used to generate the AES key.
        assertTrue("Key encryption is reversible with wrong password", !BLSKey.encryptionIsReversible(originalUnencryptedKey, encryptedKey, keyCrypter, keyCrypter.deriveKey(WRONG_PASSWORD)));

        // Change one of the encrypted key bytes (this is to simulate a faulty keyCrypter).
        // Encryption should not be reversible
        byte[] goodEncryptedPrivateKeyBytes = encryptedPrivateKey.encryptedBytes;

        // Break the encrypted private key and check it is broken.
        byte[] badEncryptedPrivateKeyBytes = new byte[goodEncryptedPrivateKeyBytes.length];
        encryptedPrivateKey = new EncryptedData(encryptedPrivateKey.initialisationVector, badEncryptedPrivateKeyBytes);
        BLSKey badEncryptedKey = BLSKey.fromEncrypted(encryptedPrivateKey, keyCrypter, originalUnencryptedKey.getPubKey());
        assertTrue("Key encryption is reversible with faulty encrypted bytes", !BLSKey.encryptionIsReversible(originalUnencryptedKey, badEncryptedKey, keyCrypter, keyCrypter.deriveKey(PASSWORD1)));
    }

    @Test
    public void testToString() throws Exception {
        BLSKey key = BLSKey.fromPrivate(PRIVATE_KEY).decompress(); // An example private key.
        NetworkParameters params = MAINNET;
        assertEquals("BLSKey{pub HEX=0f805ee206ecfc037e2998cca95042f5de40f1bf1c8ea03e31f48461a9ab9b604afdee6ccf4b4526b4189e1acdf160ec, isEncrypted=false, isPubKeyOnly=false}", key.toString());
        assertEquals("BLSKey{pub HEX=0f805ee206ecfc037e2998cca95042f5de40f1bf1c8ea03e31f48461a9ab9b604afdee6ccf4b4526b4189e1acdf160ec, priv HEX=117c779e44e36d3f84445ec67eec49470b0789eb633f102209af6e0ebd9c1bf9, isEncrypted=false, isPubKeyOnly=false}", key.toStringWithPrivate(null, params));
    }

    @Test
    public void testGetPrivateKeyAsHex() throws Exception {
        BLSKey key = BLSKey.fromPrivate(PRIVATE_KEY).decompress(); // An example private key.
        assertEquals("117c779e44e36d3f84445ec67eec49470b0789eb633f102209af6e0ebd9c1bf9", key.getPrivateKeyAsHex());
    }

    @Test
    public void testGetPublicKeyAsHex() throws Exception {
        BLSSecretKey key1 = BLSSecretKey.fromSeed(new byte[] {1, 2, 3});
        BLSKey key = BLSKey.fromPrivate(PRIVATE_KEY).decompress(); // An example private key.
        assertEquals("0f805ee206ecfc037e2998cca95042f5de40f1bf1c8ea03e31f48461a9ab9b604afdee6ccf4b4526b4189e1acdf160ec", key.getPublicKeyAsHex());
    }

    @Test
    public void roundTripDumpedPrivKey() throws Exception {
        BLSKey key = new BLSKey();
        String base58 = key.getPrivateKeyEncoded(UNITTEST).toString();
        IKey key2 = DumpedPrivateKey.fromBase58(UNITTEST, base58).getKey(BLSKeyFactory.get());
        assertArrayEquals(key.getPrivKeyBytes(), key2.getPrivKeyBytes());
        assertArrayEquals(key.getPubKey(), key2.getPubKey());
    }

    @Test
    public void clear() throws Exception {
        BLSKey unencryptedKey = new BLSKey();
        BLSKey encryptedKey = (new BLSKey()).encrypt(keyCrypter, keyCrypter.deriveKey(PASSWORD1));

        checkSomeBytesAreNonZero(unencryptedKey.getPrivKeyBytes());

        // The encryptedPrivateKey should be null in an unencrypted BLSKey anyhow but check all the same.
        assertTrue(unencryptedKey.getEncryptedPrivateKey() == null);

        checkSomeBytesAreNonZero(encryptedKey.getSecretBytes());
        checkSomeBytesAreNonZero(encryptedKey.getEncryptedPrivateKey().encryptedBytes);
        checkSomeBytesAreNonZero(encryptedKey.getEncryptedPrivateKey().initialisationVector);
    }

    @Test
    public void isPubKeyCompressed() {
        assertFalse(BLSKey.isPubKeyCompressed(HEX.decode(
                "0f805ee206ecfc037e2998cca95042f5de40f1bf1c8ea03e31f48461a9ab9b604afdee6ccf4b4526b4189e1acdf160ec")));
        assertFalse(BLSKey.isPubKeyCompressed(HEX.decode(
                "0238746c59d46d5408bf8b1d0af5740f036d27f617ce7b0cbdce0abebd1c7aafe1a6e1703fcb56b2953f0b965c740d25")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void isPubKeyCompressed_tooShort() {
        BLSKey.isPubKeyCompressed(HEX.decode("036d"));
    }

    private static boolean checkSomeBytesAreNonZero(byte[] bytes) {
        if (bytes == null) return false;
        for (byte b : bytes) if (b != 0) return true;
        return false;
    }

    @Test
    public void testPublicKeysAreEqual() {
        BLSKey key = new BLSKey();
        BLSKey pubKey1 = BLSKey.fromPublicOnly(key);
        assertFalse(pubKey1.isCompressed());
        BLSKey pubKey2 = pubKey1.decompress();
        assertEquals(pubKey1, pubKey2);
        assertEquals(pubKey1.hashCode(), pubKey2.hashCode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void fromPrivate_exceedsSize() {
        final byte[] bytes = new byte[33];
        bytes[0] = 42;
        BLSKey.fromPrivate(bytes);
    }
}
