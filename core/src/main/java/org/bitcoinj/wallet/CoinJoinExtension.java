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
import com.google.protobuf.CodedOutputStream;
import org.bitcoinj.coinjoin.CoinJoinClientOptions;
import org.bitcoinj.crypto.ChildNumber;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Handles the CoinJoin related KeyChain
 */

public class CoinJoinExtension extends AbstractKeyChainExtension {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinExtension.class);

    protected KeyChainGroup coinJoinKeyChainGroup;

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
            coinJoinKeyChainGroup = KeyChainGroup.fromProtobufEncrypted(containingWallet.params, coinJoinProto.getKeyList(), containingWallet.getKeyCrypter());
        } else {
            coinJoinKeyChainGroup = KeyChainGroup.fromProtobufUnencrypted(containingWallet.params, coinJoinProto.getKeyList());
        }
        rounds = coinJoinProto.getRounds();
        CoinJoinClientOptions.setRounds(rounds);
    }

    public boolean hasKeyChain(ImmutableList<ChildNumber> path) {
        if (coinJoinKeyChainGroup == null)
            return false;
        boolean hasPath = false;
        for (DeterministicKeyChain chain : coinJoinKeyChainGroup.getDeterministicKeyChains()) {
            if (chain.getAccountPath().equals(path)) {
                hasPath = true;
                break;
            }
        }
        return hasPath;
    }

    public void addKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path) {
        if (!hasKeyChain(path)) {
            if (coinJoinKeyChainGroup == null) {
                coinJoinKeyChainGroup = KeyChainGroup.builder(wallet.getParams()).build();
            }
            coinJoinKeyChainGroup.addAndActivateHDChain(DeterministicKeyChain.builder().seed(seed).accountPath(path).build());
        }
    }

    @Override
    KeyChainGroup getKeyChainGroup() {
        return coinJoinKeyChainGroup;
    }

    public void setRounds(int rounds) {
        this.rounds = rounds;
    }

    @Override
    public String toString(boolean includeLookahead, boolean includePrivateKeys, @Nullable KeyParameter aesKey) {
        return "COINJOIN:\n Rounds: " + rounds + "\n" +
                super.toString(includeLookahead, includePrivateKeys, aesKey);
    }
}