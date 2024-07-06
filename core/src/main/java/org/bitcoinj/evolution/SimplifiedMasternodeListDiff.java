package org.bitcoinj.evolution;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.quorums.FinalCommitment;
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
    private Sha256Hash prevBlockHash;
    private Sha256Hash blockHash;
    PartialMerkleTree cbTxMerkleTree;
    Transaction coinBaseTx;
    protected HashSet<Sha256Hash> deletedMNs;
    protected ArrayList<SimplifiedMasternodeListEntry> mnList;

    protected ArrayList<Pair<Integer, Sha256Hash>> deletedQuorums;
    protected ArrayList<FinalCommitment> newQuorums;
    protected HashMap<BLSSignature, HashSet<Integer>> quorumsCLSigs;


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
        this.quorumsCLSigs = Maps.newHashMap();
    }

    @Override
    protected void parse() throws ProtocolException {
        if (protocolVersion >= NetworkParameters.ProtocolVersion.MNLISTDIFF_VERSION_ORDER.getBitcoinProtocolVersion()) {
            version = (short) readUint16();
        }
        prevBlockHash = readHash();
        blockHash = readHash();

        cbTxMerkleTree = new PartialMerkleTree(params, payload, cursor);
        cursor += cbTxMerkleTree.getMessageSize();

        coinBaseTx = new Transaction(params, payload, cursor);
        cursor += coinBaseTx.getMessageSize();
        if (protocolVersion >= NetworkParameters.ProtocolVersion.BLS_SCHEME.getBitcoinProtocolVersion() &&
            protocolVersion < NetworkParameters.ProtocolVersion.MNLISTDIFF_VERSION_ORDER.getBitcoinProtocolVersion()) {
            version = (short) readUint16();
        } else if (protocolVersion <= NetworkParameters.ProtocolVersion.BLS_LEGACY.getBitcoinProtocolVersion()) {
            version = 0;
        }

        int size = (int)readVarInt();
        deletedMNs = new HashSet<>(size);
        for(int i = 0; i < size; ++i) {
            deletedMNs.add(readHash());
        }

        size = (int)readVarInt();
        mnList = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            SimplifiedMasternodeListEntry mn = new SimplifiedMasternodeListEntry(params, payload, cursor, protocolVersion);
            cursor += mn.getMessageSize();
            mnList.add(mn);
        }

        // process quorum changes
        size = (int)readVarInt();
        deletedQuorums = new ArrayList<>(size);
        for(int i = 0; i < size; ++i) {
            deletedQuorums.add(new Pair<>((int)readBytes(1)[0], readHash()));
        }

        size = (int)readVarInt();
        newQuorums = new ArrayList<>(size);
        for(int i = 0; i < size; ++i) {
            FinalCommitment commitment = new FinalCommitment(params, payload, cursor);
            cursor += commitment.getMessageSize();
            newQuorums.add(commitment);
        }

        // process quorum ChainlockSignatures
        if (protocolVersion >= NetworkParameters.ProtocolVersion.MNLISTDIFF_CHAINLOCKS.getBitcoinProtocolVersion()) {
            size = (int)readVarInt();
            quorumsCLSigs = new HashMap<>(size);
            for (int i = 0; i < size; ++i) {
                BLSSignature signature = new BLSSignature(params, payload, cursor);
                cursor += signature.getMessageSize();

                int setSize = (int)readVarInt();
                HashSet<Integer> heightSet = new HashSet<>(setSize);
                for (int j = 0; j < setSize; ++j) {
                    int height = readUint16();
                    heightSet.add(height);
                }
                quorumsCLSigs.put(signature, heightSet);
            }
        }

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        if (protocolVersion >= NetworkParameters.ProtocolVersion.MNLISTDIFF_VERSION_ORDER.getBitcoinProtocolVersion()) {
            Utils.uint16ToByteStreamLE(version, stream);
        }
        stream.write(prevBlockHash.getReversedBytes());
        stream.write(blockHash.getReversedBytes());

        cbTxMerkleTree.bitcoinSerializeToStream(stream);
        coinBaseTx.bitcoinSerialize(stream);

        if (protocolVersion >= NetworkParameters.ProtocolVersion.BLS_SCHEME.getBitcoinProtocolVersion() &&
            protocolVersion < NetworkParameters.ProtocolVersion.MNLISTDIFF_VERSION_ORDER.getBitcoinProtocolVersion()) {
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

        // process quorum ChainLockSignatures
        if (protocolVersion >= NetworkParameters.ProtocolVersion.MNLISTDIFF_CHAINLOCKS.getBitcoinProtocolVersion()) {
            stream.write(new VarInt(quorumsCLSigs.size()).encode());
            for (Map.Entry<BLSSignature, HashSet<Integer>> entry : quorumsCLSigs.entrySet()) {
                entry.getKey().bitcoinSerialize(stream);
                HashSet<Integer> heightSet = entry.getValue();
                stream.write(new VarInt(heightSet.size()).encode());
                for (Integer height : heightSet) {
                    Utils.uint16ToByteStreamLE(height, stream);
                }
            }
        }
    }

    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    public boolean hasChanges() {
        return !mnList.isEmpty() || !deletedMNs.isEmpty() || hasQuorumChanges();
    }

    boolean verify() {
        //check that coinbase is in the merkle root
        return true;
    }

    private String getAddRemovedString() {
        return String.format("adding %d and removing %d masternodes%s", mnList.size(), deletedMNs.size(),
                (coinBaseTx.getExtraPayloadObject().getVersion() >= 2 ? (String.format(" while adding %d and removing %d quorums", newQuorums.size(), deletedQuorums.size())) : ""));
    }

    @Override
    public String toString() {
        return String.format("Simplified MNList Diff{ %s }", getAddRemovedString());
    }

    public String toString(DualBlockChain blockChain) {
        int height = -1;
        int prevHeight = -1;
        try {
            height = blockChain.getBlock(blockHash).getHeight();
            prevHeight = blockChain.getBlock(prevBlockHash).getHeight();
        } catch (Exception x) {
            // swallow
        }
        return String.format("Simplified MNList Diff{ %d -> %d/%d; %s }", prevHeight, height, getHeight(), getAddRemovedString());
    }

    public Transaction getCoinBaseTx() {
        return coinBaseTx;
    }

    public List<SimplifiedMasternodeListEntry> getMnList() {
        return mnList;
    }

    public List<Pair<Integer, Sha256Hash>> getDeletedQuorums() {
        return deletedQuorums;
    }

    public List<FinalCommitment> getNewQuorums() {
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

    public HashMap<BLSSignature, HashSet<Integer>> getQuorumsCLSigs() {
        return quorumsCLSigs;
    }
}
