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

package org.bitcoinj.coinjoin;

import org.bitcoinj.core.Coin;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_RANDOM_ROUNDS;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_AMOUNT;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_DENOMS_GOAL;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_DENOMS_HARDCAP;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_MULTISESSION;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_ROUNDS;
import static org.bitcoinj.coinjoin.CoinJoinConstants.DEFAULT_COINJOIN_SESSIONS;

public class CoinJoinClientOptions {
    public static int getSessions() { return get().coinJoinSessions.get(); }
    public static int getRounds() { return get().coinJoinRounds.get(); }
    public static int getRandomRounds() { return get().coinJoinRandomRounds.get(); }
    public static Coin getAmount() { return get().coinJoinAmount.get(); }
    public static int getDenomsGoal() { return get().coinJoinDenomsGoal.get(); }
    public static int getDenomsHardCap() { return get().coinJoinDenomsHardCap.get(); }

    public static void setEnabled(boolean enabled) {
        get().enableCoinJoin.set(enabled);
    }
    public static void setMultiSessionEnabled(boolean enabled) {
        get().isCoinJoinMultiSession.set(enabled);
    }

    public static void setSessions(int sessions) {
        get().coinJoinSessions.set(sessions);
    }

    public static void setRounds(int rounds) {
        get().coinJoinRounds.set(rounds);
    }
    public static void setAmount(Coin amount) {
        get().coinJoinAmount.set(amount);
    }

    public static boolean isEnabled() { return CoinJoinClientOptions.get().enableCoinJoin.get(); }
    public static boolean isMultiSessionEnabled() { return CoinJoinClientOptions.get().isCoinJoinMultiSession.get(); }

    public static List<Coin> getDenominations() { return CoinJoinClientOptions.get().allowedDenominations.get(); }

    public static boolean removeDenomination(Coin amount) { return CoinJoinClientOptions.get().allowedDenominations.get().remove(amount); }

    public static void resetDenominations() { CoinJoinClientOptions.get().allowedDenominations.set(CoinJoin.getStandardDenominations()); }
    private static CoinJoinClientOptions instance;
    private static boolean onceFlag;

    private final AtomicInteger coinJoinSessions = new AtomicInteger(DEFAULT_COINJOIN_SESSIONS);
    private final AtomicInteger coinJoinRounds = new AtomicInteger(DEFAULT_COINJOIN_ROUNDS);
    private final AtomicInteger coinJoinRandomRounds = new AtomicInteger(COINJOIN_RANDOM_ROUNDS);
    private final AtomicReference<Coin> coinJoinAmount = new AtomicReference<>(DEFAULT_COINJOIN_AMOUNT);
    private final AtomicInteger coinJoinDenomsGoal = new AtomicInteger(DEFAULT_COINJOIN_DENOMS_GOAL);
    private final AtomicInteger coinJoinDenomsHardCap = new AtomicInteger(DEFAULT_COINJOIN_DENOMS_HARDCAP);
    private final AtomicBoolean enableCoinJoin = new AtomicBoolean(false);
    private final AtomicBoolean isCoinJoinMultiSession = new AtomicBoolean(DEFAULT_COINJOIN_MULTISESSION);
    private final AtomicReference<List<Coin>> allowedDenominations = new AtomicReference<>(CoinJoin.getStandardDenominations());

    private CoinJoinClientOptions() {
        coinJoinSessions.set(DEFAULT_COINJOIN_SESSIONS);
        coinJoinRounds.set(DEFAULT_COINJOIN_ROUNDS);
        coinJoinRandomRounds.set(COINJOIN_RANDOM_ROUNDS);
        coinJoinAmount.set(DEFAULT_COINJOIN_AMOUNT);
        coinJoinDenomsGoal.set(DEFAULT_COINJOIN_DENOMS_GOAL);
        coinJoinDenomsHardCap.set(DEFAULT_COINJOIN_DENOMS_HARDCAP);
        enableCoinJoin.set(false);
        isCoinJoinMultiSession.set(DEFAULT_COINJOIN_MULTISESSION);
    }

    private static CoinJoinClientOptions get() {
        if (!onceFlag) {
            init();
            onceFlag = true;
        }
        return instance;
    }

    public static void reset() {
        init();
        onceFlag = true;
    }
    private static void init() {
        instance = new CoinJoinClientOptions();
    }
}
