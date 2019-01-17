package org.darkcoinj;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionInput;

/**
 * Created by Eric on 2/8/2015.
 */
@Deprecated
public class DarkSendEntryVin {
    boolean isSigSet;
    TransactionInput vin;

    DarkSendEntryVin(NetworkParameters params)
    {
        isSigSet = false;
        vin = new TransactionInput(params, null, null);  //need to set later
    }
}
