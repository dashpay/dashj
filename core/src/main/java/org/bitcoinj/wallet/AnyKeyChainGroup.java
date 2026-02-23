/*
 * Copyright 2014 Mike Hearn
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

package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.HDUtils;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.crypto.LinuxSecureRandom;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.Script.ScriptType;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.listeners.CurrentKeyChangeEventListener;
import org.bitcoinj.wallet.listeners.KeyChainEventListener;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * <p>A KeyChainGroup is used by the {@link Wallet} and manages: a {@link BasicKeyChain} object
 * (which will normally be empty), and zero or more {@link AnyDeterministicKeyChain}s. The last added
 * deterministic keychain is always the default active keychain, that's the one we normally derive keys and
 * addresses from.</p>
 *
 * <p>There can be active keychains for each output script type. However this class almost entirely only works on
 * the default active keychain (see {@link #getActiveKeyChain()}). The other active keychains
 * (see {@link #getActiveKeyChain(ScriptType, long)}) are meant as fallback for if a sender doesn't understand a
 * certain new script type (e.g. P2WPKH which comes with the new Bech32 address format). Active keychains
 * share the same seed, so that upgrading the wallet
 * (see {@link #upgradeToDeterministic(ScriptType, KeyChainGroupStructure, long, KeyParameter)}) to understand
 * a new script type doesn't require a fresh backup.</p>
 *
 * <p>If a key rotation time is set, it may be necessary to add a new BLSDeterministicKeyChain with a fresh seed
 * and also preserve the old one, so funds can be swept from the rotating keys. In this case, there may be
 * more than one deterministic chain. The latest chain is called the active chain and is where new keys are served
 * from.</p>
 *
 * <p>The wallet delegates most key management tasks to this class. It is <b>not</b> thread safe and requires external
 * locking, i.e. by the wallet lock. The group then in turn delegates most operations to the key chain objects,
 * combining their responses together when necessary.</p>
 *
 * <p>Deterministic key chains have a concept of a lookahead size and threshold. Please see the discussion in the
 * class docs for {@link AnyDeterministicKeyChain} for more information on this topic.</p>
 */
public class AnyKeyChainGroup implements IKeyBag {

    /**
     * Builder for {@link AnyKeyChainGroup}. Use {@link AnyKeyChainGroup#builder(NetworkParameters, KeyFactory)} to acquire an instance.
     */
    public static class Builder {
        private final NetworkParameters params;
        private final KeyChainGroupStructure structure;
        private final List<AnyDeterministicKeyChain> chains = new LinkedList<>();
        private int lookaheadSize = -1, lookaheadThreshold = -1;

        private final KeyFactory keyFactory;

