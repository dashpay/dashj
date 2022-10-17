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
import org.bitcoinj.core.NetworkParameters;

public class CoinJoinConstants {
    public static final int COINJOIN_AUTO_TIMEOUT_MIN = 5;
    public static final int COINJOIN_AUTO_TIMEOUT_MAX = 15;
    public static final int COINJOIN_QUEUE_TIMEOUT = 30;
    public static final int COINJOIN_SIGNING_TIMEOUT = 15;

    public static final int COINJOIN_ENTRY_MAX_SIZE = 9;

    public static final int MIN_COINJOIN_SESSIONS = 1;
    public static final int MIN_COINJOIN_ROUNDS = 2;
    public static final int MIN_COINJOIN_AMOUNT = 2;
    public static final int MIN_COINJOIN_DENOMS_GOAL = 10;
    public static final int MIN_COINJOIN_DENOMS_HARDCAP = 10;
    public static final int MAX_COINJOIN_SESSIONS = 10;
    public static final int MAX_COINJOIN_ROUNDS = 16;
    public static final int MAX_COINJOIN_DENOMS_GOAL = 100000;
    public static final int MAX_COINJOIN_DENOMS_HARDCAP = 100000;
    public static final int MAX_COINJOIN_AMOUNT = (int) NetworkParameters.MAX_MONEY.value;
    public static final int DEFAULT_COINJOIN_SESSIONS = 4;
    public static final int DEFAULT_COINJOIN_ROUNDS = 4;
    public static final Coin DEFAULT_COINJOIN_AMOUNT = Coin.valueOf(1000);
    public static final int DEFAULT_COINJOIN_DENOMS_GOAL = 50;
    public static final int DEFAULT_COINJOIN_DENOMS_HARDCAP = 300;

    public static final boolean DEFAULT_COINJOIN_AUTOSTART = false;
    public static final boolean DEFAULT_COINJOIN_MULTISESSION = false;

    // How many new denom outputs to create before we consider the "goal" loop in CreateDenominated
    // a final one and start creating an actual tx. Same limit applies for the "hard cap" part of the algo.
    // NOTE: We do not allow txes larger than 100kB, so we have to limit the number of outputs here.
    // We still want to create a lot of outputs though.
    // Knowing that each CTxOut is ~35b big, 400 outputs should take 400 x ~35b = ~17.5kb.
    // More than 500 outputs starts to make qt quite laggy.
    // Additionally to need all 500 outputs (assuming a max per denom of 50) you'd need to be trying to
    // create denominations for over 3000 dash!
    public static final int COINJOIN_DENOM_OUTPUTS_THRESHOLD = 500;

    // Warn user if mixing in gui or try to create backup if mixing in daemon mode
    // when we have only this many keys left
    public static final int COINJOIN_KEYS_THRESHOLD_WARNING = 100;
    // Stop mixing completely, it's too dangerous to continue when we have only this many keys left
    public static final int COINJOIN_KEYS_THRESHOLD_STOP = 50;
    // Pseudorandomly mix up to this many times in addition to base round count
    public static final int COINJOIN_RANDOM_ROUNDS = 3;
}
