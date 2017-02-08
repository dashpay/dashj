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
    long timeCreated;




    //MasterNodeSystem system;

    TransactionLockVote(NetworkParameters params, byte[] payload)
    {
        super(params, payload, 0);
      //  this.system = MasterNodeSystem.get();
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

    public boolean isValid()
    {
        if(Context.get().isLiteMode())
            return true;
        return true;
        /*
        StringBuilder errorMessage = new StringBuilder();
        String strMessage = txHash.toString() + confirmedHeight;
        //LogPrintf("verify strMessage %s \n", strMessage.c_str());

        if(!Context.get().masternodeManager.has(outpointMasternode))
        {
            log.info("InstantX::C::SignatureValid() - Unknown Masternode");
            return false;
        }

        if(!checkSignature()) {
            log.info("InstantX::CConsensusVote::SignatureValid() - Verify message failed");
            return false;
        }

        return true;
        */
    }
    boolean checkSignature()
    {
        /*StringBuilder errorMessage = new StringBuilder();
        String strMessage = txHash.toString() + outpoint.toStringCpp();

        MasternodeInfo infoMn = Context.get().masternodeManager.getMasternodeInfo(outpointMasternode);

        if(!infoMn.fInfoValid) {
            log.info("CTxLockVote::CheckSignature -- Unknown Masternode: masternode=%s\n", outpointMasternode.ToString());
            return false;
        }

        if(!DarkSendSigner.verifyMessage(infoMn.pubKeyMasternode, vchMasternodeSignature, strMessage, errorMessage)) {
            log.info("CTxLockVote::CheckSignature -- VerifyMessage() failed, error: "+  errorMessage);
            return false;
        }
*/
        return true;
    }
    public Sha256Hash getTxHash() { return txHash; }
    public TransactionOutPoint getOutpointMasternode() { return outpointMasternode; }
    public TransactionOutPoint getOutpoint() { return outpoint; }
}
