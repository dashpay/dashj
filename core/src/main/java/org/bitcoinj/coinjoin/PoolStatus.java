/*
 * Copyright (c) 2023 Dash Core Group
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

public enum PoolStatus {
    WARMUP(0x0001),
    IDLE(0x0002),
    CONNECTING(0x0003),
    MIXING(0x0004),
    FINISHED(0x1005),
    // Errors
    ERR_NO_INPUTS(0x2100),
    ERR_MASTERNODE_NOT_FOUND(0x2101),
    ERR_NO_MASTERNODES_DETECTED(0x2102),
    ERR_WALLET_LOCKED(0x3103),
    ERR_NOT_ENOUGH_FUNDS(0x2104),
    // Warnings
    WARN_NO_MIXING_QUEUES(0x2200),
    WARN_NO_COMPATIBLE_MASTERNODE(0x2201);

    private static final int STOP = 0x1000;
    private static final int ERROR = 0x2000;
    private static final int WARNING = 0x4000;

    final int value;
    PoolStatus(int value) {
        this.value = value;
    }

    public boolean isError() { return (value & ERROR) != 0; }
    public boolean isWarning() { return (value & WARNING) != 0; }
    public boolean shouldStop() { return (value & STOP) != 0; }
}
