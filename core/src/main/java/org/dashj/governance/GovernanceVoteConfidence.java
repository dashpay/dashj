/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

// TODO: Modify the getDepthInBlocks method to require the chain height to be specified, in preparation for ceasing to touch every tx on every block.

/**
 * <p>A TransactionConfidence object tracks data you can use to make a confidence decision about a transaction.
 * It also contains some pre-canned rules for common scenarios: if you aren't really sure what level of confidence
 * you need, these should prove useful. You can get a confidence object using {@link Transaction#getConfidence()}.
 * They cannot be constructed directly.</p>
 *
 * <p>Confidence in a transaction can come in multiple ways:</p>
 *
 * <ul>
 * <li>Because you created it yourself and only you have the necessary keys.</li>
 * <li>Receiving it from a fully validating peer you know is trustworthy, for instance, because it's run by yourself.</li>
 * <li>Receiving it from a peer on the network you randomly chose. If your network connection is not being
 *     intercepted, you have a pretty good chance of connecting to a node that is following the rules.</li>
 * <li>Receiving it from multiple peers on the network. If your network connection is not being intercepted,
 *     hearing about a transaction from multiple peers indicates the network has accepted the transaction and
 *     thus miners likely have too (miners have the final say in whether a transaction becomes valid or not).</li>
 * <li>Seeing the transaction appear appear in a block on the main chain. Your confidence increases as the transaction
 *     becomes further buried under work. Work can be measured either in blocks (roughly, units of time), or
 *     amount of work done.</li>
 * </ul>
 *
 * <p>Alternatively, you may know that the transaction is "dead", that is, one or more of its inputs have
 * been double spent and will never confirm unless there is another re-org.</p>
 *
 * To make a copy that won't be changed, use {@link GovernanceVoteConfidence#duplicate()}.
 */
public class GovernanceVoteConfidence {

    /**
     * The peers that have announced the transaction to us. Network nodes don't have stable identities, so we use
     * IP address as an approximation. It's obviously vulnerable to being gamed if we allow arbitrary people to connect
     * to us, so only peers we explicitly connected to should go here.
     */
    private CopyOnWriteArrayList<PeerAddress> broadcastBy;
    /** The time the transaction was last announced to us. */
    private Date lastBroadcastedAt;
    /** The Transaction that this confidence object is associated with. */
    private final Sha256Hash hash;
    // Lazily created listeners array.
    private CopyOnWriteArrayList<ListenerRegistration<Listener>> listeners;


    /** Describes the state of the transaction in general terms. Properties can be read to learn specifics. */
    public enum ConfidenceType {
        /**
         * If PENDING, then the vote has been seen by other peers.
         */
        PENDING(1),

        /**
         * If a transaction hasn't been broadcast yet, or there's no record of it, its confidence is UNKNOWN.
         */
        UNKNOWN(0);

        private int value;
        ConfidenceType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private ConfidenceType confidenceType = ConfidenceType.UNKNOWN;

    /**
     * Information about where the transaction was first seen (network, sent direct from peer, created by ourselves).
     * Useful for risk analyzing pending transactions. Probably not that useful after a tx is included in the chain,
     * unless re-org double spends start happening frequently.
     */
    public enum Source {
        /** We don't know where the transaction came from. */
        UNKNOWN,
        /** We got this vote from a network peer. */
        NETWORK,
        /** This vote was created by us. */
        SELF
    }
    private Source source = Source.UNKNOWN;

    public GovernanceVoteConfidence(Sha256Hash hash) {
        // Assume a default number of peers for our set.
        broadcastBy = new CopyOnWriteArrayList<PeerAddress>();
        listeners = new CopyOnWriteArrayList<ListenerRegistration<Listener>>();
        this.hash = hash;
    }

    /**
     * <p>A confidence listener is informed when the level of {@link GovernanceVoteConfidence} is updated by something, like
     * for example a {@link Wallet}. You can add listeners to update your user interface or manage your order tracking
     * system when confidence levels pass a certain threshold. <b>Note that confidence can go down as well as up.</b>
     * For example, this can happen if somebody is doing a double-spend attack against you. Whilst it's unlikely, your
     * code should be able to handle that in order to be correct.</p>
     *
     * <p>During listener execution, it's safe to remove the current listener but not others.</p>
     */
    public interface Listener {
        /** An enum that describes why a vote confidence listener is being invoked (i.e. the class of change). */
        enum ChangeReason {
            /**
             * Occurs when the type returned by {@link GovernanceVoteConfidence#getConfidenceType()}
             * has changed. For example, if a PENDING transaction changes to BUILDING or DEAD, then this reason will
             * be given. It's a high level summary.
             */
            TYPE,

