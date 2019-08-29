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
    protected void parse() throws ProtocolException {

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
