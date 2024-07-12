/*
 * Copyright 2019 Dash Core Group
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.AbstractManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.DualBlockChain;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.quorums.ChainLocksHandler;
import org.bitcoinj.quorums.FinalCommitment;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.Quorum;
import org.bitcoinj.quorums.QuorumManager;
import org.bitcoinj.quorums.QuorumRotationInfo;
import org.bitcoinj.quorums.QuorumSnapshotManager;
import org.bitcoinj.quorums.SigningManager;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.utils.ContextPropagatingThreadFactory;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class manages the state of the masternode lists and quorums.  It does so with help from
 * {@link QuorumState} for DIP4 quorums and {@link QuorumRotationState} for DIP24 quorums
 *
 * Additionally, this class manages the bootstrap system and the persistence of data through its base
 * class {@link AbstractManager}
 */

public class SimplifiedMasternodeListManager extends AbstractManager implements QuorumStateManager {
    private static final Logger log = LoggerFactory.getLogger(SimplifiedMasternodeListManager.class);
    private final ReentrantLock lock = Threading.lock("SimplifiedMasternodeListManager");

    public static final int DMN_FORMAT_VERSION = 1;
    public static final int LLMQ_FORMAT_VERSION = 2;
    public static final int QUORUM_ROTATION_FORMAT_VERSION = 3;
    public static final int BLS_SCHEME_FORMAT_VERSION = 4;
    public static final int SMLE_VERSION_FORMAT_VERSION = 5;

    public static final int MAX_CACHE_SIZE = 10;
    public static final int MIN_CACHE_SIZE = 1;
    private ExecutorService threadPool = Executors.newFixedThreadPool(1, new ContextPropagatingThreadFactory("process-qrinfo"));

    public List<Quorum> getAllQuorums(LLMQParameters.LLMQType llmqType) {
        ArrayList<Quorum> list = Lists.newArrayList();

        for (Map.Entry<Sha256Hash, SimplifiedQuorumList> entry: getQuorumListCache(llmqType).entrySet()) {
            entry.getValue().forEachQuorum(true, new SimplifiedQuorumList.ForeachQuorumCallback() {
                @Override
                public void processQuorum(FinalCommitment finalCommitment) {
                    list.add(new Quorum(LLMQParameters.fromType(llmqType), finalCommitment));
                }
            });

        }
        return list;
    }

    public int getBlockHeight(Sha256Hash quorumHash) {
        return blockChain.getBlockHeight(quorumHash);
    }

    public StoredBlock getBlockTip() {
        return blockChain.getChainHead();
    }

    public void waitForBootstrapLoaded() throws ExecutionException, InterruptedException {
        if (quorumState.bootStrapLoaded != null) {
            quorumState.bootStrapLoaded.get();
        }
        if (quorumRotationState.bootStrapLoaded != null) {
            quorumRotationState.bootStrapLoaded.get();
        }
    }

    public enum SaveOptions {
        SAVE_EVERY_BLOCK,
        SAVE_EVERY_CHANGE,
    }

    public SaveOptions saveOptions;

    long tipHeight;
    Sha256Hash tipBlockHash;

    DualBlockChain blockChain;

    boolean loadedFromFile;
    boolean requiresLoadingFromFile;

    PeerGroup peerGroup;

    QuorumSnapshotManager quorumSnapshotManager;
    QuorumManager quorumManager;

    QuorumState quorumState; //before DIP24
    QuorumRotationState quorumRotationState; //DIP24

    public SimplifiedMasternodeListManager(Context context) {
        super(context);
        tipBlockHash = params.getGenesisBlock().getHash();

        saveOptions = SaveOptions.SAVE_EVERY_CHANGE;
        loadedFromFile = false;
        requiresLoadingFromFile = true;

        quorumState = new QuorumState(context, MasternodeListSyncOptions.SYNC_MINIMUM);
        quorumState.setStateManager(this);
        quorumRotationState = new QuorumRotationState(context);
        quorumRotationState.setStateManager(this);
    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }

    @Override
    public AbstractManager createEmpty() {
        return new SimplifiedMasternodeListManager(Context.get());
    }

    @Override
    public void checkAndRemove() {

    }

    @Override
    public void clear() {

    }

