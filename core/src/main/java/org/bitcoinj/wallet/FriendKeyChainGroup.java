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
import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.*;
import org.bitcoinj.evolution.EvolutionContact;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.wallet.FriendKeyChain.*;

public class FriendKeyChainGroup extends KeyChainGroup {

    private static final Logger log = LoggerFactory.getLogger(FriendKeyChainGroup.class);

    /**
     * Builder for {@link FriendKeyChainGroup}. Use {@link FriendKeyChainGroup#builder(NetworkParameters)} to acquire an instance.
     */
    public static class Builder {
        private final NetworkParameters params;
        private final KeyChainGroupStructure structure;
        private final List<DeterministicKeyChain> chains = new LinkedList<>();
        private int lookaheadSize = -1, lookaheadThreshold = -1;

        private Builder(NetworkParameters params, KeyChainGroupStructure structure) {
            this.params = params;
            this.structure = structure;
        }

        /**
         * <p>Add chain from a random source.</p>
         * <p>In the case of P2PKH, just a P2PKH chain is created and activated which is then the default chain for fresh
         * addresses. It can be upgraded to P2WPKH later.</p>
         * <p>In the case of P2WPKH, both a P2PKH and a P2WPKH chain are created and activated, the latter being the default
         * chain. This behaviour will likely be changed with bitcoinj 0.16 such that only a P2WPKH chain is created and
         * activated.</p>
         * @param outputScriptType type of addresses (aka output scripts) to generate for receiving
         */
        public FriendKeyChainGroup.Builder fromRandom(Script.ScriptType outputScriptType) {
            DeterministicSeed seed = new DeterministicSeed(new SecureRandom(),
                    DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS, "");
            fromSeed(seed, outputScriptType);
            return this;
        }

        /**
         * <p>Add chain from a given seed.</p>
         * <p>In the case of P2PKH, just a P2PKH chain is created and activated which is then the default chain for fresh
         * addresses. It can be upgraded to P2WPKH later.</p>
         * <p>In the case of P2WPKH, both a P2PKH and a P2WPKH chain are created and activated, the latter being the default
         * chain. This behaviour will likely be changed with bitcoinj 0.16 such that only a P2WPKH chain is created and
         * activated.</p>
         * @param seed deterministic seed to derive all keys from
         * @param outputScriptType type of addresses (aka output scripts) to generate for receiving
         */
        public FriendKeyChainGroup.Builder fromSeed(DeterministicSeed seed, Script.ScriptType outputScriptType) {
            if (outputScriptType == Script.ScriptType.P2PKH) {
                DeterministicKeyChain chain = DeterministicKeyChain.builder().seed(seed)
                        .outputScriptType(Script.ScriptType.P2PKH)
                        .accountPath(structure.accountPathFor(Script.ScriptType.P2PKH)).build();
                this.chains.clear();
                this.chains.add(chain);
            } else {
                throw new IllegalArgumentException(outputScriptType.toString());
            }
            return this;
        }

        /**
         * Add a single chain.
         * @param chain to add
         */
        public FriendKeyChainGroup.Builder addChain(DeterministicKeyChain chain) {
            this.chains.add(chain);
            return this;
        }

        /**
         * Add multiple chains.
         * @param chains to add
         */
        public FriendKeyChainGroup.Builder chains(List<DeterministicKeyChain> chains) {
            this.chains.clear();
            this.chains.addAll(chains);
            return this;
        }

        /**
         * Set a custom lookahead size for all deterministic chains
         * @param lookaheadSize lookahead size
         */
        public FriendKeyChainGroup.Builder lookaheadSize(int lookaheadSize) {
            this.lookaheadSize = lookaheadSize;
            return this;
        }

        /**
         * Set a custom lookahead threshold for all deterministic chains
         * @param lookaheadThreshold lookahead threshold
         */
        public FriendKeyChainGroup.Builder lookaheadThreshold(int lookaheadThreshold) {
            this.lookaheadThreshold = lookaheadThreshold;
            return this;
        }

        public FriendKeyChainGroup build() {
            return new FriendKeyChainGroup(params, null, chains, lookaheadSize, lookaheadThreshold, null, null);
        }
    }

    public static FriendKeyChainGroup.Builder friendlybuilder(NetworkParameters params) {
        return new Builder(params, KeyChainGroupStructure.DEFAULT);
    }



