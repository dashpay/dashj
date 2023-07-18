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
import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.factory.ECKeyFactory;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptPattern;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Handles the CoinJoin related KeyChain
 */

public class CoinJoinExtension extends AbstractKeyChainGroupExtension {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinExtension.class);

    protected AnyKeyChainGroup coinJoinKeyChainGroup;

    protected int rounds = CoinJoinClientOptions.getRounds();

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
        }
    }

    @Override
    public AnyKeyChainGroup getKeyChainGroup() {
        return coinJoinKeyChainGroup;
    }

    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    public TreeMap<Integer, List<TransactionOutput>> getOutputs() {
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
        builder.append("Outputs:\n");

        for (Map.Entry<Integer, List<TransactionOutput>> entry : getOutputs().entrySet()) {
            int denom = entry.getKey();
            List<TransactionOutput> outputs = entry.getValue();
            Coin value = outputs.stream().map(TransactionOutput::getValue).reduce(Coin::add).orElse(Coin.ZERO);
            builder.append(CoinJoin.denominationToString(denom)).append(" ").append(outputs.size()).append(" ")
                    .append(value.toFriendlyString()).append("\n");
        }
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
}
