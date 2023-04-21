/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 * Copyright 2014-2016 the libsecp256k1 contributors
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.io.BaseEncoding;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyType;
import org.bitcoinj.crypto.LinuxSecureRandom;
import org.bitcoinj.crypto.factory.BLSKeyFactory;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * <p>Represents a BLS key pair.</p>
 */
public class BLSKey implements IKey {
    private static final Logger log = LoggerFactory.getLogger(BLSKey.class);

    private static final SecureRandom secureRandom;

    static {
        // Init proper random number generator, as some old Android installations have bugs that make it unsecure.
        if (Utils.isAndroidRuntime())
            new LinuxSecureRandom();

        secureRandom = new SecureRandom();
    }

    // The two parts of the key. If "priv" is set, "pub" can always be calculated. If "pub" is set but not "priv", we
    // can only verify signatures not make them.
    @Nullable protected BLSSecretKey priv;
    protected BLSPublicKey pub;

    // Creation time of the key in seconds since the epoch, or zero if the key was deserialized from a version that did
    // not have this field.
    protected long creationTimeSeconds;

    protected KeyCrypter keyCrypter;
    protected EncryptedData encryptedPrivateKey;

    private byte[] pubKeyHash;

    /**
     * Generates an entirely new keypair. Point compression is used so the resulting public key will be 33 bytes
     * (32 for the co-ordinate and 1 byte to represent the y bit).
     */
    public BLSKey() {
        this(secureRandom);
    }

    /**
     * Generates an entirely new keypair with the given {@link SecureRandom} object.
     */
    public BLSKey(SecureRandom secureRandom) {
        byte [] seed = new byte[64];
        secureRandom.nextBytes(seed);
        priv = BLSSecretKey.fromSeed(seed);
        pub = priv.getPublicKey();
        creationTimeSeconds = Utils.currentTimeSeconds();
    }

    public BLSKey(byte[] seed) {
        priv = BLSSecretKey.fromSeed(seed);
        pub = priv.getPublicKey();
    }

    /**
     * Creates an BLSKey given the private key only. The public key is calculated from it (this is slow). The resulting
     * public key is compressed.
     */
    public static BLSKey fromPrivate(byte[] privKeyBytes) {
        BLSSecretKey secretKey = new BLSSecretKey(privKeyBytes);
        return new BLSKey(secretKey, secretKey.getPublicKey());
    }

    /**
     * Creates an BLSKey that simply trusts the caller to ensure that point is really the result of multiplying the
     * generator point by the private key. This is used to speed things up when you know you have the right values
     * already. The compression state of the point will be preserved.
     */
    public static BLSKey fromPrivateAndPrecalculatedPublic(byte[] priv, byte[] pub) {
        checkNotNull(priv);
        checkNotNull(pub);
        return new BLSKey(priv, pub);
    }

    /**
     * Creates an BLSKey that cannot be used for signing, only verifying signatures, from the given encoded point.
     * The compression state of pub will be preserved.
     */
    public static BLSKey fromPublicOnly(byte[] pub) {
        return new BLSKey((byte[]) null, pub);
    }

    public static BLSKey fromPublicOnly(BLSKey key) {
        return fromPublicOnly(key.getPubKey());
    }

    /**
     * Returns a copy of this key, but with the public point represented in uncompressed form. Normally you would
     * never need this: it's for specialised scenarios or when backwards compatibility in encoded form is necessary.
     */
    public BLSKey decompress() {
        return new BLSKey(priv != null ? priv.bitcoinSerialize() : null, pub.bitcoinSerialize());
    }

    /**
     * Creates an BLSKey given only the private key bytes. This is the same as using the BigInteger constructor, but
     * is more convenient if you are importing a key from elsewhere. The public key will be automatically derived
     * from the private key.
     */
    public BLSKey(@Nullable byte[] privKeyBytes, @Nullable byte[] pubKey) {
        priv = privKeyBytes == null ? null : new BLSSecretKey(privKeyBytes);
        pub = pubKey == null ? (priv != null ? priv.getPublicKey() : null) : new BLSPublicKey(pubKey);
    }

