package org.dashj.core;

import org.dashj.params.MainNetParams;
import org.dashj.store.BlockStoreException;
import org.dashj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

import static org.dashj.core.Masternode.MASTERNODE_SENTINEL_MAX_SECONDS;
import static org.dashj.core.MasternodePing.MASTERNODE_MIN_MNP_SECONDS;

/**
 * Created by Hash Engineering on 2/22/2016.
 *
 * Translated from CActiveMasternode class (dash/src/activemasternode.cpp)
 */
@Deprecated
public class ActiveMasternode {
    private static final Logger log = LoggerFactory.getLogger(ActiveMasternode.class);
    ReentrantLock lock = Threading.lock("activemasternode");
    public static final int ACTIVE_MASTERNODE_INITIAL               =      0; // initial state
    public static final int ACTIVE_MASTERNODE_SYNC_IN_PROCESS       =      1;
    public static final int ACTIVE_MASTERNODE_INPUT_TOO_NEW         =      2;
    public static final int ACTIVE_MASTERNODE_NOT_CAPABLE           =      3;
    public static final int ACTIVE_MASTERNODE_STARTED               =      4;

    enum MasternodeType {
        MASTERNODE_UNKNOWN(0),
        MASTERNODE_REMOTE(1);

        int value;

        MasternodeType(int value)
        {
            this.value = value;
        }
    }

    MasternodeType type;
    boolean pingerEnabled;

    // sentinel ping data
    long sentinelPingTime;
    int  sentinelVersion;

    Context context;

    // Initialized by init.cpp
    // Keys for the main Masternode
    public PublicKey pubKeyMasternode;
    ECKey keyMasternode;

    // Initialized while registering Masternode
    public TransactionOutPoint outpoint;
    public MasternodeAddress service;

    public int state;
    public String notCapableReason;

    public ActiveMasternode(Context context)
    {
        this.context = context;
        type = MasternodeType.MASTERNODE_UNKNOWN;
        state = ACTIVE_MASTERNODE_INITIAL;
        pubKeyMasternode = new PublicKey(context.getParams());
        keyMasternode = new ECKey();
        outpoint = new TransactionOutPoint(context.getParams(), 0, Sha256Hash.ZERO_HASH);
        pingerEnabled = false;
    }

    public void manageState()
    {
        log.info("masternode--CActiveMasternode::ManageState -- Start");
        if(!DarkCoinSystem.fMasterNode) {
            log.info("masternode--CActiveMasternode::ManageState -- Not a masternode, returning");
            return;
        }

        if(context.getParams().getId() != NetworkParameters.ID_REGTEST && !context.masternodeSync.isBlockchainSynced()) {
            state = ACTIVE_MASTERNODE_SYNC_IN_PROCESS;
            log.info("CActiveMasternode::ManageState -- {}: {}", getStateString(), getStatus());
            return;
        }

        if(state == ACTIVE_MASTERNODE_SYNC_IN_PROCESS) {
            state = ACTIVE_MASTERNODE_INITIAL;
        }

        log.info("masternode--CActiveMasternode::ManageState -- status = {}, type = {}, pinger enabled = {}", getStatus(), getTypeString(), pingerEnabled);

        if(type == MasternodeType.MASTERNODE_UNKNOWN) {
            manageStateInitial();
        }

        if(type == MasternodeType.MASTERNODE_REMOTE) {
            manageStateRemote();
        }

        sendMasternodePing();
    }

    String getStateString()
    {
        switch (state) {
            case ACTIVE_MASTERNODE_INITIAL:         return "INITIAL";
            case ACTIVE_MASTERNODE_SYNC_IN_PROCESS: return "SYNC_IN_PROCESS";
            case ACTIVE_MASTERNODE_INPUT_TOO_NEW:   return "INPUT_TOO_NEW";
            case ACTIVE_MASTERNODE_NOT_CAPABLE:     return "NOT_CAPABLE";
            case ACTIVE_MASTERNODE_STARTED:         return "STARTED";
            default:                                return "UNKNOWN";
        }
    }

