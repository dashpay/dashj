package org.bitcoinj.coinjoin;

import org.bitcoinj.coinjoin.utils.CoinJoinResult;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static org.bitcoinj.coinjoin.PoolMessage.ERR_DENOM;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_INVALID_COLLATERAL;

public class CoinJoinServer extends CoinJoinBaseSession {

    static final Logger log = LoggerFactory.getLogger(CoinJoinServer.class);
    public CoinJoinServer(Context context) {
        super(context);
    }

    public void setSession(int sessionID) {
        this.sessionID.set(sessionID);
    }

    public void setDenomination(int denomination) {
        this.sessionDenom = denomination;
    }

    public CoinJoinResult isAcceptableDSA(CoinJoinAccept dsa) {
        if (!CoinJoin.isValidDenomination(dsa.getDenomination())) {
            log.info("coinjoin: denom not valid!");
            return CoinJoinResult.fail(ERR_DENOM);
        }

        // check collateral
        // the server doesn't have full access to the inputs
        if (!CoinJoin.isCollateralValid(dsa.getTxCollateral(), false)) {
            log.info("coinjoin: collateral not valid!");
            return CoinJoinResult.fail(ERR_INVALID_COLLATERAL);
        }

        return CoinJoinResult.success();
    }

    public boolean validateFinalTransaction(List<CoinJoinEntry> entries, Transaction finalMutableTransaction) {
        for (CoinJoinEntry entry: entries){
            // Check that the final transaction has all our outputs
            for (TransactionOutput txout : entry.getMixingOutputs()) {
                boolean found = false;
                for (TransactionOutput output : finalMutableTransaction.getOutputs()) {
                    found = txout.getValue().equals(output.getValue())
                            && Arrays.equals(txout.getScriptBytes(), output.getScriptBytes());
                    if (found) {
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }
}
