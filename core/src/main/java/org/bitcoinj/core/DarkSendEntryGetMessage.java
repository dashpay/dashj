package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 2/10/2015.
 */
public class DarkSendEntryGetMessage extends Message {
    private static final Logger log = LoggerFactory.getLogger(DarkSendEntryGetMessage.class);

    TransactionInput vin;

    private transient int optimalEncodingMessageSize;


    DarkSendEntryGetMessage()
    {
        super();
    }

    DarkSendEntryGetMessage(NetworkParameters params, byte[] payloadBytes)
    {
        super(params, payloadBytes, 0);
    }
    DarkSendEntryGetMessage(TransactionInput vin)
    {
        this.vin = vin;
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

        return cursor - offset;
    }
    @Override
    protected void parse() throws ProtocolException {


        cursor = offset;


        TransactionOutPoint outpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += outpoint.getMessageSize();
        int scriptLen = (int) readVarInt();
        byte [] scriptBytes = readBytes(scriptLen);
        long sequence = readUint32();
        vin = new TransactionInput(params, null, scriptBytes, outpoint);

        cursor += outpoint.getMessageSize() + scriptLen + VarInt.sizeOf(scriptLen) +4;

         length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
    }

    public String toString()
    {
        return "dseg Message:  " +
                "vin: " + vin.toString();

    }
}
