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
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.*;
import org.bitcoinj.evolution.EvolutionContact;
import org.bitcoinj.script.Script;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

public class FriendKeyChain extends ExternalKeyChain {

    private static final Logger log = LoggerFactory.getLogger(FriendKeyChain.class);

    public enum KeyChainType {
        RECEIVING_CHAIN,
        SENDING_CHAIN,
    }
    KeyChainType type;


    // m / 9' / 5' / 15' - Friend Key Chain root path
    public static final ImmutableList<ChildNumber> FRIEND_ROOT_PATH = ImmutableList.of(ChildNumber.NINE_HARDENED,
            ChildNumber.FIVE_HARDENED, DerivationPathFactory.FEATURE_PURPOSE_DASHPAY);
    // m / 9' / 1' / 15' - Friend Key Chain root path (testnet)
    public static final ImmutableList<ChildNumber> FRIEND_ROOT_PATH_TESTNET = ImmutableList.of(ChildNumber.NINE_HARDENED,
            ChildNumber.ONE_HARDENED, DerivationPathFactory.FEATURE_PURPOSE_DASHPAY);

    public static ImmutableList<ChildNumber> getRootPath(NetworkParameters params) {
        return params.getId().equals(NetworkParameters.ID_MAINNET) ? FRIEND_ROOT_PATH : FRIEND_ROOT_PATH_TESTNET;
    }

    public static ImmutableList<ChildNumber> getContactPath(NetworkParameters params, EvolutionContact contact, KeyChainType type) {
        Sha256Hash userA = type == KeyChainType.RECEIVING_CHAIN ? contact.getEvolutionUserId() : contact.getFriendUserId();
        Sha256Hash userB = type == KeyChainType.RECEIVING_CHAIN ? contact.getFriendUserId() : contact.getEvolutionUserId();
        int account = type == KeyChainType.RECEIVING_CHAIN ? contact.getUserAccount() : contact.getFriendAccountReference();
        return new ImmutableList.Builder().addAll(getRootPath(params)).add(new ChildNumber(account, true)).add(new ExtendedChildNumber(userA)).add(new ExtendedChildNumber(userB)).build();
    }

    public static final int PATH_INDEX_ACCOUNT = 3;
    public static final int PATH_INDEX_TO_ID = 4;
    public static final int PATH_INDEX_FROM_ID = 5;


    public FriendKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> rootPath, int account, Sha256Hash myBlockchainUserId, Sha256Hash theirBlockchainUserId) {
        super(seed, ImmutableList.<ChildNumber>builder().addAll(rootPath).add(new ChildNumber(account, true)).add(new ExtendedChildNumber(myBlockchainUserId)).add(new ExtendedChildNumber(theirBlockchainUserId)).build());
        type = KeyChainType.RECEIVING_CHAIN;
    }

    public FriendKeyChain(DeterministicSeed seed, KeyCrypter keyCrypter, ImmutableList<ChildNumber> rootPath,  int account, Sha256Hash myBlockchainUserId, Sha256Hash theirBlockchainUserId) {
        super(seed, keyCrypter, ImmutableList.<ChildNumber>builder().addAll(rootPath).add(new ChildNumber(account, true)).add(new ExtendedChildNumber(myBlockchainUserId)).add(new ExtendedChildNumber(theirBlockchainUserId)).build());
        type = KeyChainType.RECEIVING_CHAIN;
    }

    public FriendKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path) {
        super(seed, path);
        type = KeyChainType.RECEIVING_CHAIN;
    }

    public FriendKeyChain(DeterministicSeed seed, KeyCrypter keyCrypter, ImmutableList<ChildNumber> path) {
        super(seed, keyCrypter, path);
        type = KeyChainType.RECEIVING_CHAIN;
    }

    /** xpub must contain a depth to indicate that it is not a masterkey **/
    public FriendKeyChain(NetworkParameters params, String xpub, EvolutionContact contact) {
        super(DeterministicKey.deserializeB58(xpub, getContactPath(params, contact, KeyChainType.SENDING_CHAIN), params),
                getContactPath(params, contact, KeyChainType.SENDING_CHAIN));
        type = KeyChainType.SENDING_CHAIN;
        this.isFollowing = true;
    }

    public FriendKeyChain(DeterministicKey watchingKey) {
        super(watchingKey, true);
        type = KeyChainType.SENDING_CHAIN;
    }

    public FriendKeyChain(DeterministicKey watchingKey, boolean isFollowing) {
        super(watchingKey, isFollowing);
    }

    /**
     * For use in encryption when {@link #toEncrypted(KeyCrypter, KeyParameter)} is called, so that
     * subclasses can override that method and create an instance of the right class.
     *
     * See also {@link #makeKeyChainFromSeed(DeterministicSeed, ImmutableList, Script.ScriptType)}
     */
    protected FriendKeyChain(KeyCrypter crypter, KeyParameter aesKey, FriendKeyChain chain) {
        super(crypter, aesKey, chain);
        type = chain.type;
    }

    /** {@inheritDoc} */
    @Override
    public DeterministicKey getKey(KeyPurpose purpose) {
        return getKeys(purpose, 1).get(0);
    }

    /** {@inheritDoc} */
    @Override
    public List<DeterministicKey> getKeys(KeyPurpose purpose, int numberOfKeys) {
        checkArgument(numberOfKeys > 0);
        lock.lock();
        try {
            DeterministicKey parentKey;
            int index;
            switch (purpose) {
                case RECEIVE_FUNDS:
                case REFUND:
                    issuedExternalKeys += numberOfKeys;
                    index = issuedExternalKeys;
                    parentKey = getKeyByPath(getAccountPath());
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
            /** Optimization: see {@link DeterministicKeyChain.getKeys(org.bitcoinj.wallet.KeyChain.KeyPurpose, int)} */
            List<DeterministicKey> lookahead = maybeLookAhead(parentKey, index, 0, 0);
            basicKeyChain.importKeys(lookahead);
            List<DeterministicKey> keys = new ArrayList<DeterministicKey>(numberOfKeys);
            for (int i = 0; i < numberOfKeys; i++) {
                ImmutableList<ChildNumber> path = HDUtils.append(parentKey.getPath(), new ChildNumber(index - numberOfKeys + i, false));
                DeterministicKey k = hierarchy.get(path, false, false);
                checkForBitFlip(k);
                keys.add(k);
            }
            return keys;
        } finally {
            lock.unlock();
        }
    }

    public DeterministicKey getKey(int index) {
        return getKeyByPath(new ImmutableList.Builder().addAll(getAccountPath()).addAll(ImmutableList.of(new ChildNumber(index, false))).build(), true);
    }

    public KeyChainType getType() {
        return type;
    }

    @Override
    public FriendKeyChain toEncrypted(KeyCrypter keyCrypter, KeyParameter aesKey) {
        return new FriendKeyChain(keyCrypter, aesKey, this);
    }
}
