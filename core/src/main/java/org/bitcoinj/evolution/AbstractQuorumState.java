/*
 * Copyright 2022 Dash Core Group
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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.BlockQueue;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.DualBlockChain;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.listeners.ChainDownloadStartedEventListener;
import org.bitcoinj.core.listeners.HeadersDownloadStartedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.core.listeners.ReorganizeListener;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.quorums.ChainLockSignature;
import org.bitcoinj.quorums.ChainLocksHandler;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.quorums.QuorumRotationInfo;
import org.bitcoinj.quorums.SigningManager;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The abstract base class of all Quorum State classes.  This class handles sync of masternode and quorum lists
 *
 * @param <Request>     A class derived from {@link AbstractQuorumRequest} used as the message to request updates
 *                      to the masternode and quorum lists
 * @param <DiffMessage> A class derived from {@link AbstractDiffMessage} used as the message that contains updates
 *                      *           to the masternode and quorum lists
 */

public abstract class AbstractQuorumState<Request extends AbstractQuorumRequest, DiffMessage extends AbstractDiffMessage> extends Message {

    private static final Random random = new Random();
    public static final int SNAPSHOT_LIST_PERIOD = 576; // once per day
    public static final int LISTS_CACHE_SIZE = 576;
    public static final int SNAPSHOT_TIME_PERIOD = 60 * 60 * 26;

    public static final int MAX_CACHE_SIZE = 10;
    public static final int MIN_CACHE_SIZE = 1;

    private static final Logger log = LoggerFactory.getLogger(AbstractQuorumState.class);
    protected final ReentrantLock lock = Threading.lock("AbstractQuorumState");

    Context context;
    DualBlockChain blockChain;
    protected PeerGroup peerGroup;
    //protected BlockStore headerStore;
    //protected BlockStore blockStore;

    QuorumUpdateRequest<Request> lastRequest;

    static final long WAIT_GETMNLISTDIFF = 5;
    Peer downloadPeer;
    boolean waitingForMNListDiff;
    boolean initChainTipSyncComplete = false;
    BlockQueue pendingBlocks = new BlockQueue();

    int failedAttempts;
    static final int MAX_ATTEMPTS = 3;

    public MasternodeListSyncOptions syncOptions;
    public int syncInterval;

    QuorumStateManager stateManager;
    ChainLocksHandler chainLocksHandler;

    // bootstrap
    String bootstrapFilePath = null;
    InputStream bootstrapStream = null;
    int bootStrapFileFormat = 0;
    public SettableFuture<Boolean> bootStrapLoaded;

    boolean isLoadingBootstrap = false;


    public AbstractQuorumState(Context context) {
        super(context.getParams());
        this.context = context;
        initializeOnce();
        initialize();
    }

    public AbstractQuorumState(NetworkParameters params, byte[] payload, int offset, int protocolVersion) {
        super(params, payload, offset, protocolVersion);
        initialize();
    }

    private void initializeOnce() {
        syncOptions = MasternodeListSyncOptions.SYNC_MINIMUM;
        syncInterval = 8;
    }

    private void initialize() {
        waitingForMNListDiff = false;
    }

    public void setStateManager(QuorumStateManager stateManager) {
        this.stateManager = stateManager;
    }

    public void setChainLocksHandler(ChainLocksHandler chainLocksHandler) {
        this.chainLocksHandler = chainLocksHandler;
    }

    public void setBootstrap(String bootstrapFilePath, InputStream bootstrapStream, int bootStrapFileFormat) {
        this.bootstrapFilePath = bootstrapFilePath;
        this.bootstrapStream = bootstrapStream;
        this.bootStrapFileFormat = bootStrapFileFormat;
        if (bootStrapFileFormat == SimplifiedMasternodeListManager.SMLE_VERSION_FORMAT_VERSION) {
            protocolVersion = NetworkParameters.ProtocolVersion.SMNLE_VERSIONED.getBitcoinProtocolVersion();
        } else if (bootStrapFileFormat == SimplifiedMasternodeListManager.BLS_SCHEME_FORMAT_VERSION) {
            protocolVersion = NetworkParameters.ProtocolVersion.DMN_TYPE.getBitcoinProtocolVersion();
        } else if (bootStrapFileFormat == SimplifiedMasternodeListManager.QUORUM_ROTATION_FORMAT_VERSION) {
            protocolVersion = NetworkParameters.ProtocolVersion.BLS_LEGACY.getBitcoinProtocolVersion();
        } else {
            protocolVersion = NetworkParameters.ProtocolVersion.CORE17.getBitcoinProtocolVersion();
        }
    }

