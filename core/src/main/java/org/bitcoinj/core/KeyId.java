package org.bitcoinj.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Created by Hash Engineering on 8/25/2018.
 */
public class KeyId extends ChildMessage {
    public static final int MESSAGE_SIZE = 20;

    byte [] bytes;


    public KeyId(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    public KeyId(byte [] key)
    {
        assert(key.length == 20);
        bytes = new byte[key.length];
        System.arraycopy(key, 0, bytes, 0, key.length);
    }

    protected static int calcLength(byte[] buf, int offset) {
        return MESSAGE_SIZE;
    }

    public int calculateMessageSizeInBytes()
    {
        return bytes.length;
    }

    @Override
    protected void parse() throws ProtocolException {
        bytes = readBytes(MESSAGE_SIZE);
        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(bytes);
    }

    public String toString()
    {
        return "key id: " + Utils.HEX.encode(bytes);
    }

    public byte [] getBytes() { return bytes; }

    public boolean equals(Object o)
    {
       KeyId key = (KeyId)o;
        if(key.bytes.length == this.bytes.length)
        {
            if(Arrays.equals(key.bytes, this.bytes) == true)
                return true;
        }
        return false;
    }

    public KeyId duplicate()
    {
        KeyId copy = new KeyId(getBytes());
        return copy;
    }

    Address getAddress(NetworkParameters params) {
        return Address.fromP2SHHash(params, bytes);
    }

    @Override
    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(MESSAGE_SIZE);
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

}
