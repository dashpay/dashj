package org.bitcoinj.core;

/**
 * Created by Eric on 2/21/2016.
 */
public class MasternodePayments {
    NetworkParameters params;

    //! minimum peer version that can receive masternode payments
    // V1 - Last protocol version before update
    // V2 - Newest protocol version
    static final int MIN_MASTERNODE_PAYMENT_PROTO_VERSION_1 = 70066;
    static final int MIN_MASTERNODE_PAYMENT_PROTO_VERSION_2 = 70103;

    MasternodePayments(NetworkParameters params)
    {
        this.params = params;
    }

    int getMinMasternodePaymentsProto() {
        return params.sporkManager.isSporkActive(SporkManager.SPORK_10_MASTERNODE_PAY_UPDATED_NODES)
                ? MIN_MASTERNODE_PAYMENT_PROTO_VERSION_2
                : MIN_MASTERNODE_PAYMENT_PROTO_VERSION_1;
    }
}
