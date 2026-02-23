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

    public KeyParameter deriveKey(ECKey secretKey, ECKey publicKey) throws KeyCrypterException {
        Preconditions.checkArgument(secretKey.hasPrivKey(), "secretKey must have private key bytes");
        return deriveKey(secretKey.getPrivKeyBytes(), publicKey.getPubKey());
    }

    /** this method by passes some key verification methods in ECKey
     * This to be used in tests only (not public)
     * */
    KeyParameter deriveKey(byte [] secretKey, byte [] publicKey) throws KeyCrypterException {
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
            BigInteger sharedSecret = agreement.calculateAgreement(pubKey);
            byte[] password = BigIntegers.asUnsignedByteArray(32, sharedSecret);
            watch.stop();

            log.info("Deriving key took {} for a ECDH Key Exchange", watch);
            return new KeyParameter(password);
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
