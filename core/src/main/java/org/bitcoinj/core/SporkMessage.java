package org.bitcoinj.core;

import org.bitcoinj.utils.Threading;
import org.darkcoinj.DarkSend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.hashengineering.crypto.X11.x11Digest;

/**
 * Created by Eric on 2/8/2015.
 */
public class SporkMessage extends Message{

    MasternodeSignature sig;
    int nSporkID;
    long nValue;
    long nTimeSigned;

    static int HASH_SIZE = 20;


    public SporkMessage(NetworkParameters params) { super(params);}

    public SporkMessage(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
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

        int cursor = offset;

        //vin
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        //MasternodeAddress address;
        cursor += MasternodeAddress.MESSAGE_SIZE;
        //PublicKey pubkey;
        cursor += PublicKey.calcLength(buf, cursor);

        //PublicKey pubkey2;
        cursor += PublicKey.calcLength(buf, cursor);

        // byte [] sig;
        cursor += MasternodeSignature.calcLength(buf, cursor);

        cursor += 4 + 8 + 8;
        cursor += MasternodeSignature.calcLength(buf, cursor);

        return cursor - offset;
    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;

        nSporkID = (int)readUint32();

        nValue = readInt64();

        nTimeSigned = readInt64();

        sig = new MasternodeSignature(params, payload, cursor);
        cursor += sig.getMessageSize();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        Utils.uint32ToByteStreamLE(nSporkID, stream);
        Utils.int64ToByteStreamLE(nValue, stream);
        Utils.int64ToByteStreamLE(nValue, stream);

        sig.bitcoinSerialize(stream);
    }

    @Override
    public Sha256Hash getHash()
    {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(HASH_SIZE);
            Utils.uint32ToByteStreamLE(nSporkID, bos);
            Utils.int64ToByteStreamLE(nValue, bos);
            Utils.int64ToByteStreamLE(nValue, bos);
            return Sha256Hash.wrapReversed(x11Digest(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }
}
