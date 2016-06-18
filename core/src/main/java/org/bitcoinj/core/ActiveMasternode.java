package org.bitcoinj.core;

import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Hash Engineering on 2/22/2016.
 *
 * Translated from CActiveMasternode class (dash/src/activemasternode.cpp)
 */
public class ActiveMasternode {
    private static final Logger log = LoggerFactory.getLogger(ActiveMasternode.class);
    ReentrantLock lock = Threading.lock("activemasternode");
    public static final int ACTIVE_MASTERNODE_INITIAL               =      0; // initial state
    public static final int ACTIVE_MASTERNODE_SYNC_IN_PROCESS       =      1;
    public static final int ACTIVE_MASTERNODE_INPUT_TOO_NEW         =      2;
    public static final int ACTIVE_MASTERNODE_NOT_CAPABLE           =      3;
    public static final int ACTIVE_MASTERNODE_STARTED               =      4;

    Context context;

    // Initialized by init.cpp
    // Keys for the main Masternode
    public PublicKey pubKeyMasternode = new PublicKey();

    // Initialized while registering Masternode
    public TransactionInput vin;
    public MasternodeAddress service;

    public int status;
    public String notCapableReason;

    public ActiveMasternode(Context context)
    {
        this.context = context;

        status = ACTIVE_MASTERNODE_NOT_CAPABLE;
    }

    // when starting a Masternode, this can enable to run as a hot wallet with no funds
    boolean enableHotColdMasterNode(TransactionInput newVin, MasternodeAddress newService)
    {
        if(!DarkCoinSystem.fMasterNode) return false;

        //Since this is not for a masternode, we don't need to impliment

        status = ACTIVE_MASTERNODE_STARTED;

        //The values below are needed for signing mnping messages going forward
        vin = newVin;
        service = newService;

        log.info("CActiveMasternode::EnableHotColdMasterNode() - Enabled! You may shut down the cold daemon.\n");

        return true;
    }

    public void manageStatus() {
        //std::string errorMessage;

        if (!DarkCoinSystem.fMasterNode) return;
    }
}
