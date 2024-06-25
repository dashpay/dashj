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

package org.bitcoinj.crypto.ed25519;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.LinuxSecureRandom;
import org.bitcoinj.crypto.factory.Ed25519KeyFactory;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Wallet;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.math.ec.rfc7748.X25519;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * <p>Represents an elliptic curve public and (optionally) private key, usable for digital signatures but not encryption.
 * Creating a new Ed25519Key with the empty constructor will generate a new random keypair. Other static methods can be used
 * when you already have the public or private parts. If you create a key with only the public part, you can check
 * signatures but not create them.</p>
 *
 * <p>Ed25519Key also provides access to text message signing.
 * This is slightly different to signing raw bytes - if you want to sign your own data and it won't be exposed as
 * text to people, you don't want to use this. If in doubt, ask on the mailing list.</p>
 *
 * <p>This class supports a variety of serialization forms. The methods that accept/return byte arrays serialize
 * private keys as raw byte arrays and public keys using the SEC standard byte encoding for public keys. Signatures
 * are encoded using bytes.</p>
 *
 * <p>A key can be <i>compressed</i> or <i>uncompressed</i>. This refers to whether the public key is represented
 * when encoded into bytes as an (x, y) coordinate on the elliptic curve, or whether it's represented as just an X
 * co-ordinate and an extra byte that carries a sign bit. With the latter form the Y coordinate can be calculated
 * dynamically, however, <b>because the binary serialization is different the address of a key changes if its
 * compression status is changed</b>. If you deviate from the defaults it's important to understand this: money sent
 * to a compressed version of the key will have a different address to the same key in uncompressed form. Whether
 * a public key is compressed or not is recorded in the SEC binary serialisation format, and preserved in a flag in
 * this class so round-tripping preserves state. Unless you're working with old software or doing unusual things, you
 * can usually ignore the compressed/uncompressed distinction.</p>
 */
public class Ed25519Key implements IKey {
    private static final Logger log = LoggerFactory.getLogger(Ed25519Key.class);

    private static final SecureRandom secureRandom;

    static {
        // Init proper random number generator, as some old Android installations have bugs that make it unsecure.
        if (Utils.isAndroidRuntime())
            new LinuxSecureRandom();

        secureRandom = new SecureRandom();
    }

    @Nullable protected final Ed25519PrivateKeyParameters priv;  // A field element.
    protected final Ed25519PublicKeyParameters pub;

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
    public Ed25519Key() {
        this(secureRandom);
    }

    /**
     * Generates an entirely new keypair with the given {@link SecureRandom} object. Point compression is used so the
     * resulting public key will be 33 bytes (32 for the co-ordinate and 1 byte to represent the y bit).
     */
    public Ed25519Key(SecureRandom secureRandom) {
        Ed25519KeyPairGenerator generator = new Ed25519KeyPairGenerator();

        // Initialize the key pair generator with a random seed
        generator.init(new Ed25519KeyGenerationParameters(secureRandom));

        // Generate a new key pair
        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

        // Get the public and private key parameters
        pub = (Ed25519PublicKeyParameters) keyPair.getPublic();
        //pub = getPointWithCompression(pubParams.getQ(), true);
        priv = (Ed25519PrivateKeyParameters) keyPair.getPrivate();

        creationTimeSeconds = Utils.currentTimeSeconds();
    }


    /**
     * Creates an Ed25519Key given the private key only. The public key is calculated from it (this is slow). The resulting
     * public key is compressed.
     */
    public static Ed25519Key fromPrivate(byte[] privKeyBytes) {
        return fromPrivate(privKeyBytes, true);
    }

    /**
     * Creates an Ed25519Key given the private key only. The public key is calculated from it (this is slow).
     * @param compressed Determines whether the resulting Ed25519Key will use a compressed encoding for the public key.
     */
    public static Ed25519Key fromPrivate(byte[] privKeyBytes, boolean compressed) {
        checkArgument(privKeyBytes.length == 32);
        return new Ed25519Key(new Ed25519PrivateKeyParameters(privKeyBytes, 0), (byte[]) null, compressed);
    }

