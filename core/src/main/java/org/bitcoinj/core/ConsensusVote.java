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

import org.darkcoinj.MasterNodeSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;

public class ConsensusVote extends ChildMessage implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ConsensusVote.class);

    TransactionInput vinMasterNode;
    boolean approved;
    Sha256Hash txHash;
    byte [] vchMasterNodeSignature;
    int blockHeight;


    private transient int optimalEncodingMessageSize;

    MasterNodeSystem system;

    ConsensusVote(MasterNodeSystem system)
    {
        this.system = system;
    }
    ConsensusVote(NetworkParameters params, byte[] bytes, int cursor)
    {
        super(params, bytes, cursor);
    }

    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
            //If length hasn't been provided this tx is probably contained within a block.
            //In parseRetain mode the block needs to know how long the transaction is
            //unfortunately this requires a fairly deep (though not total) parse.
            //This is due to the fact that transactions in the block's list do not include a
            //size header and inputs/outputs are also variable length due the contained
            //script so each must be instantiated so the scriptlength varint can be read
            //to calculate total length of the transaction.
            //We will still persist will this semi-light parsing because getting the lengths
            //of the various components gains us the ability to cache the backing bytearrays
            //so that only those subcomponents that have changed will need to be reserialized.

            //parse();
            //parsed = true;
            length = calcLength(payload, offset);
            cursor = offset + length;
        }
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
        // approved
        cursor += 1;

        //vchMasterNodeSignature
        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        //blockHeight
        cursor += 4;


        return cursor - offset;
    }
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        cursor = offset;

        byte [] hash256 = readBytes(32);
        txHash = new Sha256Hash(hash256);
        optimalEncodingMessageSize = 32;

        vinMasterNode = new TransactionInput(params, null, payload, cursor);
        optimalEncodingMessageSize += vinMasterNode.getMessageSize();

        byte [] approvedByte = readBytes(1);
        approved = approvedByte[0] != 0 ? true : false;
        optimalEncodingMessageSize += 1;

        vchMasterNodeSignature = readByteArray();
        optimalEncodingMessageSize += vchMasterNodeSignature.length;

        blockHeight = (int)readUint32();
        optimalEncodingMessageSize += 4;

        length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(txHash.getBytes()); //writes 32

        vinMasterNode.bitcoinSerialize(stream);

        stream.write(new VarInt(approved ? 1 : 0).encode());

        stream.write(new VarInt(vchMasterNodeSignature.length).encode());
        stream.write(vchMasterNodeSignature);

        uint32ToByteStreamLE(blockHeight, stream);


    }

    long getOptimalEncodingMessageSize()
    {
        if (optimalEncodingMessageSize != 0)
            return optimalEncodingMessageSize;
        maybeParse();
        if (optimalEncodingMessageSize != 0)
            return optimalEncodingMessageSize;
        optimalEncodingMessageSize = getMessageSize();
        return optimalEncodingMessageSize;
    }

    public String toString()
    {
        return "not ready";
    }

    public long getHeight()
    {return blockHeight;}
    public boolean isApproved() { return approved; }
    /*
    boolean signatureValid()
    {
        String errorMessage;
        String strMessage = txHash.toString() + blockHeight + approved;
        log.info("verify strMessage %s \n", strMessage);

        int n = system.getMasternodeByVin(vinMasterNode);

        if(n == -1)
        {
            log.warn("InstantX::CConsensusVote::SignatureValid() - Unknown Masternode\n");
            return false;
        }

        log.info("verify addr %s \n", system.vecMasternodes.get(0).getAddress().toString());
        log.info("verify addr %s \n", system.vecMasternodes.get(1).getAddress().toString());
        log.info("verify addr %d %s \n", n, system.vecMasternodes.get(n).getAddress().toString());

        CScript pubkey;
        pubkey.SetDestination(system.vecMasternodes[n].pubkey2.GetID());
        CTxDestination address1;
        ExtractDestination(pubkey, address1);
        CBitcoinAddress address2(address1);
        LogPrintf("verify pubkey2 %s \n", address2.ToString().c_str());

        if(!DarkSendSigner.VerifyMessage(system.vecMasternodes[n].pubkey2, vchMasterNodeSignature, strMessage, errorMessage)) {
            LogPrintf("InstantX::CConsensusVote::SignatureValid() - Verify message failed\n");
            return false;
        }

        return true;
    }

    bool CConsensusVote::Sign()
    {
        StringBuilder errorMessage = new StringBuilder();

        CKey key2;
        CPubKey pubkey2;
        String strMessage = txHash.toString() + blockHeight + approved;
        log.info("signing strMessage %s \n", strMessage);
        log.info("signing privkey %s \n", MasterNodeSystem.strMasterNodePrivKey);

        if(!DarkSendSigner.SetKey(strMasterNodePrivKey, errorMessage, key2, pubkey2))
        {
            log.error("CActiveMasternode::RegisterAsMasterNode() - ERROR: Invalid masternodeprivkey: '%s'\n", errorMessage.c_str());
            return false;
        }

        Script pubkey;
        pubkey.SetDestination(pubkey2.GetID());
        CTxDestination address1;
        ExtractDestination(pubkey, address1);
        Address address2(address1);
        log.info("signing pubkey2 %s \n", address2.ToString().c_str());

        if(!DarkSendSigner.SignMessage(strMessage, errorMessage, vchMasterNodeSignature, key2)) {
            log.error("CActiveMasternode::RegisterAsMasterNode() - Sign message failed");
            return false;
        }

        if(!DarkSendSigner.VerifyMessage(pubkey2, vchMasterNodeSignature, strMessage, errorMessage)) {
            log.error("CActiveMasternode::RegisterAsMasterNode() - Verify message failed");
            return false;
        }

        return true;
    }
    */
}
