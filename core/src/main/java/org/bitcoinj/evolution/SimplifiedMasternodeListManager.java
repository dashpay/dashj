package org.bitcoinj.evolution;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.ChainDownloadStartedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.core.listeners.ReorganizeListener;
import org.bitcoinj.evolution.listeners.MasternodeListDownloadedListener;
import org.bitcoinj.quorums.GetQuorumRotationInfo;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.quorums.PreviousQuorumQuarters;
import org.bitcoinj.quorums.Quorum;
import org.bitcoinj.quorums.QuorumManager;
import org.bitcoinj.quorums.QuorumRotationInfo;
import org.bitcoinj.quorums.QuorumSnapshot;
import org.bitcoinj.quorums.QuorumSnapshotManager;
import org.bitcoinj.quorums.SigningManager;
import org.bitcoinj.quorums.SnapshotSkipMode;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class SimplifiedMasternodeListManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(SimplifiedMasternodeListManager.class);
    private ReentrantLock lock = Threading.lock("SimplifiedMasternodeListManager");

    static final int DMN_FORMAT_VERSION = 1;
    static final int LLMQ_FORMAT_VERSION = 2;
    static final int QUORUM_ROTATION_FORMAT_VERSION = 3;

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
            if (!LLMQUtils.isQuorumRotationEnabled(context, params, params.getLlmqForInstantSend())) {
                return size() > (syncOptions == SyncOptions.SYNC_MINIMUM ? MIN_CACHE_SIZE : MAX_CACHE_SIZE);
            } else {
                return size() > 4;
            }
        }
    };

    LinkedHashMap<Sha256Hash, SimplifiedQuorumList> quorumsCache = new LinkedHashMap<Sha256Hash, SimplifiedQuorumList>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, SimplifiedQuorumList> eldest) {
            if (!LLMQUtils.isQuorumRotationEnabled(context, params, params.getLlmqForInstantSend())) {
                return size() > (syncOptions == SyncOptions.SYNC_MINIMUM ? MIN_CACHE_SIZE : MAX_CACHE_SIZE);
            } else {
                return size() > 4;
            }
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
    boolean initChainTipSyncComplete = false;
    LinkedHashMap<Sha256Hash, StoredBlock> pendingBlocksMap;
    ArrayList<StoredBlock> pendingBlocks;

    int failedAttempts;
    static final int MAX_ATTEMPTS = 10;
    boolean loadedFromFile;
    boolean requiresLoadingFromFile;

    PeerGroup peerGroup;

    //DIP24
    //     static std::map<Consensus::LLMQType, unordered_lru_cache<uint256, std::vector<CDeterministicMNCPtr>, StaticSaltedHasher>> mapQuorumMembers GUARDED_BY(cs_members);
    private ReentrantLock memberLock = Threading.lock("MemberLock");
    @GuardedBy("memberLock")
    HashMap<LLMQParameters.LLMQType, HashMap<Sha256Hash, ArrayList<Masternode>>> mapQuorumMembers = new HashMap<>();
    //static std::map<Consensus::LLMQType, unordered_lru_cache<std::pair<uint256, int>, std::vector<CDeterministicMNCPtr>, StaticSaltedHasher>> mapIndexedQuorumMembers GUARDED_BY(cs_indexed_members);
    private ReentrantLock indexedMemberLock = Threading.lock("IndexedMemberLock");
    @GuardedBy("indexedMemberLock")
    HashMap<LLMQParameters.LLMQType, HashMap<Pair<Sha256Hash, Integer>, ArrayList<Masternode>>> mapIndexedQuorumMembers = new HashMap<>();
    QuorumSnapshotManager quorumSnapshotManager;
    QuorumManager quorumManager;

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

    public SimplifiedMasternodeListManager(Context context) {
        super(context);
        tipBlockHash = params.getGenesisBlock().getHash();
        mnList = new SimplifiedMasternodeList(context.getParams());
        quorumList = new SimplifiedQuorumList(context.getParams());

        mnListTip = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinusC = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus2C = new SimplifiedMasternodeList(context.getParams());
        mnListAtHMinus3C = new SimplifiedMasternodeList(context.getParams());
        quorumListTip = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinusC = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus2C = new SimplifiedQuorumList(context.getParams());
        quorumListAtHMinus3C = new SimplifiedQuorumList(context.getParams());

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
        initChainTipSyncComplete = !context.getSyncFlags().contains(MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST);
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

    public void processMasternodeListDiff(Peer peer, SimplifiedMasternodeListDiff mnlistdiff) {
        if(!shouldProcessMNListDiff())
            return;

        processMasternodeListDiff(peer, mnlistdiff, false);
    }


    public void processMasternodeListDiff(@Nullable Peer peer, SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap) {
        StoredBlock block = null;
        long newHeight = ((CoinbaseTx) mnlistdiff.coinBaseTx.getExtraPayloadObject()).getHeight();
        if (peer != null) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Received, mnlistdiff);
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
            if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Processing, mnlistdiff);

            SimplifiedMasternodeList newMNList = mnList.applyDiff(mnlistdiff);
            if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_MNLIST))
                newMNList.verify(mnlistdiff.coinBaseTx, mnlistdiff, mnList);
            if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedMasternodes, mnlistdiff);
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
            if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.ProcessedQuorums, mnlistdiff);
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

            if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Finished, mnlistdiff);

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

    public void processQuorumRotationInfo(@Nullable Peer peer, QuorumRotationInfo quorumRotationInfo, boolean isLoadingBootStrap) {
        StoredBlock block;
        StoredBlock blockTip;
        StoredBlock blockMinusC;
        StoredBlock blockMinus2C;
        StoredBlock blockMinus3C;
        long newHeight = ((CoinbaseTx) quorumRotationInfo.getMnListDiffTip().coinBaseTx.getExtraPayloadObject()).getHeight();
        if (peer != null) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Received, quorumRotationInfo.getMnListDiffTip());
        Stopwatch watch = Stopwatch.createStarted();
        Stopwatch watchMNList = Stopwatch.createUnstarted();
        Stopwatch watchQuorums = Stopwatch.createUnstarted();
        boolean isSyncingHeadersFirst = context.peerGroup != null && context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;
        log.info("processing quorumrotationinfo between : {} & {}; {}", mnList.getHeight(), newHeight, quorumRotationInfo);
        lock.lock();
        try {
            block = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffTip().blockHash);
            blockTip = chain.getBlockStore().get((int)quorumRotationInfo.getCreationHeight());
            blockMinusC = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinusC().blockHash);
            blockMinus2C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus2C().blockHash);
            blockMinus3C = chain.getBlockStore().get(quorumRotationInfo.getMnListDiffAtHMinus3C().blockHash);

            if(!isLoadingBootStrap && block.getHeight() != newHeight)
                throw new ProtocolException("mnlistdiff blockhash (height="+block.getHeight()+" doesn't match coinbase blockheight: " + newHeight);

            watchMNList.start();
            if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Processing, quorumRotationInfo.getMnListDiffTip());

            SimplifiedMasternodeList newMNListTip = mnListTip.applyDiff(quorumRotationInfo.getMnListDiffTip());
            SimplifiedMasternodeList newNMListAtHMinusC = mnListAtHMinusC.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC());
            SimplifiedMasternodeList newNMListAtHMinus2C = mnListAtHMinus2C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C());
            SimplifiedMasternodeList newNMListAtHMinus3C = mnListAtHMinus3C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C());
            if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_MNLIST)) {
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

            // now calculate quorums?

            //SimplifiedQuorumList newQuorumListTip = quorumListTip;
            //SimplifiedQuorumList newQuorumListTipAtHMinusC = quorumListAtHMinusC;
            //SimplifiedQuorumList newQuorumListTipAtHMinus2C = quorumListAtHMinus2C;
            //SimplifiedQuorumList newQuorumListTipAtHMinus3C = quorumListAtHMinus3C;

            watchQuorums.start();

            SimplifiedQuorumList newQuorumListTip = quorumListTip.applyDiff(quorumRotationInfo.getMnListDiffTip(), isLoadingBootStrap, chain);
            SimplifiedQuorumList newQuorumListTipAtHMinusC = quorumListAtHMinusC.applyDiff(quorumRotationInfo.getMnListDiffAtHMinusC(), isLoadingBootStrap, chain);
            SimplifiedQuorumList newQuorumListTipAtHMinus2C = quorumListAtHMinus2C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus2C(), isLoadingBootStrap, chain);
            SimplifiedQuorumList newQuorumListTipAtHMinus3C = quorumListAtHMinus3C.applyDiff(quorumRotationInfo.getMnListDiffAtHMinus3C(), isLoadingBootStrap, chain);
            if(context.masternodeSync.hasVerifyFlag(MasternodeSync.VERIFY_FLAGS.MNLISTDIFF_QUORUM)) {
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

            mnList = newMNListTip;
            mnListTip = newMNListTip;
            mnListAtHMinusC = newNMListAtHMinusC;
            mnListAtHMinus2C = newNMListAtHMinus2C;
            mnListAtHMinus3C = newNMListAtHMinus3C;

            quorumList = newQuorumListTip;
            quorumListTip = newQuorumListTip;
            quorumListAtHMinusC = newQuorumListTipAtHMinusC;
            quorumListAtHMinus2C = newQuorumListTipAtHMinus2C;
            quorumListAtHMinus3C = newQuorumListTipAtHMinus3C;

            //quorumList = newQuorumList;
            log.info(this.toString());
            unCache();
            failedAttempts = 0;

            if(!pendingBlocks.isEmpty()) {
                StoredBlock thisBlock = pendingBlocks.get(0);
                pendingBlocks.remove(0);
                pendingBlocksMap.remove(thisBlock.getHeader().getHash());
            } else log.warn("pendingBlocks is empty");

            if (peer != null && isSyncingHeadersFirst) peer.queueMasternodeListDownloadedListeners(MasternodeListDownloadedListener.Stage.Finished, quorumRotationInfo.getMnListDiffTip());

            setFormatVersion(LLMQ_FORMAT_VERSION);
            if(quorumRotationInfo.getMnListDiffTip().hasChanges() || pendingBlocks.size() < MAX_CACHE_SIZE || saveOptions == SaveOptions.SAVE_EVERY_BLOCK)
                save();

            log.info("getAllQuorums: {}", getAllQuorumMembers(params.getLlmqForInstantSend(), blockTip.getHeader().getHash()).size());
            ArrayList<Quorum> list = context.quorumManager.scanQuorums(params.getLlmqForInstantSend(), LLMQParameters.fromType(params.getLlmqForInstantSend()).getSigningActiveQuorumCount());
            log.info("quorum list: {}", list.toString());
        } catch(MasternodeListDiffException x) {
            //we already have this mnlistdiff or doesn't match our current tipBlockHash
            if(mnList.getBlockHash().equals(quorumRotationInfo.getMnListDiffTip().blockHash)) {
                log.info("heights are the same:  " + x.getMessage());
                log.info("mnList = {} vs mnlistdiff {}", mnList.getBlockHash(), quorumRotationInfo.getMnListDiffTip().prevBlockHash);
                log.info("mnlistdiff {} -> {}", quorumRotationInfo.getMnListDiffTip().prevBlockHash, quorumRotationInfo.getMnListDiffTip().blockHash);
                log.info("lastRequest {} -> {}", lastRequestMessage.baseBlockHash, lastRequestMessage.blockHash);
                //remove this block from the list
                if(pendingBlocks.size() > 0) {
                    StoredBlock thisBlock = pendingBlocks.get(0);
                    if(thisBlock.getHeader().getPrevBlockHash().equals(quorumRotationInfo.getMnListDiffTip().prevBlockHash) &&
                            thisBlock.getHeader().getHash().equals(quorumRotationInfo.getMnListDiffTip().prevBlockHash)) {
                        pendingBlocks.remove(0);
                        pendingBlocksMap.remove(thisBlock.getHeader().getHash());
                    }
                }
            } else {
                log.info("heights are different " + x.getMessage());
                log.info("mnlistdiff height = {}; mnList: {}; quorumList: {}", newHeight, mnList.getHeight(), quorumList.getHeight());
                log.info("mnList = {} vs mnlistdiff = {}", mnList.getBlockHash(), quorumRotationInfo.getMnListDiffTip().prevBlockHash);
                log.info("mnlistdiff {} -> {}", quorumRotationInfo.getMnListDiffTip().prevBlockHash, quorumRotationInfo.getMnListDiffTip().blockHash);
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
            log.info("NPE: close this peer: ", x);
            failedAttempts++;
            throw new VerificationException("verification error: NPE", x);
        } catch(FileNotFoundException x) {
            //file name is not set, do not save
            log.info(x.getMessage());
        } catch(BlockStoreException x) {
            log.info(x.getMessage());
            failedAttempts++;
            throw new ProtocolException(x);
        } finally {
            watch.stop();
            log.info("processing mnlistdiff times : Total: " + watch + "mnList: " + watchMNList + " quorums" + watchQuorums + "mnlistdiff" + quorumRotationInfo.getMnListDiffTip());
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

    public NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            boolean value = initChainTipSyncComplete || !context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_HEADERS_MN_LIST_FIRST);
            if(value && getListAtChainTip().getHeight() < blockChain.getBestChainHeight() && isDeterministicMNsSporkActive() && isLoadedFromFile()) {
                long timePeriod = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE  * 3 * 60L;
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
                boolean value = initChainTipSyncComplete;
                if (value && getListAtChainTip().getHeight() < blockChain.getBestChainHeight() && isDeterministicMNsSporkActive() && isLoadedFromFile()) {
                    maybeGetMNListDiffFresh();
                    if (!waitingForMNListDiff && mnList.getBlockHash().equals(params.getGenesisBlock().getHash()) || mnList.getHeight() < blockChain.getBestChainHeight()) {
                        long timePeriod = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60L;
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
            long timePeriod = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60L;
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
            if (LLMQUtils.isQuorumRotationEnabled(context, params, params.getLlmqForInstantSend())) {
                lastRequestMessage = new GetSimplifiedMasternodeListDiff(mnList.getBlockHash(), endBlock.getHeader().getHash());
                downloadPeer.sendMessage(lastRequestMessage);
            } else {
                GetQuorumRotationInfo msg = new GetQuorumRotationInfo(params, 0, Lists.newArrayList(), endBlock.getHeader().getHash());
                downloadPeer.sendMessage(msg);
            }
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
                    GetQuorumRotationInfo requestMessage2 = new GetQuorumRotationInfo(params, 0, Lists.newArrayList(), mnList.getBlockHash());
                    if(requestMessage.equals(lastRequestMessage)) {
                        log.info("request for mnlistdiff is the same as the last request");
                    }

                    if (!LLMQUtils.isQuorumRotationEnabled(context, params, params.getLlmqForInstantSend())) {
                        downloadPeer.sendMessage(requestMessage);
                    } else {
                        log.info("message = {}, {}", requestMessage2, downloadPeer);
                        downloadPeer.sendMessage(requestMessage2);
                    }

                    lastRequestMessage = requestMessage;
                    lastRequestTime = Utils.currentTimeMillis();
                    waitingForMNListDiff = true;
                }
            }
        } finally {
            lock.unlock();
        }

    }

    public void setBlockChain(AbstractBlockChain blockChain, @Nullable AbstractBlockChain headersChain, @Nullable PeerGroup peerGroup,
                              QuorumManager quorumManager, QuorumSnapshotManager quorumSnapshotManager) {
        this.blockChain = blockChain;
        this.peerGroup = peerGroup;
        this.headersChain = headersChain;
        this.quorumManager = quorumManager;
        this.quorumSnapshotManager = quorumSnapshotManager;
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
                initChainTipSyncComplete = false;
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
            if (LLMQUtils.isQuorumRotationEnabled(context, params, llmqType)) {
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
                //quorumManager.setQuorumIndexQuorumHash(llmqType, quorumBaseBlock.getHeader().getHash(), quorumIndex);

                return quorumMembers;
            } else {
                LLMQParameters llmqParameters = params.getLlmqs().get(llmqType);
                SimplifiedMasternodeList allMns = getListForBlock(blockHash);
                if (allMns != null) {
                    Sha256Hash modifier = LLMQUtils.buildLLMQBlockHash(llmqType, blockHash);
                    return allMns.calculateQuorum(llmqParameters.getSize(), modifier);
                }
                return null;
            }
        } catch (BlockStoreException x) {
            // we are in deep trouble
            throw new RuntimeException(x);
        } finally {
            lock.unlock();
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
        ArrayList<Masternode> list = mns.calculateQuorum(mns.getAllMNsCount(), quorumBaseBlock.getHeader().getHash());

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
            long timePeriod = syncOptions == SyncOptions.SYNC_SNAPSHOT_PERIOD ? SNAPSHOT_TIME_PERIOD : MAX_CACHE_SIZE * 3 * 60L;
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
                    processMasternodeListDiff(null, mnlistdiff, true);
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
}
