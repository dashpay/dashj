package org.bitcoinj.crypto;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.dashj.bls.PrivateKey;
import org.dashj.bls.PrivateKeyVector;

import java.util.ArrayList;

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

    public BLSSecretKey(byte [] buffer) {
        super(buffer, BLS_CURVE_SECKEY_SIZE);
    }

    public BLSSecretKey(BLSSecretKey secretKey) {
        super(secretKey.getBuffer(), BLS_CURVE_SECKEY_SIZE);
    }

    public static BLSSecretKey fromSeed(byte [] seed) {
        return new BLSSecretKey(PrivateKey.FromSeed(seed, seed.length));
    }

    @Override
    boolean internalSetBuffer(byte[] buffer) {
        try {
            privateKey = PrivateKey.FromBytes(buffer);
            return true;
        } catch (Exception x) {
            return false;
        }
    }

    @Override
    boolean internalGetBuffer(byte[] buffer) {
        privateKey.Serialize(buffer);
        return true;
    }

    @Override
    protected void parse() throws ProtocolException {
        byte[] buffer = readBytes(BLS_CURVE_SECKEY_SIZE);
        valid = internalSetBuffer(buffer);
        serializedSize = BLS_CURVE_SECKEY_SIZE;
        length = cursor - offset;
    }

    public void AggregateInsecure(BLSSecretKey sk) {
        Preconditions.checkState(valid && sk.valid);
        PrivateKeyVector privateKeys = new PrivateKeyVector();
        privateKeys.push_back(privateKey);
        privateKeys.push_back(sk.privateKey);
        privateKey = PrivateKey.AggregateInsecure(privateKeys);
        updateHash();
    }

    public static BLSSecretKey AggregateInsecure(ArrayList<BLSSecretKey> sks) {
        if(sks.isEmpty()) {
            return new BLSSecretKey();
        }

        PrivateKeyVector privateKeys = new PrivateKeyVector();
        for(BLSSecretKey sk : sks) {
            privateKeys.push_back(sk.privateKey);
        }

        PrivateKey agg = PrivateKey.AggregateInsecure(privateKeys);
        BLSSecretKey result = new BLSSecretKey(agg);

        return result;
    }

    public void makeNewKey()
    {
        byte [] buf = new byte[32];
        while (true) {
            new LinuxSecureRandom().engineNextBytes(buf);
            try {
                privateKey = PrivateKey.FromBytes(buf);
                break;
            } catch (Exception x) {

            }
        }
        valid = true;
        updateHash();
    }

    /* Dash Core Only
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
            mskVec.push_back(sk.privateKey);
        }

        try {
            privateKey = BLS.PrivateKeyShare(mskVec, id.hash.getBytes());
        } catch (Exception x) {
            return false;
        }

        valid = true;
        updateHash();
        return true;
    }*/

    public BLSPublicKey GetPublicKey() {
        return new BLSPublicKey(privateKey.GetPublicKey());
    }

    public BLSSignature Sign(Sha256Hash hash) {
        if(!valid) {
            return null;
        }

        BLSSignature signature = new BLSSignature(privateKey.SignInsecurePrehashed(hash.getBytes()));
        return signature;
    }
}
