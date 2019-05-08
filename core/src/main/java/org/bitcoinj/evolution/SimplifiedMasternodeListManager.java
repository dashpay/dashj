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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

public class SimplifiedMasternodeListManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(MasternodeManager.class);
    private ReentrantLock lock = Threading.lock("SimplifiedMasternodeListManager");

    static final int DMN_FORMAT_VERSION = 1;
    static final int LLMQ_FORMAT_VERSION = 2;

    public static final int SNAPSHOT_LIST_PERIOD = 576; // once per day
    public static final int LISTS_CACHE_SIZE = 576;
    public static final int SNAPSHOT_TIME_PERIOD = 60 * 60 * 30;

    HashMap<Sha256Hash, SimplifiedMasternodeList> mnListsCache;
    SimplifiedMasternodeList mnList;
    SimplifiedQuorumList quorumList;
    long tipHeight;
    Sha256Hash tipBlockHash;

    AbstractBlockChain blockChain;

    Sha256Hash lastRequestHash = Sha256Hash.ZERO_HASH;
    int lastRequestCount;
    long lastRequestTime;
    static final long WAIT_GETMNLISTDIFF = 5000;
    Peer downloadPeer;
    boolean waitingForMNListDiff;
    LinkedHashMap<Sha256Hash, StoredBlock> pendingBlocksMap;
    ArrayList<StoredBlock> pendingBlocks;

    public SimplifiedMasternodeListManager(Context context) {
        super(context);
        tipBlockHash = Sha256Hash.ZERO_HASH;
        mnList = new SimplifiedMasternodeList(context.getParams());
        quorumList = new SimplifiedQuorumList(context.getParams());
        lastRequestTime = 0;
        waitingForMNListDiff = true;
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
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        lock.lock();
        try {
            mnList.bitcoinSerialize(stream);
            stream.write(tipBlockHash.getReversedBytes());
            Utils.uint32ToByteStreamLE(tipHeight, stream);
            if(getFormatVersion() >= 2)
                quorumList.bitcoinSerialize(stream);
        } finally {
            lock.unlock();
        }
    }

    public void updatedBlockTip(StoredBlock tip) {
    }

    public void processMasternodeListDiff(SimplifiedMasternodeListDiff mnlistdiff) {
        long newHeight = ((CoinbaseTx) mnlistdiff.coinBaseTx.getExtraPayloadObject()).getHeight();
        log.info("processing mnlistdiff between : " + tipHeight + " & " + newHeight + "; " + mnlistdiff);
        lock.lock();
        try {
            if(!params.getId().equals(NetworkParameters.ID_UNITTESTNET)) {
                StoredBlock block = blockChain.getBlockStore().get(mnlistdiff.blockHash);
                if(block.getHeight() != newHeight)
                    throw new ProtocolException("mnlistdiff blockhash (height="+block.getHeight()+" doesn't match coinbase blockheight: " + newHeight);
            }
            SimplifiedMasternodeList newMNList = mnList.applyDiff(mnlistdiff);
            newMNList.verify(mnlistdiff.coinBaseTx);
            SimplifiedQuorumList newQuorumList = null;
            if(mnlistdiff.coinBaseTx.getExtraPayloadObject().getVersion() >= 2) {
                newQuorumList = quorumList.applyDiff(mnlistdiff);
                newQuorumList.verify(mnlistdiff.coinBaseTx, newMNList);
            }
            mnList = newMNList;
            quorumList = newQuorumList;
            tipHeight = newHeight;
            tipBlockHash = mnlistdiff.blockHash;
            log.info(this.toString());
            unCache();
            if(mnlistdiff.hasChanges()) {
                if(mnlistdiff.coinBaseTx.getExtraPayloadObject().getVersion() >= 2 && quorumList.size() > 0)
                    setFormatVersion(LLMQ_FORMAT_VERSION);
                save();
            }
        } catch(IllegalArgumentException x) {
            //we already have this mnlistdiff or doesn't match our current tipBlockHash
            log.info(x.getMessage());
        } catch(NullPointerException x) {
            //file name is not set, do not save
            log.info(x.getMessage());
        } catch(BlockStoreException x) {
            log.info(x.getMessage());
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
                if (Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < 60 * 60 * 25)
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
                if (tipBlockHash.equals(Sha256Hash.ZERO_HASH) || tipHeight < blockChain.getBestChainHeight()) {
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
                List<Peer> peers = context.peerGroup.getConnectedPeers();
                if(peers != null && !peers.isEmpty()) {
                    downloadPeer = peers.get(new Random().nextInt(peers.size()));
                }
            }
        }
    };

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
        if(lastRequestTime + WAIT_GETMNLISTDIFF > Utils.currentTimeMillis())
            return;

        if(mnList.size() == 0 || tipBlockHash.equals(Sha256Hash.ZERO_HASH) ||
                tipHeight < blockChain.getChainHead().getHeight() - 3000) {
            mnList = new SimplifiedMasternodeList(params);
            tipHeight = -1;
            tipBlockHash = Sha256Hash.ZERO_HASH;
        } else {
            if(tipBlockHash.equals(blockChain.getChainHead().getHeader().getHash()))
                return;
        }

        if(downloadPeer != null ) {
            downloadPeer.sendMessage(new GetSimplifiedMasternodeListDiff(tipBlockHash, blockChain.getChainHead().getHeader().getHash()));
            lastRequestHash = tipBlockHash;
            lastRequestTime = Utils.currentTimeMillis();
            waitingForMNListDiff = true;
        }
    }

    void requestNextMNListDiff() {
        lock.lock();
        try {
            log.info("handling next mnlistdiff: " + pendingBlocks.size());
            if(pendingBlocks.size() == 0)
                return;
            if(downloadPeer != null) {
                int count = 0;
                for(int i = 0; i < pendingBlocks.size(); ++i) {
                    if(pendingBlocks.get(i).getHeight() <= tipHeight) {
                        count++;
                        log.info("ignoring requests that we have" + count);
                        continue;
                    }
                    break;
                }
                if(count < pendingBlocks.size()) {
                    StoredBlock nextBlock = pendingBlocks.get(count);
                    log.info("requesting mnlistdiff from {} to {}", tipHeight, nextBlock.getHeight());
                    downloadPeer.sendMessage(new GetSimplifiedMasternodeListDiff(tipBlockHash, nextBlock.getHeader().getHash()));
                    waitingForMNListDiff = true;
                }

                if(count == 0) {
                    log.info("removing {} blocks from the pending queue", 1);
                    StoredBlock block = pendingBlocks.get(0);
                    pendingBlocksMap.remove(block);
                    pendingBlocks.remove(0);
                } else {
                    log.info("removing {} blocks from the pending queue", count);
                    for (int i = count - 1; i >= 0; --i) {
                        StoredBlock block = pendingBlocks.get(i);
                        pendingBlocksMap.remove(block);
                        pendingBlocks.remove(i);
                    }
                }
            }
        } finally {
            lock.unlock();
        }

    }

    public void setBlockChain(AbstractBlockChain blockChain, PeerGroup peerGroup) {
        this.blockChain = blockChain;
        blockChain.addNewBestBlockListener(Threading.SAME_THREAD, newBestBlockListener);
        peerGroup.addConnectedEventListener(peerConnectedEventListener);
        peerGroup.addChainDownloadStartedEventListener(chainDownloadStartedEventListener);
        peerGroup.addDisconnectedEventListener(peerDisconnectedEventListener);
    }

    public void requestMNListDiff(StoredBlock block) {
        /*Peer peer = context.peerGroup.getDownloadPeer();
        if(peer == null) {
            List<Peer> peers = context.peerGroup.getConnectedPeers();
            peer = peers.get(new Random().nextInt(peers.size()));
        }
        if(peer != null)*/
        requestMNListDiff(/*peer*/null, block);
    }

    public void requestMNListDiff(Peer peer, StoredBlock block) {
        Sha256Hash hash = block.getHeader().getHash();
        //log.info("getmnlistdiff:  current block:  " + tipHeight + " requested block " + block.getHeight());

        //if(waitingForMNListDiff) {
        log.info("adding 1 block to the pending queue: {} - {}", block.getHeight(), block.getHeader().getHash());
        if(pendingBlocksMap.put(hash, block) == null)
            pendingBlocks.add(block);
        //    return;
        //} else
        //
        if(!waitingForMNListDiff)
            requestNextMNListDiff();



        /*lock.lock();
        try {
            //If we are requesting the block we have already, then skip the request
            if (hash.equals(tipBlockHash) && !hash.equals(Sha256Hash.ZERO_HASH))
                return;

            if (lastRequestHash.equals(tipBlockHash)) {
                lastRequestCount++;
                if (lastRequestCount > 24) {
                    lastRequestCount = 0;
                    tipBlockHash = Sha256Hash.ZERO_HASH;
                    tipHeight = 0;
                    mnList = new SimplifiedMasternodeList(params);
                }
                log.info("Requesting the same mnlistdiff " + lastRequestCount + " times");
                if (lastRequestCount > 1) {
                    log.info("Stopping at 1 times to wait for a reply");
                    return;
                }
            } else {
                lastRequestCount = 0;
            }
            peer.sendMessage(new GetSimplifiedMasternodeListDiff(tipBlockHash, hash));
            lastRequestHash = tipBlockHash;
            lastRequestTime = Utils.currentTimeMillis();
        } finally {
            lock.unlock();
        }*/
    }

    public void updateMNList() {
        requestMNListDiff(context.blockChain.getChainHead());
    }

    @Override
    public String toString() {
        return "SimplifiedMNListManager:  {" + mnList + ", tipHeight "+ tipHeight +"}";
    }

    public long getSpork15Value() {
        return context.sporkManager.getSporkValue(SporkManager.SPORK_15_DETERMINISTIC_MNS_ENABLED);
    }

    public boolean isDeterministicMNsSporkActive(long height) {
        if(height == -1) {
            height = tipHeight;
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
        if(getFormatVersion() < LLMQ_FORMAT_VERSION) {
            tipHeight = -1;
            tipBlockHash = Sha256Hash.ZERO_HASH;
            mnList = new SimplifiedMasternodeList(context.getParams());
            requestMNListDiff(blockChain.getChainHead());
        }
    }
}
