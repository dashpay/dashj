package org.bitcoinj.core;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static org.bitcoinj.core.Utils.int64ToByteStreamLE;
import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;

/**
 * Created by Eric on 2/8/2015.
 */
public class MasterNodePaymentWinner extends ChildMessage implements Serializable {
    int blockHeight;
    TransactionInput vin;
    byte [] vchSig;
    long score;

    private transient int optimalEncodingMessageSize;

    MasterNodePaymentWinner()
    {
    }
    MasterNodePaymentWinner(NetworkParameters params, byte[] bytes, int cursor)
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
        // jump past blockHeight
        cursor += 4 ;
        //score
        cursor += 8;
        // TransactionInput
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        //vchSig
        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;




        return cursor - offset;
    }
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        cursor = offset;

        blockHeight = (int)readUint32();
        optimalEncodingMessageSize = 4;

        score = readInt64();
        optimalEncodingMessageSize += 8;

        vin = new TransactionInput(params, null, payload, cursor);
        optimalEncodingMessageSize += vin.getMessageSize();

        vchSig = readByteArray();
        optimalEncodingMessageSize += vchSig.length;

        length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        uint32ToByteStreamLE(blockHeight, stream);
        int64ToByteStreamLE(score, stream);
        vin.bitcoinSerialize(stream);

        stream.write(new VarInt(vchSig.length).encode());
        stream.write(vchSig);

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
}
