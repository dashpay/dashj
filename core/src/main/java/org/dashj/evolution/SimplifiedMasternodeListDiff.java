package org.dashj.evolution;

import org.dashj.core.*;
import org.dashj.quorums.FinalCommitment;
import org.dashj.utils.Pair;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import static org.dashj.core.Sha256Hash.hashTwice;

public class SimplifiedMasternodeListDiff extends Message {
    public Sha256Hash prevBlockHash;
    public Sha256Hash blockHash;
    PartialMerkleTree cbTxMerkleTree;
    Transaction coinBaseTx;
    protected HashSet<Sha256Hash> deletedMNs;
    protected ArrayList<SimplifiedMasternodeListEntry> mnList;

    protected ArrayList<Pair<Integer, Sha256Hash>> deletedQuorums;
    protected ArrayList<FinalCommitment> newQuorums;


    public SimplifiedMasternodeListDiff(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        prevBlockHash = readHash();
        blockHash = readHash();

        cbTxMerkleTree = new PartialMerkleTree(params, payload, cursor);
        cursor += cbTxMerkleTree.getMessageSize();

        coinBaseTx = new Transaction(params, payload, cursor);
        cursor += coinBaseTx.getMessageSize();

        int size = (int)readVarInt();
        deletedMNs = new HashSet<Sha256Hash>(size);
        for(int i = 0; i < size; ++i) {
            deletedMNs.add(readHash());
        }

        size = (int)readVarInt();
        mnList = new ArrayList<SimplifiedMasternodeListEntry>(size);
        for(int i = 0; i < size; ++i)
        {
            SimplifiedMasternodeListEntry mn = new SimplifiedMasternodeListEntry(params, payload, cursor);
            cursor += mn.getMessageSize();
            mnList.add(mn);
        }

        //0.14 format include quorum information
        if(payload.length > cursor - offset) {
            size = (int)readVarInt();
            deletedQuorums = new ArrayList<Pair<Integer, Sha256Hash>>(size);
            for(int i = 0; i < size; ++i) {
                deletedQuorums.add(new Pair((int)readBytes(1)[0], readHash()));
            }

            size = (int)readVarInt();
            newQuorums = new ArrayList<FinalCommitment>(size);
            for(int i = 0; i < size; ++i) {
                FinalCommitment commitment = new FinalCommitment(params, payload, cursor);
                cursor += commitment.getMessageSize();
                newQuorums.add(commitment);

            }
        }

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(prevBlockHash.getReversedBytes());
        stream.write(blockHash.getReversedBytes());

        cbTxMerkleTree.bitcoinSerializeToStream(stream);
        coinBaseTx.bitcoinSerialize(stream);

        stream.write(new VarInt(deletedMNs.size()).encode());
        for(Sha256Hash entry : deletedMNs) {
            stream.write(entry.getReversedBytes());
        }

        stream.write(new VarInt(mnList.size()).encode());
        for(SimplifiedMasternodeListEntry entry : mnList) {
            entry.bitcoinSerializeToStream(stream);
        }
    }

    public boolean hasChanges() {
        return !mnList.isEmpty() || !deletedMNs.isEmpty();
    }

    boolean verify() {
        //check that coinbase is in the merkle root
        return true;
    }

    @Override
    public String toString() {
        return "Simplified MNList Diff:  adding " + mnList.size() + " and removing " + deletedMNs.size() + " masternodes" +
                (coinBaseTx.getExtraPayloadObject().getVersion() >= 2 ? (" while adding " + newQuorums.size() + " and removing " + deletedQuorums.size() + " quorums") : "");
    }

    public Transaction getCoinBaseTx() {
        return coinBaseTx;
    }

    public ArrayList<Pair<Integer, Sha256Hash>> getDeletedQuorums() {
        return deletedQuorums;
    }

    public ArrayList<FinalCommitment> getNewQuorums() {
        return newQuorums;
    }

    public boolean hasQuorumChanges() {
        return newQuorums.size() + deletedQuorums.size() > 0;
    }

    public boolean hasMasternodeListChanges() {
        return mnList.size() + deletedMNs.size() > 0;
    }
}
