package org.darkcoinj;

import org.bitcoinj.core.*;

import java.util.ArrayList;

/**
 * Created by Eric on 2/8/2015.
 */
@Deprecated
public class DarkSendEntry {
    boolean isSet;
    ArrayList<DarkSendEntryVin> sev;
    long amount;
    Transaction collateral;
    ArrayList<TransactionOutput> vout;
    Transaction txSupporting;
    long addedTime;

    //other variables
    NetworkParameters params;
    DarkSendEntry(NetworkParameters params)
    {
        isSet = false;
        collateral = new Transaction(params);//CTransaction();
        amount = 0;
        this.params = params;
    }

    boolean add(final ArrayList<TransactionInput> vinIn, long amountIn, final Transaction collateralIn, final ArrayList<TransactionOutput> voutIn)
    {
        if(isSet){return false;}


        for(TransactionInput v : vinIn)
        {
            DarkSendEntryVin s = new DarkSendEntryVin(params);
            s.vin = v;
            sev.add(s);
        }
        vout = voutIn;
        amount = amountIn;
        collateral = collateralIn;
        isSet = true;
        addedTime = Utils.currentTimeSeconds();

        return true;
    }

    boolean addSig(final TransactionInput vin)
    {

        for(DarkSendEntryVin s : sev) {
            if(s.vin.getOutpoint().equals(vin.getOutpoint()) && s.vin.getSequenceNumber() == vin.getSequenceNumber()){
                if(s.isSigSet){return false;}
                s.vin.setScriptSig(vin.getScriptSig());
                //TODO: ???? s.vin.prevPubKey = vin.prevPubKey;
                s.isSigSet = true;

                return true;
            }
        }

        return false;
    }

    boolean isExpired()
    {
        return (Utils.currentTimeSeconds() - addedTime) > DarkSend.DARKSEND_QUEUE_TIMEOUT;// 120 seconds
    }
}
