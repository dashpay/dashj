package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

public class SimplifiedMasternodeListManager extends AbstractManager {
    private static final Logger log = LoggerFactory.getLogger(MasternodeManager.class);


    public static final int SNAPSHOT_LIST_PERIOD = 576; // once per day
    public static final int LISTS_CACHE_SIZE = 576;

    HashMap<Sha256Hash, SimplifiedMasternodeList> mnListsCache;
    SimplifiedMasternodeList mnList;
    long tipHeight;
    Sha256Hash tipBlockHash;

    AbstractBlockChain blockChain;

    public SimplifiedMasternodeListManager(Context context) {
        super(context);
        tipBlockHash = Sha256Hash.ZERO_HASH;
        mnList = new SimplifiedMasternodeList(context.getParams());
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
        int size = (int)readVarInt();
        mnList = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnList.getMessageSize();
        tipBlockHash = readHash();
        tipHeight = readUint32();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        mnList.bitcoinSerialize(stream);
        stream.write(tipBlockHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(tipHeight, stream);
    }

    public void updatedBlockTip(StoredBlock tip) {
    }

    public void processMasternodeListDiff(SimplifiedMasternodeListDiff mnlistdiff) {
        log.info("processing mnlistdiff: " + mnlistdiff);
        SimplifiedMasternodeList newMNList = mnList.applyDiff(mnlistdiff);
        newMNList.verify(mnlistdiff.coinBaseTx);
        mnList = newMNList;
        tipHeight = ((CoinbaseTx)mnlistdiff.coinBaseTx.getExtraPayloadObject()).getHeight();
        tipBlockHash = mnlistdiff.blockHash;
        log.info(this.toString());
    }

    public NewBestBlockListener newBestBlockListener = new NewBestBlockListener() {
        @Override
        public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
            if(Utils.currentTimeSeconds() - block.getHeader().getTimeSeconds() < 60 * 60)
                requestMNListDiff(block);
        }
    };

    public PeerConnectedEventListener peerConnectedEventListener = new PeerConnectedEventListener() {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            if(tipBlockHash.equals(Sha256Hash.ZERO_HASH)) {
                if(Utils.currentTimeSeconds() - blockChain.getChainHead().getHeader().getTimeSeconds() < 60 * 60)
                    peer.sendMessage(new GetSimplifiedMasternodeListDiff(tipBlockHash, blockChain.getChainHead().getHeader().getHash()));
            }
        }
    };

    public void setBlockChain(AbstractBlockChain blockChain, PeerGroup peerGroup) {
        this.blockChain = blockChain;
        blockChain.addNewBestBlockListener(newBestBlockListener);
        peerGroup.addConnectedEventListener(peerConnectedEventListener);
    }

    public void requestMNListDiff(StoredBlock block) {
        context.peerGroup.getDownloadPeer().sendMessage(new GetSimplifiedMasternodeListDiff(tipBlockHash, block.getHeader().getHash()));
    }

    @Override
    public String toString() {
        return "SimplifiedMNListManager:  {" + mnList + ", tipHeight "+ tipHeight +"}";
    }
}
