package org.bitcoinj.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static com.hashengineering.crypto.X11.x11Digest;

/**
 * Created by Eric on 2/8/2015.
 */
public class GetMasternodeVoteSyncMessage extends Message{

    Sha256Hash prop;

    public GetMasternodeVoteSyncMessage(NetworkParameters params) { super(params);}

    public GetMasternodeVoteSyncMessage(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
    }
    public GetMasternodeVoteSyncMessage(NetworkParameters params, Sha256Hash prop)
    {
        super(params);
        this.prop = prop;
    }

    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;

        //vin
        cursor += 32;
        return cursor - offset;
    }

    @Override
    protected void parse() throws ProtocolException {

        prop = readHash();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(prop.getReversedBytes());
    }
}
