package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.crypto.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class AuthenticationKeyChain extends DeterministicKeyChain {

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
        super(seed, path);
        setLookaheadSize(5);
    }

    public AuthenticationKeyChain(DeterministicSeed seed, KeyCrypter keyCrypter, ImmutableList<ChildNumber> path) {
        super(seed, keyCrypter, path);
        setLookaheadSize(5);
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
            List<DeterministicKey> keys = new ArrayList<DeterministicKey>(numberOfKeys);
            for (int i = 0; i < numberOfKeys; i++) {
                //ImmutableList<ChildNumber> path = HDUtils.append(parentKey.getPath(), new ChildNumber(index - numberOfKeys + i, false));
                //DeterministicKey k = hierarchy.get(path, false, false);
                DeterministicKey k = HDKeyDerivation.deriveChildKey(parentKey, new ChildNumber(index - numberOfKeys + i));
                // Just a last minute sanity check before we hand the key out to the app for usage. This isn't inspired
                // by any real problem reports from bitcoinj users, but I've heard of cases via the grapevine of
                // places that lost money due to bitflips causing addresses to not match keys. Of course in an
                // environment with flaky RAM there's no real way to always win: bitflips could be introduced at any
                // other layer. But as we're potentially retrieving from long term storage here, check anyway.
                checkForBitFlip(k);
                keys.add(k);
            }
            return keys;
        } finally {
            lock.unlock();
        }
    }

    private void checkForBitFlip(DeterministicKey k) {
        DeterministicKey parent = checkNotNull(k.getParent());
        byte[] rederived = HDKeyDerivation.deriveChildKeyBytesFromPublic(parent, k.getChildNumber(), HDKeyDerivation.PublicDeriveMode.WITH_INVERSION).keyBytes;
        byte[] actual = k.getPubKey();
        if (!Arrays.equals(rederived, actual))
            throw new IllegalStateException(String.format(Locale.US, "Bit-flip check failed: %s vs %s", Arrays.toString(rederived), Arrays.toString(actual)));
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