    /**
     * Creates an BLSKey given only the private key bytes. This is the same as using the BigInteger constructor, but
     * is more convenient if you are importing a key from elsewhere. The public key will be automatically derived
     * from the private key.
     */
    public BLSKey(@Nullable BLSSecretKey privKey, @Nullable BLSPublicKey pubKey) {
        priv = privKey;
        pub = pubKey;
    }

    /**
     * Create a new BLSKey with an encrypted private key, a public key and a KeyCrypter.
     *
     * @param encryptedPrivateKey The private key, encrypted,
     * @param pubKey The keys public key
     * @param keyCrypter The KeyCrypter that will be used, with an AES key, to encrypt and decrypt the private key
     */
    @Deprecated
    public BLSKey(EncryptedData encryptedPrivateKey, byte[] pubKey, KeyCrypter keyCrypter) {
        this((byte[])null, pubKey);

        this.keyCrypter = checkNotNull(keyCrypter);
        this.encryptedPrivateKey = encryptedPrivateKey;
    }

    /**
     * Constructs a key that has an encrypted private component. The given object wraps encrypted bytes and an
     * initialization vector. Note that the key will not be decrypted during this call: the returned BLSKey is
     * unusable for signing unless a decryption key is supplied.
     */
    public static BLSKey fromEncrypted(EncryptedData encryptedPrivateKey, KeyCrypter crypter, byte[] pubKey) {
        BLSKey key = fromPublicOnly(pubKey);
        key.encryptedPrivateKey = checkNotNull(encryptedPrivateKey);
        key.keyCrypter = checkNotNull(crypter);
        return key;
    }

    /**
     * Returns true if this key doesn't have unencrypted access to private key bytes. This may be because it was never
     * given any private key bytes to begin with (a watching key), or because the key is encrypted. You can use
     * {@link #isEncrypted()} to tell the cases apart.
     */
    public boolean isPubKeyOnly() {
        return priv == null;
    }

    /**
     * Returns true if this key has unencrypted access to private key bytes. Does the opposite of
     * {@link #isPubKeyOnly()}.
     */
    public boolean hasPrivKey() {
        return priv != null;
    }

    /** Returns true if this key is watch only, meaning it has a public key but no private key. */
    public boolean isWatching() {
        return isPubKeyOnly() && !isEncrypted();
    }

    /** Gets the hash160 form of the public key (as seen in addresses). */
    public byte[] getPubKeyHash() {
        if (pubKeyHash == null)
            pubKeyHash = Utils.sha256hash160(this.pub.bitcoinSerialize());
        return pubKeyHash;
    }

    /**
     * Gets the raw public key value using the BLS scheme of the public key object.
     */
    public byte[] getPubKey() {
        return pub.bitcoinSerialize(pub.isLegacy());
    }

    /** Gets the public key in the form of an elliptic curve point object from Bouncy Castle. */
    public BLSPublicKey getPubKeyObject() {
        return pub;
    }

    @Override
    public KeyFactory getKeyFactory() {
        return BLSKeyFactory.get();
    }

    /**
     * Gets the private key in the form of an integer field element. The public key is derived by performing EC
     * point addition this number of times (i.e. point multiplying).
     *
     * @throws IllegalStateException if the private key bytes are not available.
     */
    public BLSSecretKey getPrivKey() {
        if (priv == null)
            throw new MissingPrivateKeyException();
        return priv;
    }

    /**
     * Returns whether this key is using the compressed form or not. Compressed pubkeys are only 33 bytes, not 64.
     */
    public boolean isCompressed() {
        return false;
    }

    /**
     * Signs the given hash and returns the BLSSignature.
     * @throws KeyCrypterException if this BLSKey doesn't have a private part.
     */
    public BLSSignature sign(Sha256Hash input) throws KeyCrypterException {
        return priv.sign(input);
    }

    /**
     * If this global variable is set to true, sign() creates a dummy signature and verify() always returns true.
     * This is intended to help accelerate unit tests that do a lot of signing/verifying, which in the debugger
     * can be painfully slow.
     */
    @VisibleForTesting
    public static boolean FAKE_SIGNATURES = false;

