/*
 * Copyright 2013 Matija Mazi.
 * Copyright 2014 Andreas Schildbach
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

package org.bitcoinj.crypto;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.script.Script;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * A deterministic key is a node in a {@link DeterministicHierarchy}. As per
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki">the BIP 32 specification</a> it is a pair
 * (key, chaincode). If you know its path in the tree and its chain code you can derive more keys from this. To obtain
 * one of these, you can call {@link HDKeyDerivation#createMasterPrivateKey(byte[])}.
 */
public interface IDeterministicKey extends IKey {

    /** Sorts deterministic keys in the order of their child number. That's <i>usually</i> the order used to derive them. */
    Comparator<IKey> CHILDNUM_ORDER = new Comparator<IKey>() {
        @Override
        public int compare(IKey k1, IKey k2) {
            ChildNumber cn1 = ((IDeterministicKey) k1).getChildNumber();
            ChildNumber cn2 = ((IDeterministicKey) k2).getChildNumber();
            return cn1.compareTo(cn2);
        }
    };

    /**
     * Return the fingerprint of this key's parent as an int value, or zero if this key is the
     * root node of the key hierarchy.  Raise an exception if the arguments are inconsistent.
     * This method exists to avoid code repetition in the constructors.
     */
//    private int ascertainParentFingerprint(IDeterministicKey parentKey, int parentFingerprint)
//    throws IllegalArgumentException {
//        if (parentFingerprint != 0) {
//            if (parent != null)
//                checkArgument(parent.getFingerprint() == parentFingerprint,
//                              "parent fingerprint mismatch",
//                              Integer.toHexString(parent.getFingerprint()), Integer.toHexString(parentFingerprint));
//            return parentFingerprint;
//        } else return 0;
//    }

    /**
     * Returns the path through some {@link DeterministicHierarchy} which reaches this keys position in the tree.
     * A path can be written as 0/1/0 which means the first child of the root, the second child of that node, then
     * the first child of that node.
     */
    ImmutableList<ChildNumber> getPath();
    
    /**
     * Returns the path of this key as a human readable string starting with M to indicate the master key.
     */
    String getPathAsString();

    /**
     * Return this key's depth in the hierarchy, where the root node is at depth zero.
     * This may be different than the number of segments in the path if this key was
     * deserialized without access to its parent.
     */
    int getDepth();

    /** Returns the last element of the path returned by {@link IDeterministicKey#getPath()} */
    ChildNumber getChildNumber();

    /**
     * Returns the chain code associated with this key. See the specification to learn more about chain codes.
     */
    byte[] getChainCode();

    /**
     * Returns RIPE-MD160(SHA256(pub key bytes)).
     */
    byte[] getIdentifier();

    /** Returns the first 32 bits of the result of {@link #getIdentifier()}. */
    int getFingerprint();

    @Nullable
    IDeterministicKey getParent();

    /**
     * Return the fingerprint of the key from which this key was derived, if this is a
     * child key, or else an array of four zero-value bytes.
     */
    public int getParentFingerprint();

    IDeterministicKey dropPrivateBytes();

