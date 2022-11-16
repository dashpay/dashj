/*
 * Copyright 2019 Dash Core Group
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
import org.bitcoinj.wallet.Protos.Wallet.EncryptionType;
import org.bouncycastle.crypto.params.KeyParameter;

import org.dashj.bls.G1Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>This class encrypts and decrypts byte arrays and strings using a BLS Key Exchange as the
 * key derivation function and AES for the encryption.</p>
 *
 * <p>You can use this class to:</p>
 *
 * <p>1) Using a BLS private key and a public key, create an AES key that can encrypt and decrypt data.
 * </p>
 *
 * <p>2) Using the AES Key generated above, you then can encrypt and decrypt any bytes using
 * the AES symmetric cipher. Eight bytes of salt is used to prevent dictionary attacks.</p>
 */
public class BLSKeyExchangeCrypter extends KeyCrypterAESCBC {

    private static final Logger log = LoggerFactory.getLogger(BLSKeyExchangeCrypter.class);
    private final boolean legacy;

    public BLSKeyExchangeCrypter() {
        legacy = BLSScheme.isLegacyDefault();
    }

    public BLSKeyExchangeCrypter(boolean legacy) {
        this.legacy = legacy;
    }

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

    public KeyParameter deriveKey(BLSSecretKey secretKey, BLSPublicKey blsPeerPublicKey) throws KeyCrypterException {
        try {
            final Stopwatch watch = Stopwatch.createStarted();
            BLSPublicKey publicKey = new BLSPublicKey();
            publicKey.setDHKeyExchange(secretKey, blsPeerPublicKey);
            G1Element pk = publicKey.publicKeyImpl;
            watch.stop();
            byte [] key = new byte [32];
            System.arraycopy(pk.serialize(legacy), 0, key, 0, 32);
            log.info("Deriving key took {} for a BLS Key Exchange", watch);
            return new KeyParameter(key);
        } catch (Exception e) {
            throw new KeyCrypterException("Could not generate key from BLS private and public keys.", e);
        }
    }

    /**
     * Return the EncryptionType enum value which denotes the type of encryption/ decryption that this KeyCrypter
     * can understand.
     */
    @Override
    public EncryptionType getUnderstoodEncryptionType() {
        return EncryptionType.ENCRYPTED_BLS_KEYEXCHANGE_AES;
    }

    @Override
    public String toString() {
        return "AES-" + KEY_LENGTH * 8 + "-CBC, BLS DHExchangeKey)";
    }
}
