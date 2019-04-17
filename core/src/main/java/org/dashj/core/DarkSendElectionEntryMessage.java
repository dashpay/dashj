package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 2/10/2015.
 */
public class DarkSendElectionEntryMessage extends Message {
    private static final Logger log = LoggerFactory.getLogger(DarkSendElectionEntryMessage.class);

    TransactionInput vin;
    PeerAddress addr;
    PublicKey pubkey;
    PublicKey pubkey2;
    byte [] vchSig;
    long sigTime;
    int count;
    int current;
    long lastUpdated;
    int protocolVersion;

    private transient int optimalEncodingMessageSize;


    DarkSendElectionEntryMessage()
    {
        super();
    }

    DarkSendElectionEntryMessage(NetworkParameters params, byte[] payloadBytes)
    {
        super(params, payloadBytes, 0);
    }

    DarkSendElectionEntryMessage(NetworkParameters params, TransactionInput vin, PeerAddress addr, byte [] vchSig,  long sigTime, PublicKey pubkey, PublicKey pubkey2, int count, int current, long lastTimeSeen, int protocolVersion)
    {
        super(params);
        this.vin = vin;
        this.addr = addr;
        this.vchSig = vchSig;
        this.sigTime = sigTime;
        this.pubkey = pubkey;
        this.pubkey2 = pubkey2;
        this.count = count;
        this.current = current;
        this.protocolVersion = protocolVersion;

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

        varint = new VarInt(buf, cursor);
        cursor += varint.getOriginalSizeInBytes() + varint.value;

        varint = new VarInt(buf, cursor);
        cursor += varint.getOriginalSizeInBytes() + varint.value;

        varint = new VarInt(buf, cursor);
        cursor += varint.getOriginalSizeInBytes() + varint.value;

        cursor += 8 + 4 + 4 + 8 + 4;

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

        pubkey = new PublicKey(params, payload, cursor);
        cursor += pubkey.getMessageSize();

        pubkey2 = new PublicKey(params, payload, cursor);
        cursor += pubkey.getMessageSize();

        vchSig = readByteArray();

        sigTime = readInt64();

        count = (int)readUint32();

        current = (int)readUint32();

        lastUpdated = readInt64();

        protocolVersion = (int)readUint32();

        length = cursor - offset;


    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        pubkey.bitcoinSerialize(stream);
        pubkey2.bitcoinSerialize(stream);

        stream.write(new VarInt(vchSig.length).encode());
        stream.write(vchSig);

        Utils.int64ToByteStreamLE(sigTime, stream);
        Utils.uint32ToByteStreamLE(count, stream);
        Utils.uint32ToByteStreamLE(current, stream);
        Utils.int64ToByteStreamLE(lastUpdated, stream);
        Utils.uint32ToByteStreamLE(protocolVersion, stream);
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
        return "dsee Message:  " +
                "vin: " + vin.toString() + " - " + addr.toString();

    }
}
