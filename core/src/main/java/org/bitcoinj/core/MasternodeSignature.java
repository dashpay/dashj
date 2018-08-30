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

    /*public MasternodeSignature(NetworkParameters params, byte[] payloadBytes, int cursor, Message parent, boolean parseLazy, boolean parseRetain)
    {
        super(params, payloadBytes, cursor, parent, parseLazy, parseRetain, payloadBytes.length);
    }*/

    public MasternodeSignature(byte [] signature)
    {
        bytes = new byte[signature.length];
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        length = VarInt.sizeOf(bytes.length) + bytes.length;
    }

    public MasternodeSignature(MasternodeSignature other) {
        super(other.getParams());
        bytes = new byte[other.bytes.length];
        System.arraycopy(other.bytes, 0, bytes, 0, other.bytes.length);
        length = other.getMessageSize();
    }

    public static MasternodeSignature createEmpty() {
        return new MasternodeSignature(new byte[0]);
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
    protected void parse() throws ProtocolException {

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
        return "sig: " + Utils.HEX.encode(Utils.reverseBytes(bytes));

    }

    public byte [] getBytes() { return bytes; }

    public boolean equals(Object o)
    {
       MasternodeSignature key = (MasternodeSignature)o;
        if(key.bytes.length == this.bytes.length)
        {
            if(Arrays.equals(key.bytes, this.bytes) == true)
                return true;
        }
        return false;
    }

    public MasternodeSignature duplicate()
    {
        MasternodeSignature copy = new MasternodeSignature(getBytes());

        return copy;
    }

    public boolean isEmpty() {
        return bytes.length == 0;
    }

    public void clear() {
        bytes = new byte[0];
    }
}
