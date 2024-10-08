/*
 * Copyright 2014 devrandom
 * Copyright 2019 Andreas Schildbach
 * Copyright 2023 Dash Core Group
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
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.script.Script;
/**
 * Factory interface for creation keychains while de-serializing a wallet.
 */
public interface AnyKeyChainFactory {
    /**
     * Make a keychain (but not a watching one) with the specified account path
     *
     * @param key the protobuf for the root key
     * @param firstSubKey the protobuf for the first child key (normally the parent of the external subchain)
     * @param seed the seed
     * @param crypter the encrypted/decrypter
     * @param isMarried whether the keychain is leading in a marriage
     * @param outputScriptType type of addresses (aka output scripts) to generate for receiving
     * @param accountPath account path to generate receiving addresses on
     */
    AnyDeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed,
                                          KeyCrypter crypter, boolean isMarried, Script.ScriptType outputScriptType,
                                          ImmutableList<ChildNumber> accountPath, KeyFactory keyFactory, boolean hardenedKeysOnly);

    /**
     * Make a watching keychain.
     *
     * <p>isMarried and isFollowingKey must not be true at the same time.
     *
     * @param key the protobuf for the account key
     * @param firstSubKey the protobuf for the first child key (normally the parent of the external subchain)
     * @param accountKey the account extended public key
     * @param isFollowingKey whether the keychain is following in a marriage
     * @param isMarried whether the keychain is leading in a marriage
     * @param outputScriptType type of addresses (aka output scripts) to generate for watching
     */
    AnyDeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, IDeterministicKey accountKey,
                                                  boolean isFollowingKey, boolean isMarried, Script.ScriptType outputScriptType)
            throws UnreadableWalletException;

    /**
     * Make a spending keychain.
     *
     * <p>isMarried and isFollowingKey must not be true at the same time.
     *
     * @param key the protobuf for the account key
     * @param firstSubKey the protobuf for the first child key (normally the parent of the external subchain)
     * @param accountKey the account extended public key
     * @param isMarried whether the keychain is leading in a marriage
     * @param outputScriptType type of addresses (aka output scripts) to generate for spending
     */
    AnyDeterministicKeyChain makeSpendingKeyChain(Protos.Key key, Protos.Key firstSubKey, IDeterministicKey accountKey,
                                                  boolean isMarried, Script.ScriptType outputScriptType, boolean hardenedKeysOnly) throws UnreadableWalletException;

    AnyDeterministicKeyChain makeSpendingFriendKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed,
                                                        KeyCrypter crypter, boolean isMarried, ImmutableList<ChildNumber> accountPath,
                                                        KeyFactory keyFactory, boolean hardenedKeysOnly) throws UnreadableWalletException;

    AnyDeterministicKeyChain makeWatchingFriendKeyChain(IDeterministicKey accountKey, ImmutableList<ChildNumber> accountPath) throws UnreadableWalletException;

}