    @Override
    protected void parse() throws ProtocolException {
        protocolVersion = getProtocolVersion();
        quorumState = new QuorumState(context, MasternodeListSyncOptions.SYNC_MINIMUM, payload, cursor, protocolVersion);
        quorumState.setStateManager(this);
        cursor += quorumState.getMessageSize();
        tipBlockHash = readHash();
        tipHeight = readUint32();
        if(getFormatVersion() >= LLMQ_FORMAT_VERSION) {
            cursor += quorumState.parseQuorums(payload, cursor);
            //read pending blocks
            parsePendingBlocks(quorumState);

            if (!quorumState.isConsistent()) {
                log.warn("QuorumState is inconsistent -- clearing state");
                quorumState.clearState();
            }
        }
        if (getFormatVersion() >= QUORUM_ROTATION_FORMAT_VERSION && (cursor < payload.length)) {
            quorumRotationState = new QuorumRotationState(context, payload, cursor, protocolVersion);
            quorumRotationState.setStateManager(this);
            cursor += quorumRotationState.getMessageSize();
        }

        processQuorumList(quorumState.getQuorumListAtTip());
        processQuorumList(quorumRotationState.getQuorumListAtH());
        // now set protocol version to current and file format version to current for future saves
        protocolVersion = NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion();
        setFormatVersion(SMLE_VERSION_FORMAT_VERSION);
        length = cursor - offset;
    }

    public int getProtocolVersion() {
        if (formatVersion >= 5) {
            return NetworkParameters.ProtocolVersion.SMNLE_VERSIONED.getBitcoinProtocolVersion();
        } else if (formatVersion == 4) {
            return NetworkParameters.ProtocolVersion.BLS_SCHEME.getBitcoinProtocolVersion();
        } else {
            return NetworkParameters.ProtocolVersion.ISDLOCK.getBitcoinProtocolVersion();
        }
    }

    private <T extends AbstractQuorumRequest, D extends AbstractDiffMessage> void parsePendingBlocks(AbstractQuorumState<T, D> state) {
        int size = (int)readVarInt();
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
        for(int i = 0; i < size; ++i) {
            buffer.put(readBytes(StoredBlock.COMPACT_SERIALIZED_SIZE));
            buffer.rewind();
            StoredBlock block = StoredBlock.deserializeCompact(params, buffer);
            if(block.getHeight() != 0 && state.syncOptions != MasternodeListSyncOptions.SYNC_MINIMUM) {
                state.pushPendingBlock(block);
            }
            buffer.rewind();
        }
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        lock.lock();
        try {
            SimplifiedMasternodeList mnListToSave = null;
            ArrayList<StoredBlock> otherPendingBlocks = new ArrayList<StoredBlock>(MAX_CACHE_SIZE);
            if (getMasternodeListCache().size() > 0) {
                for (Map.Entry<Sha256Hash, SimplifiedMasternodeList> entry : getMasternodeListCache().entrySet()) {
                    if (mnListToSave == null) {
                        mnListToSave = entry.getValue();
                    } else {
                        otherPendingBlocks.add(entry.getValue().getStoredBlock());
                    }
                }
            } else {
                mnListToSave = quorumState.mnList;
            }

            quorumState.bitcoinSerialize(stream);
            stream.write(mnListToSave.getBlockHash().getReversedBytes());
            Utils.uint32ToByteStreamLE(mnListToSave.getHeight(), stream);

            if (getFormatVersion() >= LLMQ_FORMAT_VERSION) {
                quorumState.serializeQuorumsToStream(stream);
                if (quorumState.syncOptions != MasternodeListSyncOptions.SYNC_MINIMUM) {
                    stream.write(new VarInt((long)quorumState.getPendingBlocks().size() + otherPendingBlocks.size()).encode());
                    ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
                    log.info("saving {} blocks to catch up mnList", otherPendingBlocks.size());
                    for (StoredBlock block : otherPendingBlocks) {
                        block.serializeCompact(buffer);
                        stream.write(buffer.array());
                        buffer.clear();
                    }
                    for (StoredBlock block : quorumState.getPendingBlocks()) {
                        block.serializeCompact(buffer);
                        stream.write(buffer.array());
                        buffer.clear();
                    }
                } else stream.write(new VarInt(0).encode());
            }
            if (getFormatVersion() >= QUORUM_ROTATION_FORMAT_VERSION) {
                quorumRotationState.bitcoinSerialize(stream);
            }

        } finally {
            lock.unlock();
        }
    }

    public void updatedBlockTip(StoredBlock tip) {
    }

