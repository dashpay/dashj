package org.bitcoinj.core;

/**
 * Created by Eric on 2/8/2017.
 */
public class MasternodeInfo {

    MasternodeInfo()
    {
    }



    public TransactionInput vin;
    public MasternodeAddress addr;
    public PublicKey pubKeyCollateralAddress;
    public PublicKey pubKeyMasternode;
    public MasternodeSignature sig;
    public long sigTime; //mnb message time
    public long nLastDsq; //the dsq count from the last dsq broadcast of this node
    public long nTimeLastChecked;
    public long nTimeLastPaid;
    public long nTimeLastWatchdogVote;
    public Masternode.State nActiveState;
    public int nProtocolVersion;
    public boolean fInfoValid;
}
