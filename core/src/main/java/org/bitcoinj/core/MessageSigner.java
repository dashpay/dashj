package org.bitcoinj.core;

import com.google.common.base.Charsets;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.darkcoinj.DarkSendPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;

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

    public static MasternodeSignature signMessage(String message, ECKey key) throws KeyCrypterException
    {
        byte dataToHash [] = (Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES+message).getBytes(Charsets.UTF_8);
        ECKey.ECDSASignature signature = key.sign(Sha256Hash.twiceOf(dataToHash));
        return new MasternodeSignature(signature.encodeToDER());
    }

    public static boolean verifyMessage(PublicKey pubkey, MasternodeSignature vchSig, String message,
                                        StringBuilder errorMessage) {
        ECKey ecKey = null;
        try {
            ecKey = ECKey.fromPublicOnly(pubkey.getBytes());
            ecKey.verifyMessage(message.getBytes(), vchSig.getBytes());
            return true;
        }
        catch(SignatureException x)
        {
            errorMessage.append("keys don't match - input: "+Utils.HEX.encode(pubkey.getId()));
            errorMessage.append(", recovered: " + (ecKey != null ? Utils.HEX.encode(ecKey.getPubKeyHash()) : "null"));
            errorMessage.append(",\nmessage: "+ String.valueOf(message));
            errorMessage.append(", sig: \n" + Base64.toBase64String(vchSig.getBytes())+ "\n" + x.getMessage());
            return false;
        }
    }

}
