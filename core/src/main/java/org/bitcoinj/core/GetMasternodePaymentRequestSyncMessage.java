package org.bitcoinj.core;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 2/27/2016.
 *
 * mnget Message
 */
public class GetMasternodePaymentRequestSyncMessage extends Message{

    int countNeeded;

    public GetMasternodePaymentRequestSyncMessage(NetworkParameters params) { super(params);}

    public GetMasternodePaymentRequestSyncMessage(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
    }
    public GetMasternodePaymentRequestSyncMessage(NetworkParameters params, int countNeeded)
    {
        super(params);
        this.countNeeded = countNeeded;
    }

    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;

        //vin
        cursor += 4;
        return cursor - offset;
    }

    @Override
    protected void parse() throws ProtocolException {

        countNeeded = (int)readUint32();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(countNeeded, stream);
    }
}