    /**
     * Creates an Ed25519Key that simply trusts the caller to ensure that point is really the result of multiplying the
     * generator point by the private key. This is used to speed things up when you know you have the right values
     * already. The compression state of the point will be preserved.
     */
    public static Ed25519Key fromPrivateAndPrecalculatedPublic(byte[] priv, byte[] pub) {
        checkNotNull(priv);
        checkNotNull(pub);
        return new Ed25519Key(priv, pub);
    }

    /**
     * Creates an Ed25519Key that cannot be used for signing, only verifying signatures, from the given point.
     * @param compressed Determines whether the resulting Ed25519Key will use a compressed encoding for the public key.
     */
    public static Ed25519Key fromPublicOnly(byte[] pub, boolean compressed) {
        return new Ed25519Key(null, pub, compressed);
    }

    /**
     * Creates an Ed25519Key that cannot be used for signing, only verifying signatures, from the given encoded point.
     * The compression state of pub will be preserved.
     */
    public static Ed25519Key fromPublicOnly(byte[] pub) {
        return new Ed25519Key((byte[]) null, pub);
    }

    public static Ed25519Key fromPublicOnly(Ed25519Key key) {
        return fromPublicOnly(key.getPubKey(), key.isCompressed());
    }

    /**
     * Returns a copy of this key, but with the public point represented in uncompressed form. Normally you would
     * never need this: it's for specialised scenarios or when backwards compatibility in encoded form is necessary.
     */
    public Ed25519Key decompress() {
        byte [] pubKeyBytes = getPubKey();
        if (pubKeyBytes.length == 64)
            return this;
        else {
            byte[] uncompressedPublicKey = new byte[64]; // allocate space for the uncompressed public key
            System.arraycopy(pub.getEncoded(), 0, uncompressedPublicKey, 0, 32); // copy x-coordinate from compressed key
            X25519.scalarMultBase(uncompressedPublicKey, 32, uncompressedPublicKey, 0); // compute y-coordinate

            return new Ed25519Key(priv, uncompressedPublicKey);
        }
    }

    /**
     * Creates an Ed25519Key given only the private key bytes. This is the same as using the BigInteger constructor, but
     * is more convenient if you are importing a key from elsewhere. The public key will be automatically derived
     * from the private key.
     */
    public Ed25519Key(@Nullable byte[] privKeyBytes, @Nullable byte[] pubKey) {
        this(privKeyBytes == null ? null : new Ed25519PrivateKeyParameters(privKeyBytes, 0), pubKey);
    }

    /**
     * Create a new Ed25519Key with an encrypted private key, a public key and a KeyCrypter.
     *
     * @param encryptedPrivateKey The private key, encrypted,
     * @param pubKey The keys public key
     * @param keyCrypter The KeyCrypter that will be used, with an AES key, to encrypt and decrypt the private key
     */
    public Ed25519Key(EncryptedData encryptedPrivateKey, byte[] pubKey, KeyCrypter keyCrypter) {
        this((byte[])null, pubKey);

        this.keyCrypter = checkNotNull(keyCrypter);
        this.encryptedPrivateKey = encryptedPrivateKey;
    }

    /**
     * Constructs a key that has an encrypted private component. The given object wraps encrypted bytes and an
     * initialization vector. Note that the key will not be decrypted during this call: the returned Ed25519Key is
     * unusable for signing unless a decryption key is supplied.
     */
    public static Ed25519Key fromEncrypted(EncryptedData encryptedPrivateKey, KeyCrypter crypter, byte[] pubKey) {
        Ed25519Key key = fromPublicOnly(pubKey);
        key.encryptedPrivateKey = checkNotNull(encryptedPrivateKey);
        key.keyCrypter = checkNotNull(crypter);
        return key;
    }

    /**
     * Creates an Ed25519Key given either the private key only, the public key only, or both. If only the private key
     * is supplied, the public key will be calculated from it (this is slow). If both are supplied, it's assumed
     * the public key already correctly matches the private key. If only the public key is supplied, this Ed25519Key cannot
     * be used for signing.
     * @param compressed If set to true and pubKey is null, the derived public key will be in compressed form.
     */

