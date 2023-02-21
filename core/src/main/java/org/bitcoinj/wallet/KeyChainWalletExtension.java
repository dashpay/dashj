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

/**
 * Defines basic keychain functionality for an extension
 */
public interface KeyChainWalletExtension extends WalletExtension {
    boolean supportsBloomFilters();
    boolean supportsEncryption();
    void addAndActivateHDChain(DeterministicKeyChain keyChain);
    boolean checkPassword(CharSequence password);
    boolean checkAESKey(KeyParameter aesKey);
    DeterministicKey currentKey(KeyChain.KeyPurpose purpose);
    Address currentAddress(KeyChain.KeyPurpose purpose);
    void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey);
    void decrypt(KeyParameter aesKey);
    ECKey findKeyFromPubKey(byte[] pubKey);
    ECKey findKeyFromPubKeyHash(byte[] pubKeyHash, @Nullable Script.ScriptType scriptType);
    RedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash);
    Address freshAddress(KeyChain.KeyPurpose purpose);
    DeterministicKey freshKey(KeyChain.KeyPurpose purpose);
    List<DeterministicKey> freshKeys(int numberOfKeys);
    Address freshReceiveAddress();
    DeterministicKey freshReceiveKey();
    List<DeterministicKey> freshKeys(KeyChain.KeyPurpose purpose, int numberOfKeys);
    DeterministicKeyChain getActiveKeyChain();
    List<DeterministicKeyChain> getActiveKeyChains(long walletCreationTime);
    BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak);
    int getBloomFilterElementCount();
    long getEarliestKeyCreationTime();
    List<ECKey> getIssuedReceiveKeys();
    int getLookaheadSize();
    int getLookaheadThreshold();
    boolean hasKey(ECKey key);
    int importKeys(List<ECKey> keys);
    int importKeys(ECKey... keys);
    int importKeysAndEncrypt(final List<ECKey> keys, KeyParameter aesKey);
    boolean isDeterministicUpgradeRequired(Script.ScriptType preferredScriptType, long keyRotationTimeSecs);
    boolean isEncrypted();
    boolean isMarried();
    boolean isWatching();
    void markPubKeyAsUsed(byte[] pubkey);
    void markPubKeyHashAsUsed(byte[] pubKeyHash);
    void markP2SHAddressAsUsed(Address address);
    int numKeys();
    boolean removeImportedKey(ECKey key);
    List<Protos.Key> serializeToProtobuf();
    void upgradeToDeterministic(Script.ScriptType preferredScriptType, KeyChainGroupStructure structure,
                                long keyRotationTimeSecs, @Nullable KeyParameter aesKey);

    // listener support
    void addEventListener(KeyChainEventListener listener);
    void addEventListener(KeyChainEventListener listener, Executor executor);
    boolean removeEventListener(KeyChainEventListener listener);
    String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey);
}
