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

package org.bitcoinj.wallet;

import org.bitcoinj.core.Coin;

public class Balance {
    private Coin myTrusted = Coin.ZERO;           //!< Trusted, at depth=GetBalance.min_depth or more
    private Coin myUntrustedPending = Coin.ZERO; //!< Untrusted, but in mempool (pending)
    private Coin myImmature = Coin.ZERO;          //!< Immature coinbases in the main chain
    private Coin watchOnlyTrusted = Coin.ZERO;
    private Coin watchOnlyUntrustedPending = Coin.ZERO;
    private Coin watchOnlyImmature = Coin.ZERO;
    private Coin anonymized = Coin.ZERO;
    private Coin denominatedTrusted = Coin.ZERO;
    private Coin denominatedUntrustedPending = Coin.ZERO;

    public Coin getMyTrusted() {
        return myTrusted;
    }

    public Balance setMyTrusted(Coin myTrusted) {
        this.myTrusted = myTrusted;
        return this;
    }

    public Coin getMyUntrustedPending() {
        return myUntrustedPending;
    }

    public Balance setMyUntrustedPending(Coin myUntrustedPending) {
        this.myUntrustedPending = myUntrustedPending;
        return this;
    }

    public Coin getMyImmature() {
        return myImmature;
    }

    public Balance setMyImmature(Coin myImmature) {
        this.myImmature = myImmature;
        return this;
    }

    public Coin getWatchOnlyTrusted() {
        return watchOnlyTrusted;
    }

    public Balance setWatchOnlyTrusted(Coin watchOnlyTrusted) {
        this.watchOnlyTrusted = watchOnlyTrusted;
        return this;
    }

    public Coin getWatchOnlyUntrustedPending() {
        return watchOnlyUntrustedPending;
    }

    public Balance setWatchOnlyUntrustedPending(Coin watchOnlyUntrustedPending) {
        this.watchOnlyUntrustedPending = watchOnlyUntrustedPending;
        return this;
    }

    public Coin getWatchOnlyImmature() {
        return watchOnlyImmature;
    }
    
    public Balance setWatchOnlyImmature(Coin watchOnlyImmature) {
        this.watchOnlyImmature = watchOnlyImmature;
        return this;
    }

    public Coin getAnonymized() {
        return anonymized;
    }

    public Balance setAnonymized(Coin anonymized) {
        this.anonymized = anonymized;
        return this;
    }

    public Coin getDenominatedTrusted() {
        return denominatedTrusted;
    }

    public Balance setDenominatedTrusted(Coin denominatedTrusted) {
        this.denominatedTrusted = denominatedTrusted;
        return this;
    }

    public Coin getDenominatedUntrustedPending() {
        return denominatedUntrustedPending;
    }

    public Balance setDenominatedUntrustedPending(Coin denominatedUntrustedPending) {
        this.denominatedUntrustedPending = denominatedUntrustedPending;
        return this;
    }

    @Override
    public String toString() {
        return "Balance{" +
                "myTrusted=" + myTrusted.toFriendlyString() +
                ", myUntrustedPending=" + myUntrustedPending.toFriendlyString() +
                ", myImmature=" + myImmature.toFriendlyString() +
                ", watchOnlyTrusted=" + watchOnlyTrusted.toFriendlyString() +
                ", watchOnlyUntrustedPending=" + watchOnlyUntrustedPending.toFriendlyString() +
                ", watchOnlyImmature=" + watchOnlyImmature.toFriendlyString() +
                ", anonymized=" + anonymized.toFriendlyString() +
                ", denominatedTrusted=" + denominatedTrusted.toFriendlyString() +
                ", denominatedUntrustedPending=" + denominatedUntrustedPending.toFriendlyString() +
                '}';
    }
}
