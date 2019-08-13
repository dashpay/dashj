/*
 * Copyright 2013 Matija Mazi.
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

import com.google.common.primitives.Ints;
import org.bitcoinj.core.Sha256Hash;

import java.math.BigInteger;
import java.util.Locale;

/**
 * <p>This is just a wrapper for an integer of any size (child number) as per BIP 32 with a boolean getter for the most significant bit
 * and a getter for the actual 0-based child number. A {@link java.util.List} of these forms a <i>path</i> through a
 * {@link DeterministicHierarchy}. This class is immutable.
 */
public class ExtendedChildNumber extends ChildNumber {
    /**
     * The bit that's set in the child number to indicate whether this key is "hardened". Given a hardened key, it is
     * not possible to derive a child public key if you know only the hardened public key. With a non-hardened key this
     * is possible, so you can derive trees of public keys given only a public parent, but the downside is that it's
     * possible to leak private keys if you disclose a parent public key and a child private key (elliptic curve maths
     * allows you to work upwards).
     */
    public static final BigInteger MAX_INT = BigInteger.valueOf(2 << 32 - 1);
    private final short size;
    private final boolean isHardened;
    private final BigInteger bi;

    /*public static final ExtendedChildNumber ZERO = new ExtendedChildNumber(0);
    public static final ExtendedChildNumber ONE = new ExtendedChildNumber(1);
    public static final ExtendedChildNumber ZERO_HARDENED = new ExtendedChildNumber(0, true);
    public static final ExtendedChildNumber ONE_HARDENED = new ExtendedChildNumber(1, true);
    public static final ExtendedChildNumber FIVE_HARDENED = new ExtendedChildNumber(5, true);

    public ExtendedChildNumber(int childNumber, boolean isHardened) {
        super(childNumber, isHardened);
        size = 4;
        this.isHardened = isHardened;
        this.bi = BigInteger.valueOf(childNumber);
    }

    public ExtendedChildNumber(int i) {
        super(i);
        this.bi = BigInteger.valueOf(i & ~HARDENED_BIT);
        this.size = 4;
        this.isHardened = (i & HARDENED_BIT) != 0;
    }*/

    public ExtendedChildNumber(BigInteger i, boolean isHardened) {
        super(false);
        this.bi = i;
        this.size = (short)i.toByteArray().length;
        this.isHardened = isHardened;
    }

    public ExtendedChildNumber(BigInteger i) {
        this(i, false);
    }

    public ExtendedChildNumber(Sha256Hash i, boolean isHardened) {
        super(false);
        this.bi = new BigInteger(1, i.getBytes());
        this.size = 32;
        this.isHardened = isHardened;
    }

    public ExtendedChildNumber(Sha256Hash i) {
        this(i, false);
    }

    public ExtendedChildNumber(byte [] i, boolean isHardened) {
        super(false);
        this.bi = new BigInteger(1, i);
        this.size = (short)i.length;
        this.isHardened = isHardened;
    }

    /** Returns the uint32 encoded form of the path element, including the most significant bit. */
    public int getI() {
        return bi.intValue();
    }

    /** Returns the uint32 encoded form of the path element, including the most significant bit. */
    public int i() { return bi.intValue() | (isHardened ? HARDENED_BIT : 0); }

    public boolean isHardened() {
        return isHardened;
    }

    public short getSize() {
        return size;
    }

    public BigInteger bi() {
        return bi;
    }

    /** Returns the child number without the hardening bit set (i.e. index in that part of the tree). */
    public int num() {
        return bi.intValue() & (~HARDENED_BIT);
    }


    @Override
    public String toString() {
        //if(bi.bi.compareTo(MAX_INT) < 0)
        //    return String.format(Locale.US, "%d%s", bi, isHardened() ? "H" : "");
        return String.format(Locale.US, "(%s)%s", bi.toString(16), isHardened() ? "H" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtendedChildNumber n = (ExtendedChildNumber)o;
        return bi.equals(n.bi) && isHardened == n.isHardened;
    }

    @Override
    public int hashCode() {
        return bi.hashCode();
    }

    @Override
    public int compareTo(ChildNumber other) {
        if(other instanceof ExtendedChildNumber)
            // note that in this implementation compareTo() is not consistent with equals()
            return this.bi.compareTo(((ExtendedChildNumber)other).bi());
        else
            return this.bi.compareTo(BigInteger.valueOf(other.num()));
    }
}