    /**
     * Signs the given hash and returns the BLSSignature.
     *
     * @param aesKey The AES key to use for decryption of the private key. If null then no decryption is required.
     * @throws KeyCrypterException if there's something wrong with aesKey.
     * @throws BLSKey.MissingPrivateKeyException if this key cannot sign because it's pubkey only.
     */
    public BLSSignature sign(Sha256Hash input, @Nullable KeyParameter aesKey) throws KeyCrypterException {
        KeyCrypter crypter = getKeyCrypter();
        if (crypter != null) {
            if (aesKey == null)
                throw new KeyIsEncryptedException();
            return decrypt(aesKey).sign(input);
        } else {
            // No decryption of private key required.
            if (priv == null)
                throw new MissingPrivateKeyException();
        }
        return doSign(input, this);
    }

    protected BLSSignature doSign(Sha256Hash input, BLSKey privateKeyForSigning) {
        if (FAKE_SIGNATURES)
            return BLSSignature.dummy();
        return privateKeyForSigning.sign(input);
    }

    protected BLSSignature doSign(Sha256Hash input, BLSSecretKey privateKeyForSigning) {
        if (FAKE_SIGNATURES)
            return BLSSignature.dummy();
        return privateKeyForSigning.sign(input);
    }

    /**
     * <p>Verifies the given BLS signature against the message bytes using the public key bytes.</p>
     *
     * @param data      Hash of the data to verify.
     * @param signature BLS encoded signature.
     * @param pub       The public key bytes to use.
     */
    public static boolean verify(byte[] data, BLSSignature signature, byte[] pub) {
        if (FAKE_SIGNATURES)
            return true;

        try {
            return signature.verifyInsecure(new BLSPublicKey(pub), Sha256Hash.wrap(data));
        } catch (Exception e) {
            log.error("Caught NPE inside bls-signatures", e);
            return false;
        }
    }

    /**
     * Verifies the given ASN.1 encoded ECDSA signature against a hash using the public key.
     *
     * @param data      Hash of the data to verify.
     * @param signature ASN.1 encoded signature.
     * @param pub       The public key bytes to use.
     * @throws SignatureDecodeException if the signature is unparseable in some way.
     */
    public static boolean verify(byte[] data, byte[] signature, byte[] pub) throws SignatureDecodeException {
        return verify(data, new BLSSignature(signature), pub);
    }

    /**
     * Verifies the given ASN.1 encoded ECDSA signature against a hash using the public key.
     *
     * @param hash      Hash of the data to verify.
     * @param signature ASN.1 encoded signature.
     * @throws SignatureDecodeException if the signature is unparseable in some way.
     */
    public boolean verify(byte[] hash, byte[] signature) throws SignatureDecodeException {
        return BLSKey.verify(hash, signature, getPubKey());
    }

    /**
     * Verifies the given R/S pair (signature) against a hash using the public key.
     */
    public boolean verify(Sha256Hash sigHash, BLSSignature signature) {
        return BLSKey.verify(sigHash.getBytes(), signature, getPubKey());
    }

    /**
     * Verifies the given ASN.1 encoded ECDSA signature against a hash using the public key, and throws an exception
     * if the signature doesn't match
     * @throws SignatureDecodeException if the signature is unparseable in some way.
     * @throws SignatureException if the signature does not match.
     */
    public void verifyOrThrow(byte[] hash, byte[] signature) throws SignatureDecodeException, SignatureException {
        if (!verify(hash, signature))
            throw new SignatureException();
    }

    /**
     * Verifies the given R/S pair (signature) against a hash using the public key, and throws an exception
     * if the signature doesn't match
     * @throws SignatureException if the signature does not match.
     */
    public void verifyOrThrow(Sha256Hash sigHash, BLSSignature signature) throws SignatureException {
        if (!BLSKey.verify(sigHash.getBytes(), signature, getPubKey()))
            throw new SignatureException();
    }

