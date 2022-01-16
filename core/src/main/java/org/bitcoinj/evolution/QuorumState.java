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

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.evolution.listeners.MasternodeListDownloadedListener;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.LLMQUtils;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class QuorumState extends Message {
    private static final Logger log = LoggerFactory.getLogger(QuorumState.class);
    Context context;
    NetworkParameters params;
    AbstractBlockChain blockChain;
    BlockStore blockStore;

    SimplifiedMasternodeList mnList;
    SimplifiedQuorumList quorumList;
    SimplifiedMasternodeListManager.SyncOptions syncOptions;

    LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> mnListsCache = new LinkedHashMap<Sha256Hash, SimplifiedMasternodeList>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, SimplifiedMasternodeList> eldest) {
            return size() > (syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM ? SimplifiedMasternodeListManager.MIN_CACHE_SIZE : SimplifiedMasternodeListManager.MAX_CACHE_SIZE);
        }
    };

    LinkedHashMap<Sha256Hash, SimplifiedQuorumList> quorumsCache = new LinkedHashMap<Sha256Hash, SimplifiedQuorumList>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, SimplifiedQuorumList> eldest) {
            return size() > (syncOptions == SimplifiedMasternodeListManager.SyncOptions.SYNC_MINIMUM ? SimplifiedMasternodeListManager.MIN_CACHE_SIZE : SimplifiedMasternodeListManager.MAX_CACHE_SIZE);
        }
    };
    public QuorumState(Context context, SimplifiedMasternodeListManager.SyncOptions syncOptions) {
        this.context = context;
        params = context.getParams();
        mnList = new SimplifiedMasternodeList(context.getParams());
        quorumList = new SimplifiedQuorumList(context.getParams());
        this.syncOptions = syncOptions;
    }

    public QuorumState(Context context, SimplifiedMasternodeListManager.SyncOptions syncOptions, byte [] payload, int offset) {
        super(context.getParams(), payload, offset);
        this.context = context;
        this.syncOptions = syncOptions;
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        blockStore = blockChain.getBlockStore();
    }

    public void applyDiff(Peer peer, AbstractBlockChain headersChain, AbstractBlockChain blockChain,
                          SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap)
            throws BlockStoreException, MasternodeListDiffException {
        StoredBlock block;
        boolean isSyncingHeadersFirst = context.peerGroup.getSyncStage() == PeerGroup.SyncStage.MNLIST;
        AbstractBlockChain chain = isSyncingHeadersFirst ? headersChain : blockChain;

        long newHeight = ((CoinbaseTx) mnlistdiff.coinBaseTx.getExtraPayloadObject()).getHeight();
        block = chain.getBlockStore().get(mnlistdiff.blockHash);
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
            return allMns.calculateQuorum(llmqParameters.getSize(), modifier);
        }
        return null;
    }


    public SimplifiedMasternodeList getListForBlock(Sha256Hash blockHash) {
        return mnListsCache.get(blockHash);
    }

    @Override
    protected void parse() throws ProtocolException {
        mnList = new SimplifiedMasternodeList(params, payload, cursor);
        cursor += mnList.getMessageSize();
        length = cursor - offset;
    }

    public void parseQuorums(byte [] payload, int offset) {
        quorumList = new SimplifiedQuorumList(params, payload, offset);
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        mnList.bitcoinSerialize(stream);
    }

    public void serializeQuorumsToStream(OutputStream stream) throws IOException {
        quorumList.bitcoinSerialize(stream);
    }

    public LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> getMasternodeListCache() {
        return mnListsCache;
    }

    public LinkedHashMap<Sha256Hash, SimplifiedQuorumList> getQuorumsCache() {
        return quorumsCache;
    }
}