        private Builder(NetworkParameters params, KeyChainGroupStructure structure, KeyFactory keyFactory) {
            this.params = params;
            this.structure = structure;
            this.keyFactory = keyFactory;
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
        public Builder fromRandom(ScriptType outputScriptType) {
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
        public Builder fromSeed(DeterministicSeed seed, ScriptType outputScriptType) {
            if (outputScriptType == ScriptType.P2PKH) {
                AnyDeterministicKeyChain chain = AnyDeterministicKeyChain.builder().seed(seed)
                        .outputScriptType(ScriptType.P2PKH)
                        .accountPath(structure.accountPathFor(ScriptType.P2PKH)).build();
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
        public Builder addChain(AnyDeterministicKeyChain chain) {
            this.chains.add(chain);
            return this;
        }

        /**
         * Add multiple chains.
         * @param chains to add
         */
        public Builder chains(List<AnyDeterministicKeyChain> chains) {
            this.chains.clear();
            this.chains.addAll(chains);
            return this;
        }

        /**
         * Set a custom lookahead size for all deterministic chains
         * @param lookaheadSize lookahead size
         */
        public Builder lookaheadSize(int lookaheadSize) {
            this.lookaheadSize = lookaheadSize;
            return this;
        }

        /**
         * Set a custom lookahead threshold for all deterministic chains
         * @param lookaheadThreshold lookahead threshold
         */
        public Builder lookaheadThreshold(int lookaheadThreshold) {
            this.lookaheadThreshold = lookaheadThreshold;
            return this;
        }

        public AnyKeyChainGroup build() {
            return new AnyKeyChainGroup(params, null, chains, lookaheadSize, lookaheadThreshold, null, null, keyFactory);
        }
    }

    static {
        // Init proper random number generator, as some old Android installations have bugs that make it unsecure.
        if (Utils.isAndroidRuntime())
            new LinuxSecureRandom();
    }

    private static final Logger log = LoggerFactory.getLogger(AnyKeyChainGroup.class);

    private AnyBasicKeyChain basic;
    protected final NetworkParameters params;
    // Keychains for deterministically derived keys. If this is null, no chains should be created automatically.
    protected final @Nullable LinkedList<AnyDeterministicKeyChain> chains;
    // currentKeys is used for normal, non-multisig/married wallets. currentAddresses is used when we're handing out
    // P2SH addresses. They're mutually exclusive.
    private final EnumMap<KeyChain.KeyPurpose, IDeterministicKey> currentKeys;
    protected final EnumMap<KeyChain.KeyPurpose, Address> currentAddresses;
    @Nullable private KeyCrypter keyCrypter;
    private int lookaheadSize = -1;
    private int lookaheadThreshold = -1;
    protected KeyFactory keyFactory;

    private final CopyOnWriteArrayList<ListenerRegistration<CurrentKeyChangeEventListener>> currentKeyChangeListeners = new CopyOnWriteArrayList<>();

    /** Creates a keychain group with just a basic chain. No deterministic chains will be created automatically. */
    public static AnyKeyChainGroup createBasic(NetworkParameters params, KeyFactory keyFactory) {
        return new AnyKeyChainGroup(params, new AnyBasicKeyChain(keyFactory), null, -1, -1, null, null, keyFactory);
    }

    public static AnyKeyChainGroup.Builder builder(NetworkParameters params, KeyFactory keyFactory) {
        return new Builder(params, KeyChainGroupStructure.DEFAULT, keyFactory);
    }

    public static AnyKeyChainGroup.Builder builder(NetworkParameters params, KeyChainGroupStructure structure, KeyFactory keyFactory) {
        return new Builder(params, structure, keyFactory);
    }

    protected AnyKeyChainGroup(NetworkParameters params, @Nullable AnyBasicKeyChain basicKeyChain,
                               @Nullable List<AnyDeterministicKeyChain> chains, int lookaheadSize, int lookaheadThreshold,
                               @Nullable EnumMap<KeyChain.KeyPurpose, IDeterministicKey> currentKeys, @Nullable KeyCrypter crypter,
                               KeyFactory keyFactory) {
        this.params = params;
        this.basic = basicKeyChain == null ? new AnyBasicKeyChain(crypter, keyFactory) : basicKeyChain;
        this.keyFactory = keyFactory;
        if (chains != null) {
            if (lookaheadSize > -1)
                this.lookaheadSize = lookaheadSize;
            else if (params.getId().equals(NetworkParameters.ID_UNITTESTNET))
                this.lookaheadSize = 5; // Cut down excess computation for unit tests.
            if (lookaheadThreshold > -1)
                this.lookaheadThreshold = lookaheadThreshold;
            this.chains = new LinkedList<>(chains);
            for (AnyDeterministicKeyChain chain : this.chains) {
                if (this.lookaheadSize > -1)
                    chain.setLookaheadSize(this.lookaheadSize);
                if (this.lookaheadThreshold > -1)
                    chain.setLookaheadThreshold(this.lookaheadThreshold);
            }
        } else {
            this.chains = null;
        }
        this.keyCrypter = crypter;
        this.currentKeys = currentKeys == null
                ? new EnumMap<>(KeyChain.KeyPurpose.class)
                : currentKeys;
        this.currentAddresses = new EnumMap<>(KeyChain.KeyPurpose.class);

        if (isMarried()) {
            maybeLookaheadScripts();
            for (Map.Entry<KeyChain.KeyPurpose, IDeterministicKey> entry : this.currentKeys.entrySet()) {
                Address address = ScriptBuilder
                        .createP2SHOutputScript(getActiveKeyChain().getRedeemData(entry.getValue()).redeemScript)
                        .getToAddress(params);
                currentAddresses.put(entry.getKey(), address);
            }
        }
    }

    /** Returns true if it contains any deterministic keychain or one could be created. */
    public boolean isSupportsDeterministicChains() {
        return chains != null;
    }

    // This keeps married redeem data in sync with the number of keys issued
    protected void maybeLookaheadScripts() {
        for (AnyDeterministicKeyChain chain : chains) {
            chain.maybeLookAheadScripts();
        }
    }

    /**
     * Adds an HD chain to the chains list, and make it the default chain (from which keys are issued).
     * Useful for adding a complex pre-configured keychain, such as a married wallet.
     */
    public void addAndActivateHDChain(AnyDeterministicKeyChain chain) {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        log.info("Activating a new HD chain: {}", chain);
        for (ListenerRegistration<KeyChainEventListener> registration : basic.getListeners())
            chain.addEventListener(registration.listener, registration.executor);
        if (lookaheadSize >= 0)
            chain.setLookaheadSize(lookaheadSize);
        if (lookaheadThreshold >= 0)
            chain.setLookaheadThreshold(lookaheadThreshold);
        chains.add(chain);
        currentKeys.clear();
        currentAddresses.clear();
        queueOnCurrentKeyChanged();
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
    public IDeterministicKey currentKey(KeyChain.KeyPurpose purpose) {
        AnyDeterministicKeyChain chain = getActiveKeyChain();
        if (chain.isMarried()) {
            throw new UnsupportedOperationException("Key is not suitable to receive coins for married keychains." +
                                                    " Use freshAddress to get P2SH address instead");
        }
        IDeterministicKey current = currentKeys.get(purpose);
        if (current == null) {
            current = freshKey(purpose);
            currentKeys.put(purpose, current);
        }
        return current;
    }

    /**
     * Returns address for a {@link #currentKey(KeyChain.KeyPurpose)}
     */
    public Address currentAddress(KeyChain.KeyPurpose purpose) {
        AnyDeterministicKeyChain chain = getActiveKeyChain();
        ScriptType outputScriptType = chain.getOutputScriptType();
        if (chain.isMarried()) {
            Address current = currentAddresses.get(purpose);
            if (current == null) {
                current = freshAddress(purpose);
                currentAddresses.put(purpose, current);
            }
            return current;
        } else if (outputScriptType == ScriptType.P2PKH) {
            return Address.fromKey(params, currentKey(purpose), outputScriptType);
        } else {
            throw new IllegalStateException(chain.getOutputScriptType().toString());
        }
    }

    /**
     * Returns a key that has not been returned by this method before (fresh). You can think of this as being
     * a newly created key, although the notion of "create" is not really valid for a
     * {@link AnyDeterministicKeyChain}. When the parameter is
     * {@link KeyChain.KeyPurpose#RECEIVE_FUNDS} the returned key is suitable for being put
     * into a receive coins wizard type UI. You should use this when the user is definitely going to hand this key out
     * to someone who wishes to send money.
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #freshAddress(KeyChain.KeyPurpose)}
     * to get a proper P2SH address</p>
     */
    public IDeterministicKey freshKey(KeyChain.KeyPurpose purpose) {
        return freshKeys(purpose, 1).get(0);
    }

    /**
     * Returns a key/s that have not been returned by this method before (fresh). You can think of this as being
     * newly created key/s, although the notion of "create" is not really valid for a
     * {@link AnyDeterministicKeyChain}. When the parameter is
     * {@link KeyChain.KeyPurpose#RECEIVE_FUNDS} the returned key is suitable for being put
     * into a receive coins wizard type UI. You should use this when the user is definitely going to hand this key out
     * to someone who wishes to send money.
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #freshAddress(KeyChain.KeyPurpose)}
     * to get a proper P2SH address</p>
     */
    public List<IDeterministicKey> freshKeys(KeyChain.KeyPurpose purpose, int numberOfKeys) {
        AnyDeterministicKeyChain chain = getActiveKeyChain();
        if (chain.isMarried()) {
            throw new UnsupportedOperationException("Key is not suitable to receive coins for married keychains." +
                    " Use freshAddress to get P2SH address instead");
        }
        return chain.getKeys(purpose, numberOfKeys);   // Always returns the next key along the key chain.
    }

    /**
     * <p>Returns a fresh address for a given {@link KeyChain.KeyPurpose} and of a given
     * {@link ScriptType}.</p>
     * <p>This method is meant for when you really need a fallback address. Normally, you should be
     * using {@link #freshAddress(KeyChain.KeyPurpose)} or
     * {@link #currentAddress(KeyChain.KeyPurpose)}.</p>
     */
    public Address freshAddress(KeyChain.KeyPurpose purpose, ScriptType outputScriptType, long keyRotationTimeSecs) {
        AnyDeterministicKeyChain chain = getActiveKeyChain(outputScriptType, keyRotationTimeSecs);
        return Address.fromKey(params, chain.getKey(purpose), outputScriptType);
    }

    /**
     * Returns address for a {@link #freshKey(KeyChain.KeyPurpose)}
     */
    public Address freshAddress(KeyChain.KeyPurpose purpose) {
        AnyDeterministicKeyChain chain = getActiveKeyChain();
        ScriptType outputScriptType = chain.getOutputScriptType();
        if (chain.isMarried()) {
            Script outputScript = chain.freshOutputScript(purpose);
            checkState(ScriptPattern.isP2SH(outputScript)); // Only handle P2SH for now
            Address freshAddress = Address.fromScriptHash(params,
                    ScriptPattern.extractHashFromP2SH(outputScript));
            maybeLookaheadScripts();
            currentAddresses.put(purpose, freshAddress);
            return freshAddress;
        } else if (outputScriptType == ScriptType.P2PKH) {
            return Address.fromKey(params, freshKey(purpose), outputScriptType);
        } else {
            throw new IllegalStateException(chain.getOutputScriptType().toString());
        }
    }

    /**
     * Returns the key chains that are used for generation of fresh/current keys, in the order of how they
     * were added. The default active chain will come last in the list.
     */
    public List<AnyDeterministicKeyChain> getActiveKeyChains(long keyRotationTimeSecs) {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        List<AnyDeterministicKeyChain> activeChains = new LinkedList<>();
        for (AnyDeterministicKeyChain chain : chains)
            if (chain.getEarliestKeyCreationTime() >= keyRotationTimeSecs)
                activeChains.add(chain);
        return activeChains;
    }

    /**
     * Returns the key chain that's used for generation of fresh/current keys of the given type. If it's not the default
     * type and no active chain for this type exists, {@code null} is returned. No upgrade or downgrade is tried.
     */
    public final AnyDeterministicKeyChain getActiveKeyChain(ScriptType outputScriptType, long keyRotationTimeSecs) {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        for (AnyDeterministicKeyChain chain : ImmutableList.copyOf(chains).reverse())
            if (chain.getOutputScriptType() == outputScriptType
                    && chain.getEarliestKeyCreationTime() >= keyRotationTimeSecs)
                return chain;
        return null;
    }

    /**
     * Returns the key chain that's used for generation of default fresh/current keys. This is always the newest
     * deterministic chain. If no deterministic chain is present but imported keys instead, a deterministic upgrate is
     * tried.
     */
    public final AnyDeterministicKeyChain getActiveKeyChain() {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        if (chains.isEmpty())
            throw new DeterministicUpgradeRequiredException();
        return chains.get(chains.size() - 1);
    }

    /**
     * Merge all active chains from the given keychain group into this keychain group.
     */
    public final void mergeActiveKeyChains(AnyKeyChainGroup from, long keyRotationTimeSecs) {
        checkArgument(isEncrypted() == from.isEncrypted(), "encrypted and non-encrypted keychains cannot be mixed");
        for (AnyDeterministicKeyChain chain : from.getActiveKeyChains(keyRotationTimeSecs))
            addAndActivateHDChain(chain);
    }

    /**
     * Gets the current lookahead size being used for ALL deterministic key chains. See
     * {@link AnyDeterministicKeyChain#setLookaheadSize(int)}
     * for more information.
     */
    public int getLookaheadSize() {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        if (lookaheadSize == -1)
            return getActiveKeyChain().getLookaheadSize();
        else
            return lookaheadSize;
    }

    /**
     * Gets the current lookahead threshold being used for ALL deterministic key chains. See
     * {@link AnyDeterministicKeyChain#setLookaheadThreshold(int)}
     * for more information.
     */
    public int getLookaheadThreshold() {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        if (lookaheadThreshold == -1)
            return getActiveKeyChain().getLookaheadThreshold();
        else
            return lookaheadThreshold;
    }

    /** Imports the given keys into the basic chain, creating it if necessary. */
    public int importKeys(List<IKey> keys) {
        return basic.importKeys(keys);
    }

    /** Imports the given keys into the basic chain, creating it if necessary. */
    public int importKeys(IKey... keys) {
        return importKeys(ImmutableList.copyOf(keys));
    }

    public boolean checkPassword(CharSequence password) {
        checkState(keyCrypter != null, "Not encrypted");
        return checkAESKey(keyCrypter.deriveKey(password));
    }

    public boolean checkAESKey(KeyParameter aesKey) {
        checkState(keyCrypter != null, "Not encrypted");
        if (basic.numKeys() > 0)
            return basic.checkAESKey(aesKey);
        return getActiveKeyChain().checkAESKey(aesKey);
    }

    /** Imports the given unencrypted keys into the basic chain, encrypting them along the way with the given key. */
    public int importKeysAndEncrypt(final List<IKey> keys, KeyParameter aesKey) {
        // TODO: Firstly check if the aes key can decrypt any of the existing keys successfully.
        checkState(keyCrypter != null, "Not encrypted");
        LinkedList<IKey> encryptedKeys = Lists.newLinkedList();
        for (IKey key : keys) {
            if (key.isEncrypted())
                throw new IllegalArgumentException("Cannot provide already encrypted keys");
            encryptedKeys.add(key.encrypt(keyCrypter, aesKey));
        }
        return importKeys(encryptedKeys);
    }

    @Override
    @Nullable
    public IRedeemData findRedeemDataFromScriptHash(byte[] scriptHash) {
        if (chains != null) {
            // Iterate in reverse order, since the active keychain is the one most likely to have the hit
            for (Iterator<AnyDeterministicKeyChain> iter = chains.descendingIterator(); iter.hasNext();) {
                AnyDeterministicKeyChain chain = iter.next();
                IRedeemData redeemData = chain.findRedeemDataByScriptHash(ByteString.copyFrom(scriptHash));
                if (redeemData != null)
                    return redeemData;
            }
        }
        return null;
    }

    public void markP2SHAddressAsUsed(Address address) {
        checkArgument(address.getOutputScriptType() == ScriptType.P2SH);
        IRedeemData data = findRedeemDataFromScriptHash(address.getHash());
        if (data == null)
            return;   // Not our P2SH address.
        for (IKey key : data.keys) {
            for (AnyDeterministicKeyChain chain : chains) {
                IDeterministicKey k = chain.findKeyFromPubKey(key.getPubKey());
                if (k == null) continue;
                chain.markKeyAsUsed(k);
                maybeMarkCurrentAddressAsUsed(address);
            }
        }
    }

    @Nullable
    @Override
    public IKey findKeyFromPubKeyHash(byte[] pubKeyHash, @Nullable ScriptType scriptType) {
        IKey result;
        // BasicKeyChain can mix output script types.
        if ((result = basic.findKeyFromPubHash(pubKeyHash)) != null)
            return result;
        if (chains != null) {
            for (AnyDeterministicKeyChain chain : chains) {
                // This check limits BLSDeterministicKeyChain to specific output script usage.
                if (scriptType != null && scriptType != chain.getOutputScriptType())
                    continue;
                if ((result = chain.findKeyFromPubHash(pubKeyHash)) != null)
                    return result;
            }
        }
        return null;
    }

    /**
     * Mark the DeterministicKeys as used, if they match the pubKeyHash
     * See {@link AnyDeterministicKeyChain#markKeyAsUsed(IDeterministicKey)} for more info on this.
     */
    public void markPubKeyHashAsUsed(byte[] pubKeyHash) {
        if (chains != null) {
            for (AnyDeterministicKeyChain chain : chains) {
                IDeterministicKey key;
                if ((key = chain.markPubHashAsUsed(pubKeyHash)) != null) {
                    maybeMarkCurrentKeyAsUsed(key);
                    return;
                }
            }
        }
    }

    /** If the given P2SH address is "current", advance it to a new one. */
    private void maybeMarkCurrentAddressAsUsed(Address address) {
        checkArgument(address.getOutputScriptType() == ScriptType.P2SH);
        for (Map.Entry<KeyChain.KeyPurpose, Address> entry : currentAddresses.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equals(address)) {
                log.info("Marking P2SH address as used: {}", address);
                currentAddresses.put(entry.getKey(), freshAddress(entry.getKey()));
                queueOnCurrentKeyChanged();
                return;
            }
        }
    }

    /** If the given key is "current", advance the current key to a new one. */
    protected void maybeMarkCurrentKeyAsUsed(IDeterministicKey key) {
        // It's OK for currentKeys to be empty here: it means we're a married wallet and the key may be a part of a
        // rotating chain.
        for (Map.Entry<KeyChain.KeyPurpose, IDeterministicKey> entry : currentKeys.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equals(key)) {
                log.info("Marking key as used: {}", key);
                currentKeys.put(entry.getKey(), freshKey(entry.getKey()));
                queueOnCurrentKeyChanged();
                return;
            }
        }
    }

    public boolean hasKey(IKey key) {
        if (basic.hasKey(key))
            return true;
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                if (chain.hasKey(key))
                    return true;
        return false;
    }

    @Nullable
    @Override
    public IKey findKeyFromPubKey(byte[] pubKey) {
        IKey result;
        if ((result = basic.findKeyFromPubKey(pubKey)) != null)
            return result;
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                if ((result = chain.findKeyFromPubKey(pubKey)) != null)
                    return result;
        return null;
    }

    /**
     * Mark the DeterministicKeys as used, if they match the pubkey
     * See {@link AnyDeterministicKeyChain#markKeyAsUsed(IDeterministicKey)} for more info on this.
     */
    public void markPubKeyAsUsed(byte[] pubkey) {
        if (chains != null) {
            for (AnyDeterministicKeyChain chain : chains) {
                IDeterministicKey key;
                if ((key = chain.markPubKeyAsUsed(pubkey)) != null) {
                    maybeMarkCurrentKeyAsUsed(key);
                    return;
                }
            }
        }
    }

    /** Returns the number of keys managed by this group, including the lookahead buffers. */
    public int numKeys() {
        int result = basic.numKeys();
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                result += chain.numKeys();
        return result;
    }

    /**
     * Returns number of keys used on external and internal paths.
     */
    public int getTotalIssuedKeys() {
        int result = basic.numKeys();
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                result += chain.numLeafKeysIssued();
        return result;
    }

    /**
     * Removes a key that was imported into the basic key chain. You cannot remove deterministic keys.
     * @throws IllegalArgumentException if the key is deterministic.
     */
    public boolean removeImportedKey(IKey key) {
        checkNotNull(key);
        checkArgument(!(key instanceof IDeterministicKey));
        return basic.removeKey(key);
    }

    /**
     * Whether the active keychain is married.  A keychain is married when it vends P2SH addresses
     * from multiple keychains in a multisig relationship.
     * @see MarriedKeyChain
     */
    public final boolean isMarried() {
        return chains != null && !chains.isEmpty() && getActiveKeyChain().isMarried();
    }

    /**
     * Encrypt the keys in the group using the KeyCrypter and the AES key. A good default KeyCrypter to use is
     * {@link KeyCrypterScrypt}.
     *
     * @throws KeyCrypterException Thrown if the wallet encryption fails for some reason,
     *         leaving the group unchanged.
     * @throws DeterministicUpgradeRequiredException Thrown if there are random keys but no HD chain.
     */
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        checkNotNull(keyCrypter);
        checkNotNull(aesKey);
        checkState((chains != null && !chains.isEmpty()) || basic.numKeys() != 0, "can't encrypt entirely empty wallet");
        // This code must be exception safe.

        AnyBasicKeyChain newBasic = basic.toEncrypted(keyCrypter, aesKey);
        List<AnyDeterministicKeyChain> newChains = new ArrayList<>();
        if (chains != null) {
            for (AnyDeterministicKeyChain chain : chains)
                newChains.add(chain.toEncrypted(keyCrypter, aesKey));
        }
        this.keyCrypter = keyCrypter;
        this.basic = newBasic;
        if (chains != null) {
            this.chains.clear();
            this.chains.addAll(newChains);
        }
    }

    /**
     * Decrypt the keys in the group using the previously given key crypter and the AES key. A good default
     * KeyCrypter to use is {@link KeyCrypterScrypt}.
     *
     * @throws KeyCrypterException Thrown if the wallet decryption fails for some reason, leaving the group unchanged.
     */
    public void decrypt(KeyParameter aesKey) {
        checkNotNull(aesKey);

        AnyBasicKeyChain newBasic = basic.toDecrypted(aesKey);
        if (chains != null) {
            List<AnyDeterministicKeyChain> newChains = new ArrayList<>(chains.size());
            for (AnyDeterministicKeyChain chain : chains)
                newChains.add(chain.toDecrypted(aesKey));

            // Code below this point must be exception safe.
            this.chains.clear();
            this.chains.addAll(newChains);
        }
        this.basic = newBasic;
        this.keyCrypter = null;
    }

    /** Returns true if the group is encrypted. */
    public boolean isEncrypted() {
        return keyCrypter != null;
    }

    /**
     * Returns whether this chain has only watching keys (unencrypted keys with no private part). Mixed chains are
     * forbidden.
     * 
     * @throws IllegalStateException if there are no keys, or if there is a mix between watching and non-watching keys.
     */
    public boolean isWatching() {
        BasicKeyChain.State basicState = basic.isWatching();
        BasicKeyChain.State activeState = BasicKeyChain.State.EMPTY;
        if (chains != null && !chains.isEmpty()) {
            if (getActiveKeyChain().isWatching())
                activeState = BasicKeyChain.State.WATCHING;
            else
                activeState = BasicKeyChain.State.REGULAR;
        }
        if (basicState == BasicKeyChain.State.EMPTY) {
            if (activeState == BasicKeyChain.State.EMPTY)
                throw new IllegalStateException("Empty key chain group: cannot answer isWatching() query");
            return activeState == BasicKeyChain.State.WATCHING;
        } else if (activeState == BasicKeyChain.State.EMPTY)
            return basicState == BasicKeyChain.State.WATCHING;
        else {
            if (activeState != basicState)
                throw new IllegalStateException("Mix of watching and non-watching keys in wallet");
            return activeState == BasicKeyChain.State.WATCHING;
        }
    }

    /** Returns the key crypter or null if the group is not encrypted. */
    @Nullable public KeyCrypter getKeyCrypter() { return keyCrypter; }

    /**
     * Returns a list of the non-deterministic keys that have been imported into the wallet, or the empty list if none.
     */
    public List<IKey> getImportedKeys() {
        return basic.getKeys();
    }

    public long getEarliestKeyCreationTime() {
        long time = basic.getEarliestKeyCreationTime();   // Long.MAX_VALUE if empty.
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                time = Math.min(time, chain.getEarliestKeyCreationTime());
        return time;
    }

    public int getBloomFilterElementCount() {
        int result = basic.numBloomFilterEntries();
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                result += chain.numBloomFilterEntries();
        return result;
    }

    public BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak) {
        BloomFilter filter = new BloomFilter(size, falsePositiveRate, nTweak);
        if (basic.numKeys() > 0)
            filter.merge(basic.getFilter(size, falsePositiveRate, nTweak));
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                filter.merge(chain.getFilter(size, falsePositiveRate, nTweak));
        return filter;
    }

