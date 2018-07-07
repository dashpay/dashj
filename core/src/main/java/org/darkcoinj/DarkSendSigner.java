package org.darkcoinj;

import com.google.common.base.Charsets;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;

import java.security.SignatureException;

/**
 * Created by Eric on 2/8/2015.
 */
@Deprecated
public class DarkSendSigner {
    private static final Logger log = LoggerFactory.getLogger(DarkSendPool.class);
    public static boolean isVinAssociatedWithPubkey(NetworkParameters params, TransactionInput vin, PublicKey pubkey) {
        //TODO:  This function requires the blockchain!  we don't have it
       Script payee2 = ScriptBuilder.createOutputScript(new Address(params, ECKey.fromPublicOnly(pubkey.getBytes()).getPubKeyHash()));
        //payee2.SetDestination(pubkey.GetID());

        Transaction txVin;
        Sha256Hash hash;
        /*
        if (GetTransaction(vin.prevout.hash, txVin, hash, true)) {
            for(TransactionOutput out : txVin.vout) {
                if (out.getValue() == Coin.valueOf(1000)) {
                    if (out.getScriptPubKey() == payee2) return true;
                }
            }
        }*/

        return true;  //we will assume this is true, we cannot check it.
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
    public static MasternodeSignature signMessage(String strMessage, StringBuilder errorMessage, ECKey key)
    {
        //ECKey ecKey = ECKey.fromPublicOnly(key.getBytes());
        try {
            byte dataToHash [] = (Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES+strMessage).getBytes(Charsets.UTF_8);


            ECKey.ECDSASignature signature = key.sign(Sha256Hash.twiceOf(dataToHash));

            return new MasternodeSignature(signature.encodeToDER());
        }
        catch (KeyCrypterException x)
        {

        }
        errorMessage.append("Sign failed");
        return null;
    }
    public static boolean verifyMessage(PublicKey pubkey, MasternodeSignature vchSig, String strMessage, StringBuilder errorMessage)
    {
        //int length = Utils.BITCOIN_SIGNED_MESSAGE_HEADER.length()+strMessage.length();

        //byte dataToHash [] = (Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES+strMessage).getBytes();

        ECKey pubkey2 = null;

        try {
            //pubkey2 = PublicKey.recoverCompact(Sha256Hash.twiceOf(dataToHash), vchSig);

            pubkey2 = ECKey.fromPublicOnly(pubkey.getBytes());

            pubkey2.verifyMessage(strMessage.getBytes(), vchSig.getBytes());

            //ECKey.verify()

            //if(DarkCoinSystem.fDebug && !pubkey.getId().equals(pubkey2.getId()))
            //    log.info("DarkSendSigner.verifyMessage -- keys don't match: " + pubkey2.getId().toString()+ " " + pubkey.getId().toString());

            //return pubkey.getId().equals(pubkey2.getId());
            return true;

        }
        catch(SignatureException x)
        {
            errorMessage.append("keys don't match - input: "+Utils.HEX.encode(pubkey.getId()));
            errorMessage.append(", recovered: " + (pubkey2 != null ? Utils.HEX.encode(pubkey2.getPubKeyHash()) : "null"));
            errorMessage.append(",\nmessage: "+ String.valueOf(strMessage));
            errorMessage.append(", sig: \n" + Base64.toBase64String(vchSig.getBytes())+ "\n" + x.getMessage());

            return false;
        }
    }
    public static boolean verifyMessage1(PublicKey pubkey, MasternodeSignature vchSig, byte[] message, StringBuilder errorMessage)
    {
        //int length = Utils.BITCOIN_SIGNED_MESSAGE_HEADER.length()+strMessage.length();

        byte dataToHash []; // = (Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES+strMessage).getBytes();

        //ByteOutputStream bos = new ByteOutputStream(message.length + Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES.length);
        //bos.write(Utils.BITCOIN_SIGNED_MESSAGE_HEADER_BYTES);
        //bos.write(message);
        dataToHash = Utils.formatMessageForSigning(message);//bos.getBytes();

        //PublicKey pubkey2;
        ECKey pubkey2 = null;
        try {
           // pubkey2 = PublicKey.recoverCompact(Sha256Hash.twiceOf(dataToHash), vchSig);


            //ECKey.verify()

            //if(DarkCoinSystem.fDebug && !pubkey.getId().equals(pubkey2.getId()));
            //    log.info("DarkSendSigner.verifyMessage -- keys don't match: " + pubkey2.getId().toString()+ " " + pubkey.getId().toString());

            //return pubkey.getId().equals(pubkey2.getId());
            //return true;

            pubkey2 = ECKey.fromPublicOnly(pubkey.getBytes());

            pubkey2.verifyMessage(message, vchSig.getBytes());

            return true;

        }
        catch(SignatureException x)
        {
            errorMessage.append("keys don't match - input: "+Utils.HEX.encode(pubkey.getId()));
            errorMessage.append(", recovered: " + (pubkey2 != null ? Utils.HEX.encode(pubkey2.getPubKeyHash()) : "null"));
            errorMessage.append(", message: "+ Utils.sanitizeString(new String(message)));
            errorMessage.append(", sig:  not impl!\n" + x.getMessage());

            return false;
        }
    }

}
