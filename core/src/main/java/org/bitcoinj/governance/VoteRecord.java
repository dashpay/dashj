package org.bitcoinj.governance;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by Eric on 5/24/2018.
 */
public class VoteRecord extends Message {
    public TreeMap<Integer, VoteInstance> mapInstances;


    public VoteRecord(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    protected void parse() throws ProtocolException {
        int size = (int)readVarInt();
        mapInstances = new TreeMap<Integer, VoteInstance>();
        for(int i = 0; i < size; ++i) {
            int value = (int)readUint32();
            VoteInstance voteInstance = new VoteInstance(params, payload, cursor);
            cursor += voteInstance.getMessageSize();
            mapInstances.put(value, voteInstance);
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(mapInstances.size()).encode());
        for(Map.Entry<Integer, VoteInstance> entry : mapInstances.entrySet()) {
            Utils.uint32ToByteStreamLE(entry.getKey(), stream);
            entry.getValue().bitcoinSerialize(stream);
        }
    }
}
