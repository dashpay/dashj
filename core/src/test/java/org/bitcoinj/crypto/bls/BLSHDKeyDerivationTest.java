/*
 * Copyright by the original author or authors.
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.bls.BLSHDKeyDerivation.PublicDeriveMode;
import org.dashj.bls.BLSJniLibrary;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Andreas Schildbach
 */
public class BLSHDKeyDerivationTest {
    private static final byte[] SEED = {1, 50, 6, (byte) 244, 24, (byte) 199, 1, 25};
    // Hardened child 77 - known values from BLSDeterministicKeyTest.legacyBLSDerivationTest
    private static final ChildNumber HARDENED_CHILD = new ChildNumber(77, true);
    private static final String EXPECTED_HARDENED_CHAIN_CODE = "f2c8e4269bb3e54f8179a5c6976d92ca14c3260dd729981e9d15f53049fd698b";
    // Non-hardened child 3 - for consistency check between private and public derivation
    private static final ChildNumber SOFTENED_CHILD = new ChildNumber(3, false);

    @BeforeClass
    public static void setup() {
        BLSJniLibrary.init();
    }

    @Test
    public void testDeriveFromPrivateParent() {
        BLSScheme.setLegacyDefault(false);
        BLSDeterministicKey parent = new BLSDeterministicKey(SEED);
        assertFalse(parent.isPubKeyOnly());

        // Hardened derivation via private key - verify against known values
        BLSDeterministicKey fromPrivate = BLSHDKeyDerivation.deriveChildKeyFromPrivate(parent, HARDENED_CHILD);
        assertEquals(EXPECTED_HARDENED_CHAIN_CODE, Utils.HEX.encode(fromPrivate.getChainCode()));
        assertFalse(fromPrivate.isPubKeyOnly());

        // Non-hardened: private and public derivation must yield identical chain code and public key
        BLSDeterministicKey fromPrivateSoftened = BLSHDKeyDerivation.deriveChildKeyFromPrivate(parent, SOFTENED_CHILD);
        BLSDeterministicKey fromPublicSoftened = BLSHDKeyDerivation.deriveChildKeyFromPublic(parent, SOFTENED_CHILD,
                PublicDeriveMode.NORMAL);
        assertArrayEquals(fromPrivateSoftened.getChainCode(), fromPublicSoftened.getChainCode());
        assertArrayEquals(fromPrivateSoftened.getPubKey(), fromPublicSoftened.getPubKey());
        assertFalse(fromPrivateSoftened.isPubKeyOnly());
        // TODO: this doesn't work like ECKey
        // assertTrue(fromPublicSoftened.isPubKeyOnly());
    }

    @Test
    public void testDeriveFromPublicParent() {
        BLSScheme.setLegacyDefault(false);
        BLSDeterministicKey parent = new BLSDeterministicKey(SEED).dropPrivateBytes();
        assertTrue(parent.isPubKeyOnly());

        try {
            BLSHDKeyDerivation.deriveChildKeyFromPrivate(parent, SOFTENED_CHILD);
            fail();
        } catch (IllegalArgumentException x) {
            System.out.println(x.toString());
            // expected: no private key available on parent
        }

        BLSDeterministicKey fromPublic = BLSHDKeyDerivation.deriveChildKeyFromPublic(parent, SOFTENED_CHILD,
                PublicDeriveMode.NORMAL);
        assertTrue(fromPublic.isPubKeyOnly());
    }
}