/*
 * Copyright 2019 by Dash Core Group
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

package org.bitcoinj.wallet;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.*;
import org.bitcoinj.evolution.EvolutionContact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class FriendKeyChain extends DeterministicKeyChain {

    private static final Logger log = LoggerFactory.getLogger(FriendKeyChain.class);

    enum KeyChainType {
        RECEIVING_CHAIN,
        SENDING_CHAIN,
    }
    KeyChainType type;
    int currentIndex;
    int issuedKeys;

    // m / 9' / 5' / 5' / 1' - Friend Key Chain root path
    public static final ImmutableList<ChildNumber> FRIEND_ROOT_PATH = ImmutableList.of(ChildNumber.NINE_HARDENED,
            ChildNumber.FIVE_HARDENED, ChildNumber.FIVE_HARDENED, ChildNumber.ONE_HARDENED);
    public static final ImmutableList<ChildNumber> FRIEND_ROOT_PATH_TESTNET = ImmutableList.of(ChildNumber.NINE_HARDENED,
            ChildNumber.ONE_HARDENED, ChildNumber.FIVE_HARDENED, ChildNumber.ONE_HARDENED);

    public static ImmutableList<ChildNumber> getRootPath(NetworkParameters params) {
        return params.getId().equals(NetworkParameters.ID_MAINNET) ? FRIEND_ROOT_PATH : FRIEND_ROOT_PATH_TESTNET;
    }

    public static ImmutableList<ChildNumber> getContactPath(NetworkParameters params, EvolutionContact contact, KeyChainType type) {
        Sha256Hash userA = type == KeyChainType.RECEIVING_CHAIN ? contact.getEvolutionUserId() : contact.getFriendUserId();
        Sha256Hash userB = type == KeyChainType.RECEIVING_CHAIN ? contact.getFriendUserId() : contact.getEvolutionUserId();
        return new ImmutableList.Builder().addAll(getRootPath(params)).add(new ChildNumber(contact.getUserAccount(), true)).add(new ExtendedChildNumber(userA)).add(new ExtendedChildNumber(userB)).build();
    }

    public static final int PATH_INDEX_ACCOUNT = 4;
    public static final int PATH_INDEX_TO_ID = 5;
    public static final int PATH_INDEX_FROM_ID = 6;


    public FriendKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> rootPath, int account, Sha256Hash myBlockchainUserId, Sha256Hash theirBlockchainUserId) {
        super(seed, ImmutableList.<ChildNumber>builder().addAll(rootPath).add(new ChildNumber(account, true)).add(new ExtendedChildNumber(myBlockchainUserId)).add(new ExtendedChildNumber(theirBlockchainUserId)).build());
        type = KeyChainType.RECEIVING_CHAIN;
    }

    public FriendKeyChain(DeterministicSeed seed, KeyCrypter keyCrypter, ImmutableList<ChildNumber> rootPath,  int account, Sha256Hash myBlockchainUserId, Sha256Hash theirBlockchainUserId) {
        super(seed, keyCrypter, ImmutableList.<ChildNumber>builder().addAll(rootPath).add(new ChildNumber(account, true)).add(new ExtendedChildNumber(myBlockchainUserId)).add(new ExtendedChildNumber(theirBlockchainUserId)).build());
        type = KeyChainType.RECEIVING_CHAIN;
    }

    public FriendKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path) {
        super(seed, path);
        type = KeyChainType.RECEIVING_CHAIN;
    }

    public FriendKeyChain(DeterministicSeed seed, KeyCrypter keyCrypter, ImmutableList<ChildNumber> path) {
        super(seed, keyCrypter, path);
        type = KeyChainType.RECEIVING_CHAIN;
    }

    /** xpub must contain a depth to indicate that it is not a masterkey **/
    public FriendKeyChain(NetworkParameters params, String xpub, EvolutionContact contact) {
        super(DeterministicKey.deserializeB58(xpub, getContactPath(params, contact, KeyChainType.SENDING_CHAIN), params));
        setAccountPath(getContactPath(params, contact, KeyChainType.SENDING_CHAIN));
        //TODO:  check to see that xpub has a depth, if not the path won't agree with getContactPath
    }

    public FriendKeyChain(DeterministicKey watchingKey) {
        super(watchingKey);
    }

    public FriendKeyChain(DeterministicKey watchingKey, boolean isFollowing) {
        super(watchingKey, isFollowing);
    }

    /** {@inheritDoc} */
    @Override
    public DeterministicKey getKey(KeyPurpose purpose) {
        return getKeys(purpose, 1).get(0);
    }

    /** {@inheritDoc} */
    @Override
    public List<DeterministicKey> getKeys(KeyPurpose purpose, int numberOfKeys) {
        checkArgument(numberOfKeys > 0);
        lock.lock();
        try {
            DeterministicKey parentKey;
            int index;
            switch (purpose) {
                case RECEIVE_FUNDS:
                case REFUND:
                    issuedKeys += numberOfKeys;
                    index = issuedKeys;
                    parentKey = getKeyByPath(getAccountPath());
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            // Optimization: potentially do a very quick key generation for just the number of keys we need if we
            // didn't already create them, ignoring the configured lookahead size. This ensures we'll be able to
            // retrieve the keys in the following loop, but if we're totally fresh and didn't get a chance to
            // calculate the lookahead keys yet, this will not block waiting to calculate 100+ EC point multiplies.
            // On slow/crappy Android phones looking ahead 100 keys can take ~5 seconds but the OS will kill us
            // if we block for just one second on the UI thread. Because UI threads may need an address in order
            // to render the screen, we need getKeys to be fast even if the wallet is totally brand new and lookahead
            // didn't happen yet.
            //
            // It's safe to do this because when a network thread tries to calculate a Bloom filter, we'll go ahead
            // and calculate the full lookahead zone there, so network requests will always use the right amount.
            //List<DeterministicKey> lookahead = maybeLookAhead(parentKey, index, 0, 0);
            //basicKeyChain.importKeys(lookahead);
            List<DeterministicKey> keys = new ArrayList<DeterministicKey>(numberOfKeys);
            for (int i = 0; i < numberOfKeys; i++) {
                ImmutableList<ChildNumber> path = HDUtils.append(parentKey.getPath(), new ChildNumber(index - numberOfKeys + i, false));
                DeterministicKey k = hierarchy.get(path, false, false);
                //DeterministicKey k = HDKeyDerivation.deriveChildKey(parentKey, new ChildNumber(index - numberOfKeys + i));
                // Just a last minute sanity check before we hand the key out to the app for usage. This isn't inspired
                // by any real problem reports from bitcoinj users, but I've heard of cases via the grapevine of
                // places that lost money due to bitflips causing addresses to not match keys. Of course in an
                // environment with flaky RAM there's no real way to always win: bitflips could be introduced at any
                // other layer. But as we're potentially retrieving from long term storage here, check anyway.
                checkForBitFlip(k);
                keys.add(k);
            }
            return keys;
        } finally {
            lock.unlock();
        }
    }

    public DeterministicKey getKey(int index) {
        return getKeyByPath(new ImmutableList.Builder().addAll(getAccountPath()).addAll(ImmutableList.of(new ChildNumber(index, false))).build(), true);
    }

    /** {@inheritDoc} */
    @Override
    public int getIssuedExternalKeys() {
        return currentIndex;
    }

    /** {@inheritDoc} */
    @Override
    public int getIssuedInternalKeys() {
        throw new UnsupportedOperationException("Contact key chains do not have internal keys");
    }

    /**
     * Getter for property 'currentIndex'.
     *
     * @return Value for property 'currentIndex'.
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    public DeterministicKey freshReceivingKey() {
        return getKey(KeyPurpose.RECEIVE_FUNDS);
    }

    public DeterministicKey currentReceivingKey() {
        return getKey(currentIndex);
    }

    /** {@inheritDoc} */
    @Override
    public DeterministicKey markKeyAsUsed(DeterministicKey k) {
        int numChildren = k.getChildNumber().i() + 1;

        if (k.getParent() == getKeyByPath(getAccountPath())) {
            if (issuedKeys < numChildren) {
                issuedKeys = numChildren;
                maybeLookAhead();
            }
        }
        return k;
    }

    /**
     * Pre-generate enough keys to reach the lookahead size. You can call this if you need to explicitly invoke
     * the lookahead procedure, but it's normally unnecessary as it will be done automatically when needed.
     */
    public void maybeLookAhead() {
        lock.lock();
        try {
            List<DeterministicKey> keys = maybeLookAhead(getKeyByPath(getAccountPath()), issuedKeys);
            if (keys.isEmpty())
                return;
            keyLookaheadEpoch++;
            // Batch add all keys at once so there's only one event listener invocation, as this will be listened to
            // by the wallet and used to rebuild/broadcast the Bloom filter. That's expensive so we don't want to do
            // it more often than necessary.
            basicKeyChain.importKeys(keys);
        } finally {
            lock.unlock();
        }
    }

    private List<DeterministicKey> maybeLookAhead(DeterministicKey parent, int issued) {
        checkState(lock.isHeldByCurrentThread());
        return maybeLookAhead(parent, issued, getLookaheadSize(), getLookaheadThreshold());
    }

    /**
     * Pre-generate enough keys to reach the lookahead size, but only if there are more than the lookaheadThreshold to
     * be generated, so that the Bloom filter does not have to be regenerated that often.
     *
     * The returned mutable list of keys must be inserted into the basic key chain.
     */
    private List<DeterministicKey> maybeLookAhead(DeterministicKey parent, int issued, int lookaheadSize, int lookaheadThreshold) {
        checkState(lock.isHeldByCurrentThread());
        final int numChildren = hierarchy.getNumChildren(parent.getPath());
        final int needed = issued + lookaheadSize + lookaheadThreshold - numChildren;

        if (needed <= lookaheadThreshold)
            return new ArrayList<DeterministicKey>();

        log.info("{} keys needed for {} = {} issued + {} lookahead size + {} lookahead threshold - {} num children",
                needed, parent.getPathAsString(), issued, lookaheadSize, lookaheadThreshold, numChildren);

        List<DeterministicKey> result  = new ArrayList<DeterministicKey>(needed);
        final Stopwatch watch = Stopwatch.createStarted();
        int nextChild = numChildren;
        for (int i = 0; i < needed; i++) {
            DeterministicKey key = HDKeyDerivation.deriveThisOrNextChildKey(parent, nextChild);
            key = key.dropPrivateBytes();
            hierarchy.putKey(key);
            result.add(key);
            nextChild = key.getChildNumber().num() + 1;
        }
        watch.stop();
        log.info("Took {}", watch);
        return result;
    }

    /**
     * Setter for property 'issuedKeys'.
     *
     * @param issuedKeys Value to set for property 'issuedKeys'.
     */
    public void setIssuedKeys(int issuedKeys) {
        this.issuedKeys = issuedKeys;
    }
}
