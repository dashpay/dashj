/*
 * Copyright 2020 Dash Core Group
 *
 * Licensed under the MIT license (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://opensource.org/licenses/mit-license.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.crypto;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.wallet.Protos.Wallet.EncryptionType;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.BigIntegers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>This class encrypts and decrypts byte arrays and strings using a ECDH Key Exchange as the
 * key derivation function and AES for the encryption.  This uses the same method as secp256k1_ecdh</p>
 *
 * <p>You can use this class to:</p>
 *
 * <p>1) Using a ECDSA private key and a public key, create an AES key that can encrypt and decrypt data.
 * </p>
 *
 * <p>2) Using the AES Key generated above, you then can encrypt and decrypt any bytes using
 * the AES symmetric cipher.</p>
 *
 * <p><b>Standard and legacy keys</b></p>
 *
 * <p>The AES key is the 32 byte libsecp256k1 ECDH value {@code SHA256((y & 1 | 0x02) || x)}, used as-is.
 * This is the key that libsecp256k1, iOS and the Rust SDK use, and the only key used for encryption.</p>
 *
 * <p>From #295 (7a9db0281, March 2026) until this fix, dashj turned the hash into a signed BigInteger and back
 * with {@code BigIntegers.asUnsignedByteArray(32, ...)}. For about 1 in 512 key pairs (hash starts with
 * {@code 0xFF} followed by a byte {@code >= 0x80}) that changed the key: {@code ff80..} became {@code 0080..},
 * {@code ffff80..} became {@code 000080..}. dashj could still talk to dashj, but not to other implementations.
 * Data that dashj encrypted for such a pair during that time can only be decrypted with that altered
 * ("legacy") key, so {@link #deriveLegacyKey}, {@link #deriveCandidateKeys} and
 * {@link #decryptWithFallback} exist to read it. Never encrypt with the legacy key.</p>
 */
public class KeyCrypterECDH extends KeyCrypterAESCBC {

    private static final Logger log = LoggerFactory.getLogger(KeyCrypterECDH.class);

    /**
     * Generate AES key.
     *
     *
     * @param password    The password to use in key generation
     * @return            The KeyParameter containing the created AES key
     * @throws KeyCrypterException
     */
    @Override
    public KeyParameter deriveKey(CharSequence password) throws KeyCrypterException {
        throw new UnsupportedOperationException("use deriveKey(ECKey, ECKey) instead");
    }

    /**
     * Derive the standard (libsecp256k1 compatible) AES key for a key pair. Use this key for all encryption.
     */
    public KeyParameter deriveKey(ECKey secretKey, ECKey publicKey) throws KeyCrypterException {
        Preconditions.checkArgument(secretKey.hasPrivKey(), "secretKey must have private key bytes");
        return deriveKey(secretKey.getPrivKeyBytes(), publicKey.getPubKey());
    }

    /** this method by passes some key verification methods in ECKey
     * This to be used in tests only (not public)
     * */
    KeyParameter deriveKey(byte [] secretKey, byte [] publicKey) throws KeyCrypterException {
        return new KeyParameter(calculateSharedSecret(secretKey, publicKey));
    }

    /**
     * Derive the key that dashj used from #295 until this fix. It differs from {@link #deriveKey(ECKey, ECKey)}
     * only for about 1 in 512 key pairs. Use it only to decrypt data written during that time, never to encrypt.
     */
    public KeyParameter deriveLegacyKey(ECKey secretKey, ECKey publicKey) throws KeyCrypterException {
        Preconditions.checkArgument(secretKey.hasPrivKey(), "secretKey must have private key bytes");
        return deriveLegacyKey(secretKey.getPrivKeyBytes(), publicKey.getPubKey());
    }

    KeyParameter deriveLegacyKey(byte [] secretKey, byte [] publicKey) throws KeyCrypterException {
        return new KeyParameter(legacyKeyFromSharedSecret(calculateSharedSecret(secretKey, publicKey)));
    }

    /**
     * The keys to try when decrypting data from this key pair: the standard key first, then the legacy (#295)
     * key only when it is different. Callers that can check the decrypted bytes (for example by parsing them)
     * should try each key in order and keep the first result that checks out, because an AES-CBC decrypt with
     * the wrong key passes the padding check about 1 time in 256.
     */
    public List<KeyParameter> deriveCandidateKeys(ECKey secretKey, ECKey publicKey) throws KeyCrypterException {
        Preconditions.checkArgument(secretKey.hasPrivKey(), "secretKey must have private key bytes");
        return deriveCandidateKeys(secretKey.getPrivKeyBytes(), publicKey.getPubKey());
    }