    public boolean isRequiringUpdateAllBloomFilter() {
        throw new UnsupportedOperationException();   // Unused.
    }

    /** Adds a listener for events that are run when keys are added, on the user thread. */
    public void addEventListener(KeyChainEventListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
    }

    /** Adds a listener for events that are run when keys are added, on the given executor. */
    public void addEventListener(KeyChainEventListener listener, Executor executor) {
        checkNotNull(listener);
        checkNotNull(executor);
        basic.addEventListener(listener, executor);
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                chain.addEventListener(listener, executor);
    }

    /** Removes a listener for events that are run when keys are added. */
    public boolean removeEventListener(KeyChainEventListener listener) {
        checkNotNull(listener);
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                chain.removeEventListener(listener);
        return basic.removeEventListener(listener);
    }

    /** Adds a listener for events that are run when a current key and/or address changes. */
    public void addCurrentKeyChangeEventListener(CurrentKeyChangeEventListener listener) {
        addCurrentKeyChangeEventListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds a listener for events that are run when a current key and/or address changes, on the given
     * executor.
     */
    public void addCurrentKeyChangeEventListener(CurrentKeyChangeEventListener listener, Executor executor) {
        checkNotNull(listener);
        currentKeyChangeListeners.add(new ListenerRegistration<>(listener, executor));
    }

    /** Removes a listener for events that are run when a current key and/or address changes. */
    public boolean removeCurrentKeyChangeEventListener(CurrentKeyChangeEventListener listener) {
        checkNotNull(listener);
        return ListenerRegistration.removeFromList(listener, currentKeyChangeListeners);
    }

    protected void queueOnCurrentKeyChanged() {
        for (final ListenerRegistration<CurrentKeyChangeEventListener> registration : currentKeyChangeListeners) {
            registration.executor.execute(new Runnable() {
                @Override
                public void run() {
                    registration.listener.onCurrentKeyChanged();
                }
            });
        }
    }

    /** Returns a list of key protobufs obtained by merging the chains. */
    public List<Protos.Key> serializeToProtobuf() {
        List<Protos.Key> result;
        if (basic != null)
            result = basic.serializeToProtobuf();
        else
            result = new ArrayList<>();
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                result.addAll(chain.serializeToProtobuf());
        return result;
    }

    static AnyKeyChainGroup fromProtobufUnencrypted(NetworkParameters params, List<Protos.Key> keys, KeyFactory keyFactory, boolean hardenedKeysOnly) throws UnreadableWalletException {
        return fromProtobufUnencrypted(params, keys, new AnyDefaultKeyChainFactory(), keyFactory, hardenedKeysOnly);
    }

    public static AnyKeyChainGroup fromProtobufUnencrypted(NetworkParameters params, List<Protos.Key> keys, AnyKeyChainFactory factory, KeyFactory keyFactory, boolean hardenedKeysOnly) throws UnreadableWalletException {
        AnyBasicKeyChain basicKeyChain = AnyBasicKeyChain.fromProtobufUnencrypted(keys, keyFactory);
        List<AnyDeterministicKeyChain> chains = AnyDeterministicKeyChain.fromProtobuf(keys, null, factory, keyFactory, hardenedKeysOnly);
        int lookaheadSize = -1, lookaheadThreshold = -1;
        EnumMap<KeyChain.KeyPurpose, IDeterministicKey> currentKeys = null;
        if (!chains.isEmpty()) {
            AnyDeterministicKeyChain activeChain = chains.get(chains.size() - 1);
            lookaheadSize = activeChain.getLookaheadSize();
            lookaheadThreshold = activeChain.getLookaheadThreshold();
            currentKeys = createCurrentKeysMap(chains);
        }
        extractFollowingKeychains(chains);
        return new AnyKeyChainGroup(params, basicKeyChain, chains, lookaheadSize, lookaheadThreshold, currentKeys, null, keyFactory);
    }

    static AnyKeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys, KeyCrypter crypter, KeyFactory keyFactory, boolean hardenedKeysOnly) throws UnreadableWalletException {
        return fromProtobufEncrypted(params, keys, crypter, new AnyDefaultKeyChainFactory(), keyFactory, hardenedKeysOnly);
    }

