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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * One owner payout of a version 3+ ProRegTx or ProUpRegTx: a script and its share of the owner reward
 * in basis points (Dash Core {@code MasternodePayoutShare}).
 */
public final class MasternodePayoutShare {
    public static final int MIN_REWARD = 100;
    public static final int MAX_REWARD = 10000;

    private final Script scriptPayout;
    private final int reward;

    public MasternodePayoutShare(Script scriptPayout, int reward) {
        this.scriptPayout = scriptPayout;
        this.reward = reward;
    }

    public Script getScriptPayout() {
        return scriptPayout;
    }

    /** the share of the owner reward in basis points */
    public int getReward() {
        return reward;
    }

    void serialize(OutputStream stream) throws IOException {
        Utils.bytesToByteStream(scriptPayout.getProgram(), stream);
        Utils.uint16ToByteStreamLE(reward, stream);
    }

    /** Dash Core's PayoutListToString */
    static String toString(NetworkParameters params, List<MasternodePayoutShare> payouts) {
        StringBuilder s = new StringBuilder();
        for (MasternodePayoutShare payout : payouts) {
            if (s.length() > 0) {
                s.append(',');
            }
            String payee;
            try {
                payee = payout.scriptPayout.getToAddress(params).toString();
            } catch (ScriptException x) {
                payee = Utils.HEX.encode(payout.scriptPayout.getProgram());
            }
            s.append(String.format(Locale.US, "%s:%d", payee, payout.reward));
        }
        return s.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MasternodePayoutShare that = (MasternodePayoutShare) o;
        return reward == that.reward && scriptPayout.equals(that.scriptPayout);
    }

    @Override
    public int hashCode() {
        return 31 * scriptPayout.hashCode() + reward;
    }

    @Override
    public String toString() {
        return "MasternodePayoutShare(script=" + scriptPayout + ", reward=" + reward + ")";
    }
}
