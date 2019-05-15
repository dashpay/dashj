package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.ChainDownloadStartedEventListener;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class SimplifiedMasternodeListManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(MasternodeManager.class);
    private ReentrantLock lock = Threading.lock("SimplifiedMasternodeListManager");

    static final int DMN_FORMAT_VERSION = 1;
    static final int LLMQ_FORMAT_VERSION = 2;

    public static final int SNAPSHOT_LIST_PERIOD = 576; // once per day
    public static final int LISTS_CACHE_SIZE = 576;
    public static final int SNAPSHOT_TIME_PERIOD = 60 * 60 * 30;

    public static final int MAX_CACHE_SIZE = 10;

    SimplifiedMasternodeList mnList;
    SimplifiedQuorumList quorumList;
    long tipHeight;
    Sha256Hash tipBlockHash;

    AbstractBlockChain blockChain;

    Sha256Hash lastRequestHash = Sha256Hash.ZERO_HASH;
    GetSimplifiedMasternodeListDiff lastRequestMessage;
    long lastRequestTime;
    static final long WAIT_GETMNLISTDIFF = 5000;
    Peer downloadPeer;
    boolean waitingForMNListDiff;
    LinkedHashMap<Sha256Hash, StoredBlock> pendingBlocksMap;
    ArrayList<StoredBlock> pendingBlocks;

    int failedAttempts;
    static final int MAX_ATTEMPTS = 10;

    PeerGroup peerGroup;

    public SimplifiedMasternodeListManager(Context context) {
        super(context);
        tipBlockHash = Sha256Hash.ZERO_HASH;
        mnList = new SimplifiedMasternodeList(context.getParams());
        quorumList = new SimplifiedQuorumList(context.getParams());
        lastRequestTime = 0;
        waitingForMNListDiff = false;
        pendingBlocks = new ArrayList<StoredBlock>();
        pendingBlocksMap = new LinkedHashMap<Sha256Hash, StoredBlock>();
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
        } else {
            quorumList = new SimplifiedQuorumList(params);
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        lock.lock();
        try {
            mnList.bitcoinSerialize(stream);
            stream.write(mnList.getBlockHash().getReversedBytes());
            Utils.uint32ToByteStreamLE(mnList.getHeight(), stream);
            if(getFormatVersion() >= 2)
                quorumList.bitcoinSerialize(stream);
        } finally {
            lock.unlock();
        }
    }

    public void updatedBlockTip(StoredBlock tip) {
    }

    public void processMasternodeListDiff(SimplifiedMasternodeListDiff mnlistdiff) {
        StoredBlock block = null;
        long newHeight = ((CoinbaseTx) mnlistdiff.coinBaseTx.getExtraPayloadObject()).getHeight();
        log.info("processing mnlistdiff between : " + mnList.getHeight() + " & " + newHeight + "; " + mnlistdiff);
        lock.lock();
        try {
            if(!params.getId().equals(NetworkParameters.ID_UNITTESTNET)) {
                block = blockChain.getBlockStore().get(mnlistdiff.blockHash);
                if(block.getHeight() != newHeight)
                    throw new ProtocolException("mnlistdiff blockhash (height="+block.getHeight()+" doesn't match coinbase blockheight: " + newHeight);
            }
            SimplifiedMasternodeList newMNList = mnList.applyDiff(mnlistdiff);
            newMNList.verify(mnlistdiff.coinBaseTx);
            SimplifiedQuorumList newQuorumList = quorumList;
            if(mnlistdiff.coinBaseTx.getExtraPayloadObject().getVersion() >= 2) {
                newQuorumList = quorumList.applyDiff(mnlistdiff);
                newQuorumList.verify(mnlistdiff.coinBaseTx, newMNList);
            } else {
                quorumList.syncWithMasternodeList(newMNList);
            }
            mnList = newMNList;
            quorumList = newQuorumList;
            log.info(this.toString());
            unCache();
            failedAttempts = 0;

            StoredBlock thisBlock = pendingBlocks.get(0);
            pendingBlocks.remove(0);
            pendingBlocksMap.remove(thisBlock.getHeader().getHash());

            if(mnlistdiff.hasChanges()) {
                if(mnlistdiff.coinBaseTx.getExtraPayloadObject().getVersion() >= 2 && quorumList.size() > 0)
                    setFormatVersion(LLMQ_FORMAT_VERSION);
                save();
            }
        } catch(MasternodeListDiffException x) {
            //we already have this mnlistdiff or doesn't match our current tipBlockHash
            if(mnList.getBlockHash().equals(mnlistdiff.blockHash)) {
                log.info("heights are the same:  " + x.getMessage());
                log.info("mnList = {} vs mnlistdiff {}", mnList.getBlockHash(), mnlistdiff.prevBlockHash);
                log.info("mnlistdiff {} -> {}", mnlistdiff.prevBlockHash, mnlistdiff.blockHash);
                log.info("lastRequest {} -> {}", lastRequestMessage.baseBlockHash, lastRequestMessage.blockHash);
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
            //file name is not set, do not save
            log.info(x.getMessage());
        } catch(BlockStoreException x) {
            log.info(x.getMessage());
            failedAttempts++;
            throw new ProtocolException(x);
        } finally {
            waitingForMNListDiff = false;
            requestNextMNListDiff();
            lock.unlock();
        }
    }

    public NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            if(isDeterministicMNsSporkActive()) {
                if (Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < SNAPSHOT_TIME_PERIOD)
                    requestMNListDiff(block);
            }
        }
    };

    public PeerConnectedEventListener peerConnectedEventListener = new PeerConnectedEventListener() {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            if(isDeterministicMNsSporkActive()) {
                if(downloadPeer == null)
                    downloadPeer = peer;
                maybeGetMNListDiffFresh();
                if (mnList.getBlockHash().equals(Sha256Hash.ZERO_HASH) || mnList.getHeight() < blockChain.getBestChainHeight()) {
                    if(Utils.currentTimeSeconds() - blockChain.getChainHead().getHeader().getTimeSeconds() < SNAPSHOT_TIME_PERIOD)
                        requestMNListDiff(peer, blockChain.getChainHead());
                }
            }
        }
    };

    PeerDisconnectedEventListener peerDisconnectedEventListener = new PeerDisconnectedEventListener() {
        @Override
        public void onPeerDisconnected(Peer peer, int peerCount) {
            if(downloadPeer == peer) {
                downloadPeer = null;
                chooseRandomDownloadPeer();
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
                maybeGetMNListDiffFresh();
            } finally {
                lock.unlock();
            }

        }
    };

    void maybeGetMNListDiffFresh() {
        if(pendingBlocks.size() > 0 || lastRequestTime + WAIT_GETMNLISTDIFF > Utils.currentTimeMillis() ||
                blockChain.getChainHead().getHeader().getTimeSeconds() < Utils.currentTimeSeconds() - SNAPSHOT_TIME_PERIOD)
            return;

        //Should we reset our masternode/quorum list
        if(mnList.size() == 0 || mnList.getBlockHash().equals(Sha256Hash.ZERO_HASH)) {
            mnList = new SimplifiedMasternodeList(params);
            quorumList = new SimplifiedQuorumList(params);
        } else {
            if(mnList.getBlockHash().equals(blockChain.getChainHead().getHeader().getHash()))
                return;
        }

        if(downloadPeer == null)
        {
            downloadPeer = context.peerGroup.getDownloadPeer();
        }

        StoredBlock block = blockChain.getChainHead();
        log.info("maybe requesting mnlistdiff from {} to {}; \n  From {}\n  To {}", mnList.getHeight(), block.getHeight(), mnList.getBlockHash(), block.getHeader().getHash());

        lastRequestMessage = new GetSimplifiedMasternodeListDiff(mnList.getBlockHash(), blockChain.getChainHead().getHeader().getHash());
        downloadPeer.sendMessage(lastRequestMessage);
        lastRequestHash = mnList.getBlockHash();
        lastRequestTime = Utils.currentTimeMillis();
        waitingForMNListDiff = true;
    }

    void requestNextMNListDiff() {
        lock.lock();
        try {
            log.info("handling next mnlistdiff: " + pendingBlocks.size());
            if(pendingBlocks.size() == 0)
                return;

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
                    log.info("requesting mnlistdiff from {} to {}; \n  From {}\n To {}", mnList.getHeight(), nextBlock.getHeight(), mnList.getBlockHash(), nextBlock.getHeader().getHash());
                    lastRequestMessage = new GetSimplifiedMasternodeListDiff(mnList.getBlockHash(), nextBlock.getHeader().getHash());
                    downloadPeer.sendMessage(lastRequestMessage);
                    waitingForMNListDiff = true;
                }
            }
        } finally {
            lock.unlock();
        }

    }

    public void setBlockChain(AbstractBlockChain blockChain, PeerGroup peerGroup) {
        this.blockChain = blockChain;
        this.peerGroup = peerGroup;
        blockChain.addNewBestBlockListener(Threading.SAME_THREAD, newBestBlockListener);
        peerGroup.addConnectedEventListener(peerConnectedEventListener);
        peerGroup.addChainDownloadStartedEventListener(chainDownloadStartedEventListener);
        peerGroup.addDisconnectedEventListener(peerDisconnectedEventListener);
    }

    @Override
    public void close() {
        blockChain.removeNewBestBlockListener(newBestBlockListener);
        peerGroup.removeConnectedEventListener(peerConnectedEventListener);
        peerGroup.removeChainDownloadStartedEventListener(chainDownloadStartedEventListener);
        peerGroup.removeDisconnectedEventListener(peerDisconnectedEventListener);
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
        return "SimplifiedMNListManager:  {" + mnList + ", tipHeight: "+ mnList.getHeight() + " " + quorumList +" height:" + quorumList.getHeight() + "}";
    }

    public long getSpork15Value() {
        return context.sporkManager.getSporkValue(SporkManager.SPORK_15_DETERMINISTIC_MNS_ENABLED);
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
        resetMNList(false);
    }

    public void resetMNList(boolean force) {
        if(force || getFormatVersion() < LLMQ_FORMAT_VERSION) {
            log.info("resetting masternode list");
            mnList = new SimplifiedMasternodeList(context.getParams());
            quorumList = new SimplifiedQuorumList(context.getParams());
            pendingBlocks.clear();
            pendingBlocksMap.clear();
            requestMNListDiff(blockChain.getChainHead());
        }
    }
}
