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
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.EncryptedData;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyType;
import org.bitcoinj.crypto.LazyECPoint;

import java.math.BigInteger;

public class ECKeyFactory implements KeyFactory {

    private static final ECKeyFactory INSTANCE = new ECKeyFactory();
    public static ECKeyFactory get() {
        return INSTANCE;
    }

    @Override
    public IKey newKey() {
        return new ECKey();
    }

    @Override
    public IKey fromEncrypted(EncryptedData e, KeyCrypter keyCrypter, byte[] pub) {
        return ECKey.fromEncrypted(e, keyCrypter, pub);
    }

    @Override
    public IKey fromPrivateAndPrecalculatedPublic(byte[] priv, byte[] pub) {
        return ECKey.fromPrivateAndPrecalculatedPublic(priv, pub);
    }

    @Override
    public IKey fromPublicOnly(byte[] pub) {
        return ECKey.fromPublicOnly(pub);
    }

    @Override
    public IDeterministicKey fromExtended(ImmutableList<ChildNumber> immutablePath, byte[] chainCode, byte[] pubkey, byte[] priv, IDeterministicKey parent) {
        LazyECPoint point = new LazyECPoint(ECKey.CURVE.getCurve(), pubkey);
        BigInteger privKey = priv != null ? new BigInteger(1, priv) : null;
        return new DeterministicKey(immutablePath, chainCode, point, privKey, (DeterministicKey) parent);
    }

    @Override
    public IDeterministicKey fromExtendedEncrypted(ImmutableList<ChildNumber> immutablePath, byte[] chainCode, KeyCrypter keyCrypter, byte[] pubkey, EncryptedData data, IDeterministicKey parent) {
        LazyECPoint point = new LazyECPoint(ECKey.CURVE.getCurve(), pubkey);
        return new DeterministicKey(immutablePath, chainCode, keyCrypter, point, data, (DeterministicKey) parent);
    }

    @Override
    public IDeterministicKey fromChildAndParent(IDeterministicKey child, IDeterministicKey parent) {
        return new DeterministicKey((DeterministicKey) child, (DeterministicKey) parent);
    }

    @Override
    public IKey fromPrivate(byte[] privateKeyBytes, boolean pubKeyCompressed) {
        return ECKey.fromPrivate(privateKeyBytes, pubKeyCompressed);
    }

    @Override
    public IDeterministicKey deserializeB58(IDeterministicKey parent, String pub58, NetworkParameters params) {
        return DeterministicKey.deserializeB58((DeterministicKey) parent, pub58, params);
    }

    @Override
    public IDeterministicKey createMasterPrivateKey(byte[] bytes) {
        return HDKeyDerivation.createMasterPrivateKey(bytes);
    }

    @Override
    public KeyType getKeyType() {
        return KeyType.ECDSA;
    }
}
