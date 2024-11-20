/*
 * Copyright 2018 Dash Core Group
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

package org.bitcoinj.evolution;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.utils.MerkleRoot;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Math.min;
import static org.bitcoinj.evolution.SimplifiedMasternodeListDiff.CURRENT_VERSION;

public class SimplifiedMasternodeList extends Message {

    private static final Logger log = LoggerFactory.getLogger(SimplifiedMasternodeList.class);
    private final ReentrantLock lock = Threading.lock("SimplifiedMasternodeList");

    private short version;
    private Sha256Hash blockHash;
    private long height;
    private StoredBlock storedBlock;
    private boolean storedBlockMatchesRequest;
    HashMap<Sha256Hash, SimplifiedMasternodeListEntry> mnMap;

    private CoinbaseTx coinbaseTxPayload;

    public SimplifiedMasternodeList(NetworkParameters params) {
        super(params);
        blockHash = params.getGenesisBlock().getHash();
        height = -1;
        mnMap = new HashMap<>(5000);
        storedBlock = new StoredBlock(params.getGenesisBlock(), BigInteger.ZERO, 0);
        initProtocolVersion();
    }

    private void initProtocolVersion() {
        protocolVersion = params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT);
    }

    SimplifiedMasternodeList(NetworkParameters params, byte [] payload, int offset, int protocolVersion) {
        super(params, payload, offset, protocolVersion);
    }

    SimplifiedMasternodeList(SimplifiedMasternodeList other, short version) {
        super(other.params);
        this.version = version;
        this.blockHash = other.blockHash;
        this.height = other.height;
        mnMap = new HashMap<>(other.mnMap);
        this.storedBlock = other.storedBlock;
        initProtocolVersion();
    }

    SimplifiedMasternodeList(NetworkParameters params, ArrayList<SimplifiedMasternodeListEntry> entries, int protocolVersion) {
        super(params);
        this.protocolVersion = protocolVersion;
        this.version = CURRENT_VERSION;
        this.blockHash = params.getGenesisBlock().getHash();
        this.height = -1;
        mnMap = new HashMap<>(entries.size());
        for(SimplifiedMasternodeListEntry entry : entries)
            addMN(entry);
        storedBlock = new StoredBlock(params.getGenesisBlock(), BigInteger.ZERO, 0);

    }

    @Override
    protected void parse() throws ProtocolException {
        if (protocolVersion >= NetworkParameters.ProtocolVersion.BLS_SCHEME.getBitcoinProtocolVersion()) {
            version = (short) readUint16();
        } else {
            version = CURRENT_VERSION;
        }
        blockHash = readHash();
        height = (int)readUint32();
        int size = (int)readVarInt();
        mnMap = new HashMap<>(size);
        for(int i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            SimplifiedMasternodeListEntry mn = new SimplifiedMasternodeListEntry(params, payload, cursor, protocolVersion);
            cursor += mn.getMessageSize();
            mnMap.put(hash, mn);
        }

        // read the number of properties, which should be zero
        size = (int)readVarInt();
        Preconditions.checkArgument(size == 0, "There is an offset error with this data file, rejecting...");

        if(Context.get().masternodeListManager.getFormatVersion() >= 2) {
            ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
            buffer.put(readBytes(StoredBlock.COMPACT_SERIALIZED_SIZE));
            buffer.rewind();
            storedBlock = StoredBlock.deserializeCompact(params, buffer);
            storedBlockMatchesRequest = false;
        } else {
            storedBlock = new StoredBlock(params.getGenesisBlock(), BigInteger.ZERO, 0);
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        if (protocolVersion >= NetworkParameters.ProtocolVersion.BLS_SCHEME.getBitcoinProtocolVersion()) {
            Utils.uint16ToByteStreamLE(version, stream);
        }
        stream.write(blockHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(height, stream);

        stream.write(new VarInt(mnMap.size()).encode());
        for(Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
            stream.write(entry.getKey().getReversedBytes());
            entry.getValue().setProtocolVersion(protocolVersion);
            entry.getValue().bitcoinSerializeToStream(stream);
        }
        // unique properties -- do not save
        stream.write(new VarInt(0).encode());
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
        try {
            storedBlock.serializeCompact(buffer);
        } catch (IllegalStateException x) {
            // the chainwork is too large
            log.info("chain work is too large on {}, work = {}, size = {} bits (max = 96)", storedBlock,
                    storedBlock.getChainWork().toString(16), storedBlock.getChainWork().bitLength());
            storedBlock = new StoredBlock(storedBlock.getHeader(), BigInteger.ZERO, storedBlock.getHeight());
            storedBlock.serializeCompact(buffer);
        }
        stream.write(buffer.array());
    }

    public int size() {
        return mnMap.size();
    }

    @Override
    public String toString() {
        return "Simplified MN List(count: " + size() + " height: " + height +  ")";
    }

    public SimplifiedMasternodeList applyDiff(SimplifiedMasternodeListDiff diff) throws MasternodeListDiffException
    {
        CoinbaseTx cbtx = (CoinbaseTx)diff.coinBaseTx.getExtraPayloadObject();
        if(!diff.getPrevBlockHash().equals(blockHash) && !(diff.getPrevBlockHash().isZero() && blockHash.equals(params.getGenesisBlock().getHash())))
            throw new MasternodeListDiffException("The mnlistdiff does not connect to this list.  height: " +
                    height + " -> " + cbtx.getHeight(), false, false, height == cbtx.getHeight(), false);

        lock.lock();
        try {
            SimplifiedMasternodeList result = new SimplifiedMasternodeList(this, diff.getVersion());

            result.blockHash = diff.getBlockHash();
            result.height = cbtx.getHeight();
            result.coinbaseTxPayload = cbtx;

            for (Sha256Hash hash : diff.deletedMNs) {
                result.removeMN(hash);
            }
            for (SimplifiedMasternodeListEntry entry : diff.mnList) {
                result.addMN(entry);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    void addMN(SimplifiedMasternodeListEntry dmn)
    {
        lock.lock();
        try {
            mnMap.put(dmn.proRegTxHash, dmn);
        } finally {
            lock.unlock();
        }
    }

    void removeMN(Sha256Hash proTxHash) {
        lock.lock();
        try {
            SimplifiedMasternodeListEntry dmn = getMN(proTxHash);
            if (dmn != null) {
                mnMap.remove(proTxHash);
            }
        } finally {
            lock.unlock();
        }
    }

    public SimplifiedMasternodeListEntry getMN(Sha256Hash proTxHash)
    {
        lock.lock();
        try {
            return mnMap.get(proTxHash);
        } finally {
            lock.unlock();
        }
    }

    public boolean verify(Transaction coinbaseTx, SimplifiedMasternodeListDiff mnlistdiff, SimplifiedMasternodeList prevList) throws MasternodeListDiffException {
        //check mnListMerkleRoot

        if(!(coinbaseTx.getExtraPayloadObject() instanceof CoinbaseTx))
            throw new VerificationException("transaction is not a coinbase transaction");

        CoinbaseTx cbtx = (CoinbaseTx)coinbaseTx.getExtraPayloadObject();

        if(mnlistdiff.mnList.isEmpty() && mnlistdiff.deletedMNs.isEmpty() &&
                prevList != null && prevList.coinbaseTxPayload != null &&
                cbtx.getMerkleRootMasternodeList().equals(prevList.coinbaseTxPayload.getMerkleRootMasternodeList()))
                return true;


        lock.lock();
        try {
            ArrayList<Sha256Hash> proTxHashes = new ArrayList<>(mnMap.size());
            for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
                proTxHashes.add(entry.getValue().proRegTxHash);
            }
            proTxHashes.sort(Comparator.naturalOrder());

            ArrayList<Sha256Hash> smnlHashes = new ArrayList<>(mnMap.size());
            for (Sha256Hash hash : proTxHashes) {
                smnlHashes.add(mnMap.get(hash).getHash());
            }

            if (smnlHashes.isEmpty())
                return true;

            if (!cbtx.getMerkleRootMasternodeList().equals(MerkleRoot.calculateMerkleRoot(smnlHashes)))
                throw new MasternodeListDiffException("MerkleRoot of masternode list does not match coinbaseTx", true, false, false, true);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public Sha256Hash calculateMerkleRoot() {
        lock.lock();
        try {
            ArrayList<Sha256Hash> proTxHashes = new ArrayList<>(mnMap.size());
            for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
                proTxHashes.add(entry.getValue().proRegTxHash);
            }

            proTxHashes.sort(Comparator.naturalOrder());

            ArrayList<Sha256Hash> smnlHashes = new ArrayList<>(mnMap.size());
            for (Sha256Hash hash : proTxHashes) {
                for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet())
                    if (entry.getValue().proRegTxHash.equals(hash))
                        smnlHashes.add(entry.getValue().getHash());
            }

            return MerkleRoot.calculateMerkleRoot(smnlHashes);
        } finally {
            lock.unlock();
        }
    }

    public boolean containsMN(Sha256Hash proTxHash) {
        for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
            if (entry.getValue().getProTxHash().equals(proTxHash)) {
                return true;
            }
        }
        return false;
    }

    public boolean isValid(Sha256Hash proRegTxHash) {
        lock.lock();
        try {
            SimplifiedMasternodeListEntry entry = mnMap.get(proRegTxHash);
            if (entry != null) {
                return entry.isValid();
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean containsMN(PeerAddress address) {
        for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
            if (entry.getValue().getService().getAddr().equals(address.getAddr())) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    public Masternode getValidMNByCollateral(TransactionOutPoint masternodeOutpoint) {
        // TODO: we don't have an answer for this yet
        // masternodeOutpoint is hardcoded
        for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
            if (Objects.equals(entry.getValue().getCollateralOutpoint(), masternodeOutpoint)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Masternode getMNByAddress(InetSocketAddress socketAddress) {
        for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
            if (Objects.equals(entry.getValue().getService().getSocketAddress(), socketAddress)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public List<Masternode> getMasternodesByVotingKey(KeyId votingKeyId) {
        return mnMap.values().stream()
                .filter(simplifiedMasternodeListEntry -> simplifiedMasternodeListEntry.keyIdVoting.equals(votingKeyId))
                .collect(Collectors.toList());
    }

    public interface ForeachMNCallback {
        void processMN(SimplifiedMasternodeListEntry mn);
    }

    public void forEachMN(boolean onlyValid, ForeachMNCallback callback) {
        lock.lock();
        try {
            for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
                if (!onlyValid || isMNValid(entry.getValue())) {
                    callback.processMN(entry.getValue());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void forEachMN(boolean onlyValid, ForeachMNCallback callback, Comparator<SimplifiedMasternodeListEntry> comparator) {
        lock.lock();
        try {
            Collection<SimplifiedMasternodeListEntry> entries = mnMap.values();
            Stream<SimplifiedMasternodeListEntry> sortedEntries = entries.stream().sorted(comparator);

            sortedEntries.forEach(entry -> {
                if (onlyValid && !entry.isValid()) {
                    return;
                }
                callback.processMN (entry);
            });
        } finally {
            lock.unlock();
        }
    }

    public int getAllMNsCount()
    {
        return mnMap.size();
    }

    public int getValidMNsCount()
    {
        lock.lock();
        try {
            int count = 0;
            for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> p : mnMap.entrySet()) {
                if (isMNValid(p.getValue())) {
                    count++;
                }
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    public boolean isMNValid(SimplifiedMasternodeListEntry entry) {
        return entry.isValid;
    }

    ArrayList<Pair<Sha256Hash, Masternode>> calculateScores(final Sha256Hash modifier, boolean hpmnOnly)
    {
        final ArrayList<Pair<Sha256Hash, Masternode>> scores = new ArrayList<>(getAllMNsCount());

        forEachMN(true, mn -> {
            if(mn.getConfirmedHash().isZero()) {
                // we only take confirmed MNs into account to avoid hash grinding on the ProRegTxHash to sneak MNs into a
                // future quorums
                return;
            }
            if (hpmnOnly && mn.type != MasternodeType.HIGHPERFORMANCE.index)
                return;


            // calculate sha256(sha256(proTxHash, confirmedHash), modifier) per MN
            // Please note that this is not a double-sha256 but a single-sha256
            // The first part is already precalculated (confirmedHashWithProRegTxHash)
            // TODO When https://github.com/bitcoin/bitcoin/pull/13191 gets backported, implement something that is similar but for single-sha256
            try {
                UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(64);
                bos.write(mn.getConfirmedHashWithProRegTxHash().getReversedBytes());
                bos.write(modifier.getReversedBytes());
                scores.add(new Pair<>(Sha256Hash.of(bos.toByteArray()), mn)); //we don't reverse this, it is not for a wire message
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        });

        return scores;
    }

    static class CompareScoreMN implements Comparator<Pair<Sha256Hash, Masternode>>
    {
        public int compare(Pair<Sha256Hash, Masternode> a, Pair<Sha256Hash, Masternode> b) {
            if(a.getFirst().compareTo(b.getFirst()) < 0)
                return -1;
            if(a.getFirst().equals(b.getFirst()))
                return 0;
            else return 1;
        }
    }

    public int getMasternodeRank(Sha256Hash proTxHash, Sha256Hash quorumModifierHash, boolean hpmnOnly)
    {
        int rank = -1;
        //Added to speed things up

        SimplifiedMasternodeListEntry mnExisting = getMN(proTxHash);
        if (mnExisting == null)
            return -1;

        lock.lock();
        try {

            ArrayList<Pair<Sha256Hash, Masternode>> vecMasternodeScores = calculateScores(quorumModifierHash, hpmnOnly);
            if (vecMasternodeScores.isEmpty())
                return -1;

            Collections.sort(vecMasternodeScores, Collections.reverseOrder(new CompareScoreMN()));


            rank = 0;
            for (Pair<Sha256Hash, Masternode> scorePair : vecMasternodeScores) {
                rank++;
                if (scorePair.getSecond().getProRegTxHash().equals(proTxHash)) {
                    return rank;
                }
            }
            return -1;
        } finally {
            lock.unlock();
        }
    }

    public long getHeight() {
        return height;
    }

    protected void setHeight(int height) {
        this.height = height;
    }

    public Sha256Hash getBlockHash() {
        return blockHash;
    }

    ArrayList<Masternode> calculateQuorum(int maxSize, Sha256Hash modifier) {
        return calculateQuorum(maxSize, modifier, false);
    }

    ArrayList<Masternode> calculateQuorum(int maxSize, Sha256Hash modifier, boolean hpmnOnly)
    {
        ArrayList<Pair<Sha256Hash, Masternode>> scores = calculateScores(modifier, hpmnOnly);

        // sort is descending order
        scores.sort(Collections.reverseOrder(new CompareScoreMN()));

        // take top maxSize entries and return it
        int size = min(scores.size(), maxSize);
        ArrayList<Masternode> result = new ArrayList<>(size);
        if (!scores.isEmpty()) {
            for (int i = 0; i < size; i++) {
                result.add(scores.get(i).getSecond());
            }
        }
        return result;
    }

    public void setBlock(StoredBlock storedblock, boolean storedBlockMatchesRequest) {
        this.storedBlock = storedblock == null ? new StoredBlock(params.getGenesisBlock(), BigInteger.valueOf(0), 0) : storedblock;
        this.storedBlockMatchesRequest = storedBlockMatchesRequest;
    }

    public StoredBlock getStoredBlock() {
        return storedBlock;
    }

    public Masternode getMNByCollateral(TransactionOutPoint outPoint) {
        throw new UnsupportedOperationException("SimplifiedMasternodeEntries do not have an outpoint");
    }

    public int countEnabled() {
        return size();
    }

    public Collection<SimplifiedMasternodeListEntry> getSortedList(Comparator<Masternode> comparator) {
        ArrayList<SimplifiedMasternodeListEntry> list = Lists.newArrayList();
        forEachMN(true, list::add);
        list.sort(comparator);
        return list;
    }

    static class CompareMNProTxWithModifier implements Comparator<Masternode>
    {
        private final Sha256Hash dkgBlockHash;
        public CompareMNProTxWithModifier(Sha256Hash dkgBlockHash) {
            this.dkgBlockHash = dkgBlockHash;
        }

        public int compare(Masternode t1, Masternode t2) {
            Sha256Hash p1 = LLMQUtils.buildProTxDkgBlockHash(t1.proRegTxHash, dkgBlockHash);
            Sha256Hash p2 = LLMQUtils.buildProTxDkgBlockHash(t2.proRegTxHash, dkgBlockHash);

            if(p1.compareTo(p2) < 0)
                return -1;
            if(p1.equals(p2))
                return 0;
            else return 1;
        }
    }

    public Collection<SimplifiedMasternodeListEntry> getListSortedByModifier(Block dkgBlockHash) {
        return getSortedList(new CompareMNProTxWithModifier(dkgBlockHash.getHash()));
    }
}
