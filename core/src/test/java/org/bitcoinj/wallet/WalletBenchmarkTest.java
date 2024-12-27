package org.bitcoinj.wallet;

import com.google.common.base.Stopwatch;
import org.bitcoinj.core.Transaction;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class WalletBenchmarkTest {
    @Test
    public void getTransactionsTest() throws UnreadableWalletException, IOException {
        Set<Transaction> set = null;
        Collection<Transaction> list = null;
        int count = 1;
        try (InputStream stream = getClass().getResourceAsStream("coinjoin-testnet-big.wallet")) {
            WalletEx coinJoinWallet = (WalletEx) new WalletProtobufSerializer().readWallet(stream);
            Stopwatch watch = Stopwatch.createStarted();
            for (int i = 0; i < count; ++i)
                set = coinJoinWallet.getTransactions(true);
            System.out.println("getTransactions = " + watch);
        }
        try (InputStream stream = getClass().getResourceAsStream("coinjoin-testnet-big.wallet")) {
            WalletEx coinJoinWallet = (WalletEx) new WalletProtobufSerializer().readWallet(stream);
            Stopwatch watch = Stopwatch.createStarted();
            for (int i = 0; i < count; ++i)
               set = coinJoinWallet.getTransactionsOpt(true);
            System.out.println("getTransactions(optimized) = " + watch);
        }
        try (InputStream stream = getClass().getResourceAsStream("coinjoin-testnet-big.wallet")) {
            WalletEx coinJoinWallet = (WalletEx) new WalletProtobufSerializer().readWallet(stream);
            Stopwatch watch = Stopwatch.createStarted();
            for (int i = 0; i < count; ++i)
                list = coinJoinWallet.getTransactionList(true);
            System.out.println("getTransactionList = " + watch);
        }
        assertEquals(set.size(), list.size());
    }

}
