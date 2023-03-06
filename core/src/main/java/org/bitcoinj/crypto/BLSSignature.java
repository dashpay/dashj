package org.bitcoinj.crypto;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.dashj.bls.*;
import org.dashj.bls.Utils.ByteVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * This class wraps a G2Element in the BLS library
 */

public class BLSSignature extends BLSAbstractObject {

    public static int BLS_CURVE_SIG_SIZE   = 96;
    static byte [] emptySignatureBytes = new byte[BLS_CURVE_SIG_SIZE];
    G2Element signatureImpl;

    BLSSignature() {
        super(BLS_CURVE_SIG_SIZE);
    }

    public BLSSignature(byte[] signature) {
        super(signature, BLS_CURVE_SIG_SIZE, BLSScheme.isLegacyDefault());
    }
    public BLSSignature(byte [] signature, boolean legacy) {
        super(signature, BLS_CURVE_SIG_SIZE, legacy);
    }

    public BLSSignature(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset, BLSScheme.isLegacyDefault());
    }
    public BLSSignature(NetworkParameters params, byte [] payload, int offset, boolean legacy) {
        super(params, payload, offset, legacy);
    }

    public BLSSignature(BLSSignature signature) {
        super(signature.getBuffer(), BLS_CURVE_SIG_SIZE, signature.legacy);
    }

    BLSSignature(G2Element sk) {
        super(BLS_CURVE_SIG_SIZE);
        valid = true;
        signatureImpl = sk;
        updateHash();
    }

    public static BLSSignature dummy() {
        return new BLSSignature(emptySignatureBytes);
    }

    @Override
    boolean internalSetBuffer(byte[] buffer) {
        try {
            if(Arrays.equals(buffer, emptySignatureBytes))
                return false;
            signatureImpl = G2Element.fromBytes(buffer, legacy);
            return true;
        } catch (Exception x) {
            //This is added in as a hack, because for some reason when all the unit
            //line above fails with an exception, but we can run it again.
            try {
                signatureImpl = G2Element.fromBytes(buffer, legacy);
                return true;
            } catch (Exception x2) {
                return false;
            }
        }
    }

    @Override
    boolean internalGetBuffer(byte[] buffer, boolean legacy) {
        byte [] serialized = signatureImpl.serialize(legacy);
        System.arraycopy(serialized, 0, buffer, 0, buffer.length);
        return true;
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse(); // set version
        byte[] buffer = readBytes(BLS_CURVE_SIG_SIZE);
        valid = internalSetBuffer(buffer);
        serializedSize = BLS_CURVE_SIG_SIZE;
        length = cursor - offset;
    }

    public void aggregateInsecure(BLSSignature o) {
        checkState(valid && o.valid);
        signatureImpl = BLSScheme.get(BLSScheme.isLegacyDefault()).aggregate(new G2ElementVector(new G2Element[]{signatureImpl, o.signatureImpl}));
        updateHash();
    }

    public static BLSSignature aggregateInsecure(ArrayList<BLSSignature> sigs, boolean legacy) {
        if(sigs.isEmpty()) {
            return new BLSSignature();
        }

        G2ElementVector v_sigs = new G2ElementVector();
        v_sigs.reserve(sigs.size());
        for(BLSSignature sk : sigs) {
            v_sigs.add(sk.signatureImpl);
        }

        G2Element agg = BLSScheme.get(legacy).aggregate(v_sigs);

        return new BLSSignature(agg);
    }

    public static BLSSignature aggregateSecure(List<BLSSignature> sigs,
                                               List<BLSPublicKey> pks,
                                               Sha256Hash hash,
                                               boolean legacy)
    {
        if (sigs.size() != pks.size() || sigs.isEmpty()) {
            return new BLSSignature();
        }

        G1ElementVector vecPublicKeys = new G1ElementVector();
        vecPublicKeys.reserve(sigs.size());
        for (BLSPublicKey pk : pks) {
            vecPublicKeys.add(pk.publicKeyImpl);
        }

        G2ElementVector vecSignatures = new G2ElementVector();
        vecSignatures.reserve(pks.size());
        for (BLSSignature sig : sigs) {
            vecSignatures.add(sig.signatureImpl);
        }

        return new BLSSignature(BLSScheme.get(legacy).aggregateSecure(vecPublicKeys, vecSignatures, hash.getBytes()));
    }

    public void subInsecure(BLSSignature o) {
        Preconditions.checkArgument(valid && o.valid);
        signatureImpl = DASHJBLS.add(signatureImpl, o.signatureImpl.negate());
        updateHash();
    }

    public boolean verifyInsecure(BLSPublicKey pubKey, Sha256Hash hash) {
        if(!valid || !pubKey.valid)
            return false;

        try {
            return BLSScheme.get(BLSScheme.isLegacyDefault()).verify(pubKey.publicKeyImpl, hash.getBytes(), signatureImpl);
        } catch (Exception x) {
            log.error("signature verification error: ", x);
            return false;
        }
    }

    public boolean verifyInsecureAggregated(ArrayList<BLSPublicKey> pubKeys, ArrayList<Sha256Hash> hashes)
    {
        if (!valid) {
            return false;
        }
        checkState(!pubKeys.isEmpty() && !hashes.isEmpty() && pubKeys.size() == hashes.size());

        G1ElementVector pubKeyVec = new G1ElementVector();
        Uint8VectorVector hashes2 = new Uint8VectorVector();
        hashes2.reserve(hashes.size());//will this crash
        pubKeyVec.reserve(pubKeys.size());
        for (int i = 0; i < pubKeys.size(); i++) {
            BLSPublicKey p = pubKeys.get(i);
            if (!p.valid) {
                return false;
            }
            pubKeyVec.add(p.publicKeyImpl);
            hashes2.add(new ByteVector(hashes.get(i).getBytes()));
        }

        try {
            return BLSScheme.get(BLSScheme.isLegacyDefault()).aggregateVerify(pubKeyVec, hashes2, signatureImpl);
        } catch (Exception x) {
            log.error("signature verification error: ", x);
            return false;
        }
    }

    public boolean verifySecureAggregated(ArrayList<BLSPublicKey> pks, Sha256Hash hash)
    {
        if (pks.isEmpty()) {
            return false;
        }

        G1ElementVector vecPublicKeys = new G1ElementVector();
        vecPublicKeys.reserve(pks.size());
        for (BLSPublicKey pk : pks) {
            vecPublicKeys.add(pk.publicKeyImpl);
        }
        return BLSScheme.get(BLSScheme.isLegacyDefault()).verifySecure(vecPublicKeys, signatureImpl, hash.getBytes());
    }

    boolean recover(ArrayList<BLSSignature> sigs, ArrayList<BLSId> ids)
    {
        valid = false;
        updateHash();

        if (sigs.isEmpty() || ids.isEmpty() || sigs.size() != ids.size()) {
            return false;
        }

        G2ElementVector sigsVec = new G2ElementVector();
        Uint8VectorVector idsVec = new Uint8VectorVector();
        sigsVec.reserve(sigs.size());
        idsVec.reserve(sigs.size());

        for (int i = 0; i < sigs.size(); i++) {
            if (!sigs.get(i).valid || !ids.get(i).valid) {
                return false;
            }
            sigsVec.add(sigs.get(i).signatureImpl);
            idsVec.add(new ByteVector(ids.get(i).hash.getBytes()));
        }

        try {
            signatureImpl = DASHJBLS.signatureRecover(sigsVec, idsVec);
        } catch (Exception x) {
            return false;
        }

        valid = true;
        updateHash();
        return true;
    }

    public boolean checkMalleable(byte [] buf, int size)
    {
        byte [] buf2 = getBuffer(serializedSize);
        if (!Arrays.equals(buf, buf2)) {
            // TODO not sure if this is actually possible with the BLS libs. I'm assuming here that somewhere deep inside
            // these libs masking might happen, so that 2 different binary representations could result in the same object
            // representation
            return false;
        }
        return true;
    }

    public boolean isCanonical() {
        return true;
    }
}
