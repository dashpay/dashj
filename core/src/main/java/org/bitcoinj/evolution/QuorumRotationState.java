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

package org.bitcoinj.evolution;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Message;
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
    AbstractBlockChain blockChain;
    BlockStore blockStore;

    QuorumSnapshot quorumSnapshotAtHMinusC;
    QuorumSnapshot quorumSnapshotAtHMinus2C;
    QuorumSnapshot quorumSnapshotAtHMinus3C;
    QuorumSnapshot quorumSnapshotAtHMinus4C;

    SimplifiedMasternodeList mnListTip;
    SimplifiedMasternodeList mnListAtH;
    SimplifiedMasternodeList mnListAtHMinusC;
    SimplifiedMasternodeList mnListAtHMinus2C;
    SimplifiedMasternodeList mnListAtHMinus3C;
    SimplifiedMasternodeList mnListAtHMinus4C;

    SimplifiedQuorumList quorumListTip;
    SimplifiedQuorumList quorumListAtH;
    SimplifiedQuorumList quorumListAtHMinusC;
    SimplifiedQuorumList quorumListAtHMinus2C;
    SimplifiedQuorumList quorumListAtHMinus3C;
    SimplifiedQuorumList quorumListAtHMinus4C;

    LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> mnListsCache = new LinkedHashMap<>();
    LinkedHashMap<Sha256Hash, SimplifiedQuorumList> quorumsCache = new LinkedHashMap<>();
    LinkedHashMap<Sha256Hash, QuorumSnapshot> quorumSnapshotCache = new LinkedHashMap<>();

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
        mnListAtH = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinusC = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus2C = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus3C = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus4C = new SimplifiedMasternodeList(context.getParams());
        quorumListTip = new SimplifiedQuorumList(context.getParams());
        quorumListAtH = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinusC = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus2C = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus3C = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus4C = new SimplifiedQuorumList(context.getParams());
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
        StoredBlock blockAtTip;
        StoredBlock blockAtH;
        StoredBlock blockMinusC;
        StoredBlock blockMinus2C;
        StoredBlock blockMinus3C;
        StoredBlock blockMinus4C;
        long newHeight = ((CoinbaseTx) quorumRotationInfo.getMnListDiffAtH().coinBaseTx.getExtraPayloadObject()).getHeight();
        if (peer != null)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Received, quorumRotationInfo.getMnListDiffTip());

        boolean isSyncingHeadersFirst = context.peerGroup != null && context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;
        log.info("processing quorumrotationinfo between (atH): {} & {}; {}", mnListAtH.getHeight(), newHeight, quorumRotationInfo);
        blockAtTip = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffTip().blockHash);
        blockAtH = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtH().blockHash);
        blockMinusC = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinusC().blockHash);
        blockMinus2C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus2C().blockHash);
        blockMinus3C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus3C().blockHash);
        blockMinus4C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus4C().blockHash);

        if (!isLoadingBootStrap && blockAtH.getHeight() != newHeight)
            throw new ProtocolException("qrinfo blockhash (height=" + blockAtH.getHeight() + " doesn't match coinbase block height: " + newHeight);

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Processing, quorumRotationInfo.getMnListDiffTip());

        SimplifiedMasternodeList newMNListTip = mnListTip.applyDiff(quorumRotationInfo.getMnListDiffTip());
        SimplifiedMasternodeList newMNListAtH = mnListAtH.applyDiff(quorumRotationInfo.getMnListDiffAtH());
        SimplifiedMasternodeList newMNListAtHMinusC = mnListAtHMinusC.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC());
        SimplifiedMasternodeList newMNListAtHMinus2C = mnListAtHMinus2C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C());
        SimplifiedMasternodeList newMNListAtHMinus3C = mnListAtHMinus3C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C());
        SimplifiedMasternodeList newMNListAtHMinus4C = mnListAtHMinus4C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus4C());

        if (context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_MNLIST)) {
            newMNListTip.verify(quorumRotationInfo.getMnListDiffTip().coinBaseTx, quorumRotationInfo.getMnListDiffTip(), mnListTip);
            newMNListAtH.verify(quorumRotationInfo.getMnListDiffAtH().coinBaseTx, quorumRotationInfo.getMnListDiffAtH(), mnListAtH);
            newMNListAtHMinusC.verify(quorumRotationInfo.getMnListDiffAtHMinusC().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinusC(), mnListAtHMinusC);
            newMNListAtHMinus2C.verify(quorumRotationInfo.getMnListDiffAtHMinus2C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus2C(), mnListAtHMinus2C);
            newMNListAtHMinus3C.verify(quorumRotationInfo.getMnListDiffAtHMinus3C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), mnListAtHMinus3C);
            newMNListAtHMinus4C.verify(quorumRotationInfo.getMnListDiffAtHMinus4C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), mnListAtHMinus4C);
        }

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedMasternodes, quorumRotationInfo.getMnListDiffTip());

        newMNListTip.setBlock(blockAtTip, blockAtTip != null && blockAtTip.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffTip().prevBlockHash));
        newMNListAtH.setBlock(blockAtH, blockAtH != null && blockAtH.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash));
        newMNListAtHMinusC.setBlock(blockMinusC, blockMinusC != null && blockMinusC.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash));
        newMNListAtHMinus2C.setBlock(blockMinus2C, blockMinus2C != null && blockMinus2C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus2C().prevBlockHash));
        newMNListAtHMinus3C.setBlock(blockMinus3C, blockMinus3C != null && blockMinus3C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus3C().prevBlockHash));
        newMNListAtHMinus4C.setBlock(blockMinus4C, blockMinus4C != null && blockMinus4C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus4C().prevBlockHash));

        mnListsCache.clear();
        mnListsCache.put(newMNListAtH.getBlockHash(), newMNListAtH);
        mnListsCache.put(newMNListAtHMinusC.getBlockHash(), newMNListAtHMinusC);
        mnListsCache.put(newMNListAtHMinus2C.getBlockHash(), newMNListAtHMinus2C);
        mnListsCache.put(newMNListAtHMinus3C.getBlockHash(), newMNListAtHMinus3C);
        mnListsCache.put(newMNListAtHMinus4C.getBlockHash(), newMNListAtHMinus4C);

        mnListTip = newMNListTip;
        mnListAtH = newMNListAtH;
        mnListAtHMinusC = newMNListAtHMinusC;
        mnListAtHMinus2C = newMNListAtHMinus2C;
        mnListAtHMinus3C = newMNListAtHMinus3C;
        mnListAtHMinus4C = newMNListAtHMinus4C;

        quorumSnapshotAtHMinusC = quorumRotationInfo.getQuorumSnapshotAtHMinusC();
        quorumSnapshotAtHMinus2C = quorumRotationInfo.getQuorumSnapshotAtHMinus2C();
        quorumSnapshotAtHMinus3C = quorumRotationInfo.getQuorumSnapshotAtHMinus3C();
        quorumSnapshotAtHMinus4C = quorumRotationInfo.getQuorumSnapshotAtHMinus4C();

        quorumSnapshotCache.put(blockMinusC.getHeader().getHash(), quorumSnapshotAtHMinusC);
        quorumSnapshotCache.put(blockMinus2C.getHeader().getHash(), quorumSnapshotAtHMinus2C);
        quorumSnapshotCache.put(blockMinus3C.getHeader().getHash(), quorumSnapshotAtHMinus3C);
        quorumSnapshotCache.put(blockMinus4C.getHeader().getHash(), quorumSnapshotAtHMinus4C);

        // now calculate quorums
        SimplifiedQuorumList newQuorumListAtHMinus4C = quorumListAtHMinus4C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus4C(), isLoadingBootStrap, chain);
        SimplifiedQuorumList newQuorumListAtHMinus3C = quorumListAtHMinus3C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C(), isLoadingBootStrap, chain);
        SimplifiedQuorumList newQuorumListAtHMinus2C = quorumListAtHMinus2C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C(), isLoadingBootStrap, chain);
        SimplifiedQuorumList newQuorumListAtHMinusC = quorumListAtHMinusC.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC(), isLoadingBootStrap, chain);
        SimplifiedQuorumList newQuorumListAtH = quorumListAtH.applyDiff(quorumRotationInfo.getMnListDiffAtH(), isLoadingBootStrap, chain);
        SimplifiedQuorumList newQuorumListTip = quorumListTip.applyDiff(quorumRotationInfo.getMnListDiffTip(), isLoadingBootStrap, chain);

        if (context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_QUORUM)) {
            newQuorumListTip.verify(quorumRotationInfo.getMnListDiffTip().coinBaseTx, quorumRotationInfo.getMnListDiffTip(), quorumListTip, newMNListTip);
            newQuorumListAtH.verify(quorumRotationInfo.getMnListDiffAtH().coinBaseTx, quorumRotationInfo.getMnListDiffAtH(), quorumListAtH, newMNListAtH);

            newQuorumListAtHMinusC.verify(quorumRotationInfo.getMnListDiffAtHMinusC().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinusC(), quorumListAtHMinusC, newMNListAtHMinusC);
            newQuorumListAtHMinus2C.verify(quorumRotationInfo.getMnListDiffAtHMinus2C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus2C(), quorumListAtHMinus2C, newMNListAtHMinus2C);
            newQuorumListAtHMinus3C.verify(quorumRotationInfo.getMnListDiffAtHMinus3C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), quorumListAtHMinus3C, newMNListAtHMinus3C);
            newQuorumListAtHMinus4C.verify(quorumRotationInfo.getMnListDiffAtHMinus4C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus4C(), quorumListAtHMinus4C, newMNListAtHMinus4C);
        }

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedQuorums, quorumRotationInfo.getMnListDiffTip());

        quorumsCache.clear();
        quorumsCache.put(newQuorumListTip.getBlockHash(), newQuorumListTip);
        quorumsCache.put(newQuorumListAtH.getBlockHash(), newQuorumListAtH);
        quorumsCache.put(newQuorumListAtHMinusC.getBlockHash(), newQuorumListAtHMinusC);
        quorumsCache.put(newQuorumListAtHMinus2C.getBlockHash(), newQuorumListAtHMinus2C);
        quorumsCache.put(newQuorumListAtHMinus3C.getBlockHash(), newQuorumListAtHMinus3C);
        quorumsCache.put(newQuorumListAtHMinus4C.getBlockHash(), newQuorumListAtHMinus4C);

        quorumListTip = newQuorumListTip;
        quorumListAtH = newQuorumListAtH;
        quorumListAtHMinusC = newQuorumListAtHMinusC;
        quorumListAtHMinus2C = newQuorumListAtHMinus2C;
        quorumListAtHMinus3C = newQuorumListAtHMinus3C;
        quorumListAtHMinus4C = newQuorumListAtHMinus4C;
    }

    public SimplifiedMasternodeList getMnListTip() {
        return mnListTip;
    }

    public GetQuorumRotationInfo getQuorumRotationInfoRequest(StoredBlock nextBlock) {
        ArrayList<Sha256Hash> baseBlockHashes = Lists.newArrayList(
            Sets.newHashSet(
                mnListTip.getBlockHash(),
                mnListAtH.getBlockHash(),
                mnListAtHMinusC.getBlockHash(),
                mnListAtHMinus2C.getBlockHash(),
                mnListAtHMinus3C.getBlockHash(),
                mnListAtHMinus4C.getBlockHash()
            )
        );
        return new GetQuorumRotationInfo(context.getParams(), baseBlockHashes.size(), baseBlockHashes,
                nextBlock.getHeader().getHash(),
                true);
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

        QuorumSnapshot quSnapshotHMinusC = quorumSnapshotCache.get(blockHMinusC.getHeader().getHash()); //quorumSnapshotAtHMinusC;
        if (quSnapshotHMinusC != null){

            quarters.quarterHMinusC = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinusC, quSnapshotHMinusC);

            QuorumSnapshot quSnapshotHMinus2C = quorumSnapshotCache.get(blockHMinus2C.getHeader().getHash()); //quorumSnapshotAtHMinus2C;
            if (quSnapshotHMinus2C != null) {
                quarters.quarterHMinus2C = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinus2C, quSnapshotHMinus2C);

                QuorumSnapshot quSnapshotHMinus3C = quorumSnapshotCache.get(blockHMinus3C.getHeader().getHash()); //quorumSnapshotAtHMinus3C;
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
            int idxk = 0;
            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                //Iterate over the first quarterSize elements
                while (quarterQuorumMembers.get(i).size() < quarterSize) {
                    if (idxk != processedSkipList.size() && idx == processedSkipList.get(idxk))
                        idxk++;
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
        mnListAtH = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListAtH.getMessageSize();
        mnListAtHMinusC = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListAtHMinusC.getMessageSize();
        mnListAtHMinus2C = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListAtHMinus2C.getMessageSize();
        mnListAtHMinus3C = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListAtHMinus3C.getMessageSize();
        mnListAtHMinus4C = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnListAtHMinus4C.getMessageSize();

        mnListsCache = new LinkedHashMap<>();
        mnListsCache.put(mnListTip.getBlockHash(), mnListTip);
        mnListsCache.put(mnListAtH.getBlockHash(), mnListAtH);
        mnListsCache.put(mnListAtHMinus2C.getBlockHash(), mnListAtHMinus2C);
        mnListsCache.put(mnListAtHMinus3C.getBlockHash(), mnListAtHMinus3C);
        mnListsCache.put(mnListAtHMinus4C.getBlockHash(), mnListAtHMinus4C);

        quorumListTip = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListTip.getMessageSize();
        quorumListAtH = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListAtH.getMessageSize();
        quorumListAtHMinusC = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListAtHMinusC.getMessageSize();
        quorumListAtHMinus2C = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListAtHMinus2C.getMessageSize();
        quorumListAtHMinus3C = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListAtHMinus3C.getMessageSize();
        quorumListAtHMinus4C = new SimplifiedQuorumList(params, payload, cursor);
        cursor += quorumListAtHMinus4C.getMessageSize();

        quorumsCache = new LinkedHashMap<>();
        quorumsCache.put(quorumListTip.getBlockHash(), quorumListTip);
        quorumsCache.put(quorumListAtH.getBlockHash(), quorumListAtH);
        quorumsCache.put(quorumListAtHMinusC.getBlockHash(), quorumListAtHMinusC);
        quorumsCache.put(quorumListAtHMinus2C.getBlockHash(), quorumListAtHMinus2C);
        quorumsCache.put(quorumListAtHMinus3C.getBlockHash(), quorumListAtHMinus3C);
        quorumsCache.put(quorumListAtHMinus4C.getBlockHash(), quorumListAtHMinus4C);

        quorumSnapshotAtHMinusC = new QuorumSnapshot(params, payload, cursor);
        quorumSnapshotAtHMinus2C = new QuorumSnapshot(params, payload, cursor);
        quorumSnapshotAtHMinus3C = new QuorumSnapshot(params, payload, cursor);
        quorumSnapshotAtHMinus4C = new QuorumSnapshot(params, payload, cursor);

        quorumSnapshotCache = new LinkedHashMap<>();
        quorumSnapshotCache.put(mnListAtHMinusC.getBlockHash(), quorumSnapshotAtHMinusC);
        quorumSnapshotCache.put(mnListAtHMinus2C.getBlockHash(), quorumSnapshotAtHMinus2C);
        quorumSnapshotCache.put(mnListAtHMinus3C.getBlockHash(), quorumSnapshotAtHMinus3C);
        quorumSnapshotCache.put(mnListAtHMinus4C.getBlockHash(), quorumSnapshotAtHMinus4C);
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        mnListTip.bitcoinSerialize(stream);
        mnListAtH.bitcoinSerialize(stream);
        mnListAtHMinusC.bitcoinSerialize(stream);
        mnListAtHMinus2C.bitcoinSerialize(stream);
        mnListAtHMinus3C.bitcoinSerialize(stream);
        mnListAtHMinus4C.bitcoinSerialize(stream);

        quorumListTip.bitcoinSerialize(stream);
        quorumListAtH.bitcoinSerialize(stream);
        quorumListAtHMinusC.bitcoinSerialize(stream);
        quorumListAtHMinus2C.bitcoinSerialize(stream);
        quorumListAtHMinus3C.bitcoinSerialize(stream);
        quorumListAtHMinus4C.bitcoinSerialize(stream);

        quorumSnapshotAtHMinusC.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus2C.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus3C.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus4C.bitcoinSerialize(stream);
    }

    public SimplifiedMasternodeList getMnListAtH() {
        return mnListAtH;
    }

    public SimplifiedQuorumList getQuorumListAtH() {
        return quorumListAtH;
    }

    public SimplifiedQuorumList getQuorumListAtTip() {
        return quorumListTip;
    }

    public LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> getMasternodeListCache() {
        return mnListsCache;
    }

    public LinkedHashMap<Sha256Hash, SimplifiedQuorumList> getQuorumsCache() {
        return quorumsCache;
    }
}