            /**
             * Occurs when a transaction that is in the best known block chain gets buried by another block. If you're
             * waiting for a certain number of confirmations, this is the reason to watch out for.
             */
            DEPTH,

            /**
             * Occurs when a pending transaction (not in the chain) was announced by another connected peers. By
             * watching the number of peers that announced a transaction go up, you can see whether it's being
             * accepted by the network or not. If all your peers announce, it's a pretty good bet the transaction
             * is considered relayable and has thus reached the miners.
             */
            SEEN_PEERS,
        }
        void onConfidenceChanged(GovernanceVoteConfidence confidence, ChangeReason reason);
    }

    // This is used to ensure that confidence objects which aren't referenced from anywhere but which have an event
    // listener set on them don't become eligible for garbage collection. Otherwise the TxConfidenceTable, which only
    // has weak references to these objects, would not be enough to keep the event listeners working as transactions
    // propagate around the network - it cannot know directly if the API user is interested in the object, so it uses
    // heap reachability as a proxy for interest.
    //
    // We add ourselves to this set when a listener is added and remove ourselves when the listener list is empty.
    private static final Set<GovernanceVoteConfidence> pinnedConfidenceObjects = Collections.synchronizedSet(new HashSet<GovernanceVoteConfidence>());

    /**
     * <p>Adds an event listener that will be run when this confidence object is updated. The listener will be locked and
     * is likely to be invoked on a peer thread.</p>
     *
     * <p>It is called when the vote
     * transitions between confidence states, ie, from not being seen in the chain to being seen.</p>
     */
    public void addEventListener(Executor executor, Listener listener) {
        checkNotNull(listener);
        listeners.addIfAbsent(new ListenerRegistration<Listener>(listener, executor));
        pinnedConfidenceObjects.add(this);
    }

    /**
     * <p>Adds an event listener that will be run when this confidence object is updated. The listener will be locked and
     * is likely to be invoked on a peer thread.</p>
     *
     * <p>It is called when the vote
     * transitions between confidence states, ie, from not being seen in the chain to being seen.</p>
     */
    public void addEventListener(Listener listener) {
        addEventListener(Threading.USER_THREAD, listener);
    }

    public boolean removeEventListener(Listener listener) {
        checkNotNull(listener);
        boolean removed = ListenerRegistration.removeFromList(listener, listeners);
        if (listeners.isEmpty())
            pinnedConfidenceObjects.remove(this);
        return removed;
    }

    /**
     * Returns a general statement of the level of confidence you can have in this vote.
     */
    public synchronized ConfidenceType getConfidenceType() {
        return confidenceType;
    }

    /**
     * Called by other objects in the system, like a {@link Wallet}, when new information about the confidence of a
     * vote becomes available.
     */
    public synchronized void setConfidenceType(ConfidenceType confidenceType) {
        if (confidenceType == this.confidenceType)
            return;
        this.confidenceType = confidenceType;

        if (confidenceType == ConfidenceType.PENDING) {

        }
    }


    /**
     * Called by a {@link Peer} when a vote is pending and announced by a peer. The more peers announce the
     * vote, the more peers have validated it (assuming your internet connection is not being intercepted).
     * If confidence is currently unknown, sets it to {@link ConfidenceType#PENDING}. Does not run listeners.
     *
     * @param address IP address of the peer, used as a proxy for identity.
     * @return true if marked, false if this address was already seen
     */
    public boolean markBroadcastBy(PeerAddress address) {
        lastBroadcastedAt = Utils.now();
        if (!broadcastBy.addIfAbsent(address))
            return false;  // Duplicate.
        synchronized (this) {
            if (getConfidenceType() == ConfidenceType.UNKNOWN) {
                this.confidenceType = ConfidenceType.PENDING;
            }
        }
        return true;
    }

    /**
     * Returns how many peers have been passed to {@link GovernanceVoteConfidence#markBroadcastBy}.
     */
    public int numBroadcastPeers() {
        return broadcastBy.size();
    }

