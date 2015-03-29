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
import java.math.BigInteger;
import java.util.Arrays;

import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;

public class ConsensusVote extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ConsensusVote.class);

    public TransactionInput vinMasterNode;
    public Sha256Hash txHash;
    public int blockHeight;
    byte [] vchMasterNodeSignature;



    private transient int optimalEncodingMessageSize;

    MasterNodeSystem system;

    ConsensusVote(MasterNodeSystem system)
    {
        this.system = MasterNodeSystem.get();
    }
    ConsensusVote(NetworkParameters params, byte[] payload)
    {
        super(params, payload, 0, false, false, payload.length);
        this.system = MasterNodeSystem.get();
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
        optimalEncodingMessageSize = 32;

        TransactionOutPoint outpoint = new TransactionOutPoint(params, payload, cursor, this, parseLazy, parseRetain);
        cursor += outpoint.getMessageSize();
        int scriptLen = (int) readVarInt();
        byte [] scriptBytes = readBytes(scriptLen);
        long sequence = readUint32();
        vinMasterNode = new TransactionInput(params, null, scriptBytes, outpoint);
        optimalEncodingMessageSize += vinMasterNode.getMessageSize();

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
        return "ConsensusVote: tx: " + txHash +
                "height: " + blockHeight;
    }

    public long getHeight()
    {return blockHeight;}


    public boolean signatureValid()
    {
        StringBuilder errorMessage = new StringBuilder();
        String strMessage = txHash.toString() + blockHeight;
        log.info("verify strMessage " + strMessage);

        /*int n = system.getMasternodeByVin(vinMasterNode);

        if(n == -1)
        {
            log.warn("InstantX::CConsensusVote::SignatureValid() - Unknown Masternode\n");
            return false;
        }

        log.info("verify addr %s \n", system.vecMasternodes.get(0).getAddress().toString());
        log.info("verify addr %s \n", system.vecMasternodes.get(1).getAddress().toString());
        log.info("verify addr %d %s \n", n, system.vecMasternodes.get(n).getAddress().toString());

        Script pubkey;
        //pubkey.SetDestination(system.vecMasternodes.get(n).pubkey2.GetID());
        pubkey = ScriptBuilder.createOutputScript(system.vecMasternodes.get(n).pubkey2);
        //CTxDestination address1;
       //ExtractDestination(pubkey, address1);
        //CBitcoinAddress address2(address1);

        Address address2 = pubkey.getToAddress(params);
        log.info("verify pubkey2 %s \n", address2.toString());

        if(!DarkSendSigner.verifyMessage(system.vecMasternodes.get(n).pubkey2, vchMasterNodeSignature, strMessage, errorMessage)) {
            log.info("InstantX::CConsensusVote::SignatureValid() - Verify message failed\n");
            return false;
        } */

        return true;
    }
    /*
    boolean sign()
    {
        StringBuilder errorMessage = new StringBuilder();

        //CKey key2;
        //CPubKey pubkey2;
        String strMessage = txHash.toString() + blockHeight;
        log.info("signing strMessage %s \n", strMessage);
        log.info("signing privkey %s \n", DarkCoinSystem.strMasterNodePrivKey);

        ECKey key2 = DarkSendSigner.setKey(DarkCoinSystem.strMasterNodePrivKey, errorMessage);
        if(key2 == null)
        {
            log.error("CActiveMasternode::RegisterAsMasterNode() - ERROR: Invalid masternodeprivkey: '%s'\n", errorMessage);
            return false;
        }

        Script pubkey = ScriptBuilder.createOutputScript(key2);
        //pubkey.SetDestination(pubkey2.GetID());
        //CTxDestination address1;
        //ExtractDestination(pubkey, address1);
        //Address address2(address1);
        Address address2 = pubkey.getToAddress(params);
        log.info("signing pubkey2 %s \n", address2.toString());

        if(vchMasterNodeSignature = DarkSendSigner.signMessage(strMessage, errorMessage, vchMasterNodeSignature, key2) == null) {
            log.error("CActiveMasternode::RegisterAsMasterNode() - Sign message failed");
            return false;
        }

        if(!DarkSendSigner.verifyMessage(pubkey2, vchMasterNodeSignature, strMessage, errorMessage)) {
            log.error("CActiveMasternode::RegisterAsMasterNode() - Verify message failed");
            return false;
        }

        return true;
    }
    */
    public Sha256Hash getHash()
    {
        BigInteger part1 =  vinMasterNode.getOutpoint().getHash().toBigInteger();
        BigInteger part2 = BigInteger.valueOf(vinMasterNode.getOutpoint().getIndex());
        BigInteger part3 = txHash.toBigInteger();

        return new Sha256Hash(Arrays.copyOf(part1.add(part2.add(part3)).toByteArray(), 32));
    }
}
