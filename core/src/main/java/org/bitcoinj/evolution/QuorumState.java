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
import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.evolution.listeners.MasternodeListDownloadedListener;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.quorums.SigningManager;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class QuorumState extends AbstractQuorumState<GetSimplifiedMasternodeListDiff, SimplifiedMasternodeListDiff> {
    private static final Logger log = LoggerFactory.getLogger(QuorumState.class);

    SimplifiedMasternodeList mnList;
    SimplifiedQuorumList quorumList;

    LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> mnListsCache = new LinkedHashMap<Sha256Hash, SimplifiedMasternodeList>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, SimplifiedMasternodeList> eldest) {
            return size() > (syncOptions == MasternodeListSyncOptions.SYNC_MINIMUM ? SimplifiedMasternodeListManager.MIN_CACHE_SIZE : SimplifiedMasternodeListManager.MAX_CACHE_SIZE);
        }
    };

    LinkedHashMap<Sha256Hash, SimplifiedQuorumList> quorumsCache = new LinkedHashMap<Sha256Hash, SimplifiedQuorumList>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, SimplifiedQuorumList> eldest) {
            return size() > (syncOptions == MasternodeListSyncOptions.SYNC_MINIMUM ? SimplifiedMasternodeListManager.MIN_CACHE_SIZE : SimplifiedMasternodeListManager.MAX_CACHE_SIZE);
        }
    };
    public QuorumState(Context context, MasternodeListSyncOptions syncOptions) {
        super(context);
        this.context = context;
        params = context.getParams();
        this.syncOptions = syncOptions;
        init();
    }

    public QuorumState(Context context, MasternodeListSyncOptions syncOptions, byte [] payload, int offset, int protocolVersion) {
        super(context.getParams(), payload, offset, protocolVersion);
        this.context = context;
        this.syncOptions = syncOptions;
        finishInitialization();
    }

    void init() {
        mnList = new SimplifiedMasternodeList(context.getParams());
        mnListsCache.put(mnList.getBlockHash(), mnList);
        quorumList = new SimplifiedQuorumList(context.getParams());
        quorumsCache.put(quorumList.getBlockHash(), quorumList);
        finishInitialization();
    }

    void finishInitialization() {
        this.lastRequest = new QuorumUpdateRequest<>(new GetSimplifiedMasternodeListDiff(Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH));
    }

    @Override
    protected void clearState() {
        super.clearState();
        init();
    }

    @Override
    SimplifiedMasternodeListDiff loadDiffMessageFromBuffer(byte[] buffer, int protocolVersion) {
        return new SimplifiedMasternodeListDiff(params, buffer, protocolVersion);
    }

    @Override
    public void requestReset(Peer peer, StoredBlock nextBlock) {
        lastRequest = new QuorumUpdateRequest<>(new GetSimplifiedMasternodeListDiff(params.getGenesisBlock().getHash(),
                nextBlock.getHeader().getHash()));
        sendRequestWithRetry(peer);
    }

    @Override
    public void requestUpdate(Peer peer, StoredBlock nextBlock) {
        if (!params.isDIP24Only()) {
            lastRequest = new QuorumUpdateRequest<>(getMasternodeListDiffRequest(nextBlock));
            sendRequestWithRetry(peer);
        }
    }

    public void applyDiff(Peer peer, AbstractBlockChain headersChain, AbstractBlockChain blockChain,
                          SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap)
            throws BlockStoreException, MasternodeListDiffException {
        StoredBlock block;
        boolean isSyncingHeadersFirst = context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;

        long newHeight = ((CoinbaseTx) mnlistdiff.coinBaseTx.getExtraPayloadObject()).getHeight();
        block = chain.getBlockStore().get(mnlistdiff.blockHash);
        if (block == null) {
            // this should be improved.  isSyncingHeadersFirst could be false, but blockChain may not be synced
            // so we need to check the headersChain
            block = headersChain.getBlockStore().get(mnlistdiff.blockHash);
            if (block != null) {
                chain = headersChain;
            }
        }
        if(!isLoadingBootStrap && block.getHeight() != newHeight)
            throw new ProtocolException("mnlistdiff blockhash (height="+block.getHeight()+" doesn't match coinbase blockheight: " + newHeight);

        if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Processing, mnlistdiff);

        SimplifiedMasternodeList newMNList = mnList.applyDiff(mnlistdiff);
        if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_MNLIST))
            newMNList.verify(mnlistdiff.coinBaseTx, mnlistdiff, mnList);
        if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedMasternodes, mnlistdiff);
        newMNList.setBlock(block, block != null && block.getHeader().getPrevBlockHash().equals(mnlistdiff.prevBlockHash));

        SimplifiedQuorumList newQuorumList = quorumList;
        if(mnlistdiff.coinBaseTx.getExtraPayloadObject().getVersion() >= SimplifiedMasternodeListManager.LLMQ_FORMAT_VERSION) {
            newQuorumList = quorumList.applyDiff(mnlistdiff, isLoadingBootStrap, chain, false, true);
            if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_QUORUM))
                newQuorumList.verify(mnlistdiff.coinBaseTx, mnlistdiff, quorumList, newMNList);
        } else {
            quorumList.syncWithMasternodeList(newMNList);
        }
        if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedQuorums, mnlistdiff);

        // save the current state, if both mnLists and quorums are both applied
        mnListsCache.put(newMNList.getBlockHash(), newMNList);
        mnList = newMNList;
        quorumsCache.put(newQuorumList.getBlockHash(), newQuorumList);
        quorumList = newQuorumList;
    }

    @Override
    int getBlockHeightOffset() {
        return SigningManager.SIGN_HEIGHT_OFFSET;
    }

    @Override
    public int getUpdateInterval() {
        return 1;
    }

    @Override
    boolean needsUpdate(StoredBlock nextBlock) {
        return nextBlock.getHeight() > mnList.getHeight();
    }

    public SimplifiedMasternodeList getMnList() {
        return mnList;
    }

    public GetSimplifiedMasternodeListDiff getMasternodeListDiffRequest(StoredBlock nextBlock) {
        return new GetSimplifiedMasternodeListDiff(mnList.getBlockHash(),
                nextBlock.getHeader().getHash());
    }

    public ArrayList<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash) {
        LLMQParameters llmqParameters = params.getLlmqs().get(llmqType);
        SimplifiedMasternodeList allMns = getListForBlock(blockHash);
        if (allMns != null) {
            Sha256Hash modifier = LLMQUtils.buildLLMQBlockHash(llmqType, blockHash);
            boolean hmpnOnly = (params.getLlmqPlatform() == llmqType) && params.isV19Active(allMns.getStoredBlock());
            return allMns.calculateQuorum(llmqParameters.getSize(), modifier, hmpnOnly);
        }
        return null;
    }


    public SimplifiedMasternodeList getListForBlock(Sha256Hash blockHash) {
        return mnListsCache.get(blockHash);
    }

    @Override
    protected void parse() throws ProtocolException {
        mnList = new SimplifiedMasternodeList(params, payload, cursor, protocolVersion);
        cursor += mnList.getMessageSize();

        // specify an empty quorumList for now
        quorumList = new SimplifiedQuorumList(params);
        length = cursor - offset;
    }

    public int parseQuorums(byte [] payload, int offset) {
        quorumList = new SimplifiedQuorumList(params, payload, offset, protocolVersion);
        return quorumList.getMessageSize();
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        mnList.bitcoinSerialize(stream);
    }

    public void serializeQuorumsToStream(OutputStream stream) throws IOException {
        quorumList.bitcoinSerialize(stream);
    }

    @Override
    public SimplifiedMasternodeList getMasternodeList() {
        return mnList;
    }

    @Override
    public SimplifiedMasternodeList getMasternodeListAtTip() {
        return mnList;
    }

    public LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> getMasternodeListCache() {
        return mnListsCache;
    }

    public LinkedHashMap<Sha256Hash, SimplifiedQuorumList> getQuorumsCache() {
        return quorumsCache;
    }

    @Override
    public SimplifiedQuorumList getQuorumListAtTip() {
        return quorumList;
    }

    @Override
    public void processDiff(@Nullable Peer peer, SimplifiedMasternodeListDiff mnlistdiff, AbstractBlockChain headersChain,
                            AbstractBlockChain blockChain, boolean isLoadingBootStrap) throws VerificationException {
        if (mnlistdiff.getVersion() == SimplifiedMasternodeListDiff.BASIC_BLS_VERSION) {
            BLSScheme.setLegacyDefault(false);
        }
        StoredBlock block = null;
        long newHeight = ((CoinbaseTx) mnlistdiff.coinBaseTx.getExtraPayloadObject()).getHeight();
        if (peer != null) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Received, mnlistdiff);
        Stopwatch watch = Stopwatch.createStarted();
        Stopwatch watchMNList = Stopwatch.createUnstarted();
        Stopwatch watchQuorums = Stopwatch.createUnstarted();
        boolean isSyncingHeadersFirst = context.peerGroup != null && context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        this.headerChain = headersChain;
        log.info("processing {} mnlistdiff between : {} & {}; {}",
                isLoadingBootStrap ? "bootstrap" : "requested",
                getMnList().getHeight(), newHeight, mnlistdiff);

        mnlistdiff.dump(mnList.getHeight(), newHeight);

        lock.lock();
        try {
            applyDiff(peer, headersChain, blockChain, mnlistdiff, isLoadingBootStrap);

            log.info(this.toString());
            unCache();
            clearFailedAttempts();

            if(!pendingBlocks.isEmpty()) {
                // remove the first pending block
                popPendingBlock();
            } else log.warn("pendingBlocks is empty");

            if (peer != null && isSyncingHeadersFirst)
                peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Finished, mnlistdiff);

        } catch(MasternodeListDiffException x) {
            // we already have this mnlistdiff or doesn't match our current tipBlockHash
            if(getMnList().getBlockHash().equals(mnlistdiff.blockHash)) {
                log.info("heights are the same:  " + x.getMessage());
                log.info("mnList = {} vs mnlistdiff {}", getMnList().getBlockHash(), mnlistdiff.prevBlockHash);
                log.info("mnlistdiff {} -> {}", mnlistdiff.prevBlockHash, mnlistdiff.blockHash);
                log.info("lastRequest {} -> {}", lastRequest.request.baseBlockHash, lastRequest.request.blockHash);
                // remove this block from the list
                if(getPendingBlocks().size() > 0) {
                    StoredBlock thisBlock = getPendingBlocks().get(0);
                    if(thisBlock.getHeader().getPrevBlockHash().equals(mnlistdiff.prevBlockHash) &&
                            thisBlock.getHeader().getHash().equals(mnlistdiff.prevBlockHash)) {
                        popPendingBlock();
                    }
                }
            } else {
                log.info("heights are different " + x.getMessage());
                log.info("mnlistdiff height = {}; mnList: {}; quorumList: {}", newHeight, getMnList().getHeight(), quorumList.getHeight());
                log.info("mnList = {} vs mnlistdiff = {}", getMnList().getBlockHash(), mnlistdiff.prevBlockHash);
                log.info("mnlistdiff {} -> {}", mnlistdiff.prevBlockHash, mnlistdiff.blockHash);
                log.info("lastRequest {} -> {}", lastRequest.request.baseBlockHash, lastRequest.request.blockHash);
                if (x.requireReset && x.merkleRootMismatch) {
                    resetMNList(true);
                } else {
                    incrementFailedAttempts();
                    log.info("failed attempts {}", getFailedAttempts());
                    if (reachedMaxFailedAttempts())
                        resetMNList(true);
                }
            }
        } catch(VerificationException x) {
            //request this block again and close this peer
            log.info("verification error: close this peer" + x.getMessage());
            incrementFailedAttempts();
            throw x;
        } catch(NullPointerException x) {
            log.info("NPE: close this peer", x);
            incrementFailedAttempts();
            throw new VerificationException("verification error: " + x.getMessage());
        } catch(BlockStoreException x) {
            log.info(x.getMessage());
            incrementFailedAttempts();
            throw new ProtocolException(x);
        } finally {
            watch.stop();
            log.info("processing mnlistdiff times : Total: " + watch + "mnList: " + watchMNList + " quorums" + watchQuorums + "mnlistdiff" + mnlistdiff);
            waitingForMNListDiff = false;
            if (isSyncingHeadersFirst) {
                if (downloadPeer != null) {
                    log.info("initChainTipSync=false");
                    context.peerGroup.triggerMnListDownloadComplete();
                    log.info("initChainTipSync=true");
                    initChainTipSyncComplete = true;
                } else {
                    context.peerGroup.triggerMnListDownloadComplete();
                    initChainTipSyncComplete = true;
                }
            }
            requestNextMNListDiff();
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "QuorumState{" +
                "mnList=" + mnList +
                ", quorumList=" + quorumList +
                ", mnListsCacheSize=" + mnListsCache.size() +
                ", quorumsCacheSize=" + quorumsCache.size() +
                '}';
    }

    @Override
    public boolean isConsistent() {
        return mnList.getBlockHash().equals(quorumList.getBlockHash());
    }
}
