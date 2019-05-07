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

package org.bitcoinj.core;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.Networks;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.script.Script.ScriptType;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.*;

public class AddressTest {
    private static final NetworkParameters TESTNET = TestNet3Params.get();
    private static final NetworkParameters MAINNET = MainNetParams.get();

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(LegacyAddress.class)
                .withPrefabValues(NetworkParameters.class, MAINNET, TESTNET)
                .suppress(Warning.NULL_FIELDS)
                .suppress(Warning.TRANSIENT_FIELDS)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testJavaSerialization() throws Exception {
        Address testAddress = Address.fromBase58(TESTNET, "ydzm4Uvr2GkLxTwQnwiyijYUukk1eweBo4");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(testAddress);
        Address testAddressCopy = (Address) new ObjectInputStream(new ByteArrayInputStream(os.toByteArray()))
                .readObject();
        assertEquals(testAddress, testAddressCopy);

        Address mainAddress = Address.fromBase58(MAINNET, "Xtqn4ks8sJS7iG7S7r1Jf37eFFSJGwh8a8");
        os = new ByteArrayOutputStream();
        new ObjectOutputStream(os).writeObject(mainAddress);
        Address mainAddressCopy = (Address) new ObjectInputStream(new ByteArrayInputStream(os.toByteArray()))
                .readObject();
        assertEquals(mainAddress, mainAddressCopy);
    }

    @Test
    public void stringification() throws Exception {
        // Test a testnet address.
        Address a = Address.fromPubKeyHash(TESTNET, HEX.decode("fda79a24e50ff70ff42f7d89585da5bd19d9e5cc"));
        assertEquals("yjSeawEuRUJDpr9FMmGx1oFtPrEjQG3vkg", a.toString());
        assertEquals(Script.ScriptType.P2PKH, a.getOutputScriptType());

        Address b = Address.fromPubKeyHash(MAINNET, HEX.decode("4a22c3c4cbb31e4d03b15550636762bda0baf85a"));
        assertEquals("XhSqUwiG6PGjRCXD5sksyvRNE1ZV8jkaVC", b.toString());
        assertEquals(Script.ScriptType.P2PKH, b.getOutputScriptType());
    }

    @Test
    public void decoding() throws Exception {
        Address a = Address.fromBase58(TESTNET, "yjSeawEuRUJDpr9FMmGx1oFtPrEjQG3vkg");
        assertEquals("fda79a24e50ff70ff42f7d89585da5bd19d9e5cc", Utils.HEX.encode(a.getHash()));

        Address b = Address.fromBase58(MAINNET, "XhSqUwiG6PGjRCXD5sksyvRNE1ZV8jkaVC");
        assertEquals("4a22c3c4cbb31e4d03b15550636762bda0baf85a", Utils.HEX.encode(b.getHash()));
    }

    @Test
    public void errorPaths() {
        // Check what happens if we try and decode garbage.
        try {
            Address.fromBase58(TESTNET, "this is not a valid address!");
            fail();
        } catch (AddressFormatException.WrongNetwork e) {
            fail();
        } catch (AddressFormatException e) {
            // Success.
        }

        // Check the empty case.
        try {
            Address.fromBase58(TESTNET, "");
            fail();
        } catch (AddressFormatException.WrongNetwork e) {
            fail();
        } catch (AddressFormatException e) {
            // Success.
        }

        // Check the case of a mismatched network.
        try {
            Address.fromBase58(TESTNET, "Xtqn4ks8sJS7iG7S7r1Jf37eFFSJGwh8a8");
            fail();
        } catch (AddressFormatException.WrongNetwork e) {
            // Success.
        } catch (AddressFormatException e) {
            fail();
        }
    }

    @Test
    public void getNetwork() throws Exception {
        NetworkParameters params = Address.getParametersFromAddress("XgxQxd6B8iYgEEryemnJrpvoWZ3149MCkK");
        assertEquals(MAINNET.getId(), params.getId());
        params = Address.getParametersFromAddress("ydzm4Uvr2GkLxTwQnwiyijYUukk1eweBo4");
        assertEquals(TESTNET.getId(), params.getId());
    }

    @Test
    public void getAltNetwork() throws Exception {
        // An alternative network
        class AltNetwork extends MainNetParams {
            AltNetwork() {
                super();
                id = "alt.network";
                addressHeader = 48;
                p2shHeader = 5;
            }
        }
        AltNetwork altNetwork = new AltNetwork();
        // Add new network params
        Networks.register(altNetwork);
        // Check if can parse address
        NetworkParameters params = Address.getParametersFromAddress("LLxSnHLN2CYyzB5eWTR9K9rS9uWtbTQFb6");
        assertEquals(altNetwork.getId(), params.getId());
        // Check if main network works as before
        params = Address.getParametersFromAddress("Xtqn4ks8sJS7iG7S7r1Jf37eFFSJGwh8a8");
        assertEquals(MAINNET.getId(), params.getId());
        // Unregister network
        Networks.unregister(altNetwork);
        try {
            Address.getParametersFromAddress("LLxSnHLN2CYyzB5eWTR9K9rS9uWtbTQFb6");
            fail();
        } catch (AddressFormatException e) { }
    }