    public static AnyKeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys, KeyCrypter crypter, AnyKeyChainFactory factory, KeyFactory keyFactory, boolean hardenedKeysOnly) throws UnreadableWalletException {
        checkNotNull(crypter);
        AnyBasicKeyChain basicKeyChain = AnyBasicKeyChain.fromProtobufEncrypted(keys, crypter, keyFactory);
        List<AnyDeterministicKeyChain> chains = AnyDeterministicKeyChain.fromProtobuf(keys, crypter, factory, keyFactory, hardenedKeysOnly);
        int lookaheadSize = -1, lookaheadThreshold = -1;
        EnumMap<KeyChain.KeyPurpose, IDeterministicKey> currentKeys = null;
        if (!chains.isEmpty()) {
            AnyDeterministicKeyChain activeChain = chains.get(chains.size() - 1);
            lookaheadSize = activeChain.getLookaheadSize();
            lookaheadThreshold = activeChain.getLookaheadThreshold();
            currentKeys = createCurrentKeysMap(chains);
        }
        extractFollowingKeychains(chains);
        return new AnyKeyChainGroup(params, basicKeyChain, chains, lookaheadSize, lookaheadThreshold, currentKeys, crypter, keyFactory);
    }

    /**
     * <p>This method will upgrade the wallet along the following path: {@code Basic --> P2PKH --> P2WPKH}</p>
     * <p>It won't skip any steps in that upgrade path because the user might be restoring from a backup and
     * still expects money on the P2PKH chain.</p>
     * <p>It will extract and reuse the seed from the current wallet, so that a fresh backup isn't required
     * after upgrading. If coming from a basic chain containing only random keys this means it will pick the
     * oldest non-rotating private key as a seed.</p>
     * <p>Note that for upgrading an encrypted wallet, the decryption key is needed. In future, we could skip
     * that requirement for a {@code P2PKH --> P2WPKH} upgrade and just clone the encryped seed, but currently
     * the key is needed even for that.</p>
     *
     * @param preferredScriptType desired script type for the active keychain
     * @param structure keychain group structure to derive an account path from
     * @param keyRotationTimeSecs If non-zero, UNIX time for which keys created before this are assumed to be
     *                            compromised or weak, those keys will not be used for deterministic upgrade.
     * @param aesKey If non-null, the encryption key the keychain is encrypted under. If the keychain is encrypted
     *               and this is not supplied, an exception is thrown letting you know you should ask the user for
     *               their password, turn it into a key, and then try again.
     * @throws IllegalStateException if there is already a deterministic key chain present or if there are
     *                                         no random keys (i.e. this is not an upgrade scenario), or if aesKey is
     *                                         provided but the wallet is not encrypted.
     * @throws IllegalArgumentException if the rotation time specified excludes all keys.
     * @throws DeterministicUpgradeRequiresPassword if the key chain group is encrypted
     *         and you should provide the users encryption key.
     */
    public void upgradeToDeterministic(ScriptType preferredScriptType, KeyChainGroupStructure structure,
                                       long keyRotationTimeSecs, @Nullable KeyParameter aesKey)
            throws DeterministicUpgradeRequiresPassword, AllRandomKeysRotating {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        checkNotNull(structure);
        checkArgument(keyRotationTimeSecs >= 0);
        if (!isDeterministicUpgradeRequired(preferredScriptType, keyRotationTimeSecs))
            return; // Nothing to do.

        // Basic --> P2PKH upgrade
        if (basic.numKeys() > 0 && getActiveKeyChain(ScriptType.P2PKH, keyRotationTimeSecs) == null) {
            // Subtract one because the key rotation time might have been set to the creation time of the first known good
            // key, in which case, that's the one we want to find.
            IKey keyToUse = basic.findOldestKeyAfter(keyRotationTimeSecs - 1);
            if (keyToUse == null)
                throw new AllRandomKeysRotating();
            boolean keyWasEncrypted = keyToUse.isEncrypted();
            if (keyWasEncrypted) {
                if (aesKey == null) {
                    // We can't auto upgrade because we don't know the users password at this point. We throw an
                    // exception so the calling code knows to abort the load and ask the user for their password, they can
                    // then try loading the wallet again passing in the AES key.
                    //
                    // There are a few different approaches we could have used here, but they all suck. The most obvious
                    // is to try and be as lazy as possible, running in the old random-wallet mode until the user enters
                    // their password for some other reason and doing the upgrade then. But this could result in strange
                    // and unexpected UI flows for the user, as well as complicating the job of wallet developers who then
                    // have to support both "old" and "new" UI modes simultaneously, switching them on the fly. Given that
                    // this is a one-off transition, it seems more reasonable to just ask the user for their password
                    // on startup, and then the wallet app can have all the widgets for accessing seed words etc active
                    // all the time.
                    throw new DeterministicUpgradeRequiresPassword();
                }
                keyToUse = keyToUse.decrypt(aesKey);
            } else if (aesKey != null) {
                throw new IllegalStateException("AES Key was provided but wallet is not encrypted.");
            }

            log.info(
                    "Upgrading from basic keychain to P2PKH deterministic keychain. Using oldest non-rotating private key (address: {})",
                    Address.fromKey(params, keyToUse));
            byte[] entropy = checkNotNull(keyToUse.getSecretBytes());
            // Private keys should be at least 128 bits long.
            checkState(entropy.length >= DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8);
            // We reduce the entropy here to 128 bits because people like to write their seeds down on paper, and 128
            // bits should be sufficient forever unless the laws of the universe change or ECC is broken; in either case
            // we all have bigger problems.
            entropy = Arrays.copyOfRange(entropy, 0, DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8);    // final argument is exclusive range.
            checkState(entropy.length == DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS / 8);
            AnyDeterministicKeyChain chain = AnyDeterministicKeyChain.builder()
                    .entropy(entropy, keyToUse.getCreationTimeSeconds())
                    .outputScriptType(ScriptType.P2PKH)
                    .accountPath(structure.accountPathFor(ScriptType.P2PKH)).build();
            if (keyWasEncrypted)
                chain = chain.toEncrypted(checkNotNull(keyCrypter), aesKey);
            addAndActivateHDChain(chain);
        }
    }

