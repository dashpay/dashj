/*
 * Copyright 2019 Dash Core Group
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

import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BLSExchangeKeyCrypterTest {

    BLSSecretKey aliceKey;
    BLSSecretKey bobKey;

    static byte [] aliceSeed = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10
    };

    static byte [] bobSeed = {
            10, 9, 8, 7, 6, 6, 7, 8, 9, 10
    };

    static String secret = "my little secret is a pony that never sleeps";

    @Before
    public void setup() {
        aliceKey = BLSSecretKey.fromSeed(aliceSeed);
        bobKey = BLSSecretKey.fromSeed(bobSeed);
    }

    @Test
    public void testEncryptionAndDecryption() {
        //Alice is sending to Bob
        BLSKeyExchangeCrypter aliceKeyExchangeCrypter = new BLSKeyExchangeCrypter();
        KeyParameter aliceKeyParameter = aliceKeyExchangeCrypter.deriveKey(aliceKey, bobKey.GetPublicKey());

        EncryptedData encryptedData = aliceKeyExchangeCrypter.encrypt(secret.getBytes(), aliceKeyParameter);
        assertNotNull(encryptedData);

        //Bob is receiving from Alice
        BLSKeyExchangeCrypter bobKeyExchangeCrypter = new BLSKeyExchangeCrypter();
        KeyParameter bobKeyParameter = bobKeyExchangeCrypter.deriveKey(bobKey, aliceKey.GetPublicKey());
        byte [] decryptedData = bobKeyExchangeCrypter.decrypt(encryptedData, bobKeyParameter);
        assertNotNull(decryptedData);

        assertEquals("they should be the same string", new String(decryptedData), secret );
    }

    @Test
    public void decryptData() {
        //Bob is receiving from Alice
        byte[] initialisationVector = {-22, -59, -68, -42, -21, -123, 7, 71, 89, -32, 38, 20, -105, 66, -116, -101};
        byte[] encryptedPrivateKey = {-41, 43, -44, 24, -50, -106, -26, -100, -69, 103, 102, -27, -97, -115, 31, -127, 56, -81, -80, 104, 96, 24, -69, 77, 64, 19, 105, -25, 123, -92, 115, 103, -7, 58, 73, -91, 40, -12, -52, -98, 63, 32, -102, 81, 94, 109, -40, -14};

        EncryptedData encryptedData = new EncryptedData(initialisationVector, encryptedPrivateKey);

        BLSKeyExchangeCrypter bobKeyExchangeCrypter = new BLSKeyExchangeCrypter();
        KeyParameter bobKeyParameter = bobKeyExchangeCrypter.deriveKey(bobKey, aliceKey.GetPublicKey());
        byte [] decryptedData = bobKeyExchangeCrypter.decrypt(encryptedData, bobKeyParameter);
        assertNotNull(decryptedData);

        assertEquals("they should be the same string", new String(decryptedData), secret );
    }

}