    // TODO: Do we need to keep track of the header chain also?
    public void setBlockChain(PeerGroup peerGroup, DualBlockChain blockChain) {
        this.blockChain = blockChain;
        if (peerGroup != null) {
            this.peerGroup = peerGroup;
            peerGroup.addMnListDownloadCompleteListener(() -> initChainTipSyncComplete = true, Threading.SAME_THREAD);
        }
    }

    protected void pushPendingBlock(StoredBlock block) {
        pendingBlocks.add(block);
    }
//
    public BlockQueue getPendingBlocks() {
        return pendingBlocks;
    }

    public void clearFailedAttempts() {
        failedAttempts = 0;
    }

    public void incrementFailedAttempts() {
        ++failedAttempts;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public boolean reachedMaxFailedAttempts() {
        return failedAttempts > MAX_ATTEMPTS;
    }

    abstract int getBlockHeightOffset();

    public abstract int getUpdateInterval();

    abstract public boolean isConsistent();

    abstract boolean needsUpdate(StoredBlock nextBlock);

    public abstract void processDiff(@Nullable Peer peer, DiffMessage difference,
                                     DualBlockChain blockChain,
                                     boolean isLoadingBootStrap, PeerGroup.SyncStage syncStage)
            throws VerificationException;

    public abstract void requestReset(Peer peer, StoredBlock block);

    public abstract void requestUpdate(Peer peer, StoredBlock block);

    public void retryLastUpdate(Peer peer) {
        if (peer != null) {
            peer.sendMessage(lastRequest.getRequestMessage());
        } else {
            log.info("no peer supplied to retry the last update request: {}", lastRequest.request);
        }
    }

    public abstract SimplifiedMasternodeList getListForBlock(Sha256Hash blockHash);

    public abstract SimplifiedMasternodeList getMasternodeList();

    public abstract SimplifiedMasternodeList getMasternodeListAtTip();

    public abstract LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> getMasternodeListCache();

    public abstract LinkedHashMap<Sha256Hash, SimplifiedQuorumList> getQuorumsCache();

    public abstract SimplifiedQuorumList getQuorumListAtTip();

    public abstract ArrayList<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash);

    public void resetMNList() {
        resetMNList(false, true);
    }

    public void resetMNList(boolean force) {
        resetMNList(force, true);
    }

    public void resetMNList(boolean force, boolean requestFreshList) {
        try {
            if (force) {
                log.info("resetting masternode list; force: {}, requestFreshList: {}", force, requestFreshList);
                clearState();
                pendingBlocks.clear();
                waitingForMNListDiff = false;
                unCache();
                if (notUsingBootstrapFile())
                    stateManager.save();

                if (requestFreshList) {
                    if (notUsingBootstrapFileAndStream()) {
                        requestAfterMNListReset();
                    } else {
                        waitingForMNListDiff = true;
                        setLoadingBootstrap();
                        loadBootstrapAndSync();
                    }
                }
            }
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    protected void requestAfterMNListReset() throws BlockStoreException {
        if (blockChain == null) //not initialized
            return;
        int rewindBlockCount = syncOptions == MasternodeListSyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_LIST_PERIOD : MAX_CACHE_SIZE;
        int height = blockChain.getBestChainHeight() - rewindBlockCount;
        if (height < params.getDIP0008BlockHeight())
            height = params.getDIP0008BlockHeight();
        int currentHeight = blockChain.getBestChainHeight();
        if (syncOptions == MasternodeListSyncOptions.SYNC_MINIMUM && currentHeight != 0) {
            height = currentHeight - getBlockHeightOffset();
        } else {
            height = currentHeight;
        }
        StoredBlock resetBlock = blockChain.getBlock(height);
        if (resetBlock == null)
            resetBlock = blockChain.getChainHead();
        requestMNListDiff(resetBlock != null ? resetBlock : blockChain.getChainHead());
    }

    public void requestMNListDiff(StoredBlock block) {
        requestMNListDiff(null, block);
    }

    public void requestMNListDiff(Peer peer, StoredBlock block) {
        Sha256Hash hash = block.getHeader().getHash();

        if (block.getHeader().getTimeSeconds() < Utils.currentTimeSeconds() - SNAPSHOT_TIME_PERIOD)
            return;

        if (failedAttempts > MAX_ATTEMPTS) {
            log.info("failed attempts maximum reached");
            failedAttempts = 0;
            resetMNList(true);
        }

        if (!pendingBlocks.contains(block)) {
            log.info("adding 1 block to the {} pending queue of size: {} : {}/{}", lastRequest.request.getClass().getSimpleName(), pendingBlocks.size(), block.getHeight(), block.getHeader().getHash());
            pendingBlocks.add(block);
        } else {
            log.info("block {} at {} is already in the pendingBlocksMap", block.getHeader().getHash(), block.getHeight());
        }

        if (!waitingForMNListDiff) {
            log.info("requesting: next");
            requestNextMNListDiff();
        } else {
            log.info("waiting for the last mnlistdiff/qrinfo");
        }

        if (lastRequest.getTime() + WAIT_GETMNLISTDIFF * 4 < Utils.currentTimeSeconds()) {
            log.info("requesting: fresh");
            maybeGetMNListDiffFresh();
        }

    }

    protected boolean shouldProcessMNListDiff() {
        return context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_DMN_LIST) ||
                context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_QUORUM_LIST);
    }

