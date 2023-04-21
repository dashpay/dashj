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
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyType;
import org.bitcoinj.crypto.bls.BLSDeterministicKey;
import org.bitcoinj.crypto.bls.BLSHDKeyDerivation;
import org.bitcoinj.crypto.bls.BLSKey;

import java.math.BigInteger;

public class BLSKeyFactory implements KeyFactory {

    private static final BLSKeyFactory INSTANCE = new BLSKeyFactory();
    public static BLSKeyFactory get() {
        return INSTANCE;
    }

    @Override
    public IKey newKey() {
        return new ECKey();
    }

    @Override
    public IKey fromEncrypted(EncryptedData e, KeyCrypter keyCrypter, byte[] pub) {
        return new BLSKey(e, pub, keyCrypter);
    }

    @Override
    public IKey fromPrivate(BigInteger bi) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IKey fromPrivateAndPrecalculatedPublic(byte[] priv, byte[] pub) {
        return new BLSKey(priv, pub);
    }

    @Override
    public IKey fromPublicOnly(byte[] pub) {
        return ECKey.fromPublicOnly(pub);
    }

    @Override
    public IKey fromPublicOnly(IKey key) {
        return BLSKey.fromPublicOnly((BLSKey) key);
    }

    @Override
    public IDeterministicKey fromExtended(ImmutableList<ChildNumber> immutablePath, byte[] chainCode, byte[] pubkey, byte[] priv, IDeterministicKey parent) {
        return new BLSDeterministicKey(immutablePath, chainCode, BLSPublicKey.fromSerializedBytes(pubkey), priv != null ? new BLSSecretKey(priv) : null, parent);
    }

    @Override
    public IDeterministicKey fromExtendedEncrypted(ImmutableList<ChildNumber> immutablePath, byte[] chainCode, KeyCrypter keyCrypter, byte[] pubkey, EncryptedData data, IDeterministicKey parent) {
        return new BLSDeterministicKey(immutablePath, chainCode, keyCrypter, BLSPublicKey.fromSerializedBytes(pubkey), data, (BLSDeterministicKey) parent);
    }

    @Override
    public IDeterministicKey fromChildAndParent(IDeterministicKey child, IDeterministicKey parent) {
        return new BLSDeterministicKey((BLSDeterministicKey) child, (BLSDeterministicKey) parent);
    }

    @Override
    public IDeterministicKey deserializeB58(String base58, NetworkParameters params) {
        return null;
    }

    @Override
    public IKey fromPrivate(byte[] privateKeyBytes, boolean pubKeyCompressed) {
        return BLSKey.fromPrivate(privateKeyBytes);
    }

    @Override
    public IDeterministicKey deserializeB58(IDeterministicKey parent, String pub58, NetworkParameters params) {
        return BLSDeterministicKey.deserializeB58((BLSDeterministicKey) parent, pub58, params);
    }
    @Override
    public IDeterministicKey deserializeB58(String base58, ImmutableList<ChildNumber> path, NetworkParameters params) {
        return BLSDeterministicKey.deserializeB58(base58, path, params);
    }

    @Override
    public IDeterministicKey createMasterPrivateKey(byte[] bytes) {
        return BLSHDKeyDerivation.createMasterPrivateKey(bytes);
    }

    @Override
    public KeyType getKeyType() {
        return KeyType.BLS;
    }

    @Override
    public byte getDumpedPrivateKeyLastByte() {
        return 0x02;
    }
}
