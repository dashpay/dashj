package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Created by Hash Engineering on 2/20/2015.
 */
public class MasternodeSignature extends ChildMessage {
    private static final Logger log = LoggerFactory.getLogger(MasternodeSignature.class);

    byte [] bytes;

    MasternodeSignature(NetworkParameters params)
    {
        super(params);
    }

    public MasternodeSignature(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    public MasternodeSignature(NetworkParameters params, byte[] payloadBytes, int cursor, Message parent, boolean parseLazy, boolean parseRetain)
    {
        super(params, payloadBytes, cursor, parent, parseLazy, parseRetain, payloadBytes.length);
    }

    public MasternodeSignature(byte [] signature)
    {
        super();
        bytes = new byte[signature.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
    }

    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
            length = calcLength(payload, offset);
            cursor = offset + length;
        }
    }
    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;// + 4;
        varint = new VarInt(buf, cursor);
        long len = varint.value;
        len += varint.getOriginalSizeInBytes();
        cursor += len;

        return cursor - offset;
    }

    public int calculateMessageSizeInBytes()
    {
        return VarInt.sizeOf(bytes.length) + bytes.length;
    }
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        cursor = offset;

        bytes = readByteArray();

        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(new VarInt(bytes.length).encode());
        stream.write(bytes);
    }

    public String toString()
    {
        return "sig: " + Utils.HEX.encode(bytes);

    }

    public byte [] getBytes() { maybeParse(); return bytes; }

    public boolean equals(Object o)
    {
        maybeParse();
       MasternodeSignature key = (MasternodeSignature)o;
        if(key.bytes.length == this.bytes.length)
        {
            if(Arrays.equals(key.bytes, this.bytes) == true)
                return true;
        }
        return false;
    }

    MasternodeSignature duplicate()
    {
        MasternodeSignature copy = new MasternodeSignature(params, getBytes(), 0);

        return copy;
    }

}
