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
 * The "getheaders2" command requests compressed block headers (DIP-0025).
 * It is structurally identical to "getheaders" but results in a "headers2" response
 * containing compressed headers.
 *
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 *
 * @see <a href="https://github.com/dashpay/dips/blob/master/dip-0025.md">DIP-0025</a>
 */
public class GetHeaders2Message extends GetBlocksMessage {

    public GetHeaders2Message(NetworkParameters params, BlockLocator locator, Sha256Hash stopHash) {
        super(params, locator, stopHash);
    }

    public GetHeaders2Message(NetworkParameters params, byte[] payload) throws ProtocolException {
        super(params, payload);
    }

    @Override
    public String toString() {
        return "getheaders2: " + locator.toString();
    }

    /**
     * Compares two getheaders2 messages. Note that even though they are structurally identical,
     * a GetHeaders2Message will not compare equal to a GetHeadersMessage or GetBlocksMessage
     * containing the same data.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GetHeaders2Message other = (GetHeaders2Message) o;
        return version == other.version && stopHash.equals(other.stopHash) &&
                locator.size() == other.locator.size() && locator.equals(other.locator);
    }

    @Override
    public int hashCode() {
        int hashCode = (int) version ^ "getheaders2".hashCode() ^ stopHash.hashCode();
        return hashCode ^= locator.hashCode();
    }
}
