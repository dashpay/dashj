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
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.dashj.bls.DASHJBLS;
import org.dashj.bls.G1Element;
import org.dashj.bls.G1ElementVector;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class wraps a G1Element in the BLS library
 */

public class BLSPublicKey extends BLSAbstractObject {
    public static int BLS_CURVE_PUBKEY_SIZE  = 48;
    G1Element publicKeyImpl;

    public BLSPublicKey() {
        super(BLS_CURVE_PUBKEY_SIZE);
    }
    public BLSPublicKey(G1Element pk) {
        this(pk, BLSScheme.isLegacyDefault());
    }

    public BLSPublicKey(G1Element pk, boolean legacy) {
        super(BLS_CURVE_PUBKEY_SIZE);
        valid = true;
        publicKeyImpl = pk;
        this.legacy = legacy;
        updateHash();
    }

    /**
     *
     * @param publicKey if length == 49, the first byte indicates legacy
     */
    public BLSPublicKey(byte [] publicKey) {
        this(publicKey, BLSScheme.isLegacyDefault());
    }

    public static BLSPublicKey fromSerializedBytes(byte [] publicKey) {
        boolean legacy;
        if (publicKey.length == BLS_CURVE_PUBKEY_SIZE + 1) {
            legacy = publicKey[0] != 0;
            publicKey = Arrays.copyOfRange(publicKey, 1, publicKey.length);
        } else {
            legacy = BLSScheme.isLegacyDefault();
        }
        return new BLSPublicKey(publicKey, legacy);
    }

    public BLSPublicKey(byte [] publicKey, boolean legacy) {
        super(BLS_CURVE_PUBKEY_SIZE);
        Preconditions.checkArgument(publicKey.length == BLS_CURVE_PUBKEY_SIZE);
        publicKeyImpl = G1Element.fromBytes(publicKey, legacy);
        valid = true;
        this.legacy = legacy;
        updateHash();
    }

    public BLSPublicKey(String publicKeyHex, boolean legacy) {
        this(Utils.HEX.decode(publicKeyHex), legacy);
    }

    public BLSPublicKey(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset, BLSScheme.isLegacyDefault());
    }

    public BLSPublicKey(NetworkParameters params, byte [] payload, int offset, boolean legacy) {
        super(params, payload, offset, legacy);
    }

    public BLSPublicKey(BLSPublicKey publicKey) {
        super(publicKey.getBuffer(), BLS_CURVE_PUBKEY_SIZE, publicKey.legacy);
    }

    @Override
    boolean internalSetBuffer(byte[] buffer) {
        try {
            publicKeyImpl = G1Element.fromBytes(buffer, legacy);
            return true;
        } catch (Exception x) {
            // try again
            try {
                publicKeyImpl = G1Element.fromBytes(buffer, legacy);
                return true;
            } catch (Exception x2) {
                return false;
            }
        }
    }

    @Override
    boolean internalGetBuffer(byte[] buffer, boolean legacy) {
        byte [] serialized = publicKeyImpl.serialize(legacy);
        System.arraycopy(serialized, 0, buffer, 0, buffer.length);
        return true;
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();
        byte[] buffer = readBytes(BLS_CURVE_PUBKEY_SIZE);
        valid = internalSetBuffer(buffer);
        serializedSize = BLS_CURVE_PUBKEY_SIZE;
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
    }

    public byte[] bitcoinSerialize(boolean legacy) {
        return publicKeyImpl.serialize(legacy);
    }

    public void aggregateInsecure(BLSPublicKey sk) {
        Preconditions.checkState(valid && sk.valid);
        publicKeyImpl = BLSScheme.get(BLSScheme.isLegacyDefault()).aggregate(
                new G1ElementVector(
                        new G1Element[]{publicKeyImpl, sk.publicKeyImpl}
                )
        );
        updateHash();
    }

    public static BLSPublicKey aggregateInsecure(ArrayList<BLSPublicKey> pks, boolean legacy) {
        if(pks.isEmpty()) {
            return new BLSPublicKey();
        }

        G1ElementVector publicKeys = new G1ElementVector();
        for(BLSPublicKey sk : pks) {
            publicKeys.add(sk.publicKeyImpl);
        }

        G1Element agg = BLSScheme.get(legacy).aggregate(publicKeys);

        return new BLSPublicKey(agg);
    }

    public boolean publicKeyShare(ArrayList<BLSPublicKey> mpk, BLSId id) {
        valid = false;
        updateHash();

        if(!id.valid)
            return false;

        G1ElementVector mpkVec = new G1ElementVector();
        for(BLSPublicKey pk : mpk) {
            if(!pk.valid)
                return false;
            mpkVec.add(pk.publicKeyImpl);
        }

        try {
            publicKeyImpl = DASHJBLS.publicKeyShare(mpkVec, id.hash.getBytes());
        } catch (Exception x) {
            return false;
        }

        valid = true;
        updateHash();
        return true;
    }

    public boolean setDHKeyExchange(BLSSecretKey sk, BLSPublicKey pk)
    {
        valid = false;
        hash = Sha256Hash.ZERO_HASH;

        if (!sk.valid || !pk.valid) {
            return false;
        }
        publicKeyImpl = DASHJBLS.multiply(sk.privateKey, pk.publicKeyImpl);
        valid = true;
        updateHash();
        return true;
    }

    public static BLSPublicKey dHKeyExchange(BLSSecretKey sk, BLSPublicKey pk) {
        if (!sk.valid || !pk.valid) {
            return null;
        }
        return new BLSPublicKey(DASHJBLS.multiply(sk.privateKey, pk.publicKeyImpl));
    }

    public long getFingerprint() {
        return publicKeyImpl.getFingerprint(false);
    }

    public long getFingerprint(boolean legacy) {
        return publicKeyImpl.getFingerprint(legacy);
    }

    public boolean isLegacy() {
        return legacy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        BLSPublicKey that = (BLSPublicKey) o;
        if (publicKeyImpl == that.publicKeyImpl) {
            return true;
        }
        return DASHJBLS.objectEquals(publicKeyImpl, that.publicKeyImpl);
    }
}
