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
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Created by Hash Engineering on 2/20/2016.
 *
 * Handles SporkMessages and their verification
 */
public class SporkManager {
    private static final Logger log = LoggerFactory.getLogger(SporkManager.class);
    ReentrantLock lock = Threading.lock("SporkManager");

    private static final HashMap<SporkId, SporkDefinition> mapSporkDefaults = new HashMap<>();
    private static void makeSporkDefinition(SporkId sporkId, long defaultValue) {
        mapSporkDefaults.put(sporkId, new SporkDefinition(sporkId, defaultValue));
    }
    static {
        makeSporkDefinition(SporkId.SPORK_2_INSTANTSEND_ENABLED, 4070908800L); // OFF
        makeSporkDefinition(SporkId.SPORK_3_INSTANTSEND_BLOCK_FILTERING, 4070908800L); // OFF
        makeSporkDefinition(SporkId.SPORK_9_SUPERBLOCKS_ENABLED, 4070908800L); // OFF
        makeSporkDefinition(SporkId.SPORK_17_QUORUM_DKG_ENABLED, 4070908800L); // OFF
        makeSporkDefinition(SporkId.SPORK_19_CHAINLOCKS_ENABLED, 4070908800L); // OFF
        makeSporkDefinition(SporkId.SPORK_21_QUORUM_ALL_CONNECTED,4070908800L); // OFF
        makeSporkDefinition(SporkId.SPORK_23_QUORUM_POSE, 4070908800L); // OFF
    }

    int minSporkKeys;

    @GuardedBy("lock") private final HashMap<Sha256Hash, SporkMessage> mapSporksByHash;
    @GuardedBy("lock") private final HashMap<SporkId, Map<KeyId, SporkMessage>> mapSporksActive;
    @GuardedBy("lock") private final HashMap<SporkId, Boolean> mapSporksCachedActive;
    @GuardedBy("lock") private final HashMap<SporkId, Long> mapSporksCachedValues;
    @GuardedBy("lock") private final HashSet<KeyId> setSporkPubKeyIds = new HashSet<>();

    private AbstractBlockChain blockChain;
    private final Context context;

    public SporkManager(Context context)
    {
        this.context = context;
        mapSporksByHash = new HashMap<>();
        mapSporksActive = new HashMap<>();
        mapSporksCachedActive = new HashMap<>();
        mapSporksCachedValues = new HashMap<>();
        setSporkAddress(context.getParams().getSporkAddress());
        eventListeners = new CopyOnWriteArrayList<>();
        setMinSporkKeys(context.getParams().getMinSporkKeys());
    }

    void setBlockChain(AbstractBlockChain blockChain, @Nullable PeerGroup peerGroup) {
        this.blockChain = blockChain;
        if (peerGroup != null) {
            peerGroup.addConnectedEventListener(peerConnectedEventListener);
        }
    }

    public void clear() {
        mapSporksActive.clear();
        mapSporksByHash.clear();
    }

    public void close(PeerGroup peerGroup) {
        peerGroup.removeConnectedEventListener(peerConnectedEventListener);
    }

    void processSpork(Peer from, SporkMessage spork) {
        if (context.isLiteMode() && !context.allowInstantXinLiteMode()) {
            return; //disable all darksend/masternode related functionality
        }

        Sha256Hash hash = spork.getHash();
        String logMessage = String.format("SPORK -- hash: %s id: %d (%s) value: %10d bestHeight: %d peer=%s:%d",
                hash, spork.getSporkId().value, String.format("%1$35s", spork.getSporkId().name()),
                spork.getValue(), blockChain.getBestChainHeight(), from.getAddress().getAddr().toString(),
                from.getAddress().getPort());

        if (spork.getTimeSigned() > Utils.currentTimeSeconds() + 2 * 60 * 60) {
            log.info("processSpork -- ERROR: too far into the future");
            // need to reject this peer
            //throw new ProtocolException("spork too far into the future");
            return;
        }

        KeyId keyIdSigner = spork.getSignerKeyId();
        if (keyIdSigner == null || !setSporkPubKeyIds.contains(keyIdSigner)) {
            log.info("processSpork -- ERROR: invalid signature");
            // TODO: need to reject this peer?  for now old peers will give us old sporks?
            // throw new ProtocolException("spork has invalid signature");
            return;
        }

        if (mapSporksActive.containsKey(spork.getSporkId())) {
            if (mapSporksActive.get(spork.getSporkId()).containsKey(keyIdSigner)) {
                if (mapSporksActive.get(spork.getSporkId()).get(keyIdSigner).getTimeSigned() >= spork.getTimeSigned()) {
                    log.info("{} seen ", logMessage);
                    return;
                } else {
                    log.info("{} updated", logMessage);
                }
            } else {
                log.info("{} new signer", logMessage);
            }
        } else {
            log.info("{} new", logMessage);
        }

        mapSporksByHash.put(hash, spork);
        Map<KeyId, SporkMessage> mapKeyMessage = mapSporksActive.get(spork.getSporkId());
        if (mapKeyMessage != null) {
            mapKeyMessage.put(keyIdSigner, spork);
        } else {
            mapKeyMessage = new HashMap<>();
            mapKeyMessage.put(keyIdSigner, spork);
            mapSporksActive.put(spork.getSporkId(), mapKeyMessage);
        }
        mapSporksCachedActive.remove(spork.getSporkId());
        mapSporksCachedValues.remove(spork.getSporkId());
        queueOnUpdate(spork);
    }

