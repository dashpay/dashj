package org.bitcoinj.core;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.darkcoinj.DarkSendSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Base64;


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

    public MasternodeBroadcast(NetworkParameters params, byte [] payloadBytes, int cursor)
    {
        super(params, payloadBytes, cursor);
    }

    public MasternodeBroadcast(Masternode masternode)
    {
       super(masternode);
    }


    private transient int optimalEncodingMessageSize;


    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;

        //vin
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (uint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        return cursor - offset;
    }

    @Override
    protected void parse() throws ProtocolException {

        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        address = new MasternodeAddress(params, payload, cursor, 0);
        cursor += address.getMessageSize();

        pubKeyCollateralAddress = new PublicKey(params, payload, cursor);
        cursor += pubKeyCollateralAddress.getMessageSize();

        pubKeyMasternode = new PublicKey(params, payload, cursor);
        cursor += pubKeyMasternode.getMessageSize();

        sig = new MasternodeSignature(params, payload, cursor);
        cursor += sig.getMessageSize();

        sigTime = readInt64();

        protocolVersion = (int)readUint32();

        lastPing = new MasternodePing(params, payload, cursor);
        cursor += lastPing.getMessageSize();

        //nLastDsq = readInt64();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        address.bitcoinSerialize(stream);
        pubKeyCollateralAddress.bitcoinSerialize(stream);
        pubKeyMasternode.bitcoinSerialize(stream);

        sig.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(sigTime, stream);
        Utils.uint32ToByteStreamLE(protocolVersion, stream);

        lastPing.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(nLastDsq, stream);

    }

    public Sha256Hash getHash()
    {
        byte [] dataToHash = new byte[pubKeyCollateralAddress.getBytes().length+8];
        Utils.uint32ToByteArrayLE(sigTime, dataToHash, 0);
        System.arraycopy(pubKeyCollateralAddress.getBytes(), 0, dataToHash, 8, pubKeyCollateralAddress.getBytes().length);
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(8 + vin.getMessageSize() + pubKeyCollateralAddress.calculateMessageSizeInBytes());
            vin.bitcoinSerialize(bos);
            Utils.int64ToByteStreamLE(sigTime, bos);
            pubKeyCollateralAddress.bitcoinSerialize(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice((bos.toByteArray())));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    boolean checkAndUpdate()//int& nDos
    {
        // make sure signature isn't in the future (past is OK)
        if (sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            log.info("CMasternodeBroadcast::CheckAndUpdate - Signature rejected, too far into the future " + vin.toString());
            //nDos = 1;
            return false;
        }

        // incorrect ping or its sigTime
        if(lastPing.equals(MasternodePing.EMPTY) || !lastPing.checkAndUpdate(false, true))
            return false;

        if (protocolVersion < context.masternodePayments.getMinMasternodePaymentsProto()) {
            log.info("CMasternodeBroadcast::CheckAndUpdate - ignoring outdated Masternode " + vin.toString() + " protocol version " + protocolVersion);
            return false;
        }

        Script pubkeyScript;
        //pubkeyScript = GetScriptForDestination(pubKeyCollateralAddress.GetID());
        pubkeyScript = ScriptBuilder.createOutputScript(new Address(params, pubKeyCollateralAddress.getId()));

        if (pubkeyScript.getProgram().length != 25) {
            log.info("CMasternodeBroadcast::CheckAndUpdate - pubKeyCollateralAddress the wrong size");
            //nDos = 100;
            return false;
        }

        Script pubkeyScript2;
        //pubkeyScript2 = GetScriptForDestination(pubKeyMasternode.GetID());
        pubkeyScript2 = ScriptBuilder.createOutputScript(new Address(params, pubKeyMasternode.getId()));

        if (pubkeyScript2.getProgram().length != 25) {
            log.info("CMasternodeBroadcast::CheckAndUpdate - pubKeyMasternode the wrong size\n");
            //nDos = 100;
            return false;
        }

        //if(!vin.scriptSig.empty()) {
        if (!vin.getScriptSig().getChunks().isEmpty()) {
            log.info("CMasternodeBroadcast::CheckAndUpdate - Ignore Not Empty ScriptSig " + vin.toString());
            return false;
        }

        StringBuilder errorMessage = new StringBuilder();

        if (!verifySignature()) {
            //if(!DarkSendSigner.verifyMessage(pubKeyCollateralAddress, sig, strMessage, errorMessage)){
            log.info("CMasternodeBroadcast::CheckAndUpdate - VerifySignature failed: " + vin.toString());
            //nDos = 100;
            return false;
        }

        if (params.getId().equals(NetworkParameters.ID_MAINNET)) {
            if (address.getPort() != 9999) return false;
        } else if (address.getPort() == 9999) return false;

        //search existing Masternode list, this is where we update existing Masternodes with new mnb broadcasts
        Masternode pmn = context.masternodeManager.find(vin);

        // no such masternode or it's not enabled already, nothing to update
        if (pmn == null || (pmn != null && !pmn.isEnabled())) return true;

         // this broadcast is older or equal than the one that we already have - it's bad and should never happen
         // unless someone is doing something fishy
         // (mapSeenMasternodeBroadcast in CMasternodeMan::ProcessMessage should filter legit duplicates)
        if(pmn.sigTime >= sigTime) {
             log.info("CMasternodeBroadcast::CheckAndUpdate - Bad sigTime "+sigTime+" for Masternode "+address+" "+vin+" (existing broadcast is at " + pmn.sigTime);
             return false;
        }

         // masternode is not enabled yet/already, nothing to update
        if(!pmn.isEnabled()) return true;

        // mn.pubKeyCollateralAddress = pubKeyCollateralAddress, IsVinAssociatedWithPubkey is validated once below,
        //   after that they just need to match
        if (pmn.pubKeyCollateralAddress.equals(pubKeyCollateralAddress) && !pmn.isBroadcastedWithin(MASTERNODE_MIN_MNB_SECONDS)) {
            //take the newest entry
            log.info("CMasternodeBroadcast::CheckAndUpdate - Got updated entry for " + address.toString());
            if (pmn.updateFromNewBroadcast(this)) {
                pmn.check();
                // normally masternode should be in pre-enabled status after update, if not - do not relay
                if (pmn.isEnabled()) relay();
            }
            context.masternodeSync.addedMasternodeList(getHash());
        }

        return true;

    }

    boolean checkAndUpdate_old()//int& nDos
    {
        // make sure signature isn't in the future (past is OK)
        if (sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            log.info("CMasternodeBroadcast::CheckAndUpdate - Signature rejected, too far into the future " + vin.toString());
            //nDos = 1;
            return false;
        }

        // incorrect ping or its sigTime
        if(lastPing.equals(MasternodePing.EMPTY) || !lastPing.checkAndUpdate(false, true))
            return false;

        //String vchPubKey = new String(pubKeyCollateralAddress.getBytes(), StandardCharsets.US_ASCII);
        //String vchPubKey2 = new String(pubKeyMasternode.getBytes(), StandardCharsets.US_ASCII);
        //std::string strMessage = addr.ToString() + boost::lexical_cast<std::string>(sigTime) + vchPubKey + vchPubKey2 + boost::lexical_cast<std::string>(protocolVersion);
        //String strMessage = address.toString() + sigTime + vchPubKey + vchPubKey2 + protocolVersion;
        try {
            byte [] message = null;
            String strMessage = "";

            if(protocolVersion < 70201) {
                UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream((address.toString() + sigTime).length() + pubKeyCollateralAddress.getBytes().length + pubKeyMasternode.getBytes().length + ((Integer) protocolVersion).toString().getBytes().length);
                bos.write((address.toString() + sigTime).getBytes());
                bos.write(pubKeyCollateralAddress.getBytes());
                bos.write(pubKeyMasternode.getBytes());
                bos.write(((Integer) protocolVersion).toString().getBytes());

                message = bos.toByteArray();
            }
            else
            {
                /*UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream((address.toString() + sigTime).length() + pubKeyCollateralAddress.getBytes().length + pubKeyMasternode.getBytes().length + ((Integer) protocolVersion).toString().getBytes().length);
                bos.write((address.toString() + sigTime).getBytes());
                bos.write(pubKeyCollateralAddress.getId());
                bos.write(pubKeyMasternode.getId());
                bos.write(((Integer) protocolVersion).toString().getBytes());
*/
                // message = bos.toByteArray();
                strMessage = address.toString() + sigTime + Utils.HEX.encode(Utils.reverseBytes(pubKeyCollateralAddress.getId())) + Utils.HEX.encode(Utils.reverseBytes(pubKeyMasternode.getId())) + protocolVersion;
                message = strMessage.getBytes();
                log.info("CMasternodeBroadcast::VerifySignature - sanitized strMessage: "+Utils.sanitizeString(strMessage)+", pubKeyCollateralAddress address: "+new Address(params, pubKeyCollateralAddress.getId()).toString()+", sig: %s\n" +
                        Base64.toBase64String(sig.getBytes()));
            }

            if (protocolVersion < context.masternodePayments.getMinMasternodePaymentsProto()) {
                log.info("mnb - ignoring outdated Masternode " + vin.toString() + " protocol version " + protocolVersion);
                return false;
            }

            Script pubkeyScript;
            //pubkeyScript = GetScriptForDestination(pubKeyCollateralAddress.GetID());
            pubkeyScript = ScriptBuilder.createOutputScript(new Address(params, pubKeyCollateralAddress.getId()));

            if (pubkeyScript.getProgram().length != 25) {
                log.info("mnb - pubKeyCollateralAddress the wrong size");
                //nDos = 100;
                return false;
            }

            Script pubkeyScript2;
            //pubkeyScript2 = GetScriptForDestination(pubKeyMasternode.GetID());
            pubkeyScript2 = ScriptBuilder.createOutputScript(new Address(params, pubKeyMasternode.getId()));

            if (pubkeyScript2.getProgram().length != 25) {
                log.info("mnb - pubKeyMasternode the wrong size\n");
                //nDos = 100;
                return false;
            }

            //if(!vin.scriptSig.empty()) {
            if (!vin.getScriptSig().getChunks().isEmpty()) {
                log.info("mnb - Ignore Not Empty ScriptSig " + vin.toString());
                return false;
            }

            StringBuilder errorMessage = new StringBuilder();

            if (!DarkSendSigner.verifyMessage1(pubKeyCollateralAddress, sig, message, errorMessage)) {
                //if(!DarkSendSigner.verifyMessage(pubKeyCollateralAddress, sig, strMessage, errorMessage)){
                log.info("mnb - Got bad Masternode address signature: " + errorMessage);
                //nDos = 100;
                return false;
            }

            if (params.getId().equals(NetworkParameters.ID_MAINNET)) {
                if (address.getPort() != 9999) return false;
            } else if (address.getPort() == 9999) return false;

            //search existing Masternode list, this is where we update existing Masternodes with new mnb broadcasts
            Masternode pmn = context.masternodeManager.find(vin);

            // no such masternode or it's not enabled already, nothing to update
            if (pmn == null || (pmn != null && !pmn.isEnabled())) return true;

            // mn.pubKeyCollateralAddress = pubKeyCollateralAddress, IsVinAssociatedWithPubkey is validated once below,
            //   after that they just need to match
            if (pmn.pubKeyCollateralAddress.equals(pubKeyCollateralAddress) && !pmn.isBroadcastedWithin(MASTERNODE_MIN_MNB_SECONDS)) {
                //take the newest entry
                log.info("mnb - Got updated entry for " + address.toString());
                if (pmn.updateFromNewBroadcast(this)) {
                    pmn.check();
                    if (pmn.isEnabled()) relay();
                }
                context.masternodeSync.addedMasternodeList(getHash());
            }

            return true;
        } catch (IOException x){
            return false;
        }
    }

    void relay()
    {
        //for SPV, do not relay
        //CInv inv(MSG_MASTERNODE_ANNOUNCE, GetHash());
        //RelayInv(inv);
    }

    boolean checkInputsAndAdd()
    {
        // we are a masternode with the same vin (i.e. already activated) and this mnb is ours (matches our Masternode privkey)
        // so nothing to do here for us
        if(DarkCoinSystem.fMasterNode && vin.getOutpoint().equals(context.activeMasternode.vin.getOutpoint()) && pubKeyMasternode.equals(context.activeMasternode.pubKeyMasternode))
            return true;

        // incorrect ping or its sigTime
        if(lastPing == MasternodePing.EMPTY || !lastPing.checkAndUpdate(false, true))
            return false;

        // search existing Masternode list
        Masternode pmn = context.masternodeManager.find(vin);

        if(pmn != null) {
            // nothing to do here if we already know about this masternode and it's enabled
            if(pmn.isEnabled() || pmn.isPreEnabled()) return true;
                // if it's not enabled, remove old MN first and continue
            else context.masternodeManager.remove(pmn.vin);
        }

        /*  TODO:  Will this work?

         if(GetInputAge(vin) < Params().GetConsensus().nMasternodeMinimumConfirmations){
            LogPrintf("CMasternodeBroadcast::CheckInputsAndAdd - Input must have at least %d confirmations\n", Params().GetConsensus().nMasternodeMinimumConfirmations);
            // maybe we miss few blocks, let this mnb to be checked again later
            mnodeman.mapSeenMasternodeBroadcast.erase(GetHash());
            masternodeSync.mapSeenSyncMNB.erase(GetHash());
            return false;
        }
        CValidationState state;
        CMutableTransaction tx = CMutableTransaction();
        TransactionOutput vout = new TransactionOutput(context, Coin.valueOf(999,99), context.darkSendPool.collateralPubKey);
        tx.vin.push_back(vin);
        tx.vout.push_back(vout);

        {
            TRY_LOCK(cs_main, lockMain);
            if(!lockMain) {
                // not mnb fault, let it to be checked again later
                context.masternodeManager.mapSeenMasternodeBroadcast.remove(getHash());
                context.masternodeSync.mapSeenSyncMNB.remove(getHash());
                return false;
            }

            if(!AcceptableInputs(mempool, state, CTransaction(tx), false, NULL)) {
                //set nDos
                state.IsInvalid(nDoS);
                return false;
            }
        }
        */

        log.info("masternode - mnb - Accepted Masternode entry\n");

        /* TODO:  Will this work?
        if(GetInputAge(vin) < MASTERNODE_MIN_CONFIRMATIONS){
            log.info("mnb - Input must have at least {1} confirmations", MASTERNODE_MIN_CONFIRMATIONS);
            // maybe we miss few blocks, let this mnb to be checked again later
            mnodeman.mapSeenMasternodeBroadcast.erase(GetHash());
            masternodeSync.mapSeenSyncMNB.erase(GetHash());
            return false;
        }
        */

        // make sure the vout that was signed is related to the transaction that spawned the Masternode
        //  - this is expensive, so it's only done once per Masternode
        if(!DarkSendSigner.isVinAssociatedWithPubkey(params, vin, pubKeyCollateralAddress)) {
            log.info("CMasternodeMan::CheckInputsAndAdd - Got mismatched pubKeyCollateralAddress and vin");
            //nDos = 33;
            return false;
        }

        // verify that sig time is legit in past
        // should be at least not earlier than block when 1000 DASH tx got MASTERNODE_MIN_CONFIRMATIONS
        /*
        uint256 hashBlock = 0;
        CTransaction tx2;
        GetTransaction(vin.prevout.hash, tx2, hashBlock, true);
        BlockMap::iterator mi = mapBlockIndex.find(hashBlock);
        if (mi != mapBlockIndex.end() && (*mi).second)
        {
            CBlockIndex* pMNIndex = (*mi).second; // block for 1000 DASH tx -> 1 confirmation
            CBlockIndex* pConfIndex = chainActive[pMNIndex->nHeight + MASTERNODE_MIN_CONFIRMATIONS - 1]; // block where tx got MASTERNODE_MIN_CONFIRMATIONS
            if(pConfIndex->GetBlockTime() > sigTime)
            {
                LogPrintf("mnb - Bad sigTime %d for Masternode %20s %105s (%i conf block is at %d)\n",
                        sigTime, addr.ToString(), vin.ToString(), MASTERNODE_MIN_CONFIRMATIONS, pConfIndex->GetBlockTime());
                return false;
            }
        }

        */


        // if it matches our Masternode privkey...
        if(DarkCoinSystem.fMasterNode && pubKeyMasternode == context.activeMasternode.pubKeyMasternode) {
            if(protocolVersion == params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT)) {
                // ... and PROTOCOL_VERSION, then we've been remotely activated ...
                context.activeMasternode.enableHotColdMasterNode(vin, address);
            } else {
                // ... otherwise we need to reactivate our node, don not add it to the list and do not relay
                // but also do not ban the node we get this message from
                log.info("CMasternodeBroadcast::CheckInputsAndAdd - wrong PROTOCOL_VERSION, announce message: "+protocolVersion+" MN: "+params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT)+" - re-activate your MN");
                return false;
            }
        }

        log.info("mnb - Got NEW Masternode entry - {} - {} - {} - {} ", getHash().toString(), address.toString(), vin.toString(), sigTime);
        Masternode mn = new Masternode(this);
        context.masternodeManager.add(mn);

        // if it matches our Masternode privkey, then we've been remotely activated
        if(pubKeyMasternode.equals(context.activeMasternode.pubKeyMasternode) && protocolVersion == CoinDefinition.PROTOCOL_VERSION){
            context.activeMasternode.enableHotColdMasterNode(vin, address);
        }

        boolean isLocal = address.getAddr().isSiteLocalAddress() || address.getAddr().isLoopbackAddress();

        if(params.getId().equals(NetworkParameters.ID_REGTEST)) isLocal = false;
        if(!isLocal) relay();

        return true;
    }

    boolean verifySignature()
    {
        String strMessage;
        StringBuilder errorMessage = new StringBuilder();
        //nDos = 0;

        //
        // REMOVE AFTER MIGRATION TO 12.1
        //
        if(protocolVersion < 70201) {
            byte [] message;
            try {
                UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream((address.toString() + sigTime).length() + pubKeyCollateralAddress.getBytes().length + pubKeyMasternode.getBytes().length + ((Integer) protocolVersion).toString().getBytes().length);
                bos.write((address.toString() + sigTime).getBytes());
                bos.write(pubKeyCollateralAddress.getBytes());
                bos.write(pubKeyMasternode.getBytes());
                bos.write(((Integer) protocolVersion).toString().getBytes());
                message = bos.toByteArray();
            }
            catch (Exception x)
            {
                return false;
            }


            /*log.info("masternode", "CMasternodeBroadcast::VerifySignature - sanitized strMessage: "+Utils.sanitizeString(String.valueOf(message))+", pubKeyCollateralAddress address: "+new Address(params, pubKeyCollateralAddress.getId()).toString()+", sig: %s" +
                    Base64.toBase64String(sig.getBytes()));*/
            log.info("CMasternodeBroadcast::VerifySignature - sanitized strMessage: "+Utils.sanitizeString(String.valueOf(message))+", pubKeyCollateralAddress address: "+new Address(params, pubKeyCollateralAddress.getId()).toString()+", sig: %s" +
                    Base64.toBase64String(sig.getBytes()));

            if(!DarkSendSigner.verifyMessage1(pubKeyCollateralAddress, sig, message, errorMessage)){
                /*if (addr.ToString() != addr.ToString(false))
                {
                    // maybe it's wrong format, try again with the old one
                    strMessage = addr.ToString() + boost::lexical_cast<std::string>(sigTime) +
                            vchPubKey + vchPubKey2 + boost::lexical_cast<std::string>(protocolVersion);

                    LogPrint("masternode", "CMasternodeBroadcast::VerifySignature - second try, sanitized strMessage: %s, pubKeyCollateralAddress address: %s, sig: %s\n",
                            SanitizeString(strMessage), CBitcoinAddress(pubKeyCollateralAddress.GetID()).ToString(),
                            EncodeBase64(&vchSig[0], vchSig.size()));

                    if(!darkSendSigner.VerifyMessage(pubKeyCollateralAddress, vchSig, strMessage, errorMessage)){
                        // didn't work either
                        LogPrintf("CMasternodeBroadcast::VerifySignature - Got bad Masternode address signature, second try, sanitized error: %s\n",
                                SanitizeString(errorMessage));
                        // don't ban for old masternodes, their sigs could be broken because of the bug
                        return false;
                    }
                } else {*/
                    // nope, sig is actually wrong
                    log.warn("CMasternodeBroadcast::VerifySignature - Got bad Masternode address signature, sanitized error: %s\n",
                            Utils.sanitizeString(errorMessage.toString()));
                    // don't ban for old masternodes, their sigs could be broken because of the bug
                    return false;
                //}
            }
        } else {
            //
            // END REMOVE
            //


            strMessage = address.toString() + sigTime + Utils.HEX.encode(Utils.reverseBytes(pubKeyCollateralAddress.getId())) + Utils.HEX.encode(Utils.reverseBytes(pubKeyMasternode.getId())) + protocolVersion;

            log.info("CMasternodeBroadcast::VerifySignature - sanitized strMessage: "+Utils.sanitizeString(strMessage)+", pubKeyCollateralAddress address: "+new Address(params, pubKeyCollateralAddress.getId()).toString()+", sig: %s\n" +
                    Base64.toBase64String(sig.getBytes()));

            //LogPrint("masternode", "CMasternodeBroadcast::VerifySignature - strMessage: %s, pubKeyCollateralAddress address: %s, sig: %s\n", strMessage, CBitcoinAddress(pubKeyCollateralAddress.GetID()).ToString(), EncodeBase64(&vchSig[0], vchSig.size()));

            if(!DarkSendSigner.verifyMessage(pubKeyCollateralAddress, sig, strMessage, errorMessage)){
                log.warn("CMasternodeBroadcast::VerifySignature - Got bad Masternode address signature, error: " + errorMessage);
                //nDos = 100;
                return false;
            }
        }

        return true;
    }

}