    /**
     * <p>Returns the same key with the parent pointer removed (it still knows its own path and the parent fingerprint).</p>
     *
     * <p>If this key doesn't have private key bytes stored/cached itself, but could rederive them from the parent, then
     * the new key returned by this method won't be able to do that. Thus, using dropPrivateBytes().dropParent() on a
     * regular DeterministicKey will yield a new DeterministicKey that cannot sign or do other things involving the
     * private key at all.</p>
     */
    IDeterministicKey dropParent();

//    static byte[] addChecksum(byte[] input) {
//        int inputLength = input.length;
//        byte[] checksummed = new byte[inputLength + 4];
//        System.arraycopy(input, 0, checksummed, 0, inputLength);
//        byte[] checksum = Sha256Hash.hashTwice(input);
//        System.arraycopy(checksum, 0, checksummed, inputLength, 4);
//        return checksummed;
//    }

//    @Override
//    IDeterministicKey encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
//        throw new UnsupportedOperationException("Must supply a new parent for encryption");
//    }
//
    IDeterministicKey encrypt(KeyCrypter keyCrypter, KeyParameter aesKey, @Nullable IDeterministicKey newParent) throws KeyCrypterException;
    //{
//        // Same as the parent code, except we construct a DeterministicKey instead of an ECKey.
//        checkNotNull(keyCrypter);
//        if (newParent != null)
//            checkArgument(newParent.isEncrypted());
//        final byte[] privKeyBytes = getPrivKeyBytes();
//        checkState(privKeyBytes != null, "Private key is not available");
//        EncryptedData encryptedPrivateKey = keyCrypter.encrypt(privKeyBytes, aesKey);
//        IDeterministicKey key = new IDeterministicKey(childNumberPath, chainCode, keyCrypter, pub, encryptedPrivateKey, newParent);
//        if (newParent == null)
//            key.setCreationTimeSeconds(getCreationTimeSeconds());
//        return key;
//    }

    @Nullable
    @Override
    byte[] getSecretBytes();

    /**
     * A deterministic key is considered to be encrypted if it has access to encrypted private key bytes, OR if its
     * parent does. The reason is because the parent would be encrypted under the same key and this key knows how to
     * rederive its own private key bytes from the parent, if needed.
     */
    @Override
    boolean isEncrypted();

//    @Override
//    public ECDSASignature sign(Sha256Hash input, @Nullable KeyParameter aesKey) throws KeyCrypterException {
//        if (isEncrypted()) {
//            // If the key is encrypted, ECKey.sign will decrypt it first before rerunning sign. Decryption walks the
//            // key hierarchy to find the private key (see below), so, we can just run the inherited method.
//            return super.sign(input, aesKey);
//        } else {
//            // If it's not encrypted, derive the private via the parents.
//            final BigInteger privateKey = findOrDerivePrivateKey();
//            if (privateKey == null) {
//                // This key is a part of a public-key only hierarchy and cannot be used for signing
//                throw new MissingPrivateKeyException();
//            }
//            return super.doSign(input, privateKey);
//        }
//    }

//    // For when a key is encrypted, either decrypt our encrypted private key bytes, or work up the tree asking parents
//    // to decrypt and re-derive.
//    private BigInteger findOrDeriveEncryptedPrivateKey(KeyCrypter keyCrypter, KeyParameter aesKey) {
//        if (encryptedPrivateKey != null) {
//            byte[] decryptedKey = keyCrypter.decrypt(encryptedPrivateKey, aesKey);
//            if (decryptedKey.length != 32)
//                throw new KeyCrypterException.InvalidCipherText(
//                        "Decrypted key must be 32 bytes long, but is " + decryptedKey.length);
//            return new BigInteger(1, decryptedKey);
//        }
//        // Otherwise we don't have it, but maybe we can figure it out from our parents. Walk up the tree looking for
//        // the first key that has some encrypted private key data.
//        IDeterministicKey cursor = parent;
//        while (cursor != null) {
//            if (cursor.encryptedPrivateKey != null) break;
//            cursor = cursor.parent;
//        }
//        if (cursor == null)
//            throw new KeyCrypterException("Neither this key nor its parents have an encrypted private key");
//        byte[] parentalPrivateKeyBytes = keyCrypter.decrypt(cursor.encryptedPrivateKey, aesKey);
//        if (parentalPrivateKeyBytes.length != 32)
//            throw new KeyCrypterException.InvalidCipherText(
//                    "Decrypted key must be 32 bytes long, but is " + parentalPrivateKeyBytes.length);
//        return derivePrivateKeyDownwards(cursor, parentalPrivateKeyBytes);
//    }

//    private IDeterministicKey findParentWithPrivKey() {
//        IDeterministicKey cursor = this;
//        while (cursor != null) {
//            if (cursor.priv != null) break;
//            cursor = cursor.parent;
//        }
//        return cursor;
//    }
//
//    @Nullable
//    private BigInteger findOrDerivePrivateKey() {
//        IDeterministicKey cursor = findParentWithPrivKey();
//        if (cursor == null)
//            return null;
//        return derivePrivateKeyDownwards(cursor, cursor.priv.toByteArray());
//    }
//
//    private BigInteger derivePrivateKeyDownwards(IDeterministicKey cursor, byte[] parentalPrivateKeyBytes) {
//        IDeterministicKey downCursor = new IDeterministicKey(cursor.childNumberPath, cursor.chainCode,
//                cursor.pub, new BigInteger(1, parentalPrivateKeyBytes), cursor.parent);
//        // Now we have to rederive the keys along the path back to ourselves. That path can be found by just truncating
//        // our path with the length of the parents path.
//        ImmutableList<ChildNumber> path = childNumberPath.subList(cursor.getPath().size(), childNumberPath.size());
//        for (ChildNumber num : path) {
//            downCursor = HDKeyDerivation.deriveChildKey(downCursor, num);
//        }
//        // downCursor is now the same key as us, but with private key bytes.
//        // If it's not, it means we tried decrypting with an invalid password and earlier checks e.g. for padding didn't
//        // catch it.
//        if (!downCursor.pub.equals(pub))
//            throw new KeyCrypterException.PublicPrivateMismatch("Could not decrypt bytes");
//        return checkNotNull(downCursor.priv);
//    }

