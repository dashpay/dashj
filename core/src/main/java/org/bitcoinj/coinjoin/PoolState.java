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

public enum PoolState {
    POOL_STATE_IDLE(0),
    POOL_STATE_QUEUE(1),
    POOL_STATE_ACCEPTING_ENTRIES(2),
    POOL_STATE_SIGNING(3),
    POOL_STATE_ERROR(4),
    POOL_STATE_MIN(POOL_STATE_IDLE.value),
    POOL_STATE_MAX(POOL_STATE_ERROR.value);

    public final int value;

    PoolState(int value) {
        this.value = value;
    }

    public static PoolState fromValue(int value) {
        for (PoolState e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return POOL_STATE_IDLE;
    }
}
