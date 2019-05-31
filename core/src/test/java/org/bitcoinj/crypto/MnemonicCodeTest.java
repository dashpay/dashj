/*
 * Copyright 2013 Ken Sedgwick
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.bitcoinj.core.Utils.HEX;
import static org.bitcoinj.core.Utils.WHITESPACE_SPLITTER;
import static org.junit.Assert.*;

/**
 * Test the various guard clauses of {@link MnemonicCode}.
 *
 * See {@link MnemonicCodeVectorsTest} test vectors.
 */
public class MnemonicCodeTest {

    private MnemonicCode mc;

    @Before
    public void setup() throws IOException {
        mc = new MnemonicCode();
    }

    @Test(expected = MnemonicException.MnemonicLengthException.class)
    public void testBadEntropyLength() throws Exception {
        byte[] entropy = HEX.decode("7f7f7f7f7f7f7f7f7f7f7f7f7f7f");
        mc.toMnemonic(entropy);
    }    

    @Test(expected = MnemonicException.MnemonicLengthException.class)
    public void testBadLength() throws Exception {
        List<String> words = WHITESPACE_SPLITTER.splitToList("risk tiger venture dinner age assume float denial penalty hello");
        mc.check(words);
    }

    @Test(expected = MnemonicException.MnemonicWordException.class)
    public void testBadWord() throws Exception {
        List<String> words = WHITESPACE_SPLITTER.splitToList("risk tiger venture dinner xyzzy assume float denial penalty hello game wing");
        mc.check(words);
    }

    @Test(expected = MnemonicException.MnemonicChecksumException.class)
    public void testBadChecksum() throws Exception {
        List<String> words = WHITESPACE_SPLITTER.splitToList("bless cloud wheel regular tiny venue bird web grief security dignity zoo");
        mc.check(words);
    }

    @Test(expected = MnemonicException.MnemonicLengthException.class)
    public void testEmptyMnemonic() throws Exception {
        List<String> words = new ArrayList<>();
        mc.check(words);
    }

    @Test(expected = MnemonicException.MnemonicLengthException.class)
    public void testEmptyEntropy() throws Exception {
        byte[] entropy = {};
        mc.toMnemonic(entropy);
    }

    @Test(expected = NullPointerException.class)
    public void testNullPassphrase() throws Exception {
        List<String> code = WHITESPACE_SPLITTER.splitToList("legal winner thank year wave sausage worth useful legal winner thank yellow");
        MnemonicCode.toSeed(code, null);
    }

    @Test
    public void testDiacriticsInsensitiveMnemonics() throws Exception {
        String bip39spanishWordlist = "mnemonic/wordlist/spanish.txt";
        String bip39spanishSha256 = "a556a26c6a5bb36db0fb7d8bf579cb7465fcaeec03957c0dda61b569962d9da5";
        InputStream stream = MnemonicCode.class.getResourceAsStream(bip39spanishWordlist);
        if (stream == null)
            throw new FileNotFoundException(bip39spanishWordlist);
        MnemonicCode mnemonicCode = new MnemonicCode(stream, bip39spanishSha256);
        assertEquals(0, mnemonicCode.search(mnemonicCode.getWordList(), "ábaco"));
        assertEquals(0, mnemonicCode.search(mnemonicCode.getWordList(), "abaco"));
        assertEquals(19, mnemonicCode.search(mnemonicCode.getWordList(), "ácido"));
        assertEquals(19, mnemonicCode.search(mnemonicCode.getWordList(), "acido"));
        assertEquals(935, mnemonicCode.search(mnemonicCode.getWordList(), "jabalí"));
        assertEquals(935, mnemonicCode.search(mnemonicCode.getWordList(), "jabali"));
        assertEquals(1691, mnemonicCode.search(mnemonicCode.getWordList(), "satán"));
        assertEquals(1691, mnemonicCode.search(mnemonicCode.getWordList(), "satan"));
        assertEquals(428, mnemonicCode.search(mnemonicCode.getWordList(), "cojín"));
        assertEquals(428, mnemonicCode.search(mnemonicCode.getWordList(), "cojin"));
        assertEquals(1323, mnemonicCode.search(mnemonicCode.getWordList(), "órgano"));
        assertEquals(1323, mnemonicCode.search(mnemonicCode.getWordList(), "organo"));
        assertEquals(1340, mnemonicCode.search(mnemonicCode.getWordList(), "óvulo"));
        assertEquals(1340, mnemonicCode.search(mnemonicCode.getWordList(), "ovulo"));

        List<String> spanishSeed1 = Arrays.asList("llama", "vaca", "huir", "satán", "trompa", "cojín", "rienda", "alteza", "lomo", "forma", "tela", "hongo");
        List<String> spanishSeed1NoDiacritics = Arrays.asList("llama", "vaca", "huir", "satan", "trompa", "cojin", "rienda", "alteza", "lomo", "forma", "tela", "hongo");
        byte[] spanishSeed1Entropy = { -127, -66, -127, -68, -23, -66, -10, 107, 50, 112, 81, -125, -85, -109, -113, 54};
        assertArrayEquals(spanishSeed1Entropy, mnemonicCode.toEntropy(spanishSeed1));
        assertArrayEquals(spanishSeed1Entropy, mnemonicCode.toEntropy(spanishSeed1NoDiacritics));

        List<String> spanishSeed2 = Arrays.asList("captar", "onza", "órgano", "término", "pilar", "fiar", "dental", "buey", "urna", "guiso", "imponer", "libertad");
        List<String> spanishSeed2NoDiacritics = Arrays.asList("captar", "onza", "organo", "termino", "pilar", "fiar", "dental", "buey", "urna", "guiso", "imponer", "libertad");
        byte[] spanishSeed2Entropy = { 42, -108, 110, -107, -14, -69, 56, -80, 16, 65, 32, -13, 77, 25, -57, -65};
        assertArrayEquals(spanishSeed2Entropy, mnemonicCode.toEntropy(spanishSeed2));
        assertArrayEquals(spanishSeed2Entropy, mnemonicCode.toEntropy(spanishSeed2NoDiacritics));
    }
 }
