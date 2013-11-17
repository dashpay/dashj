package com.google.digitalcoin.wallet;

import com.google.digitalcoin.core.TransactionOutput;

import java.math.BigInteger;
import java.util.LinkedList;

/**
 * A CoinSelector is responsible for picking some outputs to spend, from the list of all spendable outputs. It
 * allows you to customize the policies for creation of transactions to suit your needs. The select operation
 * may return a {@link CoinSelection} that has a valueGathered lower than the requested target, if there's not
 * enough money in the wallet.
 */
public interface CoinSelector {
    public CoinSelection select(BigInteger target, LinkedList<TransactionOutput> candidates);
}