    /**
     * Returns true if the given pubkey is canonical, i.e. the correct length taking into account compression.
     */
    public static boolean isPubKeyCanonical(byte[] pubkey) {
        return true;
    }

    /**
     * Returns true if the given pubkey is in its compressed form.
     */
    public static boolean isPubKeyCompressed(byte[] encoded) {
        if (encoded.length < BLSPublicKey.BLS_CURVE_PUBKEY_SIZE)
            throw new IllegalArgumentException("public key is too short");
        if (encoded.length > BLSPublicKey.BLS_CURVE_PUBKEY_SIZE)
            throw new IllegalArgumentException("public key is too long");
        return false;
    }

    /**
     * Signs a text message using the standard Bitcoin messaging signing format and returns the signature as a base64
     * encoded string.
     *
     * @throws IllegalStateException if this BLSKey does not have the private part.
     * @throws KeyCrypterException if this BLSKey is encrypted and no AESKey is provided or it does not decrypt the BLSKey.
     */
    public String signMessage(String message) throws KeyCrypterException {
        return signMessage(message, null);
    }

    /**
     * Signs a text message using the standard Bitcoin messaging signing format and returns the signature as a base64
     * encoded string.
     *
     * @throws IllegalStateException if this BLSKey does not have the private part.
     * @throws KeyCrypterException if this BLSKey is encrypted and no AESKey is provided or it does not decrypt the BLSKey.
     */
    public String signMessage(String message, @Nullable KeyParameter aesKey) throws KeyCrypterException {
        byte[] data = Utils.formatMessageForSigning(message);
        Sha256Hash hash = Sha256Hash.twiceOf(data);
        BLSSignature sig = sign(hash, aesKey);
        byte[] sigData = sig.bitcoinSerialize();
        return new String(Base64.encode(sigData), StandardCharsets.UTF_8);
    }

    /**
     * Signs a hash using the standard Bitcoin messaging signing format and returns the signature as a byte array.
     *
     * @throws IllegalStateException if this BLSKey does not have the private part.
     * @throws KeyCrypterException if this BLSKey is encrypted and no AESKey is provided or it does not decrypt the BLSKey.
     */
    public byte [] signHash(Sha256Hash hash) throws KeyCrypterException {
        return signHash(hash, null);
    }

    /**
     * Signs a hash using the standard Bitcoin messaging signing format and returns the signature as a byte array.
     *
     * @throws IllegalStateException if this BLSKey does not have the private part.
     * @throws KeyCrypterException if this BLSKey is encrypted and no AESKey is provided or it does not decrypt the BLSKey.
     */
    public byte [] signHash(Sha256Hash hash, @Nullable KeyParameter aesKey) throws KeyCrypterException {
        BLSSignature sig = sign(hash, aesKey);
        byte[] sigData = sig.bitcoinSerialize();
        return sigData;
    }

    /**
     * If the signature cannot be verified, throws a SignatureException.
     */
    public void verifyMessage(String message, String signatureBase64) throws SignatureException {
        byte [] messageForSigning = Utils.formatMessageForSigning(message);
        BLSSignature signature = new BLSSignature(BaseEncoding.base64().decode(signatureBase64));
        if (!signature.verifyInsecure(pub, Sha256Hash.twiceOf(messageForSigning)))
            throw new SignatureException("Signature did not match for message");
    }
    public void verifyMessage(byte [] message, byte [] signatureEncoded) throws SignatureException {
        BLSSignature signature = new BLSSignature(signatureEncoded);
        byte[] messageBytes = Utils.formatMessageForSigning(message);
        if (!signature.verifyInsecure(pub, Sha256Hash.twiceOf(messageBytes)))
            throw new SignatureException("Signature did not match for message");
    }

    /**
     * Returns a 32 byte array containing the private key.
     * @throws BLSKey.MissingPrivateKeyException if the private key bytes are missing/encrypted.
     */
    public byte[] getPrivKeyBytes() {
        if (priv == null)
            throw new MissingPrivateKeyException();
        return getPrivKey().bitcoinSerialize();
    }

