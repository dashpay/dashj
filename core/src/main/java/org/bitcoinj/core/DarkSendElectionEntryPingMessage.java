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
    private static final Logger log = LoggerFactory.getLogger(ConsensusVote.class);

    TransactionInput vin;
    byte [] vchSig;
    long sigTime;
    boolean stop;

    private transient int optimalEncodingMessageSize;

    MasterNodeSystem system;

    DarkSendElectionEntryPingMessage()
    {
        super();
    }

    DarkSendElectionEntryPingMessage(MasterNodeSystem system)
    {
        super();
        this.system = system;
    }
    DarkSendElectionEntryPingMessage(NetworkParameters params, byte[] payloadBytes)
    {
        super(params, payloadBytes, 0, false, false, payloadBytes.length);
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

        //vchMasterNodeSignature
        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        //sigTime, stop
        cursor += 8 + 1;


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
        maybeParse();
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
