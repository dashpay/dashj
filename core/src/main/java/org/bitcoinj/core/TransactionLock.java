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
import java.util.ArrayList;

import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;

public class TransactionLock extends ChildMessage implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(Transaction.class);
    public int blockHeight;
    Transaction tx;
    ArrayList<ConsensusVote> vecConsensusVotes;

    private transient int optimalEncodingMessageSize;

    DarkCoinSystem system;

    TransactionLock()
    {
    }
    TransactionLock(NetworkParameters params, byte[] bytes, int cursor, Message parent, boolean parseLazy, boolean parseRetain, int length)
    {
        super(params, bytes, cursor, parent, parseLazy, parseRetain, length);
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

        int cursor = offset;

        int txLength = Transaction.calcLength(buf, cursor);
        cursor += txLength;

        cursor += 4;

        cursor += ConsensusVote.calcLength(buf, cursor);

        // 4 = length of number votes (uint32)
        return cursor - offset;
    }
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        cursor = offset;
        tx = new Transaction(params, payload, cursor);
        optimalEncodingMessageSize = tx.getMessageSize();
        blockHeight = (int)readUint32();
        optimalEncodingMessageSize += 4;


        long count = readVarInt();
        optimalEncodingMessageSize += VarInt.sizeOf(count);
        vecConsensusVotes = new ArrayList<ConsensusVote>((int)count);
        for(int i = 0; i < count; ++i)
        {
            ConsensusVote vote = new ConsensusVote(params, payload, cursor);
            vecConsensusVotes.add(vote);
            optimalEncodingMessageSize += vote.getOptimalEncodingMessageSize();
            cursor += vote.getMessageSize();
        }
        length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        tx.bitcoinSerialize(stream);
        uint32ToByteStreamLE(blockHeight, stream);

        stream.write(new VarInt(vecConsensusVotes.size()).encode());
        for(int i = 0; i < vecConsensusVotes.size(); ++i)
            vecConsensusVotes.get(i).bitcoinSerialize(stream);

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

    public String toString() {
        return "nothing here";
    }
    public Sha256Hash getHash()
    {
        return tx.getHash();
    }
    /*
    boolean SignaturesValid()
    {
        for(ConsensusVote vote : vecConsensusVotes) {
            int n = system.masternode.getMasterNodeRank(vote.vinMasterNode, vote.getHeight(), InstantXSystem.MIN_INSTANTX_PROTO_VERSION)
            if(n == -1)
            {
                log.warn("InstantX::DoConsensusVote - Unknown Masternode\n");
                return false;
            }

            if(n > 10)
            {
                log.warn("InstantX::DoConsensusVote - Masternode not in the top 10\n");
                return false;
            }

            if(!vote.SignatureValid()){
                log.warn("InstantX::CTransactionLock::SignaturesValid - Signature not valid\n");
                return false;
            }
        }
        return true;
    }
    */
    int countSignatures()
    {
        return vecConsensusVotes.size();
    }
    boolean allInFavor()
    {
        for(ConsensusVote vote : vecConsensusVotes)
        {
            if(vote.isApproved() == false)
                return false;
        }
        return true;
    }
    void addSignature(ConsensusVote cv)
    {
        vecConsensusVotes.add(cv);
    }


    public long getHeight()
    {return blockHeight;}


}
