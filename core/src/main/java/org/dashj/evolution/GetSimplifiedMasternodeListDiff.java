package org.dashj.evolution;

import org.dashj.core.Message;
import org.dashj.core.NetworkParameters;
import org.dashj.core.ProtocolException;
import org.dashj.core.Sha256Hash;

import java.io.IOException;
import java.io.OutputStream;

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
}
