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
        super(params, payloadBytes, 0, false, false, payloadBytes.length);
    }
    DarkSendEntryGetMessage(TransactionInput vin)
    {
        this.vin = vin;
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
        //vin TransactionInput
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        return cursor - offset;
    }
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        cursor = offset;

        optimalEncodingMessageSize = 0;

        TransactionOutPoint outpoint = new TransactionOutPoint(params, payload, cursor, this, parseLazy, parseRetain);
        cursor += outpoint.getMessageSize();
        int scriptLen = (int) readVarInt();
        byte [] scriptBytes = readBytes(scriptLen);
        long sequence = readUint32();
        vin = new TransactionInput(params, null, scriptBytes, outpoint);

        optimalEncodingMessageSize += outpoint.getMessageSize() + scriptLen + VarInt.sizeOf(scriptLen) +4;

         length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
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
        return "dseg Message:  " +
                "vin: " + vin.toString();

    }
}
