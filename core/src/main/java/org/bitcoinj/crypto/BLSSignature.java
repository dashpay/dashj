package org.bitcoinj.crypto;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.dashj.bls.*;

import java.util.ArrayList;
import java.util.Arrays;

public class BLSSignature extends BLSAbstractObject {

    public static int BLS_CURVE_SIG_SIZE   = 96;
    static byte [] emptySignatureBytes = new byte[BLS_CURVE_SIG_SIZE];
    InsecureSignature signatureImpl;

    BLSSignature() {
        super(BLS_CURVE_SIG_SIZE);
    }

    public BLSSignature(byte [] signature) {
        super(signature, BLS_CURVE_SIG_SIZE);
    }

    public BLSSignature(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public BLSSignature(BLSSignature signature) {
        super(signature.getBuffer(), BLS_CURVE_SIG_SIZE);
    }

    BLSSignature(InsecureSignature sk) {
        super(BLS_CURVE_SIG_SIZE);
        valid = true;
        signatureImpl = sk;
        updateHash();
    }

    @Override
    boolean internalSetBuffer(byte[] buffer) {
        try {
            if(Arrays.equals(buffer, emptySignatureBytes))
                return false;
            signatureImpl = InsecureSignature.FromBytes(buffer);
            return true;
        } catch (Exception x) {
            //This is added in as a hack, because for some reason when all the unit
            //line above fails with an exception, but we can run it again.
            try {
                signatureImpl = InsecureSignature.FromBytes(buffer);
                return true;
            } catch (Exception x2) {
                return false;
            }
        }
    }

    @Override
    boolean internalGetBuffer(byte[] buffer) {
        signatureImpl.Serialize(buffer);
        return true;
    }

    @Override
    protected void parse() throws ProtocolException {
        byte[] buffer = readBytes(BLS_CURVE_SIG_SIZE);
        valid = internalSetBuffer(buffer);
        serializedSize = BLS_CURVE_SIG_SIZE;
        length = cursor - offset;
    }

    public void aggregateInsecure(BLSSignature o) {
        Preconditions.checkState(valid && o.valid);
        InsecureSignatureVector sigs = new InsecureSignatureVector();
        sigs.push_back(signatureImpl);
        sigs.push_back(o.signatureImpl);
        signatureImpl = InsecureSignature.Aggregate(sigs);
        updateHash();
    }

    public static BLSSignature aggregateInsecure(ArrayList<BLSSignature> sks) {
        if(sks.isEmpty()) {
            return new BLSSignature();
        }

        InsecureSignatureVector sigs = new InsecureSignatureVector();
        for(BLSSignature sk : sks) {
            sigs.push_back(sk.signatureImpl);
        }

        InsecureSignature agg = InsecureSignature.Aggregate(sigs);
        BLSSignature result = new BLSSignature(agg);

        return result;
    }

    public void subInsecure(BLSSignature o) {
        Preconditions.checkArgument(valid && o.valid);
        InsecureSignatureVector sigs = new InsecureSignatureVector();
        sigs.push_back(o.signatureImpl);
        signatureImpl = signatureImpl.DivideBy(sigs);
        updateHash();
    }

    public boolean verifyInsecure(BLSPublicKey pubKey, Sha256Hash hash) {
        if(!valid || !pubKey.valid)
            return false;

        try {
            return signatureImpl.Verify(hash.getBytes(), pubKey.publicKeyImpl);
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
        Preconditions.checkState(!pubKeys.isEmpty() && !hashes.isEmpty() && pubKeys.size() == hashes.size());

        PublicKeyVector pubKeyVec = new PublicKeyVector();
        MessageHashVector hashes2 = new MessageHashVector();
        hashes2.reserve(hashes.size());//will this crash
        pubKeyVec.reserve(pubKeys.size());
        for (int i = 0; i < pubKeys.size(); i++) {
            BLSPublicKey p = pubKeys.get(i);
            if (!p.valid) {
                return false;
            }
            pubKeyVec.push_back(p.publicKeyImpl);
            hashes2.push_back(hashes.get(i).getBytes());
        }

        try {
            return signatureImpl.Verify(hashes2, pubKeyVec);
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

        AggregationInfoVector v = new AggregationInfoVector();
        v.reserve(pks.size());
        for (BLSPublicKey pk : pks) {
            AggregationInfo aggInfo = AggregationInfo.FromMsgHash(pk.publicKeyImpl, hash.getBytes());
            v.push_back(aggInfo);
        }

        AggregationInfo aggInfo = AggregationInfo.MergeInfos(v);
        Signature aggSig = Signature.FromInsecureSig(signatureImpl, aggInfo);
        return aggSig.Verify();
    }

    /* Dash Core only
    boolean recover(ArrayList<BLSSignature> sigs, ArrayList<BLSId> ids)
    {
        valid = false;
        updateHash();

        if (sigs.isEmpty() || ids.isEmpty() || sigs.size() != ids.size()) {
            return false;
        }

        InsecureSignatureVector sigsVec = new InsecureSignatureVector();
        MessageHashVector idsVec = new MessageHashVector();
        sigsVec.reserve(sigs.size());
        idsVec.reserve(sigs.size());

        for (int i = 0; i < sigs.size(); i++) {
            if (!sigs.get(i).valid || !ids.get(i).valid) {
                return false;
            }
            sigsVec.push_back(sigs.get(i).signatureImpl);
            idsVec.push_back(ids.get(i).hash.getBytes());
        }

        try {
            signatureImpl = BLS.RecoverSig(sigsVec, idsVec);
        } catch (Exception x) {
            return false;
        }

        valid = true;
        updateHash();
        return true;
    }*/

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
}