    /**
     * Returns true if a call to {@link #upgradeToDeterministic(ScriptType, KeyChainGroupStructure, long, KeyParameter)} is required
     * in order to have an active deterministic keychain of the desired script type.
     */
    public boolean isDeterministicUpgradeRequired(ScriptType preferredScriptType, long keyRotationTimeSecs) {
        if (!isSupportsDeterministicChains())
            return false;
        if (getActiveKeyChain(preferredScriptType, keyRotationTimeSecs) == null)
            return true;
        return false;
    }

    protected static EnumMap<KeyChain.KeyPurpose, IDeterministicKey> createCurrentKeysMap(List<AnyDeterministicKeyChain> chains) {
        AnyDeterministicKeyChain activeChain = chains.get(chains.size() - 1);

        EnumMap<KeyChain.KeyPurpose, IDeterministicKey> currentKeys = new EnumMap<>(KeyChain.KeyPurpose.class);

        // assuming that only RECEIVE and CHANGE keys are being used at the moment, we will treat latest issued external key
        // as current RECEIVE key and latest issued internal key as CHANGE key. This should be changed as soon as other
        // kinds of KeyPurpose are introduced.
        if (activeChain.getIssuedExternalKeys() > 0) {
            IDeterministicKey currentExternalKey = activeChain.getKeyByPath(
                    HDUtils.append(
                            HDUtils.concat(activeChain.getAccountPath(), activeChain.hardenedKeysOnly ? AnyDeterministicKeyChain.EXTERNAL_SUBPATH_HARDENED : AnyDeterministicKeyChain.EXTERNAL_SUBPATH),
                            new ChildNumber(activeChain.getIssuedExternalKeys() - 1)));
            currentKeys.put(KeyChain.KeyPurpose.RECEIVE_FUNDS, currentExternalKey);
        }

        if (activeChain.getIssuedInternalKeys() > 0) {
            IDeterministicKey currentInternalKey = activeChain.getKeyByPath(
                    HDUtils.append(
                            HDUtils.concat(activeChain.getAccountPath(), activeChain.hardenedKeysOnly ? AnyDeterministicKeyChain.INTERNAL_SUBPATH_HARDENED : AnyDeterministicKeyChain.INTERNAL_SUBPATH),
                            new ChildNumber(activeChain.getIssuedInternalKeys() - 1)));
            currentKeys.put(KeyChain.KeyPurpose.CHANGE, currentInternalKey);
        }
        return currentKeys;
    }

