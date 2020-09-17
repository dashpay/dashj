package org.bitcoinj.core;

import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.SporkUpdatedEventListener;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Created by Hash Engineering on 2/20/2016.
 */
public class SporkManager {
    private static final Logger log = LoggerFactory.getLogger(SporkManager.class);

    public static final int SPORK_2_INSTANTSEND_ENABLED                            = 10001;
    public static final int SPORK_3_INSTANTSEND_BLOCK_FILTERING                    = 10002;
    public static final int SPORK_5_INSTANTSEND_MAX_VALUE                          = 10004;
    public static final int SPORK_6_NEW_SIGS                                       = 10005;
    public static final int SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT                 = 10007;
    public static final int SPORK_9_SUPERBLOCKS_ENABLED                            = 10008;
    public static final int SPORK_10_MASTERNODE_PAY_UPDATED_NODES                  = 10009;
    public static final int SPORK_12_RECONSIDER_BLOCKS                             = 10011;
    public static final int SPORK_14_REQUIRE_SENTINEL_FLAG                         = 10013;
    public static final int SPORK_15_DETERMINISTIC_MNS_ENABLED                     = 10014;
    public static final int SPORK_16_INSTANTSEND_AUTOLOCKS                         = 10015;
    public static final int SPORK_17_QUORUM_DKG_ENABLED                            = 10016;
    public static final int SPORK_18_QUORUM_DEBUG_ENABLED                          = 10017;
    public static final int SPORK_19_CHAINLOCKS_ENABLED                            = 10018;
    public static final int SPORK_20_INSTANTSEND_LLMQ_BASED                        = 10019;
    public static final int SPORK_21_QUORUM_ALL_CONNECTED                          = 10020;
    public static final int SPORK_22_PS_MORE_PARTICIPANTS                          = 10021;


    static final int SPORK_START = SPORK_2_INSTANTSEND_ENABLED;
    static final int SPORK_END   = SPORK_22_PS_MORE_PARTICIPANTS;

    private static HashMap<Integer, Long> mapSporkDefaults;
    static {
        mapSporkDefaults = new HashMap<Integer, Long>();
        mapSporkDefaults.put(SPORK_2_INSTANTSEND_ENABLED, 0L);
        mapSporkDefaults.put(SPORK_3_INSTANTSEND_BLOCK_FILTERING, 0L);
        mapSporkDefaults.put(SPORK_6_NEW_SIGS, 4070908800L); // obsolete, but still used in Governance code
        mapSporkDefaults.put(SPORK_9_SUPERBLOCKS_ENABLED, 4070908800L);
        mapSporkDefaults.put(SPORK_17_QUORUM_DKG_ENABLED,            4070908800L);// OFF
        mapSporkDefaults.put(SPORK_18_QUORUM_DEBUG_ENABLED,          4070908800L); // OFF
        mapSporkDefaults.put(SPORK_19_CHAINLOCKS_ENABLED,            4070908800L); // OFF
        mapSporkDefaults.put(SPORK_21_QUORUM_ALL_CONNECTED,          4070908800L); // OFF
        mapSporkDefaults.put(SPORK_22_PS_MORE_PARTICIPANTS, 4070908800L); // OFF
    }

    MasternodeSignature sig;
    ECKey sporkPrivKey;
    byte [] sporkPubKeyId;

    HashMap<Sha256Hash, SporkMessage> mapSporks;
    HashMap<Integer, SporkMessage> mapSporksActive;

    AbstractBlockChain blockChain;
    Context context;

    SporkManager(Context context)
    {
        this.context = context;
        mapSporks = new HashMap<Sha256Hash, SporkMessage>();
        mapSporksActive = new HashMap<Integer, SporkMessage>();
        setSporkAddress(context.getParams().getSporkAddress());
        eventListeners = new CopyOnWriteArrayList<ListenerRegistration<SporkUpdatedEventListener>>();
    }

