/*
 * Copyright 2016 Hash Engineering Solutions
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 *
 * Handles SporkMessages and their verification
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

    private static final HashMap<Integer, Long> mapSporkDefaults;
    static {
        mapSporkDefaults = new HashMap<>();
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

    byte [] sporkPubKeyId;

    private HashMap<Sha256Hash, SporkMessage> mapSporks;
    private HashMap<Integer, SporkMessage> mapSporksActive;

    private AbstractBlockChain blockChain;
    private Context context;

    public SporkManager(Context context)
    {
        this.context = context;
        mapSporks = new HashMap<>();
        mapSporksActive = new HashMap<>();
        setSporkAddress(context.getParams().getSporkAddress());
        eventListeners = new CopyOnWriteArrayList<>();
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
        if (context.isLiteMode() && !context.allowInstantXinLiteMode()) {
            return; //disable all darksend/masternode related functionality
        }

        Sha256Hash hash = spork.getHash();
        if (mapSporksActive.containsKey(spork.getSporkId())) {
            if (mapSporksActive.get(spork.getSporkId()).getTimeSigned() >= spork.getTimeSigned()) {
                log.info("spork - seen " + String.format("%1$35s", getSporkNameById(spork.getSporkId())) +" block " + blockChain.getBestChainHeight() + " hash: " + hash.toString());
                return;
            } else {
                log.info("received updated spork "+ String.format("%1$35s", getSporkNameById(spork.getSporkId())) +" block " +blockChain.getBestChainHeight() +
                        " hash: " + hash.toString());
            }
        }

        log.info("spork - new ID " + spork.getSporkId() + " " + String.format("%1$35s", getSporkNameById(spork.getSporkId())) +" Time "+spork.getTimeSigned()+" bestHeight " + blockChain.getBestChainHeight() +
                " hash: " + hash.toString());

        if (!spork.checkSignature(sporkPubKeyId)) {
            log.info("spork - invalid signature");
            return;
        }

        mapSporks.put(hash, spork);
        mapSporksActive.put(spork.getSporkId(), spork);
        queueOnUpdate(spork);

        //does a task if needed
        executeSpork(spork.getSporkId(), spork.getValue());
    }

    public void processSporkForUnitTesting(int spork) {
        SporkMessage sporkMessage = new SporkMessage(context.getParams(), spork, 0, 0);
        mapSporks.put(Sha256Hash.ZERO_HASH, sporkMessage);
        mapSporksActive.put(spork, sporkMessage);
    }

    // grab the spork, otherwise say it's off
    public boolean isSporkActive(int sporkId)
    {
        long r;

        if(mapSporksActive.containsKey(sporkId)){
            r = mapSporksActive.get(sporkId).getValue();
        } else if (mapSporkDefaults.containsKey(sporkId)) {
            r = mapSporkDefaults.get(sporkId);
        } else {
            log.warn("isSporkActive:  Unknown Spork ID {}", sporkId);
            r = 4070908800L; // 2099-1-1 i.e. off by default
        }

        return r < Utils.currentTimeSeconds();
    }

    // grab the value of the spork on the network, or the default
    public long getSporkValue(int sporkId)
    {
        if (mapSporksActive.containsKey(sporkId))
            return mapSporksActive.get(sporkId).getValue();

        if (mapSporkDefaults.containsKey(sporkId)) {
            return mapSporkDefaults.get(sporkId);
        }

        log.info("getSporkValue:  Unknown Spork ID {}", sporkId);
        return -1;
    }

    public void executeSpork(int sporkId, long value)
    {

    }

    public int getSporkIdByName(String strName)
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

        return -1;
    }

    public String getSporkNameById(int id)
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

    void setSporkAddress(String strAddress) {
        try {
            Address address = Address.fromBase58(context.getParams(), strAddress);
            sporkPubKeyId = address.getHash();
        } catch (AddressFormatException x) {
            log.error("Failed to parse spork address");
        }
    }

    /**
     * When connecting to a peer, request their sporks
     */
    public final PeerConnectedEventListener peerConnectedEventListener = new PeerConnectedEventListener() {
        @Override
        public void onPeerConnected(Peer peer, int peerCount) {
            if (!peer.hasFulfilledRequest("spork-sync")) {
                peer.fulfilledRequest("spork-sync");
                //get current network sporks
                peer.sendMessage(new GetSporksMessage(context.getParams()));
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
        eventListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeEventListener(SporkUpdatedEventListener listener) {
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
        List<SporkMessage> sporkList = new ArrayList<>(mapSporks.size());
        for (Map.Entry<Sha256Hash, SporkMessage> entry : mapSporks.entrySet()) {
            sporkList.add(entry.getValue());
        }
        return sporkList;
    }

    public boolean hasSpork(Sha256Hash hashSpork) {
        return mapSporks.containsKey(hashSpork);
    }
}