    @Test
    public void p2shAddress() throws Exception {
        // Test that we can construct P2SH addresses
        Address mainNetP2SHAddress = Address.fromBase58(MAINNET, "7WJnm5FSpJttSr72bWWqFFZrXwB8ZzsK7b"); //2ac4b0b501117cc8119c5797b519538d4942e90e
        assertEquals(mainNetP2SHAddress.getVersion(), MAINNET.p2shHeader);
        assertEquals(Script.ScriptType.P2SH, mainNetP2SHAddress.getOutputScriptType());

        Address testNetP2SHAddress = Address.fromBase58(TESTNET, "8gfggfujFTJDtRMtrkWBKHX4Uz6uufXNC2"); //18a0e827269b5211eb51a4af1b2fa69333efa722
        assertEquals(testNetP2SHAddress.getVersion(), TESTNET.p2shHeader);
        assertEquals(Script.ScriptType.P2SH, testNetP2SHAddress.getOutputScriptType());

        // Test that we can determine what network a P2SH address belongs to
        NetworkParameters mainNetParams = Address.getParametersFromAddress("7WJnm5FSpJttSr72bWWqFFZrXwB8ZzsK7b");
        assertEquals(MAINNET.getId(), mainNetParams.getId());
        NetworkParameters testNetParams = Address.getParametersFromAddress("8gfggfujFTJDtRMtrkWBKHX4Uz6uufXNC2");
        assertEquals(TESTNET.getId(), testNetParams.getId());

        // Test that we can convert them from hashes
        byte[] hex = HEX.decode("2ac4b0b501117cc8119c5797b519538d4942e90e");
        Address a = Address.fromScriptHash(MAINNET, hex);
        assertEquals("7WJnm5FSpJttSr72bWWqFFZrXwB8ZzsK7b", a.toString());
        Address b = Address.fromScriptHash(TESTNET, HEX.decode("18a0e827269b5211eb51a4af1b2fa69333efa722"));
        assertEquals("8gfggfujFTJDtRMtrkWBKHX4Uz6uufXNC2", b.toString());
        Address c = Address.fromScriptHash(MAINNET,
                ScriptPattern.extractHashFromP2SH(ScriptBuilder.createP2SHOutputScript(hex)));
        assertEquals("7WJnm5FSpJttSr72bWWqFFZrXwB8ZzsK7b", c.toString());
    }

    @Test
    public void p2shAddressCreationFromKeys() throws Exception {
        // import some keys from this example: https://gist.github.com/gavinandresen/3966071
        ECKey key1 = DumpedPrivateKey.fromBase58(MAINNET, "7rZrMXc6R9TeuMa5UCX8i9Ffn1r3d8ypp6dJkmg1JEPZNhQodL8").getKey();
        key1 = ECKey.fromPrivate(key1.getPrivKeyBytes());
        ECKey key2 = DumpedPrivateKey.fromBase58(MAINNET, "7rPEeydXbxsD1VHSuU7V8Jhn2RPPBgQUxZ1vnVK6QMKxigcC9HX").getKey();
        key2 = ECKey.fromPrivate(key2.getPrivKeyBytes());
        ECKey key3 = DumpedPrivateKey.fromBase58(MAINNET, "7sM7Tn3tMHUyE6iYVrxAzRf94XnPceRHrdKQNQwFPVfwp3uwr1k").getKey();
        key3 = ECKey.fromPrivate(key3.getPrivKeyBytes());

        List<ECKey> keys = Arrays.asList(key1, key2, key3);
        Script p2shScript = ScriptBuilder.createP2SHOutputScript(2, keys);
        Address address = Address.fromScriptHash(MAINNET,
                ScriptPattern.extractHashFromP2SH(p2shScript));
        assertEquals("7pUXwZyXEMWv87j2vnY6SiWDB1bi2rHEb4", address.toString());
    }

    @Test
    public void cloning() throws Exception {
        Address a = Address.fromPubKeyHash(TESTNET, HEX.decode("fda79a24e50ff70ff42f7d89585da5bd19d9e5cc"));
        Address b = a.clone();

        assertEquals(a, b);
        assertNotSame(a, b);
    }

    @Test
    public void roundtripBase58() throws Exception {
        String base58 = "Xtqn4ks8sJS7iG7S7r1Jf37eFFSJGwh8a8";
        assertEquals(base58, Address.fromBase58(null, base58).toBase58());
    }

    @Test
    public void comparisonCloneEqualTo() throws Exception {
        Address a = Address.fromBase58(MAINNET, "XoVhYqRxPWkCw8WjZfiHPYsoR7g4Ny3x7K");
        Address b = a.clone();

        int result = a.compareTo(b);
        assertEquals(0, result);
    }

    @Test
    public void comparisonLessThan() throws Exception {
        Address a = Address.fromBase58(MAINNET, "XoVhYqRxPWkCw8WjZfiHPYsoR7g4Ny3x7K");
        Address b = Address.fromBase58(MAINNET, "XpDe4AXdEf9NtW5ZBeGmuzy2VShg3qZMFQ");

        int result = a.compareTo(b);
        assertTrue(result < 0);
    }

    @Test
    public void comparisonGreaterThan() throws Exception {
        Address a = Address.fromBase58(MAINNET, "XpDe4AXdEf9NtW5ZBeGmuzy2VShg3qZMFQ");
        Address b = Address.fromBase58(MAINNET, "XoVhYqRxPWkCw8WjZfiHPYsoR7g4Ny3x7K");

        int result = a.compareTo(b);
        assertTrue(result > 0);
    }

    @Test
    public void comparisonBytesVsString() throws Exception {
        // TODO: To properly test this we need a much larger data set
        Address a = Address.fromBase58(MAINNET, "XoVhYqRxPWkCw8WjZfiHPYsoR7g4Ny3x7K");
        Address b = Address.fromBase58(MAINNET, "XpDe4AXdEf9NtW5ZBeGmuzy2VShg3qZMFQ");

        int resultBytes = a.compareTo(b);
        int resultsString = a.toString().compareTo(b.toString());
        assertTrue( resultBytes < 0 );
        assertTrue( resultsString < 0 );
    }
}
