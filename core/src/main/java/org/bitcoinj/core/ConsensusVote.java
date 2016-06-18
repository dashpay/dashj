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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;

import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;

public class ConsensusVote extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ConsensusVote.class);

    public TransactionInput vinMasternode;
    public Sha256Hash txHash;
    public int blockHeight;
    MasternodeSignature vchMasterNodeSignature;



    //MasterNodeSystem system;

    ConsensusVote(NetworkParameters params, byte[] payload)
    {
        super(params, payload, 0, false, false, payload.length);
      //  this.system = MasterNodeSystem.get();
    }

    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
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

        txHash = readHash();

        TransactionOutPoint outpoint = new TransactionOutPoint(params, payload, cursor, this, parseLazy, parseRetain);
        cursor += outpoint.getMessageSize();
        int scriptLen = (int) readVarInt();
        byte [] scriptBytes = readBytes(scriptLen);
        long sequence = readUint32();
        vinMasternode = new TransactionInput(params, null, scriptBytes, outpoint);

        vchMasterNodeSignature = new MasternodeSignature(params, payload, cursor);
        cursor += vchMasterNodeSignature.getMessageSize();

        blockHeight = (int)readUint32();

        length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(txHash.getBytes()); //writes 32

        vinMasternode.bitcoinSerialize(stream);

        stream.write(new VarInt(vchMasterNodeSignature.length).encode());
        vchMasterNodeSignature.bitcoinSerialize(stream);

        uint32ToByteStreamLE(blockHeight, stream);


    }

    public String toString()
    {
        return "ConsensusVote: tx: " + txHash +
                "height: " + blockHeight;
    }

    public long getHeight()
    {return blockHeight;}


    public Sha256Hash getHash()
    {
        BigInteger part1 =  vinMasternode.getOutpoint().getHash().toBigInteger();
        BigInteger part2 = BigInteger.valueOf(vinMasternode.getOutpoint().getIndex());
        BigInteger part3 = txHash.toBigInteger();

        return Sha256Hash.wrap(Arrays.copyOf(part1.add(part2.add(part3)).toByteArray(), 32));
    }

    public boolean signatureValid()
    {
        StringBuilder errorMessage = new StringBuilder();
        String strMessage = txHash.toString() + blockHeight;
        //LogPrintf("verify strMessage %s \n", strMessage.c_str());

        Masternode pmn = Context.get().masternodeManager.find(vinMasternode);

        if(pmn == null)
        {
            log.info("InstantX::CConsensusVote::SignatureValid() - Unknown Masternode");
            return false;
        }

        if(!DarkSendSigner.verifyMessage(pmn.pubkey2, vchMasterNodeSignature, strMessage, errorMessage)) {
            log.info("InstantX::CConsensusVote::SignatureValid() - Verify message failed");
            return false;
        }

        return true;
    }
}
