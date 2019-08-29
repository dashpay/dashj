package org.darkcoinj;

import org.dashj.core.Transaction;
import org.dashj.core.TransactionInput;

/**
 * Created by Eric on 2/8/2015.
 */
@Deprecated
public class DarkSendBroadcastTransaction {
    Transaction tx;
    TransactionInput vin;
    byte [] vchSig;
    long sigTime;
}