    /**
     * Derives a child at the given index using hardened derivation.  Note: {@code index} is
     * not the "i" value.  If you want the softened derivation, then use instead
     * {@code HDKeyDerivation.deriveChildKey(this, new ChildNumber(child, false))}.
     */
    IDeterministicKey derive(int child);

//    /**
//     * Returns the private key of this deterministic key. Even if this object isn't storing the private key,
//     * it can be re-derived by walking up to the parents if necessary and this is what will happen.
//     * @throws IllegalStateException if the parents are encrypted or a watching chain.
//     */
//    @Override
//    public BigInteger getPrivKey() {
//        final BigInteger key = findOrDerivePrivateKey();
//        checkState(key != null, "Private key bytes not available");
//        return key;
//    }

//    @Deprecated
//    public byte[] serializePublic(NetworkParameters params) {
//        return serialize(params, true, Script.ScriptType.P2PKH);
//    }
//
//    @Deprecated
//    public byte[] serializePrivate(NetworkParameters params) {
//        return serialize(params, false, Script.ScriptType.P2PKH);
//    }
//
//    private byte[] serialize(NetworkParameters params, boolean pub, Script.ScriptType outputScriptType) {
//        ByteBuffer ser = ByteBuffer.allocate(78);
//        if (outputScriptType == Script.ScriptType.P2PKH)
//            ser.putInt(pub ? params.getBip32HeaderP2PKHpub() : params.getBip32HeaderP2PKHpriv());
//        else
//            throw new IllegalStateException(outputScriptType.toString());
//        ser.put((byte) getDepth());
//        ser.putInt(getParentFingerprint());
//        ser.putInt(getChildNumber().i());
//        ser.put(getChainCode());
//        ser.put(pub ? getPubKey() : getPrivKeyBytes33());
//        checkState(ser.position() == 78);
//        return ser.array();
//    }

    String serializePubB58(NetworkParameters params);
    String serializePubB58(NetworkParameters params, Script.ScriptType outputScriptType);
    String serializePrivB58(NetworkParameters params);
    String serializePrivB58(NetworkParameters params, Script.ScriptType outputScriptType);

