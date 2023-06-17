package org.bitcoinj.evolution;

import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Math.min;
import static org.bitcoinj.core.Sha256Hash.hashTwice;

public class SimplifiedMasternodeList extends Message {

    private final Logger log = LoggerFactory.getLogger(SimplifiedMasternodeList.class);
    private ReentrantLock lock = Threading.lock("SimplifiedMasternodeList");

    private short version;
    private Sha256Hash blockHash;
    private long height;
    private StoredBlock storedBlock;
    private boolean storedBlockMatchesRequest;
    HashMap<Sha256Hash, SimplifiedMasternodeListEntry> mnMap;
    HashMap<Sha256Hash, Pair<Sha256Hash, Integer>> mnUniquePropertyMap;

    private CoinbaseTx coinbaseTxPayload;

    public SimplifiedMasternodeList(NetworkParameters params) {
        super(params);
        blockHash = params.getGenesisBlock().getHash();
        height = -1;
        mnMap = new HashMap<Sha256Hash, SimplifiedMasternodeListEntry>(5000);
        mnUniquePropertyMap = new HashMap<Sha256Hash, Pair<Sha256Hash, Integer>>(5000);
        storedBlock = new StoredBlock(params.getGenesisBlock(), BigInteger.ZERO, 0);
    }

    SimplifiedMasternodeList(NetworkParameters params, byte [] payload, int offset, int protocolVersion) {
        super(params, payload, offset, protocolVersion);
    }

    SimplifiedMasternodeList(SimplifiedMasternodeList other, short version) {
        super(other.params);
        this.version = version;
        this.protocolVersion = version == SimplifiedMasternodeListDiff.BASIC_BLS_VERSION ?
                NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion() :
                NetworkParameters.ProtocolVersion.BLS_LEGACY.getBitcoinProtocolVersion();
        this.blockHash = other.blockHash;
        this.height = other.height;
        mnMap = new HashMap<Sha256Hash, SimplifiedMasternodeListEntry>(other.mnMap);
        // tell all entries to render as version 2 (BLS basic scheme)
        for (SimplifiedMasternodeListEntry mn : mnMap.values()) {
            mn.version = version;
        }
        mnUniquePropertyMap = new HashMap<Sha256Hash, Pair<Sha256Hash, Integer>>(other.mnUniquePropertyMap);
        this.storedBlock = other.storedBlock;
    }

    SimplifiedMasternodeList(NetworkParameters params, ArrayList<SimplifiedMasternodeListEntry> entries, int protocolVersion) {
        super(params);
        this.protocolVersion = protocolVersion;
        this.version = protocolVersion == NetworkParameters.ProtocolVersion.BLS_LEGACY.getBitcoinProtocolVersion() ?
                SimplifiedMasternodeListDiff.LEGACY_BLS_VERSION : SimplifiedMasternodeListDiff.BASIC_BLS_VERSION;
        this.blockHash = params.getGenesisBlock().getHash();
        this.height = -1;
        mnUniquePropertyMap = new HashMap<Sha256Hash, Pair<Sha256Hash, Integer>>();
        mnMap = new HashMap<Sha256Hash, SimplifiedMasternodeListEntry>(entries.size());
        for(SimplifiedMasternodeListEntry entry : entries)
            addMN(entry);
        storedBlock = new StoredBlock(params.getGenesisBlock(), BigInteger.ZERO, 0);

    }