    String getStatus()
    {
        switch (state) {
            case ACTIVE_MASTERNODE_INITIAL:         return "Node just started, not yet activated";
            case ACTIVE_MASTERNODE_SYNC_IN_PROCESS: return "Sync in progress. Must wait until sync is complete to start Masternode";
            case ACTIVE_MASTERNODE_INPUT_TOO_NEW:   return String.format("Masternode input must have at least %d confirmations", context.getParams().getMasternodeMinimumConfirmations());
            case ACTIVE_MASTERNODE_NOT_CAPABLE:     return "Not capable masternode: " + notCapableReason;
            case ACTIVE_MASTERNODE_STARTED:         return "Masternode successfully started";
            default:                                return "Unknown";
        }
    }

    String getTypeString()
    {
        String strType;
        switch(type) {
            case MASTERNODE_REMOTE:
                strType = "REMOTE";
                break;
            default:
                strType = "UNKNOWN";
                break;
        }
        return strType;
    }

    boolean sendMasternodePing()
    {
        if(!pingerEnabled) {
            log.info("masternode--CActiveMasternode::SendMasternodePing -- {}: masternode ping service is disabled, skipping...", getStateString());
            return false;
        }

        if(!context.masternodeManager.has(outpoint)) {
            notCapableReason = "Masternode not in masternode list";
            state = ACTIVE_MASTERNODE_NOT_CAPABLE;
            log.info("CActiveMasternode::SendMasternodePing -- {}: {}", getStateString(), notCapableReason);
            return false;
        }

        try {
            MasternodePing mnp = new MasternodePing(context, outpoint);
            mnp.sentinelVersion = sentinelVersion;
            mnp.sentinelIsCurrent =
                    (Math.abs(Utils.currentTimeSeconds() - sentinelPingTime) < MASTERNODE_SENTINEL_MAX_SECONDS);
            if (!mnp.sign(keyMasternode, pubKeyMasternode)) {
                log.info("CActiveMasternode::SendMasternodePing -- ERROR: Couldn't sign Masternode Ping");
                return false;
            }

            // Update lastPing for our masternode in Masternode list
            if (context.masternodeManager.isMasternodePingedWithin(outpoint, MASTERNODE_MIN_MNP_SECONDS, mnp.sigTime)) {
                log.info("CActiveMasternode::SendMasternodePing -- Too early to send Masternode Ping");
                return false;
            }

            context.masternodeManager.setMasternodeLastPing(outpoint, mnp);

            log.info("CActiveMasternode::SendMasternodePing -- Relaying ping, collateral={}", outpoint.toStringShort());
            mnp.relay();
        } catch (BlockStoreException x) {
            log.error("CActiveMasternode::SendMasternodePing -- cannot determine blockHash");
            return false;
        }
        return true;
    }

    boolean updateSentinelPing(int version)
    {
        sentinelVersion = version;
        sentinelPingTime = Utils.currentTimeSeconds();

        return true;
    }

