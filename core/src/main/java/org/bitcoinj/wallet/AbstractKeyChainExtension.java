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

import org.bitcoinj.core.Address;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.listeners.KeyChainEventListener;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements basic keychain functionality for a keychain extension
 */
abstract public class AbstractKeyChainExtension implements KeyChainWalletExtension {
    protected final ReentrantLock keyChainGroupLock = Threading.lock("keychaingroup");
    Wallet wallet;

    protected AbstractKeyChainExtension(Wallet wallet) {
        this.wallet = wallet;
    }

    abstract KeyChainGroup getKeyChainGroup();

    boolean isInitialized() {
        return getKeyChainGroup() != null;
    }

    public void addAndActivateHDChain(DeterministicKeyChain keyChain) {
        keyChainGroupLock.lock();
        try {
            getKeyChainGroup().addAndActivateHDChain(keyChain);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    public DeterministicKey currentKey(KeyChain.KeyPurpose purpose) {
        keyChainGroupLock.lock();
        try {
            return getKeyChainGroup().currentKey(purpose);
        } finally {
            keyChainGroupLock.unlock();
        }
    }
    public DeterministicKey currentReceiveKey() {
        return currentKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    @Override
    public void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            getKeyChainGroup().encrypt(keyCrypter, aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public void decrypt(KeyParameter aesKey) {
        keyChainGroupLock.lock();
        try {
            getKeyChainGroup().decrypt(aesKey);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public ECKey findKeyFromPubKey(byte[] pubKey) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().findKeyFromPubKey(pubKey) : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public ECKey findKeyFromPubKeyHash(byte[] pubKeyHash, @Nullable Script.ScriptType scriptType) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().findKeyFromPubKeyHash(pubKeyHash, scriptType) : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() ? getKeyChainGroup().findRedeemDataFromScriptHash(payToScriptHash) : null;
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public DeterministicKey freshKey(KeyChain.KeyPurpose purpose) {
        keyChainGroupLock.lock();
        try {
            return getKeyChainGroup().freshKey(purpose);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public List<DeterministicKey> freshKeys(KeyChain.KeyPurpose purpose, int numberOfKeys) {
        keyChainGroupLock.lock();
        try {
            return getKeyChainGroup().freshKeys(purpose, numberOfKeys);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    public DeterministicKey freshReceiveKey() {
        return freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    @Override
    public DeterministicKeyChain getActiveKeyChain() {
        keyChainGroupLock.lock();
        try {
            return getKeyChainGroup().getActiveKeyChain();
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    public List<DeterministicKeyChain> getActiveKeyChains(long walletCreationTime) {
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
    public boolean hasKey(ECKey key) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().hasKey(key);
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

    @Override
    public void addKeyChainEventListener(Executor executor, KeyChainEventListener listener) {
        keyChainGroupLock.lock();
        try {
            if (isInitialized())
                getKeyChainGroup().addEventListener(listener, executor);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    @Override
    public boolean removeKeyChainEventListener(KeyChainEventListener listener) {
        keyChainGroupLock.lock();
        try {
            return isInitialized() && getKeyChainGroup().removeEventListener(listener);
        } finally {
            keyChainGroupLock.unlock();
        }
    }

    public String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey) {
        return getWalletExtensionID() + ":\n" + (isInitialized() ? getKeyChainGroup().toString(includeLookahead, includePrivateKeys, aesKey) : "No keychains");
    }
}
