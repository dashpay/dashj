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

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class KeyCrypterECDHTest {

    ECKey aliceKey;
    ECKey bobKey;

    static String secret = "my little secret is a pony that never sleeps";

    @Before
    public void setup() {
        aliceKey = ECKey.fromPrivate(Utils.HEX.decode("7337920e97ebfe34ee82137a26ef7bd296a3132f3c6494235e1f17820770aa01"));//fromSeed(aliceSeed);
        bobKey = ECKey.fromPrivate(Utils.HEX.decode("0086b1dd5edf5e19e17e086dbf296d8d697438fb11ef0dd044e186e634d3c99ff7")); //.fromSeed(bobSeed);
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
    public void decryptData() {
        //Bob is receiving from Alice
        byte initialisationVector[] = Utils.HEX.decode("59da47647645668bab9f7d039b1c87d1");
        byte encryptedPrivateKey [] = Utils.HEX.decode("34645d7d9c5b9065fc63483abe8b89294050339b0a299b7a1988a27786a860ceb7c518052ecf1583051e8b8fed2c398d");

        EncryptedData encryptedData = new EncryptedData(initialisationVector, encryptedPrivateKey);

        KeyCrypterECDH bobKeyExchangeCrypter = new KeyCrypterECDH();
        KeyParameter bobKeyParameter = bobKeyExchangeCrypter.deriveKey(bobKey, aliceKey);
        byte [] decryptedData = bobKeyExchangeCrypter.decrypt(encryptedData, bobKeyParameter);
        assertNotNull(decryptedData);

        assertEquals("they should be the same string", new String(decryptedData), secret );
    }

}