    void requestNextMNListDiff() {
        if (!shouldProcessMNListDiff())
            return;

        log.info("download peer = {}", downloadPeer);
        Peer downloadPeerBackup = downloadPeer == null ? context.peerGroup.getDownloadPeer() : downloadPeer;

        lock.lock();
        try {
            if (waitingForMNListDiff)
                return;

            log.info("handling next mnlistdiff: " + pendingBlocks.size());

            //fill up the pending list with recent blocks
            if (syncOptions != MasternodeListSyncOptions.SYNC_MINIMUM) {
                // TODO: update based on headers first sync
                Sha256Hash tipHash = blockChain.getChainHead().getHeader().getHash();
                ArrayList<StoredBlock> blocksToAdd = new ArrayList<>();
                if (!getMasternodeListCache().containsKey(tipHash) && !pendingBlocks.contains(blockChain.getChainHead())) {
                    StoredBlock cursor = blockChain.getChainHead();
                    do {
                        if (!pendingBlocks.contains(cursor)) {
                            blocksToAdd.add(0, cursor);
                        } else break;
                        try {
                            cursor = cursor.getPrev(blockChain.getBlockChain().getBlockStore());
                        } catch (BlockStoreException x) {
                            break;
                        }
                    } while (cursor != null);

                    pendingBlocks.addAll(blocksToAdd);
                }
            }

            if (pendingBlocks.isEmpty()) {
                log.info("There are no pending blocks to request: {}", lastRequest.request);
                return;
            }

            if (downloadPeer == null) {
                downloadPeer = downloadPeerBackup;
            }

            if (downloadPeer != null) {

                Iterator<StoredBlock> blockIterator = pendingBlocks.iterator();
                StoredBlock nextBlock;
                while (blockIterator.hasNext()) {
                    nextBlock = blockIterator.next();
                    if (nextBlock.getHeight() <= getMasternodeListAtTip().getHeight()) {
                        blockIterator.remove();
                        log.debug("removing {}/{} from pending blocks", nextBlock.getHeight(), nextBlock.getHeader().getHash());
                    } else break;
                }

                if (!pendingBlocks.isEmpty()) {
                    nextBlock = pendingBlocks.peek();
                    if (syncInterval > 1 && nextBlock.getHeader().getTimeSeconds() < Utils.currentTimeSeconds() - 60 * 60 && pendingBlocks.size() > syncInterval) {
                        // let's skip up to the next syncInterval blocks
                        while (blockIterator.hasNext()) {
                            nextBlock = blockIterator.next();
                            if (nextBlock.getHeight() % syncInterval == 0) break;
                            blockIterator.remove();
                        }
                        log.info("skipping up to the next syncInterval");
                    }

                    log.info("sending {} from {} to {}; \n  From {}\n To {}", lastRequest.request.getClass().getSimpleName(),
                            getMasternodeListAtTip().getHeight(), nextBlock.getHeight(), getMasternodeListAtTip().getBlockHash(), nextBlock.getHeader().getHash());
                    requestUpdate(downloadPeer, nextBlock);
                    log.info("message = {}", lastRequest.getRequestMessage().toString(blockChain));
                    waitingForMNListDiff = true;
                } else {
                    log.info("there are no pending blocks to process");
                    if (!initChainTipSyncComplete) {
                        initChainTipSyncComplete = true;
                    }
                }
            } else {
                log.warn("downloadPeer is null, not requesting update");
            }
        } finally {
            lock.unlock();
        }
    }

