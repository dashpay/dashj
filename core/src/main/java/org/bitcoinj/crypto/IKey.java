/*
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

import com.google.common.primitives.UnsignedBytes;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.script.Script;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.Comparator;

public interface IKey extends EncryptableItem {

    Comparator<IKey> AGE_COMPARATOR = new Comparator<IKey>() {
        @Override
        public int compare(IKey k1, IKey k2) {
            if (k1.getCreationTimeSeconds() == k2.getCreationTimeSeconds())
                return 0;
            else
                return k1.getCreationTimeSeconds() > k2.getCreationTimeSeconds() ? 1 : -1;
        }
    };

    /** Compares pub key bytes using {@link com.google.common.primitives.UnsignedBytes#lexicographicalComparator()} */
    Comparator<IKey> PUBKEY_COMPARATOR = new Comparator<IKey>() {
        private final Comparator<byte[]> comparator = UnsignedBytes.lexicographicalComparator();

        @Override
        public int compare(IKey k1, IKey k2) {
            return comparator.compare(k1.getPubKey(), k2.getPubKey());
        }
    };

    class MissingPrivateKeyException extends RuntimeException {
    }

    class KeyIsEncryptedException extends ECKey.MissingPrivateKeyException {
    }

    static boolean encryptionIsReversible(IKey originalKey, IKey encryptedKey, KeyCrypter keyCrypter, KeyParameter aesKey) {
        try {
            IKey rebornUnencryptedKey = encryptedKey.decrypt(keyCrypter, aesKey);
            byte[] originalPrivateKeyBytes = originalKey.getPrivKeyBytes();
            byte[] rebornKeyBytes = rebornUnencryptedKey.getPrivKeyBytes();
            if (!Arrays.equals(originalPrivateKeyBytes, rebornKeyBytes)) {
                return false;
            }
            return true;
        } catch (KeyCrypterException kce) {
            return false;
        }
    }

    byte[] getPrivKeyBytes();


    byte[] getPubKey();
    byte[] getPubKeyHash();

    /**
     * A deterministic key is considered to be 'public key only' if it hasn't got a private key part and it cannot be
     * rederived. If the hierarchy is encrypted this returns true.
     */
    boolean isPubKeyOnly();

    boolean hasPrivKey();

    /**
     * Returns this keys {@link KeyCrypter} <b>or</b> the keycrypter of its parent key.
     */
    @Nullable
    KeyCrypter getKeyCrypter();

    void setCreationTimeSeconds(long creationTimeSeconds);

    boolean isWatching();

    IKey encrypt(KeyCrypter keyCrypter, KeyParameter aesKey);
    void formatKeyWithAddress(boolean includePrivateKeys, @Nullable KeyParameter aesKey, StringBuilder builder,
                              NetworkParameters params, Script.ScriptType outputScriptType, @Nullable String comment);

    IKey decrypt(KeyParameter aesKey);

    IKey decrypt(KeyCrypter keyCrypter, KeyParameter aesKey);

    Object getPubKeyObject();

    KeyFactory getKeyFactory();

    DumpedPrivateKey getPrivateKeyEncoded(NetworkParameters params);

    String getPrivateKeyAsWiF(NetworkParameters params);

    IKey decompress();

    boolean isCompressed();

    byte[] signHash(Sha256Hash messageHash) throws KeyCrypterException;
    byte[] signHash(Sha256Hash messageHash, KeyParameter aesKey) throws KeyCrypterException;

    String signMessage(String message);
    void verifyMessage(String message, String signatureBase64) throws SignatureException;
}
