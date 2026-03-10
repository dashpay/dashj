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
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
}
