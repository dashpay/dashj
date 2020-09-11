package org.bitcoinj.evolution;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.ChainDownloadStartedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.core.listeners.ReorganizeListener;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.quorums.SigningManager;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class SimplifiedMasternodeListManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(SimplifiedMasternodeListManager.class);
    private ReentrantLock lock = Threading.lock("SimplifiedMasternodeListManager");

    static final int DMN_FORMAT_VERSION = 1;
    static final int LLMQ_FORMAT_VERSION = 2;

    public static final int SNAPSHOT_LIST_PERIOD = 576; // once per day
    public static final int LISTS_CACHE_SIZE = 576;
    public static final int SNAPSHOT_TIME_PERIOD = 60 * 60 * 26;

    public static int MAX_CACHE_SIZE = 10;
    public static int MIN_CACHE_SIZE = 1;

    public enum SaveOptions {
        SAVE_EVERY_BLOCK,
        SAVE_EVERY_CHANGE,
    };

    public SaveOptions saveOptions;

    public enum SyncOptions {
        SYNC_SNAPSHOT_PERIOD,
        SYNC_CACHE_PERIOD,
        SYNC_MINIMUM;
    }

    public SyncOptions syncOptions;
    public int syncInterval;

    LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> mnListsCache = new LinkedHashMap<Sha256Hash, SimplifiedMasternodeList>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, SimplifiedMasternodeList> eldest) {
            return size() > (syncOptions == SyncOptions.SYNC_MINIMUM ? MIN_CACHE_SIZE : MAX_CACHE_SIZE);
        }
    };

    LinkedHashMap<Sha256Hash, SimplifiedQuorumList> quorumsCache = new LinkedHashMap<Sha256Hash, SimplifiedQuorumList>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, SimplifiedQuorumList> eldest) {
            return size() > (syncOptions == SyncOptions.SYNC_MINIMUM ? MIN_CACHE_SIZE : MAX_CACHE_SIZE);
        }
    };

    SimplifiedMasternodeList mnList;
    SimplifiedQuorumList quorumList;
    long tipHeight;
    Sha256Hash tipBlockHash;

    AbstractBlockChain blockChain;
    AbstractBlockChain headersChain;

    Sha256Hash lastRequestHash = Sha256Hash.ZERO_HASH;
    GetSimplifiedMasternodeListDiff lastRequestMessage;
    long lastRequestTime;
    static final long WAIT_GETMNLISTDIFF = 5000;
    Peer downloadPeer;
    boolean waitingForMNListDiff;
    boolean initChainTipSync = false;
    LinkedHashMap<Sha256Hash, StoredBlock> pendingBlocksMap;
    ArrayList<StoredBlock> pendingBlocks;

    int failedAttempts;
    static final int MAX_ATTEMPTS = 10;
    boolean loadedFromFile;
    boolean requiresLoadingFromFile;

    PeerGroup peerGroup;

    public SimplifiedMasternodeListManager(Context context) {
        super(context);
        tipBlockHash = params.getGenesisBlock().getHash();
        mnList = new SimplifiedMasternodeList(context.getParams());
        quorumList = new SimplifiedQuorumList(context.getParams());
        lastRequestTime = 0;
        waitingForMNListDiff = false;
        pendingBlocks = new ArrayList<StoredBlock>();
        pendingBlocksMap = new LinkedHashMap<Sha256Hash, StoredBlock>();
        saveOptions = SaveOptions.SAVE_EVERY_CHANGE;
        syncOptions = SyncOptions.SYNC_MINIMUM;
        syncInterval = 8;
        loadedFromFile = false;
        requiresLoadingFromFile = true;
        lastRequestMessage = new GetSimplifiedMasternodeListDiff(Sha256Hash.ZERO_HASH, Sha256Hash.ZERO_HASH);
        initChainTipSync = !context.getSyncFlags().contains(MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST);
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
        mnList = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnList.getMessageSize();
        tipBlockHash = readHash();
        tipHeight = readUint32();
        if(getFormatVersion() >= 2) {
            quorumList = new SimplifiedQuorumList(params, payload, cursor);
            cursor += quorumList.getMessageSize();

            //read pending blocks
            int size = (int)readVarInt();
            ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
            for(int i = 0; i < size; ++i) {
                buffer.put(readBytes(StoredBlock.COMPACT_SERIALIZED_SIZE));
                buffer.rewind();
                StoredBlock block = StoredBlock.deserializeCompact(params, buffer);
                if(block.getHeight() != 0 && syncOptions != SyncOptions.SYNC_MINIMUM) {
                    pendingBlocks.add(block);
                    pendingBlocksMap.put(block.getHeader().getHash(), block);
                }
                buffer.rewind();
            }
        } else {
            quorumList = new SimplifiedQuorumList(params);
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        lock.lock();
        try {
            SimplifiedMasternodeList mnListToSave = null;
            SimplifiedQuorumList quorumListToSave = null;
            ArrayList<StoredBlock> otherPendingBlocks = new ArrayList<StoredBlock>(MAX_CACHE_SIZE);
            if(mnListsCache.size() > 0) {
                for(Map.Entry<Sha256Hash, SimplifiedMasternodeList> entry : mnListsCache.entrySet()) {
                    if(mnListToSave == null) {
                        mnListToSave = entry.getValue();
                        quorumListToSave = quorumsCache.get(entry.getKey());
                    } else {
                        otherPendingBlocks.add(entry.getValue().getStoredBlock());
                    }
                }
            } else {
                mnListToSave = mnList;
                quorumListToSave = quorumList;
            }

            mnListToSave.bitcoinSerialize(stream);
            stream.write(mnListToSave.getBlockHash().getReversedBytes());
            Utils.uint32ToByteStreamLE(mnListToSave.getHeight(), stream);
            if(getFormatVersion() >= 2) {
                quorumListToSave.bitcoinSerialize(stream);
                if(syncOptions != SyncOptions.SYNC_MINIMUM) {
                    stream.write(new VarInt(pendingBlocks.size() + otherPendingBlocks.size()).encode());
                    ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
                    log.info("saving {} blocks to catch up mnList", otherPendingBlocks.size());
                    for (StoredBlock block : otherPendingBlocks) {
                        block.serializeCompact(buffer);
                        stream.write(buffer.array());
                        buffer.clear();
                    }
                    for (StoredBlock block : pendingBlocks) {
                        block.serializeCompact(buffer);
                        stream.write(buffer.array());
                        buffer.clear();
                    }
                } else stream.write(new VarInt(0).encode());
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

    public void processMasternodeListDiff(SimplifiedMasternodeListDiff mnlistdiff) {
        if(!shouldProcessMNListDiff())
            return;

        processMasternodeListDiff(mnlistdiff, false);
    }


    public void processMasternodeListDiff(SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap) {
        StoredBlock block = null;
        long newHeight = ((CoinbaseTx) mnlistdiff.coinBaseTx.getExtraPayloadObject()).getHeight();
        Stopwatch watch = Stopwatch.createStarted();
        Stopwatch watchMNList = Stopwatch.createUnstarted();
        Stopwatch watchQuorums = Stopwatch.createUnstarted();
        boolean isSyncingHeadersFirst = context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;
        log.info("processing mnlistdiff between : " + mnList.getHeight() + " & " + newHeight + "; " + mnlistdiff);
        lock.lock();
        try {
            block = chain.getBlockStore().get(mnlistdiff.blockHash);
            if(!isLoadingBootStrap && block.getHeight() != newHeight)
                throw new ProtocolException("mnlistdiff blockhash (height="+block.getHeight()+" doesn't match coinbase blockheight: " + newHeight);

            watchMNList.start();
            SimplifiedMasternodeList newMNList = mnList.applyDiff(mnlistdiff);
            if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_MNLIST))
                newMNList.verify(mnlistdiff.coinBaseTx, mnlistdiff, mnList);
            newMNList.setBlock(block, block == null ? false : block.getHeader().getPrevBlockHash().equals(mnlistdiff.prevBlockHash));
            SimplifiedQuorumList newQuorumList = quorumList;
            if(mnlistdiff.coinBaseTx.getExtraPayloadObject().getVersion() >= 2) {
                watchQuorums.start();
                newQuorumList = quorumList.applyDiff(mnlistdiff, isLoadingBootStrap, chain);
                if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_QUORUM))
                    newQuorumList.verify(mnlistdiff.coinBaseTx, mnlistdiff, quorumList, newMNList);
            } else {
                quorumList.syncWithMasternodeList(newMNList);
            }
            mnListsCache.put(newMNList.getBlockHash(), newMNList);
            quorumsCache.put(newQuorumList.getBlockHash(), newQuorumList);
            mnList = newMNList;
            quorumList = newQuorumList;
            log.info(this.toString());
            unCache();
            failedAttempts = 0;

            if(!pendingBlocks.isEmpty()) {
                StoredBlock thisBlock = pendingBlocks.get(0);
                pendingBlocks.remove(0);
                pendingBlocksMap.remove(thisBlock.getHeader().getHash());
            } else log.warn("pendingBlocks is empty");

            peerGroup.queueMasternodeListDownloadedListeners(mnlistdiff);

            if(mnlistdiff.coinBaseTx.getExtraPayloadObject().getVersion() >= 2 && quorumList.size() > 0)
                setFormatVersion(LLMQ_FORMAT_VERSION);
            if(mnlistdiff.hasChanges() || pendingBlocks.size() < MAX_CACHE_SIZE || saveOptions == SaveOptions.SAVE_EVERY_BLOCK)
                save();
        } catch(MasternodeListDiffException x) {
            //we already have this mnlistdiff or doesn't match our current tipBlockHash
            if(mnList.getBlockHash().equals(mnlistdiff.blockHash)) {
                log.info("heights are the same:  " + x.getMessage());
                log.info("mnList = {} vs mnlistdiff {}", mnList.getBlockHash(), mnlistdiff.prevBlockHash);
                log.info("mnlistdiff {} -> {}", mnlistdiff.prevBlockHash, mnlistdiff.blockHash);
                log.info("lastRequest {} -> {}", lastRequestMessage.baseBlockHash, lastRequestMessage.blockHash);
                //remove this block from the list
                if(pendingBlocks.size() > 0) {
                    StoredBlock thisBlock = pendingBlocks.get(0);
                    if(thisBlock.getHeader().getPrevBlockHash().equals(mnlistdiff.prevBlockHash) &&
                            thisBlock.getHeader().getHash().equals(mnlistdiff.prevBlockHash)) {
                        pendingBlocks.remove(0);
                        pendingBlocksMap.remove(thisBlock.getHeader().getHash());
                    }
                }
            } else {
                log.info("heights are different " + x.getMessage());
                log.info("mnlistdiff height = {}; mnList: {}; quorumList: {}", newHeight, mnList.getHeight(), quorumList.getHeight());
                log.info("mnList = {} vs mnlistdiff = {}", mnList.getBlockHash(), mnlistdiff.prevBlockHash);
                log.info("mnlistdiff {} -> {}", mnlistdiff.prevBlockHash, mnlistdiff.blockHash);
                log.info("lastRequest {} -> {}", lastRequestMessage.baseBlockHash, lastRequestMessage.blockHash);
                failedAttempts++;
                if(failedAttempts > MAX_ATTEMPTS)
                    resetMNList(true);
            }
        } catch(VerificationException x) {
            //request this block again and close this peer
            log.info("verification error: close this peer" + x.getMessage());
            failedAttempts++;
            throw x;
        } catch(NullPointerException x) {
            log.info("NPE: close this peer" + x.getMessage());
            failedAttempts++;
            throw new VerificationException("verification error: " + x.getMessage());
        } catch(FileNotFoundException x) {
            //file name is not set, do not save
            log.info(x.getMessage());
        } catch(BlockStoreException x) {
            log.info(x.getMessage());
            failedAttempts++;
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
                    initChainTipSync = true;
                } else {
                    context.peerGroup.triggerMnListDownloadComplete();
                    initChainTipSync = true;
                }
            }
            requestNextMNListDiff();
            lock.unlock();
        }
    }

    public NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            boolean value = initChainTipSync;
            if(value && getListAtChainTip().getHeight() < blockChain.getBestChainHeight() && isDeterministicMNsSporkActive() && isLoadedFromFile()) {
                long timePeriod = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE  * 3 * 60;
                if (Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < timePeriod) {
                    if(syncOptions == SyncOptions.SYNC_MINIMUM) {
                        try {
                            StoredBlock requestBlock = blockChain.getBlockStore().get(block.getHeight() - SigningManager.SIGN_HEIGHT_OFFSET);
                            if(getListAtChainTip().getHeight() > requestBlock.getHeight())
                                requestBlock = blockChain.getBlockStore().get((int)getListAtChainTip().getHeight()+1);
                            if (requestBlock != null) {
                                block = requestBlock;
                            }
                        } catch (BlockStoreException x) {
                            //do nothing
                        }
                    }
                    requestMNListDiff(block);
                }
            }
        }
    };

    public PeerConnectedEventListener peerConnectedEventListener = new PeerConnectedEventListener() {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            lock.lock();
            try {
                if (downloadPeer == null)
                    downloadPeer = peer;
                boolean value = initChainTipSync;
                if (value && getListAtChainTip().getHeight() < blockChain.getBestChainHeight() && isDeterministicMNsSporkActive() && isLoadedFromFile()) {
                    maybeGetMNListDiffFresh();
                    if (!waitingForMNListDiff && mnList.getBlockHash().equals(params.getGenesisBlock().getHash()) || mnList.getHeight() < blockChain.getBestChainHeight()) {
                        long timePeriod = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60;
                        if (Utils.currentTimeSeconds() - blockChain.getChainHead().getHeader().getTimeSeconds() < timePeriod) {
                            StoredBlock block = blockChain.getChainHead();
                            if (syncOptions == SyncOptions.SYNC_MINIMUM) {
                                try {
                                    StoredBlock requestBlock = blockChain.getBlockStore().get(block.getHeight() - SigningManager.SIGN_HEIGHT_OFFSET);
                                    if (getListAtChainTip().getHeight() > requestBlock.getHeight())
                                        requestBlock = blockChain.getBlockStore().get((int) getListAtChainTip().getHeight() + 1);
                                    if (requestBlock != null) {
                                        block = requestBlock;
                                    }
                                } catch (BlockStoreException x) {
                                    //do nothing
                                }
                            }
                            if (!pendingBlocksMap.containsKey(block.getHeader().getHash()))
                                requestMNListDiff(peer, block);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    };

    PeerDisconnectedEventListener peerDisconnectedEventListener = new PeerDisconnectedEventListener() {
        @Override
        public void onPeerDisconnected(Peer peer, int peerCount) {
            log.info("Peer disconnected: " + peer.getAddress());
            if(downloadPeer == peer) {
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
                SimplifiedMasternodeList mnlistAtSplitPoint = mnListsCache.get(splitPoint.getHeader().getHash());
                if (mnlistAtSplitPoint != null) {
                    Iterator<Map.Entry<Sha256Hash, SimplifiedMasternodeList>> iterator = mnListsCache.entrySet().iterator();
                    boolean foundSplitPoint = true;
                    while (iterator.hasNext()) {
                        Map.Entry<Sha256Hash, SimplifiedMasternodeList> entry = iterator.next();
                        if (entry.getValue().equals(splitPoint.getHeader().getHash())) {
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
        if(peers != null && !peers.isEmpty()) {
            downloadPeer = peers.get(new Random().nextInt(peers.size()));
        }
    }

    ChainDownloadStartedEventListener chainDownloadStartedEventListener = new ChainDownloadStartedEventListener() {
        @Override
        public void onChainDownloadStarted(Peer peer, int blocksLeft) {
            lock.lock();
            try {
                downloadPeer = peer;
                if(isLoadedFromFile())
                    maybeGetMNListDiffFresh();
            } finally {
                lock.unlock();
            }

        }
    };

    void maybeGetMNListDiffFresh() {
        if(!shouldProcessMNListDiff())
            return;

        lock.lock();
        try {
            long timePeriod = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60;
            if (pendingBlocks.size() > 0) {
                if (!waitingForMNListDiff) {
                    requestNextMNListDiff();
                    return;
                }
                if (lastRequestTime + WAIT_GETMNLISTDIFF < Utils.currentTimeMillis()) {
                    waitingForMNListDiff = false;
                    requestNextMNListDiff();
                    return;
                }
                //if (lastRequestTime + WAIT_GETMNLISTDIFF < Utils.currentTimeMillis())
                //    return;
                return;
            } else if (lastRequestTime + WAIT_GETMNLISTDIFF > Utils.currentTimeMillis() ||
                    blockChain.getChainHead().getHeader().getTimeSeconds() < Utils.currentTimeSeconds() - timePeriod) {
                return;
            }

            //Should we reset our masternode/quorum list
            if (mnList.size() == 0 || mnList.getBlockHash().equals(params.getGenesisBlock().getHash())) {
                mnList = new SimplifiedMasternodeList(params);
                quorumList = new SimplifiedQuorumList(params);
            } else {
                if (mnList.getBlockHash().equals(blockChain.getChainHead().getHeader().getHash()))
                    return;
            }

            if (downloadPeer == null) {
                downloadPeer = context.peerGroup.getDownloadPeer();
            }

            StoredBlock block = blockChain.getChainHead();
            log.info("maybe requesting mnlistdiff from {} to {}; \n  From {}\n  To {}", mnList.getHeight(), block.getHeight(), mnList.getBlockHash(), block.getHeader().getHash());
            if (mnList.getBlockHash().equals(params.getGenesisBlock().getHash())) {
                resetMNList(true);
                return;
            }

            if (!blockChain.getChainHead().getHeader().getPrevBlockHash().equals(mnList.getBlockHash())) {
                if (syncOptions != SyncOptions.SYNC_MINIMUM)
                    fillPendingBlocksList(mnList.getBlockHash(), blockChain.getChainHead().getHeader().getHash(), pendingBlocks.size());
                requestNextMNListDiff();
                return;
            }

            StoredBlock endBlock = blockChain.getChainHead();
            try {
                if (syncOptions == SyncOptions.SYNC_MINIMUM)
                    endBlock = blockChain.getBlockStore().get(endBlock.getHeight() - SigningManager.SIGN_HEIGHT_OFFSET);
                if (mnListsCache.containsKey(endBlock.getHeader().getHash()))
                    endBlock = blockChain.getBlockStore().get((int) getListAtChainTip().getHeight() + 1);
                if (endBlock == null)
                    endBlock = blockChain.getChainHead();
            } catch (BlockStoreException x) {
                // do nothing
            }
            lastRequestMessage = new GetSimplifiedMasternodeListDiff(mnList.getBlockHash(), endBlock.getHeader().getHash());
            downloadPeer.sendMessage(lastRequestMessage);
            lastRequestHash = mnList.getBlockHash();
            lastRequestTime = Utils.currentTimeMillis();
            waitingForMNListDiff = true;
        } finally {
            lock.unlock();
        }
    }

    void requestNextMNListDiff() {
        if(!shouldProcessMNListDiff())
            return;

        lock.lock();
        try {
            if(waitingForMNListDiff)
                return;

            log.info("handling next mnlistdiff: " + pendingBlocks.size());

            //fill up the pending list with recent blocks
            if(syncOptions != SyncOptions.SYNC_MINIMUM) {
                Sha256Hash tipHash = blockChain.getChainHead().getHeader().getHash();
                ArrayList<StoredBlock> blocksToAdd = new ArrayList<StoredBlock>();
                if (!mnListsCache.containsKey(tipHash) && !pendingBlocksMap.containsKey(tipHash)) {
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

            if(pendingBlocks.size() == 0)
                return;

            //if(downloadPeer == null)
            //    chooseRandomDownloadPeer();

            if(downloadPeer != null) {

                Iterator<StoredBlock> blockIterator = pendingBlocks.iterator();
                StoredBlock nextBlock;
                while(blockIterator.hasNext()) {
                    nextBlock = blockIterator.next();
                    if(nextBlock.getHeight() <= mnList.getHeight()) {
                        blockIterator.remove();
                        pendingBlocksMap.remove(nextBlock.getHeader().getHash());
                    }
                    else break;
                }

                if(pendingBlocks.size() != 0) {
                    nextBlock = pendingBlocks.get(0);
                    if(syncInterval > 1 && nextBlock.getHeader().getTimeSeconds() < Utils.currentTimeSeconds() - 60 * 60 && pendingBlocks.size() > syncInterval) {
                        //lets skip up to the next syncInterval blocks
                        while(blockIterator.hasNext()) {
                            nextBlock = blockIterator.next();
                            if(nextBlock.getHeight() % syncInterval == 0) break;
                            blockIterator.remove();
                        }
                    }
                    log.info("requesting mnlistdiff from {} to {}; \n  From {}\n To {}", mnList.getHeight(), nextBlock.getHeight(), mnList.getBlockHash(), nextBlock.getHeader().getHash());
                    GetSimplifiedMasternodeListDiff requestMessage = new GetSimplifiedMasternodeListDiff(mnList.getBlockHash(), nextBlock.getHeader().getHash());
                    if(requestMessage.equals(lastRequestMessage)) {
                        log.info("request for mnlistdiff is the same as the last request");
                    }
                    //try {
                        downloadPeer.sendMessage(requestMessage);
                    /*} catch (CancelledKeyException x) {
                        //the connection was closed
                        chooseRandomDownloadPeer();
                        downloadPeer.sendMessage(requestMessage);
                    }*/
                    lastRequestMessage = requestMessage;
                    lastRequestTime = Utils.currentTimeMillis();
                    waitingForMNListDiff = true;
                }
            }
        } finally {
            lock.unlock();
        }

    }

    public void setBlockChain(AbstractBlockChain blockChain, AbstractBlockChain headersChain, PeerGroup peerGroup) {
        this.blockChain = blockChain;
        this.peerGroup = peerGroup;
        this.headersChain = headersChain;
        if(shouldProcessMNListDiff()) {
            blockChain.addNewBestBlockListener(Threading.SAME_THREAD, newBestBlockListener);
            blockChain.addReorganizeListener(reorganizeListener);
            if (peerGroup != null) {
                peerGroup.addConnectedEventListener(peerConnectedEventListener);
                peerGroup.addChainDownloadStartedEventListener(chainDownloadStartedEventListener);
                peerGroup.addDisconnectedEventListener(peerDisconnectedEventListener);
            }
        }
    }

    @Override
    public void close() {
        if(shouldProcessMNListDiff()) {
            blockChain.removeNewBestBlockListener(newBestBlockListener);
            blockChain.removeReorganizeListener(reorganizeListener);
            peerGroup.removeConnectedEventListener(peerConnectedEventListener);
            peerGroup.removeChainDownloadStartedEventListener(chainDownloadStartedEventListener);
            peerGroup.removeDisconnectedEventListener(peerDisconnectedEventListener);
            try {
                save();
            } catch (FileNotFoundException x) {
                //do nothing
            }
        }
    }

    public void requestMNListDiff(StoredBlock block) {
        requestMNListDiff(null, block);
    }

    public void requestMNListDiff(Peer peer, StoredBlock block) {
        Sha256Hash hash = block.getHeader().getHash();
        //log.info("getmnlistdiff:  current block:  " + tipHeight + " requested block " + block.getHeight());

        if(block.getHeader().getTimeSeconds() < Utils.currentTimeSeconds() - SNAPSHOT_TIME_PERIOD)
            return;

        if(failedAttempts > MAX_ATTEMPTS) {
            log.info("failed attempts maximum reached");
            failedAttempts = 0;
            resetMNList(true);
        }

        if(pendingBlocksMap.put(hash, block) == null) {
            log.info("adding 1 block to the pending queue: {} - {}", block.getHeight(), block.getHeader().getHash());
            pendingBlocks.add(block);
        }

        if(!waitingForMNListDiff)
            requestNextMNListDiff();

        if(lastRequestTime + WAIT_GETMNLISTDIFF * 4 < Utils.currentTimeMillis()) {
            maybeGetMNListDiffFresh();
        }
    }

    public void updateMNList() {
        requestMNListDiff(context.blockChain.getChainHead());
    }

    @Override
    public String toString() {
        return "SimplifiedMNListManager:  {tip:" + mnList + ", " + quorumList + ", pending blocks: " + pendingBlocks.size() + "}";
    }

    @Deprecated
    public long getSpork15Value() {
        return 0;
    }

    public boolean isDeterministicMNsSporkActive(long height) {
        if(height == -1) {
            height = mnList.getHeight();
        }

        return height > params.getDeterministicMasternodesEnabledHeight();
    }

    public boolean isDeterministicMNsSporkActive() {
        return isDeterministicMNsSporkActive(-1) || params.isDeterministicMasternodesEnabled();
    }

    public SimplifiedMasternodeList getListAtChainTip() {
        return mnList;
    }

    public SimplifiedQuorumList getQuorumListAtTip() {
        return quorumList;
    }

    @Override
    public int getCurrentFormatVersion() {
        return quorumList.size() != 0 ? LLMQ_FORMAT_VERSION : DMN_FORMAT_VERSION;
    }

    public void resetMNList() {
        resetMNList(false, true);
    }

    public void resetMNList(boolean force) {
        resetMNList(force, true);
    }


    public void resetMNList(boolean force, boolean requestFreshList) {
        try {
            if(force || getFormatVersion() < LLMQ_FORMAT_VERSION) {
                log.info("resetting masternode list");
                mnList = new SimplifiedMasternodeList(context.getParams());
                quorumList = new SimplifiedQuorumList(context.getParams());
                mnListsCache.clear();
                quorumsCache.clear();
                pendingBlocks.clear();
                pendingBlocksMap.clear();
                waitingForMNListDiff = false;
                initChainTipSync = false;
                unCache();
                try {
                    if(bootStrapFilePath == null)
                        save();
                } catch (FileNotFoundException x) {
                    //swallow, the file has no name
                }
                if(requestFreshList) {
                    if(bootStrapFilePath == null && bootStrapStream == null) {
                        requestAfterMNListReset();
                    } else {
                        waitingForMNListDiff = true;
                        isLoadingBootStrap = true;
                        loadBootstrapAndSync();
                    }
                }
            }
        } catch (BlockStoreException x) {
            throw new RuntimeException(x);
        }
    }

    protected void requestAfterMNListReset() throws BlockStoreException {
        if(blockChain == null) //not initialized
            return;
        int rewindBlockCount = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_LIST_PERIOD : MAX_CACHE_SIZE;
        int height = blockChain.getBestChainHeight() - rewindBlockCount;
        if (height < params.getDIP0008BlockHeight())
            height = params.getDIP0008BlockHeight();
        if (syncOptions == SyncOptions.SYNC_MINIMUM)
            height = blockChain.getBestChainHeight() - SigningManager.SIGN_HEIGHT_OFFSET;
        StoredBlock resetBlock = blockChain.getBlockStore().get(height);
        if (resetBlock == null)
            resetBlock = blockChain.getChainHead();
        requestMNListDiff(resetBlock != null ? resetBlock : blockChain.getChainHead());
    }

    public SimplifiedMasternodeList getListForBlock(Sha256Hash blockHash) {
        lock.lock();
        try {
            return mnListsCache.get(blockHash);
        } finally {
            lock.unlock();
        }
    }

    public SimplifiedQuorumList getQuorumListForBlock(Sha256Hash blockHash) {
        lock.lock();
        try {
            return quorumsCache.get(blockHash);
        } finally {
            lock.unlock();
        }
    }

    public ArrayList<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash)
    {
        lock.lock();
        try {
            LLMQParameters llmqParameters = params.getLlmqs().get(llmqType);
            SimplifiedMasternodeList allMns = getListForBlock(blockHash);
            if (allMns != null) {
                Sha256Hash modifier = LLMQUtils.buildLLMQBlockHash(llmqType, blockHash);
                return allMns.calculateQuorum(llmqParameters.getSize(), modifier);
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    public boolean isSynced() {
        return pendingBlocks.isEmpty();
    }

    void fillPendingBlocksList(Sha256Hash first, Sha256Hash last, int insertIndex) {
        lock.lock();
        try {
            StoredBlock cursor = blockChain.getBlockStore().get(last);
            while(cursor != null && !cursor.getHeader().getHash().equals(first)) {
                if(!pendingBlocksMap.containsKey(cursor.getHeader().getHash())) {
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

    public void setRequiresLoadingFromFile(boolean requiresLoadingFromFile) {
        this.requiresLoadingFromFile = requiresLoadingFromFile;
    }

    public void setLoadedFromFile(boolean loadedFromFile) {
        this.loadedFromFile = loadedFromFile;
    }

    public boolean isLoadedFromFile() {
        return loadedFromFile || !requiresLoadingFromFile;
    }

    @Override
    public void onFirstSaveComplete() {
        lock.lock();
        try {
            if(blockChain == null || blockChain.getBestChainHeight() >= getListAtChainTip().getHeight())
                return;
            StoredBlock block = blockChain.getChainHead();
            long timePeriod = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60;
            if (Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < timePeriod) {
                if (syncOptions == SyncOptions.SYNC_MINIMUM) {
                    try {
                        StoredBlock requestBlock = blockChain.getBlockStore().get(block.getHeight() - SigningManager.SIGN_HEIGHT_OFFSET);
                        if (getListAtChainTip().getHeight() > requestBlock.getHeight())
                            requestBlock = blockChain.getBlockStore().get((int) getListAtChainTip().getHeight() + 1);
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

    static String bootStrapFilePath = null;
    static InputStream bootStrapStream = null;
    boolean isLoadingBootStrap = false;

    public static void setBootStrapFilePath(String bootStrapFilePath) {
        SimplifiedMasternodeListManager.bootStrapFilePath = bootStrapFilePath;
    }

    public static void setBootStrapStream(InputStream bootStrapStream) {
        SimplifiedMasternodeListManager.bootStrapStream = bootStrapStream;
    }

    protected void loadBootstrapAndSync() {
        Preconditions.checkState(bootStrapFilePath != null || bootStrapStream != null);
        Preconditions.checkState(mnList.size() == 0);
        Preconditions.checkState(quorumList.size() == 0);
        Preconditions.checkState(mnListsCache.size() == 0);
        Preconditions.checkState(quorumsCache.size() == 0);

        new Thread(new Runnable() {
            @Override
            public void run() {

                log.info("loading mnlistdiff bootstrap file: " + bootStrapFilePath != null ? bootStrapFilePath : "input stream");
                Context.propagate(context);
                //load the file
                InputStream stream = bootStrapStream;
                try {
                    if(stream != null)
                        stream.reset();
                    stream = stream != null ? stream : new FileInputStream(bootStrapFilePath);
                    byte[] buffer = new byte[(int) stream.available()];
                    stream.read(buffer);

                    isLoadingBootStrap = true;
                    SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(params, buffer);
                    processMasternodeListDiff(mnlistdiff, true);
                    log.info("finished loading mnlist bootstrap file");
                } catch (VerificationException | FileNotFoundException x) {
                    log.info("failed loading mnlist bootstrap file" + x.getMessage());
                } catch (IOException x) {
                    log.info("failed loading mnlist bootstrap file" + x.getMessage());
                } catch (IllegalStateException x) {
                    log.info("failed loading mnlist bootstrap file" + x.getMessage());
                } catch (NullPointerException x) {
                    log.info("failed loading mnlist bootstrap file" + x.getMessage());
                } finally {
                    isLoadingBootStrap = false;
                    try {
                        if (stream != null)
                            stream.close();
                        requestAfterMNListReset();
                    } catch (IOException x) {

                    } catch (BlockStoreException x) {

                    }
                }
            }
        }).start();
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public Peer getDownloadPeer() {
        return downloadPeer != null ? downloadPeer : peerGroup.getDownloadPeer();
    }
}
