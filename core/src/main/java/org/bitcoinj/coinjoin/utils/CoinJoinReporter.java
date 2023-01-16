package org.bitcoinj.coinjoin.utils;

import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinClientSession;
import org.bitcoinj.coinjoin.listeners.SessionCompleteListener;
import org.bitcoinj.wallet.Wallet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class CoinJoinReporter implements SessionCompleteListener {

    FileWriter fileWriter;
    BufferedWriter writer;
    Wallet wallet;

    private int completedSessions = 0;

    public CoinJoinReporter(Wallet wallet) {
        this.wallet = wallet;
        try {
            File reportFile = new File("./coinjoin-report.txt");
            //outputStream = Files.newOutputStream(reportFile.toPath());
            fileWriter = new FileWriter(reportFile);
            writer = new BufferedWriter(fileWriter);
            writer.write("CoinJoin Report:");
            writer.newLine();
            writer.newLine();
            writer.flush();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        close();
    }

    public void close() {
        try {
            writer.flush();
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    public void onSessionComplete(CoinJoinClientSession session) {
        try {
            completedSessions++;
            writer.write("Session Complete: ");
            writer.write(String.format("id: %d, denom: %s[%d]", session.getSessionID(), CoinJoin.denominationToAmount(session.getSessionDenom()), session.getSessionDenom()));
            writer.newLine();
            double percentComplete = 100.0 * wallet.getBalance(Wallet.BalanceType.COINJOIN_SPENDABLE).value / wallet.getBalance(Wallet.BalanceType.DENOMINATED_FOR_MIXING_SPENDABLE).value;
            writer.write(String.format("Session Stats: %d sessions, %.02f", completedSessions, percentComplete));
            writer.newLine();
            writer.flush();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }
}
