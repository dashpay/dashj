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
package org.dashj.core;

import org.dashj.crypto.BLSSignature;
import org.dashj.evolution.SimplifiedMasternodeList;
import org.dashj.evolution.SimplifiedMasternodeListEntry;
import org.dashj.evolution.SimplifiedMasternodeListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static org.dashj.core.InstantSend.INSTANTSEND_TIMEOUT_SECONDS;

public class TransactionLockVote extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(TransactionLockVote.class);

    public Sha256Hash txHash;
    TransactionOutPoint outpoint;
    TransactionOutPoint outpointMasternode;
    Sha256Hash masternodeProTxHash;
    Sha256Hash quorumModifierHash;
    MasternodeSignature vchMasternodeSignature;

    //local memory only
    public int confirmedHeight;
    Context context;

    public long getTimeCreated() {
        return timeCreated;
    }

    long timeCreated;


    MasternodeManager masternodeManager;
    SimplifiedMasternodeListManager masternodeListManager;

    //MasterNodeSystem system;

    public TransactionLockVote(NetworkParameters params, byte[] payload)
    {
        super(params, payload, 0);
        this.context = Context.get();
        masternodeManager = context.masternodeManager;
        masternodeListManager = context.masternodeListManager;
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

        if(params.isSupportingEvolution() && Context.get().masternodeListManager.isDeterministicMNsSporkActive()) {
            quorumModifierHash = readHash();
            masternodeProTxHash = readHash();
        }

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

        if(params.isSupportingEvolution() && masternodeListManager.isDeterministicMNsSporkActive()) {
            stream.write(quorumModifierHash.getReversedBytes());
            stream.write(masternodeProTxHash.getReversedBytes());
        }
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
            bos.write(txHash.getReversedBytes());
            outpoint.bitcoinSerialize(bos);
            outpointMasternode.bitcoinSerialize(bos);
            if(params.isSupportingEvolution() && masternodeListManager.isDeterministicMNsSporkActive()) {
                bos.write(quorumModifierHash.getReversedBytes());
                bos.write(masternodeProTxHash.getReversedBytes());
            }
            return Sha256Hash.twiceOf(bos.toByteArray());
        }
        catch(IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    public Sha256Hash getSignatureHash() {
        return getHash();
    }

    public boolean isValid(Peer peer)
    {
        SimplifiedMasternodeList mnList = masternodeListManager.getListAtChainTip();

        if(params.isSupportingEvolution() && masternodeListManager.isDeterministicMNsSporkActive()) {


            if(mnList.getMN(masternodeProTxHash) == null) {
                log.info("Unknown masternode " + masternodeProTxHash);
                return false;
            }

            // At this point Dash Core would check unspent outputs (UTXO's) to find the
            // block height of the related transaction.  Then this would be used to calculate
            // the lockInputHeight (input height + 4).  Next the masternode rank would be
            // determined of the masternode that created this TxLockVote.  Since an SPV node
            // cannot normally determine the input height, we will trust quorumModifierHash
            // as being the block hash of the lockInputHeight.

            int rank = masternodeListManager.getListAtChainTip().getMasternodeRank(masternodeProTxHash, quorumModifierHash);
            if (rank < 0) {
                //can be caused by past versions trying to vote with an invalid protocol
                log.error("Can't calculate rank for masternode {}", masternodeProTxHash);
                return false;
            }
            log.info("Masternode {}, rank={}", masternodeProTxHash, rank);

            int signaturesTotal = TransactionOutPointLock.SIGNATURES_TOTAL;
            if (rank > signaturesTotal) {
                log.error("Masternode {} is not in the top {} ({}), vote hash={}",
                        masternodeProTxHash, signaturesTotal, rank, getHash());
                return false;
            }
        } else {
            //don't check masternode rank on 0.12.3
        }

        if(!checkSignature()) {
            log.info("CTxLockVote::IsValid -- Signature invalid");
            return false;
        }

        return true;
    }

    boolean checkSignature()
    {
        if(masternodeListManager.isDeterministicMNsSporkActive()) {

            SimplifiedMasternodeListEntry dmn = masternodeListManager.getListAtChainTip().getMN(masternodeProTxHash);
            if(dmn == null) {
                log.error("TxLockVote.checkSignature:  Unknown Masternode: " + masternodeProTxHash);
                return false;
            }
            Sha256Hash hash = getSignatureHash();

            BLSSignature sig = new BLSSignature(vchMasternodeSignature.getBytes());
            if(!sig.isValid() || !sig.verifyInsecure(dmn.getPubKeyOperator(), hash)) {
                log.error("CTxLockVote::CheckSignature -- VerifyInsecure() failed");
                return false;
            }
        } else if (context.sporkManager.isSporkActive(SporkManager.SPORK_6_NEW_SIGS)/* && !context.isLiteMode()*/) {
            //This will not be handled, but we will leave the code here for now

            /*Sha256Hash hash = getSignatureHash();

            StringBuilder strError = new StringBuilder();
            String strMessage = txHash.toString() + outpoint.toStringCpp();

            MasternodeInfo infoMn = masternodeManager.getMasternodeInfo(outpointMasternode);
            if(infoMn == null){
                log.error("CTxLockVote::CheckSignature -- Unknown Masternode: masternode={}", outpointMasternode.toString());
                return false;
            }

            if (!HashSigner.verifyHash(hash, infoMn.legacyKeyIDOperator, vchMasternodeSignature, strError)) {
                // could be a signature in old format
                if (!MessageSigner.verifyMessage(infoMn.legacyKeyIDOperator, vchMasternodeSignature, strMessage, strError)) {
                    // nope, not in old format either
                    log.error("CTxLockVote::CheckSignature -- VerifyMessage() failed, error: {}", strError);
                    return false;
                }
            }*/
            return true;
        } else {
            //old sigs, we won't handle this case either.
            return true;
        }

        return true;
    }
    public Sha256Hash getTxHash() { return txHash; }
    public TransactionOutPoint getOutpointMasternode() { return outpointMasternode; }
    public TransactionOutPoint getOutpoint() { return outpoint; }

    public Sha256Hash getQuorumModifierHash() {
        return quorumModifierHash;
    }

    public Sha256Hash getMasternodeProTxHash() {
        return masternodeProTxHash;
    }

    public void setConfirmedHeight(int confirmedHeight) { this.confirmedHeight = confirmedHeight; }


    public boolean isExpired(int height)
    {
        // Locks and votes expire nInstantSendKeepLock blocks after the block corresponding tx was included into.
        return (confirmedHeight != -1) && (height - confirmedHeight > params.getInstantSendKeepLock());
    }
    public boolean isTimedOut()
    {
        return Utils.currentTimeSeconds() - timeCreated > INSTANTSEND_TIMEOUT_SECONDS;
    }

    public void relay() {}
}
