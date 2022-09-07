/*
 * Copyright 2022 Dash Core Group
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

package org.bitcoinj.crypto;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KeyCrypterAESCBCTest {

    ECKey aliceKey;

    static String secret = "my little secret is a pony that never sleeps";

    @Before
    public void setup() {
        aliceKey = ECKey.fromPrivate(Utils.HEX.decode("7337920e97ebfe34ee82137a26ef7bd296a3132f3c6494235e1f17820770aa01"));
    }

    @Test
    public void testEncryptionAndDecryption() {
        //Alice is sending to Bob
        KeyCrypterECDH aliceKeyExchangeCrypter = new KeyCrypterECDH();
        KeyParameter aliceKeyParameter = aliceKeyExchangeCrypter.deriveKey(aliceKey);

        EncryptedData encryptedData = aliceKeyExchangeCrypter.encrypt(secret.getBytes(), aliceKeyParameter);

        byte [] decryptedData = aliceKeyExchangeCrypter.decrypt(encryptedData, aliceKeyParameter);

        assertEquals("they should be the same string", new String(decryptedData), secret );
    }
}