    void maybeGetMNListDiffFresh() {
        if (!shouldProcessMNListDiff())
            return;

        if (downloadPeer == null) {
            downloadPeer = context.peerGroup.getDownloadPeer();
        }

        lock.lock();
        try {
            long timePeriod = syncOptions == MasternodeListSyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60;
            if (!pendingBlocks.isEmpty()) {
                if (!waitingForMNListDiff) {
                    requestNextMNListDiff();
                    return;
                }
                if (lastRequest.time + WAIT_GETMNLISTDIFF < Utils.currentTimeSeconds()) {
                    waitingForMNListDiff = false;
                    requestNextMNListDiff();
                    return;
                }
                return;
            } else if (lastRequest.time + WAIT_GETMNLISTDIFF > Utils.currentTimeSeconds() ||
                    blockChain.getChainHead().getHeader().getTimeSeconds() < Utils.currentTimeSeconds() - timePeriod) {
                return;
            }

            //Should we reset our masternode/quorum list
            if (getMasternodeListAtTip().size() == 0 || getMasternodeListAtTip().getBlockHash().equals(params.getGenesisBlock().getHash())) {
                clearState();
            } else {
                // this may be out of date
                if (getMasternodeListAtTip().getBlockHash().equals(blockChain.getChainHead().getHeader().getHash()))
                    return;
            }



            StoredBlock block = blockChain.getChainHead();
            SimplifiedMasternodeList mnList = getMasternodeListAtTip();
            log.info("maybe requesting {} from {} to {}; \n  From {}\n  To {}", lastRequest.request.getClass().getSimpleName(),
                    mnList.getHeight(), block.getHeight(), mnList.getBlockHash(), block.getHeader().getHash());

            if (mnList.getBlockHash().equals(params.getGenesisBlock().getHash())) {
                resetMNList(true, true);
                return;
            }

            if (!blockChain.getChainHead().getHeader().getPrevBlockHash().equals(mnList.getBlockHash())) {
                if (syncOptions != MasternodeListSyncOptions.SYNC_MINIMUM)
                    fillPendingBlocksList(mnList.getBlockHash(), blockChain.getChainHead().getHeader().getHash());
                requestNextMNListDiff();
                return;
            }

            StoredBlock endBlock = blockChain.getChainHead();

            if (syncOptions == MasternodeListSyncOptions.SYNC_MINIMUM)
                endBlock = blockChain.getBlock(endBlock.getHeight() - SigningManager.SIGN_HEIGHT_OFFSET);
            if (getMasternodeListCache().containsKey(endBlock.getHeader().getHash()))
                endBlock = blockChain.getBlock((int) getMasternodeListAtTip().getHeight() + 1);
            if (endBlock == null)
                endBlock = blockChain.getChainHead();

            requestUpdate(downloadPeer, endBlock);
            waitingForMNListDiff = true;
        } finally {
            lock.unlock();
        }
    }

    protected void clearState() {
        initialize();
    }

    public boolean isSynced() {
        return pendingBlocks.isEmpty();
    }

    protected void fillPendingBlocksList(Sha256Hash first, Sha256Hash last) {
        lock.lock();
        try {
            // TODO: update to use DualBlockchain?
            StoredBlock cursor = blockChain.getBlockChain().getBlockStore().get(last);
            while (cursor != null && !cursor.getHeader().getHash().equals(first)) {
                if (!pendingBlocks.contains(cursor)) {
                    pendingBlocks.add(cursor);
                }
                cursor = cursor.getPrev(blockChain.getBlockChain().getBlockStore());
            }
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        } finally {
            lock.unlock();
        }
    }

    public boolean isDeterministicMNsSporkActive(long height) {
        if (height == -1) {
            height = getMasternodeListAtTip().getHeight();
        }

        return height > params.getDeterministicMasternodesEnabledHeight();
    }

    public boolean isDeterministicMNsSporkActive() {
        return isDeterministicMNsSporkActive(-1) || params.isDeterministicMasternodesEnabled();
    }

