/*
 * Copyright (c) 2022 Dash Core Group
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
package org.bitcoinj.coinjoin.utils;

import org.bitcoinj.coinjoin.PoolMessage;

/**
 * Allows a method to return a boolean success value and a message
 */
public class CoinJoinResult {
    boolean success;
    PoolMessage messageId;

    private CoinJoinResult(boolean success, PoolMessage messageId) {
        this.success = success;
        this.messageId = messageId;
    }

    public static CoinJoinResult success() {
        return new CoinJoinResult(true, PoolMessage.MSG_SUCCESS);
    }

    public static CoinJoinResult success(PoolMessage messageId) {
        return new CoinJoinResult(true, messageId);
    }

    public static CoinJoinResult fail(PoolMessage messageId) {
        return new CoinJoinResult(false, messageId);
    }
    public static CoinJoinResult fail() {
        return new CoinJoinResult(false, PoolMessage.MSG_NOERR);
    }

    public String getMessage() {
        return messageId.name();
    }

    public PoolMessage getMessageId() {
        return messageId;
    }

    public boolean isSuccess() {
        return success;
    }
}