    void setBlockChain(AbstractBlockChain blockChain, @Nullable PeerGroup peerGroup) {
        this.blockChain = blockChain;
        if (peerGroup != null) {
            peerGroup.addConnectedEventListener(peerConnectedEventListener);
        }
    }

    public void close(PeerGroup peerGroup) {
        peerGroup.removeConnectedEventListener(peerConnectedEventListener);
    }

    void processSpork(Peer from, SporkMessage spork) {
        if (context.isLiteMode() && !context.allowInstantXinLiteMode()) return; //disable all darksend/masternode related functionality

            Sha256Hash hash = spork.getHash();
            if (mapSporksActive.containsKey(spork.nSporkID)) {
                if (mapSporksActive.get(spork.nSporkID).nTimeSigned >= spork.nTimeSigned) {
                    log.info("spork - seen "+ String.format("%1$35s",getSporkNameByID(spork.nSporkID)) +" block " + blockChain.getBestChainHeight() + "hash: " + hash.toString());
                    return;
                } else {
                    log.info("received updated spork "+ String.format("%1$35s",getSporkNameByID(spork.nSporkID)) +" block " +blockChain.getBestChainHeight() +
                            "hash: " + hash.toString());
                }
            }

            log.info("spork - new ID "+spork.nSporkID+" "+ String.format("%1$35s",getSporkNameByID(spork.nSporkID)) +" Time "+spork.nTimeSigned+" bestHeight " + blockChain.getBestChainHeight() +
                    "hash: " + hash.toString());

            if (!spork.checkSignature(sporkPubKeyId)) {
                log.info("spork - invalid signature");
                return;
            }

            mapSporks.put(hash, spork);
            mapSporksActive.put(spork.nSporkID, spork);
            queueOnUpdate(spork);
            relay(spork);

            //does a task if needed
            executeSpork(spork.nSporkID, spork.nValue);
    }

    public void processSporkForUnitTesting(int spork) {
        SporkMessage sporkMessage = new SporkMessage(context.getParams());
        sporkMessage.nSporkID = spork;
        mapSporks.put(Sha256Hash.ZERO_HASH, sporkMessage);
        mapSporksActive.put(sporkMessage.nSporkID, sporkMessage);
    }
    /*
    void processGetSporks(GetSporksMessage m)
    {
        if (strCommand == "getsporks")
        {
            std::map<int, CSporkMessage>::iterator it = mapSporksActive.begin();

            while(it != mapSporksActive.end()) {
                pfrom->PushMessage("spork", it->second);
                it++;
            }
        }

    }*/




    // grab the spork, otherwise say it's off
    public boolean isSporkActive(int nSporkID)
    {
        long r = -1;

        if(mapSporksActive.containsKey(nSporkID)){
            r = mapSporksActive.get(nSporkID).nValue;
        } else if (mapSporkDefaults.containsKey(nSporkID)) {
            r = mapSporkDefaults.get(nSporkID);
        } else {
            log.warn("isSporkActive:  Unknown Spork ID {}", nSporkID);
            r = 4070908800L; // 2099-1-1 i.e. off by default
        }

        return r < Utils.currentTimeSeconds();
    }

    // grab the value of the spork on the network, or the default
    public long getSporkValue(int nSporkID)
    {
        if (mapSporksActive.containsKey(nSporkID))
            return mapSporksActive.get(nSporkID).nValue;

        if (mapSporkDefaults.containsKey(nSporkID)) {
            return mapSporkDefaults.get(nSporkID);
        }

        log.info("getSporkValue:  Unknown Spork ID {}", nSporkID);
        return -1;
    }

    public void executeSpork(int nSporkID, long nValue)
    {

    }

    boolean updateSpork(int nSporkID, long nValue)
    {
        SporkMessage msg = new SporkMessage(context.getParams());
        msg.nSporkID = nSporkID;
        msg.nValue = nValue;
        msg.nTimeSigned = Utils.currentTimeSeconds();
        if(msg.sign(sporkPrivKey)){
            relay(msg);
            mapSporks.put(msg.getHash(), msg);
            mapSporksActive.put(nSporkID, msg);
            return true;
        }

        return false;
    }

