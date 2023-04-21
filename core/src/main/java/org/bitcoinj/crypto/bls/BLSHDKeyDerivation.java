/*
 * Copyright 2013 Matija Mazi.
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

package org.bitcoinj.crypto.bls;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.HDDerivationException;
import org.dashj.bls.ExtendedPrivateKey;
import org.dashj.bls.ExtendedPublicKey;


import static com.google.common.base.Preconditions.checkArgument;

/**
 * Implementation of the BLS deterministic wallet child key generation algorithm.
 */
public final class BLSHDKeyDerivation {
    private BLSHDKeyDerivation() { }

    /**
     * Child derivation may fail (although with extremely low probability); in such case it is re-attempted.
     * This is the maximum number of re-attempts (to avoid an infinite loop in case of bugs etc.).
     */
    public static final int MAX_CHILD_DERIVATION_ATTEMPTS = 100;

    /**
     * Generates a new deterministic key from the given seed, which can be any arbitrary byte array. However resist
     * the temptation to use a string as the seed - any key derived from a password is likely to be weak and easily
     * broken by attackers (this is not theoretical, people have had money stolen that way). This method checks
     * that the given seed is at least 64 bits long.
     *
     * @throws HDDerivationException if generated master key is invalid (private key not between 0 and n inclusive)
     * @throws IllegalArgumentException if the seed is less than 8 bytes and could be brute forced
     */
    public static BLSDeterministicKey createMasterPrivateKey(byte[] seed) throws HDDerivationException {
        checkArgument(seed.length > 8, "Seed is too short and could be brute forced");
        BLSDeterministicKey masterPrivKey = new BLSDeterministicKey(seed);
        masterPrivKey.setCreationTimeSeconds(Utils.currentTimeSeconds());
        return masterPrivKey;
    }

    /**
     * @throws HDDerivationException if privKeyBytes is invalid (not between 0 and n inclusive).
     */
    public static BLSDeterministicKey createMasterPrivKeyFromBytes(byte[] privKeyBytes, byte[] chainCode)
            throws HDDerivationException {
        // childNumberPath is an empty list because we are creating the root key.
        return createMasterPrivKeyFromBytes(privKeyBytes, chainCode, ImmutableList.<ChildNumber> of());
    }

    /**
     * @throws HDDerivationException if privKeyBytes is invalid (not between 0 and n inclusive).
     */
    public static BLSDeterministicKey createMasterPrivKeyFromBytes(byte[] privKeyBytes, byte[] chainCode,
            ImmutableList<ChildNumber> childNumberPath) throws HDDerivationException {
        return new BLSDeterministicKey(childNumberPath, chainCode, privKeyBytes, null);
    }

    public static BLSDeterministicKey createMasterPubKeyFromBytes(byte[] pubKeyBytes, byte[] chainCode) {
        return new BLSDeterministicKey(ImmutableList.of(), chainCode, pubKeyBytes, null, false, null);
    }

    public static BLSDeterministicKey createMasterPubKeyFromBytes(byte[] seed) {
        return new BLSDeterministicKey(seed);
    }

    /**
     * Derives a key given the "extended" child number, ie. the 0x80000000 bit of the value that you
     * pass for {@code childNumber} will determine whether to use hardened derivation or not.
     * Consider whether your code would benefit from the clarity of the equivalent, but explicit, form
     * of this method that takes a {@code ChildNumber} rather than an {@code int}, for example:
     * {@code deriveChildKey(parent, new ChildNumber(childNumber, true))}
     * where the value of the hardened bit of {@code childNumber} is zero.
     */
    public static BLSDeterministicKey deriveChildKey(BLSDeterministicKey parent, int childNumber) {
        return deriveChildKey(parent, new ChildNumber(childNumber));
    }

    /**
     * Derives a key of the "extended" child number, ie. with the 0x80000000 bit specifying whether to use
     * hardened derivation or not. If derivation fails, tries a next child.
     */
    public static BLSDeterministicKey deriveThisOrNextChildKey(BLSDeterministicKey parent, int childNumber) {
        int nAttempts = 0;
        ChildNumber child = new ChildNumber(childNumber);
        boolean isHardened = child.isHardened();
        while (nAttempts < MAX_CHILD_DERIVATION_ATTEMPTS) {
            try {
                child = new ChildNumber(child.num() + nAttempts, isHardened);
                return deriveChildKey(parent, child);
            } catch (HDDerivationException ignore) { }
            nAttempts++;
        }
        throw new HDDerivationException("Maximum number of child derivation attempts reached, this is probably an indication of a bug.");

    }

    /**
     * @throws HDDerivationException if private derivation is attempted for a public-only parent key, or
     * if the resulting derived key is invalid (eg. private key == 0).
     */
    public static BLSDeterministicKey deriveChildKey(BLSDeterministicKey parent, ChildNumber childNumber) throws HDDerivationException {
        if (!parent.hasPrivKey()) {
            ExtendedPublicKey extendedPublicKey = parent.extendedPublicKey.publicChild(childNumber.getI());
            return new BLSDeterministicKey(extendedPublicKey, parent);
        } else {
            ExtendedPrivateKey extendedPrivateKey = parent.extendedPrivateKey.privateChild(childNumber.getI());
            return new BLSDeterministicKey(extendedPrivateKey, parent);
        }
    }
}
