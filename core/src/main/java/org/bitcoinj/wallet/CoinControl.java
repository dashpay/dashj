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
import org.bitcoinj.core.NoDestination;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionDestination;
import org.bitcoinj.core.TransactionOutPoint;

import java.util.ArrayList;
import java.util.HashSet;

public class CoinControl {
    private TransactionDestination destChange;
    //! If false, allows unselected inputs, but requires all selected inputs be used if fAllowOtherInputs is true (default)
    private boolean allowOtherInputs;
    //! If false, only include as many inputs as necessary to fulfill a coin selection request. Only usable together with fAllowOtherInputs
    private boolean requireAllInputs;
    //! Includes watch only addresses which are solvable
    private boolean allowWatchOnly;
    //! Override automatic min/max checks on fee, m_feerate must be set if true
    private boolean overrideFeeRate;
    //! Override the wallet's m_pay_tx_fee if set
    private Coin feeRate;
    //! Override the discard feerate estimation with m_discard_feerate in CreateTransaction if set
    private Coin discardFeeRate;
    //! Override the default confirmation target if set
    private Integer confirmTarget;
    //! Avoid partial use of funds sent to a given address
    private boolean avoidPartialSpends;
    //! Forbids inclusion of dirty (previously used) addresses
    private boolean avoidAddressReuse;
    //! Fee estimation mode to control arguments to estimateSmartFee
    //FeeEstimateMode m_fee_mode;
    //! Minimum chain depth value for coin availability
    private int minDepth = 0;
    //! Controls which types of coins are allowed to be used (default: ALL_COINS)
    private CoinType coinType;

    public CoinControl() {
        setSelected = new HashSet<>();
        setNull();
    }

    public void setNull() {
        setNull(false);
    }

    public void setNull(boolean fResetCoinType) {
        destChange = NoDestination.get();
        allowOtherInputs = false;
        allowWatchOnly = false;
        avoidPartialSpends = false;
        avoidAddressReuse = false;
        setSelected.clear();
        feeRate = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.div(1000);
        overrideFeeRate = false;
        confirmTarget = -1;
        requireAllInputs = true;
        discardFeeRate = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.div(1000);
        if (fResetCoinType) {
            coinType = CoinType.ALL_COINS;
        }
    }

    public boolean hasSelected() {
        return (setSelected.size() > 0);
    }

    public boolean isSelected(TransactionOutPoint output) {
        return setSelected.contains(output);
    }

    public void select(TransactionOutPoint output) {
        setSelected.add(output);
    }

    public void unSelect(TransactionOutPoint output) {
        setSelected.add(output);
    }

    public void unSelectAll() {
        setSelected.clear();
    }

    public void listSelected(ArrayList<TransactionOutPoint> vOutpoints) {
        vOutpoints.clear();
        vOutpoints.addAll(setSelected);
    }

    // Dash-specific helpers

    public void useCoinJoin(boolean fUseCoinJoin) {
        coinType = fUseCoinJoin ? CoinType.ONLY_FULLY_MIXED : CoinType.ALL_COINS;
    }

    public boolean isUsingCoinJoin() {
        return coinType == CoinType.ONLY_FULLY_MIXED;
    }

    private HashSet<TransactionOutPoint> setSelected;

    public Coin getDiscardFeeRate() {
        return discardFeeRate;
    }

    public void setDiscardFeeRate(Coin discardFeeRate) {
        this.discardFeeRate = discardFeeRate;
    }

    public Coin getFeeRate() {
        return feeRate;
    }

    public void setFeeRate(Coin feeRate) {
        this.feeRate = feeRate.div(1000);
    }

    public TransactionDestination getDestChange() {
        return destChange;
    }

    public CoinType getCoinType() {
        return coinType;
    }

    public int getMinDepth() {
        return minDepth;
    }

    public Integer getConfirmTarget() {
        return confirmTarget;
    }

    public void setDestChange(TransactionDestination destChange) {
        this.destChange = destChange;
    }

    public boolean shouldAllowOtherInputs() {
        return allowOtherInputs;
    }

    public void setAllowOtherInputs(boolean allowOtherInputs) {
        this.allowOtherInputs = allowOtherInputs;
    }

    public boolean shouldAllowWatchOnly() {
        return allowWatchOnly;
    }

    public void setAllowWatchOnly(boolean allowWatchOnly) {
        this.allowWatchOnly = allowWatchOnly;
    }

    public boolean shouldAvoidAddressReuse() {
        return avoidAddressReuse;
    }

    public void setAvoidAddressReuse(boolean avoidAddressReuse) {
        this.avoidAddressReuse = avoidAddressReuse;
    }

    public void setAvoidPartialSpends(boolean avoidPartialSpends) {
        this.avoidPartialSpends = avoidPartialSpends;
    }

    public void setCoinType(CoinType coinType) {
        this.coinType = coinType;
    }

    public void setConfirmTarget(Integer confirmTarget) {
        this.confirmTarget = confirmTarget;
    }

    public void setMinDepth(int minDepth) {
        this.minDepth = minDepth;
    }

    public void setOverrideFeeRate(boolean overrideFeeRate) {
        this.overrideFeeRate = overrideFeeRate;
    }

    public void setRequireAllInputs(boolean requireAllInputs) {
        this.requireAllInputs = requireAllInputs;
    }

    public void setSetSelected(HashSet<TransactionOutPoint> setSelected) {
        this.setSelected = setSelected;
    }
}
