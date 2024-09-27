/*
 * Copyright (c) 2023 Dash Core Group
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

import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBag;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.ScriptPattern;

public enum CoinJoinTransactionType {
    None,
    CreateDenomination,
    MakeCollateralInputs,
    MixingFee,
    Mixing,
    Send;

    public static CoinJoinTransactionType fromTx(Transaction tx, TransactionBag transactionBag) {
        if (tx.getInputs().size() == tx.getOutputs().size() && tx.getValue(transactionBag).equals(Coin.ZERO)) {
            return Mixing;
        } else if (isMixingFee(tx)) {
            return MixingFee;
        } else {
            boolean makeCollateral = false;
            if (tx.getOutputs().size() == 2) {
                Coin nAmount0 = tx.getOutput(0).getValue();
                Coin nAmount1 = tx.getOutput(1).getValue();
                // <case1>, see CCoinJoinClientSession.makeCollateralAmounts
                makeCollateral = (nAmount0.equals(CoinJoin.getMaxCollateralAmount()) && !CoinJoin.isDenominatedAmount(nAmount1) && nAmount1.isGreaterThanOrEqualTo(CoinJoin.getCollateralAmount())) ||
                        (nAmount1.equals(CoinJoin.getMaxCollateralAmount()) && !CoinJoin.isDenominatedAmount(nAmount0) && nAmount0.isGreaterThanOrEqualTo(CoinJoin.getCollateralAmount())) ||
                        // <case2>, see CCoinJoinClientSession.makeCollateralAmounts
                        (nAmount0 == nAmount1 && CoinJoin.isCollateralAmount(nAmount0));
            } else if (tx.getOutputs().size() == 1) {
                // <case3>, see CCoinJoinClientSession.makeCollateralAmounts
                makeCollateral = CoinJoin.isCollateralAmount(tx.getOutput(0).getValue());
            }
            if (makeCollateral) {
                return MakeCollateralInputs;
            } else {
                for (TransactionOutput output : tx.getOutputs()) {
                    if (CoinJoin.isDenominatedAmount(output.getValue())) {
                        return CreateDenomination; // Done, it's definitely a tx creating mixing denoms, no need to look any further
                    }
                }
            }
        }
        // is this a coinjoin send transaction
        if (isCoinJoinSend(tx))
            return Send;
        return None;
    }

    /**
     * CoinJoin Send
     * 1. All inputs are denomations
     * 2. Fee > 0
     * 3. No change returned.  This is not checked
    */
    private static boolean isCoinJoinSend(Transaction tx) {
        boolean inputsAreDenominated = tx.getInputs()
                .stream().allMatch(input -> input.getValue() != null && CoinJoin.isDenominatedAmount(input.getValue()));
        Coin fee = tx.getFee();
        return inputsAreDenominated && (fee != null && !fee.isZero());
    }

    public static boolean isMixingFee(Transaction tx) {
        Coin inputsValue = tx.getInputSum();
        Coin outputsValue = tx.getOutputSum();
        Coin netValue = inputsValue.subtract(outputsValue);
        // check for the tx with OP_RETURN
        if (outputsValue.isZero() && tx.getInputs().size() == 1 && tx.getOutputs().size() == 1 && ScriptPattern.isOpReturn(tx.getOutputs().get(0).getScriptPubKey())) {
            return true;
        }
        return tx.getInputs().size() == 1 && tx.getOutputs().size() == 1
                && CoinJoin.isCollateralAmount(inputsValue)
                && CoinJoin.isCollateralAmount(outputsValue)
                && CoinJoin.isCollateralAmount(netValue);
    }
}
