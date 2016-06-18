package org.darkcoinj;

import org.bitcoinj.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Eric on 3/7/2015.
 */
public class ActiveMasterNode {
    private static final Logger log = LoggerFactory.getLogger(ActiveMasterNode.class);
    // Initialized by init.cpp
    // Keys for the main masternode
    public PublicKey pubKeyMasternode;

    // Initialized while registering masternode
    TransactionInput vin;
    PeerAddress service;

    int status;
    String notCapableReason;

    public ActiveMasterNode()
    {
        status = MasterNodeSystem.MASTERNODE_NOT_PROCESSED;
    }
    // when starting a masternode, this can enable to run as a hot wallet with no funds
    public boolean EnableHotColdMasterNode(TransactionInput newVin, PeerAddress newService)
    {
        if(!DarkCoinSystem.fMasterNode) return false;

        status = MasterNodeSystem.MASTERNODE_REMOTELY_ENABLED;

        //The values below are needed for signing dseep messages going forward
        this.vin = newVin;
        this.service = newService;

        log.info("CActiveMasternode::EnableHotColdMasterNode() - Enabled! You may shut down the cold daemon.\n");

        return true;
    }
}
