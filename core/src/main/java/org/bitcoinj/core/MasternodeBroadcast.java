package org.bitcoinj.core;

import org.darkcoinj.DarkSend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 2/20/2015.
 */
public class MasternodeBroadcast extends Masternode {
    private static final Logger log = LoggerFactory.getLogger(MasternodeBroadcast.class);

    public MasternodeBroadcast(NetworkParameters params, byte [] payloadBytes)
    {
        super(params, payloadBytes, 0);
    }

    public MasternodeBroadcast(Masternode masternode)
    {
       super(masternode);
    }


    private transient int optimalEncodingMessageSize;
    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
            length = calcLength(payload, offset);
            cursor = offset + length;
        }
    }

    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;

        //vin
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        return cursor - offset;
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;


        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        address = new MasternodeAddress(params, payload, cursor, 0);
        cursor += address.getMessageSize();

        pubkey = new PublicKey(params, payload, cursor);
        cursor += pubkey.getMessageSize();

        pubkey2 = new PublicKey(params, payload, cursor);
        cursor += pubkey2.getMessageSize();

        sig = new MasternodeSignature(params, payload, cursor);
        cursor += sig.getMessageSize();

        sigTime = readInt64();

        protocolVersion = (int)readUint32();

        lastPing = new MasternodePing(params, payload, cursor);
        cursor += lastPing.getMessageSize();

        nLastDsq = readInt64();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        address.bitcoinSerialize(stream);
        pubkey.bitcoinSerialize(stream);
        pubkey2.bitcoinSerialize(stream);

        sig.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(sigTime, stream);
        Utils.uint32ToByteStreamLE(protocolVersion, stream);

        Utils.int64ToByteStreamLE(nLastDsq, stream);

    }

    public Sha256Hash getHash()
    {
        byte [] dataToHash = new byte[pubkey.getBytes().length];
        Utils.uint32ToByteArrayLE(sigTime, dataToHash, 0);
        System.arraycopy(pubkey.getBytes(), 0, dataToHash, 8, pubkey.getBytes().length);

        return Sha256Hash.twiceOf(dataToHash);
    }
/*
    boolean checkAndUpdate()//int& nDos
    {
        // make sure signature isn't in the future (past is OK)
        if (sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            log.info("mnb - Signature rejected, too far into the future "+ vin.toString());
            //nDos = 1;
            return false;
        }

        std::string vchPubKey(pubkey.begin(), pubkey.end());
        std::string vchPubKey2(pubkey2.begin(), pubkey2.end());
        std::string strMessage = addr.ToString() + boost::lexical_cast<std::string>(sigTime) + vchPubKey + vchPubKey2 + boost::lexical_cast<std::string>(protocolVersion);

        if(protocolVersion < masternodePayments.GetMinMasternodePaymentsProto()) {
            LogPrintf("mnb - ignoring outdated Masternode %s protocol version %d\n", vin.ToString(), protocolVersion);
            return false;
        }

        CScript pubkeyScript;
        pubkeyScript = GetScriptForDestination(pubkey.GetID());

        if(pubkeyScript.size() != 25) {
            LogPrintf("mnb - pubkey the wrong size\n");
            nDos = 100;
            return false;
        }

        CScript pubkeyScript2;
        pubkeyScript2 = GetScriptForDestination(pubkey2.GetID());

        if(pubkeyScript2.size() != 25) {
            LogPrintf("mnb - pubkey2 the wrong size\n");
            nDos = 100;
            return false;
        }

        if(!vin.scriptSig.empty()) {
            LogPrintf("mnb - Ignore Not Empty ScriptSig %s\n",vin.ToString());
            return false;
        }

        std::string errorMessage = "";
        if(!darkSendSigner.VerifyMessage(pubkey, sig, strMessage, errorMessage)){
            LogPrintf("mnb - Got bad Masternode address signature\n");
            nDos = 100;
            return false;
        }

        if(params.getId().equals(NetworkParameters.ID_MAINNET)) {
            if(addr.GetPort() != 9999) return false;
        } else if(addr.GetPort() == 9999) return false;

        //search existing Masternode list, this is where we update existing Masternodes with new mnb broadcasts
        CMasternode* pmn = mnodeman.Find(vin);

        // no such masternode or it's not enabled already, nothing to update
        if(pmn == NULL || (pmn != NULL && !pmn->IsEnabled())) return true;

        // mn.pubkey = pubkey, IsVinAssociatedWithPubkey is validated once below,
        //   after that they just need to match
        if(pmn->pubkey == pubkey && !pmn->IsBroadcastedWithin(MASTERNODE_MIN_MNB_SECONDS)) {
            //take the newest entry
            LogPrintf("mnb - Got updated entry for %s\n", addr.ToString());
            if(pmn->UpdateFromNewBroadcast((*this))){
                pmn->Check();
                if(pmn->IsEnabled()) Relay();
            }
            masternodeSync.AddedMasternodeList(GetHash());
        }

        return true;
    }
*/

}
