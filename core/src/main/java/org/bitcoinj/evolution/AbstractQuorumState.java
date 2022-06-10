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

import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
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
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.core.listeners.ReorganizeListener;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.FileNotFoundException;
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

    public static final int SNAPSHOT_LIST_PERIOD = 576; // once per day
    public static final int LISTS_CACHE_SIZE = 576;
    public static final int SNAPSHOT_TIME_PERIOD = 60 * 60 * 26;

    public static int MAX_CACHE_SIZE = 10;
    public static int MIN_CACHE_SIZE = 1;

    private static final Logger log = LoggerFactory.getLogger(AbstractQuorumState.class);
    protected ReentrantLock lock = Threading.lock("AbstractQuorumState");

    Context context;
    AbstractBlockChain headerChain;
    AbstractBlockChain blockChain;
    BlockStore headerStore;
    BlockStore blockStore;

    QuorumUpdateRequest<Request> lastRequest;

    static final long WAIT_GETMNLISTDIFF = 5000;
    Peer downloadPeer;
    boolean waitingForMNListDiff;
    boolean initChainTipSyncComplete = false;
    LinkedHashMap<Sha256Hash, StoredBlock> pendingBlocksMap;
    ArrayList<StoredBlock> pendingBlocks;

    int failedAttempts;
    static final int MAX_ATTEMPTS = 10;

    public SimplifiedMasternodeListManager.SyncOptions syncOptions;
    public int syncInterval;

    QuorumStateManager stateManager;

    public AbstractQuorumState(Context context) {
        super(context.getParams());
        this.context = context;
        initializeOnce();
        initialize();
    }

    public AbstractQuorumState(NetworkParameters params, byte[] payload, int offset) {
        super(params, payload, offset);
        initialize();
    }

    private void initializeOnce() {
        syncOptions = SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM;
        syncInterval = 8;
    }

    private void initialize() {
        waitingForMNListDiff = false;
        pendingBlocks = new ArrayList<StoredBlock>();
        pendingBlocksMap = new LinkedHashMap<Sha256Hash, StoredBlock>();
    }

    public void setStateManager(QuorumStateManager stateManager) {
        this.stateManager = stateManager;
    }

    // TODO: Do we need to keep track of the header chain also?
    public void setBlockChain(/*AbstractBlockChain headerChain, */AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        //this.headerChain = headerChain;
        blockStore = blockChain.getBlockStore();
        //headerStore = headerChain.getBlockStore();
    }

    public void pushPendingBlock(StoredBlock block) {
        pendingBlocks.add(block);
        pendingBlocksMap.put(block.getHeader().getHash(), block);
    }

    public List<StoredBlock> getPendingBlocks() {
        return pendingBlocks;
    }

    public void popPendingBlock() {
        StoredBlock thisBlock = pendingBlocks.get(0);
        pendingBlocks.remove(0);
        pendingBlocksMap.remove(thisBlock.getHeader().getHash());
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

    abstract boolean needsUpdate(StoredBlock nextBlock);

    public abstract void processDiff(@Nullable Peer peer, DiffMessage difference, AbstractBlockChain headersChain, AbstractBlockChain blockChain, boolean isLoadingBootStrap);

    public abstract void requestReset(Peer peer, StoredBlock block);

    public abstract void requestUpdate(Peer peer, StoredBlock block);

    public void retryLastUpdate(Peer peer) {
        peer.sendMessage(lastRequest.getRequestMessage());
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
            if (force /*|| getFormatVersion() < LLMQ_FORMAT_VERSION*/) {
                log.info("resetting masternode list");
                clearState();
                pendingBlocks.clear();
                pendingBlocksMap.clear();
                waitingForMNListDiff = false;
                unCache();
                try {
                    if (stateManager.notUsingBootstrapFile())
                        stateManager.save();
                } catch (FileNotFoundException x) {
                    //swallow, the file has no name
                }
                if (requestFreshList) {
                    if (stateManager.notUsingBootstrapFileAndStream()) {
                        requestAfterMNListReset();
                    } else {
                        waitingForMNListDiff = true;
                        stateManager.setLoadingBootstrap();
                        stateManager.loadBootstrapAndSync();
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
        int rewindBlockCount = syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_LIST_PERIOD : MAX_CACHE_SIZE;
        int height = blockChain.getBestChainHeight() - rewindBlockCount;
        if (height < params.getDIP0008BlockHeight())
            height = params.getDIP0008BlockHeight();
        int currentHeight = blockChain.getBestChainHeight();
        if (syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM) {
            height = currentHeight - getBlockHeightOffset();
        } else {
            height = currentHeight;
        }
        StoredBlock resetBlock = blockChain.getBlockStore().get(height);
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

        if (pendingBlocksMap.put(hash, block) == null) {
            log.info("adding 1 block to the pending queue: {} - {}", block.getHeight(), block.getHeader().getHash());
            pendingBlocks.add(block);
        }

        if (!waitingForMNListDiff) {
            log.info("requesting: next");
            requestNextMNListDiff();
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
            if (syncOptions != SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM) {
                Sha256Hash tipHash = blockChain.getChainHead().getHeader().getHash();
                ArrayList<StoredBlock> blocksToAdd = new ArrayList<StoredBlock>();
                if (!getMasternodeListCache().containsKey(tipHash) && !pendingBlocksMap.containsKey(tipHash)) {
                    StoredBlock cursor = blockChain.getChainHead();
                    do {
                        if (!pendingBlocksMap.containsKey(cursor.getHeader().getHash())) {
                            blocksToAdd.add(0, cursor);
                        } else break;
                        try {
                            cursor = cursor.getPrev(blockChain.getBlockStore());
                        } catch (BlockStoreException x) {
                            break;
                        }
                    } while (cursor != null);

                    for (StoredBlock block : blocksToAdd) {
                        pendingBlocks.add(block);
                        pendingBlocksMap.put(block.getHeader().getHash(), block);
                    }
                }
            }

            if (pendingBlocks.size() == 0)
                return;

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
                        pendingBlocksMap.remove(nextBlock.getHeader().getHash());
                    } else break;
                }

                if (pendingBlocks.size() != 0) {
                    nextBlock = pendingBlocks.get(0);
                    if (syncInterval > 1 && nextBlock.getHeader().getTimeSeconds() < Utils.currentTimeSeconds() - 60 * 60 && pendingBlocks.size() > syncInterval) {
                        // lets skip up to the next syncInterval blocks
                        while (blockIterator.hasNext()) {
                            nextBlock = blockIterator.next();
                            if (nextBlock.getHeight() % syncInterval == 0) break;
                            blockIterator.remove();
                        }
                    }

                    log.info("sending {} from {} to {}; \n  From {}\n To {}", lastRequest.request.getClass().getSimpleName(),
                            getMasternodeListAtTip().getHeight(), nextBlock.getHeight(), getMasternodeListAtTip().getBlockHash(), nextBlock.getHeader().getHash());
                    requestUpdate(downloadPeer, nextBlock);
                    log.info("message = {}", lastRequest.getRequestMessage().toString(blockChain));

                    waitingForMNListDiff = true;
                }
            }
        } finally {
            lock.unlock();
        }

    }

    void maybeGetMNListDiffFresh() {
        if (!shouldProcessMNListDiff())
            return;

        lock.lock();
        try {

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

    void fillPendingBlocksList(Sha256Hash first, Sha256Hash last, int insertIndex) {
        lock.lock();
        try {
            StoredBlock cursor = blockChain.getBlockStore().get(last);
            while (cursor != null && !cursor.getHeader().getHash().equals(first)) {
                if (!pendingBlocksMap.containsKey(cursor.getHeader().getHash())) {
                    pendingBlocks.add(insertIndex, cursor);
                    pendingBlocksMap.put(cursor.getHeader().getHash(), cursor);
                }
                cursor = cursor.getPrev(blockChain.getBlockStore());
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
        this.blockChain = blockChain;
        blockChain.addNewBestBlockListener(Threading.SAME_THREAD, newBestBlockListener);
        blockChain.addReorganizeListener(reorganizeListener);
        if (peerGroup != null) {
            peerGroup.addConnectedEventListener(peerConnectedEventListener);
            peerGroup.addChainDownloadStartedEventListener(chainDownloadStartedEventListener);
            peerGroup.addDisconnectedEventListener(peerDisconnectedEventListener);
        }
    }

    public void removeEventListeners(AbstractBlockChain blockChain, PeerGroup peerGroup) {
        blockChain.removeNewBestBlockListener(newBestBlockListener);
        blockChain.removeReorganizeListener(reorganizeListener);
        peerGroup.removeConnectedEventListener(peerConnectedEventListener);
        peerGroup.removeChainDownloadStartedEventListener(chainDownloadStartedEventListener);
        peerGroup.removeDisconnectedEventListener(peerDisconnectedEventListener);
    }

    public NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            boolean value = initChainTipSyncComplete || !context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST);
            boolean needsUpdate = needsUpdate(block);
            if (needsUpdate && value && getMasternodeListAtTip().getHeight() < block.getHeight() && isDeterministicMNsSporkActive() && stateManager.isLoadedFromFile()) {
                long timePeriod = syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 600L;
                if (Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < timePeriod) {
                    if (syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM) {
                        try {
                            StoredBlock requestBlock = getBlockHeightOffset() > 0 ? blockChain.getBlockStore().get(block.getHeight() - getBlockHeightOffset()) : block;
                            if (getMasternodeListAtTip().getHeight() > requestBlock.getHeight())
                                requestBlock = blockChain.getBlockStore().get((int) getMasternodeListAtTip().getHeight() + 1);
                            if (requestBlock != null) {
                                block = requestBlock;
                            }
                        } catch (BlockStoreException x) {
                            throw new RuntimeException(x);
                        } catch (NullPointerException x) {
                            log.info("null pointer exception", x);
                        }
                    }
                    log.info("new best block: requesting {}", block.getHeight());
                    requestMNListDiff(block);
                    return;
                }
            }
            log.info("new best block {}: not requesting needsUpdate: {}, {}, {}", block.getHeight(), needsUpdate, value, stateManager.isLoadedFromFile());
        }
    };

    public PeerConnectedEventListener peerConnectedEventListener = new PeerConnectedEventListener() {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            lock.lock();
            log.info("Peer Connected");
            try {
                if (downloadPeer == null)
                    downloadPeer = peer;
                // TODO: do we need this for testnet
                //boolean value = initChainTipSyncComplete;
                /*if (value && getMasternodeListAtTip().getHeight() < blockChain.getBestChainHeight() && isDeterministicMNsSporkActive() && stateManager.isLoadedFromFile()) {
                    maybeGetMNListDiffFresh();
                    if (!waitingForMNListDiff && getMasternodeListAtTip().getBlockHash().equals(params.getGenesisBlock().getHash()) || getMasternodeListAtTip().getHeight() < blockChain.getBestChainHeight()) {
                        long timePeriod = syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60L;
                        if (Utils.currentTimeSeconds() - blockChain.getChainHead().getHeader().getTimeSeconds() < timePeriod) {
                            StoredBlock block = blockChain.getChainHead();
                            if (syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM) {
                                try {
                                    StoredBlock requestBlock = blockChain.getBlockStore().get(block.getHeight() - getBlockHeightOffset());
                                    if (getMasternodeListAtTip().getHeight() > requestBlock.getHeight())
                                        requestBlock = blockChain.getBlockStore().get((int) getMasternodeListAtTip().getHeight() + 1);
                                    if (requestBlock != null) {
                                        block = requestBlock;
                                    }
                                } catch (BlockStoreException x) {
                                    // do nothing
                                }
                            }
                            if (!pendingBlocksMap.containsKey(block.getHeader().getHash()))
                                requestMNListDiff(peer, block);
                        }
                    }
                }*/
            } finally {
                lock.unlock();
            }
        }
    };

    PeerDisconnectedEventListener peerDisconnectedEventListener = new PeerDisconnectedEventListener() {
        @Override
        public void onPeerDisconnected(Peer peer, int peerCount) {
            log.info("Peer disconnected: " + peer.getAddress());
            if (downloadPeer == peer) {
                downloadPeer = null;
                chooseRandomDownloadPeer();
            }
        }
    };

    ReorganizeListener reorganizeListener = new ReorganizeListener() {
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
                    pendingBlocksMap.clear();
                    for (StoredBlock newBlock : newBlocks) {
                        pendingBlocks.add(newBlock);
                        pendingBlocksMap.put(newBlock.getHeader().getHash(), newBlock);
                    }
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
            downloadPeer = peers.get(new Random().nextInt(peers.size()));
        }
    }

    ChainDownloadStartedEventListener chainDownloadStartedEventListener = new ChainDownloadStartedEventListener() {
        @Override
        public void onChainDownloadStarted(Peer peer, int blocksLeft) {
            lock.lock();
            try {
                downloadPeer = peer;
                if (stateManager.isLoadedFromFile())
                    maybeGetMNListDiffFresh();
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
            long timePeriod = syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60L;
            if (Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < timePeriod) {
                if (syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM) {
                    try {
                        StoredBlock requestBlock = blockChain.getBlockStore().get(block.getHeight() - getBlockHeightOffset());
                        if (getMasternodeListAtTip().getHeight() > requestBlock.getHeight())
                            requestBlock = blockChain.getBlockStore().get((int) getMasternodeListAtTip().getHeight() + 1);
                        if (requestBlock != null) {
                            block = requestBlock;
                        }
                    } catch (BlockStoreException x) {
                        //do nothing
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
                        boolean flag = context.peerGroup.getLock().tryLock(500, TimeUnit.MILLISECONDS);
                        try {
                            if (flag) {
                                log.info(Thread.currentThread().getName() + ": lock acquired");
                                downloadPeer = context.peerGroup.getDownloadPeer();
                                retryLastUpdate(downloadPeer);
                            }
                        } finally {
                            context.peerGroup.getLock().unlock();
                        }
                    } catch (InterruptedException x) {
                        e.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, Threading.THREAD_POOL);
    }

}