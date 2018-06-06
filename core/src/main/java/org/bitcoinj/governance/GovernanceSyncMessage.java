package org.bitcoinj.governance;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by HashEngineering on 2/12/2017.
 */
public class GovernanceSyncMessage extends Message {

    Sha256Hash prop;
    BloomFilter bloomFilter;

    public GovernanceSyncMessage(NetworkParameters params) {
        super(params);
        prop = Sha256Hash.ZERO_HASH;
    }

    public GovernanceSyncMessage(NetworkParameters params, Sha256Hash prop) {
        super();
        this.prop = prop;
    }

    public GovernanceSyncMessage(NetworkParameters params, Sha256Hash prop, BloomFilter bloomFilter) {
        super();
        this.prop = prop;
        this.bloomFilter = bloomFilter;
    }

    public GovernanceSyncMessage(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    protected void parse()
    {
        prop = readHash();
        if(protocolVersion >= GovernanceManager.GOVERNANCE_FILTER_PROTO_VERSION) {
            bloomFilter = new BloomFilter(params, payload);
            offset += bloomFilter.getMessageSize();
        }
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(prop.getReversedBytes());
        /*if(protocolVersion >= GovernanceManager.GOVERNANCE_FILTER_PROTO_VERSION)*/ {
            if (bloomFilter != null) //TODO:  This may be a bug
                bloomFilter.bitcoinSerialize(stream);
            else {
                stream.write(new VarInt(0).encode());
                //stream.write(data);
                Utils.uint32ToByteStreamLE(0, stream);
                Utils.uint32ToByteStreamLE(0, stream);
                stream.write((byte)0);
            }
        }
    }
}

