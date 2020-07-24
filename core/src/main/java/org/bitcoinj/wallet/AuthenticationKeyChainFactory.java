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
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.script.Script;

/**
 * Default factory for creating authentication keychains while de-serializing.
 */
public class AuthenticationKeyChainFactory implements KeyChainFactory {

    @Override
    public DeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed,
                                              KeyCrypter crypter, boolean isMarried, Script.ScriptType scriptType, ImmutableList<ChildNumber> accountPath) {
        AuthenticationKeyChain chain;
        if (isMarried)
            throw new UnsupportedOperationException();
        else
            chain = new AuthenticationKeyChain(seed, crypter, accountPath);
        return chain;
    }

    @Override
    public DeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey,
                                                      boolean isFollowingKey, boolean isMarried, Script.ScriptType scriptType) throws UnreadableWalletException {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeterministicKeyChain makeSpendingKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicKey accountKey, boolean isMarried, Script.ScriptType outputScriptType) throws UnreadableWalletException {
        throw new UnsupportedOperationException();
    }

    public DeterministicKeyChain makeSpendingFriendKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed, KeyCrypter crypter, boolean isMarried, ImmutableList<ChildNumber> accountPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeterministicKeyChain makeWatchingFriendKeyChain(DeterministicKey accountKey, ImmutableList<ChildNumber> accountPath) throws UnreadableWalletException {
        throw new UnsupportedOperationException();
    }
}
