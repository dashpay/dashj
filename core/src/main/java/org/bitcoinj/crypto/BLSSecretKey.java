/*
 * Copyright 2019 Dash Core Group.
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

import com.google.common.base.Preconditions;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.dashj.bls.DASHJBLS;
import org.dashj.bls.PrivateKey;
import org.dashj.bls.PrivateKeyVector;

import java.util.ArrayList;

/**
 * This class wraps a PrivateKey in the BLS library
 */

public class BLSSecretKey extends BLSAbstractObject
{
    public static int BLS_CURVE_SECKEY_SIZE = 32;
    PrivateKey privateKey;

    BLSSecretKey() {
        super(BLS_CURVE_SECKEY_SIZE);
    }

    public BLSSecretKey(PrivateKey sk) {
        super(BLS_CURVE_SECKEY_SIZE);
        valid = true;
        privateKey = sk;
        updateHash();
    }

    public BLSSecretKey(byte[] buffer) {
        super(buffer, BLS_CURVE_SECKEY_SIZE, BLSScheme.isLegacyDefault());
    }

    public BLSSecretKey(byte [] buffer, boolean legacy) {
        super(buffer, BLS_CURVE_SECKEY_SIZE, legacy);
    }

    public BLSSecretKey(BLSSecretKey secretKey) {
        super(secretKey.getBuffer(), BLS_CURVE_SECKEY_SIZE, secretKey.legacy);
    }

    public BLSSecretKey(String hex) {
        this(Utils.HEX.decode(hex), BLSScheme.isLegacyDefault());
    }

    public BLSSecretKey(String hex, boolean legacy) {
        this(Utils.HEX.decode(hex), legacy);
    }

    public static BLSSecretKey fromSeed(byte [] seed) {
        return new BLSSecretKey(PrivateKey.fromSeedBIP32(seed));
    }

    @Override
    boolean internalSetBuffer(byte[] buffer) {
        try {
            privateKey = PrivateKey.fromBytes(buffer, legacy);
            return true;
        } catch (Exception x) {
            return false;
        }
    }

    @Override
    boolean internalGetBuffer(byte[] buffer, boolean legacy) {
        byte [] serialized = privateKey.serialize(legacy);
        System.arraycopy(serialized, 0, buffer, 0, buffer.length);
        return true;
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        byte[] buffer = readBytes(BLS_CURVE_SECKEY_SIZE);
        valid = internalSetBuffer(buffer);
        serializedSize = BLS_CURVE_SECKEY_SIZE;
        length = cursor - offset;
    }

    public void aggregateInsecure(BLSSecretKey sk) {
        Preconditions.checkState(valid && sk.valid);
        privateKey = PrivateKey.aggregate(new PrivateKeyVector(new PrivateKey[]{privateKey, sk.privateKey}));
        updateHash();
    }

    public static BLSSecretKey aggregateInsecure(ArrayList<BLSSecretKey> sks) {
        if(sks.isEmpty()) {
            return new BLSSecretKey();
        }

        PrivateKeyVector privateKeys = new PrivateKeyVector();
        privateKeys.reserve(sks.size());
        for(BLSSecretKey sk : sks) {
            privateKeys.add(sk.privateKey);
        }

        PrivateKey agg = PrivateKey.aggregate(privateKeys);
        return new BLSSecretKey(agg);
    }

    static public BLSSecretKey makeNewKey()
    {
        byte [] buf = new byte[32];
        while (true) {
            new LinuxSecureRandom().engineNextBytes(buf);
            try {
                return new BLSSecretKey(PrivateKey.fromBytes(buf));
            } catch (Exception x) {
                // swallow
            }
        }
    }

    boolean secretKeyShare(ArrayList<BLSSecretKey> msk, BLSId id)
    {
        valid = false;
        updateHash();

        if (!id.valid) {
            return false;
        }

        PrivateKeyVector mskVec = new PrivateKeyVector();
        mskVec.reserve(msk.size());
        for (BLSSecretKey sk : msk) {
            if (!sk.valid) {
                return false;
            }
            mskVec.add(sk.privateKey);
        }

        try {
            privateKey = DASHJBLS.privateKeyShare(mskVec, id.hash.getBytes());
        } catch (Exception x) {
            return false;
        }

        valid = true;
        updateHash();
        return true;
    }

    @Deprecated
    public BLSPublicKey GetPublicKey() {
        return getPublicKey();
    }


    public BLSPublicKey getPublicKey() {
        if (!valid) {
            return new BLSPublicKey();
        }

        BLSPublicKey result = new BLSPublicKey(privateKey.getG1Element());
        result.setLegacy(legacy);
        return result;
    }

    @Deprecated
    public BLSSignature Sign(Sha256Hash hash) {
        return sign(hash);
    }

    public BLSSignature sign(Sha256Hash hash) {
        if(!valid) {
            return new BLSSignature();
        }

        return new BLSSignature(BLSScheme.get(BLSScheme.isLegacyDefault()).sign(privateKey, hash.getBytes()));
    }
}
