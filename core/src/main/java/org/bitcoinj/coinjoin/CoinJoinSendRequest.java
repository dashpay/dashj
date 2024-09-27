package org.bitcoinj.coinjoin;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;

public class CoinJoinSendRequest {
    /**
     * <p>Creates a new CoinJoin SendRequest to the given address for the given value.</p>
     */
    public static SendRequest to(Wallet wallet, Address destination, Coin value) {
        SendRequest req = SendRequest.to(destination, value);
        req.coinSelector = new CoinJoinCoinSelector(wallet);
        req.returnChange = false;
        return req;
    }

    /**
     * <p>Creates a new CoinJoin SendRequest to the given pubkey for the given value.</p>
     */
    public static SendRequest to(Wallet wallet, ECKey destination, Coin value) {
        SendRequest req = SendRequest.to(wallet.getParams(), destination, value);
        req.coinSelector = new CoinJoinCoinSelector(wallet);
        req.returnChange = false;
        return req;
    }
    /** Simply wraps a pre-built incomplete CoinJoin transaction provided by you. */
    public static SendRequest forTx(Wallet wallet, Transaction tx, boolean returnChange) {
        SendRequest req = SendRequest.forTx(tx);
        req.coinSelector = new CoinJoinCoinSelector(wallet);
        req.returnChange = returnChange;
        return req;
    }
}
