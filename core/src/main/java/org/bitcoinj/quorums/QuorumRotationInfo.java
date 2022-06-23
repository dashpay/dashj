/*
 * Copyright 2021 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.quorums;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.evolution.AbstractDiffMessage;
import org.bitcoinj.evolution.SimplifiedMasternodeListDiff;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

public class QuorumRotationInfo extends AbstractDiffMessage {

    private static final Logger log = LoggerFactory.getLogger(QuorumRotationInfo.class);

    QuorumSnapshot quorumSnapshotAtHMinusC;
    QuorumSnapshot quorumSnapshotAtHMinus2C;
    QuorumSnapshot quorumSnapshotAtHMinus3C;
    SimplifiedMasternodeListDiff mnListDiffTip;
    SimplifiedMasternodeListDiff mnListDiffAtH;
    SimplifiedMasternodeListDiff mnListDiffAtHMinusC;
    SimplifiedMasternodeListDiff mnListDiffAtHMinus2C;
    SimplifiedMasternodeListDiff mnListDiffAtHMinus3C;

    boolean extraShare;
    QuorumSnapshot quorumSnapshotAtHMinus4C;
    SimplifiedMasternodeListDiff mnListDiffAtHMinus4C;

    ArrayList<FinalCommitment> lastCommitmentPerIndex;
    ArrayList<QuorumSnapshot> quorumSnapshotList;
    ArrayList<SimplifiedMasternodeListDiff> mnListDiffLists;

    QuorumRotationInfo(NetworkParameters params) {
        super(params);
    }

    public QuorumRotationInfo(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        quorumSnapshotAtHMinusC = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinusC.getMessageSize();
        quorumSnapshotAtHMinus2C = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinus2C.getMessageSize();
        quorumSnapshotAtHMinus3C = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinus3C.getMessageSize();

        mnListDiffTip = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffTip.getMessageSize();
        mnListDiffAtH = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffAtH.getMessageSize();
        mnListDiffAtHMinusC = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffAtHMinusC.getMessageSize();
        mnListDiffAtHMinus2C = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffAtHMinus2C.getMessageSize();
        mnListDiffAtHMinus3C = new SimplifiedMasternodeListDiff(params, payload, cursor);
        cursor += mnListDiffAtHMinus3C.getMessageSize();

        // extra share?
        extraShare = readBytes(1)[0] == 1;
        if (extraShare) {
            quorumSnapshotAtHMinus4C = new QuorumSnapshot(params, payload, cursor);
            cursor += quorumSnapshotAtHMinus4C.getMessageSize();
            mnListDiffAtHMinus4C = new SimplifiedMasternodeListDiff(params, payload, cursor);
            cursor += mnListDiffAtHMinus4C.getMessageSize();
        }

        int size = (int)readVarInt();
        lastCommitmentPerIndex = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            FinalCommitment commitment = new FinalCommitment(params, payload, cursor);
            cursor += commitment.getMessageSize();
            lastCommitmentPerIndex.add(commitment);
        }

        size = (int)readVarInt();
        quorumSnapshotList = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            QuorumSnapshot snapshot = new QuorumSnapshot(params, payload, cursor);
            cursor += snapshot.getMessageSize();
            quorumSnapshotList.add(snapshot);
        }

        size = (int)readVarInt();
        mnListDiffLists = new ArrayList<>(size);
        for (int i = 0; i < size; ++i) {
            SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(params, payload, cursor);
            cursor += mnlistdiff.getMessageSize();
            mnListDiffLists.add(mnlistdiff);
        }

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        quorumSnapshotAtHMinusC.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus2C.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus3C.bitcoinSerialize(stream);

        mnListDiffTip.bitcoinSerialize(stream);
        mnListDiffAtH.bitcoinSerialize(stream);
        mnListDiffAtHMinusC.bitcoinSerialize(stream);
        mnListDiffAtHMinus2C.bitcoinSerialize(stream);
        mnListDiffAtHMinus3C.bitcoinSerialize(stream);

        // extra share?
        stream.write(extraShare ? 1 : 0);
        if (extraShare) {
            quorumSnapshotAtHMinus4C.bitcoinSerialize(stream);
            mnListDiffAtHMinus4C.bitcoinSerialize(stream);
        }

        stream.write(new VarInt(lastCommitmentPerIndex.size()).encode());
        for (FinalCommitment commitment : lastCommitmentPerIndex) {
            commitment.bitcoinSerializeToStream(stream);
        }

        stream.write(new VarInt(quorumSnapshotList.size()).encode());
        for (QuorumSnapshot snapshot : quorumSnapshotList) {
            snapshot.bitcoinSerialize(stream);
        }

        stream.write(new VarInt(mnListDiffLists.size()).encode());
        for (SimplifiedMasternodeListDiff mnlistdiff : mnListDiffLists) {
            mnlistdiff.bitcoinSerialize(stream);
        }
    }

    public SimplifiedMasternodeListDiff getMnListDiffTip() {
        return mnListDiffTip;
    }

    public SimplifiedMasternodeListDiff getMnListDiffAtH() {
        return mnListDiffAtH;
    }

    public SimplifiedMasternodeListDiff getMnListDiffAtHMinusC() {
        return mnListDiffAtHMinusC;
    }

    public SimplifiedMasternodeListDiff getMnListDiffAtHMinus2C() {
        return mnListDiffAtHMinus2C;
    }

    public SimplifiedMasternodeListDiff getMnListDiffAtHMinus3C() {
        return mnListDiffAtHMinus3C;
    }

    public SimplifiedMasternodeListDiff getMnListDiffAtHMinus4C() {
        return mnListDiffAtHMinus4C;
    }

    public QuorumSnapshot getQuorumSnapshotAtHMinusC() {
        return quorumSnapshotAtHMinusC;
    }

    public QuorumSnapshot getQuorumSnapshotAtHMinus2C() {
        return quorumSnapshotAtHMinus2C;
    }

    public QuorumSnapshot getQuorumSnapshotAtHMinus3C() {
        return quorumSnapshotAtHMinus3C;
    }

    public QuorumSnapshot getQuorumSnapshotAtHMinus4C() {
        return quorumSnapshotAtHMinus4C;
    }

    public ArrayList<FinalCommitment> getLastCommitmentPerIndex() {
        return lastCommitmentPerIndex;
    }

    public ArrayList<SimplifiedMasternodeListDiff> getMnListDiffLists() {
        return mnListDiffLists;
    }

    public ArrayList<QuorumSnapshot> getQuorumSnapshotList() {
        return quorumSnapshotList;
    }

    @Override
    public String toString() {
        return "QuorumRotationInfo{" +
                ", quorumSnapshotAtHMinusC=" + quorumSnapshotAtHMinusC +
                ", quorumSnapshotAtHMinus2C=" + quorumSnapshotAtHMinus2C +
                ", quorumSnapshotAtHMinus3C=" + quorumSnapshotAtHMinus3C +
                ", mnListDiffTip=" + mnListDiffTip +
                ", mnListDiffAtH=" + mnListDiffAtH +
                ", mnListDiffAtHMinusC=" + mnListDiffAtHMinusC +
                ", mnListDiffAtHMinus2C=" + mnListDiffAtHMinus2C +
                ", mnListDiffAtHMinus3C=" + mnListDiffAtHMinus3C +
                '}';
    }

    private static int getHeight(Sha256Hash hash, AbstractBlockChain chain) {
        try {
            StoredBlock block = chain.getBlockStore().get(hash);
            return block != null ? block.getHeight() : -1;
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    public String toString(AbstractBlockChain chain) {
        BlockStore blockStore = chain.getBlockStore();
        StringBuilder builder = new StringBuilder();
        builder.append("QuorumRotationInfo{" +
                ",\n quorumSnapshotAtHMinusC=" + quorumSnapshotAtHMinusC +
                ",\n quorumSnapshotAtHMinus2C=" + quorumSnapshotAtHMinus2C +
                ",\n quorumSnapshotAtHMinus3C=" + quorumSnapshotAtHMinus3C +
                ",\n mnListDiffTip=" + mnListDiffTip.toString(blockStore) +
                ",\n mnListDiffAtH=" + mnListDiffAtH.toString(blockStore) +
                ",\n mnListDiffAtHMinusC=" + mnListDiffAtHMinusC.toString(blockStore) +
                ",\n mnListDiffAtHMinus2C=" + mnListDiffAtHMinus2C.toString(blockStore) +
                ",\n mnListDiffAtHMinus3C=" + mnListDiffAtHMinus3C.toString(blockStore) +
                ",\n mnListDiffAtHMinus4C=" + mnListDiffAtHMinus4C.toString(blockStore) +
                "------------------------------\n");
        for (FinalCommitment commitment : lastCommitmentPerIndex) {
            builder.append("lastQuorum: ").append(getHeight(commitment.quorumHash, chain)).append(" ").append(commitment).append(":").append("\n");
        }

        for (QuorumSnapshot snapshot : quorumSnapshotList) {
            builder.append("snapshot: ").append(snapshot).append("\n");
        }

        for (SimplifiedMasternodeListDiff mnlistdiff : mnListDiffLists) {
            builder.append("mnlistdiff: ").append(mnlistdiff.toString(chain.getBlockStore())).append("\n");
        }
        builder.append('}');
        return builder.toString();
    }

    // these are for tests so they have package level access
    void setQuorumSnapshotAtHMinusC(QuorumSnapshot quorumSnapshotAtHMinusC) {
        this.quorumSnapshotAtHMinusC = quorumSnapshotAtHMinusC;
    }

    void setQuorumSnapshotAtHMinus2C(QuorumSnapshot quorumSnapshotAtHMinusC) {
        this.quorumSnapshotAtHMinus2C = quorumSnapshotAtHMinusC;
    }

    public void setQuorumSnapshotAtHMinus3C(QuorumSnapshot quorumSnapshotAtHMinus3C) {
        this.quorumSnapshotAtHMinus3C = quorumSnapshotAtHMinus3C;
    }

    public void setQuorumSnapshotAtHMinus34(QuorumSnapshot quorumSnapshotAtHMinus4C) {
        this.quorumSnapshotAtHMinus4C = quorumSnapshotAtHMinus4C;
    }

    public void setMnListDiffTip(SimplifiedMasternodeListDiff mnListDiffTip) {
        this.mnListDiffTip = mnListDiffTip;
    }

    public void setMnListDiffAtHMinusC(SimplifiedMasternodeListDiff mnListDiffAtHMinusC) {
        this.mnListDiffAtHMinusC = mnListDiffAtHMinusC;
    }

    public void setMnListDiffAtHMinus2C(SimplifiedMasternodeListDiff mnListDiffAtHMinus2C) {
        this.mnListDiffAtHMinus2C = mnListDiffAtHMinus2C;
    }

    public void setMnListDiffAtHMinus3C(SimplifiedMasternodeListDiff mnListDiffAtHMinus3C) {
        this.mnListDiffAtHMinus3C = mnListDiffAtHMinus3C;
    }

    public void setMnListDiffAtHMinus4C(SimplifiedMasternodeListDiff mnListDiffAtHMinus4C) {
        this.mnListDiffAtHMinus4C = mnListDiffAtHMinus4C;
    }

    public boolean hasChanges() {
        return mnListDiffTip.hasChanges() || mnListDiffAtH.hasChanges() || mnListDiffAtHMinusC.hasChanges() ||
                mnListDiffAtHMinus2C.hasChanges() || mnListDiffAtHMinus3C.hasChanges() || mnListDiffAtHMinus4C.hasChanges();
    }

    public void dump(long startHeight, long endHeight) {
        try {
            File dumpFile = new File("qrinfo-" + startHeight + "-" + endHeight + ".dat");
            OutputStream stream = new FileOutputStream(dumpFile);
            stream.write(bitcoinSerialize());
            stream.close();
            log.info("dump successful");
        } catch (FileNotFoundException x) {
            log.warn("could not dump qrinfo - file not found.");
        } catch (IOException x) {
            // nothing
            log.warn("could not dump qrinfo", x);
        }
    }
}
