package org.bitcoinj.core;

import org.darkcoinj.DarkSend;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 2/20/2015.
 */
public class MasternodeBroadcast extends Masternode {

    public MasternodeBroadcast(NetworkParameters params, byte [] payloadBytes)
    {
        super(params, payloadBytes);
    }

    public MasternodeBroadcast(Masternode masternode)
    {
       super(masternode);
    }


    private transient int optimalEncodingMessageSize;
    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
            length = calcLength(payload, offset);
            cursor = offset + length;
        }
    }

    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;

        //vin
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        varint = new VarInt(buf, cursor);
        long size = varint.value;
        cursor += varint.getOriginalSizeInBytes();
        cursor += size;

        return cursor - offset;
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;


        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        address = new MasternodeAddress(params, payload, cursor, 0);
        cursor += address.getMessageSize();

        pubkey = new PublicKey(params, payload, cursor);
        cursor += pubkey.getMessageSize();

        pubkey2 = new PublicKey(params, payload, cursor);
        cursor += pubkey2.getMessageSize();

        sig = new MasternodeSignature(params, payload, cursor);
        cursor += sig.getMessageSize();

        sigTime = readInt64();

        protocolVersion = (int)readUint32();

        lastPing = new MasternodePing(params, payload, cursor);
        cursor += lastPing.getMessageSize();

        nLastDsq = readInt64();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        address.bitcoinSerialize(stream);
        pubkey.bitcoinSerialize(stream);
        pubkey2.bitcoinSerialize(stream);

        sig.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(sigTime, stream);
        Utils.uint32ToByteStreamLE(protocolVersion, stream);

        Utils.int64ToByteStreamLE(nLastDsq, stream);

    }

}
