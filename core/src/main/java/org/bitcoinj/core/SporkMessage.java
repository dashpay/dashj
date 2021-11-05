/*
 * Copyright 2015 Hash Engineering Solutions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoinj.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SignatureException;

/**
 * Created by Hash Engineering on 2/8/2015.
 *
 */
public class SporkMessage extends Message{

    private static final Logger log = LoggerFactory.getLogger(SporkMessage.class);

    private MasternodeSignature sig;
    private SporkId sporkId;
    private long value;
    private long timeSigned;

    private static final int MESSAGE_SIZE_WITHOUT_SIG = 20;

    public SporkMessage(NetworkParameters params, byte[] payload, int cursor)
    {
        super(params, payload, cursor);
    }

    public SporkMessage(NetworkParameters params, SporkId sporkId, long value, long timeSigned) {
        super(params);
        this.sporkId = sporkId;
        this.value = value;
        this.timeSigned = timeSigned;
    }

    @Override
    protected void parse() throws ProtocolException {
        sporkId = SporkId.fromValue((int)readUint32());
        value = readInt64();
        timeSigned = readInt64();
        sig = new MasternodeSignature(params, payload, cursor);
        cursor += sig.getMessageSize();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(sporkId.value, stream);
        Utils.int64ToByteStreamLE(value, stream);
        Utils.int64ToByteStreamLE(timeSigned, stream);
        sig.bitcoinSerialize(stream);
    }

    /**
     * @return the hash of this message without the signature
     */
    @Override
    public Sha256Hash getHash()
    {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(MESSAGE_SIZE_WITHOUT_SIG);
            Utils.uint32ToByteStreamLE(sporkId.value, bos);
            Utils.int64ToByteStreamLE(value, bos);
            Utils.int64ToByteStreamLE(timeSigned, bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    public Sha256Hash getSignatureHash() {
        return getHash();
    }

    public KeyId getSignerKeyId() {
        ECKey pubkeyFromSig;
        // Harden Spork6 so that it is active on testnet and no other networks
        if (params.getId().equals(NetworkParameters.ID_TESTNET)) {
            try {
                Sha256Hash hash = getSignatureHash();
                pubkeyFromSig = ECKey.signedMessageToKey(Sha256Hash.wrapReversed(hash.getBytes()), sig.getBytes());
            } catch (SecurityException | SignatureException x) {
                return null;
            }
        } else {
            String message = "" + sporkId.value + value + timeSigned;
            byte[] messageBytes = Utils.formatMessageForSigning(message);
            try {
                pubkeyFromSig = ECKey.signedMessageToKey(Sha256Hash.twiceOf(messageBytes), sig.getBytes());
            } catch (SecurityException | SignatureException x) {
                return null;
            }
        }
        return new KeyId(pubkeyFromSig.getPubKeyHash());
    }

    boolean checkSignature(byte [] publicKeyId)
    {
        StringBuilder errorMessage = new StringBuilder();

        // Harden Spork6 so that it is active on testnet and no other networks
        if(params.getId().equals(NetworkParameters.ID_TESTNET)) {
            Sha256Hash hash = getSignatureHash();
            if(!HashSigner.verifyHash(Sha256Hash.wrapReversed(hash.getBytes()), publicKeyId, sig, errorMessage)) {
                // Note: unlike for many other messages when SPORK_6_NEW_SIGS is ON sporks with sigs in old format
                // and newer timestamps should not be accepted, so if we failed here - that's it
                log.error("checkSignature -- verifyHash() failed, error: {}", errorMessage);
                return false;
            }
        } else {
            String strMessage = "" + sporkId.value + value + timeSigned;

            if (!MessageSigner.verifyMessage(publicKeyId, sig, strMessage, errorMessage)) {
                Sha256Hash hash = getSignatureHash();
                if (!HashSigner.verifyHash(Sha256Hash.wrapReversed(hash.getBytes()), publicKeyId, sig, errorMessage)) {
                    log.error("checkSignature -- verifyHash() failed, error: {}", errorMessage);
                    return false;
                }
            }
        }
        return true;
    }

    public SporkId getSporkId() {
        return sporkId;
    }

    public long getValue() {
        return value;
    }

    public long getTimeSigned() {
        return timeSigned;
    }

    public MasternodeSignature getSignature() {
        return sig;
    }
}