    public synchronized void checkAndRemove() {
        Iterator<Map.Entry<SporkId, Map<KeyId, SporkMessage>>> itActive = mapSporksActive.entrySet().iterator();
        while (itActive.hasNext()) {
            Map.Entry<SporkId, Map<KeyId, SporkMessage>> entry = itActive.next();
            Iterator<Map.Entry<KeyId, SporkMessage>> itSignerPair = entry.getValue().entrySet().iterator();
            while (itSignerPair.hasNext()) {
                Map.Entry<KeyId, SporkMessage> signerEntry = itSignerPair.next();
                boolean fHasValidSig = setSporkPubKeyIds.contains(signerEntry.getKey()) &&
                        signerEntry.getValue().checkSignature(signerEntry.getKey().getBytes());
                if (!fHasValidSig) {
                    mapSporksByHash.remove(signerEntry.getValue().getHash());
                    itSignerPair.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                itActive.remove();
            }
        }

        Iterator<Map.Entry<Sha256Hash, SporkMessage>> itByHash = mapSporksByHash.entrySet().iterator();
        while (itByHash.hasNext()) {
            Map.Entry<Sha256Hash, SporkMessage> entry = itByHash.next();
            boolean found = false;
            for (KeyId signer: setSporkPubKeyIds) {
                if (entry.getValue().checkSignature(signer.getBytes())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                itByHash.remove();
            }
        }
    }

    @Deprecated
    public void processSporkForUnitTesting(SporkId spork) {
        SporkMessage sporkMessage = new SporkMessage(context.getParams(), spork, 0, 0);
        mapSporksByHash.put(Sha256Hash.ZERO_HASH, sporkMessage);
        HashMap<KeyId, SporkMessage> mapKeyMessage = new HashMap<>();
        mapKeyMessage.put(KeyId.KEYID_ZERO, sporkMessage);
        mapSporksActive.put(spork, mapKeyMessage);
    }

    // grab the spork, otherwise say it's off
    public boolean isSporkActive(SporkId sporkId) {
        long r;
        // If sporkId is cached, and the cached value is true, then return early true
        Boolean it = mapSporksCachedActive.get(sporkId);
        if (it != null && it) {
            return true;
        }

        long sporkValue = getSporkValue(sporkId);
        boolean result = sporkValue < Utils.currentTimeSeconds();

        // Only cache true values
        if (result) {
            mapSporksCachedActive.put(sporkId, result);
        }
        return result;
    }

    Pair<Boolean, Long> sporkValueIsActive(SporkId sporkId) {
        if (!mapSporksActive.containsKey(sporkId)) {
            return new Pair<>(false, 0L);
        }

        Long value = mapSporksCachedValues.get(sporkId);
        if (value != null) {
            return new Pair<>(true, value);
        }

        // calc how many values we have and how many signers vote for every value
        HashMap<Long, Integer> mapValueCounts = new HashMap<>();
        for (Map.Entry<KeyId, SporkMessage> entry: mapSporksActive.get(sporkId).entrySet()) {
            long entryValue = entry.getValue().getValue();

            mapValueCounts.merge(entryValue, 1, Integer::sum);

            if (mapValueCounts.get(entryValue) >= minSporkKeys) {
                // minSporkKeys is always more than the half of the max spork keys number,
                // so there is only one such value and we can stop here
                mapSporksCachedValues.put(sporkId, entryValue);
                return new Pair<>(true, entryValue);
            }
        }

        return new Pair<>(false, 0L);
    }

    // grab the value of the spork on the network, or the default
    public long getSporkValue(SporkId sporkId)
    {
        long sporkValue = -1L;
        Pair<Boolean, Long> pair = sporkValueIsActive(sporkId);
        if (pair.getFirst()) {
            return pair.getSecond();
        }

        if (mapSporkDefaults.containsKey(sporkId)) {
            return mapSporkDefaults.get(sporkId).defaultValue;
        }

        log.info("getSporkValue:  Unknown Spork ID {}", sporkId);
        return -1;
    }

    public SporkId getSporkIdByName(String strName)
    {
        for (Map.Entry<SporkId, SporkDefinition> entry : mapSporkDefaults.entrySet()) {
            if (entry.getValue().name.equals(strName)) {
                return entry.getKey();
            }
        }
        log.info("getSporkIDByName -- Unknown Spork name '{}'", strName);
        return SporkId.SPORK_INVALID;
    }

    public SporkId getSporkByHash(Sha256Hash hash) {
        return mapSporksByHash.get(hash).getSporkId();
    }

    boolean setSporkAddress(String strAddress) {
        try {
            Address address = Address.fromBase58(context.getParams(), strAddress);
            KeyId sporkPubKeyId = new KeyId(address.getHash());
            setSporkPubKeyIds.add(sporkPubKeyId);
            return true;
        } catch (AddressFormatException x) {
            log.error("Failed to parse spork address");
        }
        return false;
    }

    boolean setMinSporkKeys(int minSporkKeys)
    {
        int maxKeysNumber = setSporkPubKeyIds.size();
        if ((minSporkKeys <= maxKeysNumber / 2) || (minSporkKeys > maxKeysNumber)) {
            log.info("setMinSporkKeys -- Invalid min spork signers number: {}", minSporkKeys);
            return false;
        }
        this.minSporkKeys = minSporkKeys;
        return true;
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
        List<SporkMessage> sporkList = new ArrayList<>(mapSporksByHash.size());
        for (Map.Entry<Sha256Hash, SporkMessage> entry : mapSporksByHash.entrySet()) {
            sporkList.add(entry.getValue());
        }
        return sporkList;
    }

    public boolean hasSpork(Sha256Hash hashSpork) {
        return mapSporksByHash.containsKey(hashSpork);
    }
}
