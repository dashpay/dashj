package org.darkcoinj;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.KeyCrypterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;

import java.security.SignatureException;

/**
 * Created by Eric on 2/8/2015.
 */
public class DarkSendSigner {
    private static final Logger log = LoggerFactory.getLogger(DarkSendPool.class);
    boolean isVinAssociatedWithPubkey(TransactionInput vin, byte [] pubkey) {
        //TODO:  This function requires the blockchain!  we don't have it
       /*Script payee2 = ScriptBuilder.createOutputScript(new Address(params, ECKey.fromPublicOnly(pubkey)));
        //payee2.SetDestination(pubkey.GetID());

        Transaction txVin;
        Sha256Hash hash;
        if (GetTransaction(vin.prevout.hash, txVin, hash, true)) {
            for(TransactionOutput out : txVin.vout) {
                if (out.getValue() == Coin.valueOf(1000)) {
                    if (out.getScriptPubKey() == payee2) return true;
                }
            }
        }*/

        return false;
    }
    public static ECKey setKey(String strSecret, StringBuilder errorMessage)
    {
        //CBitcoinSecret vchSecret;
        //boolean fGood = vchSecret.SetString(strSecret);
        byte [] bytes;
        try {
            bytes = Base58.decode(strSecret);
        }
        catch (AddressFormatException x)
        {
            errorMessage.append("Invalid private key");
            return null;
        }
        ECKey key = ECKey.fromPrivate(bytes);
        return key;
    }
    public static byte [] signMessage(String strMessage, StringBuilder errorMessage, ECKey key)
    {
        try {
            String vchSig = key.signMessage(Utils.BITCOIN_SIGNED_MESSAGE_HEADER + strMessage);
            return vchSig.getBytes();
        }
        catch (KeyCrypterException x)
        {

        }



        errorMessage.append("Sign failed");
        return null;
    }
    public static boolean verifyMessage(ECKey pubkey, byte [] vchSig, String strMessage, StringBuilder errorMessage)
    {
        ECKey pubkey2;
        try {
            pubkey2 = ECKey.signedMessageToKey(Utils.BITCOIN_SIGNED_MESSAGE_HEADER+strMessage, Base64.toBase64String(vchSig));
        }
        catch(SignatureException x)
        {
            errorMessage.append("Error recovering pubkey");
            return false;
        }

        /*CPubKey pubkey2;
        if (!pubkey2.RecoverCompact(ss.GetHash(), vchSig)) {
            errorMessage = "Error recovering pubkey";
            return false;
        }*/

        if (pubkey2.getPubKeyHash() != pubkey.getPubKeyHash())
            log.warn("CDarkSendSigner::VerifyMessage -- keys don't match: %s %s", pubkey2.getPubKeyHash().toString(), pubkey.getPubKeyHash().toString());

        return (pubkey2.getPubKeyHash() == pubkey.getPubKeyHash());
    }

}
