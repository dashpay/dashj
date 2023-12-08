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

import org.bitcoinj.core.DualBlockChain;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;

/**
 * The abstract base class for messages that are for requesting masternode list and quorum list updates
 *
 * This class requires that subclasses implement {@link #toString(DualBlockChain)}
 */

public abstract class AbstractQuorumRequest extends Message {

    public AbstractQuorumRequest() {
        super();
    }

    public AbstractQuorumRequest(NetworkParameters params) {
        super(params);
    }

    public AbstractQuorumRequest(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    /**
     *
     * @param blockChain the blockChain that will convert block hashes to heights
     * @return the string representation of this object with block heights next to all block hashes
     */
    public abstract String toString(DualBlockChain blockChain);
}
