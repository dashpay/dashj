package org.bitcoinj.core;

/**
 * Created by Hash Engineering on 2/21/2016.
 */
public class MasternodePayments {
    Context context;

    //! minimum peer version that can receive masternode payments
    // V1 - Last protocol version before update
    // V2 - Newest protocol version
    static final int MIN_MASTERNODE_PAYMENT_PROTO_VERSION_1 = 70208;
    static final int MIN_MASTERNODE_PAYMENT_PROTO_VERSION_2 = 70208;

    final float nStorageCoeff = 1.25f;
    final int nMinBlocksToStore = 5000;

    public MasternodePayments(Context context)
    {
        this.context = context;
    }

    int getMinMasternodePaymentsProto() {
        return MIN_MASTERNODE_PAYMENT_PROTO_VERSION_2;
    }

    public void cleanPaymentList()
    {

    }
    public void checkAndRemove()
    {

    }
    boolean isEnoughData() {
        return false;
    }
}
