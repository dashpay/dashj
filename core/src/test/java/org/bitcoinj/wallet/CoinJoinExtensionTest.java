package org.bitcoinj.wallet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoinJoinExtensionTest {
    private final TestNet3Params UNITTEST = TestNet3Params.get();

    @Test public void emptyWalletProgressTest() {
        new Context(TestNet3Params.get());
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

        @Test
    public void testOutpointRoundsCachePersistence() throws Exception {
        // Create a WalletEx with CoinJoin extension
        WalletEx walletEx = new WalletEx(UNITTEST, KeyChainGroup.createBasic(UNITTEST));
        CoinJoinExtension coinJoinExtension = new CoinJoinExtension(walletEx);
        walletEx.addExtension(coinJoinExtension);

        // Create some fake outpoints and add them to the cache
        Sha256Hash txHash1 = Sha256Hash.of("test1".getBytes());
        Sha256Hash txHash2 = Sha256Hash.of("test2".getBytes());
        TransactionOutPoint outPoint1 = new TransactionOutPoint(UNITTEST, 0, txHash1);
        TransactionOutPoint outPoint2 = new TransactionOutPoint(UNITTEST, 1, txHash2);

        walletEx.mapOutpointRoundsCache.put(outPoint1, 5);
        walletEx.mapOutpointRoundsCache.put(outPoint2, 10);

        // Serialize and deserialize the wallet
        WalletEx wallet2 = (WalletEx) roundTrip(walletEx);

        // Verify the cache was persisted correctly
        assertEquals(2, wallet2.mapOutpointRoundsCache.size());
        assertEquals(Integer.valueOf(5), wallet2.mapOutpointRoundsCache.get(outPoint1));
        assertEquals(Integer.valueOf(10), wallet2.mapOutpointRoundsCache.get(outPoint2));
    }

    private static Wallet roundTrip(Wallet wallet) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new WalletProtobufSerializer().writeWallet(wallet, output);
        ByteArrayInputStream test = new ByteArrayInputStream(output.toByteArray());
        assertTrue(WalletProtobufSerializer.isWallet(test));
        ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
        return new WalletProtobufSerializer().readWallet(input);
    }
}
