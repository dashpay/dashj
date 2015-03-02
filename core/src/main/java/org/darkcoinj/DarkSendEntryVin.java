package org.darkcoinj;

import org.bitcoinj.core.TransactionInput;

/**
 * Created by Eric on 2/8/2015.
 */
public class DarkSendEntryVin {
    boolean isSigSet;
    TransactionInput vin;

    DarkSendEntryVin()
    {
        isSigSet = false;
        vin = null;  //need to set later
    }
}
