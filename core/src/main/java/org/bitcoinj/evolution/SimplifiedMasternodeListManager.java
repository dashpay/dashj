package org.bitcoinj.evolution;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.AbstractManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VarInt;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.quorums.FinalCommitment;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.Quorum;
import org.bitcoinj.quorums.QuorumManager;
import org.bitcoinj.quorums.QuorumRotationInfo;
import org.bitcoinj.quorums.QuorumSnapshotManager;
import org.bitcoinj.quorums.SigningManager;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
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

    public static int MAX_CACHE_SIZE = 10;
    public static int MIN_CACHE_SIZE = 1;

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
        try {
            if (headersChain != null && headersChain.getBestChainHeight() > blockChain.getBestChainHeight()) {
                return headersChain.getBlockStore().get(quorumHash).getHeight();
            } else return blockChain.getBlockStore().get(quorumHash).getHeight();
        } catch (BlockStoreException x) {
            return -1;
        }
    }

    public StoredBlock getBlockTip() {
        if (headersChain != null && headersChain.getBestChainHeight() > blockChain.getBestChainHeight()) {
            return headersChain.getChainHead();
        } else {
            return blockChain.getChainHead();
        }
    }

    public enum SaveOptions {
        SAVE_EVERY_BLOCK,
        SAVE_EVERY_CHANGE,
    };

    public SaveOptions saveOptions;

    // TODO: move to different file
    public enum SyncOptions {
        SYNC_SNAPSHOT_PERIOD,
        SYNC_CACHE_PERIOD,
        SYNC_MINIMUM;
    }

    long tipHeight;
    Sha256Hash tipBlockHash;

    AbstractBlockChain blockChain;
    AbstractBlockChain headersChain;

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

        quorumState = new QuorumState(context, SyncOptions.SYNC_MINIMUM);
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
        quorumState = new QuorumState(context, SyncOptions.SYNC_MINIMUM, payload, cursor);
        quorumState.setStateManager(this);
        cursor += quorumState.getMessageSize();
        tipBlockHash = readHash();
        tipHeight = readUint32();
        if(getFormatVersion() >= LLMQ_FORMAT_VERSION) {
            cursor += quorumState.parseQuorums(payload, cursor);
            quorumState.setStateManager(this);
            //read pending blocks
            parsePendingBlocks(quorumState);
        }
        if (getFormatVersion() >= QUORUM_ROTATION_FORMAT_VERSION && (cursor < payload.length)) {
            quorumRotationState = new QuorumRotationState(context, payload, cursor);
            quorumRotationState.setStateManager(this);
            cursor += quorumRotationState.getMessageSize();
        }
        processQuorumList(quorumState.getQuorumListAtTip());
        processQuorumList(quorumRotationState.getQuorumListAtH());
        length = cursor - offset;
    }

    private <T extends AbstractQuorumRequest, D extends AbstractDiffMessage> void parsePendingBlocks(AbstractQuorumState<T, D> state) {
        int size = (int)readVarInt();
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
        for(int i = 0; i < size; ++i) {
            buffer.put(readBytes(StoredBlock.COMPACT_SERIALIZED_SIZE));
            buffer.rewind();
            StoredBlock block = StoredBlock.deserializeCompact(params, buffer);
            if(block.getHeight() != 0 && state.syncOptions != SyncOptions.SYNC_MINIMUM) {
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
            SimplifiedQuorumList quorumListToSave = null;
            ArrayList<StoredBlock> otherPendingBlocks = new ArrayList<StoredBlock>(MAX_CACHE_SIZE);
            if (getMasternodeListCache().size() > 0) {
                for (Map.Entry<Sha256Hash, SimplifiedMasternodeList> entry : getMasternodeListCache().entrySet()) {
                    if (mnListToSave == null) {
                        mnListToSave = entry.getValue();
                        quorumListToSave = getQuorumListCache(params.getLlmqChainLocks()).get(entry.getKey());
                    } else {
                        otherPendingBlocks.add(entry.getValue().getStoredBlock());
                    }
                }
            } else {
                mnListToSave = quorumState.mnList;
                quorumListToSave = quorumState.quorumList;
            }

            quorumState.bitcoinSerialize(stream);
            stream.write(mnListToSave.getBlockHash().getReversedBytes());
            Utils.uint32ToByteStreamLE(mnListToSave.getHeight(), stream);

            if (getFormatVersion() >= LLMQ_FORMAT_VERSION) {
                quorumState.serializeQuorumsToStream(stream);
                if (quorumState.syncOptions != SyncOptions.SYNC_MINIMUM) {
                    stream.write(new VarInt(quorumState.getPendingBlocks().size() + otherPendingBlocks.size()).encode());
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

    public void processMasternodeListDiff(Peer peer, SimplifiedMasternodeListDiff mnlistdiff) {
        if(!shouldProcessMNListDiff())
            return;

        processMasternodeListDiff(peer, mnlistdiff, false);
    }

    public void processMasternodeListDiff(@Nullable Peer peer, SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap) {
        try {
            quorumState.processDiff(peer, mnlistdiff, headersChain, blockChain, isLoadingBootStrap);

            processQuorumList(quorumState.getQuorumListAtTip());

            unCache();
            if (mnlistdiff.coinBaseTx.getExtraPayloadObject().getVersion() >= LLMQ_FORMAT_VERSION && quorumState.quorumList.size() > 0)
                setFormatVersion(LLMQ_FORMAT_VERSION);
            if (mnlistdiff.hasChanges() || quorumState.getPendingBlocks().size() < MAX_CACHE_SIZE || saveOptions == SaveOptions.SAVE_EVERY_BLOCK)
                save();

            // if DIP24 is not activated, then trigger a getqrinfo
            completeQuorumState(peer);
        } catch (FileNotFoundException x) {
            //file name is not set, do not save
            log.info(x.getMessage());
        } finally {
            // TODO: do we need this finally block?
        }
    }

    public void requestQuorumStateUpdate(Peer downloadPeer, StoredBlock requestBlock, StoredBlock requestBlockMinus8) {
        // TODO: reactivate for testnet
        if (!params.isDIP24Only()) {
            quorumState.requestMNListDiff(downloadPeer, requestBlockMinus8);
        }
        if (isQuorumRotationEnabled(params.getLlmqDIP0024InstantSend())) {
            quorumRotationState.requestMNListDiff(downloadPeer, requestBlock);
        }
    }

    public void processQuorumRotationInfo(@Nullable Peer peer, QuorumRotationInfo quorumRotationInfo, boolean isLoadingBootStrap) {
        try {
            quorumRotationState.processDiff(peer, quorumRotationInfo, headersChain, blockChain, isLoadingBootStrap);

            setFormatVersion(QUORUM_ROTATION_FORMAT_VERSION);
            unCache();
            if (quorumRotationInfo.hasChanges() || quorumRotationState.getPendingBlocks().size() < MAX_CACHE_SIZE || saveOptions == SimplifiedMasternodeListManager.SaveOptions.SAVE_EVERY_BLOCK)
                save();
        } catch (FileNotFoundException x) {
            //file name is not set, do not save
            log.info(x.getMessage());
        } finally {
            // TODO: do we need a finally?
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

    public void setBlockChain(AbstractBlockChain blockChain, @Nullable AbstractBlockChain headersChain, @Nullable PeerGroup peerGroup,
                              QuorumManager quorumManager, QuorumSnapshotManager quorumSnapshotManager) {
        this.blockChain = blockChain;
        this.peerGroup = peerGroup;
        this.headersChain = headersChain;
        this.quorumManager = quorumManager;
        this.quorumSnapshotManager = quorumSnapshotManager;
        AbstractBlockChain activeChain = headersChain != null ? headersChain : blockChain;
        quorumState.setBlockChain(activeChain);
        quorumRotationState.setBlockChain(activeChain);
        if(shouldProcessMNListDiff()) {
            // TODO: reactivate for testnet
            if (!params.isDIP24Only()) {
                quorumState.addEventListeners(blockChain, peerGroup);
            }
            // not sure what to do here
            //if (isQuorumRotationEnabled(params.getLlmqDIP0024InstantSend())) {
                quorumRotationState.addEventListeners(blockChain, peerGroup);
            //}
        }
    }

    @Override
    public void close() {
        if(shouldProcessMNListDiff()) {
            // TODO: reactivate for testnet
            if (!params.isDIP24Only()) {
                quorumState.removeEventListeners(blockChain, peerGroup);
            }
            if (isQuorumRotationEnabled(params.getLlmqDIP0024InstantSend())) {
                quorumRotationState.removeEventListeners(blockChain, peerGroup);
            }

            try {
                save();
            } catch (FileNotFoundException x) {
                //do nothing
            }
        }
    }

    @Override
    public String toString() {
        StoredBlock firstPending = quorumRotationState.pendingBlocks.size() > 0 ? quorumRotationState.pendingBlocks.get(0) : null;
        int height = firstPending != null ? firstPending.getHeight() : -1;
        return "SimplifiedMNListManager:  {tip:" + getMasternodeList() +
                ", " + getQuorumListAtTip(params.getLlmqChainLocks()) +
                ", " + getQuorumListAtTip(params.getLlmqDIP0024InstantSend()) +
                ", pending blocks: " + quorumState.getPendingBlocks().size() +  " / " +
                quorumRotationState.getPendingBlocks().size() + (height != -1?  ("-->height: "+height+")") : "" ) +"}";
    }

    @Deprecated
    public long getSpork15Value() {
        return 0;
    }

    public boolean isQuorumRotationEnabled(LLMQParameters.LLMQType type) {
        if (blockChain == null) {
            return formatVersion == QUORUM_ROTATION_FORMAT_VERSION;
        }
        boolean quorumRotationActive = blockChain.getBestChainHeight() >= params.getDIP0024BlockHeight() ||
                (headersChain != null && headersChain.getBestChainHeight() >= params.getDIP0024BlockHeight());
        return params.getLlmqDIP0024InstantSend() == type && quorumRotationActive;
    }

    // TODO: this needs an argument for LLMQType
    public SimplifiedMasternodeList getListAtChainTip() {
        return getMasternodeList();
    }

    // TODO: this needs an argument for LLMQType
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
                try {
                    LLMQParameters llmqParameters = params.getLlmqs().get(llmqType);
                    StoredBlock block = blockChain.getBlockStore().get(blockHash);
                    if (block == null)
                        block = headersChain.getBlockStore().get(blockHash);
                    StoredBlock lastQuorumBlock = block.getAncestor(blockChain.getBlockStore(),
                            block.getHeight() - block.getHeight() % llmqParameters.getDkgInterval() - SigningManager.SIGN_HEIGHT_OFFSET);
                    if (lastQuorumBlock == null)
                        lastQuorumBlock = block.getAncestor(headersChain.getBlockStore(),
                                block.getHeight() - block.getHeight() % llmqParameters.getDkgInterval() - SigningManager.SIGN_HEIGHT_OFFSET);

                    //return getQuorumListCache(llmqType).get(lastQuorumBlock.getHeader().getHash());
                    return getQuorumListAtTip(llmqType);
                } catch (BlockStoreException x) {
                    throw new RuntimeException(x);
                }

            } else {
                return getQuorumListCache(llmqType).get(blockHash);
            }
        } finally {
            lock.unlock();
        }
    }

    public ArrayList<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash)
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
        return quorumState.isSynced();
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
    public boolean notUsingBootstrapFile() {
        return bootStrapFilePath == null;
    }

    @Override
    public boolean notUsingBootstrapFileAndStream() {
        return bootStrapFilePath == null && bootStrapStream == null;
    }

    @Override
    public void onFirstSaveComplete() {
        quorumState.onFirstSaveComplete();
        quorumRotationState.onFirstSaveComplete();
    }

    static String bootStrapFilePath = null;
    static InputStream bootStrapStream = null;
    static int bootStrapFileFormat = 0;
    public static SettableFuture<Boolean> bootStrapLoaded = SettableFuture.create();

    boolean isLoadingBootStrap = false;

    public static void setBootStrapFilePath(String bootStrapFilePath, int format) {
        SimplifiedMasternodeListManager.bootStrapFilePath = bootStrapFilePath;
        bootStrapFileFormat = format;
    }

    public static void setBootStrapStream(InputStream bootStrapStream, int format) {
        SimplifiedMasternodeListManager.bootStrapStream = bootStrapStream;
        bootStrapFileFormat = format;
    }

    @Override
    public void setLoadingBootstrap() {
        isLoadingBootStrap = true;
    }

    public void loadBootstrapAndSync() {
        Preconditions.checkState(bootStrapFilePath != null || bootStrapStream != null);
        Preconditions.checkState(getMasternodeList().size() == 0);
        Preconditions.checkState(getQuorumListAtTip(params.getLlmqChainLocks()).size() == 0);
        Preconditions.checkState(getMasternodeListCache().size() == 1);
        Preconditions.checkState(getQuorumListCache(params.getLlmqChainLocks()).size() == 1);

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
                    switch (bootStrapFileFormat) {
                        case LLMQ_FORMAT_VERSION:
                            SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(params, buffer);
                            processMasternodeListDiff(null, mnlistdiff, true);
                            break;
                        case QUORUM_ROTATION_FORMAT_VERSION:
                            QuorumRotationInfo qrinfo = new QuorumRotationInfo(params, buffer);
                            processQuorumRotationInfo(null, qrinfo, true);
                            break;
                        default:
                            throw new IllegalArgumentException("file format " + bootStrapFileFormat + " is not supported");
                    }
                    bootStrapLoaded.set(true);
                    log.info("finished loading mnlist bootstrap file");
                } catch (VerificationException | IOException | IllegalStateException | NullPointerException x) {
                    bootStrapLoaded.setException(x);
                    log.info("failed loading mnlist bootstrap file" + x.getMessage());
                } finally {
                    isLoadingBootStrap = false;
                    try {
                        if (stream != null)
                            stream.close();
                        quorumState.requestAfterMNListReset();
                        quorumRotationState.requestAfterMNListReset();
                    } catch (IOException x) {

                    } catch (BlockStoreException x) {
                        throw new RuntimeException(x);
                    }
                }
            }
        }).start();
    }

    /**
     * Used to determine if DIP24 has been activated.  This method won't be required when the
     * DIP24 activation height is hardcoded into NetworkParameters
     * @param quorumList the most recent quorum list from a "mnlistdiff" message
     */
    public void processQuorumList(SimplifiedQuorumList quorumList) {
        int height = (int)quorumList.getHeight();
        if (!params.isDIP0024Active(height)) {
            quorumList.forEachQuorum(true, new SimplifiedQuorumList.ForeachQuorumCallback() {
                @Override
                public void processQuorum(FinalCommitment finalCommitment) {
                    if (!params.isDIP0024Active(height) && finalCommitment.getLlmqType() == params.getLlmqDIP0024InstantSend().getValue()) {
                        params.setDIP0024Active(height);
                        setFormatVersion(QUORUM_ROTATION_FORMAT_VERSION);
                    }
                }
            });
        }
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
