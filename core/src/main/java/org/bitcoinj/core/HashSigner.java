package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.encoders.Base64;

import java.security.SignatureException;
import java.util.Arrays;

import static org.bitcoinj.core.Utils.HEX;

/**
 * Created by HashEngineering on 6/25/2018.
 */
public class HashSigner {
    private static final Logger log = LoggerFactory.getLogger(HashSigner.class);

    public static MasternodeSignature signHash(Sha256Hash hash, ECKey key) {
        return new MasternodeSignature(key.signHash(hash));
    }

    /// Verify the hash signature, returns true if successful
    public static boolean verifyHash(Sha256Hash hash, PublicKey pubkey, MasternodeSignature vchSig, StringBuilder strErrorRet) {
        return verifyHash(hash, pubkey.getId(), vchSig, strErrorRet);
    }

    public static boolean verifyHash(Sha256Hash hash, KeyId pubkeyId, MasternodeSignature vchSig, StringBuilder strErrorRet) {
        return verifyHash(hash, pubkeyId.getBytes(), vchSig, strErrorRet);
    }

    public static boolean verifyHash(Sha256Hash hash, byte [] pubkeyId, MasternodeSignature vchSig, StringBuilder strErrorRet) {
        ECKey pubkeyFromSig;

        try {
            pubkeyFromSig = ECKey.signedMessageToKey(hash, vchSig.getBytes());
            if (pubkeyFromSig == null) {
                strErrorRet.append("Error recovering public key.");
                return false;
            }
            if (!Arrays.equals(pubkeyFromSig.getPubKeyHash(), pubkeyId)) {
                strErrorRet.append(String.format("Keys don't match: pubkey=%s, pubkeyFromSig=%s, hash=%s, vchSig=%s",
                        HEX.encode(pubkeyId), HEX.encode(pubkeyFromSig.getPubKeyHash()),
                        hash.toString(), Base64.toBase64String(vchSig.getBytes())));
                return false;
            }

            return true;
        } catch (SignatureException x) {
            strErrorRet.append("exception:  " + x.getMessage());
            return false;
        }
    }


}
