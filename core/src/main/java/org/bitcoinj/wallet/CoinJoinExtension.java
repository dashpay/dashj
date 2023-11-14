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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.CodedOutputStream;
import net.jcip.annotations.GuardedBy;
import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.factory.ECKeyFactory;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.utils.Threading;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.dashj.bls.Utils.HexUtils.HEX;

/**
 * Handles the CoinJoin related KeyChain
 */

public class CoinJoinExtension extends AbstractKeyChainGroupExtension {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinExtension.class);

    protected AnyKeyChainGroup coinJoinKeyChainGroup;

    protected int rounds = CoinJoinClientOptions.getRounds();

    private final ReentrantLock unusedKeysLock = Threading.lock("unusedKeysLock");
    @GuardedBy("unusedKeysLock")
    protected final HashMap<KeyId, DeterministicKey> unusedKeys = Maps.newHashMapWithExpectedSize(1024);
    // TODO: we may not need keyUsage, it is used as a way to audit unusedKeys
    @GuardedBy("unusedKeysLock")
    protected final HashMap<IDeterministicKey, Boolean> keyUsage = Maps.newHashMap();
    private boolean loadedKeys = false;

    public CoinJoinExtension(Wallet wallet) {
        super(wallet);
    }

    /**
     * Returns a Java package/class style name used to disambiguate this extension from others.
     */
    @Override
    public String getWalletExtensionID() {
        return "org.dashj.wallet.coinjoin";
    }

    /**
     * If this returns true, the mandatory flag is set when the wallet is serialized and attempts to load it without
     * the extension being in the wallet will throw an exception. This method should not change its result during
     * the objects lifetime.
     */
    @Override
    public boolean isWalletExtensionMandatory() {
        return false;
    }

    @Override
    public boolean supportsBloomFilters() {
        return true;
    }

    @Override
    public boolean supportsEncryption() {
        return true;
    }

    /**
     * Returns bytes that will be saved in the wallet.
     */
    @Override
    public byte[] serializeWalletExtension() {
        try {
            Protos.CoinJoin.Builder builder = Protos.CoinJoin.newBuilder();
            List<Protos.Key> keys = coinJoinKeyChainGroup != null ? coinJoinKeyChainGroup.serializeToProtobuf() : Lists.newArrayList();
            builder.addAllKey(keys);
            builder.setRounds(rounds);
            Protos.CoinJoin coinJoinProto = builder.build();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            final CodedOutputStream codedOutput = CodedOutputStream.newInstance(output);
            coinJoinProto.writeTo(codedOutput);
            codedOutput.flush();
            return output.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Loads the contents of this object from the wallet.
     *
     * @param containingWallet the wallet to deserialize
     * @param data the serialized data
     */
    @Override
    public void deserializeWalletExtension(Wallet containingWallet, byte[] data) throws Exception {
        Protos.CoinJoin coinJoinProto = Protos.CoinJoin.parseFrom(data);
        if (containingWallet.isEncrypted()) {
            coinJoinKeyChainGroup = AnyKeyChainGroup.fromProtobufEncrypted(containingWallet.params,
                    coinJoinProto.getKeyList(), containingWallet.getKeyCrypter(), ECKeyFactory.get(), false);
        } else {
            coinJoinKeyChainGroup = AnyKeyChainGroup.fromProtobufUnencrypted(containingWallet.params,
                    coinJoinProto.getKeyList(), ECKeyFactory.get(), false);
        }
        rounds = coinJoinProto.getRounds();
        CoinJoinClientOptions.setRounds(rounds);
        loadedKeys = true;
    }

    public boolean hasKeyChain(ImmutableList<ChildNumber> path) {
        if (coinJoinKeyChainGroup == null)
            return false;
        boolean hasPath = false;
        for (AnyDeterministicKeyChain chain : coinJoinKeyChainGroup.getDeterministicKeyChains()) {
            if (chain.getAccountPath().equals(path)) {
                hasPath = true;
                break;
            }
        }
        return hasPath;
    }

    public void addKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path) {
        checkState(!seed.isEncrypted());
        if (!hasKeyChain(path)) {
            if (coinJoinKeyChainGroup == null) {
                coinJoinKeyChainGroup = AnyKeyChainGroup.builder(wallet.getParams(), ECKeyFactory.get()).build();
            }
            coinJoinKeyChainGroup.addAndActivateHDChain(AnyDeterministicKeyChain.builder().seed(seed).accountPath(path).build());
            //coinJoinKeyChainGroup.getActiveKeyChain().setLookaheadSize(300);
        }
    }

    public void addEncryptedKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path, @Nonnull KeyParameter keyParameter) {
        checkNotNull(keyParameter);
        checkState(seed.isEncrypted());
        if (!hasKeyChain(path)) {
            if (coinJoinKeyChainGroup == null) {
                coinJoinKeyChainGroup = AnyKeyChainGroup.builder(wallet.getParams(), ECKeyFactory.get()).build();
            }
            if (seed.isEncrypted()) {
                seed = seed.decrypt(wallet.getKeyCrypter(), "", keyParameter);
            }
            AnyDeterministicKeyChain chain = AnyDeterministicKeyChain.builder().seed(seed).accountPath(path).build();
            AnyDeterministicKeyChain encryptedChain = chain.toEncrypted(wallet.getKeyCrypter(), keyParameter);
            coinJoinKeyChainGroup.addAndActivateHDChain(encryptedChain);
            //coinJoinKeyChainGroup.getActiveKeyChain().setLookaheadSize(300);
        }
    }

    @Override
    public AnyKeyChainGroup getKeyChainGroup() {
        return coinJoinKeyChainGroup;
    }

    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    public Coin getUnmixableTotal() {
        Coin sum = Coin.ZERO;
        getOutputs().get(-1).forEach(outPoint -> {
            if (((WalletEx) wallet).getRealOutpointCoinJoinRounds(outPoint.getOutPointFor()) == -2)
                sum.add(outPoint.getValue());
        });
        return sum;
    }

    public TreeMap<Integer, List<TransactionOutput>> getOutputs() {
        checkNotNull(wallet);
        TreeMap<Integer, List<TransactionOutput>> outputs = Maps.newTreeMap();
        for (Coin amount : CoinJoin.getStandardDenominations()) {
            outputs.put(CoinJoin.amountToDenomination(amount), Lists.newArrayList());
        }
        outputs.put(0, Lists.newArrayList());
        for (TransactionOutput output : wallet.getUnspents()) {
            byte [] pkh = ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey());
            if (getKeyChainGroup().findKeyFromPubKeyHash(pkh, Script.ScriptType.P2PKH) != null) {
                int denom = CoinJoin.amountToDenomination(output.getValue());
                List<TransactionOutput> listDenoms = outputs.get(denom);
                listDenoms.add(output);
            }
        }
        return outputs;
    }

    @Override
    public String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey) {
        StringBuilder builder = new StringBuilder();
        builder.append("COINJOIN:\n Rounds: ").append(rounds).append("\n");
        builder.append(super.toString(includeLookahead, includePrivateKeys, aesKey)).append("\n");
        builder.append("Key Usage:").append(getKeyUsage()).append("\n");
        builder.append("Outputs:\n");

        for (Map.Entry<Integer, List<TransactionOutput>> entry : getOutputs().entrySet()) {
            int denom = entry.getKey();
            List<TransactionOutput> outputs = entry.getValue();
            Coin value = outputs.stream().map(TransactionOutput::getValue).reduce(Coin::add).orElse(Coin.ZERO);
            builder.append(CoinJoin.denominationToString(denom)).append(" outputs:").append(outputs.size()).append(" total:")
                    .append(value.toFriendlyString()).append("\n");
            outputs.forEach(output -> {
                TransactionOutPoint outPoint = new TransactionOutPoint(output.getParams(), output.getIndex(), output.getParentTransactionHash());
                builder.append("  addr:")
                        .append(Address.fromPubKeyHash(output.getParams(), ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey())))
                        .append(" outpoint:")
                        .append(outPoint.toStringShort())
                        .append(" ");
                int rounds = ((WalletEx) wallet).getRealOutpointCoinJoinRounds(outPoint);
                builder.append(CoinJoin.getRoundsString(rounds));
                if (rounds >= 0) {
                    builder.append(" ").append(rounds).append(" rounds");
                    if (((WalletEx) wallet).isFullyMixed(outPoint)) {
                        builder.append(" (fully mixed)");
                    }
                } else {
                    builder.append(" ").append(output.getValue().toFriendlyString());
                }
                builder.append("\n");
            });
        }
        builder.append(getUnusedKeyReport());
        builder.append(getKeyUsageReport());

        return builder.toString();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("COINJOIN:\n Rounds: ").append(rounds).append("\n");
        builder.append("Key Usage:").append(getKeyUsage()).append("\n");
        builder.append("Total Keys: ").append(getActiveKeyChain().issuedExternalKeys);
        return builder.toString();
    }

    @Override
    public boolean hasSpendableKeys() {
        return true;
    }

    @Override
    public boolean isTransactionRevelant(Transaction tx) {
        // use regular check based TransactionBag is* methods
        // there are no special transactions with CoinJoin
        return false;
    }

    /**
     *
     * @return the percentage of coinjoin keys used in transactions
     */
    public int getKeyUsage() {
        if (coinJoinKeyChainGroup.hasKeyChains()) {
            int totalKeys = coinJoinKeyChainGroup.getActiveKeyChain().getIssuedExternalKeys();
            List<IDeterministicKey> issuedKeys = coinJoinKeyChainGroup.getActiveKeyChain().getIssuedReceiveKeys();

            Set<Transaction> txes = wallet.getTransactions(true);

            Stream<IDeterministicKey> usedKeys = issuedKeys.stream().filter(key ->
                    txes.stream().anyMatch(tx ->
                            tx.getOutputs().stream().anyMatch(output -> {
                                if (ScriptPattern.isP2PKH(output.getScriptPubKey())) {
                                    byte[] publicKeyHash = ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey());
                                    return Arrays.equals(publicKeyHash, key.getPubKeyHash());
                                } else return false;
                            })
                    )
            );

            return totalKeys > 0 ? (int) usedKeys.count() * 100 / totalKeys : 0;
        } else {
            return 0;
        }
    }

    public void addUnusedKey(DeterministicKey key) {
        unusedKeysLock.lock();
        try {
            unusedKeys.put(KeyId.fromBytes(key.getPubKeyHash()), key);
            keyUsage.put(key, false);
            log.info("adding unused key: {} / {} ", HEX.encode(key.getPubKeyHash()), key.getPath());
        } finally {
            unusedKeysLock.unlock();
        }
    }

    public void addUnusedKey(KeyId keyId) {
        unusedKeysLock.lock();
        try {
            DeterministicKey key = (DeterministicKey) findKeyFromPubKey(keyId.getBytes());
            unusedKeys.put(KeyId.fromBytes(key.getPubKeyHash()), key);
            keyUsage.put(key, false);
            log.info("adding unused key: {} / {}", HEX.encode(key.getPubKeyHash()), key.getPath());
        } finally {
            unusedKeysLock.unlock();
        }
    }

    public DeterministicKey getUnusedKey() {
        unusedKeysLock.lock();
        try {
            if (unusedKeys.isEmpty()) {
                log.info("obtaining fresh key");
                log.info("keyUsage map has unused keys: {}", keyUsage.values().stream().noneMatch(used -> used));
                return (DeterministicKey) freshReceiveKey();
            } else {
                DeterministicKey key = unusedKeys.values().stream().findFirst().get();
                log.info("reusing key: {} / {}", HEX.encode(key.getPubKeyHash()), key);
                log.info("keyUsage map says this key is used: {}", keyUsage.get(key));

                // remove the key
                unusedKeys.remove(KeyId.fromBytes(key.getPubKeyHash()));
                keyUsage.put(key, true);

                return key;
            }
        } finally {
            unusedKeysLock.unlock();
        }
    }

    public void removeUnusedKey(KeyId keyId) {
        unusedKeysLock.lock();
        try {
            unusedKeys.remove(keyId);
            IDeterministicKey key = (IDeterministicKey) findKeyFromPubKeyHash(keyId.getBytes(), Script.ScriptType.P2PKH);
            keyUsage.put(key, true);
            log.info("remove unused key: {} / {}", HEX.encode(keyId.getBytes()), key);
        } finally {
            unusedKeysLock.unlock();
        }
    }

    @Override
    public void processTransaction(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType) {
        tx.getOutputs().forEach(output -> {
            if (ScriptPattern.isP2PKH(output.getScriptPubKey())) {
                byte[] pubKeyHash = ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey());
                IDeterministicKey key = (IDeterministicKey) findKeyFromPubKeyHash(pubKeyHash, Script.ScriptType.P2PKH);
                if (loadedKeys) {
                    keyUsage.put(key, true);
                    unusedKeys.remove(KeyId.fromBytes(pubKeyHash));
                }
            }
        });
    }

    @Override
    public IDeterministicKey freshReceiveKey() {
        IDeterministicKey freshKey = super.freshReceiveKey();
        log.info("fresh key: {} / {}", HEX.encode(freshKey.getPubKeyHash()), freshKey);
        keyUsage.put(freshKey, true);
        return freshKey;
    }

    boolean isKeyUsed(byte[] pubKeyHash) {
        return wallet.getTransactions(false).stream().anyMatch(tx ->
                tx.getOutputs().stream().anyMatch(output -> {
                    if (ScriptPattern.isP2PKH(output.getScriptPubKey())) {
                        byte[] publicKeyHashFromTx = ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey());
                        return Arrays.equals(publicKeyHashFromTx, pubKeyHash);
                    } else return false;
                })
        );
    }

    public void refreshUnusedKeys() {
        List<IDeterministicKey> issuedKeys;
        unusedKeysLock.lock();
        try {
            keyChainGroupLock.lock();
            try {
                unusedKeys.clear();
                issuedKeys = coinJoinKeyChainGroup.getActiveKeyChain().getIssuedReceiveKeys();
            } finally {
                keyChainGroupLock.unlock();
            }

            issuedKeys.forEach(key -> {
                unusedKeys.put(KeyId.fromBytes(key.getPubKeyHash()), (DeterministicKey) key);
                keyUsage.put(key, false);
            });

            Set<Transaction> txes = wallet.getTransactions(true);

            Stream<IDeterministicKey> usedKeys = issuedKeys.stream().filter(key -> {
                        boolean found = txes.stream().anyMatch(tx ->
                                tx.getOutputs().stream().anyMatch(output -> {
                                    if (ScriptPattern.isP2PKH(output.getScriptPubKey())) {
                                        byte[] publicKeyHash = ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey());
                                        return Arrays.equals(publicKeyHash, key.getPubKeyHash());
                                    } else return false;
                                })
                        );
                        if (found) {
                            keyUsage.put(key, true);
                        }
                        return found;
                    }
            );

            usedKeys.forEach(key -> unusedKeys.remove(KeyId.fromBytes(key.getPubKeyHash())));

            unusedKeys.forEach((keyId, key) -> log.info("unused key: {}", key));
            keyUsage.forEach((key, used) -> {
                if (!used)
                    log.info("unused key: {}", key);
            });
            loadedKeys = true;
        } finally {
            unusedKeysLock.unlock();
        }
    }

    public String getUnusedKeyReport() {
        List<IDeterministicKey> issuedKeys;
        HashMap<ImmutableList<ChildNumber>, IDeterministicKey> unusedKeyMap = Maps.newHashMap();
        unusedKeysLock.lock();
        try {
            keyChainGroupLock.lock();
            try {
                issuedKeys = coinJoinKeyChainGroup.getActiveKeyChain().getIssuedReceiveKeys();
            } finally {
                keyChainGroupLock.unlock();
            }

            issuedKeys.forEach(key -> unusedKeyMap.put(key.getPath(), key));

            Set<Transaction> txes = wallet.getTransactions(true);

            Stream<IDeterministicKey> usedKeys = issuedKeys.stream().filter(key ->
                    txes.stream().anyMatch(tx ->
                            tx.getOutputs().stream().anyMatch(output -> {
                                if (ScriptPattern.isP2PKH(output.getScriptPubKey())) {
                                    byte[] publicKeyHash = ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey());
                                    return Arrays.equals(publicKeyHash, key.getPubKeyHash());
                                } else return false;
                            })
                    )
            );

            usedKeys.forEach(key -> unusedKeyMap.remove(key.getPath()));

            StringBuilder builder = new StringBuilder();
            Stream<ImmutableList<ChildNumber>> sortedPaths = unusedKeyMap.keySet().stream().sorted(new Comparator<ImmutableList<ChildNumber>>() {
                @Override
                public int compare(ImmutableList<ChildNumber> a, ImmutableList<ChildNumber> b) {
                    int size1 = a.size();
                    int size2 =  b.size();
                    for (int i = 0; i < Math.min(size1, size2); i++) {
                        int comparison = a.get(i).compareTo(b.get(i));
                        if (comparison != 0) {
                            return comparison;
                        }
                    }
                    // If we haven't returned, the common prefix of both lists is identical.
                    // The shorter list should be considered less than the longer one.
                    return Integer.compare(size1, size2);
                }
            });

            builder.append("Unused Key List: ");
            sortedPaths.forEach(path -> {
                builder.append("  ").append(path).append("\n");
            });
            return builder.toString();

        } finally {
            unusedKeysLock.unlock();
        }
    }

    public String getKeyUsageReport() {
        List<IDeterministicKey> issuedKeys;
        HashMap<DeterministicKey, Integer> usedKeyMap = Maps.newHashMap();
        unusedKeysLock.lock();
        try {
            keyChainGroupLock.lock();
            try {
                issuedKeys = coinJoinKeyChainGroup.getActiveKeyChain().getIssuedReceiveKeys();
            } finally {
                keyChainGroupLock.unlock();
            }

            Set<Transaction> txes = wallet.getTransactions(true);

            issuedKeys.forEach(key -> txes.forEach(tx -> {
                Stream<TransactionOutput> keyUsage = tx.getOutputs().stream().filter(output -> {
                    if (ScriptPattern.isP2PKH(output.getScriptPubKey())) {
                        byte[] publicKeyHash = ScriptPattern.extractHashFromP2PKH(output.getScriptPubKey());
                        return Arrays.equals(publicKeyHash, key.getPubKeyHash());
                    } else return false;
                });
                int count = (int) keyUsage.count();
                if (count > 0) {
                    Integer currentCount = usedKeyMap.get((DeterministicKey) key);
                    usedKeyMap.put((DeterministicKey) key, currentCount == null ? 1 : currentCount + 1);
                }
            }));


            StringBuilder builder = new StringBuilder();
            builder.append("Duplicate Used Key List: \n");
            usedKeyMap.forEach((key, count) -> {
                if (count > 1)
                    builder.append("  ").append("hash160:").append(Utils.HEX.encode(key.getPubKeyHash())).append(":").append(count).append("\n");
            });
            return builder.toString();

        } finally {
            unusedKeysLock.unlock();
        }
    }
}
