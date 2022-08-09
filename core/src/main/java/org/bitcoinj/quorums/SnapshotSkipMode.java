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

package org.bitcoinj.quorums;

public enum SnapshotSkipMode {
    MODE_NO_SKIPPING(0),
    MODE_SKIPPING_ENTRIES(1),
    MODE_NO_SKIPPING_ENTRIES(2),
    MODE_ALL_SKIPPED(3),
    MODE_INVALID(-1);

    private int value;

    SnapshotSkipMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static SnapshotSkipMode fromValue(int value) {
        for (SnapshotSkipMode e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return MODE_INVALID;
    }
}