    List<KeyParameter> deriveCandidateKeys(byte [] secretKey, byte [] publicKey) throws KeyCrypterException {
        byte[] standard = calculateSharedSecret(secretKey, publicKey);
        byte[] legacy = legacyKeyFromSharedSecret(standard);
        if (Arrays.equals(standard, legacy)) {
            return Collections.singletonList(new KeyParameter(standard));
        }
        return Collections.unmodifiableList(Arrays.asList(new KeyParameter(standard), new KeyParameter(legacy)));
    }

    /**
     * Decrypt data from this key pair with the standard key and, if that fails (bad padding), with the legacy
     * (#295) key when it is different.
     *
     * <p>A wrong key gets past the padding check about 1 time in 256 and returns garbage instead of failing, so
     * for data that may have been written with the legacy key, callers that can validate the plaintext should
     * prefer {@link #deriveCandidateKeys} and check each result.</p>
     *
     * @throws KeyCrypterException if no candidate key decrypts the data (the standard key's error is thrown)
     */
    public byte[] decryptWithFallback(EncryptedData dataToDecrypt, ECKey secretKey, ECKey publicKey)
            throws KeyCrypterException {
        Preconditions.checkArgument(secretKey.hasPrivKey(), "secretKey must have private key bytes");
        return decryptWithFallback(dataToDecrypt, secretKey.getPrivKeyBytes(), publicKey.getPubKey());
    }

    byte[] decryptWithFallback(EncryptedData dataToDecrypt, byte [] secretKey, byte [] publicKey)
            throws KeyCrypterException {
        return decryptWithFallback(dataToDecrypt, deriveCandidateKeys(secretKey, publicKey));
    }

    /**
     * Decrypt with each key in order and return the first result that passes the padding check.
     *
     * @throws KeyCrypterException the first key's error if no key works
     */
    public byte[] decryptWithFallback(EncryptedData dataToDecrypt, List<KeyParameter> keys)
            throws KeyCrypterException {
        Preconditions.checkArgument(!keys.isEmpty(), "keys must not be empty");
        KeyCrypterException firstError = null;
        for (int i = 0; i < keys.size(); i++) {
            try {
                byte[] result = decrypt(dataToDecrypt, keys.get(i));
                if (i > 0) {
                    log.info("decrypted ECDH data with the legacy (#295) key");
                }
                return result;
            } catch (KeyCrypterException e) {
                if (firstError == null) {
                    firstError = e;
                }
            }
        }
        throw firstError;
    }

    /**
     * Reproduce the #295 conversion of the shared secret: {@code asUnsignedByteArray(32, new BigInteger(hash))}.
     * Equal to {@code sharedSecret} unless it starts with {@code 0xFF} followed by a byte {@code >= 0x80}.
     */
    static byte[] legacyKeyFromSharedSecret(byte[] sharedSecret) {
        Preconditions.checkArgument(sharedSecret.length == 32, "sharedSecret must be 32 bytes");
        return BigIntegers.asUnsignedByteArray(32, new BigInteger(sharedSecret));
    }

    private static byte[] calculateSharedSecret(byte [] secretKey, byte [] publicKey) throws KeyCrypterException {
        Preconditions.checkNotNull(secretKey, "secretKey must have not be null");
        Preconditions.checkNotNull(publicKey, "publicKey must have not be null");
        try {
            final Stopwatch watch = Stopwatch.createStarted();

            ECPublicKeyParameters pubKey =
                    new ECPublicKeyParameters(ECKey.CURVE_PARAMS.getCurve().decodePoint(publicKey), ECKey.CURVE);
            ECPrivateKeyParameters prvkey =
                    new ECPrivateKeyParameters(new BigInteger(1, secretKey), ECKey.CURVE);

            Secp256k1ECDHAgreement agreement = new Secp256k1ECDHAgreement();
            agreement.init(prvkey);
            byte[] sharedSecret = agreement.calculateSharedSecret(pubKey);
            watch.stop();

            log.info("Deriving key took {} for a ECDH Key Exchange", watch);
            return sharedSecret;
        } catch (Exception e) {
            throw new KeyCrypterException("Could not generate key from EC private and public keys.", e);
        }
    }

    /**
     * Return the EncryptionType enum value which denotes the type of encryption/ decryption that this KeyCrypter
     * can understand.
     */
    @Override
    public EncryptionType getUnderstoodEncryptionType() {
        return EncryptionType.ENCRYPTED_ECDH_KEYEXCHANGE_AES;
    }

    @Override
    public String toString() {
        return "AES-" + KEY_LENGTH * 8 + "-CBC, ECDH ExchangeKey";
    }
}
