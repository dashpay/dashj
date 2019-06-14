package org.bitcoinj.evolution;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class DeterministicMasternodeListDiff extends Message {
    public Sha256Hash prevBlockHash;
    public Sha256Hash blockHash;
    public long height;
    HashMap<Sha256Hash, DeterministicMasternode> addedMNs;
    HashMap<Sha256Hash, DeterministicMasternodeState> updatedMNs;
    HashSet<Sha256Hash> removedMNs;

    DeterministicMasternodeListDiff(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        prevBlockHash = readHash();
        blockHash = readHash();
        height = readUint32();
        int size = (int)readVarInt();
        addedMNs = new HashMap<Sha256Hash, DeterministicMasternode>(size);
        for(int i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            DeterministicMasternode mn = new DeterministicMasternode(params, payload, cursor);
            cursor += mn.getMessageSize();
            addedMNs.put(hash, mn);
        }
        size = (int)readVarInt();
        updatedMNs = new HashMap<Sha256Hash, DeterministicMasternodeState>(size);
        for(int i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            DeterministicMasternodeState state = new DeterministicMasternodeState(params, payload, cursor);
            cursor += state.getMessageSize();
            updatedMNs.put(hash, state);
        }
        size = (int)readVarInt();
        removedMNs = new HashSet<Sha256Hash>(size);
        for(int i = 0; i < size; ++i) {
            removedMNs.add(readHash());
        }
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(prevBlockHash.getReversedBytes());
        stream.write(blockHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(height, stream);
        stream.write(new VarInt(addedMNs.size()).encode());
        for(Map.Entry<Sha256Hash, DeterministicMasternode> entry : addedMNs.entrySet()) {
            stream.write(entry.getKey().getReversedBytes());
            entry.getValue().bitcoinSerializeToStream(stream);
        }

        stream.write(new VarInt(updatedMNs.size()).encode());
        for(Map.Entry<Sha256Hash, DeterministicMasternodeState> entry : updatedMNs.entrySet()) {
            stream.write(entry.getKey().getReversedBytes());
            entry.getValue().bitcoinSerializeToStream(stream);
        }

        stream.write(new VarInt(removedMNs.size()).encode());
        for(Sha256Hash entry : removedMNs) {
            stream.write(entry.getReversedBytes());
        }

    }

    public boolean hasChanges() {
        return !addedMNs.isEmpty() || !updatedMNs.isEmpty() || !removedMNs.isEmpty();
    }


}
