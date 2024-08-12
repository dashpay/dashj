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

import com.google.common.base.Preconditions;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

public class InputCoin implements Comparable<InputCoin> {
    private final TransactionOutPoint outPoint;
    private final TransactionOutput output;
    private final Coin effectiveValue;
    private int inputBytes;

    public InputCoin(Transaction tx, int i) {
        Preconditions.checkNotNull(tx, "transaction should not be null");
        Preconditions.checkArgument(i < tx.getOutputs().size(), "The output index is out of range");
        outPoint = new TransactionOutPoint(tx.getParams(), i, tx.getTxId());
        output = tx.getOutput(i);
        effectiveValue = output.getValue();
    }

    public InputCoin(Transaction tx, int i, int inputBytes) {
        this(tx, i);
        this.inputBytes = inputBytes;
    }


    @Override
    public int compareTo(InputCoin inputCoin) {
        return outPoint.getHash().compareTo(inputCoin.outPoint.getHash());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InputCoin inputCoin = (InputCoin) o;

        return outPoint.equals(inputCoin.outPoint);
    }

    @Override
    public int hashCode() {
        return outPoint.hashCode();
    }

    public TransactionOutPoint getOutPoint() {
        return outPoint;
    }

    public TransactionOutput getOutput() {
        return output;
    }

    public Coin getEffectiveValue() {
        return effectiveValue;
    }

    public int getInputBytes() {
        return inputBytes;
    }
}
