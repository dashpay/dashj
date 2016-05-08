package org.bitcoinj.core;

/**
 * Created by Eric on 2/21/2016.
 */
public class MasternodePayments {
    Context context;

    //! minimum peer version that can receive masternode payments
    // V1 - Last protocol version before update
    // V2 - Newest protocol version
    static final int MIN_MASTERNODE_PAYMENT_PROTO_VERSION_1 = 70066;
    static final int MIN_MASTERNODE_PAYMENT_PROTO_VERSION_2 = 70103;

    MasternodePayments(Context context)
    {
        this.context = context;
    }

    int getMinMasternodePaymentsProto() {
        return context.sporkManager.isSporkActive(SporkManager.SPORK_10_MASTERNODE_PAY_UPDATED_NODES)
                ? MIN_MASTERNODE_PAYMENT_PROTO_VERSION_2
                : MIN_MASTERNODE_PAYMENT_PROTO_VERSION_1;
    }

    public void cleanPaymentList()
    {
        /*LOCK2(cs_mapMasternodePayeeVotes, cs_mapMasternodeBlocks);

        if(chainActive.Tip() == NULL) return;

        //keep up to five cycles for historical sake
        int nLimit = std::max(int(mnodeman.size()*1.25), 1000);

        std::map<uint256, CMasternodePaymentWinner>::iterator it = mapMasternodePayeeVotes.begin();
        while(it != mapMasternodePayeeVotes.end()) {
            CMasternodePaymentWinner winner = (*it).second;

            if(chainActive.Tip()->nHeight - winner.nBlockHeight > nLimit){
                LogPrint("mnpayments", "CMasternodePayments::CleanPaymentList - Removing old Masternode payment - block %d\n", winner.nBlockHeight);
                masternodeSync.mapSeenSyncMNW.erase((*it).first);
                mapMasternodePayeeVotes.erase(it++);
                mapMasternodeBlocks.erase(winner.nBlockHeight);
            } else {
                ++it;
            }
        }
        */
    }
}
