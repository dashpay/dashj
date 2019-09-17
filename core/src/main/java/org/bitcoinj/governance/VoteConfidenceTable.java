/*
 * Copyright 2012 Google Inc.
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

package org.bitcoinj.governance;

import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.utils.Threading;

import javax.annotation.Nullable;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * <p>Tracks GovernanceVotes that are being announced across the network. Typically one is created for you by a
 * {@link PeerGroup} and then given to each Peer to update. The current purpose is to let Peers update the confidence
 * (number of peers broadcasting). It helps address an attack scenario in which a malicious remote peer (or several)
 * feeds you invalid GovernanceVotes, eg, ones that spend coins which don't exist. If you don't see most of the peers
 * announce the GovernanceVote within a reasonable time, it may be that the TX is not valid. Alternatively, an attacker
 * may control your entire internet connection: in this scenario counting broadcasting peers does not help you.</p>
 *
 * <p>It is <b>not</b> at this time directly equivalent to the Dash Core memory pool, which tracks
 * all GovernanceVotes not currently included in the best chain - it's simply a cache.</p>
 */
public class VoteConfidenceTable {
    protected ReentrantLock lock = Threading.lock("txconfidencetable");

    private static class WeakConfidenceReference extends WeakReference<GovernanceVoteConfidence> {
        public Sha256Hash hash;
        public WeakConfidenceReference(GovernanceVoteConfidence confidence, ReferenceQueue<GovernanceVoteConfidence> queue) {
            super(confidence, queue);
            hash = confidence.getTransactionHash();
        }
    }
    private LinkedHashMap<Sha256Hash, WeakConfidenceReference> table;

    // This ReferenceQueue gets entries added to it when they are only weakly reachable, ie, the TxConfidenceTable is the
    // only thing that is tracking the confidence data anymore. We check it from time to time and delete table entries
    // corresponding to expired transactions. In this way memory usage of the system is in line with however many
    // GovernanceVotes you actually care to track the confidence of. We can still end up with lots of hashes being stored
    // if our peers flood us with invs but the MAX_SIZE param caps this.
    private ReferenceQueue<GovernanceVoteConfidence> referenceQueue;

    /** The max size of a table created with the no-args constructor. */
    public static final int MAX_SIZE = 1000;

    /**
     * Creates a table that will track at most the given number of transactions (allowing you to bound memory
     * usage).
     * @param size Max number of GovernanceVotes to track. The table will fill up to this size then stop growing.
     */
    public VoteConfidenceTable(final int size) {
        table = new LinkedHashMap<Sha256Hash, WeakConfidenceReference>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Sha256Hash, WeakConfidenceReference> entry) {
                // An arbitrary choice to stop the memory used by tracked GovernanceVotes getting too huge in the event
                // of some kind of DoS attack.
                return size() > size;
            }
        };
        referenceQueue = new ReferenceQueue<GovernanceVoteConfidence>();
    }

    /**
     * Creates a table that will track at most {@link VoteConfidenceTable#MAX_SIZE} entries. You should normally use
     * this constructor.
     */
    public VoteConfidenceTable() {
        this(MAX_SIZE);
    }

    /**
     * If any GovernanceVotes have expired due to being only weakly reachable through us, go ahead and delete their
     * table entries - it means we downloaded the GovernanceVote and sent it to various event listeners, none of
     * which bothered to keep a reference. Typically, this is because the GovernanceVote does not involve any keys that
     * are relevant to any of our wallets.
     */
    private void cleanTable() {
        lock.lock();
        try {
            Reference<? extends GovernanceVoteConfidence> ref;
            while ((ref = referenceQueue.poll()) != null) {
                // Find which GovernanceVote got deleted by the GC.
                WeakConfidenceReference txRef = (WeakConfidenceReference) ref;
                // And remove the associated map entry so the other bits of memory can also be reclaimed.
                table.remove(txRef.hash);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of peers that have seen the given hash recently.
     */
    public int numBroadcastPeers(Sha256Hash txHash) {
        lock.lock();
        try {
            cleanTable();
            WeakConfidenceReference entry = table.get(txHash);
            if (entry == null) {
                return 0;  // No such TX known.
            } else {
                GovernanceVoteConfidence confidence = entry.get();
                if (confidence == null) {
                    // Such a TX hash was seen, but nothing seemed to care so we ended up throwing away the data.
                    table.remove(txHash);
                    return 0;
                } else {
                    return confidence.numBroadcastPeers();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called by peers when they see a GovernanceVote advertised in an "inv" message. It passes the data on to the relevant
     * {@link GovernanceVoteConfidence} object, creating it if needed.
     *
     * @return the number of peers that have now announced this hash (including the caller)
     */
    public GovernanceVoteConfidence seen(Sha256Hash hash, PeerAddress byPeer) {
        GovernanceVoteConfidence confidence;
        boolean fresh = false;
        lock.lock();
        {
            cleanTable();
            confidence = getOrCreate(hash);
            fresh = confidence.markBroadcastBy(byPeer);
        }
        lock.unlock();
        if (fresh)
            confidence.queueListeners(GovernanceVoteConfidence.Listener.ChangeReason.SEEN_PEERS);
        return confidence;
    }

    /**
     * Returns the {@link GovernanceVoteConfidence} for the given hash if we have downloaded it, or null if that tx hash
     * is unknown to the system at this time.
     */
    public GovernanceVoteConfidence getOrCreate(Sha256Hash hash) {
        checkNotNull(hash);
        lock.lock();
        try {
            WeakConfidenceReference reference = table.get(hash);
            if (reference != null) {
                GovernanceVoteConfidence confidence = reference.get();
                if (confidence != null)
                    return confidence;
            }
            GovernanceVoteConfidence newConfidence = new GovernanceVoteConfidence(hash);
            table.put(hash, new WeakConfidenceReference(newConfidence, referenceQueue));
            return newConfidence;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the {@link GovernanceVoteConfidence} for the given hash if we have downloaded it, or null if that tx hash
     * is unknown to the system at this time.
     */
    @Nullable
    public GovernanceVoteConfidence get(Sha256Hash hash) {
        lock.lock();
        try {
            WeakConfidenceReference ref = table.get(hash);
            if (ref == null)
                return null;
            GovernanceVoteConfidence confidence = ref.get();
            if (confidence != null)
                return confidence;
            else
                return null;
        } finally {
            lock.unlock();
        }
    }
}
