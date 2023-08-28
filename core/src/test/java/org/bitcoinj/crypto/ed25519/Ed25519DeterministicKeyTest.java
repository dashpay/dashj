/*
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

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;

import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Ed25519DeterministicKeyTest {

    private void assertStringBytesEquals(String expected, byte[] actual) {
        assertEquals(expected, Utils.HEX.encode(actual));
    }
    @Test
    public void testVector1() {
        //    Seed (hex): 000102030405060708090a0b0c0d0e0f
        //
        //    Chain m
        //    fingerprint: 00000000
        //    chain code: 90046a93de5380a72b5e45010748567d5ea02bbf6522f979e05c0d8d8ca9fffb
        //    private: 2b4be7f19ee27bbf30c667b642d5f4aa69fd169872f8fc3059c08ebae2eb19e7
        //    public: 00a4b2856bfec510abab89753fac1ac0e1112364e7d250545963f135f2a33188ed

        byte[] seed = Utils.HEX.decode("000102030405060708090a0b0c0d0e0f");
        Ed25519DeterministicKey masterKey = Ed25519HDKeyDerivation.createMasterPrivateKey(seed);
        assertStringBytesEquals("90046a93de5380a72b5e45010748567d5ea02bbf6522f979e05c0d8d8ca9fffb", masterKey.getChainCode());
        assertStringBytesEquals("2b4be7f19ee27bbf30c667b642d5f4aa69fd169872f8fc3059c08ebae2eb19e7", masterKey.getSecretBytes());
        assertStringBytesEquals("00a4b2856bfec510abab89753fac1ac0e1112364e7d250545963f135f2a33188ed", masterKey.getPubKey());
        assertEquals(ImmutableList.of(), masterKey.getPath());
        assertEquals(0, masterKey.getParentFingerprint());


        //    Chain m/0H
        //    fingerprint: ddebc675
        //    chain code: 8b59aa11380b624e81507a27fedda59fea6d0b779a778918a2fd3590e16e9c69
        //    private: 68e0fe46dfb67e368c75379acec591dad19df3cde26e63b93a8e704f1dade7a3
        //    public: 008c8a13df77a28f3445213a0f432fde644acaa215fc72dcdf300d5efaa85d350c

        Ed25519DeterministicKey zeroHardened = masterKey.deriveChildKey(ChildNumber.ZERO_HARDENED);
        assertStringBytesEquals("8b59aa11380b624e81507a27fedda59fea6d0b779a778918a2fd3590e16e9c69", zeroHardened.getChainCode());
        assertStringBytesEquals("68e0fe46dfb67e368c75379acec591dad19df3cde26e63b93a8e704f1dade7a3", zeroHardened.getSecretBytes());
        assertStringBytesEquals("008c8a13df77a28f3445213a0f432fde644acaa215fc72dcdf300d5efaa85d350c", zeroHardened.getPubKey());
        assertEquals(ImmutableList.of(ChildNumber.ZERO_HARDENED), zeroHardened.getPath());
        assertEquals(0xddebc675, zeroHardened.getParentFingerprint() );

        //    Chain m/0H/1H
        //    fingerprint: 13dab143
        //    chain code: a320425f77d1b5c2505a6b1b27382b37368ee640e3557c315416801243552f14
        //    private: b1d0bad404bf35da785a64ca1ac54b2617211d2777696fbffaf208f746ae84f2
        //    public: 001932a5270f335bed617d5b935c80aedb1a35bd9fc1e31acafd5372c30f5c1187

        Ed25519DeterministicKey zeroOneHardened = zeroHardened.deriveChildKey(ChildNumber.ONE_HARDENED);
        assertStringBytesEquals("a320425f77d1b5c2505a6b1b27382b37368ee640e3557c315416801243552f14", zeroOneHardened.getChainCode());
        assertStringBytesEquals("b1d0bad404bf35da785a64ca1ac54b2617211d2777696fbffaf208f746ae84f2", zeroOneHardened.getSecretBytes());
        assertStringBytesEquals("001932a5270f335bed617d5b935c80aedb1a35bd9fc1e31acafd5372c30f5c1187", zeroOneHardened.getPubKey());
        assertEquals(ImmutableList.of(ChildNumber.ZERO_HARDENED, ChildNumber.ONE_HARDENED), zeroOneHardened.getPath());
        assertEquals(0x13dab143, zeroOneHardened.getParentFingerprint());

        ChildNumber TWO_HARDENED = new ChildNumber(2, true);
        //    Chain m/0H/1H/2H
        //    fingerprint: ebe4cb29
        //    chain code: 2e69929e00b5ab250f49c3fb1c12f252de4fed2c1db88387094a0f8c4c9ccd6c
        //    private: 92a5b23c0b8a99e37d07df3fb9966917f5d06e02ddbd909c7e184371463e9fc9
        //    public: 00ae98736566d30ed0e9d2f4486a64bc95740d89c7db33f52121f8ea8f76ff0fc1
        Ed25519DeterministicKey levelThree = zeroOneHardened.deriveChildKey(TWO_HARDENED);
        assertStringBytesEquals("2e69929e00b5ab250f49c3fb1c12f252de4fed2c1db88387094a0f8c4c9ccd6c", levelThree.getChainCode());
        assertStringBytesEquals("92a5b23c0b8a99e37d07df3fb9966917f5d06e02ddbd909c7e184371463e9fc9", levelThree.getSecretBytes());
        assertStringBytesEquals("00ae98736566d30ed0e9d2f4486a64bc95740d89c7db33f52121f8ea8f76ff0fc1", levelThree.getPubKey());
        assertEquals(ImmutableList.of(ChildNumber.ZERO_HARDENED, ChildNumber.ONE_HARDENED, TWO_HARDENED), levelThree.getPath());
        assertEquals(0xebe4cb29, levelThree.getParentFingerprint());

        //    Chain m/0H/1H/2H/2H
        //    fingerprint: 316ec1c6
        //    chain code: 8f6d87f93d750e0efccda017d662a1b31a266e4a6f5993b15f5c1f07f74dd5cc
        //    private: 30d1dc7e5fc04c31219ab25a27ae00b50f6fd66622f6e9c913253d6511d1e662
        //    public: 008abae2d66361c879b900d204ad2cc4984fa2aa344dd7ddc46007329ac76c429c
        Ed25519DeterministicKey levelFour = levelThree.deriveChildKey(TWO_HARDENED);
        assertStringBytesEquals("8f6d87f93d750e0efccda017d662a1b31a266e4a6f5993b15f5c1f07f74dd5cc", levelFour.getChainCode());
        assertStringBytesEquals("30d1dc7e5fc04c31219ab25a27ae00b50f6fd66622f6e9c913253d6511d1e662", levelFour.getSecretBytes());
        assertStringBytesEquals("008abae2d66361c879b900d204ad2cc4984fa2aa344dd7ddc46007329ac76c429c", levelFour.getPubKey());
        assertEquals(ImmutableList.of(ChildNumber.ZERO_HARDENED, ChildNumber.ONE_HARDENED, TWO_HARDENED, TWO_HARDENED), levelFour.getPath());
        assertEquals(0x316ec1c6, levelFour.getParentFingerprint());

        //    Chain m/0H/1H/2H/2H/1000000000H
        //    fingerprint: d6322ccd
        //    chain code: 68789923a0cac2cd5a29172a475fe9e0fb14cd6adb5ad98a3fa70333e7afa230
        //    private: 8f94d394a8e8fd6b1bc2f3f49f5c47e385281d5c17e65324b0f62483e37e8793
        //    public: 003c24da049451555d51a7014a37337aa4e12d41e485abccfa46b47dfb2af54b7a
        ChildNumber BILLION_HARD = new ChildNumber(1000000000, true);
        Ed25519DeterministicKey levelFive = levelFour.deriveChildKey(BILLION_HARD);
        assertStringBytesEquals("68789923a0cac2cd5a29172a475fe9e0fb14cd6adb5ad98a3fa70333e7afa230", levelFive.getChainCode());
        assertStringBytesEquals("8f94d394a8e8fd6b1bc2f3f49f5c47e385281d5c17e65324b0f62483e37e8793", levelFive.getSecretBytes());
        assertStringBytesEquals("003c24da049451555d51a7014a37337aa4e12d41e485abccfa46b47dfb2af54b7a", levelFive.getPubKey());
        assertEquals(ImmutableList.of(ChildNumber.ZERO_HARDENED, ChildNumber.ONE_HARDENED, TWO_HARDENED, TWO_HARDENED, BILLION_HARD), levelFive.getPath());
        assertEquals(0xd6322ccd, levelFive.getParentFingerprint());
    }

    @Test
    public void testVector2() throws UnreadableWalletException {
        String seedPhrase = "enemy check owner stumble unaware debris suffer peanut good fabric bleak outside";
        byte[] seed = new DeterministicSeed(seedPhrase, null, "", 0).getSeedBytes();
        Ed25519DeterministicKey masterKey = Ed25519HDKeyDerivation.createMasterPrivateKey(seed);

        // Derive
        IDeterministicKey first = masterKey.deriveChildKey(ChildNumber.NINE_HARDENED);
        IDeterministicKey second = first.deriveChildKey(ChildNumber.FIVE_HARDENED);
        IDeterministicKey third = second.deriveChildKey(new ChildNumber(3, true));
        IDeterministicKey fourth = third.deriveChildKey(new ChildNumber(4, true));
        IDeterministicKey fifth = fourth.deriveChildKey(new ChildNumber(0, true));


        // from rust dash-core-shared
        // m/9'/5'/3'/4'/0' fingerprint 2497558984
        // m/9'/5'/3'/4'/0' chaincode 587dc2c7de6d36e06c6de0a2989cd8cb112c1e41b543002a5ff422f3eb1e8cd6
        // m/9'/5'/3'/4'/0' secret_key 7898dbaa7ab9b550e3befcd53dc276777ffc8a27124f830c04e17fcf74b9e071
        // m/9'/5'/3'/4'/0' public_key_data 08e2698fdcaa0af8416966ba9349b0c8dfaa80ed7f4094e032958a343e45f4b6
        // m/9'/5'/3'/4'/0' key_id c9bbba6a3ad5e87fb11af4f10458a52d3160259c
        // m/9'/5'/3'/4'/0' base64_keys eJjbqnq5tVDjvvzVPcJ2d3/8iicST4MMBOF/z3S54HEI4mmP3KoK+EFpZrqTSbDI36qA7X9AlOAylYo0PkX0tg==

        assertArrayEquals(Utils.HEX.decode("587dc2c7de6d36e06c6de0a2989cd8cb112c1e41b543002a5ff422f3eb1e8cd6"), fifth.getChainCode());
        assertArrayEquals(Utils.HEX.decode("7898dbaa7ab9b550e3befcd53dc276777ffc8a27124f830c04e17fcf74b9e071"), fifth.getPrivKeyBytes());
        assertArrayEquals(Utils.HEX.decode("0008e2698fdcaa0af8416966ba9349b0c8dfaa80ed7f4094e032958a343e45f4b6"), fifth.getPubKey());
        assertArrayEquals(Utils.HEX.decode("c9bbba6a3ad5e87fb11af4f10458a52d3160259c"), fifth.getPubKeyHash());
        String privatePublicBase64 = ((Ed25519DeterministicKey)fifth).getPrivatePublicBase64();
        assertEquals("eJjbqnq5tVDjvvzVPcJ2d3/8iicST4MMBOF/z3S54HEI4mmP3KoK+EFpZrqTSbDI36qA7X9AlOAylYo0PkX0tg==", privatePublicBase64);
    }
}
