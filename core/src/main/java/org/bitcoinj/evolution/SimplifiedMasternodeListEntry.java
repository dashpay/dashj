package org.bitcoinj.evolution;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;

public class SimplifiedMasternodeListEntry extends ChildMessage {

    Sha256Hash proRegTxHash;
    MasternodeAddress service;
    KeyId keyIdOperator;
    KeyId keyIdVoting;
    boolean isValid;

    SimplifiedMasternodeListEntry(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    SimplifiedMasternodeListEntry(SimplifiedMasternodeListEntry other) {
        proRegTxHash = other.proRegTxHash;
        service = other.service.duplicate();
        keyIdOperator = other.keyIdOperator.duplicate();
        keyIdVoting = other.keyIdVoting.duplicate();
    }

    @Override
    protected void parse() throws ProtocolException {
        proRegTxHash = readHash();
        service = new MasternodeAddress(params, payload, cursor, 0);
        cursor += service.getMessageSize();
        keyIdOperator = new KeyId(params, payload, cursor);
        cursor += keyIdOperator.getMessageSize();
        keyIdVoting = new KeyId(params, payload, cursor);
        cursor += keyIdVoting.getMessageSize();
        isValid = readBytes(1)[0] == 1;
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(proRegTxHash.getReversedBytes());
        service.bitcoinSerialize(stream);
        keyIdOperator.bitcoinSerialize(stream);
        keyIdVoting.bitcoinSerialize(stream);
        stream.write(isValid ? 1 : 0);
    }

    public Sha256Hash calculateHash() {
        return getHash();
    }

    @Override
    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(getMessageSize());
            bitcoinSerializeToStream(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    public String toString() {
        return String.format("SimplifiedMNListEntry(proRegTxHash=%s, service=%s, keyIDOperator=%s, keyIDVoting=%s, isValid="+isValid+")",
            proRegTxHash.toString(), service.toString(),
                keyIdOperator, keyIdVoting);
    }

}
