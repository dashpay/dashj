package org.bitcoinj.core;

import com.google.common.base.Charsets;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.darkcoinj.DarkSendPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;

import java.io.ByteArrayOutputStream;
import java.security.SignatureException;

/**
 * Created by HashEngineering on 2/8/2015.
 */
public class MessageSigner {
    private static final Logger log = LoggerFactory.getLogger(MessageSigner.class);

    public static ECKey getKeysFromSecret(String strSecret, StringBuilder errorMessage)
    {
        byte [] bytes;
        try {
            bytes = Base58.decode(strSecret);
        }
        catch (AddressFormatException x)
        {
            return null;
        }
        ECKey key = ECKey.fromPrivate(bytes);
        return key;
    }

    public static MasternodeSignature signMessage(String message, ECKey key) throws KeyCrypterException {
        byte dataToHash [] = (Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES+message).getBytes(Charsets.UTF_8);
        return HashSigner.signHash(Sha256Hash.twiceOf(dataToHash), key);
    }

    public static boolean verifyMessage(PublicKey pubkey, MasternodeSignature vchSig, String message,
                                        StringBuilder errorMessage) {
        return MessageSigner.verifyMessage(pubkey.getId(), vchSig, message, errorMessage);
    }

    public static boolean verifyMessage(byte [] pubkeyId, MasternodeSignature vchSig, String message,
                                        StringBuilder errorMessage) {
        byte [] dataToHash = Utils.formatMessageForSigning(message);
        return HashSigner.verifyHash(Sha256Hash.twiceOf(dataToHash), pubkeyId, vchSig, errorMessage);
    }
}
