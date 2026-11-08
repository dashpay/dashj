/*
 * Copyright 2026 Dash Core Group.
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

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

/**
 * One participant's contribution to the collateral of a shared masternode, as registered by a
 * version 3+ ProRegTx (Dash Core {@code CCollateralShare}).
 */
public final class CollateralShare {
    private final Coin amount;
    private final Script scriptRefund;
    private final Script scriptReward;
    private final KeyId keyIdOwner;

    public CollateralShare(Coin amount, Script scriptRefund, Script scriptReward, KeyId keyIdOwner) {
        this.amount = amount;
        this.scriptRefund = scriptRefund;
        this.scriptReward = scriptReward;
        this.keyIdOwner = keyIdOwner;
    }

    public Coin getAmount() {
        return amount;
    }

    public Script getScriptRefund() {
        return scriptRefund;
    }

    /** the reward script as registered, which may be empty */
    public Script getScriptReward() {
        return scriptReward;
    }

    /** the script this share's owner rewards are paid to; an empty reward script means the refund script */
    public Script getRewardScript() {
        return scriptReward.getProgram().length == 0 ? scriptRefund : scriptReward;
    }

    public KeyId getKeyIdOwner() {
        return keyIdOwner;
    }

    void serialize(OutputStream stream) throws IOException {
        Utils.int64ToByteStreamLE(amount.value, stream);
        Utils.bytesToByteStream(scriptRefund.getProgram(), stream);
        Utils.bytesToByteStream(scriptReward.getProgram(), stream);
        keyIdOwner.bitcoinSerialize(stream);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollateralShare that = (CollateralShare) o;
        return amount.equals(that.amount) && scriptRefund.equals(that.scriptRefund) &&
                scriptReward.equals(that.scriptReward) && keyIdOwner.equals(that.keyIdOwner);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * amount.hashCode() + scriptRefund.hashCode()) + scriptReward.hashCode()) +
                keyIdOwner.hashCode();
    }

    @Override
    public String toString() {
        return "CollateralShare(amount=" + amount.toFriendlyString() + ", refund=" + scriptRefund +
                ", reward=" + scriptReward + ", owner=" + keyIdOwner + ")";
    }
}
