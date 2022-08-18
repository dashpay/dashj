package org.bitcoinj.crypto;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.dashj.bls.BLS;
import org.dashj.bls.PublicKey;
import org.dashj.bls.PublicKeyVector;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class BLSPublicKey extends BLSAbstractObject {
    public static int BLS_CURVE_PUBKEY_SIZE  = 48;
    PublicKey publicKeyImpl;

    public BLSPublicKey() {
        super(BLS_CURVE_PUBKEY_SIZE);
    }

    public BLSPublicKey(PublicKey sk) {
        super(BLS_CURVE_PUBKEY_SIZE);
        valid = true;
        publicKeyImpl = sk;
        updateHash();
    }

    public BLSPublicKey(byte [] publicKey) {
        super(BLS_CURVE_PUBKEY_SIZE);
        publicKeyImpl = PublicKey.FromBytes(publicKey);
        valid = true;
        updateHash();
    }

    public BLSPublicKey(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public BLSPublicKey(BLSPublicKey publicKey) {
        super(publicKey.getBuffer(), BLS_CURVE_PUBKEY_SIZE);
    }

    @Override
    boolean internalSetBuffer(byte[] buffer) {
        try {
            publicKeyImpl = publicKeyImpl.FromBytes(buffer);
            return true;
        } catch (Exception x) {
            return false;
        }
    }

    @Override
    boolean internalGetBuffer(byte[] buffer) {
        publicKeyImpl.Serialize(buffer);
        return true;
    }

    @Override
    protected void parse() throws ProtocolException {
        byte[] buffer = readBytes(BLS_CURVE_PUBKEY_SIZE);
        valid = internalSetBuffer(buffer);
        serializedSize = BLS_CURVE_PUBKEY_SIZE;
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        super.bitcoinSerializeToStream(stream);
    }

    public void aggregateInsecure(BLSPublicKey sk) {
        Preconditions.checkState(valid && sk.valid);
        PublicKeyVector publicKeys = new PublicKeyVector();
        publicKeys.push_back(publicKeyImpl);
        publicKeys.push_back(sk.publicKeyImpl);
        publicKeyImpl = publicKeyImpl.AggregateInsecure(publicKeys);
        updateHash();
    }

    public static BLSPublicKey aggregateInsecure(ArrayList<BLSPublicKey> sks) {
        if(sks.isEmpty()) {
            return new BLSPublicKey();
        }

        PublicKeyVector publicKeys = new PublicKeyVector();
        for(BLSPublicKey sk : sks) {
            publicKeys.push_back(sk.publicKeyImpl);
        }

        PublicKey agg = PublicKey.AggregateInsecure(publicKeys);
        BLSPublicKey result = new BLSPublicKey(agg);

        return result;
    }
    /* Dash Core only
    public boolean publicKeyShare(ArrayList<BLSPublicKey> mpk, BLSId id) {
        valid = false;
        updateHash();

        if(!id.valid)
            return false;

        PublicKeyVector mpkVec = new PublicKeyVector();
        for(BLSPublicKey pk : mpk) {
            if(!pk.valid)
                return false;
            mpkVec.push_back(pk.publicKeyImpl);
        }

        try {
            publicKeyImpl = BLS.PublicKeyShare(mpkVec, id.hash.getBytes());
        } catch (Exception x) {
            return false;
        }

        valid = true;
        updateHash();
        return true;
    }

    boolean DHKeyExchange(BLSSecretKey sk, BLSPublicKey pk)
    {
        valid = false;
        updateHash();

        if (!sk.valid || !pk.valid) {
            return false;
        }
        publicKeyImpl = BLS.DHKeyExchange(sk.privateKey, pk.publicKeyImpl);
        valid = true;
        updateHash();
        return true;
    }
    */
}
