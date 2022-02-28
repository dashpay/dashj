package org.bitcoinj.core;

import com.google.common.base.Charsets;
import org.bitcoinj.crypto.KeyCrypterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by HashEngineering on 4/18/2018.
 */
public class MessageSigner {
    private static final Logger log = LoggerFactory.getLogger(MessageSigner.class);

    public static ECKey getKeysFromSecret(String secret, StringBuilder errorMessage)
    {
        try {
            return DumpedPrivateKey.fromBase58(Context.get().getParams(), secret).getKey();
        }
        catch (AddressFormatException x)
        {
            errorMessage.append("Decoding secret failed: " + x.getMessage());
            return null;
        }
    }

    public static MasternodeSignature signMessage(String message, ECKey key) throws KeyCrypterException {
        byte[] dataToHash = Utils.formatMessageForSigning(message);
        return HashSigner.signHash(Sha256Hash.twiceOf(dataToHash), key);
    }

    public static boolean verifyMessage(PublicKey pubkey, MasternodeSignature vchSig, String message,
                                        StringBuilder errorMessage) {
        return MessageSigner.verifyMessage(pubkey.getId(), vchSig, message, errorMessage);
    }

    public static boolean verifyMessage(KeyId pubkeyId, MasternodeSignature vchSig, String message,
                                        StringBuilder errorMessage) {
        return verifyMessage(pubkeyId.getBytes(), vchSig, message, errorMessage);
    }

    public static boolean verifyMessage(byte [] pubkeyId, MasternodeSignature vchSig, String message,
                                        StringBuilder errorMessage) {
        byte [] dataToHash = Utils.formatMessageForSigning(message);
        return HashSigner.verifyHash(Sha256Hash.twiceOf(dataToHash), pubkeyId, vchSig, errorMessage);
    }
}
