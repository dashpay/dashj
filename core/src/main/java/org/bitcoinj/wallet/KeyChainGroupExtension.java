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
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.BloomFilter;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.listeners.KeyChainEventListener;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Defines basic keychain functionality for an extension
 */
public interface KeyChainGroupExtension extends WalletExtension {
    boolean supportsBloomFilters();
    boolean supportsEncryption();
    void addAndActivateHDChain(AnyDeterministicKeyChain keyChain);
    boolean checkPassword(CharSequence password);
    boolean checkAESKey(KeyParameter aesKey);
    IDeterministicKey currentKey(KeyChain.KeyPurpose purpose);
    Address currentAddress(KeyChain.KeyPurpose purpose);
    void encrypt(KeyCrypter keyCrypter, KeyParameter aesKey);
    void decrypt(KeyParameter aesKey);
    IKey findKeyFromPubKey(byte[] pubKey);
    IKey findKeyFromPubKeyHash(byte[] pubKeyHash, @Nullable Script.ScriptType scriptType);
    IRedeemData findRedeemDataFromScriptHash(byte[] payToScriptHash);
    Address freshAddress(KeyChain.KeyPurpose purpose);
    IDeterministicKey freshKey(KeyChain.KeyPurpose purpose);
    List<IDeterministicKey> freshKeys(int numberOfKeys);
    Address freshReceiveAddress();
    IDeterministicKey freshReceiveKey();
    List<IDeterministicKey> freshKeys(KeyChain.KeyPurpose purpose, int numberOfKeys);
    AnyDeterministicKeyChain getActiveKeyChain();
    List<AnyDeterministicKeyChain> getActiveKeyChains(long walletCreationTime);
    BloomFilter getBloomFilter(int size, double falsePositiveRate, long nTweak);
    int getBloomFilterElementCount();
    long getEarliestKeyCreationTime();
    List<IKey> getIssuedReceiveKeys();
    int getLookaheadSize();
    int getLookaheadThreshold();
    boolean hasKey(IKey key);
    boolean hasKeyChains();
    int importKeys(List<IKey> keys);
    int importKeys(IKey... keys);
    int importKeysAndEncrypt(final List<IKey> keys, KeyParameter aesKey);
    boolean isDeterministicUpgradeRequired(Script.ScriptType preferredScriptType, long keyRotationTimeSecs);
    boolean isEncrypted();
    boolean isMarried();
    boolean isWatching();
    void markPubKeyAsUsed(byte[] pubkey);
    void markPubKeyHashAsUsed(byte[] pubKeyHash);
    void markP2SHAddressAsUsed(Address address);
    int numKeys();
    boolean removeImportedKey(IKey key);
    List<Protos.Key> serializeToProtobuf();
    void upgradeToDeterministic(Script.ScriptType preferredScriptType, KeyChainGroupStructure structure,
                                long keyRotationTimeSecs, @Nullable KeyParameter aesKey);

    // transaction support
    void processTransaction(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType);

    // listener support
    void addEventListener(KeyChainEventListener listener);
    void addEventListener(KeyChainEventListener listener, Executor executor);
    boolean removeEventListener(KeyChainEventListener listener);
    String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey);

    boolean hasSpendableKeys();
    boolean isTransactionRevelant(Transaction tx);
}
