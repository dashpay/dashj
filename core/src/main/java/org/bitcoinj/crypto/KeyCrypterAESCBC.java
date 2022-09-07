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
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.Protos;
import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import java.security.SecureRandom;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * <p>This class encrypts and decrypts byte arrays and strings using AES for the encryption.</p>
 *
 * <p>You can use this class to:</p>
 *
 * <p>1) Provide an ECKey or BLSSecretKey as the AES Key.
 * </p>
 *
 * <p>2) Using the AES Key above, you then can encrypt and decrypt any bytes using
 * the AES symmetric cipher.</p>
 */
public class KeyCrypterAESCBC implements KeyCrypter {

    /**
     * Key length in bytes.
     */
    public static final int KEY_LENGTH = 32; // = 256 bits.

    /**
     * The size of an AES block in bytes.
     * This is also the length of the initialisation vector.
     */
    public static final int BLOCK_LENGTH = 16;  // = 128 bits.

    static {
        // Init proper random number generator, as some old Android installations have bugs that make it unsecure.
        if (Utils.isAndroidRuntime())
            new LinuxSecureRandom();

        secureRandom = new SecureRandom();
    }

    protected static final SecureRandom secureRandom;

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
     * Password based encryption using AES - CBC 256 bits.  Allows a particular non-random IV for tests
     */
    EncryptedData encrypt(byte[] plainBytes, byte [] iv, KeyParameter aesKey) throws KeyCrypterException {
        checkNotNull(plainBytes);
        checkNotNull(aesKey);

        try {
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

    @Override
    public Protos.Wallet.EncryptionType getUnderstoodEncryptionType() {
        return Protos.Wallet.EncryptionType.ENCRYPTED_AES;
    }

    @Override
    public KeyParameter deriveKey(CharSequence password) throws KeyCrypterException {
        throw new UnsupportedOperationException("use one other deriveKey methods instead");
    }

    public KeyParameter deriveKey(ECKey secretKey) throws KeyCrypterException {
        Preconditions.checkArgument(secretKey.hasPrivKey(), "secretKey must have private key bytes");
        return new KeyParameter(secretKey.getPrivKeyBytes());
    }

    public KeyParameter deriveKey(BLSSecretKey secretKey) throws KeyCrypterException {
        Preconditions.checkArgument(secretKey.isValid(), "secretKey must be a valid BLS private key");
        return new KeyParameter(secretKey.getBuffer(32));
    }

    public KeyParameter deriveKey(byte [] secretKey) throws KeyCrypterException {
        Preconditions.checkArgument(secretKey.length == 32, "secretKey must be a 32 byte byte array");
        return new KeyParameter(secretKey);
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
}
