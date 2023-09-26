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
import org.bitcoinj.core.*;
import org.bitcoinj.evolution.listeners.MasternodeListDownloadedListener;
import org.bitcoinj.quorums.FinalCommitment;
import org.bitcoinj.quorums.GetQuorumRotationInfo;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.PreviousQuorumQuarters;
import org.bitcoinj.quorums.Quorum;
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

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Manages a DIP24 Quorum list
 * - obtaining from the network
 * - serialize/deserialize
 * - quorum membership verification
 *
 * TODO: refactor to use ArrayList instead of individual fields for H, H-2C, etc
 *       or use the HashMap cache members instead
 */

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

    List<FinalCommitment> lastCommitments;
    HashMap<Integer, SimplifiedQuorumList> activeQuorumLists;

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

        activeQuorumLists = new HashMap<>(2);
        // add an empty list to prevent crashes in the event of race conditions with QuorumState
        activeQuorumLists.put(0, new SimplifiedQuorumList(params));
        finishInitialization();
    }

    void finishInitialization() {
        lastRequest = new QuorumUpdateRequest<>(new GetQuorumRotationInfo(params, Lists.newArrayList(), Sha256Hash.ZERO_HASH, true));
        llmqType = params.getLlmqDIP0024InstantSend();
        syncOptions = MasternodeListSyncOptions.SYNC_MINIMUM;
    }

    @Override
    protected void clearState() {
        super.clearState();
        init();
    }

    @Override
    QuorumRotationInfo loadDiffMessageFromBuffer(byte[] buffer, int protocolVersion) {
        return new QuorumRotationInfo(params, buffer, protocolVersion);
    }

    public QuorumRotationState(Context context, byte[] payload, int offset, int protocolVersion) {
        super(context.getParams(), payload, offset, protocolVersion);
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
        StoredBlock blockMinus4C = null;
        long newHeight = ((CoinbaseTx) quorumRotationInfo.getMnListDiffAtH().coinBaseTx.getExtraPayloadObject()).getHeight();
        if (peer != null)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Received, quorumRotationInfo.getMnListDiffTip());

        boolean isSyncingHeadersFirst = context.peerGroup != null && context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;

        // in the event that we don't know that DIP24 is active, then isSyncingHeadersFirst is false
        //if (chain.getBestChainHeight() <= 1 || (headersChain != null && headersChain.getBestChainHeight() > blockChain.getBestChainHeight())) {
            chain = (headersChain != null && headersChain.getBestChainHeight() > blockChain.getBestChainHeight()) ? headersChain : blockChain;
        //}

        log.info("processing {} qrinfo between (atH): {} & {}; {}",
                isLoadingBootStrap ? "bootstrap" : "requested",
                mnListAtH.getHeight(), newHeight, quorumRotationInfo.toString(chain));

        blockAtTip = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffTip().blockHash);
        blockAtH = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtH().blockHash);
        blockMinusC = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinusC().blockHash);
        blockMinus2C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus2C().blockHash);
        blockMinus3C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus3C().blockHash);
        if (quorumRotationInfo.hasExtraShare()) {
            blockMinus4C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus4C().blockHash);
        }

        // TODO: this may not be needed
        // if (!isLoadingBootStrap && blockAtH.getHeight() != newHeight)
        //     throw new ProtocolException("qrinfo blockhash (height=" + blockAtH.getHeight() + " doesn't match coinbase block height: " + newHeight);

        if (peer != null && isSyncingHeadersFirst)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Processing, quorumRotationInfo.getMnListDiffTip());

        if (!mnListAtH.getBlockHash().equals(quorumRotationInfo.getMnListDiffAtH().blockHash)) {

            SimplifiedMasternodeList baseMNList;
            SimplifiedMasternodeList newMNListAtHMinus4C = null;
            if (quorumRotationInfo.hasExtraShare()) {
                baseMNList = mnListsCache.get(quorumRotationInfo.getMnListDiffAtHMinus4C().prevBlockHash);
                newMNListAtHMinus4C = baseMNList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus4C());
                mnListsCache.put(newMNListAtHMinus4C.getBlockHash(), newMNListAtHMinus4C);
            }

            baseMNList = mnListsCache.get(quorumRotationInfo.getMnListDiffAtHMinus3C().prevBlockHash);
            if (baseMNList == null)
                throw new MasternodeListDiffException("does not connect to previous lists", true, false, false, false);
            SimplifiedMasternodeList newMNListAtHMinus3C = baseMNList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C());
            mnListsCache.put(newMNListAtHMinus3C.getBlockHash(), newMNListAtHMinus3C);

            baseMNList = mnListsCache.get(quorumRotationInfo.getMnListDiffAtHMinus2C().prevBlockHash);
            if (baseMNList == null)
                throw new MasternodeListDiffException("does not connect to previous lists", true, false, false, false);
            SimplifiedMasternodeList newMNListAtHMinus2C = baseMNList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C());
            mnListsCache.put(newMNListAtHMinus2C.getBlockHash(), newMNListAtHMinus2C);

            // TODO: do we actually need to keep track of the blockchain tip mnlist?
            // SimplifiedMasternodeList newMNListTip = mnListTip.applyDiff(quorumRotationInfo.getMnListDiffTip());
            SimplifiedMasternodeList newMNListAtH = mnListAtH.applyDiff(quorumRotationInfo.getMnListDiffAtH());
            baseMNList = mnListsCache.get(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash);
            if (baseMNList == null)
                throw new MasternodeListDiffException("does not connect to previous lists", true, false, false, false);
            StoredBlock prevBlockHMinusC = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash);

            if (baseMNList == null) {
                log.info("mnList missing for " + quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash + " " + (prevBlockHMinusC != null ? prevBlockHMinusC.getHeight() : -1));
                for (Sha256Hash hash : mnListsCache.keySet()) {
                    StoredBlock block = chain.getBlockStore().get(hash);
                    log.info("--> {}: {}: {}", hash, block == null ? -1 : block.getHeight(), mnListsCache.get(hash).getBlockHash());
                }
            }
            checkNotNull(baseMNList, "mnList missing for " + quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash + " " + (prevBlockHMinusC != null ? prevBlockHMinusC.getHeight() : -1));

            SimplifiedMasternodeList newMNListAtHMinusC = baseMNList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC());
            mnListsCache.put(newMNListAtHMinusC.getBlockHash(), newMNListAtHMinusC);

            // need to handle the case where there is only 1 masternode list, or 2 -- not enough to do all verification


            if (context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_MNLIST)) {
                // TODO: do we actually need to keep track of the blockchain tip mnlist?
                // newMNListTip.verify(quorumRotationInfo.getMnListDiffTip().coinBaseTx, quorumRotationInfo.getMnListDiffTip(), mnListTip);
                newMNListAtH.verify(quorumRotationInfo.getMnListDiffAtH().coinBaseTx, quorumRotationInfo.getMnListDiffAtH(), mnListAtH);
                newMNListAtHMinusC.verify(quorumRotationInfo.getMnListDiffAtHMinusC().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinusC(), mnListAtHMinusC);
                newMNListAtHMinus2C.verify(quorumRotationInfo.getMnListDiffAtHMinus2C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus2C(), mnListAtHMinus2C);
                newMNListAtHMinus3C.verify(quorumRotationInfo.getMnListDiffAtHMinus3C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), mnListAtHMinus3C);
                if (quorumRotationInfo.hasExtraShare()) {
                    newMNListAtHMinus4C.verify(quorumRotationInfo.getMnListDiffAtHMinus4C().coinBaseTx, quorumRotationInfo.getMnListDiffAtHMinus3C(), mnListAtHMinus4C);
                }
            }

            if (peer != null && isSyncingHeadersFirst)
                peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedMasternodes, quorumRotationInfo.getMnListDiffTip());

            // TODO: do we actually need to keep track of the blockchain tip mnlist?
            // newMNListTip.setBlock(blockAtTip, blockAtTip != null && blockAtTip.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffTip().prevBlockHash));
            newMNListAtH.setBlock(blockAtH, blockAtH != null && blockAtH.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash));
            newMNListAtHMinusC.setBlock(blockMinusC, blockMinusC != null && blockMinusC.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash));
            newMNListAtHMinus2C.setBlock(blockMinus2C, blockMinus2C != null && blockMinus2C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus2C().prevBlockHash));
            newMNListAtHMinus3C.setBlock(blockMinus3C, blockMinus3C != null && blockMinus3C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus3C().prevBlockHash));
            if (quorumRotationInfo.hasExtraShare()) {
                newMNListAtHMinus4C.setBlock(blockMinus4C, blockMinus4C != null && blockMinus4C.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffAtHMinus4C().prevBlockHash));
            }

            mnListsCache.clear();
            // TODO: do we actually need to keep track of the blockchain tip mnlist?
            mnListsCache.put(newMNListAtH.getBlockHash(), newMNListAtH);
            mnListsCache.put(newMNListAtHMinusC.getBlockHash(), newMNListAtHMinusC);
            mnListsCache.put(newMNListAtHMinus2C.getBlockHash(), newMNListAtHMinus2C);
            mnListsCache.put(newMNListAtHMinus3C.getBlockHash(), newMNListAtHMinus3C);
            if (quorumRotationInfo.hasExtraShare()) {
                mnListsCache.put(newMNListAtHMinus4C.getBlockHash(), newMNListAtHMinus4C);
            }
            // process the older data

            List<SimplifiedMasternodeListDiff> oldDiffs = quorumRotationInfo.getMnListDiffLists();
            List<QuorumSnapshot> oldSnapshots = quorumRotationInfo.getQuorumSnapshotList();
            for (int i = 0; i < oldDiffs.size(); ++i) {
                SimplifiedMasternodeList oldList = new SimplifiedMasternodeList(params);
                oldList = oldList.applyDiff(oldDiffs.get(i));
                mnListsCache.put(oldDiffs.get(i).blockHash, oldList);
                quorumSnapshotCache.put(oldDiffs.get(i).blockHash, oldSnapshots.get(i));
            }

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
            if (quorumRotationInfo.hasExtraShare()) {
                quorumSnapshotAtHMinus4C = quorumRotationInfo.getQuorumSnapshotAtHMinus4C();
            }
            quorumSnapshotCache.put(newMNListAtHMinusC.getBlockHash(), quorumSnapshotAtHMinusC);
            quorumSnapshotCache.put(newMNListAtHMinus2C.getBlockHash(), quorumSnapshotAtHMinus2C);
            quorumSnapshotCache.put(newMNListAtHMinus3C.getBlockHash(), quorumSnapshotAtHMinus3C);
            if (quorumRotationInfo.hasExtraShare()) {
                quorumSnapshotCache.put(newMNListAtHMinus4C.getBlockHash(), quorumSnapshotAtHMinus4C);
            }

            // now calculate quorums, but do not validate them since they are all old
            SimplifiedQuorumList baseQuorumList;
            SimplifiedQuorumList newQuorumListAtHMinus4C = null;

            if (quorumRotationInfo.hasExtraShare()) {
                baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtHMinus4C().prevBlockHash);
                newQuorumListAtHMinus4C = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus4C(), isLoadingBootStrap, chain, true, false);
            }

            baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtHMinus3C().prevBlockHash);
            SimplifiedQuorumList newQuorumListAtHMinus3C = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C(), isLoadingBootStrap, chain, true, false);

            baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtHMinus2C().prevBlockHash);
            SimplifiedQuorumList newQuorumListAtHMinus2C = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C(), isLoadingBootStrap, chain, true, false);

            baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtHMinusC().prevBlockHash);
            SimplifiedQuorumList newQuorumListAtHMinusC = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC(), isLoadingBootStrap, chain, true, false);

            baseQuorumList = quorumsCache.get(quorumRotationInfo.getMnListDiffAtH().prevBlockHash);
            SimplifiedQuorumList newQuorumListAtH = baseQuorumList.applyDiff(quorumRotationInfo.getMnListDiffAtH(), isLoadingBootStrap, chain, true, false);

            if (context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_QUORUM)) {
                // verify the merkle root of the quorums at H
                newQuorumListAtH.verify(quorumRotationInfo.getMnListDiffAtH().coinBaseTx, quorumRotationInfo.getMnListDiffAtH(), quorumListAtH, newMNListAtH);
            }

            if (peer != null && isSyncingHeadersFirst)
                peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedQuorums, quorumRotationInfo.getMnListDiffTip());

            quorumsCache.clear();
            // we don't need to keep track of the blockchain tip quorum list
            quorumsCache.put(newQuorumListAtH.getBlockHash(), newQuorumListAtH);
            quorumsCache.put(newQuorumListAtHMinusC.getBlockHash(), newQuorumListAtHMinusC);
            quorumsCache.put(newQuorumListAtHMinus2C.getBlockHash(), newQuorumListAtHMinus2C);
            quorumsCache.put(newQuorumListAtHMinus3C.getBlockHash(), newQuorumListAtHMinus3C);
            if (quorumRotationInfo.hasExtraShare()) {
                quorumsCache.put(newQuorumListAtHMinus4C.getBlockHash(), newQuorumListAtHMinus4C);
                quorumListAtHMinus4C = newQuorumListAtHMinus4C;
            }

            quorumListAtH = newQuorumListAtH;
            quorumListAtHMinusC = newQuorumListAtHMinusC;
            quorumListAtHMinus2C = newQuorumListAtHMinus2C;
            quorumListAtHMinus3C = newQuorumListAtHMinus3C;
        } else {
            log.info("we already have the mnlist info, just read the lastCommitments");
        }

        // initialize the active quorum list for scanQuorums
        lastCommitments = quorumRotationInfo.getLastCommitmentPerIndex();

        SimplifiedQuorumList newList = new SimplifiedQuorumList(params);
        for (FinalCommitment commitment : lastCommitments) {
            Quorum quorum = new Quorum(commitment);
            newList.addQuorum(quorum);
        }
        try {
            boolean hasList = false;
            for (Map.Entry<Integer, SimplifiedQuorumList> entry : activeQuorumLists.entrySet()) {
                if (entry.getValue().equals(newList)) {
                    log.info("this new quorum list was already found in activeQuorumLists");
                    hasList = true;
                    break;
                }
            }
            if (!hasList) {
                newList.setBlock(blockAtH != null ? blockAtH : chain.getChainHead());
                newList.verifyQuorums(isLoadingBootStrap, chain, true);
                activeQuorumLists.put((int) newList.getHeight(), newList);
            }
            log.info("activeQuorumLists: {}", activeQuorumLists.size());
        } catch (Exception e) {
            log.warn("there was a problem verifying the active quorum list", e);
        }
    }

    public SimplifiedMasternodeList getMnListTip() {
        return mnListTip;
    }

    @Override
    public void requestReset(Peer peer, StoredBlock block) {
        lastRequest = new QuorumUpdateRequest<>(getQuorumRotationInfoRequestFromGenesis(block), peer.getAddress());
        sendRequestWithRetry(peer);
    }

    public GetQuorumRotationInfo getQuorumRotationInfoRequestFromGenesis(StoredBlock block) {
        return new GetQuorumRotationInfo(params, Lists.newArrayList(), block.getHeader().getHash(), true);
    }

    @Override
    public void requestUpdate(Peer peer, StoredBlock nextBlock) {
        lastRequest = new QuorumUpdateRequest<>(getQuorumRotationInfoRequest(nextBlock), peer.getAddress());
        sendRequestWithRetry(peer);
    }

    public GetQuorumRotationInfo getQuorumRotationInfoRequest(StoredBlock nextBlock) {
        try {
            // int requestHeight = nextBlock.getHeight() - nextBlock.getHeight() % getUpdateInterval();
            LLMQParameters llmqParameters = params.getLlmqs().get(llmqType);
            int requestHeight = nextBlock.getHeight() % getUpdateInterval() < llmqParameters.getDkgMiningWindowEnd() ? //(11 + LLMQParameters.fromType(llmqType).getSigningActiveQuorumCount() + SigningManager.SIGN_HEIGHT_OFFSET) ?
                    nextBlock.getHeight() - nextBlock.getHeight() % getUpdateInterval() : nextBlock.getHeight();
            // TODO: only do this on an empty list - obsolete
            //if (mnListAtH.getBlockHash().equals(params.getGenesisBlock().getHash()) /*!initChainTipSyncComplete*/) {
            //    requestHeight = requestHeight - 3 * (nextBlock.getHeight() % getUpdateInterval());
            //}
            // if nextBlock is a rotation block, then use it since it won't be in the blockStore
            StoredBlock requestBlock = requestHeight == nextBlock.getHeight() ?
                    nextBlock :
                    blockStore.get(requestHeight);

            if (requestBlock == null) {
                requestBlock = headerChain.getBlockStore().get(requestHeight);
            }
            log.info("requesting next qrinfo {} -> {}", nextBlock.getHeight(), requestHeight);

            HashSet<Sha256Hash> set = Sets.newHashSet(
                    requestBlock.getHeader().getPrevBlockHash(),
                    mnListAtH.getBlockHash(),
                    mnListAtHMinusC.getBlockHash(),
                    mnListAtHMinus2C.getBlockHash(),
                    mnListAtHMinus3C.getBlockHash()
            );
            if (mnListAtHMinus4C != null) {
                set.add(mnListAtHMinus4C.getBlockHash());
            }
            ArrayList<Sha256Hash> baseBlockHashes = Lists.newArrayList(set);

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
    public boolean isSynced() {
        if (!params.isDIP0024Active(blockChain.getBestChainHeight()))
            return true;

        if(mnListAtH.getHeight() == -1)
            return false;

        int mostCommonHeight = context.peerGroup.getMostCommonHeight();

        // determine when the last QR height was
        LLMQParameters llmqParameters = params.getLlmqs().get(llmqType);
        int rotationOffset = llmqParameters.getDkgMiningWindowEnd();
        int cycleLength = llmqParameters.getDkgInterval();

        return mostCommonHeight < (mnListAtH.getHeight() + rotationOffset + cycleLength);
    }

    @Override
    boolean needsUpdate(StoredBlock nextBlock) {
        LLMQParameters llmqParameters = params.getLlmqs().get(llmqType);
        int rotationOffset = llmqParameters.getDkgMiningWindowEnd();

        return nextBlock.getHeight() % getUpdateInterval() == rotationOffset &&
                nextBlock.getHeight() >= mnListAtH.getHeight() + rotationOffset;
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
            // TODO: There needs to be a better way to do this instead of checking each time
            if (quorumBaseBlock == null && headerChain != null) {
                quorumBaseBlock = headerChain.getBlockStore().get(blockHash);
            }
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
            StoredBlock cycleQuorumBaseBlock = blockChain.getBlockStore().get(cycleQuorumBaseHeight);
            if (cycleQuorumBaseBlock == null && headerChain != null) {
                cycleQuorumBaseBlock = headerChain.getBlockStore().get(cycleQuorumBaseHeight);
            }

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
            log.warn("missing masternode list for this quorum:", x);
            return null;
        } catch (NoSuchElementException x) {
            log.warn("cannot reconstruct list for this quorum:", x);
            return null;
        } finally {
            lock.unlock();
        }
    }

    private ArrayList<ArrayList<Masternode>> computeQuorumMembersByQuarterRotation(LLMQParameters.LLMQType llmqType, StoredBlock quorumBaseBlock) throws BlockStoreException {
        final LLMQParameters llmqParameters = LLMQParameters.fromType(llmqType);

        final int cycleLength = llmqParameters.getDkgInterval();

        final BlockStore store = headerStore != null && headerStore.getChainHead().getHeight() > blockStore.getChainHead().getHeight() ? headerStore : blockStore;
        final StoredBlock blockHMinusC = quorumBaseBlock.getAncestor(store, quorumBaseBlock.getHeight() - cycleLength);
        final StoredBlock pBlockHMinus2C = quorumBaseBlock.getAncestor(store, quorumBaseBlock.getHeight() - 2 * cycleLength);
        final StoredBlock pBlockHMinus3C = quorumBaseBlock.getAncestor(store, quorumBaseBlock.getHeight() - 3 * cycleLength);

        log.info("computeQuorumMembersByQuarterRotation llmqType[{}] nHeight[{}]", llmqType, quorumBaseBlock.getHeight());

        PreviousQuorumQuarters previousQuarters = getPreviousQuorumQuarterMembers(llmqParameters, blockHMinusC, pBlockHMinus2C, pBlockHMinus3C, quorumBaseBlock.getHeight());

        ArrayList<ArrayList<Masternode>> quorumMembers = Lists.newArrayListWithCapacity(llmqParameters.getSigningActiveQuorumCount());
        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            quorumMembers.add(Lists.newArrayList());
        }

        ArrayList<ArrayList<SimplifiedMasternodeListEntry>> newQuarterMembers = buildNewQuorumQuarterMembers(llmqParameters, quorumBaseBlock, previousQuarters);

        // logging
        if (context.isDebugMode()) {
            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                StringBuilder builder = new StringBuilder();

                builder.append(" 3Cmns[");
                for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinus3C.get(i)) {
                    builder.append(m.getProTxHash().toString().substring(0,4)).append("|");
                }
                builder.append(" ] 2Cmns[");
                for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinus2C.get(i)) {
                    builder.append(m.getProTxHash().toString().substring(0,4)).append("|");
                }
                builder.append(" ] Cmns[");
                for (SimplifiedMasternodeListEntry m : previousQuarters.quarterHMinusC.get(i)) {
                    builder.append(m.getProTxHash().toString().substring(0,4)).append("|");
                }
                builder.append(" ] new[");
                for (SimplifiedMasternodeListEntry m : newQuarterMembers.get(i)) {
                    builder.append(m.getProTxHash().toString().substring(0, 4)).append("|");
                }
                builder.append(" ]");
                log.info("QuarterComposition h[{}] i[{}]:{}", quorumBaseBlock.getHeight(), i, builder.toString());
            }
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

            if (context.isDebugMode()) {
                StringBuilder ss = new StringBuilder();
                ss.append(" [");
                for (Masternode m : quorumMembers.get(i)) {
                    ss.append(m.getProTxHash().toString().substring(0,4)).append("|");
                }
                ss.append("]");
                log.info("QuorumComposition h[{}] i[{}]:{}\n", quorumBaseBlock.getHeight(), i, ss);
            }
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

    private void printList(SimplifiedMasternodeList list, String name) {
        StringBuilder builder = new StringBuilder();
        builder.append(name).append(": \n");
        list.forEachMN(true, new SimplifiedMasternodeList.ForeachMNCallback() {
            @Override
            public void processMN(SimplifiedMasternodeListEntry mn) {
                builder.append("  ").append(mn.getProTxHash()).append("\n");
            }
        });

        log.info(builder.toString());
    }

    private void printList(List<Masternode> list, String name) {
        StringBuilder builder = new StringBuilder();
        builder.append(name).append(": \n");
        for (Masternode mn : list) {
            builder.append("  ").append(mn.getProTxHash()).append("\n");
        }

        log.info(builder.toString());
    }

    private static ArrayList<ArrayList<SimplifiedMasternodeListEntry>> createNewQuarterQuorumMembers(LLMQParameters llmqParameters) {
        int nQuorums = llmqParameters.getSigningActiveQuorumCount();
        ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterQuorumMembers = new ArrayList<>(nQuorums);
        for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
            quarterQuorumMembers.add(Lists.newArrayList());
        }
        return quarterQuorumMembers;
    }

    private ArrayList<ArrayList<SimplifiedMasternodeListEntry>> buildNewQuorumQuarterMembers(LLMQParameters llmqParameters, StoredBlock cycleQuorumBaseBlock, PreviousQuorumQuarters previousQuarters) {
        try {
            ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterQuorumMembers = createNewQuarterQuorumMembers(llmqParameters);
            int quorumSize = llmqParameters.getSize();
            int quarterSize = quorumSize / 4;

            // - 8
            final BlockStore store = headerStore != null && headerStore.getChainHead().getHeight() > blockStore.getChainHead().getHeight() ? headerStore : blockStore;
            final StoredBlock workBlock = cycleQuorumBaseBlock.getAncestor(store, cycleQuorumBaseBlock.getHeight() - 8);
            final Sha256Hash modifier = getHashModifier(llmqParameters, cycleQuorumBaseBlock);
            SimplifiedMasternodeList allMns = getListForBlock(workBlock.getHeader().getHash());
            if (allMns.getAllMNsCount() < quarterSize)
                return quarterQuorumMembers;

            SimplifiedMasternodeList MnsUsedAtH = new SimplifiedMasternodeList(params);
            SimplifiedMasternodeList MnsNotUsedAtH = new SimplifiedMasternodeList(params);
            ArrayList<SimplifiedMasternodeList> MnsUsedAtHIndex = Lists.newArrayListWithCapacity(llmqParameters.getSigningActiveQuorumCount());

            boolean skipRemovedMNs = params.isV19Active(cycleQuorumBaseBlock) || params.getId().equals(NetworkParameters.ID_TESTNET);

            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                MnsUsedAtHIndex.add(new SimplifiedMasternodeList(params));
            }

            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                for (SimplifiedMasternodeListEntry mn : previousQuarters.quarterHMinusC.get(i)) {
                    boolean skip = skipRemovedMNs && !allMns.containsMN(mn.proRegTxHash);
                    if (!skip && allMns.isValid(mn.proRegTxHash)) {
                        MnsUsedAtH.addMN(mn);
                        MnsUsedAtHIndex.get(i).addMN(mn);
                    }
                }
                for (SimplifiedMasternodeListEntry mn : previousQuarters.quarterHMinus2C.get(i)) {
                    boolean skip = skipRemovedMNs && !allMns.containsMN(mn.proRegTxHash);
                    if (!skip && allMns.isValid(mn.proRegTxHash)) {
                        MnsUsedAtH.addMN(mn);
                        MnsUsedAtHIndex.get(i).addMN(mn);
                    }
                }
                for (SimplifiedMasternodeListEntry mn : previousQuarters.quarterHMinus3C.get(i)) {
                    boolean skip = skipRemovedMNs && !allMns.containsMN(mn.proRegTxHash);
                    if (!skip && allMns.isValid(mn.proRegTxHash)) {
                        MnsUsedAtH.addMN(mn);
                        MnsUsedAtHIndex.get(i).addMN(mn);
                    }
                }
            }

            allMns.forEachMN(true, mn -> {
                if (mn.isValid() && !MnsUsedAtH.containsMN(mn.getProTxHash())) {
                    MnsNotUsedAtH.addMN(mn);
                }
            });

            if (context.isDebugMode()) {
                log.info("modifier: {}", modifier);
                printList(MnsUsedAtH, "MnsUsedAtH");
                printList(MnsNotUsedAtH, "MnsNotUsedAtH");
            }
            ArrayList<Masternode> sortedMnsUsedAtH = MnsUsedAtH.calculateQuorum(MnsUsedAtH.getAllMNsCount(), modifier);
            if (context.isDebugMode()) printList(sortedMnsUsedAtH, "sortedMnsUsedAtH");
            ArrayList<Masternode> sortedMnsNotUsedAtH = MnsNotUsedAtH.calculateQuorum(MnsNotUsedAtH.getAllMNsCount(), modifier);
            if (context.isDebugMode()) printList(sortedMnsNotUsedAtH, "sortedMnsNotUsedAtH");
            ArrayList<Masternode> sortedCombinedMnsList = new ArrayList<>(sortedMnsNotUsedAtH);
            sortedCombinedMnsList.addAll(sortedMnsUsedAtH);
            if (context.isDebugMode()) {
                printList(sortedCombinedMnsList, "sortedCombinedMnsList");
                StringBuilder ss = new StringBuilder();
                ss.append(" [");
                for (Masternode m : sortedCombinedMnsList) {
                    ss.append(m.getProTxHash().toString(), 0, 4).append("|");
                }
                ss.append("]");
                log.info("BuildNewQuorumQuarterMembers h[{}] {}\n", cycleQuorumBaseBlock.getHeight(), ss);
            }

            ArrayList<Integer> skipList = Lists.newArrayList();
            int firstSkippedIndex = 0;
            int idx = 0;
            for (int i = 0; i < llmqParameters.getSigningActiveQuorumCount(); ++i) {
                int usedMNsCount = MnsUsedAtHIndex.get(i).getAllMNsCount();
                boolean updated = false;
                int initialLoopIndex = idx;
                while (quarterQuorumMembers.get(i).size() < quarterSize) {
                    boolean skip = true;
                    Masternode mn = sortedCombinedMnsList.get(idx);
                    if (!MnsUsedAtHIndex.get(i).containsMN(mn.getProTxHash())) {
                        MnsUsedAtHIndex.get(i).addMN((SimplifiedMasternodeListEntry) sortedCombinedMnsList.get(idx));
                        quarterQuorumMembers.get(i).add((SimplifiedMasternodeListEntry) mn);
                        updated = true;
                        skip = false;
                    }
                    if (skip) {
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
                    if (idx == initialLoopIndex) {
                        // we made full "while" loop
                        if (!updated) {
                            // there are not enough MNs, there is nothing we can do here
                            return createNewQuarterQuorumMembers(llmqParameters);
                        }
                        // reset and try again
                        updated = false;
                    }
                }
            }

            return quarterQuorumMembers;
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    @Deprecated
    private QuorumSnapshot buildQuorumSnapshot(LLMQParameters llmqParameters, SimplifiedMasternodeList allMns, SimplifiedMasternodeList mnUsedAtH, ArrayList<Masternode> sortedCombinedMns, ArrayList<Integer> skipList) {
        QuorumSnapshot quorumSnapshot = new QuorumSnapshot(allMns.getAllMNsCount());

        AtomicInteger index = new AtomicInteger();
        allMns.forEachMN(true, mn -> {
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

    PreviousQuorumQuarters getPreviousQuorumQuarterMembers(LLMQParameters llmqParameters, StoredBlock blockHMinusC, StoredBlock blockHMinus2C, StoredBlock blockHMinus3C,
                                                           int height) {
        PreviousQuorumQuarters quarters = new PreviousQuorumQuarters();

        try {
            final BlockStore store = headerStore != null && headerStore.getChainHead().getHeight() > blockStore.getChainHead().getHeight() ? headerStore : blockStore;
            StoredBlock snapshotBlockHMinusC = blockHMinusC.getAncestor(store, blockHMinusC.getHeight() - 8);
            StoredBlock snapshotBlockHMinus2C = blockHMinusC.getAncestor(store, blockHMinus2C.getHeight() - 8);
            StoredBlock snapshotBlockHMinus3C = blockHMinusC.getAncestor(store, blockHMinus3C.getHeight() - 8);

            QuorumSnapshot quSnapshotHMinusC = quorumSnapshotCache.get(snapshotBlockHMinusC.getHeader().getHash());
            if (quSnapshotHMinusC != null) {

                quarters.quarterHMinusC = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinusC, quSnapshotHMinusC, height);

                QuorumSnapshot quSnapshotHMinus2C = quorumSnapshotCache.get(snapshotBlockHMinus2C.getHeader().getHash());
                if (quSnapshotHMinus2C != null) {
                    quarters.quarterHMinus2C = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinus2C, quSnapshotHMinus2C, height);

                    QuorumSnapshot quSnapshotHMinus3C = quorumSnapshotCache.get(snapshotBlockHMinus3C.getHeader().getHash());
                    if (quSnapshotHMinus3C != null) {
                        quarters.quarterHMinus3C = getQuorumQuarterMembersBySnapshot(llmqParameters, blockHMinus3C, quSnapshotHMinus3C, height);
                    }
                }
            }

            return quarters;
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    ArrayList<ArrayList<SimplifiedMasternodeListEntry>> getQuorumQuarterMembersBySnapshot(LLMQParameters llmqParameters, StoredBlock cycleQuorumBaseBlock, QuorumSnapshot snapshot, int height) {
        checkArgument(llmqParameters.useRotation());
        checkArgument(cycleQuorumBaseBlock.getHeight() % llmqParameters.getDkgInterval() == 0);
        //try {
            int numQuorums = llmqParameters.getSigningActiveQuorumCount();
            int quorumSize = llmqParameters.getSize();
            int quarterSize = quorumSize / 4;

            ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterQuorumMembers = Lists.newArrayListWithCapacity(numQuorums);
            for (int i = 0; i < numQuorums; ++i) {
                quarterQuorumMembers.add(Lists.newArrayList());
            }

            Sha256Hash modifier = getHashModifier(llmqParameters, cycleQuorumBaseBlock);

            Pair<SimplifiedMasternodeList, SimplifiedMasternodeList> result = getMNUsageBySnapshot(llmqParameters, cycleQuorumBaseBlock, snapshot, height);
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
       // }
        /*catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }*/
    }

    Pair<SimplifiedMasternodeList, SimplifiedMasternodeList> getMNUsageBySnapshot(LLMQParameters llmqParameters,
                                                                                  StoredBlock cycleQuorumBaseBlock,
                                                                                  QuorumSnapshot snapshot,
                                                                                  int height) {
        try {
            SimplifiedMasternodeList usedMNs = new SimplifiedMasternodeList(params);
            SimplifiedMasternodeList nonUsedMNs = new SimplifiedMasternodeList(params);

            final BlockStore store = headerStore != null && headerStore.getChainHead().getHeight() > blockStore.getChainHead().getHeight() ? headerStore : blockStore;
            final StoredBlock workBlock = cycleQuorumBaseBlock.getAncestor(store, cycleQuorumBaseBlock.getHeight() - 8);
            final Sha256Hash modifier = getHashModifier(llmqParameters, cycleQuorumBaseBlock);

            SimplifiedMasternodeList allMns = getListForBlock(workBlock.getHeader().getHash());
            if (allMns == null) {
                throw new NullPointerException(String.format("missing masternode list for height: %d / -8:%d", cycleQuorumBaseBlock.getHeight(), workBlock.getHeight()));
            }

            ArrayList<Masternode> list = allMns.calculateQuorum(allMns.getValidMNsCount(), modifier);
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
        mnListTip = new SimplifiedMasternodeList(params, payload, cursor, protocolVersion);
        cursor += mnListTip.getMessageSize();
        mnListAtH = new SimplifiedMasternodeList(params, payload, cursor, protocolVersion);
        cursor += mnListAtH.getMessageSize();
        mnListAtHMinusC = new SimplifiedMasternodeList(params, payload, cursor, protocolVersion);
        cursor += mnListAtHMinusC.getMessageSize();
        mnListAtHMinus2C = new SimplifiedMasternodeList(params, payload, cursor, protocolVersion);
        cursor += mnListAtHMinus2C.getMessageSize();
        mnListAtHMinus3C = new SimplifiedMasternodeList(params, payload, cursor, protocolVersion);
        cursor += mnListAtHMinus3C.getMessageSize();
        mnListAtHMinus4C = new SimplifiedMasternodeList(params, payload, cursor, protocolVersion);
        cursor += mnListAtHMinus4C.getMessageSize();

        mnListsCache = new LinkedHashMap<>();
        mnListsCache.put(mnListTip.getBlockHash(), mnListTip);
        mnListsCache.put(mnListAtH.getBlockHash(), mnListAtH);
        mnListsCache.put(mnListAtHMinusC.getBlockHash(), mnListAtHMinusC);
        mnListsCache.put(mnListAtHMinus2C.getBlockHash(), mnListAtHMinus2C);
        mnListsCache.put(mnListAtHMinus3C.getBlockHash(), mnListAtHMinus3C);
        mnListsCache.put(mnListAtHMinus4C.getBlockHash(), mnListAtHMinus4C);

        quorumListTip = new SimplifiedQuorumList(params, payload, cursor, protocolVersion);
        cursor += quorumListTip.getMessageSize();
        quorumListAtH = new SimplifiedQuorumList(params, payload, cursor, protocolVersion);
        cursor += quorumListAtH.getMessageSize();
        quorumListAtHMinusC = new SimplifiedQuorumList(params, payload, cursor, protocolVersion);
        cursor += quorumListAtHMinusC.getMessageSize();
        quorumListAtHMinus2C = new SimplifiedQuorumList(params, payload, cursor, protocolVersion);
        cursor += quorumListAtHMinus2C.getMessageSize();
        quorumListAtHMinus3C = new SimplifiedQuorumList(params, payload, cursor, protocolVersion);
        cursor += quorumListAtHMinus3C.getMessageSize();
        quorumListAtHMinus4C = new SimplifiedQuorumList(params, payload, cursor, protocolVersion);
        cursor += quorumListAtHMinus4C.getMessageSize();

        quorumsCache = new LinkedHashMap<>();
        quorumsCache.put(quorumListTip.getBlockHash(), quorumListTip);
        quorumsCache.put(quorumListAtH.getBlockHash(), quorumListAtH);
        quorumsCache.put(quorumListAtHMinusC.getBlockHash(), quorumListAtHMinusC);
        quorumsCache.put(quorumListAtHMinus2C.getBlockHash(), quorumListAtHMinus2C);
        quorumsCache.put(quorumListAtHMinus3C.getBlockHash(), quorumListAtHMinus3C);
        quorumsCache.put(quorumListAtHMinus4C.getBlockHash(), quorumListAtHMinus4C);

        quorumSnapshotAtHMinusC = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinusC.getMessageSize();
        quorumSnapshotAtHMinus2C = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinus2C.getMessageSize();
        quorumSnapshotAtHMinus3C = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinus3C.getMessageSize();
        quorumSnapshotAtHMinus4C = new QuorumSnapshot(params, payload, cursor);
        cursor += quorumSnapshotAtHMinus4C.getMessageSize();

        quorumSnapshotCache = new LinkedHashMap<>();
        quorumSnapshotCache.put(mnListAtHMinusC.getBlockHash(), quorumSnapshotAtHMinusC);
        quorumSnapshotCache.put(mnListAtHMinus2C.getBlockHash(), quorumSnapshotAtHMinus2C);
        quorumSnapshotCache.put(mnListAtHMinus3C.getBlockHash(), quorumSnapshotAtHMinus3C);
        quorumSnapshotCache.put(mnListAtHMinus4C.getBlockHash(), quorumSnapshotAtHMinus4C);

        int size = (int)readVarInt(); // generally this should be 2, but could be 1
        activeQuorumLists = new HashMap<>(size);
        for (int i = 0; i < size; ++i) {
            SimplifiedQuorumList activeQuorum = new SimplifiedQuorumList(params, payload, cursor, protocolVersion);
            cursor += activeQuorum.getMessageSize();
            activeQuorumLists.put((int)activeQuorum.getHeight(), activeQuorum);
        }
        log.info("after loading, activeQuorumLists has {} lists", activeQuorumLists.size());
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        mnListTip.bitcoinSerialize(stream);
        mnListAtH.bitcoinSerialize(stream);
        mnListAtHMinusC.bitcoinSerialize(stream);
        mnListAtHMinus2C.bitcoinSerialize(stream);
        mnListAtHMinus3C.bitcoinSerialize(stream);
        if (mnListAtHMinus4C != null) {
            mnListAtHMinus4C.bitcoinSerialize(stream);
        } else {
            new SimplifiedMasternodeList(params).bitcoinSerialize(stream);
        }

        quorumListTip.bitcoinSerialize(stream);
        quorumListAtH.bitcoinSerialize(stream);
        quorumListAtHMinusC.bitcoinSerialize(stream);
        quorumListAtHMinus2C.bitcoinSerialize(stream);
        quorumListAtHMinus3C.bitcoinSerialize(stream);
        if (quorumListAtHMinus4C != null) {
            quorumListAtHMinus4C.bitcoinSerialize(stream);
        } else {
            new SimplifiedQuorumList(params).bitcoinSerialize(stream);
        }

        quorumSnapshotAtHMinusC.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus2C.bitcoinSerialize(stream);
        quorumSnapshotAtHMinus3C.bitcoinSerialize(stream);
        if (quorumSnapshotAtHMinus4C != null) {
            quorumSnapshotAtHMinus4C.bitcoinSerialize(stream);
        } else {
            new QuorumSnapshot(0).bitcoinSerialize(stream);
        }

        // obtain the most recent DIP24 quorum hash lists - top 2
        final int[] mostRecentListHeight = {-1};
        List<Integer> heightList = Lists.newArrayList(activeQuorumLists.keySet().toArray(new Integer [0]));
        Collections.sort(heightList);
        int highestIndex = heightList.size() - 1;
        int nextHighestIndex = highestIndex - 1;
        ArrayList<SimplifiedQuorumList> listsToSave = Lists.newArrayListWithExpectedSize(2);
        if (highestIndex >= 0) {
            listsToSave.add(activeQuorumLists.get(heightList.get(highestIndex)));
        }
        if (nextHighestIndex >= 0) {
            listsToSave.add(activeQuorumLists.get(heightList.get(nextHighestIndex)));
        }
        stream.write(new VarInt(listsToSave.size()).encode());
        for (SimplifiedQuorumList listToSave : listsToSave) {
            listToSave.bitcoinSerialize(stream);
        }
    }

    public SimplifiedMasternodeList getMnListAtH() {
        return mnListAtH;
    }

    public SimplifiedQuorumList getQuorumListAtH() {
        SimplifiedQuorumList topList = getTopActiveQuorumList();
        log.warn("obtaining this quorum list: {} from {} quorum lists", topList, activeQuorumLists.size());
        return topList;
    }

    private SimplifiedQuorumList getTopActiveQuorumList() {
        int max = -1;
        // create an empty list in the case that activeQuorumLists is empty
        SimplifiedQuorumList topList = new SimplifiedQuorumList(params);
        for (Map.Entry<Integer, SimplifiedQuorumList> entry : activeQuorumLists.entrySet()) {
            if (entry.getKey() >= max) {
                max = entry.getKey();
                topList = entry.getValue();
            }
        }
        return topList;
    }

    public SimplifiedQuorumList getQuorumListForBlock(StoredBlock block) {
        int max = -1;
        // create an empty list in the case that activeQuorumLists is empty
        SimplifiedQuorumList topList = new SimplifiedQuorumList(params);
        for (Map.Entry<Integer, SimplifiedQuorumList> entry : activeQuorumLists.entrySet()) {
            if (entry.getKey() >= max && entry.getKey() <= block.getHeight()) {
                max = entry.getKey();
                topList = entry.getValue();
            }
        }
        // if no list was found, use the most recent
        if (topList.getHeight() == -1) {
            topList = getTopActiveQuorumList();
        }
        log.warn("obtaining quorum list {}: {} from {} quorum lists", block.getHeight(), topList, activeQuorumLists.size());
        return topList;
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
                .append("\n -----------Last Quorum Hashes ------------");

        if (lastCommitments != null) {
            for (int i = 0; i < lastCommitments.size(); ++i) {
                builder.append("\n");
                builder.append(i);
                builder.append(": ");
                builder.append(lastCommitments.get(i));
            }
        } else {
            builder.append("No last quorum hashes");
        }


        builder.append("}");

        return builder.toString();
    }

    @Override
    public void processDiff(@Nullable Peer peer, QuorumRotationInfo quorumRotationInfo, AbstractBlockChain headersChain,
                            AbstractBlockChain blockChain, boolean isLoadingBootStrap, PeerGroup.SyncStage syncStage) throws VerificationException {
        long newHeight = ((CoinbaseTx) quorumRotationInfo.getMnListDiffTip().coinBaseTx.getExtraPayloadObject()).getHeight();
        if (peer != null)
            peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Received, quorumRotationInfo.getMnListDiffTip());
        Stopwatch watch = Stopwatch.createStarted();
        boolean isSyncingHeadersFirst = syncStage == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;
        headerChain = headersChain;

        quorumRotationInfo.dump(getMnListTip().getHeight(), newHeight);

        lock.lock();
        try {
            setBlockChain(peerGroup, headersChain, blockChain);
            applyDiff(peer, headersChain, blockChain, quorumRotationInfo, isLoadingBootStrap);

            unCache();
            failedAttempts = 0;

            if (!pendingBlocks.isEmpty()) {
                pendingBlocks.pop();
            } else log.warn("pendingBlocks is empty");

            if (peer != null && isSyncingHeadersFirst)
                peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Finished, quorumRotationInfo.getMnListDiffTip());

        } catch (MasternodeListDiffException x) {
            //we already have this qrinfo or doesn't match our current tipBlockHash
            if (mnListAtH.getBlockHash().equals(quorumRotationInfo.getMnListDiffAtH().blockHash)) {
                log.info("heights are the same: " + x.getMessage(), x);
                log.info("mnList = {} vs qrinfo {}", mnListTip.getBlockHash(), quorumRotationInfo.getMnListDiffTip().prevBlockHash);
                log.info("mnlistdiff {} -> {}", quorumRotationInfo.getMnListDiffTip().prevBlockHash, quorumRotationInfo.getMnListDiffTip().blockHash);
                log.info("lastRequest: {} -> {}", lastRequest.request.getBaseBlockHashes(), lastRequest.request.getBlockRequestHash());
                // remove this block from the list
                if (!pendingBlocks.isEmpty()) {
                    pendingBlocks.pop();
                }
            } else {
                log.info("heights are different", x);
                log.info("qrinfo height = {}; mnListAtTip: {}; mnListAtH: {}; quorumListAtH: {}", newHeight, getMnListTip().getHeight(),
                        getMnListAtH().getHeight(), getQuorumListAtTip().getHeight());
                log.info("mnList = {} vs qrinfo = {}", mnListTip.getBlockHash(), quorumRotationInfo.getMnListDiffTip().prevBlockHash);
                log.info("qrinfo {} -> {}", quorumRotationInfo.getMnListDiffTip().prevBlockHash, quorumRotationInfo.getMnListDiffTip().blockHash);
                log.info("lastRequest: {} -> {}", lastRequest.request.getBaseBlockHashes(), lastRequest.request.getBlockRequestHash());
                log.info("requires reset {}", x.isRequiringReset());
                log.info("requires new peer {}", x.isRequiringNewPeer());
                log.info("requires reset {}", x.hasMerkleRootMismatch());
                incrementFailedAttempts();
                log.info("failed attempts {}", getFailedAttempts());
                if (reachedMaxFailedAttempts()) {
                    resetMNList(true);
                }
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
            log.info(x.getMessage(), x);
            failedAttempts++;
            throw new ProtocolException(x);
        } finally {
            watch.stop();
            log.info("processing qrinfo: Total: {} mnlistdiff: {}", watch, quorumRotationInfo.getMnListDiffTip());
            log.info(toString());
            waitingForMNListDiff = false;
            if (!initChainTipSyncComplete) {
                log.info("initChainTipSync=false");
                initChainTipSyncComplete = true;
                log.info("initChainTipSync=true");
            }
            requestNextMNListDiff();
            lock.unlock();
        }
    }

    public boolean isConsistent() {
        return mnListAtH.getBlockHash().equals(quorumListAtH.getBlockHash());
    }

    public SimplifiedQuorumList getActiveQuorumList() {
        return activeQuorumLists.values().stream().findFirst().get();
    }
}