    IDeterministicKey deriveChildKey(ChildNumber childNumber);

//    String serializePubB58(NetworkParameters params) {
//        return serializePubB58(params, Script.ScriptType.P2PKH);
//    }
//
//    public String serializePrivB58(NetworkParameters params) {
//        return serializePrivB58(params, Script.ScriptType.P2PKH);
//    }
//
//    private byte[] serializeDip14(NetworkParameters params, boolean pub, Script.ScriptType outputScriptType) {
//        ByteBuffer ser = ByteBuffer.allocate(107);
//        if (outputScriptType == Script.ScriptType.P2PKH)
//            ser.putInt(pub ? params.getDip14HeaderP2PKHpub() : params.getDip14HeaderP2PKHpriv());
//        else
//            throw new IllegalStateException(outputScriptType.toString());
//        ser.put((byte) getDepth());
//        ser.putInt(getParentFingerprint());
//        if (getChildNumber() instanceof ExtendedChildNumber) {
//            ExtendedChildNumber childNumber = (ExtendedChildNumber) getChildNumber();
//            ser.put((byte)(childNumber.isHardened() ? 1 : 0));
//            ser.put(Sha256Hash.wrap(childNumber.bi()).getBytes());
//            ser.put(getChainCode());
//            ser.put(pub ? getPubKey() : getPrivKeyBytes33());
//            checkState(ser.position() == 107);
//            return ser.array();
//        } else {
//            throw new IllegalStateException("This deterministic key does not have an extended child number");
//        }
//    }
//
//    public String serializeDip14PubB58(NetworkParameters params, Script.ScriptType outputScriptType) {
//        return toBase58(serializeDip14(params, true, outputScriptType));
//    }
//
//    public String serializeDip14PrivB58(NetworkParameters params, Script.ScriptType outputScriptType) {
//        return toBase58(serializeDip14(params, false, outputScriptType));
//    }
//
//    public String serializeDip14PubB58(NetworkParameters params) {
//        return serializeDip14PubB58(params, Script.ScriptType.P2PKH);
//    }
//
//    public String serializeDip14PrivB58(NetworkParameters params) {
//        return serializeDip14PrivB58(params, Script.ScriptType.P2PKH);
//    }
//
//    /** serializes a HD Key according to the dashpay encryptedPublicKey specification **/
//    public byte[] serializeContactPub() {
//        ByteBuffer ser = ByteBuffer.allocate(69);
//        // header (xpub) not included
//        // depth not included
//        ser.putInt(getParentFingerprint());
//        // child number not included
//        ser.put(getChainCode());
//        ser.put(getPubKey());
//        checkState(ser.position() == 69);
//        return ser.array();
//    }
//
//    public static IDeterministicKey deserializeContactPub(NetworkParameters params, byte [] contactPub) {
//        checkArgument(contactPub.length == 69);
//        ByteBuffer serXPub = ByteBuffer.allocate(78);
//        serXPub.putInt(params.getBip32HeaderP2PKHpub()); // header
//        serXPub.put((byte) 7); // use depth 0 (master)
//        serXPub.putInt((int)Utils.readUint32(contactPub, 0)); // fingerprint
//        serXPub.putInt(0); // use child number 0
//        serXPub.put(Arrays.copyOfRange(contactPub, 4, 36)); // chain code
//        serXPub.put(Arrays.copyOfRange(contactPub, 36, contactPub.length)); //public key
//        checkState(serXPub.position() == 78);
//        return deserialize(params, serXPub.array(), null);
//    }
//
//    static String toBase58(byte[] ser) {
//        return Base58.encode(addChecksum(ser));
//    }
//
//    /** Deserialize a base-58-encoded HD Key with no parent */
//    public static IDeterministicKey deserializeB58(String base58, NetworkParameters params) {
//        return deserializeB58(null, base58, params);
//    }
//
//    /**
//      * Deserialize a base-58-encoded HD Key.
//      *  @param parent The parent node in the given key's deterministic hierarchy.
//      *  @throws IllegalArgumentException if the base58 encoded key could not be parsed.
//      */
//    public static IDeterministicKey deserializeB58(@Nullable IDeterministicKey parent, String base58, NetworkParameters params) {
//        return deserialize(params, Base58.decodeChecked(base58), parent);
//    }
//
//    /**
//     * Deserialize a base-58-encoded HD Key and associates it with a given path.
//     *  @throws IllegalArgumentException if the base58 encoded key could not be parsed.
//     */
//    public static IDeterministicKey deserializeB58(String base58, ImmutableList<ChildNumber> path, NetworkParameters params) {
//        return deserialize(params, Base58.decodeChecked(base58), null, path);
//    }
//
//    /**
//      * Deserialize an HD Key with no parent
//      */
//    public static IDeterministicKey deserialize(NetworkParameters params, byte[] serializedKey) {
//        return deserialize(params, serializedKey, (IDeterministicKey)null);
//    }
//
//    /**
//      * Deserialize an HD Key.
//     * @param parent The parent node in the given key's deterministic hierarchy.
//     */
//    public static IDeterministicKey deserialize(NetworkParameters params, byte[] serializedKey, @Nullable IDeterministicKey parent) {
//        ByteBuffer buffer = ByteBuffer.wrap(serializedKey);
//        int header = buffer.getInt();
//        final boolean pub = header == params.getBip32HeaderP2PKHpub() || header == params.getBip32HeaderP2WPKHpub();
//        final boolean priv = header == params.getBip32HeaderP2PKHpriv() || header == params.getBip32HeaderP2WPKHpriv();
//        if (!(pub || priv))
//            throw new IllegalArgumentException("Unknown header bytes: " + toBase58(serializedKey).substring(0, 4));
//        int depth = buffer.get() & 0xFF; // convert signed byte to positive int since depth cannot be negative
//        final int parentFingerprint = buffer.getInt();
//        final int i = buffer.getInt();
//        final ChildNumber childNumber = new ChildNumber(i);
//        ImmutableList<ChildNumber> path;
//        if (parent != null) {
//            if (parentFingerprint == 0)
//                throw new IllegalArgumentException("Parent was provided but this key doesn't have one");
//            if (parent.getFingerprint() != parentFingerprint)
//                throw new IllegalArgumentException("Parent fingerprints don't match");
//            path = HDUtils.append(parent.getPath(), childNumber);
//            if (path.size() != depth)
//                throw new IllegalArgumentException("Depth does not match");
//        } else {
//            if (depth >= 1)
//                // We have been given a key that is not a root key, yet we lack the object representing the parent.
//                // This can happen when deserializing an account key for a watching wallet.  In this case, we assume that
//                // the client wants to conceal the key's position in the hierarchy.  The path is truncated at the
//                // parent's node.
//                path = ImmutableList.of(childNumber);
//            else path = ImmutableList.of();
//        }
//        byte[] chainCode = new byte[32];
//        buffer.get(chainCode);
//        byte[] data = new byte[33];
//        buffer.get(data);
//        checkArgument(!buffer.hasRemaining(), "Found unexpected data in key");
//        if (pub) {
//            return new IDeterministicKey(path, chainCode, new LazyECPoint(ECKey.CURVE.getCurve(), data), parent, depth, parentFingerprint);
//        } else {
//            return new IDeterministicKey(path, chainCode, new BigInteger(1, data), parent, depth, parentFingerprint);
//        }
//    }
//
//    /**
//     * Deserialize an HD Key and associate it with a full path.
//     */
//    public static IDeterministicKey deserialize(NetworkParameters params, byte[] serializedKey, IDeterministicKey parent, ImmutableList<ChildNumber> fullPath) {
//        ByteBuffer buffer = ByteBuffer.wrap(serializedKey);
//        int header = buffer.getInt();
//        if (header != params.getBip32HeaderP2PKHpriv() && header != params.getBip32HeaderP2PKHpub())
//            throw new IllegalArgumentException("Unknown header bytes: " + toBase58(serializedKey).substring(0, 4));
//        boolean pub = header == params.getBip32HeaderP2PKHpub();
//        int depth = buffer.get() & 0xFF; // convert signed byte to positive int since depth cannot be negative
//        final int parentFingerprint = buffer.getInt();
//        final int i = buffer.getInt();
//        ImmutableList<ChildNumber> path;
//
//        if (depth >= 1)
//            path = fullPath;
//        else path = ImmutableList.of();
//
//        byte[] chainCode = new byte[32];
//        buffer.get(chainCode);
//        byte[] data = new byte[33];
//        buffer.get(data);
//        checkArgument(!buffer.hasRemaining(), "Found unexpected data in key");
//        if (pub) {
//            return new IDeterministicKey(path, chainCode, new LazyECPoint(ECKey.CURVE.getCurve(), data), null, depth, parentFingerprint);
//        } else {
//            return new IDeterministicKey(path, chainCode, new BigInteger(1, data), null, depth, parentFingerprint);
//        }
//    }
//
//    /**
//     * The creation time of a deterministic key is equal to that of its parent, unless this key is the root of a tree
//     * in which case the time is stored alongside the key as per normal, see {@link ECKey#getCreationTimeSeconds()}.
//     */
//    @Override
//    public long getCreationTimeSeconds() {
//        if (parent != null)
//            return parent.getCreationTimeSeconds();
//        else
//            return super.getCreationTimeSeconds();
//    }
//
//    /**
//     * The creation time of a deterministic key is equal to that of its parent, unless this key is the root of a tree.
//     * Thus, setting the creation time on a leaf is forbidden.
//     */
//    @Override
//    public void setCreationTimeSeconds(long newCreationTimeSeconds) {
//        if (parent != null)
//            throw new IllegalStateException("Creation time can only be set on root keys.");
//        else
//            super.setCreationTimeSeconds(newCreationTimeSeconds);
//    }
//
//    /**
//     * Verifies equality of all fields but NOT the parent pointer (thus the same key derived in two separate hierarchy
//     * objects will equal each other.
//     */
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//        IDeterministicKey other = (IDeterministicKey) o;
//        return super.equals(other)
//                && Arrays.equals(this.chainCode, other.chainCode)
//                && Objects.equal(this.childNumberPath, other.childNumberPath);
//    }
//
//    @Override
//    public int hashCode() {
//        return Objects.hashCode(super.hashCode(), Arrays.hashCode(chainCode), childNumberPath);
//    }
//
//    @Override
//    public String toString() {
//        final MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
//        helper.add("pub", Utils.HEX.encode(pub.getEncoded()));
//        helper.add("chainCode", HEX.encode(chainCode));
//        helper.add("path", getPathAsString());
//        if (creationTimeSeconds > 0)
//            helper.add("creationTimeSeconds", creationTimeSeconds);
//        helper.add("isEncrypted", isEncrypted());
//        helper.add("isPubKeyOnly", isPubKeyOnly());
//        return helper.toString();
//    }
//
//    @Override
//    public void formatKeyWithAddress(boolean includePrivateKeys, @Nullable KeyParameter aesKey, StringBuilder builder,
//            NetworkParameters params, Script.ScriptType outputScriptType, @Nullable String comment) {
//        builder.append("  addr:").append(Address.fromKey(params, this, outputScriptType).toString());
//        builder.append("  hash160:").append(Utils.HEX.encode(getPubKeyHash()));
//        builder.append("  (").append(getPathAsString());
//        if (comment != null)
//            builder.append(", ").append(comment);
//        builder.append(")\n");
//        if (includePrivateKeys) {
//            builder.append("  ").append(toStringWithPrivate(aesKey, params)).append("\n");
//        }
//    }

    IDeterministicKey deriveThisOrNextChildKey(int nextChild);
}
