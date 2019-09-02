package org.bitcoinj.wallet;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.KeyCrypter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

public class ExternalKeyChain extends DeterministicKeyChain {

    private static final Logger log = LoggerFactory.getLogger(ExternalKeyChain.class);


    protected int currentIndex;
    protected int issuedKeys;

    public ExternalKeyChain(DeterministicSeed seed, ImmutableList<ChildNumber> path) {
        super(seed, path);
    }

    public ExternalKeyChain(DeterministicSeed seed, KeyCrypter keyCrypter, ImmutableList<ChildNumber> path) {
        super(seed, keyCrypter, path);
    }

    public ExternalKeyChain(DeterministicKey key) {
        super(key);
    }

    public ExternalKeyChain(DeterministicKey key, boolean isFollowing) {
        super(key, isFollowing);
    }

    /** {@inheritDoc} */
    @Override
    public int getIssuedExternalKeys() {
        return currentIndex;
    }

    /** {@inheritDoc} */
    @Override
    public int getIssuedInternalKeys() {
        throw new UnsupportedOperationException("external key chains do not have internal keys");
    }

    /** {@inheritDoc} */
    @Override
    public DeterministicKey markKeyAsUsed(DeterministicKey k) {
        int numChildren = k.getChildNumber().i() + 1;

        if (k.getParent() == getKeyByPath(getAccountPath())) {
            if (issuedKeys < numChildren) {
                issuedKeys = numChildren;
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
            List<DeterministicKey> keys = maybeLookAhead(getKeyByPath(getAccountPath()), issuedKeys);
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

    private List<DeterministicKey> maybeLookAhead(DeterministicKey parent, int issued) {
        checkState(lock.isHeldByCurrentThread());
        return maybeLookAhead(parent, issued, getLookaheadSize(), getLookaheadThreshold());
    }

    /**
     * Pre-generate enough keys to reach the lookahead size, but only if there are more than the lookaheadThreshold to
     * be generated, so that the Bloom filter does not have to be regenerated that often.
     *
     * The returned mutable list of keys must be inserted into the basic key chain.
     */
    protected List<DeterministicKey> maybeLookAhead(DeterministicKey parent, int issued, int lookaheadSize, int lookaheadThreshold) {
        checkState(lock.isHeldByCurrentThread());
        final int numChildren = hierarchy.getNumChildren(parent.getPath());
        final int needed = issued + lookaheadSize + lookaheadThreshold - numChildren;

        if (needed <= lookaheadThreshold)
            return new ArrayList<DeterministicKey>();

        log.info("{} keys needed for {} = {} issued + {} lookahead size + {} lookahead threshold - {} num children",
                needed, parent.getPathAsString(), issued, lookaheadSize, lookaheadThreshold, numChildren);

        List<DeterministicKey> result  = new ArrayList<DeterministicKey>(needed);
        final Stopwatch watch = Stopwatch.createStarted();
        int nextChild = numChildren;
        for (int i = 0; i < needed; i++) {
            DeterministicKey key = HDKeyDerivation.deriveThisOrNextChildKey(parent, nextChild);
            key = key.dropPrivateBytes();
            hierarchy.putKey(key);
            result.add(key);
            nextChild = key.getChildNumber().num() + 1;
        }
        watch.stop();
        log.info("Took {}", watch);
        return result;
    }

    /**
     * Setter for property 'issuedKeys'.
     *
     * @param issuedKeys Value to set for property 'issuedKeys'.
     */
    public void setIssuedKeys(int issuedKeys) {
        this.issuedKeys = issuedKeys;
    }

}
