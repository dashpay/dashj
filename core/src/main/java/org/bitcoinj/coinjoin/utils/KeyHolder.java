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

import org.bitcoinj.core.TransactionDestination;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletEx;

public class KeyHolder {
    ReserveDestination reserveDestination; // TODO: use ReserveKey
    TransactionDestination destination;

    public KeyHolder(WalletEx wallet) {
        // get the next CoinJoinKey?
        reserveDestination = new ReserveDestination(wallet);
        destination = reserveDestination.getReservedDestination(false);
    }

    void keepKey() {
        reserveDestination.keepDestination();
    }

    void returnKey() {
        reserveDestination.returnDestination();
    }

    Script getScriptForDestination() {
        return destination.getScript();
    }
}
