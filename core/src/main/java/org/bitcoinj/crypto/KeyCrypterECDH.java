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

import com.google.common.base.Stopwatch;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.Protos.Wallet.EncryptionType;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.agreement.DHAgreement;
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.dashj.bls.BLS;
import org.dashj.bls.PublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * <p>This class encrypts and decrypts byte arrays and strings using a ECDH Key Exchange as the
 * key derivation function and AES for the encryption.</p>
 *
 * <p>You can use this class to:</p>
 *
 * <p>1) Using a ECDSA private key and a public key, create an AES key that can encrypt and decrypt data.
 * </p>
 *
 * <p>2) Using the AES Key generated above, you then can encrypt and decrypt any bytes using
 * the AES symmetric cipher.</p>
 */
public class KeyCrypterECDH implements KeyCrypter {

    private static final Logger log = LoggerFactory.getLogger(KeyCrypterScrypt.class);

    /**
     * Key length in bytes.
     */
    public static final int KEY_LENGTH = 32; // = 256 bits.

    /**
     * The size of an AES block in bytes.
     * This is also the length of the initialisation vector.
     */
    public static final int BLOCK_LENGTH = 16;  // = 128 bits.

    /**
     * The length of the salt used.
     */
    public static final int SALT_LENGTH = 16;

    static {
        // Init proper random number generator, as some old Android installations have bugs that make it unsecure.
        if (Utils.isAndroidRuntime())
            new LinuxSecureRandom();

        secureRandom = new SecureRandom();
    }

    private static final SecureRandom secureRandom;

    /**
     * Generate AES key.
     *
     * This is a very slow operation compared to encrypt/ decrypt so it is normally worth caching the result.
     *
     * @param password    The password to use in key generation
     * @return            The KeyParameter containing the created AES key
     * @throws KeyCrypterException
     */
    @Override
    public KeyParameter deriveKey(CharSequence password) throws KeyCrypterException {
        throw new UnsupportedOperationException("use deriveKey(BLSSecretKey, BLSPublicKey) instead");
    }

    public KeyParameter deriveKey(ECKey secretKey, ECKey peerPublicKey) throws KeyCrypterException {
        try {
            final Stopwatch watch = Stopwatch.createStarted();

            ECPublicKeyParameters pubKey =
                    new ECPublicKeyParameters(ECKey.CURVE_PARAMS.getCurve().decodePoint(peerPublicKey.getPubKey()), ECKey.CURVE);
            ECPrivateKeyParameters prvkey =
                    new ECPrivateKeyParameters(new BigInteger(1, secretKey.getPrivKeyBytes()), ECKey.CURVE);

            ECDHBasicAgreement agreement = new ECDHBasicAgreement();
            agreement.init(prvkey);
            byte[] password = agreement.calculateAgreement(pubKey).toByteArray();
            watch.stop();

            log.info("Deriving key took {} for a ECDH Key Exchange", watch);
            return new KeyParameter(password);
        } catch (Exception e) {
            throw new KeyCrypterException("Could not generate key from password and salt.", e);
        }
    }
    /**
     * Password based encryption using AES - CBC 256 bits.
     */
    @Override
    public EncryptedData encrypt(byte[] plainBytes, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(plainBytes);
        checkNotNull(aesKey);

        try {
            // Generate iv - each encryption call has a different iv.
            byte[] iv = new byte[BLOCK_LENGTH];
            secureRandom.nextBytes(iv);

            ParametersWithIV keyWithIv = new ParametersWithIV(aesKey, iv);

            // Encrypt using AES.
            BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
            cipher.init(true, keyWithIv);
            byte[] encryptedBytes = new byte[cipher.getOutputSize(plainBytes.length)];
            final int length1 = cipher.processBytes(plainBytes, 0, plainBytes.length, encryptedBytes, 0);
            final int length2 = cipher.doFinal(encryptedBytes, length1);

            return new EncryptedData(iv, Arrays.copyOf(encryptedBytes, length1 + length2));
        } catch (Exception e) {
            throw new KeyCrypterException("Could not encrypt bytes.", e);
        }
    }

    /**
     * Decrypt bytes previously encrypted with this class.
     *
     * @param dataToDecrypt    The data to decrypt
     * @param aesKey           The AES key to use for decryption
     * @return                 The decrypted bytes
     * @throws KeyCrypterException if bytes could not be decrypted
     */
    @Override
    public byte[] decrypt(EncryptedData dataToDecrypt, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(dataToDecrypt);
        checkNotNull(aesKey);

        try {
            ParametersWithIV keyWithIv = new ParametersWithIV(new KeyParameter(aesKey.getKey()), dataToDecrypt.initialisationVector);

            // Decrypt the message.
            BufferedBlockCipher cipher = new PaddedBufferedBlockCipher(new CBCBlockCipher(new AESEngine()));
            cipher.init(false, keyWithIv);

            byte[] cipherBytes = dataToDecrypt.encryptedBytes;
            byte[] decryptedBytes = new byte[cipher.getOutputSize(cipherBytes.length)];
            final int length1 = cipher.processBytes(cipherBytes, 0, cipherBytes.length, decryptedBytes, 0);
            final int length2 = cipher.doFinal(decryptedBytes, length1);

            return Arrays.copyOf(decryptedBytes, length1 + length2);
        } catch (InvalidCipherTextException e) {
            throw new KeyCrypterException.InvalidCipherText("Could not decrypt bytes", e);
        } catch (RuntimeException e) {
            throw new KeyCrypterException("Could not decrypt bytes", e);
        }
    }

    /**
     * Convert a CharSequence (which are UTF16) into a byte array.
     *
     * Note: a String.getBytes() is not used to avoid creating a String of the password in the JVM.
     */
    private static byte[] convertToByteArray(CharSequence charSequence) {
        checkNotNull(charSequence);

        byte[] byteArray = new byte[charSequence.length() << 1];
        for(int i = 0; i < charSequence.length(); i++) {
            int bytePosition = i << 1;
            byteArray[bytePosition] = (byte) ((charSequence.charAt(i)&0xFF00)>>8);
            byteArray[bytePosition + 1] = (byte) (charSequence.charAt(i)&0x00FF);
        }
        return byteArray;
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
        return "AES-" + KEY_LENGTH * 8 + "-CBC, ECDH ExchangeKey)";
    }
}
