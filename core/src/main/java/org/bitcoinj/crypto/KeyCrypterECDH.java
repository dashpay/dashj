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

    public KeyParameter deriveKey(ECKey secretKey, ECKey peerPublicKey) throws KeyCrypterException {
        try {
            Preconditions.checkArgument(secretKey.hasPrivKey(), "secretKey must have private key bytes");
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
        return "AES-" + KEY_LENGTH * 8 + "-CBC, ECDH ExchangeKey)";
    }
}