    @Override
    protected void parse() throws ProtocolException {
        if (protocolVersion >= params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_SCHEME)) {
            version = (short) readUint16();
        } else {
            version = SimplifiedMasternodeListDiff.LEGACY_BLS_VERSION;
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

        size = (int)readVarInt();
        mnUniquePropertyMap = new HashMap<Sha256Hash, Pair<Sha256Hash, Integer>>(size);
        for(long i = 0; i < size; ++i)
        {
            Sha256Hash hash = readHash();
            Sha256Hash first = readHash();
            int second = (int)readUint32();
            mnUniquePropertyMap.put(hash, new Pair<Sha256Hash, Integer>(first, second));
        }
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
        if (protocolVersion >= params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.BLS_SCHEME)) {
            Utils.uint16ToByteStreamLE(version, stream);
        }
        stream.write(blockHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(height, stream);

        stream.write(new VarInt(mnMap.size()).encode());
        for(Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
            stream.write(entry.getKey().getReversedBytes());
            entry.getValue().bitcoinSerializeToStream(stream);
        }
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
        return "Simplified MN List(count:  " + size() + "height: " + height + "/" + storedBlock.getHeight() +  ")";
    }

    public SimplifiedMasternodeList applyDiff(SimplifiedMasternodeListDiff diff) throws MasternodeListDiffException
    {
        CoinbaseTx cbtx = (CoinbaseTx)diff.coinBaseTx.getExtraPayloadObject();
        if(!diff.prevBlockHash.equals(blockHash) && !(diff.prevBlockHash.isZero() && blockHash.equals(params.getGenesisBlock().getHash())))
            throw new MasternodeListDiffException("The mnlistdiff does not connect to this list.  height: " +
                    height + " -> " + cbtx.getHeight(), false, false, height == cbtx.getHeight(), false);

        lock.lock();
        try {
            SimplifiedMasternodeList result = new SimplifiedMasternodeList(this, diff.getVersion());

            result.blockHash = diff.blockHash;
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
            SimplifiedMasternodeListEntry p = mnMap.get(proTxHash);
            if (p == null) {
                return null;
            }
            return p;
        } finally {
            lock.unlock();
        }
    }


    <T extends ChildMessage> void addUniqueProperty(SimplifiedMasternodeListEntry dmn, T value)
    {
        lock.lock();
        try {
            Sha256Hash hash = value.getHash();
            int i = 1;
            Pair<Sha256Hash, Integer> oldEntry = mnUniquePropertyMap.get(hash);
            //assert(oldEntry == null || oldEntry.getFirst().equals(dmn.proRegTxHash));
            if (oldEntry != null)
                i = oldEntry.getSecond() + 1;
            Pair<Sha256Hash, Integer> newEntry = new Pair(dmn.proRegTxHash, i);

            mnUniquePropertyMap.put(hash, newEntry);
        } finally {
            lock.unlock();
        }
    }
    <T extends ChildMessage>
    void deleteUniqueProperty(SimplifiedMasternodeListEntry dmn, T oldValue)
    {
        lock.lock();
        try {
            Sha256Hash oldHash = oldValue.getHash();
            Pair<Sha256Hash, Integer> p = mnUniquePropertyMap.get(oldHash);
            //assert(p != null && p.getFirst() == dmn.proRegTxHash);
            if (p.getSecond() == 1) {
                mnUniquePropertyMap.remove(oldHash);
            } else {
                mnUniquePropertyMap.put(oldHash, new Pair<Sha256Hash, Integer>(dmn.proRegTxHash, p.getSecond() - 1));
            }
        } finally {
            lock.unlock();
        }
    }

