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

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.TransactionDestination;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.WalletEx;

public class TransactionBuilderOutput {
    /// Used for amount updates
    private final TransactionBuilder txBuilder;
    /// Reserve key where the amount of this output will end up
    private final ReserveDestination dest;
    /// Amount this output will receive
    private Coin amount;
    /// ScriptPubKey of this output
    Script script;

    public TransactionBuilderOutput(TransactionBuilder txBuilder, WalletEx wallet, Coin amount, boolean dryRun) {
        this.txBuilder = txBuilder;
        this.amount = amount;
        this.dest = new ReserveDestination(wallet);
        TransactionDestination txdest = !dryRun ? dest.getReservedDestination(false) : KeyId.fromBytes(new byte[20]);
        script = txdest.getScript();
    }

    /// Get the scriptPubKey of this output
    public Script getScript() { return script; }
    /// Get the amount of this output
    public Coin getAmount() { return amount; }
    /// Try update the amount of this output. Returns true if it was successful and false if not (e.g. insufficient amount left).
    public boolean updateAmount(Coin newAmount) {
        if (!newAmount.isPositive() || newAmount.subtract(amount).isGreaterThan(txBuilder.getAmountLeft())) {
            return false;
        }
        amount = newAmount;
        return true;
    }
    /// Tell the wallet to remove the key used by this output from the keypool
    public void keepKey() { dest.keepDestination(); }
    /// Tell the wallet to return the key used by this output to the keypool
    public void returnKey() { dest.returnDestination(); }
}
