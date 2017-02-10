/**
 * Copyright 2014 Hash Engineering Solutions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoinj.core;

import org.darkcoinj.DarkSendSigner;
import org.darkcoinj.InstantSend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

public class TransactionLockVote extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(TransactionLockVote.class);

    public Sha256Hash txHash;
    TransactionOutPoint outpoint;
    TransactionOutPoint outpointMasternode;
    MasternodeSignature vchMasternodeSignature;
    //public TransactionInput vinMasternode;

    //local memory only
    public int confirmedHeight;

    public long getTimeCreated() {
        return timeCreated;
    }

    long timeCreated;


    MasternodeManager masternodeManager;

    //MasterNodeSystem system;

    TransactionLockVote(NetworkParameters params, byte[] payload)
    {
        super(params, payload, 0);
      //  this.system = MasterNodeSystem.get();
        masternodeManager = Context.get().masternodeManager;
    }

    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;
        // jump past version (uint32)
        int cursor = offset;// + 4;
        // jump past the txHash
        cursor += 32 ;
        //vinMasternode TransactionInput
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        //vchMasternodeSignature
        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        //blockHeight
        cursor += 4;


        return cursor - offset;
    }
    @Override
    protected void parse() throws ProtocolException {

        cursor = offset;

        txHash = readHash();

        outpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += outpoint.getMessageSize();

        outpointMasternode = new TransactionOutPoint(params, payload, cursor);
        cursor+= outpointMasternode.getMessageSize();

        vchMasternodeSignature = new MasternodeSignature(params, payload, cursor);
        cursor += vchMasternodeSignature.getMessageSize();

        confirmedHeight = -1;
        timeCreated = Utils.currentTimeSeconds();

        length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(txHash.getBytes()); //writes 32

        outpoint.bitcoinSerialize(stream);
        outpointMasternode.bitcoinSerialize(stream);

        stream.write(new VarInt(vchMasternodeSignature.length).encode());
        vchMasternodeSignature.bitcoinSerialize(stream);

    }

    public String toString()
    {
        return "TransactionLockVote: tx: " + txHash +
                "height: " + confirmedHeight;
    }

    public long getHeight()
    {return confirmedHeight;}


    public Sha256Hash getHash()
    {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(txHash.getBytes());
            outpoint.bitcoinSerialize(bos);
            outpointMasternode.bitcoinSerialize(bos);

            return Sha256Hash.twiceOf(bos.toByteArray());
        }
        catch(IOException x)
        {
            return Sha256Hash.ZERO_HASH;
        }
    }

    public boolean isValid(Peer peer)
    {
        if(Context.get().isLiteMode())
            return true;

        if(!masternodeManager.has(outpointMasternode)) {
            log.info("instantsend--CTxLockVote::IsValid -- Unknown masternode "+ outpointMasternode.toStringCpp());
            masternodeManager.askForMN(peer, outpointMasternode);
            return false;
        }

        /*int nPrevoutHeight = GetUTXOHeight(outpoint);
        if(nPrevoutHeight == -1) {
            LogPrint("instantsend", "CTxLockVote::IsValid -- Failed to find UTXO %s\n", outpoint.ToStringShort());
            // Validating utxo set is not enough, votes can arrive after outpoint was already spent,
            // if lock request was mined. We should process them too to count them later if they are legit.
            CTransaction txOutpointCreated;
            uint256 nHashOutpointConfirmed;
            if(!GetTransaction(outpoint.hash, txOutpointCreated, Params().GetConsensus(), nHashOutpointConfirmed, true) || nHashOutpointConfirmed == uint256()) {
                LogPrint("instantsend", "CTxLockVote::IsValid -- Failed to find outpoint %s\n", outpoint.ToStringShort());
                return false;
            }
            LOCK(cs_main);
            BlockMap::iterator mi = mapBlockIndex.find(nHashOutpointConfirmed);
            if(mi == mapBlockIndex.end() || !mi->second) {
                // not on this chain?
                LogPrint("instantsend", "CTxLockVote::IsValid -- Failed to find block %s for outpoint %s\n", nHashOutpointConfirmed.ToString(), outpoint.ToStringShort());
                return false;
            }
            nPrevoutHeight = mi->second->nHeight;
        }

        int nLockInputHeight = nPrevoutHeight + 4;

        int n = mnodeman.GetMasternodeRank(CTxIn(outpointMasternode), nLockInputHeight, MIN_INSTANTSEND_PROTO_VERSION);

        if(n == -1) {
            //can be caused by past versions trying to vote with an invalid protocol
            LogPrint("instantsend", "CTxLockVote::IsValid -- Outdated masternode %s\n", outpointMasternode.ToStringShort());
            return false;
        }
        LogPrint("instantsend", "CTxLockVote::IsValid -- Masternode %s, rank=%d\n", outpointMasternode.ToStringShort(), n);

        int nSignaturesTotal = COutPointLock::SIGNATURES_TOTAL;
        if(n > nSignaturesTotal) {
            LogPrint("instantsend", "CTxLockVote::IsValid -- Masternode %s is not in the top %d (%d), vote hash=%s\n",
                    outpointMasternode.ToStringShort(), nSignaturesTotal, n, GetHash().ToString());
            return false;
        }
*/
        if(!checkSignature()) {
            log.info("CTxLockVote::IsValid -- Signature invalid");
            return false;
        }

        return true;
    }

    boolean checkSignature()
    {
        StringBuilder errorMessage = new StringBuilder();
        String strMessage = txHash.toString() + outpoint.toStringCpp();

        MasternodeInfo infoMn = masternodeManager.getMasternodeInfo(outpointMasternode);

        if(!infoMn.fInfoValid) {
            log.info("CTxLockVote::CheckSignature -- Unknown Masternode: masternode="+ outpointMasternode.toString());
            return false;
        }

        if(!DarkSendSigner.verifyMessage(infoMn.pubKeyMasternode, vchMasternodeSignature, strMessage, errorMessage)) {
            log.info("CTxLockVote::CheckSignature -- VerifyMessage() failed, error: "+  errorMessage);
            return false;
        }

        return true;
    }
    public Sha256Hash getTxHash() { return txHash; }
    public TransactionOutPoint getOutpointMasternode() { return outpointMasternode; }
    public TransactionOutPoint getOutpoint() { return outpoint; }

    public void setConfirmedHeight(int confirmedHeight) { this.confirmedHeight = confirmedHeight; }


    public boolean isExpired(int height)
    {
        // Locks and votes expire nInstantSendKeepLock blocks after the block corresponding tx was included into.
        return (confirmedHeight != -1) && (height - confirmedHeight > InstantSend.nInstantSendKeepLock);
    }
}
