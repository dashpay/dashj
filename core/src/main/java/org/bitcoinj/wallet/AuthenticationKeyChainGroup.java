/*
 * Copyright 2019 Dash Core Group
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
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class AuthenticationKeyChainGroup extends KeyChainGroup {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationKeyChainGroup.class);

    HashMap<AuthenticationKeyChain.KeyChainType, DeterministicKey> currentAuthenticationKeys;

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
        public AuthenticationKeyChainGroup.Builder fromRandom(Script.ScriptType outputScriptType) {
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
        public AuthenticationKeyChainGroup.Builder fromSeed(DeterministicSeed seed, Script.ScriptType outputScriptType) {
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
        public AuthenticationKeyChainGroup.Builder addChain(DeterministicKeyChain chain) {
            this.chains.add(chain);
            return this;
        }

        /**
         * Add multiple chains.
         * @param chains to add
         */
        public AuthenticationKeyChainGroup.Builder chains(List<DeterministicKeyChain> chains) {
            this.chains.clear();
            this.chains.addAll(chains);
            return this;
        }

        /**
         * Set a custom lookahead size for all deterministic chains
         * @param lookaheadSize lookahead size
         */
        public AuthenticationKeyChainGroup.Builder lookaheadSize(int lookaheadSize) {
            this.lookaheadSize = lookaheadSize;
            return this;
        }

        /**
         * Set a custom lookahead threshold for all deterministic chains
         * @param lookaheadThreshold lookahead threshold
         */
        public AuthenticationKeyChainGroup.Builder lookaheadThreshold(int lookaheadThreshold) {
            this.lookaheadThreshold = lookaheadThreshold;
            return this;
        }

        public AuthenticationKeyChainGroup build() {
            return new AuthenticationKeyChainGroup(params, null, chains, lookaheadSize, lookaheadThreshold, null, null);
        }
    }

    protected AuthenticationKeyChainGroup(NetworkParameters params, @Nullable BasicKeyChain basicKeyChain, List<DeterministicKeyChain> chains, int lookAheadSize, int lookAheadThreshold,
                                          @Nullable HashMap<AuthenticationKeyChain.KeyChainType, DeterministicKey> currentKeys, @Nullable KeyCrypter crypter) {
        super(params, basicKeyChain, chains, lookAheadSize, lookAheadThreshold,null, crypter);
        currentAuthenticationKeys = currentKeys != null ? currentKeys :
                new HashMap<AuthenticationKeyChain.KeyChainType, DeterministicKey>();
    }

    public static AuthenticationKeyChainGroup.Builder authenticationBuilder(NetworkParameters params) {
        return new Builder(params, KeyChainGroupStructure.DEFAULT);
    }

    public AuthenticationKeyChain getKeyChain(AuthenticationKeyChain.KeyChainType type) {
        for(DeterministicKeyChain chain : chains) {
            if(((AuthenticationKeyChain)chain).type == type)
                return (AuthenticationKeyChain)chain;
        }
        return null;
    }


    @Override
    public void addAndActivateHDChain(DeterministicKeyChain chain) {
        if(chain instanceof AuthenticationKeyChain)
            super.addAndActivateHDChain(chain);
        else throw new IllegalArgumentException("chain is not of type AuthenticationKeyChain");
    }

    public boolean hasKeyChains() { return !chains.isEmpty(); }

    static AuthenticationKeyChainGroup fromProtobufUnencrypted(NetworkParameters params, List<Protos.Key> keys, AuthenticationKeyChain.KeyChainType type) throws UnreadableWalletException {
        return fromProtobufUnencrypted(params, keys, new DefaultKeyChainFactory(), type);
    }

    public static AuthenticationKeyChainGroup fromProtobufUnencrypted(NetworkParameters params, List<Protos.Key> keys, KeyChainFactory factory, AuthenticationKeyChain.KeyChainType type) throws UnreadableWalletException {
        BasicKeyChain basicKeyChain = BasicKeyChain.fromProtobufUnencrypted(keys);
        List<DeterministicKeyChain> chains = DeterministicKeyChain.fromProtobuf(keys, null, factory);
        for(DeterministicKeyChain chain : chains) {
            Preconditions.checkState(chain instanceof FriendKeyChain);
        }
        HashMap<AuthenticationKeyChain.KeyChainType, DeterministicKey> currentKeys = null;
        determineIssuedKeys(keys, chains);
        int lookaheadSize = -1, lookaheadThreshold = -1;
        if (!chains.isEmpty()) {
            DeterministicKeyChain activeChain = chains.get(chains.size() - 1);
            lookaheadSize = activeChain.getLookaheadSize();
            lookaheadThreshold = activeChain.getLookaheadThreshold();
            currentKeys = createCurrentAuthenticationKeysMap(chains);
        }
        return new AuthenticationKeyChainGroup(params, basicKeyChain, chains, lookaheadSize, lookaheadThreshold, currentKeys, null);
    }

    protected static void determineIssuedKeys(List<Protos.Key> keys, List<DeterministicKeyChain> chains) {
        //TODO:  search through the currentKeys and then also chains to match up issued key counts
        for (DeterministicKeyChain chain : chains) {
            AuthenticationKeyChain contactChain = (AuthenticationKeyChain) chain;
            ImmutableList<ChildNumber> accountPath = contactChain.getAccountPath();
            int issuedKeysCount = 0;
            for (Protos.Key key : keys) {
                List<Protos.ExtendedChildNumber> path = key.getExtendedPathList();
                if (accountPath.size() + 1 <= path.size()) {
                        issuedKeysCount++;
                }
            }
            if (issuedKeysCount > 0)
                contactChain.setIssuedKeys(issuedKeysCount);
        }
    }

    static AuthenticationKeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys, KeyCrypter crypter, AuthenticationKeyChain.KeyChainType type) throws UnreadableWalletException {
        return fromProtobufEncrypted(params, keys, crypter, new DefaultKeyChainFactory(), type);
    }

    public static AuthenticationKeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys, KeyCrypter crypter, KeyChainFactory factory, AuthenticationKeyChain.KeyChainType type) throws UnreadableWalletException {
        checkNotNull(crypter);
        BasicKeyChain basicKeyChain = BasicKeyChain.fromProtobufEncrypted(keys, crypter);
        List<DeterministicKeyChain> chains = DeterministicKeyChain.fromProtobuf(keys, crypter, factory);
        for(DeterministicKeyChain chain : chains) {
            Preconditions.checkState(chain instanceof FriendKeyChain);
        }
        HashMap<AuthenticationKeyChain.KeyChainType, DeterministicKey> currentKeys = null;

        determineIssuedKeys(keys, chains);

        int lookaheadSize = -1, lookaheadThreshold = -1;
        if (!chains.isEmpty()) {
            DeterministicKeyChain activeChain = chains.get(chains.size() - 1);
            lookaheadSize = activeChain.getLookaheadSize();
            lookaheadThreshold = activeChain.getLookaheadThreshold();
            currentKeys = createCurrentAuthenticationKeysMap(chains);
        }
        return new AuthenticationKeyChainGroup(params, basicKeyChain, chains, lookaheadSize, lookaheadThreshold, currentKeys, crypter);
    }

    /**
     * Returns a key that hasn't been seen in a transaction yet, and which is suitable for displaying in a wallet
     * user interface as "a convenient key to receive funds on" when the purpose parameter is
     * {@link KeyPurpose#RECEIVE_FUNDS}. The returned key is stable until
     * it's actually seen in a pending or confirmed transaction, at which point this method will start returning
     * a different key (for each purpose independently).
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #currentAddress(org.bitcoinj.wallet.AuthenticationKeyChain.KeyChainType)}
     * to get a proper P2SH address</p>
     */
    public DeterministicKey currentKey(AuthenticationKeyChain.KeyChainType type) {
        DeterministicKeyChain chain = getKeyChain(type);
        if (chain.isMarried()) {
            throw new UnsupportedOperationException("Key is not suitable to receive coins for married keychains." +
                    " Use freshAddress to get P2SH address instead");
        }
        AuthenticationKeyChain.KeyChainType accountPath = ((AuthenticationKeyChain) chain).type;
        DeterministicKey current = currentAuthenticationKeys.get(accountPath);
        if (current == null) {
            current = freshKey(type);
            currentAuthenticationKeys.put(accountPath, current);
        }
        return current;
    }

    /**
     * Returns address for a {@link #currentKey(AuthenticationKeyChain.KeyChainType)}
     */
    public Address currentAddress(AuthenticationKeyChain.KeyChainType type) {
        DeterministicKeyChain chain = getKeyChain(type);
        if (chain.isMarried()) {
            Address current = currentAddresses.get(KeyChain.KeyPurpose.AUTHENTICATION);
            if (current == null) {
                current = freshAddress(KeyChain.KeyPurpose.AUTHENTICATION);
                currentAddresses.put(KeyChain.KeyPurpose.AUTHENTICATION, current);
            }
            return current;
        } else {
            return Address.fromKey(params, currentKey(KeyChain.KeyPurpose.AUTHENTICATION));
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
     * For married keychains use {@link #freshAddress(AuthenticationKeyChain.KeyChainType)}
     * to get a proper P2SH address</p>
     */
    public DeterministicKey freshKey(AuthenticationKeyChain.KeyChainType type) {
        return freshKeys(type, 1).get(0);
    }

    /**
     * Returns a key/s that have not been returned by this method before (fresh). You can think of this as being
     * newly created key/s, although the notion of "create" is not really valid for a
     * {@link DeterministicKeyChain}. You should use this when the user is definitely going to hand this key out
     * to someone who wishes to send money.
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #freshAddress(AuthenticationKeyChain.KeyChainType)}
     * to get a proper P2SH address</p>
     */

    public List<DeterministicKey> freshKeys(AuthenticationKeyChain.KeyChainType type, int numberOfKeys) {
        DeterministicKeyChain chain = getKeyChain(type);
        if (chain.isMarried()) {
            throw new UnsupportedOperationException("Key is not suitable to receive coins for married keychains." +
                    " Use freshAddress to get P2SH address instead");
        }
        return chain.getKeys(KeyChain.KeyPurpose.AUTHENTICATION, numberOfKeys);   // Always returns the next key along the key chain.
    }

    /**
     * Returns address for a {@link #freshKey(AuthenticationKeyChain.KeyChainType)}
     */
    public Address freshAddress(AuthenticationKeyChain.KeyChainType type) {
        DeterministicKeyChain chain = getKeyChain(type);
        if (chain.isMarried()) {
            Script outputScript = chain.freshOutputScript(KeyChain.KeyPurpose.AUTHENTICATION);
            checkState(outputScript.isPayToScriptHash()); // Only handle P2SH for now
            Address freshAddress = Address.fromP2SHScript(params, outputScript);
            maybeLookaheadScripts();
            currentAddresses.put(KeyChain.KeyPurpose.AUTHENTICATION, freshAddress);
            return freshAddress;
        } else {
            return Address.fromKey(params, freshKey(type));
        }
    }

    protected static HashMap<AuthenticationKeyChain.KeyChainType, DeterministicKey> createCurrentAuthenticationKeysMap(List<DeterministicKeyChain> chains) {

        HashMap<AuthenticationKeyChain.KeyChainType, DeterministicKey> currentKeys = new HashMap<AuthenticationKeyChain.KeyChainType, DeterministicKey>(chains.size());

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
                currentKeys.put(((AuthenticationKeyChain)chain).type, currentExternalKey);
            }
        }
        return currentKeys;
    }

    /** If the given key is "current", advance the current key to a new one. */
    protected void maybeMarkCurrentKeyAsUsed(DeterministicKey key) {
        // It's OK for currentKeys to be empty here: it means we're a married wallet and the key may be a part of a
        // rotating chain.
        for (Map.Entry<AuthenticationKeyChain.KeyChainType, DeterministicKey> entry : currentAuthenticationKeys.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equals(key)) {
                log.info("Marking key as used: {}", key);
                currentAuthenticationKeys.put(entry.getKey(), freshKey(getKeyChainType()));
                return;
            }
        }
    }

    protected AuthenticationKeyChain.KeyChainType getKeyChainType() {
        return chains.size() > 0 ? ((AuthenticationKeyChain)chains.get(0)).type : null;
    }
}
