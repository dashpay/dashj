/*
 * Copyright 2026 Dash Core Group
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

package org.bitcoinj.core;

/**
 * The "sendheaders2" message indicates that a node prefers to receive new block
 * announcements via compressed "headers2" messages (DIP-0025) rather than "inv"
 * or uncompressed "headers" messages.
 *
 * <p>This is an empty message with no payload, similar to {@link SendHeadersMessage}.</p>
 *
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 *
 * @see <a href="https://github.com/dashpay/dips/blob/master/dip-0025.md">DIP-0025</a>
 */
public class SendHeaders2Message extends EmptyMessage {

    public SendHeaders2Message() {
        super();
    }

    /**
     * Constructor required by BitcoinSerializer for deserialization.
     *
     * @param params the network parameters
     * @param payload the message payload (empty for this message type)
     */
    public SendHeaders2Message(NetworkParameters params, byte[] payload) {
        super(params);
    }
}
