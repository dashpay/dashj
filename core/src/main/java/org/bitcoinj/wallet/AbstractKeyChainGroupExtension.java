/*
 * Copyright (c) 2023 Dash Core Group
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

import com.google.common.collect.Lists;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.listeners.KeyChainEventListener;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkNotNull;
/**
 * Implements basic keychain functionality for a keychain extension
 */
abstract public class AbstractKeyChainGroupExtension implements KeyChainGroupExtension {
    protected final ReentrantLock keyChainGroupLock = Threading.lock("keychaingroup-extension");
    protected @Nullable Wallet wallet;

    protected AbstractKeyChainGroupExtension(Wallet wallet) {
        this.wallet = wallet;
    }

    abstract public AnyKeyChainGroup getKeyChainGroup();

    boolean isInitialized() {
        return getKeyChainGroup() != null;
    }

    public void addAndActivateHDChain(AnyDeterministicKeyChain keyChain) {
        keyChainGroupLock.lock();
        try {
            getKeyChainGroup().addAndActivateHDChain(keyChain);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     *  Check whether the password can decrypt the first key in the wallet.
     *  This can be used to check the validity of an entered password.
     *
     *  @return boolean true if password supplied can decrypt the first private key in the wallet, false otherwise.
     *  @throws IllegalStateException if the wallet is not encrypted.
     */
    public boolean checkPassword(CharSequence password) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().checkPassword(password);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     *  Check whether the AES key can decrypt the first encrypted key in the wallet.
     *
     *  @return boolean true if AES key supplied can decrypt the first encrypted private key in the wallet, false otherwise.
     */
    public boolean checkAESKey(KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().checkAESKey(aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /**
     * Get the wallet's KeyCrypter, or null if the wallet is not encrypted.
     * (Used in encrypting/ decrypting an IKey).
     */
    @Nullable
    public KeyCrypter getKeyCrypter() {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ?  getKeyChainGroup().getKeyCrypter() : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public Address currentAddress(KeyChain.KeyPurpose purpose) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().currentAddress(purpose) : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    public IDeterministicKey currentKey(KeyChain.KeyPurpose purpose) {
        keyChainGroupLock.lock();
        try {
            return getKeyChainGroup().currentKey(purpose);
        } finally {
            keyChainGroupLock.unlock();
        }
    }
    public IDeterministicKey currentReceiveKey() {
        return currentKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    @Override
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            if (isInitialized())
                getKeyChainGroup().encrypt(keyCrypter, aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public void decrypt(KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            if (isInitialized())
                getKeyChainGroup().decrypt(aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public IKey findKeyFromPubKey(byte[] pubKey) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().findKeyFromPubKey(pubKey) : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public IKey findKeyFromPubKeyHash(byte[] pubKeyHash, @Nullable Script.ScriptType scriptType) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().findKeyFromPubKeyHash(pubKeyHash, scriptType) : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public IRedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().findRedeemDataFromScriptHash(payToScriptHash) : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public IDeterministicKey freshKey(KeyChain.KeyPurpose purpose) {
        return freshKeys(purpose, 1).get(0);
    }

    @Override
    public List<IDeterministicKey> freshKeys(KeyChain.KeyPurpose purpose, int numberOfKeys) {
        List<IDeterministicKey> keys;
        keyChainGroupLock.lock();
        try {
            keys = getKeyChainGroup().freshKeys(purpose, numberOfKeys);
        } finally {
            keyChainGroupLock.unlock();
        }
        // Do we really need an immediate hard save? Arguably all this is doing is saving the 'current' key
        // and that's not quite so important, so we could coalesce for more performance.
        wallet.saveNow();
        return keys;
    }

    /**
     * Returns a key/s that has not been returned by this method before (fresh).
     */
    @Override
    public List<IDeterministicKey> freshKeys(int numberOfKeys) {
        List<IDeterministicKey> keys;
        checkNotNull(getKeyChainGroup(), "This wallet extension does not have any key chains.");
        keyChainGroupLock.lock();
        try {
            keys = freshKeys(KeyChain.KeyPurpose.RECEIVE_FUNDS, numberOfKeys);
        } finally {
            keyChainGroupLock.unlock();
        }
        // Do we really need an immediate hard save? Arguably all this is doing is saving the 'current' key
        // and that's not quite so important, so we could coalesce for more performance.
        wallet.saveNow();
        return keys;
    }

    @Override
    public IDeterministicKey freshReceiveKey() {
        return freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    /**
     * Returns address for a {@link #freshKey(KeyChain.KeyPurpose)}
     */
    @Override
    public Address freshAddress(KeyChain.KeyPurpose purpose) {
        Address key;
        keyChainGroupLock.lock();
        try {
            key = getKeyChainGroup().freshAddress(purpose);
        } finally {
            keyChainGroupLock.unlock();
        }
        wallet.saveNow();
        return key;
    }

    /**
     * An alias for calling {@link #freshAddress(KeyChain.KeyPurpose)} with
     * {@link KeyChain.KeyPurpose#RECEIVE_FUNDS} as the parameter.
     */
    @Override
    public Address freshReceiveAddress() {
        return freshAddress(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    /**
     * Returns only the keys that have been issued by {@link #freshKeys(int)}}.
     */
    @Override
    public List<IKey> getIssuedReceiveKeys() {
        keyChainGroupLock.lock();
        try {
            List<IKey> keys = new LinkedList<>();
            long keyRotationTimeSecs = wallet.getKeyRotationTime().getTime();
            for (final AnyDeterministicKeyChain chain : getActiveKeyChains(keyRotationTimeSecs))
                keys.addAll(chain.getIssuedReceiveKeys());
            return keys;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public AnyDeterministicKeyChain getActiveKeyChain() {
        keyChainGroupLock.lock();
        try {
            return getKeyChainGroup().getActiveKeyChain();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public List<AnyDeterministicKeyChain> getActiveKeyChains(long walletCreationTime) {
        keyChainGroupLock.lock();
        try {
            return getKeyChainGroup().getActiveKeyChains(walletCreationTime);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().getBloomFilter(size, falsePositiveRate, nTweak) : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public int getBloomFilterElementCount() {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().getBloomFilterElementCount() : 0;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public long getEarliestKeyCreationTime() {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().getEarliestKeyCreationTime() : Utils.currentTimeMillis();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /** See {@link DeterministicKeyChain#setLookaheadSize(int)} for more info on this. */
    @Override
    public int getLookaheadSize() {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().getLookaheadSize() : 0;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /** See {@link DeterministicKeyChain#setLookaheadThreshold(int)} for more info on this. */
    @Override
    public int getLookaheadThreshold() {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().getLookaheadThreshold() : 0;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public boolean hasKey(IKey key) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().hasKey(key);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public boolean hasKeyChains() {
        return isInitialized() && getKeyChainGroup().hasKeyChains();
    }

    @Override
    public int importKeys(List<IKey> keys) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().importKeys(keys) : 0;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public int importKeys(IKey... keys) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().importKeys(keys) : 0;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public int importKeysAndEncrypt(List<IKey> keys, KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().importKeysAndEncrypt(keys, aesKey) : 0;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public boolean isDeterministicUpgradeRequired(Script.ScriptType preferredScriptType, long keyRotationTimeSecs) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().isDeterministicUpgradeRequired(preferredScriptType, keyRotationTimeSecs);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public boolean isEncrypted() {
        return isInitialized() && getKeyChainGroup().isEncrypted();
    }

    /**
     * Whether the keychain is married.  A keychain is married when it vends P2SH addresses
     * from multiple keychains in a multisig relationship.
     * @see org.bitcoinj.wallet.MarriedKeyChain
     */
    public boolean isMarried() {
        return isInitialized() && getKeyChainGroup().isMarried();
    }

    @Override
    public boolean isWatching() {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().isWatching();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public void markPubKeyAsUsed(byte[] pubkey) {
        keyChainGroupLock.lock();
        try {
            if (isInitialized())
                getKeyChainGroup().markPubKeyAsUsed(pubkey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public void markPubKeyHashAsUsed(byte[] pubKeyHash) {
        keyChainGroupLock.lock();
        try {
            if (isInitialized())
                getKeyChainGroup().markPubKeyHashAsUsed(pubKeyHash);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public void markP2SHAddressAsUsed(Address address) {
        keyChainGroupLock.lock();
        try {
            if (isInitialized())
                getKeyChainGroup().markP2SHAddressAsUsed(address);
        } finally {
            keyChainGroupLock.unlock();
        }
    }
    /**
     * Returns the number of keys in the key chain group, including lookahead keys.
     */
    public int numKeys() {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().numKeys() : 0;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public boolean removeImportedKey(IKey key) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().removeImportedKey(key);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    /** Internal use only. */
    @Override
    public List<Protos.Key> serializeToProtobuf() {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().serializeToProtobuf() : Lists.newArrayList();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public void processTransaction(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType) {

    }


    /** Adds a listener for events that are run when keys are added, on the user thread. */
    public void addEventListener(KeyChainEventListener listener) {
        addEventListener(listener, Threading.USER_THREAD);
    }

    @Override
    public void addEventListener(KeyChainEventListener listener, Executor executor) {
        keyChainGroupLock.lock();
        try {
            if (isInitialized())
                getKeyChainGroup().addEventListener(listener, executor);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public boolean removeEventListener(KeyChainEventListener listener) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().removeEventListener(listener);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public void upgradeToDeterministic(Script.ScriptType preferredScriptType, KeyChainGroupStructure structure, long keyRotationTimeSecs, @Nullable KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            if(isInitialized())
                getKeyChainGroup().upgradeToDeterministic(preferredScriptType, structure, keyRotationTimeSecs, aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey, boolean includeDebugInfo) {
        return getWalletExtensionID() + ":\n" + (isInitialized() ? getKeyChainGroup().toString(includeLookahead, includePrivateKeys, aesKey) : "No keychains");
    }

    protected void saveWallet() {
        if (wallet != null)
            wallet.saveLater();
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    @Override
    public int getCombinedKeyLookaheadEpochs() {
        return hasKeyChains() ? getKeyChainGroup().getCombinedKeyLookaheadEpochs() : 0;
    }

    @Override
    public int getTotalIssuedKeys() {
        return hasKeyChains() ? getKeyChainGroup().getTotalIssuedKeys() : 0;
    }
}