    public
    boolean verify(Transaction coinbaseTx, SimplifiedMasternodeListDiff mnlistdiff, SimplifiedMasternodeList prevList) throws MasternodeListDiffException {
        //check mnListMerkleRoot

        if(!(coinbaseTx.getExtraPayloadObject() instanceof CoinbaseTx))
            throw new VerificationException("transaction is not a coinbase transaction");

        CoinbaseTx cbtx = (CoinbaseTx)coinbaseTx.getExtraPayloadObject();

        if(mnlistdiff.mnList.isEmpty() && mnlistdiff.deletedMNs.isEmpty() &&
                prevList != null && prevList.coinbaseTxPayload != null) {
            if (cbtx.merkleRootMasternodeList.equals(prevList.coinbaseTxPayload.merkleRootMasternodeList))
                return true;
        }

        lock.lock();
        try {
            ArrayList<Sha256Hash> proTxHashes = new ArrayList<Sha256Hash>(mnMap.size());
            for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
                proTxHashes.add(entry.getValue().proRegTxHash);
            }
            Collections.sort(proTxHashes, new Comparator<Sha256Hash>() {
                @Override
                public int compare(Sha256Hash o1, Sha256Hash o2) {
                    return o1.compareTo(o2);
                }
            });

            ArrayList<Sha256Hash> smnlHashes = new ArrayList<Sha256Hash>(mnMap.size());
            for (Sha256Hash hash : proTxHashes) {
                smnlHashes.add(mnMap.get(hash).getHash());
            }

            if (smnlHashes.size() == 0)
                return true;

            if (!cbtx.merkleRootMasternodeList.equals(calculateMerkleRoot(smnlHashes)))
                throw new MasternodeListDiffException("MerkleRoot of masternode list does not match coinbaseTx", true, false, false, true);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public Sha256Hash calculateMerkleRoot() {
        lock.lock();
        try {
            ArrayList<Sha256Hash> proTxHashes = new ArrayList<Sha256Hash>(mnMap.size());
            for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
                proTxHashes.add(entry.getValue().proRegTxHash);
            }

            Collections.sort(proTxHashes, new Comparator<Sha256Hash>() {
                @Override
                public int compare(Sha256Hash o1, Sha256Hash o2) {
                    return o1.compareTo(o2);
                }
            });
            ArrayList<Sha256Hash> smnlHashes = new ArrayList<Sha256Hash>(mnMap.size());
            for (Sha256Hash hash : proTxHashes) {
                for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet())
                    if (entry.getValue().proRegTxHash.equals(hash))
                        smnlHashes.add(entry.getValue().getHash());
            }

            return calculateMerkleRoot(smnlHashes);
        } finally {
            lock.unlock();
        }
    }

    private Sha256Hash calculateMerkleRoot(List<Sha256Hash> hashes) {
        List<byte[]> tree = buildMerkleTree(hashes);
        return Sha256Hash.wrap(tree.get(tree.size() - 1));
    }

    private List<byte[]> buildMerkleTree(List<Sha256Hash> hashes) {
        // The Merkle root is based on a tree of hashes calculated from the masternode list proRegHash:
        //
        //     root
        //      / \
        //   A      B
        //  / \    / \
        // t1 t2 t3 t4
        //
        // The tree is represented as a list: t1,t2,t3,t4,A,B,root where each
        // entry is a hash.
        //
        // The hashing algorithm is double SHA-256. The leaves are a hash of the serialized contents of the transaction.
        // The interior nodes are hashes of the concenation of the two child hashes.
        //
        // This structure allows the creation of proof that a transaction was included into a block without having to
        // provide the full block contents. Instead, you can provide only a Merkle branch. For example to prove tx2 was
        // in a block you can just provide tx2, the hash(tx1) and B. Now the other party has everything they need to
        // derive the root, which can be checked against the block header. These proofs aren't used right now but
        // will be helpful later when we want to download partial block contents.
        //
        // Note that if the number of transactions is not even the last tx is repeated to make it so (see
        // tx3 above). A tree with 5 transactions would look like this:
        //
        //         root
        //        /     \
        //       1        5
        //     /   \     / \
        //    2     3    4  4
        //  / \   / \   / \
        // t1 t2 t3 t4 t5 t5
        ArrayList<byte[]> tree = new ArrayList<byte[]>();
        // Start by adding all the hashes of the transactions as leaves of the tree.
        for (Sha256Hash hash : hashes) {
            tree.add(hash.getBytes());
        }
        int levelOffset = 0; // Offset in the list where the currently processed level starts.
        // Step through each level, stopping when we reach the root (levelSize == 1).
        for (int levelSize = hashes.size(); levelSize > 1; levelSize = (levelSize + 1) / 2) {
            // For each pair of nodes on that level:
            for (int left = 0; left < levelSize; left += 2) {
                // The right hand node can be the same as the left hand, in the case where we don't have enough
                // transactions.
                int right = min(left + 1, levelSize - 1);
                byte[] leftBytes = Utils.reverseBytes(tree.get(levelOffset + left));
                byte[] rightBytes = Utils.reverseBytes(tree.get(levelOffset + right));
                tree.add(Utils.reverseBytes(hashTwice(leftBytes, 0, 32, rightBytes, 0, 32)));
            }
            // Move to the next level.
            levelOffset += levelSize;
        }
        return tree;
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
            // TODO: need to sort by comparator
            for (Map.Entry<Sha256Hash, SimplifiedMasternodeListEntry> entry : mnMap.entrySet()) {
                if (!onlyValid || isMNValid(entry.getValue())) {
                    callback.processMN(entry.getValue());
                }
            }
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
        final ArrayList<Pair<Sha256Hash, Masternode>> scores = new ArrayList<Pair<Sha256Hash, Masternode>>(getAllMNsCount());

        forEachMN(true, new ForeachMNCallback() {
            @Override
            public void processMN(SimplifiedMasternodeListEntry mn) {
                if(mn.getConfirmedHash().isZero()) {
                    // we only take confirmed MNs into account to avoid hash grinding on the ProRegTxHash to sneak MNs into a
                    // future quorums
                    return;
                }
                if (hpmnOnly) {
                    if (mn.type != MasternodeType.HIGHPERFORMANCE.index)
                        return;
                }

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
            }
        });

        return scores;
    }

    static class CompareScoreMN<Object> implements Comparator<Object>
    {
        public int compare(Object t1, Object t2) {
            Pair<Sha256Hash, SimplifiedMasternodeListEntry> p1 = (Pair<Sha256Hash, SimplifiedMasternodeListEntry>)t1;
            Pair<Sha256Hash, SimplifiedMasternodeListEntry> p2 = (Pair<Sha256Hash, SimplifiedMasternodeListEntry>)t2;

            if(p1.getFirst().compareTo(p2.getFirst()) < 0)
                return -1;
            if(p1.getFirst().equals(p2.getFirst()))
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
        Collections.sort(scores, Collections.reverseOrder(new CompareScoreMN()));

        // take top maxSize entries and return it
        int size = min(scores.size(), maxSize);
        ArrayList<Masternode> result = new ArrayList<Masternode>(size);
        if (scores.size() > 0) {
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

    public Collection<SimplifiedMasternodeListEntry> getSortedList(Comparator<SimplifiedMasternodeListEntry> comparator) {
        ArrayList<SimplifiedMasternodeListEntry> list = Lists.newArrayList();
        forEachMN(true, list::add);
        list.sort(comparator);
        return list;
    }

    static class CompareMNProTxWithModifier<Object> implements Comparator<Object>
    {
        private Sha256Hash dkgBlockHash;
        public CompareMNProTxWithModifier(Sha256Hash dkgBlockHash) {
            this.dkgBlockHash = dkgBlockHash;
        }

        public int compare(Object t1, Object t2) {
            Sha256Hash p1 = LLMQUtils.buildProTxDkgBlockHash(((SimplifiedMasternodeListEntry)t1).proRegTxHash, dkgBlockHash);
            Sha256Hash p2 = LLMQUtils.buildProTxDkgBlockHash(((SimplifiedMasternodeListEntry)t2).proRegTxHash, dkgBlockHash);

            if(p1.compareTo(p2) < 0)
                return -1;
            if(p1.equals(p2))
                return 0;
            else return 1;
        }
    }

    public Collection<SimplifiedMasternodeListEntry> getListSortedByModifier(Block dkgBlockHash) {
        return getSortedList(new CompareMNProTxWithModifier<>(dkgBlockHash.getHash()));
    }
}
