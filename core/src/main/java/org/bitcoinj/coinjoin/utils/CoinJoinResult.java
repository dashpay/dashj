package org.bitcoinj.coinjoin.utils;

import org.bitcoinj.coinjoin.PoolMessage;

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
