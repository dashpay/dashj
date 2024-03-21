package org.bitcoinj.wallet;

import org.bitcoinj.core.Coin;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;

public class CoinJoinExtensionTest {

    @Test public void emptyWalletProgressTest() {
        try (InputStream is = getClass().getResourceAsStream("coinjoin-unmixed.wallet")) {
            WalletEx wallet = (WalletEx) new WalletProtobufSerializer().readWallet(is);
            assertEquals(Coin.valueOf(99999628), wallet.getBalance(Wallet.BalanceType.ESTIMATED));

            assertEquals(0.00, wallet.getCoinJoin().getMixingProgress(), 0.001);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (UnreadableWalletException e) {
            throw new RuntimeException(e);
        }
    }
}
