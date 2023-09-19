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
import org.bitcoinj.core.DualBlockChain;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.Utils;

/**
 * A utility class that keeps track of a request message and a timestamp
 *
 * @param <T> A message class derived from {@link AbstractQuorumRequest}
 */

public class QuorumUpdateRequest<T extends AbstractQuorumRequest> {
    T request;
    long time;

    private PeerAddress peerAddress;
    public QuorumUpdateRequest(T request) {
        this(request, Utils.currentTimeSeconds(), null);
    }

    public QuorumUpdateRequest(T request, PeerAddress peerAddress) {
        this(request, Utils.currentTimeSeconds(), peerAddress);
    }

    public QuorumUpdateRequest(T request, long time, PeerAddress peerAddress) {
        this.request = request;
        this.time = time;
        this.peerAddress = peerAddress;
    }

    public T getRequestMessage() {
        return request;
    }

    public long getTime() {
        return time;
    }

    public PeerAddress getPeerAddress() {
        return peerAddress;
    }

    public void setPeerAddress(PeerAddress peerAddress) {
        this.peerAddress = peerAddress;
    }

    @Override
    public String toString() {
        return "QuorumUpdateRequest{" +
                "request=" + request +
                ", time=" + time +
                '}';
    }

    public String toString(DualBlockChain blockChain) {
        return "QuorumUpdateRequest{" +
                "request=" + request.toString(blockChain) +
                ", time=" + time +
                '}';
    }
}
