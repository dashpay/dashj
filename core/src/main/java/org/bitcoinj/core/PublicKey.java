package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.security.SignatureException;
import java.util.Arrays;

/**
 * Created by Hash Engineering on 2/10/2015.
 */
public class PublicKey extends ChildMessage {
    private static final Logger log = LoggerFactory.getLogger(PublicKey.class);

    byte [] bytes;
    ECKey key;

    PublicKey(NetworkParameters params)
    {
        super(params);
    }

    public PublicKey(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    public PublicKey(NetworkParameters params, byte[] payloadBytes, int cursor, Message parent, boolean parseLazy, boolean parseRetain)
    {
        super(params, payloadBytes, cursor, parent, parseLazy, parseRetain, payloadBytes.length);
    }
    public PublicKey(byte [] key)
    {
        super();
        bytes = new byte[key.length];
        System.arraycopy(key, 0, bytes, 0, key.length);
        this.key = ECKey.fromPublicOnly(bytes);
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
    @Override
    void parse() throws ProtocolException {
        if(parsed)
            return;

        cursor = offset;

        bytes = readByteArray();
        //this.key = ECKey.fromPublicOnly(bytes);

        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        stream.write(new VarInt(bytes.length).encode());
        stream.write(bytes);
    }

    public String toString()
    {
        return "public key:  " + Utils.HEX.encode(bytes);

    }

    public byte [] getBytes() { maybeParse(); return bytes; }

    public boolean equals(Object o)
    {
        maybeParse();
       PublicKey key = (PublicKey)o;
        if(key.bytes.length == this.bytes.length)
        {
            if(Arrays.equals(key.bytes, this.bytes) == true)
                return true;
        }
        return false;
    }

    PublicKey duplicate()
    {
        PublicKey copy = new PublicKey(getBytes());

        return copy;
    }

    //
    //  This doesn't work.  May not be necessary;  ECKey.verifyMessage handles making a ECDSASignature from the signature
    //
    static public PublicKey recoverCompact(Sha256Hash hash, MasternodeSignature sig) throws SignatureException
    {
        if(sig.getBytes().length != 65)
            throw new SignatureException("signature is wrong size");
        int recid = (sig.getBytes()[0] - 27) & 3;
        boolean comp = ((sig.getBytes()[0] - 27) & 4) != 0;

        ECKey.ECDSASignature esig = ECKey.ECDSASignature.decodeFromDER(sig.getBytes());
        ECKey ecKey = ECKey.recoverFromSignature(recid, esig, hash, comp);



        return new PublicKey (ecKey.getPubKey());
    }

    public byte [] getPublicHash()
    {
        return key.getPubKeyHash();
    }

}
