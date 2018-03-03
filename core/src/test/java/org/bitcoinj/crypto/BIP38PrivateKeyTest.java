/*
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

package org.bitcoinj.crypto;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.BIP38PrivateKey.BadPassphraseException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class BIP38PrivateKeyTest {
    private static final NetworkParameters MAINNET = MainNetParams.get();
    private static final NetworkParameters TESTNET = TestNet3Params.get();

    @Test
    public void bip38testvector_noCompression_noEcMultiply_test1() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PRKQ6UsNkWTbEfu1VxQ4qmAcSTPb846QztH6WyDSMmzM4RuWuAuaaDpor");
        ECKey key = encryptedKey.decrypt("TestingOneTwoThree");
        assertEquals("7s6gmcwjrHHEhjBGJFiumZT5TCRtK3e2oLSLhEbJSSnNYeYez5m", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    public void bip38testvector_noCompression_noEcMultiply_test2() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PRJv7hs5ecaLygBm7EJ6vsmFD57yxPh5tz5rb2npd9CUppm7ndD6ebRiN");
        ECKey key = encryptedKey.decrypt("Satoshi");
        assertEquals("7rWDobu2FAWd2dSAQS3cxaN8uAngmqQk8USDmao4EAyarkGqB4b", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    @Ignore
    public void bip38testvector_noCompression_noEcMultiply_test3() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PRW5o9FLp4gJDDVqJQKJFTpMvdsSGJxMYHtHaQBF3ooa8mwD69bapcDQn");
        StringBuilder passphrase = new StringBuilder();
        passphrase.appendCodePoint(0x03d2); // GREEK UPSILON WITH HOOK
        passphrase.appendCodePoint(0x0301); // COMBINING ACUTE ACCENT
        passphrase.appendCodePoint(0x0000); // NULL
        passphrase.appendCodePoint(0x010400); // DESERET CAPITAL LETTER LONG I
        passphrase.appendCodePoint(0x01f4a9); // PILE OF POO
        ECKey key = encryptedKey.decrypt(passphrase.toString());  //TODO:
        assertEquals("7rtHxiWdAi7RX6pUURyCKbu2bYBqrgGwWoWm9FaMJVFDGz8DkXb", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    public void bip38testvector_compression_noEcMultiply_test1() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PYTFkHLMuJmTBjQrmPv76Up4pKuDm2x3eGRsb2JTupL427r4r1SX3zwsX");
        ECKey key = encryptedKey.decrypt("TestingOneTwoThree");
        assertEquals("XFVyWh2cVWyk2Bp4WRYZiJGHsGCeYtfoMz6KMnAHF92kSSKPbgtZ", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    public void bip38testvector_compression_noEcMultiply_test2() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PYTnFN6GyHJc97Sf5nu3y2oxhQEiRbv55jmsgoqw5UKNfvP3hXLPoLaqA");
        ECKey key = encryptedKey.decrypt("Satoshi");
        assertEquals("XH9tVMnd8bmQ1NQf1SH55tn4QDeKJH6dhMGimd4dBhMsRFQBkqnS", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    public void bip38testvector_ecMultiply_noCompression_noLotAndSequence_test1() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PRS5wny4cvD5qz36pUr6ZJjMKEh8Vnh7h7BXd9UhawwkwmzkjMVRreCPg");
        ECKey key = encryptedKey.decrypt("TestingOneTwoThree");
        assertEquals("7r5fCJnL2t6jF1NECFZuiEEX1qHoxEGM6sTpt48VwwtLnGficCF", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    public void bip38testvector_ecMultiply_noCompression_noLotAndSequence_test2() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PRTK43SPMdDu6iEcYAfbdzXtNnCubA5g83X68VBeNgwmtreHUvaPsrKph");
        ECKey key = encryptedKey.decrypt("Satoshi");
        assertEquals("7qaLaXUZQbtNXBtxDrRBSGgqNUwuALQAzVHXcMSX8tsUgHzbWTm", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    public void bip38testvector_ecMultiply_noCompression_lotAndSequence_test1() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PRP4XKCdh1DiTEr3TqQBGabGNwGpLKYkxEa5R6nUZrCWATziagZL6uMPZ");
        ECKey key = encryptedKey.decrypt("MOLON LABE");
        assertEquals("7rmagYs4ngvDArnoEuJxrhoggXNadmUcVS1nCiBM6sD7kqsyDGK", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    public void bip38testvector_ecMultiply_noCompression_lotAndSequence_test2() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PRLZb5UatTARzRiQaCqRwrJQ2HHKpp8uhHt3b26yfzMkkUt5vQ1cNmtTC");
        ECKey key = encryptedKey.decrypt("ΜΟΛΩΝ ΛΑΒΕ");
        assertEquals("7rND6RJJF8RDR3GPNe691f7rQPVN1n1KRDiPPaC2uJsiBWC5riQ", key.getPrivateKeyEncoded(MAINNET)
                .toString());
    }

    @Test
    @Ignore //No testnet wallet to generate keys
    public void bitcoinpaperwallet_testnet() throws Exception {
        // values taken from bitcoinpaperwallet.com
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(TESTNET,
                "6PRPhQhmtw6dQu6jD8E1KS4VphwJxBS9Eh9C8FQELcrwN3vPvskv9NKvuL");
        ECKey key = encryptedKey.decrypt("password");
        assertEquals("93MLfjbY6ugAsLeQfFY6zodDa8izgm1XAwA9cpMbUTwLkDitopg", key.getPrivateKeyEncoded(TESTNET)
                .toString());
    }

    @Test
    @Ignore //No testnet wallet to generate keys
    public void bitaddress_testnet() throws Exception {
        // values taken from bitaddress.org
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(TESTNET,
                "6PfMmVHn153N3x83Yiy4Nf76dHUkXufe2Adr9Fw5bewrunGNeaw2QCpifb");
        ECKey key = encryptedKey.decrypt("password");
        assertEquals("91tCpdaGr4Khv7UAuUxa6aMqeN5GcPVJxzLtNsnZHTCndxkRcz2", key.getPrivateKeyEncoded(TESTNET)
                .toString());
    }

    @Test(expected = BadPassphraseException.class)
    public void badPassphrase() throws Exception {
        BIP38PrivateKey encryptedKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PRVWUbkzzsbcVac2qwfssoUJAN1Xhrg6bNk8J7Nzm5H7kxEbn2Nh2ZoGg");
        encryptedKey.decrypt("BAD");
    }

    @Test(expected = AddressFormatException.InvalidDataLength.class)
    public void fromBase58_invalidLength() {
        String base58 = Base58.encodeChecked(1, new byte[16]);
        BIP38PrivateKey.fromBase58(null, base58);
    }

    @Test
    public void testJavaSerialization() throws Exception {
        BIP38PrivateKey testKey = BIP38PrivateKey.fromBase58(TESTNET,
                "6PfMmVHn153N3x83Yiy4Nf76dHUkXufe2Adr9Fw5bewrunGNeaw2QCpifb");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(testKey);
        BIP38PrivateKey testKeyCopy = (BIP38PrivateKey) new ObjectInputStream(
                new ByteArrayInputStream(os.toByteArray())).readObject();
        assertEquals(testKey, testKeyCopy);

        BIP38PrivateKey mainKey = BIP38PrivateKey.fromBase58(MAINNET,
                "6PfMmVHn153N3x83Yiy4Nf76dHUkXufe2Adr9Fw5bewrunGNeaw2QCpifb");
        os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(mainKey);
        BIP38PrivateKey mainKeyCopy = (BIP38PrivateKey) new ObjectInputStream(
                new ByteArrayInputStream(os.toByteArray())).readObject();
        assertEquals(mainKey, mainKeyCopy);
    }

    @Test
    public void cloning() throws Exception {
        BIP38PrivateKey a = BIP38PrivateKey.fromBase58(TESTNET, "6PfMmVHn153N3x83Yiy4Nf76dHUkXufe2Adr9Fw5bewrunGNeaw2QCpifb");
        // TODO: Consider overriding clone() in BIP38PrivateKey to narrow the type
        BIP38PrivateKey b = (BIP38PrivateKey) a.clone();

        assertEquals(a, b);
        assertNotSame(a, b);
    }

    @Test
    public void roundtripBase58() throws Exception {
        String base58 = "6PfMmVHn153N3x83Yiy4Nf76dHUkXufe2Adr9Fw5bewrunGNeaw2QCpifb";
        assertEquals(base58, BIP38PrivateKey.fromBase58(MAINNET, base58).toBase58());
    }
}
