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

package org.bitcoinj.core;

public enum SporkId {
    SPORK_2_INSTANTSEND_ENABLED(10001),
    SPORK_3_INSTANTSEND_BLOCK_FILTERING(10002),
    SPORK_9_SUPERBLOCKS_ENABLED(10008),
    SPORK_17_QUORUM_DKG_ENABLED(10016),
    SPORK_19_CHAINLOCKS_ENABLED(10018),
    SPORK_21_QUORUM_ALL_CONNECTED(10020),
    SPORK_23_QUORUM_POSE(10022),
    SPORK_INVALID(-1);

    public final int value;

    SporkId(int value) {
        this.value = value;
    }

    public static SporkId fromValue(int value) {
        for (SporkId e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return SPORK_INVALID;
    }
}