    public Ed25519Key(@Nullable Ed25519PrivateKeyParameters privKey, @Nullable byte[] pubKey, boolean compressed) {
        if (privKey == null && pubKey == null)
            throw new IllegalArgumentException("Ed25519Key requires at least private or public key");
        this.priv = privKey;
        if (pubKey == null) {
            // Derive public from private.
            this.pub = privKey.generatePublicKey();
        } else {
            // We expect the pubkey to be in regular encoded form, just as a BigInteger. Therefore the first byte is
            // a special marker byte.
            // TODO: This is probably not a useful API and may be confusing.
            this.pub = new Ed25519PublicKeyParameters(pubKey, 1);
        }
    }

    public Ed25519Key(@Nullable Ed25519PrivateKeyParameters privKey, @Nullable Ed25519PublicKeyParameters pubKey) {
        if (privKey == null && pubKey == null)
            throw new IllegalArgumentException("Ed25519Key requires at least private or public key");
        this.priv = privKey;
        if (pubKey == null) {
            // Derive public from private.
            this.pub = privKey.generatePublicKey();
        } else {
            // We expect the pubkey to be in regular encoded form, just as a BigInteger. Therefore the first byte is
            // a special marker byte.
            // TODO: This is probably not a useful API and may be confusing.
            this.pub = pubKey;
        }
    }

    public Ed25519Key(@Nullable Ed25519PrivateKeyParameters privKey, @Nullable Ed25519PublicKeyParameters pubKey, boolean compressed) {
        if (privKey == null && pubKey == null)
            throw new IllegalArgumentException("Ed25519Key requires at least private or public key");
        this.priv = privKey;
        if (pubKey == null) {
            // Derive public from private.
            this.pub = privKey.generatePublicKey();
        } else {
            // We expect the pubkey to be in regular encoded form, just as a BigInteger. Therefore the first byte is
            // a special marker byte.
            // TODO: This is probably not a useful API and may be confusing.
            this.pub = pubKey;
        }
    }

    /**
     * Creates an Ed25519Key given either the private key only, the public key only, or both. If only the private key
     * is supplied, the public key will be calculated from it (this is slow). If both are supplied, it's assumed
     * the public key already correctly matches the public key. If only the public key is supplied, this Ed25519Key cannot
     * be used for signing.
     */

    private Ed25519Key(@Nullable Ed25519PrivateKeyParameters privKey, @Nullable byte[] pubKey) {
        this(privKey, pubKey, false);

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
        if (pubKeyHash == null) {
            pubKeyHash = Arrays.copyOf(Sha256Hash.of(this.pub.getEncoded()).getBytes(), 20);
        }
        return pubKeyHash;
    }

    /**
     * Gets the raw public key value. There is always a 00 prefix.
     */
    public byte[] getPubKey() {
        byte[] publicKeyBytes = new byte[33];
        publicKeyBytes[0] = 0x00;
        System.arraycopy(pub.getEncoded(), 0, publicKeyBytes, 1, 32);
        return publicKeyBytes;
    }

    /**
     * Gets the private key in the form of an integer field element. The public key is derived by performing EC
     * point addition this number of times (i.e. point multiplying).
     *
     * @throws IllegalStateException if the private key bytes are not available.
     */
    public Ed25519PrivateKeyParameters getPrivKey() {
        if (priv == null)
            throw new MissingPrivateKeyException();
        return priv;
    }

    /**
     * Returns whether this key is using the compressed form or not. Compressed pubkeys are only 33 bytes, not 64.
     */
    public boolean isCompressed() {
        return pub.getEncoded().length == 32;
    }

    /**
     * Signs the given hash.
     * @throws KeyCrypterException if this Ed25519Key doesn't have a private part.
     */
    public EdDSASignature sign(Sha256Hash input) throws KeyCrypterException {
        return sign(input, null);
    }

    /**
     * If this global variable is set to true, sign() creates a dummy signature and verify() always returns true.
     * This is intended to help accelerate unit tests that do a lot of signing/verifying, which in the debugger
     * can be painfully slow.
     */
    @VisibleForTesting
    public static boolean FAKE_SIGNATURES = false;

    /**
     * Signs the given hash
     *
     * @param aesKey The AES key to use for decryption of the private key. If null then no decryption is required.
     * @throws KeyCrypterException if there's something wrong with aesKey.
     * @throws MissingPrivateKeyException if this key cannot sign because it's pubkey only.
     */
    public EdDSASignature sign(Sha256Hash input, @Nullable KeyParameter aesKey) throws KeyCrypterException {
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
        return doSign(input, priv);
    }

