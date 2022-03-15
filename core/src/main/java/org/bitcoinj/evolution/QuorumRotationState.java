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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.evolution.listeners.MasternodeListDownloadedListener;
import org.bitcoinj.quorums.GetQuorumRotationInfo;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.quorums.PreviousQuorumQuarters;
import org.bitcoinj.quorums.QuorumRotationInfo;
import org.bitcoinj.quorums.QuorumSnapshot;
import org.bitcoinj.quorums.SigningManager;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.quorums.SnapshotSkipMode;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class QuorumRotationState extends AbstractQuorumState<GetQuorumRotationInfo, QuorumRotationInfo> {
    private static final Logger log = LoggerFactory.getLogger(QuorumRotationState.class);

    LLMQParameters.LLMQType llmqType;

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

    LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> mnListsCache;
    LinkedHashMap<Sha256Hash, SimplifiedQuorumList> quorumsCache;
    LinkedHashMap<Sha256Hash, QuorumSnapshot> quorumSnapshotCache = new LinkedHashMap<>();

    private ReentrantLock memberLock = Threading.lock("memberLock");
    @GuardedBy("memberLock")
    HashMap<LLMQParameters.LLMQType, HashMap<Sha256Hash, ArrayList<Masternode>>> mapQuorumMembers = new HashMap<>();

    private ReentrantLock indexedMemberLock = Threading.lock("indexedMemberLock");
    @GuardedBy("indexedMemberLock")
    HashMap<LLMQParameters.LLMQType, HashMap<Pair<Sha256Hash, Integer>, ArrayList<Masternode>>> mapIndexedQuorumMembers = new HashMap<>();

    public QuorumRotationState(Context context) {
        super(context);
        init();
    }

    protected void init() {
        mnListTip = new SimplifiedMasternodeList(context.getParams());
        mnListAtH = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinusC = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus2C = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus3C = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus4C = new SimplifiedMasternodeList(context.getParams());
        // add the genesis block list
        mnListsCache = new LinkedHashMap<>();
        mnListsCache.put(mnListAtH.getBlockHash(), mnListAtH);

        quorumListTip = new SimplifiedQuorumList(context.getParams());
        quorumListAtH = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinusC = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus2C = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus3C = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus4C = new SimplifiedQuorumList(context.getParams());

        // add genesis block quorums
        quorumsCache = new LinkedHashMap<>(10);
        quorumsCache.put(quorumListAtH.getBlockHash(), quorumListAtH);

        quorumSnapshotAtHMinusC = new QuorumSnapshot(0);
        quorumSnapshotAtHMinus2C = new QuorumSnapshot(0);
        quorumSnapshotAtHMinus3C = new QuorumSnapshot(0);
        quorumSnapshotAtHMinus4C = new QuorumSnapshot(0);
        finishInitialization();
    }

    void finishInitialization() {
        lastRequest = new QuorumUpdateRequest<>(new GetQuorumRotationInfo(params, Lists.newArrayList(), Sha256Hash.ZERO_HASH, false));
        llmqType = params.getLlmqForInstantSend();
        syncOptions = SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM;
    }

    @Override
    protected void clearState() {
        super.clearState();
        init();
    }

    public QuorumRotationState(Context context, byte[] payload, int offset) {
        super(context.getParams(), payload, offset);
        this.context = context;
        finishInitialization();
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
        log.info("processing quorumrotationinfo between (atH): {} & {}; {}", mnListAtH.getHeight(), newHeight, quorumRotationInfo.toString(chain));
        blockAtTip = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffTip().blockHash);
        blockAtH = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtH().blockHash);
        blockMinusC = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinusC().blockHash);
        blockMinus2C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus2C().blockHash);
        blockMinus3C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus3C().blockHash);
        blockMinus4C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus4C().blockHash);

        log.info(quorumRotationInfo.toString(chain));
        if (!isLoadingBootStrap && blockAtH.getHeight() != newHeight)
            throw new ProtocolException("qrinfo blockhash (height=" + blockAtH.getHeight() + " doesn't match coinbase block height: " + newHeight);

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Processing, quorumRotationInfo.getMnListDiffTip());

        // TODO: do we actually need to keep track of the blockchain tip mnlist?
        // SimplifiedMasternodeList newMNListTip = mnListTip.applyDiff(quorumRotationInfo.getMnListDiffTip());
        SimplifiedMasternodeList newMNListAtH = mnListAtH.applyDiff(quorumRotationInfo.getMnListDiffAtH());
        SimplifiedMasternodeList baseMNList = mnListsCache.get(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash);
        SimplifiedMasternodeList newMNListAtHMinusC = baseMNList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC());

        baseMNList = mnListsCache.get(quorumRotationInfo.getMnListDiffAtHMinus2C().prevBlockHash);
        SimplifiedMasternodeList newMNListAtHMinus2C = baseMNList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C());

        baseMNList = mnListsCache.get(quorumRotationInfo.getMnListDiffAtHMinus3C().prevBlockHash);
        SimplifiedMasternodeList newMNListAtHMinus3C = baseMNList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C());

        baseMNList = mnListsCache.get(quorumRotationInfo.getMnListDiffAtHMinus4C().prevBlockHash);
        SimplifiedMasternodeList newMNListAtHMinus4C = baseMNList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus4C());

        if (context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_MNLIST)) {
            // TODO: do we actually need to keep track of the blockchain tip mnlist?
            // newMNListTip.verify(quorumRotationInfo.getMnListDiffTip().coinBaseTx, quorumRotationInfo.getMnListDiffTip(), mnListTip);
            newMNListAtH.verify(quorumRotationInfo.getMnListDiffAtH().coinBaseTx, quorumRotationInfo.getMnListDiffAtH(), mnListAtH);
            newMNListAtHMinusC.verify(quorumRotationInfo.getMnListDiffAtHMinusC().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinusC(), mnListAtHMinusC);
            newMNListAtHMinus2C.verify(quorumRotationInfo.getMnListDiffAtHMinus2C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus2C(), mnListAtHMinus2C);
            newMNListAtHMinus3C.verify(quorumRotationInfo.getMnListDiffAtHMinus3C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), mnListAtHMinus3C);
            newMNListAtHMinus4C.verify(quorumRotationInfo.getMnListDiffAtHMinus4C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), mnListAtHMinus4C);
        }

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedMasternodes, quorumRotationInfo.getMnListDiffTip());

        // TODO: do we actually need to keep track of the blockchain tip mnlist?
        // newMNListTip.setBlock(blockAtTip, blockAtTip != null && blockAtTip.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffTip().prevBlockHash));
        newMNListAtH.setBlock(blockAtH, blockAtH != null && blockAtH.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash));
        newMNListAtHMinusC.setBlock(blockMinusC, blockMinusC != null && blockMinusC.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash));
        newMNListAtHMinus2C.setBlock(blockMinus2C, blockMinus2C != null && blockMinus2C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus2C().prevBlockHash));
        newMNListAtHMinus3C.setBlock(blockMinus3C, blockMinus3C != null && blockMinus3C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus3C().prevBlockHash));
        newMNListAtHMinus4C.setBlock(blockMinus4C, blockMinus4C != null && blockMinus4C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus4C().prevBlockHash));

        mnListsCache.clear();
        // TODO: do we actually need to keep track of the blockchain tip mnlist?
        // mnListsCache.put(newMNListAtH.getBlockHash(), newMNListAtH);
        mnListsCache.put(newMNListAtHMinusC.getBlockHash(), newMNListAtHMinusC);
        mnListsCache.put(newMNListAtHMinus2C.getBlockHash(), newMNListAtHMinus2C);
        mnListsCache.put(newMNListAtHMinus3C.getBlockHash(), newMNListAtHMinus3C);
        mnListsCache.put(newMNListAtHMinus4C.getBlockHash(), newMNListAtHMinus4C);

        // TODO: do we actually need to keep track of the blockchain tip mnlist?
        mnListTip = //newMNListTip;
        mnListAtH = newMNListAtH;
        mnListAtHMinusC = newMNListAtHMinusC;
        mnListAtHMinus2C = newMNListAtHMinus2C;
        mnListAtHMinus3C = newMNListAtHMinus3C;
        mnListAtHMinus4C = newMNListAtHMinus4C;

        quorumSnapshotAtHMinusC = quorumRotationInfo.getQuorumSnapshotAtHMinusC();
        quorumSnapshotAtHMinus2C = quorumRotationInfo.getQuorumSnapshotAtHMinus2C();
        quorumSnapshotAtHMinus3C = quorumRotationInfo.getQuorumSnapshotAtHMinus3C();
        quorumSnapshotAtHMinus4C = quorumRotationInfo.getQuorumSnapshotAtHMinus4C();

        quorumSnapshotCache.put(newMNListAtHMinusC.getBlockHash(), quorumSnapshotAtHMinusC);
        quorumSnapshotCache.put(newMNListAtHMinus2C.getBlockHash(), quorumSnapshotAtHMinus2C);
        quorumSnapshotCache.put(newMNListAtHMinus3C.getBlockHash(), quorumSnapshotAtHMinus3C);
        quorumSnapshotCache.put(newMNListAtHMinus4C.getBlockHash(), quorumSnapshotAtHMinus4C);

        // now calculate quorums
        SimplifiedQuorumList baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtHMinus4C().prevBlockHash);
        SimplifiedQuorumList newQuorumListAtHMinus4C = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus4C(), isLoadingBootStrap, chain);

        baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtHMinus3C().prevBlockHash);
        SimplifiedQuorumList newQuorumListAtHMinus3C = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C(), isLoadingBootStrap, chain);

        baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtHMinus2C().prevBlockHash);
        SimplifiedQuorumList newQuorumListAtHMinus2C = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C(), isLoadingBootStrap, chain);

        baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash);
        SimplifiedQuorumList newQuorumListAtHMinusC = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC(), isLoadingBootStrap, chain);

        baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtH().prevBlockHash);
        SimplifiedQuorumList newQuorumListAtH = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtH(), isLoadingBootStrap, chain);

        // TODO: do we actually need to keep track of the blockchain tip quorum list?
        //SimplifiedQuorumList newQuorumListTip = quorumListTip.applyDiff(quorumRotationInfo.getMnListDiffTip(), isLoadingBootStrap, chain);

        if (context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_QUORUM)) {
            // TODO: do we actually need to keep track of the blockchain tip quorum list?
            // newQuorumListTip.verify(quorumRotationInfo.getMnListDiffTip().coinBaseTx, quorumRotationInfo.getMnListDiffTip(), quorumListTip, newMNListTip);
            newQuorumListAtH.verify(quorumRotationInfo.getMnListDiffAtH().coinBaseTx, quorumRotationInfo.getMnListDiffAtH(), quorumListAtH, newMNListAtH);

            newQuorumListAtHMinusC.verify(quorumRotationInfo.getMnListDiffAtHMinusC().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinusC(), quorumListAtHMinusC, newMNListAtHMinusC);
            newQuorumListAtHMinus2C.verify(quorumRotationInfo.getMnListDiffAtHMinus2C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus2C(), quorumListAtHMinus2C, newMNListAtHMinus2C);
            newQuorumListAtHMinus3C.verify(quorumRotationInfo.getMnListDiffAtHMinus3C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), quorumListAtHMinus3C, newMNListAtHMinus3C);
            newQuorumListAtHMinus4C.verify(quorumRotationInfo.getMnListDiffAtHMinus4C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus4C(), quorumListAtHMinus4C, newMNListAtHMinus4C);
        }

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedQuorums, quorumRotationInfo.getMnListDiffTip());

        quorumsCache.clear();
        // TODO: do we actually need to keep track of the blockchain tip quorum list?
        // quorumsCache.put(newQuorumListTip.getBlockHash(), newQuorumListTip);
        quorumsCache.put(newQuorumListAtH.getBlockHash(), newQuorumListAtH);
        quorumsCache.put(newQuorumListAtHMinusC.getBlockHash(), newQuorumListAtHMinusC);
        quorumsCache.put(newQuorumListAtHMinus2C.getBlockHash(), newQuorumListAtHMinus2C);
        quorumsCache.put(newQuorumListAtHMinus3C.getBlockHash(), newQuorumListAtHMinus3C);
        quorumsCache.put(newQuorumListAtHMinus4C.getBlockHash(), newQuorumListAtHMinus4C);

        // TODO: do we actually need to keep track of the blockchain tip quorum list?
        // quorumListTip = newQuorumListTip;
        quorumListAtH = newQuorumListAtH;
        quorumListAtHMinusC = newQuorumListAtHMinusC;
        quorumListAtHMinus2C = newQuorumListAtHMinus2C;
        quorumListAtHMinus3C = newQuorumListAtHMinus3C;
        quorumListAtHMinus4C = newQuorumListAtHMinus4C;
    }

    public SimplifiedMasternodeList getMnListTip() {
        return mnListTip;
    }

    @Override
    public void requestReset(Peer peer, StoredBlock block) {
        lastRequest = new QuorumUpdateRequest<>(getQuorumRotationInfoRequestFromGenesis(block));
        peer.sendMessage(lastRequest.getRequestMessage());
    }

    public GetQuorumRotationInfo getQuorumRotationInfoRequestFromGenesis(StoredBlock block) {
        return new GetQuorumRotationInfo(params, Lists.newArrayList(), block.getHeader().getHash(), true);
    }

    @Override
    public void requestUpdate(Peer peer, StoredBlock nextBlock) {
        lastRequest = new QuorumUpdateRequest<>(getQuorumRotationInfoRequest(nextBlock));
        peer.sendMessage(lastRequest.getRequestMessage());
    }

    public GetQuorumRotationInfo getQuorumRotationInfoRequest(StoredBlock nextBlock) {
        try {
            int requestHeight = nextBlock.getHeight() - nextBlock.getHeight() % getUpdateInterval();
            // TODO: only do this on an empty list
            if (mnListAtH.getBlockHash().equals(params.getGenesisBlock().getHash()) /*!initChainTipSyncComplete*/) {
                requestHeight = requestHeight - 3 * (nextBlock.getHeight() % getUpdateInterval());
            }
            // if nextBlock is a rotation block, then use it since it won't be in the blockStore
            StoredBlock requestBlock = requestHeight == nextBlock.getHeight() ?
                    nextBlock :
                    blockStore.get(requestHeight);

            ArrayList<Sha256Hash> baseBlockHashes = Lists.newArrayList(
                    Sets.newHashSet(
                            requestBlock.getHeader().getPrevBlockHash(),
                            mnListAtH.getBlockHash(),
                            mnListAtHMinusC.getBlockHash(),
                            mnListAtHMinus2C.getBlockHash(),
                            mnListAtHMinus3C.getBlockHash(),
                            mnListAtHMinus4C.getBlockHash()
                    )
            );
            return new GetQuorumRotationInfo(context.getParams(), baseBlockHashes, requestBlock.getHeader().getHash(), true);
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public int getUpdateInterval() {
        return params.getLlmqs().get(llmqType).getDkgInterval();
    }

    @Override
    int getBlockHeightOffset() {
        return 0;
    }

    @Override
    boolean needsUpdate(StoredBlock nextBlock) {
        return nextBlock.getHeight() % getUpdateInterval() == 0 &&
                nextBlock.getHeight() >= mnListAtH.getHeight() + getUpdateInterval() + SigningManager.SIGN_HEIGHT_OFFSET;
    }

    @Override
    public SimplifiedMasternodeList getMasternodeList() {
        return mnListTip;
    }

    @Override
    public SimplifiedMasternodeList getMasternodeListAtTip() {
        return mnListTip;
    }

    public ArrayList<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash) {
        lock.lock();
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
                    mapQuorumMembers.get(llmqType).put(quorumBaseBlock.getHeader().getHash(), quorumMembers);
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

            quorumMembers = q.get(quorumIndex);

            mapQuorumMembers.get(llmqType).put(quorumBaseBlock.getHeader().getHash(), quorumMembers);

            return quorumMembers;

        } catch (BlockStoreException x) {
            // we are in deep trouble
            throw new RuntimeException(x);
        } catch (NullPointerException x) {
            log.warn("missing masternode list for this quorum:" + x);
            return null;
        } finally {
            lock.unlock();
        }
    }

    private ArrayList<ArrayList<Masternode>> computeQuorumMembersByQuarterRotation(LLMQParameters.LLMQType llmqType, StoredBlock quorumBaseBlock) throws BlockStoreException {
        final LLMQParameters llmqParameters = LLMQParameters.fromType(llmqType);

        final int cycleLength = llmqParameters.getDkgInterval();

        final StoredBlock blockHMinusC = quorumBaseBlock.getAncestor(blockChain.getBlockStore(), quorumBaseBlock.getHeight() - cycleLength);
        final StoredBlock pBlockHMinus2C = quorumBaseBlock.getAncestor(blockChain.getBlockStore(), quorumBaseBlock.getHeight() - 2 * cycleLength);
        final StoredBlock pBlockHMinus3C = quorumBaseBlock.getAncestor(blockChain.getBlockStore(), quorumBaseBlock.getHeight() - 3 * cycleLength);

        log.info("computeQuorumMembersByQuarterRotation llmqType[{}] nHeight[{}]", llmqType, quorumBaseBlock.getHeight());

        PreviousQuorumQuarters previousQuarters = getPreviousQuorumQuarterMembers(llmqParameters, blockHMinusC, pBlockHMinus2C, pBlockHMinus3C);

        ArrayList<ArrayList<Masternode>> quorumMembers = Lists.newArrayListWithCapacity(llmqParameters.getSigningActiveQuorumCount());
        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            quorumMembers.add(Lists.newArrayList());
        }

        ArrayList<ArrayList<SimplifiedMasternodeListEntry>> newQuarterMembers = buildNewQuorumQuarterMembers(llmqParameters, quorumBaseBlock, previousQuarters);

        // logging
        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            StringBuilder builder = new StringBuilder();

            builder.append(" 3Cmns[");
            for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinus3C.get(i)) {
                builder.append(m.getProTxHash().toString().substring(0, 4)).append(" | ");
            }
            builder.append(" ] 2Cmns[");
            for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinus2C.get(i)) {
                builder.append(m.getProTxHash().toString().substring(0, 4)).append(" | ");
            }
            builder.append(" ] 1Cmns[");
            for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinusC.get(i)) {
                builder.append(m.getProTxHash().toString().substring(0, 4)).append(" | ");
            }
            builder.append(" ] mew[");
            for (SimplifiedMasternodeListEntry m : newQuarterMembers.get(i)) {
                builder.append(m.getProTxHash().toString().substring(0, 4)).append(" | ");
            }
            builder.append(" ]");
            log.info("QuarterComposition h[{}] i[{}]:{}", quorumBaseBlock.getHeight(), i, builder.toString());
        }

        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinus3C.get(i)) {
                checkDuplicates(quorumMembers, i, m);
                quorumMembers.get(i).add(m);
            }
            for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinus2C.get(i)) {
                checkDuplicates(quorumMembers, i, m);
                quorumMembers.get(i).add(m);
            }
            for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinusC.get(i)) {
                checkDuplicates(quorumMembers, i, m);
                quorumMembers.get(i).add(m);
            }
            for (SimplifiedMasternodeListEntry m : newQuarterMembers.get(i)) {
                checkDuplicates(quorumMembers, i, m);
                quorumMembers.get(i).add(m);
            }

            StringBuilder ss = new StringBuilder();
            ss.append(" [");
            for (Masternode m : quorumMembers.get(i)) {
                ss.append(m.getProTxHash().toString().substring(0, 4)).append(" | ");
            }
            ss.append("]");
            log.info("QuorumComposition h[{}] i[{}]:{}\n", quorumBaseBlock.getHeight(), i, ss);
        }

        return quorumMembers;
    }

    private void checkDuplicates(ArrayList<ArrayList<Masternode>> quorumMembers, int i, SimplifiedMasternodeListEntry m) {
        for (Masternode masternode : quorumMembers.get(i)) {
            if (m.equals(masternode)) {
                log.info("{} is already in the list", m);
            }
        }
    }

    private ArrayList<ArrayList<SimplifiedMasternodeListEntry>> buildNewQuorumQuarterMembers(LLMQParameters llmqParameters, StoredBlock quorumBaseBlock, PreviousQuorumQuarters previousQuarters) {
        try {
            int nQuorums = llmqParameters.getSigningActiveQuorumCount();
            ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterQuorumMembers = new ArrayList<>(nQuorums);
            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                quarterQuorumMembers.add(Lists.newArrayList());
            }
            int quorumSize = llmqParameters.getSize();
            int quarterSize = quorumSize / 4;

            // - 8
            final StoredBlock workBlock = quorumBaseBlock.getAncestor(blockChain.getBlockStore(), quorumBaseBlock.getHeight() - 8);

            Sha256Hash modifier = LLMQUtils.buildLLMQBlockHash(llmqParameters.getType(), workBlock.getHeader().getHash());
            SimplifiedMasternodeList allMns = getListForBlock(workBlock.getHeader().getHash());
            if (allMns.getAllMNsCount() < quarterSize)
                return quarterQuorumMembers;

            SimplifiedMasternodeList MnsUsedAtH = new SimplifiedMasternodeList(params);
            SimplifiedMasternodeList MnsNotUsedAtH = new SimplifiedMasternodeList(params);
            ArrayList<SimplifiedMasternodeList> MnsUsedAtHIndex = Lists.newArrayListWithCapacity(llmqParameters.getSigningActiveQuorumCount());
            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                MnsUsedAtHIndex.add(new SimplifiedMasternodeList(params));
            }

            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                for (SimplifiedMasternodeListEntry mn : previousQuarters.quarterHMinusC.get(i)) {
                    MnsUsedAtH.addMN(mn);
                    MnsUsedAtHIndex.get(i).addMN(mn);
                }
                for (SimplifiedMasternodeListEntry mn : previousQuarters.quarterHMinus2C.get(i)) {
                    MnsUsedAtH.addMN(mn);
                    MnsUsedAtHIndex.get(i).addMN(mn);
                }
                for (SimplifiedMasternodeListEntry mn : previousQuarters.quarterHMinus3C.get(i)) {
                    MnsUsedAtH.addMN(mn);
                    MnsUsedAtHIndex.get(i).addMN(mn);
                }
            }

            allMns.forEachMN(true, mn -> {
                if (!MnsUsedAtH.containsMN(mn.getProTxHash())) {
                    MnsNotUsedAtH.addMN(mn);
                }
            });

            ArrayList<Masternode> sortedMnsUsedAtH = MnsUsedAtH.calculateQuorum(MnsUsedAtH.getAllMNsCount(), modifier);
            ArrayList<Masternode> sortedMnsNotUsedAtH = MnsNotUsedAtH.calculateQuorum(MnsNotUsedAtH.getAllMNsCount(), modifier);
            ArrayList<Masternode> sortedCombinedMnsList = new ArrayList<>(sortedMnsNotUsedAtH);
            sortedCombinedMnsList.addAll(sortedMnsUsedAtH);

            StringBuilder ss = new StringBuilder();
            ss.append(" [");
            for (Masternode m : sortedCombinedMnsList) {
                ss.append(m.getProTxHash().toString(), 0, 4).append(" | ");
            }
            ss.append("]");
            log.info("BuildNewQuorumQuarterMembers h[{}] {}\n", quorumBaseBlock.getHeight(), ss);

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
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }


    private QuorumSnapshot buildQuorumSnapshot(LLMQParameters llmqParameters, SimplifiedMasternodeList mnAtH, SimplifiedMasternodeList mnUsedAtH, ArrayList<Masternode> sortedCombinedMns, ArrayList<Integer> skipList) {
        QuorumSnapshot quorumSnapshot = new QuorumSnapshot(mnAtH.getAllMNsCount());

        AtomicInteger index = new AtomicInteger();
        mnAtH.forEachMN(true, mn -> {
            if (mnUsedAtH.containsMN(mn.getProTxHash())) {
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


    @Deprecated // this method is not used, may not be required"
    void buildQuorumSnapshotSkipList(LLMQParameters llmqParameters, SimplifiedMasternodeList mnUsedAtH, ArrayList<Masternode> sortedCombinedMns, QuorumSnapshot quorumSnapshot) {
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
        } else {
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

        try {
            StoredBlock snapshotBlockHMinusC = blockHMinusC.getAncestor(blockStore, blockHMinusC.getHeight() - 8);
            StoredBlock snapshotBlockHMinus2C = blockHMinusC.getAncestor(blockStore, blockHMinus2C.getHeight() - 8);
            StoredBlock snapshotBlockHMinus3C = blockHMinusC.getAncestor(blockStore, blockHMinus3C.getHeight() - 8);

            QuorumSnapshot quSnapshotHMinusC = quorumSnapshotCache.get(snapshotBlockHMinusC.getHeader().getHash()); //quorumSnapshotAtHMinusC;
            if (quSnapshotHMinusC != null) {

                quarters.quarterHMinusC = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinusC, quSnapshotHMinusC);

                QuorumSnapshot quSnapshotHMinus2C = quorumSnapshotCache.get(snapshotBlockHMinus2C.getHeader().getHash()); //quorumSnapshotAtHMinus2C;
                if (quSnapshotHMinus2C != null) {
                    quarters.quarterHMinus2C = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinus2C, quSnapshotHMinus2C);

                    QuorumSnapshot quSnapshotHMinus3C = quorumSnapshotCache.get(snapshotBlockHMinus3C.getHeader().getHash()); //quorumSnapshotAtHMinus3C;
                    if (quSnapshotHMinus3C != null) {
                        quarters.quarterHMinus3C = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinus3C, quSnapshotHMinus3C);
                    }
                }
            }

            return quarters;
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    ArrayList<ArrayList<SimplifiedMasternodeListEntry>> getQuorumQuarterMembersBySnapshot(LLMQParameters llmqParameters, StoredBlock quorumBaseBlock, QuorumSnapshot snapshot) {
        try {
            int numQuorums = llmqParameters.getSigningActiveQuorumCount();
            int quorumSize = llmqParameters.getSize();
            int quarterSize = quorumSize / 4;

            ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterQuorumMembers = Lists.newArrayListWithCapacity(numQuorums);
            for (int i = 0; i < numQuorums; ++i) {
                quarterQuorumMembers.add(Lists.newArrayList());
            }
            final StoredBlock workBlock = quorumBaseBlock.getAncestor(blockChain.getBlockStore(), quorumBaseBlock.getHeight() - 8);

            Sha256Hash modifier = LLMQUtils.buildLLMQBlockHash(llmqParameters.getType(), workBlock.getHeader().getHash());

            Pair<SimplifiedMasternodeList, SimplifiedMasternodeList> result = getMNUsageBySnapshot(llmqParameters.getType(), quorumBaseBlock, snapshot);
            SimplifiedMasternodeList mnsUsedAtH = result.getFirst();
            SimplifiedMasternodeList mnsNotUsedAtH = result.getSecond();

            ArrayList<Masternode> sortedMnsUsedAtH = mnsUsedAtH.calculateQuorum(mnsUsedAtH.getAllMNsCount(), modifier);

            ArrayList<Masternode> sortedMnsNotUsedAtH = mnsNotUsedAtH.calculateQuorum(mnsNotUsedAtH.getAllMNsCount(), modifier);
            ArrayList<Masternode> sortedCombinedMnsList = new ArrayList<>(sortedMnsNotUsedAtH);
            for (Masternode m1 : sortedMnsUsedAtH) {
                for (Masternode m2 : sortedMnsNotUsedAtH) {
                    if (m1.equals(m2)) {
                        log.info("{} is in both lists", m1);
                    }
                }
            }
            sortedCombinedMnsList.addAll(sortedMnsUsedAtH);

            //Mode 0: No skipping
            if (snapshot.getSkipListMode() == SnapshotSkipMode.MODE_NO_SKIPPING.getValue()) {
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
            else if (snapshot.getSkipListMode() == SnapshotSkipMode.MODE_SKIPPING_ENTRIES.getValue()) {
                int first_entry_index = 0;
                ArrayList<Integer> processedSkipList = Lists.newArrayList();
                for (int s : snapshot.getSkipList()) {
                    if (first_entry_index == 0) {
                        first_entry_index = s;
                        processedSkipList.add(s);
                    } else {
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
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    Pair<SimplifiedMasternodeList, SimplifiedMasternodeList> getMNUsageBySnapshot(LLMQParameters.LLMQType llmqType, StoredBlock quorumBaseBlock, QuorumSnapshot snapshot) {
        try {
            SimplifiedMasternodeList usedMNs = new SimplifiedMasternodeList(params);
            SimplifiedMasternodeList nonUsedMNs = new SimplifiedMasternodeList(params);

            final StoredBlock workBlock = quorumBaseBlock.getAncestor(blockChain.getBlockStore(), quorumBaseBlock.getHeight() - 8);
            final Sha256Hash modifier = LLMQUtils.buildLLMQBlockHash(llmqType, workBlock.getHeader().getHash());

            SimplifiedMasternodeList mns = getListForBlock(workBlock.getHeader().getHash());
            if (mns == null) {
                throw new NullPointerException(String.format("missing masternode list for height: %d / -8:%d", quorumBaseBlock.getHeight(), workBlock.getHeight()));
            }

            ArrayList<Masternode> list = mns.calculateQuorum(mns.getValidMNsCount(), modifier);

            AtomicInteger i = new AtomicInteger();

            for (Masternode mn : list) {
                if (snapshot.getActiveQuorumMembers().get(i.get())) {
                    usedMNs.addMN((SimplifiedMasternodeListEntry) mn);
                } else {
                    nonUsedMNs.addMN((SimplifiedMasternodeListEntry) mn);
                }
                i.getAndIncrement();
            }
            return new Pair<>(usedMNs, nonUsedMNs);
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    void initIndexedQuorumsCache(HashMap<LLMQParameters.LLMQType, HashMap<Pair<Sha256Hash, Integer>, ArrayList<Masternode>>> cache) {
        for (Map.Entry<LLMQParameters.LLMQType, LLMQParameters> llmq : params.getLlmqs().entrySet()) {
            cache.put(llmq.getKey(), new HashMap<>(llmq.getValue().getSigningActiveQuorumCount() + 1));
        }
    }

    void initQuorumsCache(HashMap<LLMQParameters.LLMQType, HashMap<Sha256Hash, ArrayList<Masternode>>> cache) {
        for (Map.Entry<LLMQParameters.LLMQType, LLMQParameters> llmq : params.getLlmqs().entrySet()) {
            cache.put(llmq.getKey(), new HashMap<>(llmq.getValue().getSigningActiveQuorumCount() + 1));
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

    @Override
    public LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> getMasternodeListCache() {
        return mnListsCache;
    }

    @Override
    public LinkedHashMap<Sha256Hash, SimplifiedQuorumList> getQuorumsCache() {
        return quorumsCache;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("QuorumRotationState{")
                .append("\n -----------Masternode Lists ------------")
                .append("\n  Tip: ").append(mnListTip)
                .append("\n  H:   ").append(mnListAtH)
                .append("\n  H-C: ").append(mnListAtHMinusC)
                .append("\n  H-2C:").append(mnListAtHMinus2C)
                .append("\n  H-3C:").append(mnListAtHMinus3C)
                .append("\n  H-4C:").append(mnListAtHMinus4C)
                .append("\n -----------Quorum Lists ------------")
                .append("\n  Tip: ").append(quorumListTip)
                .append("\n  H:   ").append(quorumListAtH)
                .append("\n  H-C: ").append(quorumListAtHMinusC)
                .append("\n  H-2C:").append(quorumListAtHMinus2C)
                .append("\n  H-3C:").append(quorumListAtHMinus3C)
                .append("\n  H-4C:").append(quorumListAtHMinus4C)
                .append("}");

        return builder.toString();
    }

    @Override
    public void processDiff(@Nullable Peer peer, QuorumRotationInfo quorumRotationInfo, AbstractBlockChain headersChain, AbstractBlockChain blockChain, boolean isLoadingBootStrap) {
        long newHeight = ((CoinbaseTx) quorumRotationInfo.getMnListDiffTip().coinBaseTx.getExtraPayloadObject()).getHeight();
        if (peer != null)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Received, quorumRotationInfo.getMnListDiffTip());
        Stopwatch watch = Stopwatch.createStarted();
        Stopwatch watchMNList = Stopwatch.createUnstarted();
        Stopwatch watchQuorums = Stopwatch.createUnstarted();
        boolean isSyncingHeadersFirst = context.peerGroup != null && context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;
        log.info("processing quorumrotationinfo between : {} & {}; {}",
                getMnListTip().getHeight(),
                newHeight, quorumRotationInfo);

        quorumRotationInfo.dump(getMnListTip().getHeight(), newHeight);

        lock.lock();
        try {
            log.info(quorumRotationInfo.toString(chain));
            setBlockChain(chain);
            applyDiff(peer, headersChain, blockChain, quorumRotationInfo, isLoadingBootStrap);

            log.info(this.toString());
            unCache();
            failedAttempts = 0;

            if (!pendingBlocks.isEmpty()) {
                StoredBlock thisBlock = pendingBlocks.get(0);
                pendingBlocks.remove(0);
                pendingBlocksMap.remove(thisBlock.getHeader().getHash());
            } else log.warn("pendingBlocks is empty");

            if (peer != null && isSyncingHeadersFirst)
                peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Finished, quorumRotationInfo.getMnListDiffTip());

            //setFormatVersion(QUORUM_ROTATION_FORMAT_VERSION);
            //if(quorumRotationInfo.hasChanges() || pendingBlocks.size() < MAX_CACHE_SIZE || saveOptions == SimplifiedMasternodeListManager.SaveOptions.SAVE_EVERY_BLOCK)
            //    save();

        } catch (MasternodeListDiffException x) {
            //we already have this mnlistdiff or doesn't match our current tipBlockHash
            if (mnListTip.getBlockHash().equals(quorumRotationInfo.getMnListDiffTip().blockHash)) {
                log.info("heights are the same: " + x.getMessage(), x);
                log.info("mnList = {} vs mnlistdiff {}", mnListTip.getBlockHash(), quorumRotationInfo.getMnListDiffTip().prevBlockHash);
                log.info("mnlistdiff {} -> {}", quorumRotationInfo.getMnListDiffTip().prevBlockHash, quorumRotationInfo.getMnListDiffTip().blockHash);
                log.info("lastRequest: {} -> {}", lastRequest.request.getBaseBlockHashes(), lastRequest.request.getBlockRequestHash());
                //remove this block from the list
                if (pendingBlocks.size() > 0) {
                    StoredBlock thisBlock = pendingBlocks.get(0);
                    if (thisBlock.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffTip().prevBlockHash) &&
                            thisBlock.getHeader().getHash().equals(quorumRotationInfo.getMnListDiffTip().prevBlockHash)) {
                        pendingBlocks.remove(0);
                        pendingBlocksMap.remove(thisBlock.getHeader().getHash());
                    }
                }
            } else {
                log.info("heights are different", x);
                log.info("qrinfo height = {}; mnListAtTip: {}; mnListAtH: {}; quorumListAtH: {}", newHeight, getMnListTip().getHeight(),
                        getMnListAtH().getHeight(), getQuorumListAtTip().getHeight());
                log.info("mnList = {} vs qrinfo = {}", mnListTip.getBlockHash(), quorumRotationInfo.getMnListDiffTip().prevBlockHash);
                log.info("qrinfo {} -> {}", quorumRotationInfo.getMnListDiffTip().prevBlockHash, quorumRotationInfo.getMnListDiffTip().blockHash);
                log.info("lastRequest: {} -> {}", lastRequest.request.getBaseBlockHashes(), lastRequest.request.getBlockRequestHash());
                failedAttempts++;
                log.info("failed attempts {}", failedAttempts);
                if (failedAttempts > MAX_ATTEMPTS)
                    resetMNList(true);
            }
        } catch (VerificationException x) {
            //request this block again and close this peer
            log.info("verification error: close this peer" + x.getMessage());
            failedAttempts++;
            throw x;
        } catch (NullPointerException x) {
            log.info("NPE: close this peer: ", x);
            failedAttempts++;
            throw new VerificationException("verification error: NPE", x);
        } catch (BlockStoreException x) {
            log.info(x.getMessage());
            failedAttempts++;
            throw new ProtocolException(x);
        } finally {
            watch.stop();
            log.info("processing qrinfo: Total: {} mnlistdiff: {}", watch, quorumRotationInfo.getMnListDiffTip());
            log.info(toString());
            waitingForMNListDiff = false;
            if (isSyncingHeadersFirst) {
                initChainTipSyncComplete = true;
                if (downloadPeer != null) {
                    log.info("initChainTipSync=false");
                    context.peerGroup.triggerMnListDownloadComplete();
                    log.info("initChainTipSync=true");
                } else {
                    context.peerGroup.triggerMnListDownloadComplete();
                }
            }
            requestNextMNListDiff();
            lock.unlock();
        }
    }

    /*@Override
    public void requestMNListDiff(Peer peer, StoredBlock block) {
        try {
            int requestHeight = block.getHeight() - block.getHeight() % getUpdateInterval();
            //if (!initChainTipSyncComplete) {
            //    requestHeight = requestHeight - 3 * (block.getHeight() % getUpdateInterval());
            //}
            StoredBlock requestBlock = blockStore.get(requestHeight);
            super.requestMNListDiff(peer, requestBlock);
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }*/
}