    /**
     * Exports the private key in the form used by Dash Core's "dumpprivkey" and "importprivkey" commands. Use
     * the {@link DumpedPrivateKey#toString()} method to get the string.
     *
     * @param params The network this key is intended for use on.
     * @return Private key bytes as a {@link DumpedPrivateKey}.
     * @throws IllegalStateException if the private key is not available.
     */
    @Override
    public DumpedPrivateKey getPrivateKeyEncoded(NetworkParameters params) {
        return new DumpedPrivateKey(params, getPrivKeyBytes(), isCompressed(), getKeyFactory());
    }

    @Override
    public byte getPrivateKeyCompressedByte() {
        return 0x02;
    }


    /**
     * Returns the creation time of this key or zero if the key was deserialized from a version that did not store
     * that data.
     */
    @Override
    public long getCreationTimeSeconds() {
        return creationTimeSeconds;
    }

    /**
     * Sets the creation time of this key. Zero is a convention to mean "unavailable". This method can be useful when
     * you have a raw key you are importing from somewhere else.
     */
    public void setCreationTimeSeconds(long newCreationTimeSeconds) {
        if (newCreationTimeSeconds < 0)
            throw new IllegalArgumentException("Cannot set creation time to negative value: " + newCreationTimeSeconds);
        creationTimeSeconds = newCreationTimeSeconds;
    }

