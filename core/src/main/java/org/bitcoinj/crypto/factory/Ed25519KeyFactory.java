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

package org.bitcoinj.crypto.factory;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.crypto.HDPath;
import org.bitcoinj.crypto.ed25519.Ed25519DeterministicKey;
import org.bitcoinj.crypto.ed25519.Ed25519HDKeyDerivation;
import org.bitcoinj.crypto.ed25519.Ed25519Key;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyType;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.math.BigInteger;
import java.util.List;

public class Ed25519KeyFactory implements KeyFactory {

    private static final Ed25519KeyFactory INSTANCE = new Ed25519KeyFactory();
    public static Ed25519KeyFactory get() {
        return INSTANCE;
    }

    @Override
    public IKey newKey() {
        return new Ed25519Key();
    }

    @Override
    public IKey fromEncrypted(EncryptedData e, KeyCrypter keyCrypter, byte[] pub) {
        return Ed25519Key.fromEncrypted(e, keyCrypter, pub);
    }

    @Override
    public IKey fromPrivate(BigInteger privateKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IKey fromPrivateAndPrecalculatedPublic(byte[] priv, byte[] pub) {
        return Ed25519Key.fromPrivateAndPrecalculatedPublic(priv, pub);
    }

    @Override
    public IKey fromPublicOnly(byte[] pub) {
        return Ed25519Key.fromPublicOnly(pub);
    }

    @Override
    public IKey fromPublicOnly(IKey key) {
        return Ed25519Key.fromPublicOnly(key.getPubKey());
    }

    @Override
    public IDeterministicKey fromExtended(List<ChildNumber> immutablePath, byte[] chainCode, byte[] pubkey, byte[] priv, IDeterministicKey parent) {
        return new Ed25519DeterministicKey(immutablePath, chainCode,
                new Ed25519PublicKeyParameters(pubkey, 1),
                priv != null ? new Ed25519PrivateKeyParameters(priv, 0) : null,
                (Ed25519DeterministicKey) parent);
    }

    @Override
    public IDeterministicKey fromExtendedEncrypted(List<ChildNumber> immutablePath, byte[] chainCode, KeyCrypter keyCrypter, byte[] pubkey, EncryptedData data, IDeterministicKey parent) {
        return new Ed25519DeterministicKey(immutablePath, chainCode, keyCrypter, new Ed25519PublicKeyParameters(pubkey, 1), data, (Ed25519DeterministicKey) parent);
    }

    @Override
    public IDeterministicKey fromChildAndParent(IDeterministicKey child, IDeterministicKey parent) {
        return new Ed25519DeterministicKey((Ed25519DeterministicKey) child, (Ed25519DeterministicKey) parent);
    }

    @Override
    public IDeterministicKey deserializeB58(String base58, NetworkParameters params) {
        return Ed25519DeterministicKey.deserializeB58(base58, params);
    }

    @Override
    public IKey fromPrivate(byte[] privateKeyBytes, boolean pubKeyCompressed) {
        return Ed25519Key.fromPrivate(privateKeyBytes, pubKeyCompressed);
    }

    @Override
    public IDeterministicKey deserializeB58(IDeterministicKey parent, String pub58, NetworkParameters params) {
        return Ed25519DeterministicKey.deserializeB58((Ed25519DeterministicKey) parent, pub58, params);
    }

    @Override
    public IDeterministicKey deserializeB58(String base58, List<ChildNumber> path, NetworkParameters params) {
        return Ed25519DeterministicKey.deserializeB58(base58, path, params);
    }

    @Override
    public IDeterministicKey createMasterPrivateKey(byte[] bytes) {
        return Ed25519HDKeyDerivation.createMasterPrivateKey(bytes);
    }

    @Override
    public KeyType getKeyType() {
        return KeyType.EdDSA;
    }

    @Override
    public byte getDumpedPrivateKeyLastByte() {
        return 0x01;
    }
}
