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
package org.bitcoinj.coinjoin;

public enum PoolMessage {
    ERR_ALREADY_HAVE(0),
    ERR_DENOM(1),
    ERR_ENTRIES_FULL(2),
    ERR_EXISTING_TX(3),
    ERR_FEES(4),
    ERR_INVALID_COLLATERAL(5),
    ERR_INVALID_INPUT(6),
    ERR_INVALID_SCRIPT(7),
    ERR_INVALID_TX(8),
    ERR_MAXIMUM(9),
    ERR_MN_LIST(10),
    ERR_MODE(11),
    ERR_QUEUE_FULL(14),
    ERR_RECENT(15),
    ERR_SESSION(16),
    ERR_MISSING_TX(17),
    ERR_VERSION(18),
    MSG_NOERR(19),
    MSG_SUCCESS(20),
    MSG_ENTRIES_ADDED(21),
    ERR_SIZE_MISMATCH(22),
    MSG_POOL_MIN(ERR_ALREADY_HAVE.value),
    MSG_POOL_MAX(ERR_SIZE_MISMATCH.value),

    // extra values for DASHJ Reporting
    ERR_TIMEOUT(23);

    public final int value;

    PoolMessage(int value) {
        this.value = value;
    }

    public static PoolMessage fromValue(int value) {
        for (PoolMessage e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return MSG_NOERR;
    }
}