    HashMap<ImmutableList<ChildNumber>, DeterministicKey> currentContactKeys;

    protected FriendKeyChainGroup(NetworkParameters params, @Nullable BasicKeyChain basicKeyChain, List<DeterministicKeyChain> chains,
                            int lookAheadSize, int lookAheadThreshold,
                            @Nullable HashMap<ImmutableList<ChildNumber>, DeterministicKey> currentKeys, @Nullable KeyCrypter crypter) {
        super(params, basicKeyChain, chains, lookAheadSize, lookAheadThreshold,null, crypter);
        currentContactKeys = currentKeys != null ? currentKeys :
                new HashMap<ImmutableList<ChildNumber>, DeterministicKey>();
    }

    public FriendKeyChain getFriendKeyChain(Sha256Hash myBlockchainUserId, int account, Sha256Hash theirBlockchainUserId, int friendAccountReference, FriendKeyChain.KeyChainType type) {
        Preconditions.checkNotNull(theirBlockchainUserId);
        Preconditions.checkArgument(!theirBlockchainUserId.isZero());

        Sha256Hash to, from;
        int accountReference;
        if(type == FriendKeyChain.KeyChainType.RECEIVING_CHAIN)
        {
            from = theirBlockchainUserId;
            to = myBlockchainUserId;
            accountReference = account;
        } else {
            to = theirBlockchainUserId;
            from = myBlockchainUserId;
            accountReference = friendAccountReference;
        }
        for(DeterministicKeyChain chain : chains) {
            ImmutableList<ChildNumber> accountPath = chain.getAccountPath();
            if(accountPath.get(PATH_INDEX_ACCOUNT).equals(new ChildNumber(accountReference, true)) &&
            accountPath.get(PATH_INDEX_TO_ID).equals(new ExtendedChildNumber(to)) &&
            accountPath.get(PATH_INDEX_FROM_ID).equals(new ExtendedChildNumber(from)))
                return (FriendKeyChain)chain;
        }
        return null;
    }

    @Deprecated
    public FriendKeyChain getFriendKeyChain(Sha256Hash myBlockchainUserId, Sha256Hash theirBlockchainUserId, FriendKeyChain.KeyChainType type ) {
        return getFriendKeyChain(myBlockchainUserId, 0, theirBlockchainUserId, 0, type);
    }

    public FriendKeyChain getFriendKeyChain(EvolutionContact contact, FriendKeyChain.KeyChainType type ) {
        return getFriendKeyChain(contact.getEvolutionUserId(), contact.getUserAccount(), contact.getFriendUserId(), contact.getFriendAccountReference(), type);
    }

    @Override
    public void addAndActivateHDChain(DeterministicKeyChain chain) {
        if(chain instanceof FriendKeyChain)
            super.addAndActivateHDChain(chain);
        else throw new IllegalArgumentException("chain is not of type FriendKeyChain");
    }

    public boolean hasKeyChains() { return !chains.isEmpty(); }

    static FriendKeyChainGroup fromProtobufUnencrypted(NetworkParameters params, List<Protos.Key> keys, KeyChainType type) throws UnreadableWalletException {
        return fromProtobufUnencrypted(params, keys, new DefaultKeyChainFactory(), type);
    }

    public static FriendKeyChainGroup fromProtobufUnencrypted(NetworkParameters params, List<Protos.Key> keys, KeyChainFactory factory, KeyChainType type) throws UnreadableWalletException {
        BasicKeyChain basicKeyChain = BasicKeyChain.fromProtobufUnencrypted(keys);
        List<DeterministicKeyChain> chains = DeterministicKeyChain.fromProtobuf(keys, null, factory);
        for(DeterministicKeyChain chain : chains) {
            Preconditions.checkState(chain instanceof FriendKeyChain);
        }
        HashMap<ImmutableList<ChildNumber>, DeterministicKey> currentKeys = null;

        int lookaheadSize = -1, lookaheadThreshold = -1;
        if (!chains.isEmpty()) {
            DeterministicKeyChain activeChain = chains.get(chains.size() - 1);
            lookaheadSize = activeChain.getLookaheadSize();
            lookaheadThreshold = activeChain.getLookaheadThreshold();
            currentKeys = createCurrentContactKeysMap(chains);
        }
        return new FriendKeyChainGroup(params, basicKeyChain, chains, lookaheadSize, lookaheadThreshold, currentKeys, null);
    }