    void manageStateInitial()
    {
        log.info("masternode--CActiveMasternode::ManageStateInitial -- status = {}, type = {}, pinger enabled = {}", getStatus(), getTypeString(), pingerEnabled);

        // Check that our local network configuration is correct
        /*if (!fListen) {
            // listen option is probably overwritten by smth else, no good
            state = ACTIVE_MASTERNODE_NOT_CAPABLE;
            notCapableReason = "Masternode must accept connections from outside. Make sure listen configuration option is not overwritten by some another parameter.";
            log.info("CActiveMasternode::ManageStateInitial -- %s: %s\n", GetStateString(), notCapableReason);
            return;
        }*/

        // First try to find whatever local address is specified by externalip option
        /* TODO:fix this for masternodes
        boolean fFoundLocal = GetLocal(service) && Masternode.isValidNetAddr(service);
        if(!fFoundLocal) {
            boolean empty = true;

            // If we have some peers, let's try to find our local address from one of them
            for (Peer peer : context.peerGroup.getConnectedPeers()) {
                empty = false;
                if(peer.getAddress().getAddr().isIPv4())
                    fFoundLocal = GetLocal(service, & pnode -> addr) &&CMasternode::IsValidNetAddr (service);
                return !fFoundLocal;
            }
            // nothing and no live connections, can't do anything for now
            if (empty) {
                state = ACTIVE_MASTERNODE_NOT_CAPABLE;
                notCapableReason = "Can't detect valid external address. Will retry when there are some connections available.";
                log.info("CActiveMasternode::ManageStateInitial -- {}: {}", getStateString(), notCapableReason);
                return;
            }
        }

        if(!fFoundLocal) {
            state = ACTIVE_MASTERNODE_NOT_CAPABLE;
            notCapableReason = "Can't detect valid external address. Please consider using the externalip configuration option if problem persists. Make sure to use IPv4 address only.";
            log.info("CActiveMasternode::ManageStateInitial -- {}: {}", getStateString(), notCapableReason);
            return;
        }*/

        int mainnetDefaultPort = MainNetParams.get().getPort();
        if(context.getParams().getId() == NetworkParameters.ID_MAINNET) {
            if(service.getPort() != mainnetDefaultPort) {
                state = ACTIVE_MASTERNODE_NOT_CAPABLE;
                notCapableReason = String.format("Invalid port: {} - only {} is supported on mainnet.", service.getPort(), mainnetDefaultPort);
                log.info("CActiveMasternode::ManageStateInitial -- {}: {}", getStateString(), notCapableReason);
                return;
            }
        } else if(service.getPort() == mainnetDefaultPort) {
            state = ACTIVE_MASTERNODE_NOT_CAPABLE;
            notCapableReason = String.format("Invalid port: {} - {} is only supported on mainnet.", service.getPort(), mainnetDefaultPort);
            log.info("CActiveMasternode::ManageStateInitial -- {}: {}", getStateString(), notCapableReason);
            return;
        }

        log.info("CActiveMasternode::ManageStateInitial -- Checking inbound connection to '{}'", service.toString());

        if(null == context.peerGroup.connectTo(new PeerAddress(service.getSocketAddress()), true, 1000)) {
            state = ACTIVE_MASTERNODE_NOT_CAPABLE;
            notCapableReason = "Could not connect to " + service.toString();
            log.info("CActiveMasternode::ManageStateInitial -- {}: {}", getStateString(), notCapableReason);
            return;
        }

        // Default to REMOTE
        type = MasternodeType.MASTERNODE_REMOTE;

        log.info("masternode--CActiveMasternode::ManageStateInitial -- End status = {}, type = {}, pinger enabled = {}", getStatus(), getTypeString(), pingerEnabled);
    }

    void manageStateRemote()
    {
        log.info("masternode--CActiveMasternode::ManageStateRemote -- Start status = {}, type = {}, pinger enabled = {}, pubKeyMasternode.GetID() = {}",
                getStatus(), getTypeString(), pingerEnabled, pubKeyMasternode.toString());

        context.masternodeManager.checkMasternode(pubKeyMasternode, true);
        MasternodeInfo infoMn;
        if(null != (infoMn = context.masternodeManager.getMasternodeInfo(pubKeyMasternode))) {
            if(infoMn.nProtocolVersion != NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion()) {
                state = ACTIVE_MASTERNODE_NOT_CAPABLE;
                notCapableReason = "Invalid protocol version";
                log.info("CActiveMasternode::ManageStateRemote -- {}: {}", getStateString(), notCapableReason);
                return;
            }
            if(service != infoMn.address) {
                state = ACTIVE_MASTERNODE_NOT_CAPABLE;
                notCapableReason = "Broadcasted IP doesn't match our external address. Make sure you issued a new broadcast if IP of this masternode changed recently.";
                log.info("CActiveMasternode::ManageStateRemote -- {}: {}", getStateString(), notCapableReason);
                return;
            }
            if(!Masternode.isValidStateForAutoStart(infoMn.activeState)) {
                state = ACTIVE_MASTERNODE_NOT_CAPABLE;
                notCapableReason = String.format("Masternode in %s state", Masternode.stateToString(infoMn.activeState));
                log.info("CActiveMasternode::ManageStateRemote -- {}: {}", getStateString(), notCapableReason);
                return;
            }
            if(state != ACTIVE_MASTERNODE_STARTED) {
                log.info("CActiveMasternode::ManageStateRemote -- STARTED!");
                outpoint = infoMn.outpoint;
                service = infoMn.address;
                pingerEnabled = true;
                state = ACTIVE_MASTERNODE_STARTED;
            }
        }
        else {
            state = ACTIVE_MASTERNODE_NOT_CAPABLE;
            notCapableReason = "Masternode not in masternode list";
            log.info("CActiveMasternode::ManageStateRemote -- {}: {}", getStateString(), notCapableReason);
        }
    }

}
