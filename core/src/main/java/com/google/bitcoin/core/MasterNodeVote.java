package com.google.bitcoin.core;

import com.google.bitcoin.core.ChildMessage;
import com.google.bitcoin.core.ProtocolException;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.script.ScriptBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static com.google.bitcoin.core.Utils.int64ToByteStreamLE;
import static com.google.bitcoin.core.Utils.uint32ToByteStreamLE;

/**
 * Created by Eric on 5/28/14.
 */
public class MasterNodeVote  extends ChildMessage implements Serializable {
    public int votes;
    public Script pubkey;
    int version;
    boolean setPubkey;

    long blockHeight;

    static final int CURRENT_VERSION=1;

    private transient int optimalEncodingMessageSize;

    MasterNodeVote()
    {
        version = CURRENT_VERSION;
        votes = 0;
        pubkey = null;
        blockHeight = 0;
    }
    MasterNodeVote(NetworkParameters params, byte [] bytes, int cursor, Message parent, boolean parseLazy, boolean parseRetain, int length)
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
            length = calcLength(bytes, offset);
            cursor = offset + length;
        }
    }
    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;
        // jump past version (uint32)
        int cursor = offset;// + 4;
        // jump past the block heignt (uint64)
        cursor += 8;

        int i;
        long scriptLen;

        varint = new VarInt(buf, cursor);
        long sizeScript = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += sizeScript;


        // 4 = length of number votes (uint32)
        return cursor - offset + 4;
    }
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        cursor = offset;
        //version = (int)readUint32();

        blockHeight = readInt64();
        optimalEncodingMessageSize = 8;
        //TODO: not finished
        //pubkey = ?
        long scriptSize = readVarInt();
        optimalEncodingMessageSize += VarInt.sizeOf(scriptSize);
        byte [] scriptBytes = readBytes((int)scriptSize);
        pubkey = new Script(scriptBytes);
        optimalEncodingMessageSize += scriptSize;

        votes = (int)readUint32();

        optimalEncodingMessageSize += 4;

        length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        //uint32ToByteStreamLE(version, stream);
        int64ToByteStreamLE(blockHeight, stream);
        //scrypt pubkey         //TODO: not finished
        byte [] scriptBytes = pubkey.getProgram();
        stream.write(new VarInt(scriptBytes.length).encode());
        stream.write(scriptBytes);
        //this.
        uint32ToByteStreamLE(votes, stream);
    }

    long getOptimalEncodingMessageSize()
    {
        if(optimalEncodingMessageSize != 0)
            return optimalEncodingMessageSize;

        //TODO: not finished
        //version
        //optimalEncodingMessageSize = 4;
        //block height
        optimalEncodingMessageSize += 8;
        //pubkey
        byte [] scriptBytes = pubkey.getProgram();

        optimalEncodingMessageSize += VarInt.sizeOf(scriptBytes.length);
        optimalEncodingMessageSize += scriptBytes.length;
        //votes
        optimalEncodingMessageSize += 4;

        return optimalEncodingMessageSize;
    }

    public String toString()
    {
        return "Master Node Vote: v" + version + "; blockHeight " + blockHeight + "; pubkey" + pubkey.toString() +  "; votes: " + votes + "\n";
    }


    public void vote()
    {
        votes++;
    }
    public int getVotes()
    {return votes;}

    public long getHeight()
    {return blockHeight;}

    Script getPubkey() { return pubkey;}
}
