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

import static org.bitcoinj.core.Utils.HEX;
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
        aliceKey.setLegacy(true);
        bobKey = BLSSecretKey.fromSeed(bobSeed);
        bobKey.setLegacy(true);
    }

    @Test
    public void verifyKeysTest() {
        BLSScheme.setLegacyDefault(true);
        assertEquals("1790635de8740e9a6a6b15fb6b72f3a16afa0973d971979b6ba54761d6e2502c50db76f4d26143f05459a42cfd520d44",
                aliceKey.getPublicKey().toStringHex(true));
        assertEquals("F5BjXeh0DppqaxX7a3LzoWr6CXPZcZeba6VHYdbiUCxQ23b00mFD8FRZpCz9Ug1E", aliceKey.getPublicKey().toStringBase64(true));
        assertEquals("46891c2cec49593c81921e473db7480029e0fc1eb933c6b93d81f5370eb19fbd", aliceKey.toStringHex(true));
        assertEquals("RokcLOxJWTyBkh5HPbdIACng/B65M8a5PYH1Nw6xn70=", aliceKey.toStringBase64(true));

        assertEquals("0e2f9055c17eb13221d8b41833468ab49f7d4e874ddf4b217f5126392a608fd48ccab3510548f1da4f397c1ad4f8e01a", bobKey.getPublicKey().toStringHex(true));
        assertEquals("Di+QVcF+sTIh2LQYM0aKtJ99TodN30shf1EmOSpgj9SMyrNRBUjx2k85fBrU+OAa", bobKey.getPublicKey().toStringBase64(true));
        assertEquals("2513a9d824e763f8b3ff4304c5d52d05154a82b4c975da965f124e5dcf915805", bobKey.toStringHex(true));
        assertEquals("JROp2CTnY/iz/0MExdUtBRVKgrTJddqWXxJOXc+RWAU=", bobKey.toStringBase64(true));
    }

    @Test
    public void testEncryptionAndDecryption() {
        //Alice is sending to Bob
        BLSKeyExchangeCrypter aliceKeyExchangeCrypter = new BLSKeyExchangeCrypter(true);
        KeyParameter aliceKeyParameter = aliceKeyExchangeCrypter.deriveKey(aliceKey, bobKey.getPublicKey());

        EncryptedData encryptedData = aliceKeyExchangeCrypter.encrypt(secret.getBytes(),
                HEX.decode("eac5bcd6eb85074759e0261497428c9b"), aliceKeyParameter);
        assertEquals("d72bd418ce96e69cbb6766e59f8d1f8138afb0686018bb4d401369e77ba47367f93a49a528f4cc9e3f209a515e6dd8f2",
                HEX.encode(encryptedData.encryptedBytes));

        //Bob is receiving from Alice
        BLSKeyExchangeCrypter bobKeyExchangeCrypter = new BLSKeyExchangeCrypter(true);
        KeyParameter bobKeyParameter = bobKeyExchangeCrypter.deriveKey(bobKey, aliceKey.getPublicKey());
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

        BLSKeyExchangeCrypter bobKeyExchangeCrypter = new BLSKeyExchangeCrypter(true);
        KeyParameter bobKeyParameter = bobKeyExchangeCrypter.deriveKey(bobKey, aliceKey.getPublicKey());
        byte [] decryptedData = bobKeyExchangeCrypter.decrypt(encryptedData, bobKeyParameter);
        assertNotNull(decryptedData);

        assertEquals("they should be the same string", new String(decryptedData), secret );
    }


}