    protected EdDSASignature doSign(Sha256Hash input, Ed25519PrivateKeyParameters privateKeyForSigning) {
        if (FAKE_SIGNATURES)
            return EdDSASignature.dummy();
        checkNotNull(privateKeyForSigning);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKeyForSigning);
        signer.update(input.getBytes(), 0, input.getBytes().length);
        byte[] signature = signer.generateSignature();

        return new EdDSASignature(signature);
    }

    /**
     * <p>Verifies the given EdDSA signature against the message bytes using the public key bytes.</p>
     * 
     * <p>When using native EdDSA verification, data must be 32 bytes, and no element may be
     * larger than 520 bytes.</p>
     *
     * @param data      Hash of the data to verify.
     * @param signature encoded signature
     * @param pub       The public key bytes to use.
     */
    public static boolean verify(byte[] data, byte[] signature, byte[] pub) {
        if (FAKE_SIGNATURES)
            return true;
        byte[] messageHash = Sha256Hash.twiceOf(data).getBytes();


        // verify the signature
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(pub, 1));
        verifier.update(messageHash, 0, messageHash.length);
        return verifier.verifySignature(signature);
    }

    /**
     * Verifies the given ASN.1 encoded EdDSA signature against a hash using the public key.
     *
     * @param hash      Hash of the data to verify.
     * @param signature ASN.1 encoded signature.
     * @throws SignatureDecodeException if the signature is unparseable in some way.
     */
    public boolean verify(byte[] hash, byte[] signature) throws SignatureDecodeException {
        return Ed25519Key.verify(hash, signature, getPubKey());
    }

    /**
     * Verifies the given R/S pair (signature) against a hash using the public key.
     */
    public boolean verify(Sha256Hash sigHash, EdDSASignature signature) {
        return Ed25519Key.verify(sigHash.getBytes(), signature.getSignature(), getPubKey());
    }

    /**
     * Verifies the given ASN.1 encoded EdDSA signature against a hash using the public key, and throws an exception
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
    public void verifyOrThrow(Sha256Hash sigHash, EdDSASignature signature) throws SignatureException {
        if (!Ed25519Key.verify(sigHash.getBytes(), signature.getSignature(), getPubKey()))
            throw new SignatureException();
    }

    /**
     * Returns true if the given pubkey is canonical, i.e. the correct length taking into account compression.
     */
    public static boolean isPubKeyCanonical(byte[] pubkey) {
        if (pubkey.length < 33)
            return false;
        if (pubkey[0] == 0x04) {
            // Uncompressed pubkey
            if (pubkey.length != 65)
                return false;
        } else if (pubkey[0] == 0x00) {
            // Compressed pubkey
            if (pubkey.length != 33)
                return false;
        } else
            return false;
        return true;
    }

    /**
     * Returns true if the given pubkey is in its compressed form.
     */
    public static boolean isPubKeyCompressed(byte[] encoded) {
        if (encoded.length == 33 && encoded[0] == 0x00)
            return true;
        else if (encoded.length == 64)
            return false;
        else
            throw new IllegalArgumentException(Utils.HEX.encode(encoded));
    }

    /**
     * Signs a text message using the standard Bitcoin messaging signing format and returns the signature as a base64
     * encoded string.
     *
     * @throws IllegalStateException if this Ed25519Key does not have the private part.
     * @throws KeyCrypterException if this Ed25519Key is encrypted and no AESKey is provided or it does not decrypt the Ed25519Key.
     */
    public String signMessage(String message) throws KeyCrypterException {
        return signMessage(message, null);
    }

    /**
     * Signs a text message using the standard Bitcoin messaging signing format and returns the signature as a base64
     * encoded string.
     *
     * @throws IllegalStateException if this Ed25519Key does not have the private part.
     * @throws KeyCrypterException if this Ed25519Key is encrypted and no AESKey is provided or it does not decrypt the Ed25519Key.
     */
    public String signMessage(String message, @Nullable KeyParameter aesKey) throws KeyCrypterException {
        byte[] data = Utils.formatMessageForSigning(message);
        Sha256Hash hash = Sha256Hash.twiceOf(data);
        EdDSASignature sig = sign(hash, aesKey);
        return new String(Base64.encode(sig.getSignature()), StandardCharsets.UTF_8);
    }

    /**
     * Signs a hash using the standard Bitcoin messaging signing format and returns the signature as a byte array.
     *
     * @throws IllegalStateException if this Ed25519Key does not have the private part.
     * @throws KeyCrypterException if this Ed25519Key is encrypted and no AESKey is provided or it does not decrypt the Ed25519Key.
     */
    public byte [] signHash(Sha256Hash hash) throws KeyCrypterException {
        return signHash(hash, null);
    }

    /**
     * Signs a hash using the standard Bitcoin messaging signing format and returns the signature as a byte array.
     *
     * @throws IllegalStateException if this Ed25519Key does not have the private part.
     * @throws KeyCrypterException if this Ed25519Key is encrypted and no AESKey is provided or it does not decrypt the Ed25519Key.
     */
    public byte [] signHash(Sha256Hash hash, @Nullable KeyParameter aesKey) throws KeyCrypterException {
        EdDSASignature sig = sign(hash, aesKey);
        return sig.getSignature();
    }

    /**
     * If the key derived from the
     * signature is not the same as this one, throws a SignatureException.
     */
    public void verifyMessage(String message, String signatureBase64) throws SignatureException {
        if (!verify(Utils.formatMessageForSigning(message), Base64.decode(signatureBase64), getPubKey()))
            throw new SignatureException("Signature did not match for message");
    }
    public void verifyMessage(byte [] message, byte [] signatureEncoded) throws SignatureException {
        if (!verify(Utils.formatMessageForSigning(message), signatureEncoded, getPubKey()))
            throw new SignatureException("Signature did not match for message");
    }

    /**
     * Returns a 32 byte array containing the private key.
     * @throws MissingPrivateKeyException if the private key bytes are missing/encrypted.
     */
    public byte[] getPrivKeyBytes() {
        return priv.getEncoded();
    }

    /**
     * Exports the private key in the form used by Dash Core's "dumpprivkey" and "importprivkey" commands. Use
     * the {@link DumpedPrivateKey#toString()} method to get the string.
     *
     * @param params The network this key is intended for use on.
     * @return Private key bytes as a {@link DumpedPrivateKey}.
     * @throws IllegalStateException if the private key is not available.
     */
    public DumpedPrivateKey getPrivateKeyEncoded(NetworkParameters params) {
        return new DumpedPrivateKey(params, getPrivKeyBytes(), isCompressed(), getKeyFactory());
    }

    @Override
    public byte getPrivateKeyCompressedByte() {
        return 0x00;
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
    public Ed25519Key encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(keyCrypter);
        final byte[] privKeyBytes = getPrivKeyBytes();
        EncryptedData encryptedPrivateKey = keyCrypter.encrypt(privKeyBytes, aesKey);
        Ed25519Key result = Ed25519Key.fromEncrypted(encryptedPrivateKey, keyCrypter, getPubKey());
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
    public Ed25519Key decrypt(KeyCrypter keyCrypter, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(keyCrypter);
        // Check that the keyCrypter matches the one used to encrypt the keys, if set.
        if (this.keyCrypter != null && !this.keyCrypter.equals(keyCrypter))
            throw new KeyCrypterException("The keyCrypter being used to decrypt the key is different to the one that was used to encrypt it");
        checkState(encryptedPrivateKey != null, "This key is not encrypted");
        byte[] unencryptedPrivateKey = keyCrypter.decrypt(encryptedPrivateKey, aesKey);
        if (unencryptedPrivateKey.length != 32)
            throw new KeyCrypterException.InvalidCipherText(
                    "Decrypted key must be 32 bytes long, but is " + unencryptedPrivateKey.length);
        Ed25519Key key = Ed25519Key.fromPrivate(unencryptedPrivateKey, isCompressed());
        if (!Arrays.equals(key.getPubKey(), getPubKey()))
            throw new KeyCrypterException("Provided AES key is wrong");
        key.setCreationTimeSeconds(creationTimeSeconds);
        return key;
    }

    @Override
    public byte[] getSerializedSecretKey() {
        return getSecretBytes();
    }

    @Override
    public byte[] getSerializedPublicKey() {
        return getPubKey();
    }

    @Override
    public Object getPubKeyObject() {
        return pub;
    }

    @Override
    public KeyFactory getKeyFactory() {
        return Ed25519KeyFactory.get();
    }


    /**
     * Create a decrypted private key with AES key. Note that if the AES key is wrong, this
     * has some chance of throwing KeyCrypterException due to the corrupted padding that will result, but it can also
     * just yield a garbage key.
     *
     * @param aesKey The KeyParameter with the AES encryption key (usually constructed with keyCrypter#deriveKey and cached).
     */
    public Ed25519Key decrypt(KeyParameter aesKey) throws KeyCrypterException {
        final KeyCrypter crypter = getKeyCrypter();
        if (crypter == null)
            throw new KeyCrypterException("No key crypter available");
        return decrypt(crypter, aesKey);
    }

    /**
     * Creates decrypted private key if needed.
     */
    public Ed25519Key maybeDecrypt(@Nullable KeyParameter aesKey) throws KeyCrypterException {
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
    public static boolean encryptionIsReversible(Ed25519Key originalKey, Ed25519Key encryptedKey, KeyCrypter keyCrypter, KeyParameter aesKey) {
        try {
            Ed25519Key rebornUnencryptedKey = encryptedKey.decrypt(keyCrypter, aesKey);
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
     * Returns the the encrypted private key bytes and initialisation vector for this Ed25519Key, or null if the Ed25519Key
     * is not encrypted.
     */
    @Nullable
    public EncryptedData getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }

    /**
     * Returns the KeyCrypter that was used to encrypt to encrypt this Ed25519Key. You need this to decrypt the Ed25519Key.
     */
    @Nullable
    public KeyCrypter getKeyCrypter() {
        return keyCrypter;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof Ed25519Key)) return false;
        Ed25519Key other = (Ed25519Key) o;
        boolean privateKeysEqual = false;
        if (priv != null && other.priv != null) {
            if (Arrays.equals(getPrivKeyBytes(), other.getPrivKeyBytes())) {
                privateKeysEqual = true;
            }
        } else if (priv == other.priv) {
            privateKeysEqual = true;
        }
        return privateKeysEqual && Arrays.equals(this.pub.getEncoded(), other.pub.getEncoded())
                && Objects.equals(this.creationTimeSeconds, other.creationTimeSeconds)
                && Objects.equals(this.keyCrypter, other.keyCrypter)
                && Objects.equals(this.encryptedPrivateKey, other.encryptedPrivateKey);
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
     * Produce a string rendering of the Ed25519Key INCLUDING the private key.
     * Unless you absolutely need the private key it is better for security reasons to just use {@link #toString()}.
     */
    public String toStringWithPrivate(@Nullable KeyParameter aesKey, NetworkParameters params) {
        return toString(true, aesKey, params);
    }

    public String getPrivateKeyAsHex() {
        return Utils.HEX.encode(getPrivKeyBytes());
    }

    public String getPublicKeyAsHex() {
        return Utils.HEX.encode(getPubKey());
    }

    public String getPrivateKeyAsWiF(NetworkParameters params) {
        return getPrivateKeyEncoded(params).toString();
    }

    private String toString(boolean includePrivate, @Nullable KeyParameter aesKey, @Nullable NetworkParameters params) {
        final MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
        helper.add("pub HEX", getPublicKeyAsHex());
        if (includePrivate) {
            Ed25519Key decryptedKey = isEncrypted() ? decrypt(checkNotNull(aesKey)) : this;
            try {
                helper.add("priv HEX", decryptedKey.getPrivateKeyAsHex());
                helper.add("priv WIF", decryptedKey.getPrivateKeyAsWiF(params));
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
        helper.add("isEncrypted", isEncrypted());
        helper.add("isPubKeyOnly", isPubKeyOnly());
        return helper.toString();
    }

    public void formatKeyWithAddress(boolean includePrivateKeys, @Nullable KeyParameter aesKey, StringBuilder builder,
            NetworkParameters params, Script.ScriptType outputScriptType, @Nullable String comment) {
        builder.append("  addr:");
        if (outputScriptType != null) {
            builder.append(Address.fromKey(params, this, outputScriptType));
        } else {
            builder.append(Address.fromKey(params, this));
        }
        if (!isCompressed())
            builder.append("  UNCOMPRESSED");
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