    public void addEventListeners(AbstractBlockChain blockChain, PeerGroup peerGroup) {
        blockChain.addNewBestBlockListener(Threading.SAME_THREAD, newBestBlockListener);
        blockChain.addReorganizeListener(reorganizeListener);
        if (peerGroup != null) {
            peerGroup.addConnectedEventListener(peerConnectedEventListener);
            peerGroup.addChainDownloadStartedEventListener(chainDownloadStartedEventListener);
            peerGroup.addHeadersDownloadStartedEventListener(headersDownloadStartedEventListener);
            peerGroup.addDisconnectedEventListener(peerDisconnectedEventListener);
        }
    }

    public void removeEventListeners(AbstractBlockChain blockChain, PeerGroup peerGroup) {
        if (blockChain != null) {
            blockChain.removeNewBestBlockListener(newBestBlockListener);
            blockChain.removeReorganizeListener(reorganizeListener);
        }
         if (peerGroup != null) {
            peerGroup.removeConnectedEventListener(peerConnectedEventListener);
            peerGroup.removeChainDownloadStartedEventListener(chainDownloadStartedEventListener);
            peerGroup.removeHeadersDownloadStartedEventListener(headersDownloadStartedEventListener);
            peerGroup.removeDisconnectedEventListener(peerDisconnectedEventListener);
        }
    }

