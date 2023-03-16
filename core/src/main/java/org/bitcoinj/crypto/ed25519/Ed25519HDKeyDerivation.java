/*
 * Copyright 2013 Matija Mazi.
 * Copyright 2023 Dash Core Group.
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
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.ExtendedChildNumber;
import org.bitcoinj.crypto.HDDerivationException;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.crypto.LinuxSecureRandom;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.nio.ByteBuffer;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Implementation of the <a href="https://github.com/satoshilabs/slips/blob/master/slip-0010.md">SLIP-0010</a>
 * deterministic wallet child key generation algorithm for Ed25519.
 */
public final class Ed25519HDKeyDerivation {
    static {
        // Init proper random number generator, as some old Android installations have bugs that make it unsecure.
        if (Utils.isAndroidRuntime())
            new LinuxSecureRandom();
    }

    private Ed25519HDKeyDerivation() { }

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
    public static Ed25519DeterministicKey createMasterPrivateKey(byte[] seed) throws HDDerivationException {
        checkArgument(seed.length > 8, "Seed is too short and could be brute forced");
        // Calculate I = HMAC-SHA512(key="Bitcoin seed", msg=S)
        byte[] i = HDUtils.hmacSha512(HDUtils.createHmacSha512Digest("ed25519 seed".getBytes()), seed);
        // Split I into two 32-byte sequences, Il and Ir.
        // Use Il as master secret key, and Ir as master chain code.
        checkState(i.length == 64, i.length);
        byte[] il = Arrays.copyOfRange(i, 0, 32);
        byte[] ir = Arrays.copyOfRange(i, 32, 64);
        Arrays.fill(i, (byte)0);
        Ed25519DeterministicKey masterPrivKey = createMasterPrivKeyFromBytes(il, ir);
        Arrays.fill(il, (byte)0);
        Arrays.fill(ir, (byte)0);
        // Child deterministic keys will chain up to their parents to find the keys.
        masterPrivKey.setCreationTimeSeconds(Utils.currentTimeSeconds());
        return masterPrivKey;
    }

    /**
     * @throws HDDerivationException if privKeyBytes is invalid (not between 0 and n inclusive).
     */
    public static Ed25519DeterministicKey createMasterPrivKeyFromBytes(byte[] privKeyBytes, byte[] chainCode)
            throws HDDerivationException {
        // childNumberPath is an empty list because we are creating the root key.
        return createMasterPrivKeyFromBytes(privKeyBytes, chainCode, ImmutableList.<ChildNumber> of());
    }

    /**
     * @throws HDDerivationException if privKeyBytes is invalid (not between 0 and n inclusive).
     */
    public static Ed25519DeterministicKey createMasterPrivKeyFromBytes(byte[] privKeyBytes, byte[] chainCode,
            ImmutableList<ChildNumber> childNumberPath) throws HDDerivationException {
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(privKeyBytes, 0);
        //assertNonZero(priv, "Generated master key is invalid.");
        //assertLessThanN(priv, "Generated master key is invalid.");
        return new Ed25519DeterministicKey(childNumberPath, chainCode, priv, null);
    }

    public static Ed25519DeterministicKey createMasterPubKeyFromBytes(byte[] pubKeyBytes, byte[] chainCode) {
        return new Ed25519DeterministicKey(ImmutableList.<ChildNumber>of(), chainCode, new Ed25519PublicKeyParameters(pubKeyBytes, 0), null, null);
    }

    /**
     * Derives a key given the "extended" child number, ie. the 0x80000000 bit of the value that you
     * pass for {@code childNumber} will determine whether to use hardened derivation or not.
     * Consider whether your code would benefit from the clarity of the equivalent, but explicit, form
     * of this method that takes a {@code ChildNumber} rather than an {@code int}, for example:
     * {@code deriveChildKey(parent, new ChildNumber(childNumber, true))}
     * where the value of the hardened bit of {@code childNumber} is zero.
     */
    public static Ed25519DeterministicKey deriveChildKey(Ed25519DeterministicKey parent, int childNumber) {
        return deriveChildKey(parent, new ChildNumber(childNumber));
    }

    /**
     * Derives a key of the "extended" child number, ie. with the 0x80000000 bit specifying whether to use
     * hardened derivation or not. If derivation fails, tries a next child.
     */
    public static Ed25519DeterministicKey deriveThisOrNextChildKey(Ed25519DeterministicKey parent, int childNumber) {
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
    public static Ed25519DeterministicKey deriveChildKey(Ed25519DeterministicKey parent, ChildNumber childNumber) throws HDDerivationException {
        if (!parent.hasPrivKey()) {
            RawKeyBytes rawKey = deriveChildKeyBytesFromPublic(parent, childNumber, PublicDeriveMode.NORMAL);
            return new Ed25519DeterministicKey(
                    HDUtils.append(parent.getPath(), childNumber),
                    rawKey.chainCode,
                    new Ed25519PublicKeyParameters(rawKey.keyBytes, 0),
                    null,
                    parent);
        } else {
            RawKeyBytes rawKey = deriveChildKeyBytesFromPrivate(parent, childNumber);
            return new Ed25519DeterministicKey(
                    HDUtils.append(parent.getPath(), childNumber),
                    rawKey.chainCode,
                    new Ed25519PrivateKeyParameters(rawKey.keyBytes, 0),
                    parent);
        }
    }

    public static RawKeyBytes deriveChildKeyBytesFromPrivate(Ed25519DeterministicKey parent,
                                                              ChildNumber childNumber) throws HDDerivationException {
        checkArgument(parent.hasPrivKey(), "Parent key must have private key bytes for this method.");
        checkArgument(childNumber.isHardened(), "Unhardened derivation is unsupported (%s).", childNumber);

        boolean simple = !(childNumber instanceof ExtendedChildNumber);
        ByteBuffer data = ByteBuffer.allocate(simple ? 37 : 33 + Sha256Hash.LENGTH);
        data.put(parent.getPrivKeyBytes33());

        if (!simple) {
            data.put(Sha256Hash.wrap(((ExtendedChildNumber)childNumber).bi()).getBytes());
        } else {
            data.putInt(childNumber.i());
        }
        byte[] i = HDUtils.hmacSha512(parent.getChainCode(), data.array());
        checkState(i.length == 64, i.length);
        byte[] il = Arrays.copyOfRange(i, 0, 32);
        byte[] chainCode = Arrays.copyOfRange(i, 32, 64);

        return new RawKeyBytes(il, chainCode);
    }

    public enum PublicDeriveMode {
        NORMAL,
        WITH_INVERSION
    }

    public static RawKeyBytes deriveChildKeyBytesFromPublic(Ed25519DeterministicKey parent, ChildNumber childNumber, PublicDeriveMode mode) throws HDDerivationException {
        throw new UnsupportedOperationException("Normal derivation for Ed25519 is not supported");
    }

    public static class RawKeyBytes {
        public final byte[] keyBytes, chainCode;

        public RawKeyBytes(byte[] keyBytes, byte[] chainCode) {
            this.keyBytes = keyBytes;
            this.chainCode = chainCode;
        }
    }
}