    private static void extractFollowingKeychains(List<AnyDeterministicKeyChain> chains) {
        // look for following key chains and map them to the watch keys of followed keychains
        List<AnyDeterministicKeyChain> followingChains = new ArrayList<>();
        for (Iterator<AnyDeterministicKeyChain> it = chains.iterator(); it.hasNext(); ) {
            AnyDeterministicKeyChain chain = it.next();
            if (chain.isFollowing()) {
                followingChains.add(chain);
                it.remove();
            } else if (!followingChains.isEmpty()) {
                //if (!(chain instanceof MarriedKeyChain))
                //    throw new IllegalStateException();
                //((MarriedKeyChain)chain).setFollowingKeyChains(followingChains);
                followingChains = new ArrayList<>();
            }
        }
    }

    public String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey) {
        final StringBuilder builder = new StringBuilder();
        if (basic != null)
            builder.append(basic.toString(includePrivateKeys, aesKey, params));
        if (chains != null)
            for (AnyDeterministicKeyChain chain : chains)
                builder.append(chain.toString(includeLookahead, includePrivateKeys, aesKey, params)).append('\n');
        return builder.toString();
    }

    /** Returns a copy of the current list of chains. */
    public List<AnyDeterministicKeyChain> getDeterministicKeyChains() {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        return new ArrayList<>(chains);
    }
    /**
     * Returns a counter that increases (by an arbitrary amount) each time new keys have been calculated due to
     * lookahead and thus the Bloom filter that was previously calculated has become stale.
     */
    public int getCombinedKeyLookaheadEpochs() {
        checkState(isSupportsDeterministicChains(), "doesn't support deterministic chains");
        int epoch = 0;
        for (AnyDeterministicKeyChain chain : chains)
            epoch += chain.getKeyLookaheadEpoch();
        return epoch;
    }

    public boolean hasKeyChains() {
        return chains.size() > 0;
    }
}
