package org.bitcoinj.coinjoin;

import org.bitcoinj.core.Context;

public class CoinJoinServer extends CoinJoinBaseSession {

    public CoinJoinServer(Context context) {
        super(context);
    }

    public void setSession(int sessionID) {
        this.sessionID.set(sessionID);
    }

    public void setDenomination(int denomination) {
        this.sessionDenom = denomination;
    }
}
