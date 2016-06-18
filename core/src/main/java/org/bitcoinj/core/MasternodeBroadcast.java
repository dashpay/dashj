package org.bitcoinj.core;

import com.squareup.okhttp.internal.Network;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.darkcoinj.DarkSend;
import org.darkcoinj.DarkSendSigner;
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

    public MasternodeBroadcast(NetworkParameters params, byte [] payloadBytes, int cursor)
    {
        super(params, payloadBytes, cursor);
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
        // 4 = length of sequence field (uint32)
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

        lastPing.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(nLastDsq, stream);

    }

    public Sha256Hash getHash()
    {
        byte [] dataToHash = new byte[pubkey.getBytes().length+8];
        Utils.uint32ToByteArrayLE(sigTime, dataToHash, 0);
        System.arraycopy(pubkey.getBytes(), 0, dataToHash, 8, pubkey.getBytes().length);
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(8 + pubkey.calculateMessageSizeInBytes());
            Utils.int64ToByteStreamLE(sigTime, bos);
            pubkey.bitcoinSerialize(bos);
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
            log.info("mnb - Signature rejected, too far into the future " + vin.toString());
            //nDos = 1;
            return false;
        }

        //String vchPubKey = new String(pubkey.getBytes(), StandardCharsets.US_ASCII);
        //String vchPubKey2 = new String(pubkey2.getBytes(), StandardCharsets.US_ASCII);
        //std::string strMessage = addr.ToString() + boost::lexical_cast<std::string>(sigTime) + vchPubKey + vchPubKey2 + boost::lexical_cast<std::string>(protocolVersion);
        //String strMessage = address.toString() + sigTime + vchPubKey + vchPubKey2 + protocolVersion;
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream((address.toString() + sigTime).length() + pubkey.getBytes().length + pubkey2.getBytes().length + ((Integer) protocolVersion).toString().getBytes().length);
            bos.write((address.toString() + sigTime).getBytes());
            bos.write(pubkey.getBytes());
            bos.write(pubkey2.getBytes());
            bos.write(((Integer) protocolVersion).toString().getBytes());

            byte[] message = bos.toByteArray();

            if (protocolVersion < context.masternodePayments.getMinMasternodePaymentsProto()) {
                log.info("mnb - ignoring outdated Masternode " + vin.toString() + " protocol version " + protocolVersion);
                return false;
            }

            Script pubkeyScript;
            //pubkeyScript = GetScriptForDestination(pubkey.GetID());
            pubkeyScript = ScriptBuilder.createOutputScript(new Address(params, pubkey.getId()));

            if (pubkeyScript.getProgram().length != 25) {
                log.info("mnb - pubkey the wrong size");
                //nDos = 100;
                return false;
            }

            Script pubkeyScript2;
            //pubkeyScript2 = GetScriptForDestination(pubkey2.GetID());
            pubkeyScript2 = ScriptBuilder.createOutputScript(new Address(params, pubkey2.getId()));

            if (pubkeyScript2.getProgram().length != 25) {
                log.info("mnb - pubkey2 the wrong size\n");
                //nDos = 100;
                return false;
            }

            //if(!vin.scriptSig.empty()) {
            if (!vin.getScriptSig().getChunks().isEmpty()) {
                log.info("mnb - Ignore Not Empty ScriptSig " + vin.toString());
                return false;
            }

            StringBuilder errorMessage = new StringBuilder();
            if (!DarkSendSigner.verifyMessage1(pubkey, sig, message, errorMessage)) {
                //if(!DarkSendSigner.verifyMessage(pubkey, sig, strMessage, errorMessage)){
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

            // mn.pubkey = pubkey, IsVinAssociatedWithPubkey is validated once below,
            //   after that they just need to match
            if (pmn.pubkey.equals(pubkey) && !pmn.isBroadcastedWithin(MASTERNODE_MIN_MNB_SECONDS)) {
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
        if(DarkCoinSystem.fMasterNode && vin.getOutpoint().equals(context.activeMasternode.vin.getOutpoint()) && pubkey2.equals(context.activeMasternode.pubKeyMasternode))
            return true;

        // search existing Masternode list
        Masternode pmn = context.masternodeManager.find(vin);

        if(pmn != null) {
            // nothing to do here if we already know about this masternode and it's enabled
            if(pmn.isEnabled()) return true;
                // if it's not enabled, remove old MN first and continue
            else context.masternodeManager.remove(pmn.vin);
        }

        /*  TODO:  Will this work?
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
        log.info("mnb - Got NEW Masternode entry - {} - {} - {} - {} ", getHash().toString(), address.toString(), vin.toString(), sigTime);
        Masternode mn = new Masternode(this);
        context.masternodeManager.add(mn);

        // if it matches our Masternode privkey, then we've been remotely activated
        if(pubkey2.equals(context.activeMasternode.pubKeyMasternode) && protocolVersion == NetworkParameters.PROTOCOL_VERSION){
            context.activeMasternode.enableHotColdMasterNode(vin, address);
        }

        boolean isLocal = address.getAddr().isSiteLocalAddress() || address.getAddr().isLoopbackAddress();
        if(params.getId().equals(NetworkParameters.ID_REGTEST)) isLocal = false;

        if(!isLocal) relay();

        return true;
    }

}