    /**
     * Returns a snapshot of {@link PeerAddress}es that announced the vote.
     */
    public Set<PeerAddress> getBroadcastBy() {
        ListIterator<PeerAddress> iterator = broadcastBy.listIterator();
        return Sets.newHashSet(iterator);
    }

    /** Returns true if the given address has been seen via markBroadcastBy() */
    public boolean wasBroadcastBy(PeerAddress address) {
        return broadcastBy.contains(address);
    }

    /** Return the time the vote was last announced to us. */
    public Date getLastBroadcastedAt() {
        return lastBroadcastedAt;
    }

    /** Set the time the vote was last announced to us. */
    public void setLastBroadcastedAt(Date lastBroadcastedAt) {
        this.lastBroadcastedAt = lastBroadcastedAt;
    }

    @Override
    public synchronized String toString() {
        StringBuilder builder = new StringBuilder();
        int peers = numBroadcastPeers();
        if (peers > 0) {
            builder.append("Seen by ").append(peers).append(peers > 1 ? " peers" : " peer");
            if (lastBroadcastedAt != null)
                builder.append(" (most recently: ").append(Utils.dateTimeFormat(lastBroadcastedAt)).append(")");
            builder.append(". ");
        }
        switch (getConfidenceType()) {
            case UNKNOWN:
                builder.append("Unknown confidence level.");
                break;
            case PENDING:
                builder.append("Pending, announced by more than 1 peer.");
                break;
        }

        if (source != Source.UNKNOWN)
            builder.append(" Source: ").append(source);
        return builder.toString();
    }

    /**
     * Erases the set of broadcast/seen peers. This cannot be called whilst the confidence is PENDING. It is useful
     * for saving memory and wallet space once a tx is buried so deep it doesn't seem likely to go pending again.
     */
    public void clearBroadcastBy() {
        checkState(getConfidenceType() != ConfidenceType.PENDING);
        broadcastBy.clear();
        lastBroadcastedAt = null;
    }

    /** Returns a copy of this object. Event listeners are not duplicated. */
    public GovernanceVoteConfidence duplicate() {
        GovernanceVoteConfidence c = new GovernanceVoteConfidence(hash);
        c.broadcastBy.addAll(broadcastBy);
        c.lastBroadcastedAt = lastBroadcastedAt;
        synchronized (this) {
            c.confidenceType = confidenceType;
        }
        return c;
    }

    /**
     * Call this after adjusting the confidence, for cases where listeners should be notified. This has to be done
     * explicitly rather than being done automatically because sometimes complex changes to vote states can
     * result in a series of confidence changes that are not really useful to see separately. By invoking listeners
     * explicitly, more precise control is available. Note that this will run the listeners on the user code thread.
     */
    public void queueListeners(final Listener.ChangeReason reason) {
        for (final ListenerRegistration<Listener> registration : listeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onConfidenceChanged(GovernanceVoteConfidence.this, reason);
                }
            });
        }
    }

    /**
     * The source of a vote tries to identify where it came from originally. For instance, did we download it
     * from the peer to peer network, or make it ourselves, or receive it via Bluetooth, or import it from another app,
     * and so on. This information is useful for {@link org.bitcoinj.wallet.CoinSelector} implementations to risk analyze
     * transactions and decide when to spend them.
     */
    public synchronized Source getSource() {
        return source;
    }

    /**
     * The source of a vote tries to identify where it came from originally. For instance, did we download it
     * from the peer to peer network, or make it ourselves, or receive it via Bluetooth, or import it from another app,
     * and so on. This information is useful for {@link org.bitcoinj.wallet.CoinSelector} implementations to risk analyze
     * transactions and decide when to spend them.
     */
    public synchronized void setSource(Source source) {
        this.source = source;
    }

    public Sha256Hash getTransactionHash() {
        return hash;
    }

    //Dash Specific Additions
    public enum IXType {
        IX_NONE,
        IX_REQUEST,
        IX_LOCKED
    };

    IXType ixType = IXType.IX_NONE;

    public void setIXType(IXType ixType) {
        this.ixType = ixType;
    }

    public IXType getIXType() {
        return ixType;
    }

    public boolean isIX() { return ixType != IXType.IX_NONE; }
    public boolean isTransactionLocked() { return ixType == IXType.IX_LOCKED; }
}