    /**
     * Create an encrypted private key with the keyCrypter and the AES key supplied.
     * This method returns a new encrypted key and leaves the original unchanged.
     *
     * @param keyCrypter The keyCrypter that specifies exactly how the encrypted bytes are created.
     * @param aesKey The KeyParameter with the AES encryption key (usually constructed with keyCrypter#deriveKey and cached as it is slow to create).
     * @return encryptedKey
     */
    public BLSKey encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(keyCrypter);
        final byte[] privKeyBytes = getPrivKeyBytes();
        EncryptedData encryptedPrivateKey = keyCrypter.encrypt(privKeyBytes, aesKey);
        BLSKey result = BLSKey.fromEncrypted(encryptedPrivateKey, keyCrypter, getPubKey());
        result.setCreationTimeSeconds(creationTimeSeconds);
        return result;
    }

    /**
     * Create a decrypted private key with the keyCrypter and AES key supplied. Note that if the aesKey is wrong, this
     * has some chance of throwing KeyCrypterException due to the corrupted padding that will result, but it can also
     * just yield a garbage key.
     *
     * @param keyCrypter The keyCrypter that specifies exactly how the decrypted bytes are created.
     * @param aesKey The KeyParameter with the AES encryption key (usually constructed with keyCrypter#deriveKey and cached).
     */
    public BLSKey decrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(keyCrypter);
        // Check that the keyCrypter matches the one used to encrypt the keys, if set.
        if (this.keyCrypter != null && !this.keyCrypter.equals(keyCrypter))
            throw new KeyCrypterException("The keyCrypter being used to decrypt the key is different to the one that was used to encrypt it");
        checkState(encryptedPrivateKey != null, "This key is not encrypted");
        byte[] unencryptedPrivateKey = keyCrypter.decrypt(encryptedPrivateKey, aesKey);
        if (unencryptedPrivateKey.length != BLSSecretKey.BLS_CURVE_SECKEY_SIZE)
            throw new KeyCrypterException.InvalidCipherText(
                    "Decrypted key must be 32 bytes long, but is " + unencryptedPrivateKey.length);
        BLSKey key = BLSKey.fromPrivate(unencryptedPrivateKey);
        if (!Arrays.equals(key.getPubKey(), getPubKey()))
            throw new KeyCrypterException("Provided AES key is wrong");
        key.setCreationTimeSeconds(creationTimeSeconds);
        return key;
    }

    @Override
    public byte[] getSerializedSecretKey() {
        byte[] serializedPrivateKey = new byte[pub.getMessageSize() + 1];
        serializedPrivateKey[0] = (byte) (pub.isLegacy() ? 0 : 1);
        System.arraycopy(priv.bitcoinSerialize(), 0, serializedPrivateKey, 1, pub.getMessageSize());
        return serializedPrivateKey;
    }

    @Override
    public byte[] getSerializedPublicKey() {
        byte[] serializedPublicKey = new byte[pub.getMessageSize() + 1];
        serializedPublicKey[0] = (byte) (pub.isLegacy() ? 1 : 0);
        System.arraycopy(pub.bitcoinSerialize(pub.isLegacy()), 0, serializedPublicKey, 1, pub.getMessageSize());
        return serializedPublicKey;
    }

    /**
     * Create a decrypted private key with AES key. Note that if the AES key is wrong, this
     * has some chance of throwing KeyCrypterException due to the corrupted padding that will result, but it can also
     * just yield a garbage key.
     *
     * @param aesKey The KeyParameter with the AES encryption key (usually constructed with keyCrypter#deriveKey and cached).
     */
    public BLSKey decrypt(KeyParameter aesKey) throws KeyCrypterException {
        final KeyCrypter crypter = getKeyCrypter();
        if (crypter == null)
            throw new KeyCrypterException("No key crypter available");
        return decrypt(crypter, aesKey);
    }

    /**
     * Creates decrypted private key if needed.
     */
    public BLSKey maybeDecrypt(@Nullable KeyParameter aesKey) throws KeyCrypterException {
        return isEncrypted() && aesKey != null ? decrypt(aesKey) : this;
    }

    /**
     * <p>Check that it is possible to decrypt the key with the keyCrypter and that the original key is returned.</p>
     *
     * <p>Because it is a critical failure if the private keys cannot be decrypted successfully (resulting of loss of all
     * bitcoins controlled by the private key) you can use this method to check when you *encrypt* a wallet that
     * it can definitely be decrypted successfully.</p>
     *
     * <p>See {@link Wallet#encrypt(KeyCrypter keyCrypter, KeyParameter aesKey)} for example usage.</p>
     *
     * @return true if the encrypted key can be decrypted back to the original key successfully.
     */
    public static boolean encryptionIsReversible(BLSKey originalKey, BLSKey encryptedKey, KeyCrypter keyCrypter, KeyParameter aesKey) {
        try {
            BLSKey rebornUnencryptedKey = encryptedKey.decrypt(keyCrypter, aesKey);
            byte[] originalPrivateKeyBytes = originalKey.getPrivKeyBytes();
            byte[] rebornKeyBytes = rebornUnencryptedKey.getPrivKeyBytes();
            if (!Arrays.equals(originalPrivateKeyBytes, rebornKeyBytes)) {
                log.error("The check that encryption could be reversed failed for {}", originalKey);
                return false;
            }
            return true;
        } catch (KeyCrypterException kce) {
            log.error(kce.getMessage());
            return false;
        }
    }

    /**
     * Indicates whether the private key is encrypted (true) or not (false).
     * A private key is deemed to be encrypted when there is both a KeyCrypter and the encryptedPrivateKey is non-zero.
     */
    @Override
    public boolean isEncrypted() {
        return keyCrypter != null && encryptedPrivateKey != null && encryptedPrivateKey.encryptedBytes.length > 0;
    }

    @Nullable
    @Override
    public Protos.Wallet.EncryptionType getEncryptionType() {
        return keyCrypter != null ? keyCrypter.getUnderstoodEncryptionType() : Protos.Wallet.EncryptionType.UNENCRYPTED;
    }

    /**
     * A wrapper for {@link #getPrivKeyBytes()} that returns null if the private key bytes are missing or would have
     * to be derived (for the HD key case).
     */
    @Override
    @Nullable
    public byte[] getSecretBytes() {
        if (hasPrivKey())
            return getPrivKeyBytes();
        else
            return null;
    }

    /** An alias for {@link #getEncryptedPrivateKey()} */
    @Nullable
    @Override
    public EncryptedData getEncryptedData() {
        return getEncryptedPrivateKey();
    }

    /**
     * Returns the encrypted private key bytes and initialisation vector for this BLSKey, or null if the BLSKey
     * is not encrypted.
     */
    @Nullable
    public EncryptedData getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }

    /**
     * Returns the KeyCrypter that was used to encrypt to encrypt this BLSKey. You need this to decrypt the BLSKey.
     */
    @Nullable
    public KeyCrypter getKeyCrypter() {
        return keyCrypter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof BLSKey)) return false;
        BLSKey other = (BLSKey) o;
        return Objects.equal(this.priv, other.priv)
                && Objects.equal(this.pub, other.pub)
                && Objects.equal(this.creationTimeSeconds, other.creationTimeSeconds)
                && Objects.equal(this.keyCrypter, other.keyCrypter)
                && Objects.equal(this.encryptedPrivateKey, other.encryptedPrivateKey);
    }

    @Override
    public int hashCode() {
        return pub.hashCode();
    }

    @Override
    public String toString() {
        return toString(false, null, null);
    }

    /**
     * Produce a string rendering of the BLSKey INCLUDING the private key.
     * Unless you absolutely need the private key it is better for security reasons to just use {@link #toString()}.
     */
    public String toStringWithPrivate(@Nullable KeyParameter aesKey, NetworkParameters params) {
        return toString(true, aesKey, params);
    }

    public String getPrivateKeyAsHex() {
        return Utils.HEX.encode(getPrivKeyBytes());
    }

    public String getPublicKeyAsHex() {
        return Utils.HEX.encode(pub.bitcoinSerialize());
    }

    public String getPublicKeyAsHex(boolean legacy) {
        return Utils.HEX.encode(pub.bitcoinSerialize(legacy));
    }

    public String getPrivateKeyAsWiF(NetworkParameters params) {
        return getPrivateKeyEncoded(params).toString();
    }

    private String toString(boolean includePrivate, @Nullable KeyParameter aesKey, @Nullable NetworkParameters params) {
        final MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
        helper.add("pub HEX", getPublicKeyAsHex(pub.isLegacy()));
        if (includePrivate) {
            BLSKey decryptedKey = isEncrypted() ? decrypt(checkNotNull(aesKey)) : this;
            try {
                helper.add("priv HEX", decryptedKey.getPrivateKeyAsHex());
                //helper.add("priv WIF", decryptedKey.getPrivateKeyAsWiF(params));
            } catch (IllegalStateException e) {
                // TODO: Make hasPrivKey() work for deterministic keys and fix this.
            } catch (Exception e) {
                final String message = e.getMessage();
                helper.add("priv EXCEPTION", e.getClass().getName() + (message != null ? ": " + message : ""));
            }
        }
        if (creationTimeSeconds > 0)
            helper.add("creationTimeSeconds", creationTimeSeconds);
        helper.add("keyCrypter", keyCrypter);
        if (includePrivate)
            helper.add("encryptedPrivateKey", encryptedPrivateKey);
        helper.add("isLegacy", pub.isLegacy());
        helper.add("isEncrypted", isEncrypted());
        helper.add("isPubKeyOnly", isPubKeyOnly());
        return helper.toString();
    }

    public void formatKeyWithAddress(boolean includePrivateKeys, @Nullable KeyParameter aesKey, StringBuilder builder,
                                     NetworkParameters params, Script.ScriptType outputScriptType, @Nullable String comment) {
        builder.append("  addr:");
        if (outputScriptType != null) {
            builder.append(Address.fromPubKeyHash(params, getPubKeyHash()));
        } else {
            builder.append(Address.fromPubKeyHash(params, getPubKeyHash()));
        }

        builder.append("  hash160:");
        builder.append(Utils.HEX.encode(getPubKeyHash()));
        if (creationTimeSeconds > 0)
            builder.append("  creationTimeSeconds:").append(creationTimeSeconds).append(" [")
                    .append(Utils.dateTimeFormat(creationTimeSeconds * 1000)).append("]");
        if (comment != null)
            builder.append("  (").append(comment).append(")");
        builder.append("\n");
        if (includePrivateKeys) {
            builder.append("  ");
            builder.append(toStringWithPrivate(aesKey, params));
            builder.append("\n");
        }
    }
}
