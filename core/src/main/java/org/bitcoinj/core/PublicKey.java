package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;

/**
 * Created by Hash Engineering on 2/10/2015.
 */
public class PublicKey extends ChildMessage {
    private static final Logger log = LoggerFactory.getLogger(PublicKey.class);

    byte [] bytes;
    ECKey key;

    public PublicKey()
    {
        bytes = new byte[1];
    }

    PublicKey(NetworkParameters params)
    {
        super(params);
    }
    void invalidate()
    {
        bytes[0] = (byte)0xFF;
    }

    public PublicKey(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    /*public PublicKey(NetworkParameters params, byte[] payloadBytes, int cursor, Message parent, boolean parseLazy, boolean parseRetain)
    {
        super(params, payloadBytes, cursor, parent, parseLazy, parseRetain, payloadBytes.length);
    }*/
    public PublicKey(byte [] key)
    {
        super();
        bytes = new byte[key.length];
        System.arraycopy(key, 0, bytes, 0, key.length);
        this.key = ECKey.fromPublicOnly(bytes);
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

    public byte [] getBytes() { return bytes; }

    public boolean equals(Object o)
    {
       PublicKey key = (PublicKey)o;
        if(key.bytes.length == this.bytes.length)
        {
            if(Arrays.equals(key.bytes, this.bytes) == true)
                return true;
        }
        return false;
    }

    @Deprecated
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

        //ECKey.ECDSASignature esig = ECKey.ECDSASignature.decodeFromDER(sig.getBytes());
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(sig.getBytes(), 1, 33));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(sig.getBytes(), 33, 65));
        ECKey.ECDSASignature esig = new ECKey.ECDSASignature(r, s);
        ECKey ecKey = ECKey.recoverFromSignature(recid, esig, hash, comp);



        return new PublicKey (ecKey.getPubKey());
    }

    public byte [] getId()
    {
        return getECKey().getPubKeyHash();
    }

    public ECKey getECKey()
    {
        if(key == null) {
            key = ECKey.fromPublicOnly(bytes);
        }
        return key;

    }

}
