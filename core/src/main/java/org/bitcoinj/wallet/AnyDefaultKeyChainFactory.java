/*
 * Copyright 2014 devrandom
 * Copyright 2019 Andreas Schildbach
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
 * Default factory for creating keychains while de-serializing.
 */
public class AnyDefaultKeyChainFactory implements AnyKeyChainFactory {
    @Override
    public AnyDeterministicKeyChain makeKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed,
                                                 KeyCrypter crypter, boolean isMarried, Script.ScriptType outputScriptType,
                                                 ImmutableList<ChildNumber> accountPath, KeyFactory keyFactory) {
        AnyDeterministicKeyChain chain;
        if (isMarried)
            throw new UnsupportedOperationException("Married chains are not supported");
        else
            chain = new AnyDeterministicKeyChain(seed, crypter, outputScriptType, accountPath, keyFactory);
        return chain;
    }

    @Override
    public AnyDeterministicKeyChain makeWatchingKeyChain(Protos.Key key, Protos.Key firstSubKey,
                                                         IDeterministicKey accountKey, boolean isFollowingKey,
                                                         boolean isMarried, Script.ScriptType outputScriptType)
            throws UnreadableWalletException {
        AnyDeterministicKeyChain chain;
        if (isMarried)
            throw new UnsupportedOperationException("Married chains are not supported");
        else if (isFollowingKey)
            chain = AnyDeterministicKeyChain.builder().watchAndFollow(accountKey).outputScriptType(outputScriptType).build();
        else
            chain = AnyDeterministicKeyChain.builder().watch(accountKey).outputScriptType(outputScriptType).build();
        return chain;
    }

    @Override
    public AnyDeterministicKeyChain makeSpendingKeyChain(Protos.Key key, Protos.Key firstSubKey,
                                                         IDeterministicKey accountKey, boolean isMarried,
                                                         Script.ScriptType outputScriptType)
            throws UnreadableWalletException {
        AnyDeterministicKeyChain chain;
        if (isMarried)
            throw new UnsupportedOperationException("Married chains are not supported");
        else
            chain = AnyDeterministicKeyChain.builder().spend(accountKey).outputScriptType(outputScriptType).build();
        return chain;
    }

    @Override
    public AnyDeterministicKeyChain makeSpendingFriendKeyChain(Protos.Key key, Protos.Key firstSubKey, DeterministicSeed seed,
                                                               KeyCrypter crypter, boolean isMarried,
                                                               ImmutableList<ChildNumber> accountPath, KeyFactory keyFactory) throws UnreadableWalletException
    {
        if (isMarried)
            throw new UnsupportedOperationException("Married Friend Keychains are not allowed");
        else if(accountPath.get(0).equals(ChildNumber.NINE_HARDENED) && /* allow any coin type */
                accountPath.get(2).equals(DerivationPathFactory.FEATURE_PURPOSE_DASHPAY))
            throw new UnsupportedOperationException("Friend keys are not supported");
        else return new AnyDeterministicKeyChain(seed, crypter, Script.ScriptType.P2PKH, accountPath, keyFactory);
    }

    @Override
    public AnyDeterministicKeyChain makeWatchingFriendKeyChain(IDeterministicKey accountKey, ImmutableList<ChildNumber> accountPath)
    {
        if(accountPath.get(0).equals(ChildNumber.NINE_HARDENED) && /* allow any coin type */
                accountPath.get(2).equals(DerivationPathFactory.FEATURE_PURPOSE_DASHPAY)) {
            throw new UnsupportedOperationException("Friend keys are not supported with BLS");
        }
        else {
            throw new UnsupportedOperationException("Must be a watching friend key");
        }
    }
}
