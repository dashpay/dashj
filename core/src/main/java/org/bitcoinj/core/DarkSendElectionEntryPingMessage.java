package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

import static org.bitcoinj.core.Utils.int64ToByteStreamLE;

/**
 * Created by Eric on 2/10/2015.
 */
public class DarkSendElectionEntryPingMessage extends Message {
    private static final Logger log = LoggerFactory.getLogger(TransactionLockVote.class);

    TransactionInput vin;
    byte [] vchSig;
    long sigTime;
    boolean stop;

    private transient int optimalEncodingMessageSize;


    DarkSendElectionEntryPingMessage()
    {
        super();
    }

    DarkSendElectionEntryPingMessage(NetworkParameters params, byte[] payloadBytes)
    {
        super(params, payloadBytes, 0);
    }


    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;
        // jump past version (uint32)
        int cursor = offset;// + 4;
        //vin TransactionInput
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

        //sigTime, stop
        cursor += 8 + 1;


        return cursor - offset;
    }
    @Override
    protected void parse() throws ProtocolException {

        cursor = offset;

        optimalEncodingMessageSize = 0;

        TransactionOutPoint outpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += outpoint.getMessageSize();
        int scriptLen = (int) readVarInt();
        byte [] scriptBytes = readBytes(scriptLen);
        long sequence = readUint32();
        vin = new TransactionInput(params, null, scriptBytes, outpoint);

        optimalEncodingMessageSize += outpoint.getMessageSize() + scriptLen + VarInt.sizeOf(scriptLen) +4;

        vchSig = readByteArray();
        optimalEncodingMessageSize += vchSig.length + VarInt.sizeOf(vchSig.length);

        sigTime = readInt64();
        optimalEncodingMessageSize += 4;

        byte [] stopByte = readBytes(1);
        stop = stopByte[0] != 0 ? true : false;
        optimalEncodingMessageSize += 1;





        length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        stream.write(vchSig);
        int64ToByteStreamLE(sigTime, stream);
        stream.write(new VarInt(stop ? 1 : 0).encode());
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
        return "Dark Send Election Entry Ping Message:  " +
                "vin: " + vin.toString() +
                "sig: " + Utils.HEX.encode(vchSig) +
                "time " + sigTime +
                "stop " + stop;

    }
}
