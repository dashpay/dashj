package org.bitcoinj.evolution;

import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

public class GetSimplifiedMasternodeListDiff extends Message {

    Sha256Hash baseBlockHash;
    Sha256Hash blockHash;

    public static int MESSAGE_SIZE = 64;

    public GetSimplifiedMasternodeListDiff(Sha256Hash baseBlockHash, Sha256Hash blockHash) {
        this.baseBlockHash = baseBlockHash;
        this.blockHash = blockHash;
        length = MESSAGE_SIZE;
    }

    public GetSimplifiedMasternodeListDiff(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        baseBlockHash = readHash();
        blockHash = readHash();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(baseBlockHash.getReversedBytes());
        stream.write(blockHash.getReversedBytes());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        GetSimplifiedMasternodeListDiff diff = (GetSimplifiedMasternodeListDiff)obj;
        return diff.blockHash.equals(blockHash) && diff.baseBlockHash.equals(baseBlockHash);
    }

    @Override
    public int hashCode() {
        return new BigInteger(baseBlockHash.getBytes()).add(new BigInteger(blockHash.getBytes())).hashCode();
    }
}
