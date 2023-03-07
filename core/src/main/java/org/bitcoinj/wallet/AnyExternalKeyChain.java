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

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.IDeterministicKey;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.crypto.factory.KeyFactory;
import org.bitcoinj.script.Script;
import org.bouncycastle.crypto.params.KeyParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class AnyExternalKeyChain extends AnyDeterministicKeyChain {

    private static final Logger log = LoggerFactory.getLogger(AnyExternalKeyChain.class);

    public AnyExternalKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path, KeyFactory keyFactory) {
        super(seed, null, Script.ScriptType.P2PKH, path, keyFactory);
    }

    public AnyExternalKeyChain(DeterministicSeed seed, KeyCrypter keyCrypter, ImmutableList<ChildNumber> path, KeyFactory keyFactory) {
        super(seed, keyCrypter, Script.ScriptType.P2PKH, path, keyFactory);
    }

    public AnyExternalKeyChain(IDeterministicKey key, ImmutableList<ChildNumber> accountPath) {
        super(key, false, true, Script.ScriptType.P2PKH, accountPath);
    }

    public AnyExternalKeyChain(IDeterministicKey key, boolean isFollowing) {
        super(key, isFollowing, true, Script.ScriptType.P2PKH);
    }

    protected AnyExternalKeyChain(KeyCrypter crypter, KeyParameter aesKey, AnyExternalKeyChain chain) {
        super(crypter, aesKey, chain);
    }

    /** {@inheritDoc} */
    @Override
    public int getIssuedInternalKeys() {
        throw new UnsupportedOperationException("external key chains do not have internal keys");
    }

    /** {@inheritDoc} */
    @Override
    public IDeterministicKey markKeyAsUsed(IDeterministicKey k) {
        int numChildren = k.getChildNumber().i() + 1;

        if (k.getParent() == getKeyByPath(getAccountPath())) {
            if (issuedExternalKeys < numChildren) {
                issuedExternalKeys = numChildren;
                maybeLookAhead();
            }
        }
        return k;
    }

    /**
     * Pre-generate enough keys to reach the lookahead size. You can call this if you need to explicitly invoke
     * the lookahead procedure, but it's normally unnecessary as it will be done automatically when needed.
     */
    public void maybeLookAhead() {
        lock.lock();
        try {
            List<IDeterministicKey> keys = maybeLookAhead(getKeyByPath(getAccountPath()), issuedExternalKeys);
            if (keys.isEmpty())
                return;
            keyLookaheadEpoch++;
            // Batch add all keys at once so there's only one event listener invocation, as this will be listened to
            // by the wallet and used to rebuild/broadcast the Bloom filter. That's expensive so we don't want to do
            // it more often than necessary.
            basicKeyChain.importKeys(keys);
        } finally {
            lock.unlock();
        }
    }

    private List<IDeterministicKey> maybeLookAhead(IDeterministicKey parent, int issued) {
        checkState(lock.isHeldByCurrentThread());
        return maybeLookAhead(parent, issued, getLookaheadSize(), getLookaheadThreshold());
    }

    /**
     * Pre-generate enough keys to reach the lookahead size, but only if there are more than the lookaheadThreshold to
     * be generated, so that the Bloom filter does not have to be regenerated that often.
     *
     * The returned mutable list of keys must be inserted into the basic key chain.
     */
    protected List<IDeterministicKey> maybeLookAhead(IDeterministicKey parent, int issued, int lookaheadSize, int lookaheadThreshold) {
        checkState(lock.isHeldByCurrentThread());
        final int numChildren = hierarchy.getNumChildren(parent.getPath());
        final int needed = issued + lookaheadSize + lookaheadThreshold - numChildren;

        if (needed <= lookaheadThreshold)
            return new ArrayList<IDeterministicKey>();

        log.info("{} keys needed for {} = {} issued + {} lookahead size + {} lookahead threshold - {} num children",
                needed, parent.getPathAsString(), issued, lookaheadSize, lookaheadThreshold, numChildren);

        List<IDeterministicKey> result  = new ArrayList<IDeterministicKey>(needed);
        final Stopwatch watch = Stopwatch.createStarted();
        int nextChild = numChildren;
        for (int i = 0; i < needed; i++) {
            IDeterministicKey key = parent.deriveThisOrNextChildKey(nextChild);
            key = key.dropPrivateBytes();
            hierarchy.putKey(key);
            result.add(key);
            nextChild = key.getChildNumber().num() + 1;
        }
        watch.stop();
        log.info("Took {}", watch);
        return result;
    }
}
