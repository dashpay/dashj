package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import static org.bitcoinj.core.SporkManager.SPORK_6_NEW_SIGS;

/**
 * Created by Hash Engineering on 2/8/2015.
 */
public class SporkMessage extends Message{

    private static final Logger log = LoggerFactory.getLogger(SporkMessage.class);

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
    protected void parse() throws ProtocolException {


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
        Utils.int64ToByteStreamLE(nTimeSigned, stream);
        sig.bitcoinSerialize(stream);
    }

    @Override
    public Sha256Hash getHash()
    {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(HASH_SIZE);
            Utils.uint32ToByteStreamLE(nSporkID, bos);
            Utils.int64ToByteStreamLE(nValue, bos);
            Utils.int64ToByteStreamLE(nTimeSigned, bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    public Sha256Hash getSignatureHash() {
        return getHash();
    }

    boolean checkSignature(byte [] publicKeyId)
    {
        StringBuilder errorMessage = new StringBuilder();

        if(Context.get().sporkManager.isSporkActive(SPORK_6_NEW_SIGS)) {
            Sha256Hash hash = getSignatureHash();
            if(!HashSigner.verifyHash(Sha256Hash.wrapReversed(hash.getBytes()), publicKeyId, sig, errorMessage)) {
                // Note: unlike for many other messages when SPORK_6_NEW_SIGS is ON sporks with sigs in old format
                // and newer timestamps should not be accepted, so if we failed here - that's it
                log.error("CSporkMessage::CheckSignature -- VerifyHash() failed, error: {}", errorMessage);
                return false;
            }
        } else {
            String strMessage = "" + nSporkID + nValue + nTimeSigned;

            if (!MessageSigner.verifyMessage(publicKeyId, sig, strMessage, errorMessage)) {
                Sha256Hash hash = getSignatureHash();
                if (!HashSigner.verifyHash(Sha256Hash.wrapReversed(hash.getBytes()), publicKeyId, sig, errorMessage)) {
                    log.error("CSporkMessage::CheckSignature -- VerifyHash() failed, error: {}", errorMessage);
                    return false;
                }
            }
        }
        return true;
    }

    public boolean sign(ECKey key) {
        /*if (!key.IsValid()) {
            LogPrintf("CSporkMessage::Sign -- signing key is not valid\n");
            return false;
        }*/

        PublicKey pubKey = new PublicKey(key.getPubKey());
        StringBuilder strError = new StringBuilder();

        if (Context.get().sporkManager.isSporkActive(SPORK_6_NEW_SIGS)) {
            Sha256Hash hash = getSignatureHash();

            sig = HashSigner.signHash(hash, key);
            if (sig == null) {
                log.error("CSporkMessage::Sign -- SignHash() failed");
                return false;
            }

            if (!HashSigner.verifyHash(hash, pubKey, sig, strError)) {
                log.error("CSporkMessage::Sign -- VerifyHash() failed, error: %s\n", strError);
                return false;
            }
        } else {
            String strMessage = "" + nSporkID + nValue + nTimeSigned;

            if (null == (sig = MessageSigner.signMessage(strMessage, key))) {
                log.error("CSporkMessage::Sign -- SignMessage() failed\n");
                return false;
            }

            if (!MessageSigner.verifyMessage(pubKey, sig, strMessage, strError)) {
                log.error("CSporkMessage::Sign -- VerifyMessage() failed, error: %s\n", strError);
                return false;
            }
        }

        return true;
    }

    public int getSporkID() {
        return nSporkID;
    }

    public long getValue() {
        return nValue;
    }

    public long getTimeSigned() {
        return nTimeSigned;
    }

    public MasternodeSignature getSignature() {
        return sig;
    }
}