    void relay(SporkMessage msg)
    {
        //InventoryItem inv = new InventoryItem(InventoryItem.Type.Spork, msg.getHash());
        //RelayInv(inv);
        //TODO:  Not needed in SPV
    }

    public boolean setPrivKey(String strPrivKey) {
        StringBuilder errorMessage  = new StringBuilder();
        ECKey key = MessageSigner.getKeysFromSecret(strPrivKey, errorMessage);
        ECKey pubkey = ECKey.fromPublicOnly(key.getPubKey());

        if (key == null) {
            log.error("setPrivKey -- Failed to parse private key");
            return false;
        }

        if (!pubkey.getPubKeyHash().equals(sporkPubKeyId)) {
            log.error("setPrivKey -- New private key does not belong to spork address\n");
            return false;
        }

        SporkMessage spork = new SporkMessage(context.getParams());
        if (spork.sign(key)) {
            // Test signing successful, proceed
            log.info("setPrivKey -- Successfully initialized as spork signer");

            sporkPrivKey = key;
            return true;
        } else {
            log.error("setPrivKey -- Test signing failed");
            return false;
        }
    }


    public int getSporkIDByName(String strName)
    {
        if (strName.equals("SPORK_2_INSTANTSEND_ENABLED"))               return SPORK_2_INSTANTSEND_ENABLED;
        if (strName.equals("SPORK_3_INSTANTSEND_BLOCK_FILTERING"))       return SPORK_3_INSTANTSEND_BLOCK_FILTERING;
        if (strName.equals("SPORK_5_INSTANTSEND_MAX_VALUE"))             return SPORK_5_INSTANTSEND_MAX_VALUE;
        if (strName.equals("SPORK_6_NEW_SIGS"))                          return SPORK_6_NEW_SIGS;
        if (strName.equals("SPORK_9_SUPERBLOCKS_ENABLED"))               return SPORK_9_SUPERBLOCKS_ENABLED;
        if (strName.equals("SPORK_10_MASTERNODE_PAY_UPDATED_NODES"))     return SPORK_10_MASTERNODE_PAY_UPDATED_NODES;
        if (strName.equals("SPORK_12_RECONSIDER_BLOCKS"))                return SPORK_12_RECONSIDER_BLOCKS;
        if (strName.equals("SPORK_14_REQUIRE_SENTINEL_FLAG"))            return SPORK_14_REQUIRE_SENTINEL_FLAG;
        if (strName.equals("SPORK_15_DETERMINISTIC_MNS_ENABLED"))        return SPORK_15_DETERMINISTIC_MNS_ENABLED;
        if (strName.equals("SPORK_16_INSTANTSEND_AUTOLOCKS"))            return SPORK_16_INSTANTSEND_AUTOLOCKS;
        if (strName.equals("SPORK_17_QUORUM_DKG_ENABLED"))               return SPORK_17_QUORUM_DKG_ENABLED;
        if (strName.equals("SPORK_18_QUORUM_DEBUG_ENABLED"))             return SPORK_18_QUORUM_DEBUG_ENABLED;
        if (strName.equals("SPORK_19_CHAINLOCKS_ENABLED"))               return SPORK_19_CHAINLOCKS_ENABLED;
        if (strName.equals("SPORK_20_INSTANTSEND_LLMQ_BASED"))           return SPORK_20_INSTANTSEND_LLMQ_BASED;
        if (strName.equals("SPORK_21_QUORUM_ALL_CONNECTED"))             return SPORK_21_QUORUM_ALL_CONNECTED;
        if (strName.equals("SPORK_22_PS_MORE_PARTICIPANTS"))             return SPORK_22_PS_MORE_PARTICIPANTS;

        return -1;
    }

