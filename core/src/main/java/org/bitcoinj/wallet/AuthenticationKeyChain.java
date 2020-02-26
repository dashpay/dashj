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

import com.google.common.collect.ImmutableList;
import org.bitcoinj.crypto.*;
import org.bitcoinj.script.Script;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class AuthenticationKeyChain extends ExternalKeyChain {

    public enum KeyChainType {
        BLOCKCHAIN_USER,
        MASTERNODE_HOLDINGS,
        MASTERNODE_OWNER,
        MASTERNODE_OPERATOR,
        MASTERNODE_VOTING,
        INVALID_KEY_CHAIN
    }
    KeyChainType type;
    int currentIndex;
    int issuedKeys;

    public AuthenticationKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path) {
        super(seed, null, path);
        setLookaheadSize(5);
    }

    public AuthenticationKeyChain(DeterministicSeed seed, KeyCrypter keyCrypter, ImmutableList<ChildNumber> path) {
        super(seed, keyCrypter, path);
        setLookaheadSize(5);
    }

    /**
     * Sets the KeyChainType of this AuthenticationKeyChain.  Used by Wallet when loading from a protobuf
     * @param type
     */
    /* package */
    void setType(KeyChainType type) {
        this.type = type;
    }

    @Override
    public DeterministicKey getKey(KeyPurpose purpose) {
        return getKeys(purpose, 1).get(0);
    }

    @Override
    public List<DeterministicKey> getKeys(KeyPurpose purpose, int numberOfKeys) {
        checkArgument(numberOfKeys > 0);
        lock.lock();
        try {
            DeterministicKey parentKey;
            int index;
            switch (purpose) {
                case AUTHENTICATION:
                    issuedKeys += numberOfKeys;
                    index = issuedKeys;
                    parentKey = getKeyByPath(getAccountPath());
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            //TODO: do we need to look ahead here, even for one key?  Does anything get saved?
            //List<DeterministicKey> lookahead = maybeLookAhead(parentKey, index, 0, 0);
            //basicKeyChain.importKeys(lookahead);

            //TODO: do we need to look ahead here, even for one key?  Does anything get saved?
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

    public int getCurrentIndex() {
        return currentIndex;
    }

    public DeterministicKey freshAuthenticationKey() {
        return getKey(KeyPurpose.AUTHENTICATION);
    }

    public DeterministicKey currentAuthenticationKey() {
        return getKey(currentIndex);
    }
}
