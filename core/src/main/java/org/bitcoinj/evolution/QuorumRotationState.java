package org.bitcoinj.evolution;

import com.google.common.collect.Lists;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.evolution.listeners.MasternodeListDownloadedListener;
import org.bitcoinj.quorums.GetQuorumRotationInfo;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.quorums.PreviousQuorumQuarters;
import org.bitcoinj.quorums.QuorumRotationInfo;
import org.bitcoinj.quorums.QuorumSnapshot;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.quorums.SnapshotSkipMode;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class QuorumRotationState extends Message {
    private static final Logger log = LoggerFactory.getLogger(QuorumRotationState.class);
    Context context;
    NetworkParameters params;
    AbstractBlockChain blockChain;
    BlockStore blockStore;

    QuorumSnapshot quorumSnapshotAtHMinusC;
    QuorumSnapshot quorumSnapshotAtHMinus2C;
    QuorumSnapshot quorumSnapshotAtHMinus3C;

    SimplifiedMasternodeList mnListTip;
    SimplifiedMasternodeList mnListAtHMinusC;
    SimplifiedMasternodeList mnListAtHMinus2C;
    SimplifiedMasternodeList mnListAtHMinus3C;

    SimplifiedQuorumList quorumListTip;
    SimplifiedQuorumList quorumListAtHMinusC;
    SimplifiedQuorumList quorumListAtHMinus2C;
    SimplifiedQuorumList quorumListAtHMinus3C;

    LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> mnListsCache = new LinkedHashMap<>();
    LinkedHashMap<Sha256Hash, SimplifiedQuorumList> quorumsCache = new LinkedHashMap<>();

    private ReentrantLock memberLock = Threading.lock("memberLock");
    @GuardedBy("memberLock")
    HashMap<LLMQParameters.LLMQType, HashMap<Sha256Hash, ArrayList<Masternode>>> mapQuorumMembers = new HashMap<>();

    private ReentrantLock indexedMemberLock = Threading.lock("indexedMemberLock");
    @GuardedBy("indexedMemberLock")
    HashMap<LLMQParameters.LLMQType, HashMap<Pair<Sha256Hash, Integer>, ArrayList<Masternode>>> mapIndexedQuorumMembers = new HashMap<>();


    public QuorumRotationState(Context context) {
        this.context = context;
        params = context.getParams();
        mnListTip = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinusC = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus2C = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus3C = new SimplifiedMasternodeList(context.getParams());
        quorumListTip = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinusC = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus2C = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus3C = new SimplifiedQuorumList(context.getParams());
    }

    public QuorumRotationState(Context context, byte [] payload, int offset) {
        super(context.getParams(), payload, offset);
        this.context = context;
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        blockStore = blockChain.getBlockStore();
    }

    public void applyDiff(Peer peer, AbstractBlockChain headersChain, AbstractBlockChain blockChain,
                          QuorumRotationInfo quorumRotationInfo, boolean isLoadingBootStrap)
            throws BlockStoreException, MasternodeListDiffException {
        StoredBlock block;
        StoredBlock blockTip;
        StoredBlock blockMinusC;
        StoredBlock blockMinus2C;
        StoredBlock blockMinus3C;
        long newHeight = ((CoinbaseTx) quorumRotationInfo.getMnListDiffTip().coinBaseTx.getExtraPayloadObject()).getHeight();
        if (peer != null)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Received, quorumRotationInfo.getMnListDiffTip());

        boolean isSyncingHeadersFirst = context.peerGroup != null && context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;
        log.info("processing quorumrotationinfo between : {} & {}; {}", mnListTip.getHeight(), newHeight, quorumRotationInfo);
        block = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffTip().blockHash);
        blockTip = chain.getBlockStore().get((int) quorumRotationInfo.getCreationHeight());
        blockMinusC = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinusC().blockHash);
        blockMinus2C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus2C().blockHash);
        blockMinus3C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus3C().blockHash);

        if (!isLoadingBootStrap && block.getHeight() != newHeight)
            throw new ProtocolException("qrinfo blockhash (height=" + block.getHeight() + " doesn't match coinbase block height: " + newHeight);

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Processing, quorumRotationInfo.getMnListDiffTip());

        SimplifiedMasternodeList newMNListTip = mnListTip.applyDiff(quorumRotationInfo.getMnListDiffTip());
        SimplifiedMasternodeList newNMListAtHMinusC = mnListAtHMinusC.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC());
        SimplifiedMasternodeList newNMListAtHMinus2C = mnListAtHMinus2C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C());
        SimplifiedMasternodeList newNMListAtHMinus3C = mnListAtHMinus3C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C());
        if (context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_MNLIST)) {
            newMNListTip.verify(quorumRotationInfo.getMnListDiffTip().coinBaseTx, quorumRotationInfo.getMnListDiffTip(), mnListTip);
            newNMListAtHMinusC.verify(quorumRotationInfo.getMnListDiffAtHMinusC().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinusC(), mnListAtHMinusC);
            newNMListAtHMinus2C.verify(quorumRotationInfo.getMnListDiffAtHMinus2C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus2C(), mnListAtHMinus2C);
            newNMListAtHMinus3C.verify(quorumRotationInfo.getMnListDiffAtHMinus3C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), mnListAtHMinus3C);
        }

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedMasternodes, quorumRotationInfo.getMnListDiffTip());

        newMNListTip.setBlock(blockTip, block != null && block.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffTip().prevBlockHash));
        newNMListAtHMinusC.setBlock(blockMinusC, block != null && blockMinusC.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash));
        newNMListAtHMinus2C.setBlock(blockMinus2C, block != null && blockMinus2C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus2C().prevBlockHash));
        newNMListAtHMinus3C.setBlock(blockMinus3C, block != null && blockMinus3C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus3C().prevBlockHash));

        mnListsCache.clear();
        mnListsCache.put(blockTip.getHeader().getHash(), newMNListTip);
        mnListsCache.put(newNMListAtHMinusC.getBlockHash(), newNMListAtHMinusC);
        mnListsCache.put(newNMListAtHMinus2C.getBlockHash(), newNMListAtHMinus2C);
        mnListsCache.put(newNMListAtHMinus3C.getBlockHash(), newNMListAtHMinus3C);

        quorumSnapshotAtHMinusC = quorumRotationInfo.getQuorumSnapshotAtHMinusC();
        quorumSnapshotAtHMinus2C = quorumRotationInfo.getQuorumSnapshotAtHMinus2C();
        quorumSnapshotAtHMinus3C = quorumRotationInfo.getQuorumSnapshotAtHMinus3C();

        // now calculate quorums
        SimplifiedQuorumList newQuorumListTip = quorumListTip.applyDiff(quorumRotationInfo.getMnListDiffTip(), isLoadingBootStrap, chain);
        SimplifiedQuorumList newQuorumListTipAtHMinusC = quorumListAtHMinusC.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC(), isLoadingBootStrap, chain);
        SimplifiedQuorumList newQuorumListTipAtHMinus2C = quorumListAtHMinus2C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C(), isLoadingBootStrap, chain);
        SimplifiedQuorumList newQuorumListTipAtHMinus3C = quorumListAtHMinus3C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C(), isLoadingBootStrap, chain);
        if (context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_QUORUM)) {
            newQuorumListTip.verify(quorumRotationInfo.getMnListDiffTip().coinBaseTx, quorumRotationInfo.getMnListDiffTip(), quorumListTip, newMNListTip);
            newQuorumListTipAtHMinusC.verify(quorumRotationInfo.getMnListDiffAtHMinusC().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinusC(), quorumListAtHMinusC, newNMListAtHMinusC);
            newQuorumListTipAtHMinus2C.verify(quorumRotationInfo.getMnListDiffAtHMinus2C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus2C(), quorumListAtHMinus2C, newNMListAtHMinus2C);
            newQuorumListTipAtHMinus3C.verify(quorumRotationInfo.getMnListDiffAtHMinus3C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), quorumListAtHMinus3C, newNMListAtHMinus3C);
        }

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedQuorums, quorumRotationInfo.getMnListDiffTip());

        quorumsCache.clear();
        quorumsCache.put(blockTip.getHeader().getHash(), newQuorumListTip);
        quorumsCache.put(newQuorumListTipAtHMinusC.getBlockHash(), newQuorumListTipAtHMinusC);
        quorumsCache.put(newQuorumListTipAtHMinus2C.getBlockHash(), newQuorumListTipAtHMinus2C);
        quorumsCache.put(newQuorumListTipAtHMinus3C.getBlockHash(), newQuorumListTipAtHMinus3C);

        mnListTip = newMNListTip;
        mnListAtHMinusC = newNMListAtHMinusC;
        mnListAtHMinus2C = newNMListAtHMinus2C;
        mnListAtHMinus3C = newNMListAtHMinus3C;

        quorumListTip = newQuorumListTip;
        quorumListAtHMinusC = newQuorumListTipAtHMinusC;
        quorumListAtHMinus2C = newQuorumListTipAtHMinus2C;
        quorumListAtHMinus3C = newQuorumListTipAtHMinus3C;
    }

    public SimplifiedMasternodeList getMnListTip() {
        return mnListTip;
    }

    public GetQuorumRotationInfo getQuorumRotationInfoRequest(StoredBlock nextBlock) {
        return new GetQuorumRotationInfo(context.getParams(), 4,
                Lists.newArrayList(
                        mnListTip.getBlockHash(),
                        mnListAtHMinusC.getBlockHash(),
                        mnListAtHMinus2C.getBlockHash(),
                        mnListAtHMinus3C.getBlockHash()),
                nextBlock.getHeader().getHash());
    }

    public ArrayList<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash)
    {

        try {
            ArrayList<Masternode> quorumMembers;
            if (mapQuorumMembers.isEmpty()) {
                initQuorumsCache(mapQuorumMembers);
            }
            HashMap<Sha256Hash, ArrayList<Masternode>> mapByType = mapQuorumMembers.get(llmqType);
            if (mapByType != null) {
                quorumMembers = mapByType.get(blockHash);
                if (quorumMembers != null)
                    return quorumMembers;
            }

            StoredBlock quorumBaseBlock = blockChain.getBlockStore().get(blockHash);
            if (mapIndexedQuorumMembers.isEmpty()) {
                initIndexedQuorumsCache(mapIndexedQuorumMembers);
            }

            /*
             * Quorums created with rotation are now created in a different way. All signingActiveQuorumCount are created during the period of dkgInterval.
             * But they are not created exactly in the same block, they are spread overtime: one quorum in each block until all signingActiveQuorumCount are created.
             * The new concept of quorumIndex is introduced in order to identify them.
             * In every dkgInterval blocks (also called CycleQuorumBaseBlock), the spread quorum creation starts like this:
             * For quorumIndex = 0 : signingActiveQuorumCount
             * Quorum Q with quorumIndex is created at height CycleQuorumBaseBlock + quorumIndex
             */

            int quorumIndex = quorumBaseBlock.getHeight() % LLMQParameters.fromType(llmqType).getDkgInterval();
            int cycleQuorumBaseHeight = quorumBaseBlock.getHeight() - quorumIndex;
            final StoredBlock cycleQuorumBaseBlock = blockChain.getBlockStore().get(cycleQuorumBaseHeight);

            /*
             * Since mapQuorumMembers stores Quorum members per block hash, and we don't know yet the block hashes of blocks for all quorumIndexes (since these blocks are not created yet)
             * We store them in a second cache mapIndexedQuorumMembers which stores them by {CycleQuorumBaseBlockHash, quorumIndex}
             */

            HashMap<Pair<Sha256Hash, Integer>, ArrayList<Masternode>> mapByTypeIndexed = mapIndexedQuorumMembers.get(llmqType);
            if (mapByType != null) {
                quorumMembers = mapByTypeIndexed.get(new Pair<>(cycleQuorumBaseBlock.getHeader().getHash(), quorumIndex));
                if (quorumMembers != null) {
                    mapQuorumMembers.get(llmqType).put(cycleQuorumBaseBlock.getHeader().getHash(), quorumMembers);
                    /*
                     * We also need to store which quorum block hash corresponds to which quorumIndex
                     */
                    //quorumManager.setQuorumIndexQuorumHash(llmqType, quorumBaseBlock.getHeader().getHash(), quorumIndex);
                    return quorumMembers;
                }
            }

            ArrayList<ArrayList<Masternode>> q = computeQuorumMembersByQuarterRotation(llmqType, cycleQuorumBaseBlock);
            for (int i = 0; i < q.size(); ++i) {
                mapIndexedQuorumMembers.get(llmqType).put(new Pair<>(cycleQuorumBaseBlock.getHeader().getHash(), i), q.get(i));
            }

            quorumMembers = q.get(0);

            mapQuorumMembers.get(llmqType).put(quorumBaseBlock.getHeader().getHash(), quorumMembers);

            return quorumMembers;

        } catch (BlockStoreException x) {
            // we are in deep trouble
            throw new RuntimeException(x);
        } finally {
            //lock.unlock();
        }
    }

    private ArrayList<ArrayList<Masternode>> computeQuorumMembersByQuarterRotation(LLMQParameters.LLMQType llmqType, StoredBlock quorumBaseBlock) throws BlockStoreException {
        final LLMQParameters llmqParameters = LLMQParameters.fromType(llmqType);

        final int cycleLength = llmqParameters.getDkgInterval();

        final StoredBlock blockHMinusC = quorumBaseBlock.getAncestor(blockChain.getBlockStore(), quorumBaseBlock.getHeight() - cycleLength);
        final StoredBlock pBlockHMinus2C = quorumBaseBlock.getAncestor(blockChain.getBlockStore(),quorumBaseBlock.getHeight() - 2 * cycleLength);
        final StoredBlock pBlockHMinus3C = quorumBaseBlock.getAncestor(blockChain.getBlockStore(),quorumBaseBlock.getHeight() - 3 * cycleLength);

        PreviousQuorumQuarters previousQuarters = getPreviousQuorumQuarterMembers(llmqParameters, blockHMinusC, pBlockHMinus2C, pBlockHMinus3C);

        ArrayList<ArrayList<Masternode>> quorumMembers = Lists.newArrayListWithCapacity(llmqParameters.getSigningActiveQuorumCount());
        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            quorumMembers.add(Lists.newArrayList());
        }

        ArrayList<ArrayList<SimplifiedMasternodeListEntry>> newQuarterMembers = buildNewQuorumQuarterMembers(llmqParameters, quorumBaseBlock, previousQuarters);

        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            for (SimplifiedMasternodeListEntry m: previousQuarters.quarterHMinus3C.get(i)) {
                for (int j = 0; j < quorumMembers.get(i).size(); ++j) {
                    if (m.equals(quorumMembers.get(i).get(j))) {
                        log.info("{} is already in the list", m);
                    }
                }
                quorumMembers.get(i).add(m);
            }
            for (SimplifiedMasternodeListEntry m: previousQuarters.quarterHMinus2C.get(i)) {
                for (int j = 0; j < quorumMembers.get(i).size(); ++j) {
                    if (m.equals(quorumMembers.get(i).get(j))) {
                        log.info("{} is already in the list", m);
                    }
                }
                quorumMembers.get(i).add(m);
            }
            for (SimplifiedMasternodeListEntry m: previousQuarters.quarterHMinusC.get(i)) {
                for (int j = 0; j < quorumMembers.get(i).size(); ++j) {
                    if (m.equals(quorumMembers.get(i).get(j))) {
                        log.info("{} is already in the list", m);
                    }
                }
                quorumMembers.get(i).add(m);
            }
            for (SimplifiedMasternodeListEntry m: newQuarterMembers.get(i)) {
                for (int j = 0; j < quorumMembers.get(i).size(); ++j) {
                    if (m.equals(quorumMembers.get(i).get(j))) {
                        log.info("{} is already in the list", m);
                    }
                }
                quorumMembers.get(i).add(m);
            }
        }

        return quorumMembers;
    }

    private ArrayList<ArrayList<SimplifiedMasternodeListEntry>> buildNewQuorumQuarterMembers(LLMQParameters llmqParameters, StoredBlock quorumBaseBlock, PreviousQuorumQuarters previousQuarters)
    {

        int nQuorums = llmqParameters.getSigningActiveQuorumCount();
        ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterQuorumMembers = new ArrayList<>(nQuorums);
        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            quarterQuorumMembers.add(Lists.newArrayList());
        }
        int quorumSize = llmqParameters.getSize();
        int quarterSize = quorumSize / 4;

        Sha256Hash modifier = LLMQUtils.buildLLMQBlockHash(llmqParameters.getType(), quorumBaseBlock.getHeader().getHash());
        SimplifiedMasternodeList allMns = getListForBlock(quorumBaseBlock.getHeader().getHash());
        if (allMns.getAllMNsCount() < quarterSize)
            return quarterQuorumMembers;

        SimplifiedMasternodeList MnsUsedAtH = new SimplifiedMasternodeList(params);
        SimplifiedMasternodeList MnsNotUsedAtH = new SimplifiedMasternodeList(params);
        ArrayList<SimplifiedMasternodeList> MnsUsedAtHIndex = Lists.newArrayListWithCapacity(llmqParameters.getSigningActiveQuorumCount());
        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            MnsUsedAtHIndex.add(new SimplifiedMasternodeList(params));
        }

        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            for (SimplifiedMasternodeListEntry mn: previousQuarters.quarterHMinusC.get(i)) {
                MnsUsedAtH.addMN(mn);
                MnsUsedAtHIndex.get(i).addMN(mn);
            }
            for (SimplifiedMasternodeListEntry mn: previousQuarters.quarterHMinus2C.get(i)) {
                MnsUsedAtH.addMN(mn);
                MnsUsedAtHIndex.get(i).addMN(mn);
            }
            for (SimplifiedMasternodeListEntry mn: previousQuarters.quarterHMinus3C.get(i)) {
                MnsUsedAtH.addMN(mn);
                MnsUsedAtHIndex.get(i).addMN(mn);
            }
        }

        allMns.forEachMN(true, mn -> {
            if (!MnsUsedAtH.containsMN(mn.getProTxHash())){
                MnsNotUsedAtH.addMN(mn);
            }
        });

        ArrayList<Masternode> sortedMnsUsedAtH = MnsUsedAtH.calculateQuorum(MnsUsedAtH.getAllMNsCount(), modifier);
        ArrayList<Masternode> sortedMnsNotUsedAtH = MnsNotUsedAtH.calculateQuorum(MnsNotUsedAtH.getAllMNsCount(), modifier);
        ArrayList<Masternode> sortedCombinedMnsList = new ArrayList<>(sortedMnsNotUsedAtH);
        sortedCombinedMnsList.addAll(sortedMnsUsedAtH);

        ArrayList<Integer> skipList = Lists.newArrayList();
        int firstSkippedIndex = 0;
        int idx = 0;
        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            while (quarterQuorumMembers.get(i).size() < quarterSize) {
                Masternode mn = sortedCombinedMnsList.get(idx);
                if (!MnsUsedAtHIndex.get(i).containsMN(mn.getProTxHash())) {
                    quarterQuorumMembers.get(i).add((SimplifiedMasternodeListEntry) mn);
                } else {
                    if (firstSkippedIndex == 0) {
                        firstSkippedIndex = idx;
                        skipList.add(idx);
                    } else {
                        skipList.add(idx - firstSkippedIndex);
                    }
                }
                idx++;
                if (idx == sortedCombinedMnsList.size()) {
                    idx = 0;
                }
            }
        }

        // maybe we don't need to do this as a client, lets check it
        QuorumSnapshot quorumSnapshot = buildQuorumSnapshot(llmqParameters, allMns, MnsUsedAtH, sortedCombinedMnsList, skipList);
        log.info("quorumSnapshot = {}", quorumSnapshot);

        // TODO: Do we need this?
        // quorumSnapshotManager.storeSnapshotForBlock(llmqParameters.getType(), quorumBaseBlock, quorumSnapshot);

        return quarterQuorumMembers;
    }



    private QuorumSnapshot buildQuorumSnapshot(LLMQParameters llmqParameters, SimplifiedMasternodeList mnAtH, SimplifiedMasternodeList mnUsedAtH, ArrayList<Masternode> sortedCombinedMns, ArrayList<Integer> skipList)
    {
        QuorumSnapshot quorumSnapshot = new QuorumSnapshot(mnAtH.getAllMNsCount());

        AtomicInteger index = new AtomicInteger();
        mnAtH.forEachMN(true, mn -> {
            if (mnUsedAtH.containsMN(mn.getProTxHash())){
                quorumSnapshot.setActiveQuorumMember(index.get(), true);
            }
            index.getAndIncrement();
        });

        // TODO: do we need this?
        // buildQuorumSnapshotSkipList(llmqParameters, mnUsedAtH, sortedCombinedMns, quorumSnapshot);
        if (skipList.isEmpty()) {
            quorumSnapshot.setSkipListMode(SnapshotSkipMode.MODE_NO_SKIPPING);
            quorumSnapshot.getSkipList().clear();
        } else {
            quorumSnapshot.setSkipListMode(SnapshotSkipMode.MODE_SKIPPING_ENTRIES);
            quorumSnapshot.setSkipList(skipList);
        }
        return quorumSnapshot;
    }


    void buildQuorumSnapshotSkipList(LLMQParameters llmqParameters, SimplifiedMasternodeList mnUsedAtH, ArrayList<Masternode> sortedCombinedMns, QuorumSnapshot quorumSnapshot)
    {
        if (mnUsedAtH.getAllMNsCount() == 0) {
            quorumSnapshot.setSkipListMode(SnapshotSkipMode.MODE_NO_SKIPPING);
            quorumSnapshot.getSkipList().clear();
        } else if (mnUsedAtH.getAllMNsCount() < sortedCombinedMns.size() / 2) {
            quorumSnapshot.setSkipListMode(SnapshotSkipMode.MODE_SKIPPING_ENTRIES);

            int first_entry_index = 0;
            int index = 0;

            for (Masternode mn : sortedCombinedMns) {
                if (mnUsedAtH.containsMN(sortedCombinedMns.get(index).getProTxHash())) {
                    if (first_entry_index == 0) {
                        first_entry_index = index;
                        quorumSnapshot.getSkipList().add(index);
                    } else
                        quorumSnapshot.getSkipList().add(index - first_entry_index);
                }
                index++;
            }
        }
        else {
            //Mode 2: Non-Skipping entries
            quorumSnapshot.setSkipListMode(SnapshotSkipMode.MODE_NO_SKIPPING_ENTRIES);

            int first_entry_index = 0;
            int index = 0;

            for (Masternode mn : sortedCombinedMns) {
                if (!mnUsedAtH.containsMN(sortedCombinedMns.get(index).getProTxHash())) {
                    if (first_entry_index == 0) {
                        first_entry_index = index;
                        quorumSnapshot.getSkipList().add(index);
                    } else
                        quorumSnapshot.getSkipList().add(index - first_entry_index);
                }
                index++;
            }
        }
    }

    PreviousQuorumQuarters getPreviousQuorumQuarterMembers(LLMQParameters llmqParameters, StoredBlock blockHMinusC, StoredBlock blockHMinus2C, StoredBlock blockHMinus3C) {
        PreviousQuorumQuarters quarters = new PreviousQuorumQuarters();

        QuorumSnapshot quSnapshotHMinusC = quorumSnapshotAtHMinusC;
        if (quSnapshotHMinusC != null){

            quarters.quarterHMinusC = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinusC, quSnapshotHMinusC);

            QuorumSnapshot quSnapshotHMinus2C = quorumSnapshotAtHMinus2C;
            if (quSnapshotHMinus2C != null) {
                quarters.quarterHMinus2C = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinus2C, quSnapshotHMinus2C);

                QuorumSnapshot quSnapshotHMinus3C = quorumSnapshotAtHMinus3C;
                if (quSnapshotHMinus3C != null) {
                    quarters.quarterHMinus3C = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinus3C, quSnapshotHMinus3C);
                }
            }
        }

        return quarters;
    }

    ArrayList<ArrayList<SimplifiedMasternodeListEntry>> getQuorumQuarterMembersBySnapshot(LLMQParameters llmqParameters, StoredBlock quorumBaseBlock, QuorumSnapshot snapshot)
    {
        int numQuorums = llmqParameters.getSigningActiveQuorumCount();
        int quorumSize = llmqParameters.getSize();
        int quarterSize = quorumSize / 4;

        ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterQuorumMembers = Lists.newArrayListWithCapacity(numQuorums);
        for (int i = 0; i < numQuorums; ++i) {
            quarterQuorumMembers.add(Lists.newArrayList());
        }

        Sha256Hash modifier = LLMQUtils.buildLLMQBlockHash(llmqParameters.getType(), quorumBaseBlock.getHeader().getHash());

        Pair<SimplifiedMasternodeList, SimplifiedMasternodeList> result = getMNUsageBySnapshot(llmqParameters.getType(), quorumBaseBlock, snapshot);
        SimplifiedMasternodeList mnsUsedAtH = result.getFirst();
        SimplifiedMasternodeList mnsNotUsedAtH = result.getSecond();

        ArrayList<Masternode> sortedMnsUsedAtH = mnsUsedAtH.calculateQuorum(mnsUsedAtH.getAllMNsCount(), modifier);

        ArrayList<Masternode> sortedMnsNotUsedAtH = mnsNotUsedAtH.calculateQuorum(mnsNotUsedAtH.getAllMNsCount(), modifier);
        ArrayList<Masternode> sortedCombinedMnsList = new ArrayList<>(sortedMnsNotUsedAtH);
        for (Masternode m1 : sortedMnsUsedAtH) {
            for (Masternode m2: sortedMnsNotUsedAtH) {
                if (m1.equals(m2)) {
                    log.info("{} is in both lists", m1);
                }
            }
        }
        sortedCombinedMnsList.addAll(sortedMnsUsedAtH);

        //Mode 0: No skipping
        if (snapshot.getSkipListMode() == SnapshotSkipMode.MODE_NO_SKIPPING.getValue()){
            Iterator<Masternode> itm = sortedCombinedMnsList.iterator();
            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                //Iterate over the first quarterSize elements
                while (quarterQuorumMembers.get(i).size() < quarterSize) {
                    Masternode m = itm.next();
                    quarterQuorumMembers.get(i).add((SimplifiedMasternodeListEntry) m);
                    if (!itm.hasNext()) {
                        itm = sortedCombinedMnsList.iterator();
                    }
                }
            }
        }
        //Mode 1: List holds entries to be skipped
        else if (snapshot.getSkipListMode() == SnapshotSkipMode.MODE_SKIPPING_ENTRIES.getValue()){
            HashSet<Sha256Hash> mnProTxHashToRemove = new HashSet<>();
            int first_entry_index = 0;
            ArrayList<Integer> processedSkipList = Lists.newArrayList();
            for (int s : snapshot.getSkipList()) {
                if (first_entry_index == 0) {
                    first_entry_index = s;
                    processedSkipList.add(s);
                }
                else {
                    processedSkipList.add(first_entry_index + s);
                }
            }

            int idx = 0;
            Iterator<Integer> itsk = processedSkipList.iterator();
            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                //Iterate over the first quarterSize elements
                while (quarterQuorumMembers.get(i).size() < quarterSize) {
                    if (itsk.hasNext() && idx == itsk.next())
                        ;
                    else
                        quarterQuorumMembers.get(i).add((SimplifiedMasternodeListEntry) sortedCombinedMnsList.get(idx));
                    idx++;
                    if (idx == sortedCombinedMnsList.size())
                        idx = 0;
                }
            }
        }
        //Mode 2: List holds entries to be kept
        else if (snapshot.getSkipListMode() == SnapshotSkipMode.MODE_NO_SKIPPING_ENTRIES.getValue()) {
            // Mode 2 will be written later
        }
        //Mode 3: Every node was skipped. Returning empty quarterQuorumMembers

        return quarterQuorumMembers;
    }

    Pair<SimplifiedMasternodeList, SimplifiedMasternodeList> getMNUsageBySnapshot(LLMQParameters.LLMQType llmqType, StoredBlock quorumBaseBlock, QuorumSnapshot snapshot)
    {
        SimplifiedMasternodeList usedMNs = new SimplifiedMasternodeList(params);
        SimplifiedMasternodeList nonUsedMNs = new SimplifiedMasternodeList(params);

        SimplifiedMasternodeList mns = getListForBlock(quorumBaseBlock.getHeader().getHash());
        ArrayList<Masternode> list = mns.calculateQuorum(mns.getValidMNsCount(), quorumBaseBlock.getHeader().getHash());

        AtomicInteger i = new AtomicInteger();

        for (Masternode mn: list) {
            if (snapshot.getActiveQuorumMembers().get(i.get())) {
                usedMNs.addMN((SimplifiedMasternodeListEntry) mn);
            } else {
                nonUsedMNs.addMN((SimplifiedMasternodeListEntry) mn);
            }
            i.getAndIncrement();
        }
        return new Pair<>(usedMNs, nonUsedMNs);
    }

    void initIndexedQuorumsCache(HashMap<LLMQParameters.LLMQType, HashMap<Pair<Sha256Hash, Integer>, ArrayList<Masternode>>> cache) {
        for (Map.Entry<LLMQParameters.LLMQType, LLMQParameters> llmq : params.getLlmqs().entrySet()) {
            cache.put(llmq.getKey(), new HashMap<>(llmq.getValue().getSigningActiveQuorumCount() +1));
        }
    }

    void initQuorumsCache(HashMap<LLMQParameters.LLMQType, HashMap<Sha256Hash, ArrayList<Masternode>>> cache) {
        for (Map.Entry<LLMQParameters.LLMQType, LLMQParameters> llmq : params.getLlmqs().entrySet()) {
            cache.put(llmq.getKey(), new HashMap<>(llmq.getValue().getSigningActiveQuorumCount() +1));
        }
    }

    public SimplifiedMasternodeList getListForBlock(Sha256Hash blockHash) {
        return mnListsCache.get(blockHash);
    }

    @Override
    protected void parse() throws ProtocolException {
        mnListTip = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListTip.getMessageSize();
        mnListAtHMinusC = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListAtHMinusC.getMessageSize();
        mnListAtHMinus2C = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListAtHMinus2C.getMessageSize();
        mnListAtHMinus3C = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListAtHMinus3C.getMessageSize();

        quorumListTip = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListTip.getMessageSize();
        quorumListAtHMinusC = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListAtHMinusC.getMessageSize();
        quorumListAtHMinus2C = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListAtHMinus2C.getMessageSize();
        quorumListAtHMinus3C = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListAtHMinus3C.getMessageSize();
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        mnListTip.bitcoinSerialize(stream);
        mnListAtHMinusC.bitcoinSerialize(stream);
        mnListAtHMinus2C.bitcoinSerialize(stream);
        mnListAtHMinus3C.bitcoinSerialize(stream);

        quorumListTip.bitcoinSerialize(stream);
        quorumListAtHMinusC.bitcoinSerialize(stream);
        quorumListAtHMinus2C.bitcoinSerialize(stream);
        quorumListAtHMinus3C.bitcoinSerialize(stream);
    }
}