    public String getSporkNameByID(int id)
    {
        switch (id) {
            case SPORK_2_INSTANTSEND_ENABLED:               return "SPORK_2_INSTANTSEND_ENABLED";
            case SPORK_3_INSTANTSEND_BLOCK_FILTERING:       return "SPORK_3_INSTANTSEND_BLOCK_FILTERING";
            case SPORK_5_INSTANTSEND_MAX_VALUE:             return "SPORK_5_INSTANTSEND_MAX_VALUE";
            case SPORK_6_NEW_SIGS:                          return "SPORK_6_NEW_SIGS";
            case SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT:    return "SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT";
            case SPORK_9_SUPERBLOCKS_ENABLED:               return "SPORK_9_SUPERBLOCKS_ENABLED";
            case SPORK_10_MASTERNODE_PAY_UPDATED_NODES:     return "SPORK_10_MASTERNODE_PAY_UPDATED_NODES";
            case SPORK_12_RECONSIDER_BLOCKS:                return "SPORK_12_RECONSIDER_BLOCKS";
            case SPORK_14_REQUIRE_SENTINEL_FLAG:            return "SPORK_14_REQUIRE_SENTINEL_FLAG";
            case SPORK_15_DETERMINISTIC_MNS_ENABLED:        return "SPORK_15_DETERMINISTIC_MNS_ENABLED";
            case SPORK_16_INSTANTSEND_AUTOLOCKS:            return "SPORK_16_INSTANTSEND_AUTOLOCKS";
            case SPORK_17_QUORUM_DKG_ENABLED:               return "SPORK_17_QUORUM_DKG_ENABLED";
            case SPORK_18_QUORUM_DEBUG_ENABLED:             return "SPORK_18_QUORUM_DEBUG_ENABLED";
            case SPORK_19_CHAINLOCKS_ENABLED:               return "SPORK_19_CHAINLOCKS_ENABLED";
            case SPORK_20_INSTANTSEND_LLMQ_BASED:           return "SPORK_20_INSTANTSEND_LLMQ_BASED";
            case SPORK_21_QUORUM_ALL_CONNECTED:             return "SPORK_21_QUORUM_ALL_CONNECTED";
            case SPORK_22_PS_MORE_PARTICIPANTS:             return "SPORK_22_PS_MORE_PARTICIPANTS";
            default:
                return "Unknown";
        }
    }

    boolean setSporkAddress(String strAddress) {
        try {
            Address address = Address.fromBase58(context.getParams(), strAddress);
            sporkPubKeyId = address.getHash();
        } catch (AddressFormatException x) {
            log.error("Failed to parse spork address");
            return false;
        }
        return true;
    }

    public PeerConnectedEventListener peerConnectedEventListener = new PeerConnectedEventListener() {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            // SPORK : ALWAYS ASK FOR SPORKS AS WE SYNC (we skip this mode now)
            if (!peer.hasFulfilledRequest("spork-sync")) {
                peer.fulfilledRequest("spork-sync");

                peer.sendMessage(new GetSporksMessage(context.getParams())); //get current network sporks
            }
        }
    };

    private transient CopyOnWriteArrayList<ListenerRegistration<SporkUpdatedEventListener>> eventListeners;

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. Runs the listener methods in the user thread.
     */
    public void addEventListener(SporkUpdatedEventListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when something interesting happens,
     * like receiving money. The listener is executed by the given executor.
     */
    public void addEventListener(SporkUpdatedEventListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        eventListeners.add(new ListenerRegistration<SporkUpdatedEventListener>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeEventListener(SporkUpdatedEventListener listener) {
        //keychain.removeEventListener(listener);
        return ListenerRegistration.removeFromList(listener, eventListeners);
    }

    public void queueOnUpdate(final SporkMessage spork) {
        //checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<SporkUpdatedEventListener> registration : eventListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onSporkUpdated(spork);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onSporkUpdated(spork);
                    }
                });
            }
        }
    }

    public List<SporkMessage> getSporks() {
        List<SporkMessage> sporkList = new ArrayList<SporkMessage>(mapSporks.size());
        for (Map.Entry<Sha256Hash, SporkMessage> entry : mapSporks.entrySet()) {
            sporkList.add(entry.getValue());
        }
        return sporkList;
    }
}
