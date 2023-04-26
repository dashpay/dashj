package org.bitcoinj.evolution;

import com.google.common.base.Preconditions;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.Pair;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class DeterministicMasternodeList extends Message {

    private Sha256Hash blockHash;
    private long height;
    HashMap<Sha256Hash, DeterministicMasternode> mnMap;
    HashMap<Sha256Hash, Pair<Sha256Hash, Integer>> mnUniquePropertyMap;

    DeterministicMasternodeList(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    DeterministicMasternodeList(DeterministicMasternodeList other) {
        this.blockHash = other.blockHash;
        this.height = other.height;

        mnMap = new HashMap<Sha256Hash, DeterministicMasternode>(other.mnMap.size());
        for(Map.Entry<Sha256Hash, DeterministicMasternode> entry : mnMap.entrySet()) {
            mnMap.put(entry.getKey(), new DeterministicMasternode(entry.getValue()));
        }
    }

    @Override
    protected void parse() throws ProtocolException {
        blockHash = readHash();
        height = (int)readUint32();
        int size = (int)readVarInt();
        mnMap = new HashMap<Sha256Hash, DeterministicMasternode>(size);
        for(int i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            DeterministicMasternode mn = new DeterministicMasternode(params, payload, cursor);
            cursor += mn.getMessageSize();
            mnMap.put(hash, mn);
        }

        size = (int)readVarInt();
        mnUniquePropertyMap = new HashMap<Sha256Hash, Pair<Sha256Hash, Integer>>(size);
        for(long i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            Sha256Hash first = readHash();
            int second = (int)readUint32();
            mnUniquePropertyMap.put(hash, new Pair<Sha256Hash, Integer>(first, second));
        }
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(blockHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(height, stream);

        stream.write(new VarInt(mnMap.size()).encode());
        for(Map.Entry<Sha256Hash, DeterministicMasternode> entry : mnMap.entrySet()) {
            stream.write(entry.getKey().getReversedBytes());
            entry.getValue().bitcoinSerializeToStream(stream);
        }
        stream.write(new VarInt(mnMap.size()).encode());
        for(Map.Entry<Sha256Hash, Pair<Sha256Hash, Integer>> entry : mnUniquePropertyMap.entrySet()) {
            stream.write(entry.getKey().getReversedBytes());
            stream.write(entry.getValue().getFirst().getReversedBytes());
            Utils.uint32ToByteStreamLE(entry.getValue().getSecond().intValue(), stream);
        }
    }

    DeterministicMasternodeList applyDiff(DeterministicMasternodeListDiff diff)
    {
        Preconditions.checkState(diff.prevBlockHash.equals(blockHash) && diff.height == height + 1);

        DeterministicMasternodeList result = new DeterministicMasternodeList(this);
        result.blockHash = diff.blockHash;
        result.height = diff.height;

        for (Sha256Hash hash : diff.removedMNs) {
            result.removeMN(hash);
        }
        for (Map.Entry<Sha256Hash, DeterministicMasternode> entry : diff.addedMNs.entrySet()) {
            result.addMN(entry.getValue());
        }
        for (Map.Entry<Sha256Hash, DeterministicMasternodeState> entry : diff.updatedMNs.entrySet()) {
            result.updateMN(entry.getKey(), entry.getValue());
        }

        return result;
    }

    void addMN(DeterministicMasternode dmn)
    {
        Preconditions.checkState(!mnMap.containsKey(dmn.proRegTxHash));
        mnMap.put(dmn.proRegTxHash, dmn);
        addUniqueProperty(dmn, dmn.state.address);
        addUniqueProperty(dmn, dmn.state.keyIDOwner);
        addUniqueProperty(dmn, dmn.state.pubKeyOperator);
    }
    void updateMN(Sha256Hash proTxHash, DeterministicMasternodeState state)
    {
        DeterministicMasternode oldDmn = mnMap.get(proTxHash);
        Preconditions.checkState(oldDmn != null);
        DeterministicMasternode dmn = oldDmn;
        DeterministicMasternodeState oldState = dmn.state;
        dmn.state = state;
        mnMap.put(proTxHash, dmn);

        updateUniqueProperty(dmn, oldState.address, state.address);
        updateUniqueProperty(dmn, oldState.keyIDOwner, state.keyIDOwner);
        updateUniqueProperty(dmn, oldState.pubKeyOperator, state.pubKeyOperator);
    }
    void removeMN(Sha256Hash proTxHash)
    {
        DeterministicMasternode dmn = getMN(proTxHash);
        assert(dmn != null);
        deleteUniqueProperty(dmn, dmn.state.address);
        deleteUniqueProperty(dmn, dmn.state.keyIDOwner);
        deleteUniqueProperty(dmn, dmn.state.pubKeyOperator);
        mnMap.remove(proTxHash);
    }

    public DeterministicMasternode getMN(Sha256Hash proTxHash)
    {
        DeterministicMasternode p = mnMap.get(proTxHash);
        if (p == null) {
            return null;
        }
        return p;
    }


    @Deprecated
    <T extends Message> void addUniqueProperty(DeterministicMasternode dmn, T value)
    {
        Sha256Hash hash = value.getHash();
        int i = 1;
        Pair<Sha256Hash, Integer> oldEntry = mnUniquePropertyMap.get(hash);
        assert(oldEntry == null || oldEntry.getFirst().equals(dmn.proRegTxHash));
        if(oldEntry != null)
            i = oldEntry.getSecond() + 1;
        Pair<Sha256Hash, Integer> newEntry = new Pair(dmn.proRegTxHash, i);

        mnUniquePropertyMap.put(hash, newEntry);
    }
    @Deprecated
    <T extends Message>
    void deleteUniqueProperty(DeterministicMasternode dmn, T oldValue)
    {
        Sha256Hash oldHash = oldValue.getHash();
        Pair<Sha256Hash, Integer> p = mnUniquePropertyMap.get(oldHash);
        assert(p != null && p.getFirst() == dmn.proRegTxHash);
        if (p.getSecond() == 1) {
            mnUniquePropertyMap.remove(oldHash);
        } else {
            mnUniquePropertyMap.put(oldHash, new Pair<Sha256Hash, Integer>(dmn.proRegTxHash, p.getSecond() - 1));
        }
    }
    @Deprecated
    <T extends Message>
    void updateUniqueProperty(DeterministicMasternode dmn, T oldValue, T newValue)
    {
        if (oldValue == newValue) {
            return;
        }
        deleteUniqueProperty(dmn, oldValue);
        addUniqueProperty(dmn, newValue);
    }
}