    public final NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            boolean value = initChainTipSyncComplete || !context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST);
            boolean needsUpdate = needsUpdate(block);
            if (needsUpdate && value && getMasternodeListAtTip().getHeight() < block.getHeight() && isDeterministicMNsSporkActive() && stateManager.isLoadedFromFile()) {
                long timePeriod = syncOptions == MasternodeListSyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 600L;
                if (Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < timePeriod) {
                    if (syncOptions == MasternodeListSyncOptions.SYNC_MINIMUM) {
                        try {
                            StoredBlock requestBlock = getBlockHeightOffset() > 0 ? blockChain.getBlock(block.getHeight() - getBlockHeightOffset()) : block;
                            if (getMasternodeListAtTip().getHeight() > requestBlock.getHeight())
                                requestBlock = blockChain.getBlock((int) getMasternodeListAtTip().getHeight() + 1);
                            if (requestBlock != null) {
                                block = requestBlock;
                            }
                        } catch (NullPointerException x) {
                            log.info("null pointer exception", x);
                        }
                    }
                    log.debug("new best block: requesting {} as {}", block.getHeight(), lastRequest.getRequestMessage().getClass().getSimpleName());
                    requestMNListDiff(block);
                }
            } else {
                if (AbstractQuorumState.this instanceof QuorumRotationState && block.getHeight() % params.getLlmqs().get(params.getLlmqDIP0024InstantSend()).getDkgMiningWindowEnd() != 0) {
                    return;
                }
                log.debug("new best block: not requesting {} (value={}, update={}) as {}", block.getHeight(), value, needsUpdate,lastRequest.getRequestMessage().getClass().getSimpleName());
            }
        }
    };

    public final PeerConnectedEventListener peerConnectedEventListener = new PeerConnectedEventListener() {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            lock.lock();
            try {
                if (downloadPeer == null)
                    downloadPeer = peer;
            } finally {
                lock.unlock();
            }
        }
    };

    final PeerDisconnectedEventListener peerDisconnectedEventListener = new PeerDisconnectedEventListener() {
        @Override
        public void onPeerDisconnected(Peer peer, int peerCount) {
            if (downloadPeer == peer) {
                downloadPeer = null;
                chooseRandomDownloadPeer();
            }
            if (peer.getAddress().equals(lastRequest.getPeerAddress())) {
                log.warn("Disconnecting from peer {} before processing mnlistdiff", peer.getAddress());
                // TODO: what else should we do?
                //   request again?
            }
        }
    };

    final ReorganizeListener reorganizeListener = new ReorganizeListener() {
        @Override
        public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) throws VerificationException {
            if (!shouldProcessMNListDiff()) {
                return;
            }
            lock.lock();
            try {
                SimplifiedMasternodeList mnlistAtSplitPoint = getMasternodeListCache().get(splitPoint.getHeader().getHash());
                if (mnlistAtSplitPoint != null) {
                    Iterator<Map.Entry<Sha256Hash, SimplifiedMasternodeList>> iterator = getMasternodeListCache().entrySet().iterator();
                    boolean foundSplitPoint = false;
                    while (iterator.hasNext()) {
                        Map.Entry<Sha256Hash, SimplifiedMasternodeList> entry = iterator.next();
                        if (entry.getKey().equals(splitPoint.getHeader().getHash())) {
                            foundSplitPoint = true;
                            continue;
                        }
                        if (foundSplitPoint)
                            iterator.remove();
                    }
                    pendingBlocks.clear();
                    pendingBlocks.addAll(newBlocks);
                    requestNextMNListDiff();
                } else {
                    resetMNList(true);
                }
            } finally {
                lock.unlock();
            }
        }
    };

    void chooseRandomDownloadPeer() {
        List<Peer> peers = context.peerGroup.getConnectedPeers();
        if (peers != null && !peers.isEmpty()) {
            downloadPeer = peers.get(random.nextInt(peers.size()));
        }
    }

    final ChainDownloadStartedEventListener chainDownloadStartedEventListener = new ChainDownloadStartedEventListener() {
        @Override
        public void onChainDownloadStarted(Peer peer, int blocksLeft) {
            lock.lock();
            try {
                downloadPeer = peer;
                // perhaps this is not required with headers first sync
                // does this need to be in the next listener?
                if (stateManager.isLoadedFromFile())
                    maybeGetMNListDiffFresh();
            } finally {
                lock.unlock();
            }

        }
    };

    final HeadersDownloadStartedEventListener headersDownloadStartedEventListener = new HeadersDownloadStartedEventListener() {
        @Override
        public void onHeadersDownloadStarted(Peer peer, int blocksLeft) {
            lock.lock();
            try {
                downloadPeer = peer;
            } finally {
                lock.unlock();
            }
        }
    };

    public void onFirstSaveComplete() {
        lock.lock();
        try {
            if (blockChain == null || blockChain.getBestChainHeight() >= getMasternodeListAtTip().getHeight())
                return;
            StoredBlock block = blockChain.getChainHead();
            long timePeriod = syncOptions == MasternodeListSyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60L;
            if (Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < timePeriod) {
                if (syncOptions == MasternodeListSyncOptions.SYNC_MINIMUM) {
                    StoredBlock requestBlock = blockChain.getBlock(block.getHeight() - getBlockHeightOffset());
                    if (getMasternodeListAtTip().getHeight() > requestBlock.getHeight())
                        requestBlock = blockChain.getBlock((int) getMasternodeListAtTip().getHeight() + 1);
                    if (requestBlock != null) {
                        block = requestBlock;
                    }
                }
                requestMNListDiff(block);
            }
        } finally {
            lock.unlock();
        }
    }

    protected void sendRequestWithRetry(Peer peer) {
        ListenableFuture sendMessageFuture = peer.sendMessage(lastRequest.getRequestMessage());
        sendMessageFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    // throws an exception if there was a problem sending
                    sendMessageFuture.get();
                } catch (ExecutionException e) {
                    // send the message again
                    try {
                        log.info("Exception when sending {}", lastRequest.getRequestMessage().getClass().getSimpleName(), e);

                        // use tryLock to avoid deadlocks
                        boolean isLocked = context.peerGroup.getLock().tryLock(500, TimeUnit.MILLISECONDS);
                        try {
                            if (isLocked) {
                                log.info(Thread.currentThread().getName() + ": lock acquired");
                                downloadPeer = context.peerGroup.getDownloadPeer();
                                if (downloadPeer == null) {
                                    chooseRandomDownloadPeer();
                                }
                                retryLastUpdate(downloadPeer);
                            }
                        } finally {
                            if (isLocked) {
                                context.peerGroup.getLock().unlock();
                            }
                        }
                    } catch (InterruptedException x) {
                        x.printStackTrace();
                    } catch (NullPointerException x) {
                        log.info("peergroup is not initialized", x);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, Threading.THREAD_POOL);
    }

    public boolean notUsingBootstrapFile() {
        return bootstrapFilePath == null;
    }

    public boolean notUsingBootstrapFileAndStream() {
        return bootstrapFilePath == null && bootstrapStream == null;
    }

    abstract DiffMessage loadDiffMessageFromBuffer(byte [] buffer, int protocolVersion);

    public void setLoadingBootstrap() {
        isLoadingBootstrap = true;
        bootStrapLoaded = null;
    }

    protected void loadBootstrapAndSync() {
        Preconditions.checkState(!notUsingBootstrapFileAndStream(), "there must be a bootstrap file or stream specified");
        Preconditions.checkState(getMasternodeList().size() == 0, "masternode list is not empty: " + getMasternodeList());
        Preconditions.checkState(getQuorumListAtTip().size() == 0);
        Preconditions.checkState(getMasternodeListCache().size() == 1);
        Preconditions.checkState(getQuorumsCache().size() == 1);

        bootStrapLoaded = SettableFuture.create();

        log.info("loading bootstrap file: {}", bootstrapFilePath != null ? bootstrapFilePath : "input stream");

        // load the files or streams
        InputStream stream = bootstrapStream;

        try {
            if (stream != null)
                stream.reset();
            else if (bootstrapFilePath != null) {
                stream = Files.newInputStream(Paths.get(bootstrapFilePath));
            }
            byte[] buffer = null;

            if (stream != null) {
                buffer = new byte[(int) stream.available()];
                //noinspection ResultOfMethodCallIgnored
                stream.read(buffer);
            }

            isLoadingBootstrap = true;
            if (buffer != null) {
                DiffMessage mnlistdiff = loadDiffMessageFromBuffer(buffer, protocolVersion);
                if (mnlistdiff instanceof SimplifiedMasternodeListDiff) {
                    stateManager.processDiffMessage(null, (SimplifiedMasternodeListDiff) mnlistdiff, true);
                } else if (mnlistdiff instanceof QuorumRotationInfo) {
                    SettableFuture<Boolean> qrinfoComplete = SettableFuture.create();
                    stateManager.processDiffMessage(null, (QuorumRotationInfo) mnlistdiff, true, qrinfoComplete);
                    qrinfoComplete.get();
                } else {
                    throw new IllegalStateException("Unknown difference message: " + mnlistdiff.getShortName());
                }
            }

            if (bootStrapFileFormat < 1) {
                throw new IllegalArgumentException("file format " + bootStrapFileFormat + " is not supported");
            }
            bootStrapLoaded.set(true);
            log.info("finished loading bootstrap files");
        } catch (VerificationException | IOException | IllegalStateException | NullPointerException | InterruptedException | ExecutionException x) {
            bootStrapLoaded.setException(x);
            log.info("failed loading bootstrap files: ", x);
        } finally {
            isLoadingBootstrap = false;
            try {
                if (stream != null)
                    stream.close();
                requestAfterMNListReset();
            } catch (IOException x) {
                // swallow, file closure failed
            } catch (BlockStoreException x) {
                throw new RuntimeException(x);
            }
        }
    }

    BLSSignature getCoinbaseChainlock(StoredBlock block) {
        ChainLockSignature clsig = chainLocksHandler.getCoinbaseChainLock(block.getHeader().getHash());
        if (clsig != null)
            return clsig.getSignature();
        else return null;
    }

    // DIP29 Random Beacon for LLMQ selection is activated with v20
    Sha256Hash getHashModifier(LLMQParameters llmqParams, StoredBlock quorumBaseBlock) {
        StoredBlock workBlock = blockChain.getBlockAncestor(quorumBaseBlock, quorumBaseBlock.getHeight() - 8);

        if (params.isV20Active(workBlock.getHeight())) {
            // v20 is active: calculate modifier using the new way.
            BLSSignature bestCLSignature = getCoinbaseChainlock(workBlock);
            log.info("getHashModifier(..., {})\n  work: {}\n  sig: {}", quorumBaseBlock.getHeader().getHash(), workBlock.getHeader().getHash(), bestCLSignature);
            if (bestCLSignature != null) {
                // We have a non-null CL signature: calculate modifier using this CL signature

                return LLMQUtils.buildLLMQBlockHash(llmqParams.getType(), workBlock.getHeight(), bestCLSignature);
            } else {
                log.info("cannot find CL for block {}", workBlock.getHeader().getHash());
            }
            // No non-null CL signature found in coinbase: calculate modifier using block hash only
            return LLMQUtils.buildLLMQBlockHash(llmqParams.getType(), workBlock.getHeader().getHash());
        }

        // v20 isn't active yet: calculate modifier using the usual way
        if (llmqParams.useRotation()) {
            return LLMQUtils.buildLLMQBlockHash(llmqParams.getType(), workBlock.getHeader().getHash());
        }
        return LLMQUtils.buildLLMQBlockHash(llmqParams.getType(), quorumBaseBlock.getHeader().getHash());
    }
}
