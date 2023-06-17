package org.bitcoinj.evolution;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.quorums.FinalCommitment;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class SimplifiedMasternodeListDiff extends AbstractDiffMessage {

    private static final Logger log = LoggerFactory.getLogger(SimplifiedMasternodeListDiff.class);
    private static final String SHORT_NAME = "mnlistdiff";
    @Deprecated
    public static final short LEGACY_BLS_VERSION = 1;
    @Deprecated
    public static final short BASIC_BLS_VERSION = 2;
    public static final short CURRENT_VERSION = 1;
    private short version;
    public Sha256Hash prevBlockHash;
    public Sha256Hash blockHash;
    PartialMerkleTree cbTxMerkleTree;
    Transaction coinBaseTx;
    protected HashSet<Sha256Hash> deletedMNs;
    protected ArrayList<SimplifiedMasternodeListEntry> mnList;

    protected ArrayList<Pair<Integer, Sha256Hash>> deletedQuorums;
    protected ArrayList<FinalCommitment> newQuorums;


    public SimplifiedMasternodeListDiff(NetworkParameters params, byte [] payload, int protocolVersion) {
        super(params, payload, 0, protocolVersion);
    }

    public SimplifiedMasternodeListDiff(NetworkParameters params, byte [] payload, int offset, int protocolVersion) {
        super(params, payload, offset, protocolVersion);
    }

    @Override
    protected String getShortName() {
        return SHORT_NAME;
    }

    public SimplifiedMasternodeListDiff(NetworkParameters params, Sha256Hash prevBlockHash, Sha256Hash blockHash,
                                        PartialMerkleTree cbTxMerkleTree, Transaction coinBaseTx,
                                        List<SimplifiedMasternodeListEntry> mnList,
                                        List<FinalCommitment> quorumList,
                                        short version) {
        super(params);
        this.prevBlockHash = prevBlockHash;
        this.blockHash = blockHash;
        this.cbTxMerkleTree = cbTxMerkleTree;
        this.coinBaseTx = coinBaseTx;
        this.deletedMNs = Sets.newHashSet();
        this.mnList = Lists.newArrayList(mnList);
        this.deletedQuorums = Lists.newArrayList();
        this.newQuorums = Lists.newArrayList(quorumList);
        this.version = version;
    }

    @Override
    protected void parse() throws ProtocolException {
        prevBlockHash = readHash();
        blockHash = readHash();

        cbTxMerkleTree = new PartialMerkleTree(params, payload, cursor);
        cursor += cbTxMerkleTree.getMessageSize();

        coinBaseTx = new Transaction(params, payload, cursor);
        cursor += coinBaseTx.getMessageSize();
        if (protocolVersion >= params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_SCHEME)) {
            version = (short) readUint16();
        } else {
            version = CURRENT_VERSION;
        }

        int size = (int)readVarInt();
        deletedMNs = new HashSet<>(size);
        for(int i = 0; i < size; ++i) {
            deletedMNs.add(readHash());
        }

        size = (int)readVarInt();
        mnList = new ArrayList<SimplifiedMasternodeListEntry>(size);
        for(int i = 0; i < size; ++i)
        {
            SimplifiedMasternodeListEntry mn = new SimplifiedMasternodeListEntry(params, payload, cursor, protocolVersion);
            cursor += mn.getMessageSize();
            mnList.add(mn);
        }

        //0.14 format include quorum information
        if(payload.length > cursor - offset) {
            size = (int)readVarInt();
            deletedQuorums = new ArrayList<Pair<Integer, Sha256Hash>>(size);
            for(int i = 0; i < size; ++i) {
                deletedQuorums.add(new Pair<>((int)readBytes(1)[0], readHash()));
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

        if (protocolVersion >= params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_SCHEME)) {
            Utils.uint16ToByteStreamLE(version, stream);
        }

        stream.write(new VarInt(deletedMNs.size()).encode());
        for(Sha256Hash entry : deletedMNs) {
            stream.write(entry.getReversedBytes());
        }

        stream.write(new VarInt(mnList.size()).encode());
        for(SimplifiedMasternodeListEntry entry : mnList) {
            entry.bitcoinSerializeToStream(stream);
        }

        stream.write(new VarInt(deletedQuorums.size()).encode());
        for(Pair<Integer, Sha256Hash> entry : deletedQuorums) {
            stream.write(entry.getFirst());
            stream.write(entry.getSecond().getReversedBytes());
        }

        stream.write(new VarInt(newQuorums.size()).encode());
        for(FinalCommitment entry : newQuorums) {
            entry.bitcoinSerialize(stream);
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

    public String toString(BlockStore blockStore) {
        int height = -1;
        int prevHeight = -1;
        try {
            height = blockStore.get(blockHash).getHeight();
            prevHeight = blockStore.get(prevBlockHash).getHeight();
        } catch (Exception x) {
            // swallow
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Simplified MNList Diff: ").append(prevHeight).append(" -> ").append(height).append("/").append(getHeight())
                .append(" [adding ").append(mnList.size()).append(" and removing ").append(deletedMNs.size()).append(" masternodes")
                .append(coinBaseTx.getExtraPayloadObject().getVersion() >= 2 ? (" while adding " + newQuorums.size() + " and removing " + deletedQuorums.size() + " quorums") : "")
                .append("]");

        return builder.toString();
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

    public long getHeight() {
        return ((CoinbaseTx)getCoinBaseTx().getExtraPayloadObject()).getHeight();
    }

    public short getVersion() {
        return version;
    }

    public boolean hasBasicSchemeKeys() {
        for (SimplifiedMasternodeListEntry entry : mnList) {
            if (entry.version == SimplifiedMasternodeListEntry.BASIC_BLS_VERSION)
                return true;
        }
        return false;
    }
}
