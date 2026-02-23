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

package org.bitcoinj.crypto.ed25519;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.ed25519.Ed25519HDKeyDerivation.PublicDeriveMode;
import org.junit.Test;

/**
 * @author Andreas Schildbach
 */
public class Ed25519HDKeyDerivationTest {
    // SLIP-0010 test vector 1: seed = 000102030405060708090a0b0c0d0e0f, child m/0H
    private static final byte[] SEED = Utils.HEX.decode("000102030405060708090a0b0c0d0e0f");
    private static final ChildNumber CHILD_NUMBER = ChildNumber.ZERO_HARDENED;
    private static final String EXPECTED_CHILD_CHAIN_CODE = "8b59aa11380b624e81507a27fedda59fea6d0b779a778918a2fd3590e16e9c69";
    private static final String EXPECTED_CHILD_PRIVATE_KEY = "68e0fe46dfb67e368c75379acec591dad19df3cde26e63b93a8e704f1dade7a3";
    private static final String EXPECTED_CHILD_PUBLIC_KEY = "008c8a13df77a28f3445213a0f432fde644acaa215fc72dcdf300d5efaa85d350c";

    @Test
    public void testDeriveFromPrivateParent() {
        Ed25519DeterministicKey parent = Ed25519HDKeyDerivation.createMasterPrivateKey(SEED);
        assertFalse(parent.isPubKeyOnly());

        Ed25519DeterministicKey fromPrivate = Ed25519HDKeyDerivation.deriveChildKeyFromPrivate(parent, CHILD_NUMBER);
        assertEquals(EXPECTED_CHILD_CHAIN_CODE, Utils.HEX.encode(fromPrivate.getChainCode()));
        assertEquals(EXPECTED_CHILD_PRIVATE_KEY, Utils.HEX.encode(fromPrivate.getSecretBytes()));
        assertEquals(EXPECTED_CHILD_PUBLIC_KEY, Utils.HEX.encode(fromPrivate.getPubKey()));
        assertFalse(fromPrivate.isPubKeyOnly());

        try {
            Ed25519HDKeyDerivation.deriveChildKeyFromPublic(parent, CHILD_NUMBER, PublicDeriveMode.NORMAL);
            fail();
        } catch (UnsupportedOperationException x) {
            // expected: normal (non-hardened) derivation is not supported for Ed25519
        }
    }

    @Test
    public void testDeriveFromPublicParent() {
        Ed25519DeterministicKey parent = Ed25519HDKeyDerivation.createMasterPrivateKey(SEED).dropPrivateBytes();
        assertTrue(parent.isPubKeyOnly());

        try {
            Ed25519HDKeyDerivation.deriveChildKeyFromPrivate(parent, CHILD_NUMBER);
            fail();
        } catch (IllegalArgumentException x) {
            // expected: no private key available on parent
        }

        try {
            Ed25519HDKeyDerivation.deriveChildKeyFromPublic(parent, CHILD_NUMBER, PublicDeriveMode.NORMAL);
            fail();
        } catch (UnsupportedOperationException x) {
            // expected: normal (non-hardened) derivation is not supported for Ed25519
        }
    }
}