    protected boolean shouldProcessMNListDiff() {
        return context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_DMN_LIST) ||
                context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_QUORUM_LIST);
    }

    @Override
    public void processDiffMessage(Peer peer, SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap) {
        processMasternodeListDiff(peer, mnlistdiff, isLoadingBootStrap);
    }

    public void processMasternodeListDiff(Peer peer, SimplifiedMasternodeListDiff mnlistdiff) {
        if(!shouldProcessMNListDiff())
            return;

        processMasternodeListDiff(peer, mnlistdiff, false);
    }

    public void processMasternodeListDiff(@Nullable Peer peer, SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap) {
        try {
            quorumState.processDiff(peer, mnlistdiff, blockChain, isLoadingBootStrap, context.peerGroup.getSyncStage());

            processMasternodeList(mnlistdiff);
            processQuorumList(quorumState.getQuorumListAtTip());

            unCache();
            setFormatVersion(QUORUM_ROTATION_FORMAT_VERSION);

            if (mnlistdiff.hasChanges() || quorumState.getPendingBlocks().size() < MAX_CACHE_SIZE || saveOptions == SaveOptions.SAVE_EVERY_BLOCK)
                save();

            // if DIP24 is not activated, then trigger a getqrinfo
            completeQuorumState(peer);
        } finally {
            // TODO: do we need this finally block?
        }
    }

    public boolean requestQuorumStateUpdate(Peer downloadPeer, StoredBlock requestBlock, StoredBlock requestBlockMinus8) {
        boolean isWaitingForRequest = quorumState.requestMNListDiff(downloadPeer, requestBlockMinus8);
        if (isQuorumRotationEnabled(params.getLlmqDIP0024InstantSend())) {
            quorumRotationState.requestMNListDiff(downloadPeer, requestBlock);
        }
        return isWaitingForRequest;
    }

    public void processDiffMessage(Peer peer, QuorumRotationInfo qrinfo, boolean isLoadingBootStrap, @Nullable SettableFuture<Boolean> opComplete) {
        processQuorumRotationInfo(peer, qrinfo, isLoadingBootStrap, opComplete);
    }

    public void processQuorumRotationInfo(@Nullable Peer peer, QuorumRotationInfo quorumRotationInfo, boolean isLoadingBootStrap, @Nullable SettableFuture<Boolean> opComplete) {

        // process qrinfo asynchronously
        try {
            threadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        quorumRotationState.processDiff(peer, quorumRotationInfo, blockChain, isLoadingBootStrap, PeerGroup.SyncStage.BLOCKS);
                        processMasternodeList(quorumRotationInfo.getMnListDiffAtH());
                        unCache();

                        // save in the most up-to-date version
                        setFormatVersion(SMLE_VERSION_FORMAT_VERSION);

                        if (quorumRotationInfo.hasChanges() || quorumRotationState.getPendingBlocks().size() < MAX_CACHE_SIZE || saveOptions == SimplifiedMasternodeListManager.SaveOptions.SAVE_EVERY_BLOCK)
                            save();
                    } catch (VerificationException e) {
                        log.info("qrinfo verification error:", e);
                    }
                    if (opComplete != null)
                        opComplete.set(true);
                }
            });
        } catch (RejectedExecutionException x) {
            log.error("cannot process qrinfo:", x);
        }
    }

    // TODO: does this need an argument for LLQMType?
    public SimplifiedMasternodeList getMasternodeList() {
        if (isQuorumRotationEnabled(params.getLlmqDIP0024InstantSend())) {
            return quorumRotationState.getMnListTip();
        } else {
            return quorumState.getMnList();
        }
    }

    // TODO: does this need an argument for LLQMType?
    Map<Sha256Hash, SimplifiedMasternodeList> getMasternodeListCache() {
        if (isQuorumRotationEnabled(params.getLlmqDIP0024InstantSend())) {
            return quorumRotationState.getMasternodeListCache();
        } else {
            return quorumState.getMasternodeListCache();
        }
    }

    // TODO: does this need an argument for LLQMType?
    public Map<Sha256Hash, SimplifiedQuorumList> getQuorumListCache(LLMQParameters.LLMQType llmqType) {
        if (isQuorumRotationEnabled(llmqType)) {
            return quorumRotationState.getQuorumsCache();
        } else {
            return quorumState.getQuorumsCache();
        }
    }

    private void resetQuorumState() {
        quorumState.clearState();
        quorumRotationState.clearState();
    }

    public void setBlockChain(DualBlockChain blockChain, @Nullable PeerGroup peerGroup,
                              QuorumManager quorumManager, QuorumSnapshotManager quorumSnapshotManager, ChainLocksHandler chainLocksHandler) {
        this.blockChain = blockChain;
        this.peerGroup = peerGroup;
        this.quorumManager = quorumManager;
        this.quorumSnapshotManager = quorumSnapshotManager;
        quorumState.setBlockChain(peerGroup, blockChain);
        quorumRotationState.setBlockChain(peerGroup, blockChain);
        quorumState.setChainLocksHandler(chainLocksHandler);
        quorumRotationState.setChainLocksHandler(chainLocksHandler);
        if(shouldProcessMNListDiff()) {
            quorumState.addEventListeners(blockChain.getBlockChain(), peerGroup);
            quorumRotationState.addEventListeners(blockChain.getBlockChain(), peerGroup);
        }
        if (threadPool.isShutdown()) {
            threadPool = Executors.newFixedThreadPool(1, new ContextPropagatingThreadFactory("process-qrinfo"));
        }
    }

    @Override
    public void close() {
        if (shouldProcessMNListDiff()) {
            // TODO: refactor the next several lines into AbstractQuorumState.close(...)
            quorumState.removeEventListeners(blockChain.getBlockChain(), peerGroup);
            quorumRotationState.removeEventListeners(blockChain.getBlockChain(), peerGroup);
            // reset state of chain sync
            quorumState.close();
            quorumRotationState.close();

            try {
                threadPool.shutdown();
                threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException x) {
                // swallow
            }
            saveNow();
            super.close();

        }
    }

    @Override
    public String toString() {
        StoredBlock firstPending = !quorumRotationState.pendingBlocks.isEmpty() ? quorumRotationState.pendingBlocks.peek() : null;
        int height = firstPending != null ? firstPending.getHeight() : -1;
        return "SimplifiedMNListManager:  {tip:" + getMasternodeList() +
                ", " + getQuorumListAtTip(params.getLlmqChainLocks()) +
                ", " + getQuorumListAtTip(params.getLlmqDIP0024InstantSend()) +
                ", pending blocks: " + quorumState.getPendingBlocks().size() +  " / " +
                quorumRotationState.getPendingBlocks().size() + (height != -1?  ("-->height: "+height+")") : "" ) +"}";
    }

    public boolean isQuorumRotationEnabled(LLMQParameters.LLMQType type) {
        if (blockChain == null) {
            return formatVersion == QUORUM_ROTATION_FORMAT_VERSION;
        }
        boolean quorumRotationActive = blockChain.getBestChainHeight() >= params.getDIP0024BlockHeight();
        return params.getLlmqDIP0024InstantSend() == type && quorumRotationActive;
    }

    public SimplifiedMasternodeList getListAtChainTip() {
        if (quorumState.getMasternodeListAtTip() != null) {
            return quorumState.getMasternodeListAtTip();
        } else {
            return quorumRotationState.getMnListAtH();
        }
    }

    public SimplifiedQuorumList getQuorumListAtTip(LLMQParameters.LLMQType llmqType) {
        if (!isQuorumRotationEnabled(llmqType)) {
            return quorumState.quorumList;
        } else {
            return quorumRotationState.getQuorumListAtH();
        }
    }

    @Override
    public int getCurrentFormatVersion() {
        return getQuorumListAtTip(params.getLlmqDIP0024InstantSend()).size() != 0 ? LLMQ_FORMAT_VERSION : DMN_FORMAT_VERSION;
    }

    public void resetMNList(boolean force, boolean requestFreshList) {
        quorumState.resetMNList(force, requestFreshList);
        quorumRotationState.resetMNList(force, requestFreshList);
    }

    @VisibleForTesting
    void resetQuorumStateMNList(boolean force, boolean requestFreshList) {
        quorumState.resetMNList(force, requestFreshList);
    }

    public SimplifiedMasternodeList getListForBlock(Sha256Hash blockHash) {
        lock.lock();
        try {
            return getMasternodeListCache().get(blockHash);
        } finally {
            lock.unlock();
        }
    }

    public SimplifiedQuorumList getQuorumListForBlock(Sha256Hash blockHash, LLMQParameters.LLMQType llmqType) {
        lock.lock();
        try {
            // TODO: Chainlocks problems are from this?
            if (isQuorumRotationEnabled(llmqType)) {
                LLMQParameters llmqParameters = params.getLlmqs().get(llmqType);
                StoredBlock block = blockChain.getBlock(blockHash);
                StoredBlock lastQuorumBlock = blockChain.getBlockAncestor(block,
                        block.getHeight() - block.getHeight() % llmqParameters.getDkgInterval() - SigningManager.SIGN_HEIGHT_OFFSET);
                if (lastQuorumBlock == null) {
                    log.info("last quorum block is null");
                    return null;
                }

                return quorumRotationState.getQuorumListForBlock(lastQuorumBlock);
            } else {
                return getQuorumListCache(llmqType).get(blockHash);
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash)
    {
        if (isQuorumRotationEnabled(llmqType)) {
            return quorumRotationState.getAllQuorumMembers(llmqType, blockHash);
        } else {
            return quorumState.getAllQuorumMembers(llmqType, blockHash);
        }
    }

    public boolean isSynced() {
        // TODO: there is a problem with QRS isSynced, there is often a pending block
        // in the quorumRotationState
        // && quorumRotationState.isSynced()
        return quorumState.isSynced() && quorumRotationState.isSynced();
    }

    public boolean isSyncedForInstantSend() {
        return quorumRotationState.isSynced();
    }

    public void setRequiresLoadingFromFile(boolean requiresLoadingFromFile) {
        this.requiresLoadingFromFile = requiresLoadingFromFile;
    }

    public void setLoadedFromFile(boolean loadedFromFile) {
        this.loadedFromFile = loadedFromFile;
    }

    @Override
    public void save() {
        saveLater();
    }

    public boolean isLoadedFromFile() {
        return loadedFromFile || !requiresLoadingFromFile;
    }

    @Override
    public void onFirstSaveComplete() {
        quorumState.onFirstSaveComplete();
        quorumRotationState.onFirstSaveComplete();
    }

    static String mnlistdiffBootStrapFilePath = null;
    static String qrinfoBootStrapFilePath = null;
    static InputStream mnlistdiffBootStrapStream = null;
    static InputStream qrinfoBootStrapStream = null;
    static int bootStrapFileFormat = 0;

    public void setBootstrap(String mnlistdiffFilePath, String qrinfoFilepath, int format) {
        SimplifiedMasternodeListManager.mnlistdiffBootStrapFilePath = mnlistdiffFilePath;
        SimplifiedMasternodeListManager.qrinfoBootStrapFilePath = qrinfoFilepath;
        bootStrapFileFormat = format;
        quorumState.setBootstrap(mnlistdiffFilePath, null, format);
        quorumRotationState.setBootstrap(qrinfoFilepath, null, format);
    }

    public void setBootstrap(InputStream mnlistdiffStream, InputStream qrinfoStream, int format) {
        SimplifiedMasternodeListManager.mnlistdiffBootStrapStream = mnlistdiffStream;
        SimplifiedMasternodeListManager.qrinfoBootStrapStream = qrinfoStream;
        bootStrapFileFormat = format;
        quorumState.setBootstrap(null, mnlistdiffStream, format);
        quorumRotationState.setBootstrap(null, qrinfoStream, format);
    }

    @Deprecated
    public static void setBootStrapFilePath(String mnlistdiffFilePath,String qrinfoFilepath, int format) {
        SimplifiedMasternodeListManager.mnlistdiffBootStrapFilePath = mnlistdiffFilePath;
        SimplifiedMasternodeListManager.qrinfoBootStrapFilePath = qrinfoFilepath;
        bootStrapFileFormat = format;
    }
    @Deprecated
    public static void setBootStrapStream(InputStream mnlistdiffStream, InputStream qrinfoStream, int format) {
        SimplifiedMasternodeListManager.mnlistdiffBootStrapStream = mnlistdiffStream;
        SimplifiedMasternodeListManager.qrinfoBootStrapStream = qrinfoStream;
        bootStrapFileFormat = format;
    }

    /**
     * @param quorumList the most recent quorum list from a "mnlistdiff" message
     */
    @Deprecated
    public void processQuorumList(SimplifiedQuorumList quorumList) {
        // this was previously used to detect hard forks
    }

    @Deprecated
    public void processMasternodeList(SimplifiedMasternodeListDiff mnlistdiff) {
        // this was previously used to detect hard forks
    }

    public void completeQuorumState(Peer peer) {
        if (quorumRotationState.getQuorumListAtH().size() == 0 && isQuorumRotationEnabled(params.getLlmqDIP0024InstantSend())) {
            quorumRotationState.requestMNListDiff(peer, getBlockTip());
        }
    }

    // TODO: this lock may not be doing much of anything since most functionality was moved to
    // AbstractQuorumState
    @Deprecated
    public ReentrantLock getLock() {
        return lock;
    }
}
