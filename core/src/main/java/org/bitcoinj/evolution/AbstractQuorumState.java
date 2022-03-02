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

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.utils.Threading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The abstract base class of all Quorum State classes
 * @param <T> A class derived from AbstractQuorumRequest used as the message to request updates
 *           to the masternode and quorum lists
 */

public abstract class AbstractQuorumState<T extends AbstractQuorumRequest> extends Message {

    protected ReentrantLock lock = Threading.lock("AbstractQuorumState");

    Context context;
    AbstractBlockChain blockChain;
    BlockStore blockStore;

    QuorumUpdateRequest<T> lastRequest;

    public AbstractQuorumState(Context context) {
        super(context.getParams());
        this.context = context;
    }

    public AbstractQuorumState(NetworkParameters params, byte[] payload, int offset) {
        super(params, payload, offset);
    }

    public void setBlockChain(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        blockStore = blockChain.getBlockStore();
    }

    public abstract void requestReset(Peer peer, StoredBlock block);

    public abstract void requestUpdate(Peer peer, StoredBlock block);

    public abstract SimplifiedMasternodeList getListForBlock(Sha256Hash blockHash);

    public abstract LinkedHashMap<Sha256Hash, SimplifiedMasternodeList> getMasternodeListCache();

    public abstract LinkedHashMap<Sha256Hash, SimplifiedQuorumList> getQuorumsCache();

    public abstract ArrayList<Masternode> getAllQuorumMembers(LLMQParameters.LLMQType llmqType, Sha256Hash blockHash);
}