    static FriendKeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys, KeyCrypter crypter, KeyChainType type) throws UnreadableWalletException {
        return fromProtobufEncrypted(params, keys, crypter, new DefaultKeyChainFactory(), type);
    }

    public static FriendKeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys, KeyCrypter crypter, KeyChainFactory factory, KeyChainType type) throws UnreadableWalletException {
        checkNotNull(crypter);
        BasicKeyChain basicKeyChain = BasicKeyChain.fromProtobufEncrypted(keys, crypter);
        List<DeterministicKeyChain> chains = DeterministicKeyChain.fromProtobuf(keys, crypter, factory);
        for(DeterministicKeyChain chain : chains) {
            Preconditions.checkState(chain instanceof FriendKeyChain);
        }
        HashMap<ImmutableList<ChildNumber>, DeterministicKey> currentKeys = null;

        int lookaheadSize = -1, lookaheadThreshold = -1;
        if (!chains.isEmpty()) {
            DeterministicKeyChain activeChain = chains.get(chains.size() - 1);
            lookaheadSize = activeChain.getLookaheadSize();
            lookaheadThreshold = activeChain.getLookaheadThreshold();
            currentKeys = createCurrentContactKeysMap(chains);
        }
        return new FriendKeyChainGroup(params, basicKeyChain, chains, lookaheadSize, lookaheadThreshold, currentKeys, crypter);
    }

    /**
     * Returns a key that hasn't been seen in a transaction yet, and which is suitable for displaying in a wallet
     * user interface as "a convenient key to receive funds on" when the purpose parameter is
     * {@link KeyChain.KeyPurpose#RECEIVE_FUNDS}. The returned key is stable until
     * it's actually seen in a pending or confirmed transaction, at which point this method will start returning
     * a different key (for each purpose independently).
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #currentAddress(KeyChain.KeyPurpose)}
     * to get a proper P2SH address</p>
     */
    public DeterministicKey currentKey(EvolutionContact contact, FriendKeyChain.KeyChainType type) {
        DeterministicKeyChain chain = getFriendKeyChain(contact, type);
        if (chain.isMarried()) {
            throw new UnsupportedOperationException("Key is not suitable to receive coins for married keychains." +
                    " Use freshAddress to get P2SH address instead");
        }
        ImmutableList<ChildNumber> accountPath = chain.getAccountPath();
        DeterministicKey current = currentContactKeys.get(accountPath);
        if (current == null) {
            current = freshKey(contact, type);
            currentContactKeys.put(accountPath, current);
        }
        return current;
    }

    /**
     * Returns address for a {@link #currentKey(org.bitcoinj.evolution.EvolutionContact, KeyChainType)}
     */
    public Address currentAddress(EvolutionContact contact, FriendKeyChain.KeyChainType type, KeyChain.KeyPurpose purpose) {
        DeterministicKeyChain chain = getFriendKeyChain(contact, type);
        if (chain.isMarried()) {
            Address current = currentAddresses.get(purpose);
            if (current == null) {
                current = freshAddress(purpose);
                currentAddresses.put(purpose, current);
            }
            return current;
        } else {
            return Address.fromKey(params, currentKey(purpose));
        }
    }

    /**
     * Returns a key that has not been returned by this method before (fresh). You can think of this as being
     * a newly created key, although the notion of "create" is not really valid for a
     * {@link DeterministicKeyChain}. The returned key is suitable for being put
     * into a receive coins wizard type UI. You should use this when the user is definitely going to hand this key out
     * to someone who wishes to send money.
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #freshAddress(org.bitcoinj.evolution.EvolutionContact, org.bitcoinj.wallet.FriendKeyChain.KeyChainType)}
     * to get a proper P2SH address</p>
     */
    public DeterministicKey freshKey(EvolutionContact contact, KeyChainType type) {
        return freshKeys(contact, type, 1).get(0);
    }

    /**
     * Returns a key/s that have not been returned by this method before (fresh). You can think of this as being
     * newly created key/s, although the notion of "create" is not really valid for a
     * {@link DeterministicKeyChain}. You should use this when the user is definitely going to hand this key out
     * to someone who wishes to send money.
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #freshAddress(KeyChain.KeyPurpose)}
     * to get a proper P2SH address</p>
     */

    public List<DeterministicKey> freshKeys(EvolutionContact contact, KeyChainType type, int numberOfKeys) {
        DeterministicKeyChain chain = getFriendKeyChain(contact, type);
        if (chain.isMarried()) {
            throw new UnsupportedOperationException("Key is not suitable to receive coins for married keychains." +
                    " Use freshAddress to get P2SH address instead");
        }
        return chain.getKeys(KeyPurpose.RECEIVE_FUNDS, numberOfKeys);   // Always returns the next key along the key chain.
    }

    /**
     * Returns address for a {@link #freshKey(org.bitcoinj.evolution.EvolutionContact, org.bitcoinj.wallet.FriendKeyChain.KeyChainType)}
     */
    public Address freshAddress(EvolutionContact contact, FriendKeyChain.KeyChainType type) {
        DeterministicKeyChain chain = getFriendKeyChain(contact, type);
        if (chain.isMarried()) {
            Script outputScript = chain.freshOutputScript(KeyPurpose.RECEIVE_FUNDS);
            checkState(outputScript.isPayToScriptHash()); // Only handle P2SH for now
            Address freshAddress = Address.fromP2SHScript(params, outputScript);
            maybeLookaheadScripts();
            currentAddresses.put(KeyPurpose.RECEIVE_FUNDS, freshAddress);
            return freshAddress;
        } else {
            return Address.fromKey(params, freshKey(contact, type));
        }
    }

    protected static HashMap<ImmutableList<ChildNumber>, DeterministicKey> createCurrentContactKeysMap(List<DeterministicKeyChain> chains) {

        HashMap<ImmutableList<ChildNumber>, DeterministicKey> currentKeys = new HashMap<ImmutableList<ChildNumber>, DeterministicKey>(chains.size());

        for(DeterministicKeyChain chain : chains) {
            FriendKeyChain contactChain = (FriendKeyChain)chain;
            // assuming that only RECEIVE and CHANGE keys are being used at the moment, we will treat latest issued external key
            // as current RECEIVE key and latest issued internal key as CHANGE key. This should be changed as soon as other
            // kinds of KeyPurpose are introduced.
            if (contactChain.getIssuedExternalKeys() > 0) {
                DeterministicKey currentExternalKey = contactChain.getKeyByPath(
                        HDUtils.append(
                                contactChain.getAccountPath(),
                                new ChildNumber(contactChain.getIssuedExternalKeys() - 1)));
                currentKeys.put(chain.getAccountPath(), currentExternalKey);
            }
        }
        return currentKeys;
    }

    /** If the given key is "current", advance the current key to a new one. */
    protected void maybeMarkCurrentKeyAsUsed(DeterministicKey key) {
        // It's OK for currentKeys to be empty here: it means we're a married wallet and the key may be a part of a
        // rotating chain.
        for (Map.Entry<ImmutableList<ChildNumber>, DeterministicKey> entry : currentContactKeys.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equals(key)) {
                log.info("Marking key as used: {}", key);
                currentContactKeys.put(entry.getKey(), freshKey(new EvolutionContact(entry.getKey(), getKeyChainType() == KeyChainType.RECEIVING_CHAIN), getKeyChainType()));
                queueOnCurrentKeyChanged();
                return;
            }
        }
    }

    protected KeyChainType getKeyChainType() {
        return chains.size() > 0 ? ((FriendKeyChain)chains.get(0)).type : null;
    }

    public EvolutionContact getFriendFromPublicKeyHash(byte [] pubKeyHash) {
        ECKey key = findKeyFromPubKeyHash(pubKeyHash, Script.ScriptType.P2PKH);
        if (key instanceof DeterministicKey) {
            ImmutableList<ChildNumber> path = ((DeterministicKey)key).getPath();
            Sha256Hash from = Sha256Hash.wrap(((ExtendedChildNumber)path.get(PATH_INDEX_FROM_ID)).bi());
            Sha256Hash to = Sha256Hash.wrap(((ExtendedChildNumber)path.get(PATH_INDEX_TO_ID)).bi());
            return new EvolutionContact(from, to);
        }
        return null;
    }
}
