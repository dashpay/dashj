package org.bitcoinj.wallet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.ExtendedChildNumber;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.evolution.EvolutionContact;
import org.bitcoinj.script.Script;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.wallet.FriendKeyChain.*;

public class FriendKeyChainGroup extends KeyChainGroup {
    public FriendKeyChainGroup(NetworkParameters params) {
        super(params);
    }

    protected FriendKeyChainGroup(NetworkParameters params, @Nullable BasicKeyChain basicKeyChain, List<DeterministicKeyChain> chains,
                            @Nullable EnumMap<KeyChain.KeyPurpose, DeterministicKey> currentKeys, @Nullable KeyCrypter crypter) {
        super(params, basicKeyChain, chains, currentKeys, crypter);
    }

    public FriendKeyChain getFriendKeyChain(Sha256Hash myBlockchainUserId, int account, Sha256Hash theirBlockchainUserId, FriendKeyChain.KeyChainType type ) {
        Preconditions.checkNotNull(theirBlockchainUserId);
        Preconditions.checkArgument(!theirBlockchainUserId.equals(Sha256Hash.ZERO_HASH));

        Sha256Hash to, from;
        if(type == FriendKeyChain.KeyChainType.RECEIVING_CHAIN)
        {
            from = theirBlockchainUserId;
            to = myBlockchainUserId;
        } else {
            to = theirBlockchainUserId;
            from = myBlockchainUserId;
        }
        for(DeterministicKeyChain chain : chains) {
            ImmutableList<ChildNumber> accountPath = chain.getAccountPath();
            if(accountPath.get(PATH_INDEX_ACCOUNT).equals(new ChildNumber(account, true)) &&
            accountPath.get(PATH_INDEX_TO_ID).equals(new ExtendedChildNumber(to)) &&
            accountPath.get(PATH_INDEX_FROM_ID).equals(new ExtendedChildNumber(from)))
                return (FriendKeyChain)chain;
        }
        return null;
    }

    public FriendKeyChain getFriendKeyChain(Sha256Hash myBlockchainUserId, Sha256Hash theirBlockchainUserId, FriendKeyChain.KeyChainType type ) {
        return getFriendKeyChain(myBlockchainUserId, 0, theirBlockchainUserId, type);
    }

    public FriendKeyChain getFriendKeyChain(EvolutionContact contact, FriendKeyChain.KeyChainType type ) {
        return getFriendKeyChain(contact.getEvolutionUserId(), contact.getUserAccount(), contact.getFriendUserId(), type);
    }

    @Override
    public void addAndActivateHDChain(DeterministicKeyChain chain) {
        if(chain instanceof FriendKeyChain)
            super.addAndActivateHDChain(chain);
        else throw new IllegalArgumentException("chain is not of type FriendKeyChain");
    }

    public boolean hasKeyChains() { return !chains.isEmpty(); }

    static FriendKeyChainGroup fromProtobufUnencrypted(NetworkParameters params, List<Protos.Key> keys, KeyChainType type) throws UnreadableWalletException {
        return fromProtobufUnencrypted(params, keys, new DefaultKeyChainFactory(), type);
    }

    public static FriendKeyChainGroup fromProtobufUnencrypted(NetworkParameters params, List<Protos.Key> keys, KeyChainFactory factory, KeyChainType type) throws UnreadableWalletException {
        BasicKeyChain basicKeyChain = BasicKeyChain.fromProtobufUnencrypted(keys);
        List<DeterministicKeyChain> chains = DeterministicKeyChain.fromProtobuf(keys, null, factory);
        for(DeterministicKeyChain chain : chains) {
            Preconditions.checkState(chain instanceof FriendKeyChain);
        }
        EnumMap<KeyPurpose, DeterministicKey> currentKeys = null;
        if (!chains.isEmpty())
            currentKeys = createCurrentKeysMap(chains);
        //TODO:  search through the currentKeys and then also chains to match up issued key counts
        for(DeterministicKeyChain chain : chains) {
            FriendKeyChain contactChain = (FriendKeyChain)chain;
            ImmutableList<ChildNumber> accountPath = contactChain.getAccountPath();
            int issuedKeysCount = 0;
            for(Protos.Key key : keys) {
                List<Protos.ExtendedChildNumber> path = key.getExtendedPathList();
                if(accountPath.size() + 1 <= path.size()) {
                    if(ExtendedChildNumber.equals(accountPath.get(0),path.get(0)) &&
                            ExtendedChildNumber.equals(accountPath.get(1),path.get(1)) &&
                            ExtendedChildNumber.equals(accountPath.get(2), path.get(2)) &&
                            ExtendedChildNumber.equals(accountPath.get(3), path.get(3)) &&
                            ExtendedChildNumber.equals(accountPath.get(4), path.get(4)) &&
                            ExtendedChildNumber.equals(accountPath.get(5), path.get(5)) &&
                            ExtendedChildNumber.equals(accountPath.get(6), path.get(6)))
                        issuedKeysCount++;
                }
            }
            if(issuedKeysCount > 0)
                contactChain.setIssuedKeys(issuedKeysCount);
        }
        return new FriendKeyChainGroup(params, basicKeyChain, chains, currentKeys, null);
    }

    static FriendKeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys, KeyCrypter crypter, KeyChainType type) throws UnreadableWalletException {
        return fromProtobufEncrypted(params, keys, crypter, new DefaultKeyChainFactory(), type);
    }

    public static FriendKeyChainGroup fromProtobufEncrypted(NetworkParameters params, List<Protos.Key> keys, KeyCrypter crypter, KeyChainFactory factory, KeyChainType type) throws UnreadableWalletException {
        checkNotNull(crypter);
        BasicKeyChain basicKeyChain = BasicKeyChain.fromProtobufEncrypted(keys, crypter);
        List<DeterministicKeyChain> chains = DeterministicKeyChain.fromProtobuf(keys, crypter, factory);
        for(DeterministicKeyChain chain : chains) {
            Preconditions.checkState(chain instanceof FriendKeyChain);
        }
        EnumMap<KeyChain.KeyPurpose, DeterministicKey> currentKeys = null;
        if (!chains.isEmpty())
            currentKeys = createCurrentKeysMap(chains);
        //TODO:  search through the currentKeys and then also chains to match up issued key counts
        for(DeterministicKeyChain chain : chains) {
            FriendKeyChain contactChain = (FriendKeyChain)chain;
            ImmutableList<ChildNumber> accountPath = contactChain.getAccountPath();
            int issuedKeysCount = 0;
            for(Protos.Key key : keys) {
                List<Protos.ExtendedChildNumber> path = key.getExtendedPathList();
                if(accountPath.size() + 1 <= path.size()) {
                    if(ExtendedChildNumber.equals(accountPath.get(0),path.get(0)) &&
                            ExtendedChildNumber.equals(accountPath.get(1),path.get(1)) &&
                            ExtendedChildNumber.equals(accountPath.get(2), path.get(2)) &&
                            ExtendedChildNumber.equals(accountPath.get(3), path.get(3)) &&
                            ExtendedChildNumber.equals(accountPath.get(4), path.get(4)) &&
                            ExtendedChildNumber.equals(accountPath.get(5), path.get(5)) &&
                            ExtendedChildNumber.equals(accountPath.get(6), path.get(6)))
                        issuedKeysCount++;
                }
            }
            if(issuedKeysCount > 0)
                contactChain.setIssuedKeys(issuedKeysCount);
        }
        return new FriendKeyChainGroup(params, basicKeyChain, chains, currentKeys, crypter);
    }

    /**
     * Returns a key that hasn't been seen in a transaction yet, and which is suitable for displaying in a wallet
     * user interface as "a convenient key to receive funds on" when the purpose parameter is
     * {@link KeyChain.KeyPurpose#RECEIVE_FUNDS}. The returned key is stable until
     * it's actually seen in a pending or confirmed transaction, at which point this method will start returning
     * a different key (for each purpose independently).
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #currentAddress(KeyChain.KeyPurpose)}
     * to get a proper P2SH address</p>
     */
    public DeterministicKey currentKey(EvolutionContact contact, FriendKeyChain.KeyChainType type) {
        DeterministicKeyChain chain = getFriendKeyChain(contact, type);
        if (chain.isMarried()) {
            throw new UnsupportedOperationException("Key is not suitable to receive coins for married keychains." +
                    " Use freshAddress to get P2SH address instead");
        }
        DeterministicKey current = currentKeys.get(KeyPurpose.RECEIVE_FUNDS);
        if (current == null) {
            current = freshKey(KeyPurpose.RECEIVE_FUNDS);
            currentKeys.put(KeyPurpose.RECEIVE_FUNDS, current);
        }
        return current;
    }

    /**
     * Returns address for a {@link #currentKey(org.bitcoinj.evolution.EvolutionContact, KeyChainType)}
     */
    public Address currentAddress(EvolutionContact contact, FriendKeyChain.KeyChainType type, KeyChain.KeyPurpose purpose) {
        DeterministicKeyChain chain = getFriendKeyChain(contact, type);
        if (chain.isMarried()) {
            Address current = currentAddresses.get(purpose);
            if (current == null) {
                current = freshAddress(purpose);
                currentAddresses.put(purpose, current);
            }
            return current;
        } else {
            return currentKey(purpose).toAddress(params);
        }
    }

    /**
     * Returns a key that has not been returned by this method before (fresh). You can think of this as being
     * a newly created key, although the notion of "create" is not really valid for a
     * {@link DeterministicKeyChain}. The returned key is suitable for being put
     * into a receive coins wizard type UI. You should use this when the user is definitely going to hand this key out
     * to someone who wishes to send money.
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #freshAddress(org.bitcoinj.evolution.EvolutionContact, org.bitcoinj.wallet.FriendKeyChain.KeyChainType)}
     * to get a proper P2SH address</p>
     */
    public DeterministicKey freshKey(EvolutionContact contact, KeyChainType type) {
        return freshKeys(contact, type, 1).get(0);
    }

    /**
     * Returns a key/s that have not been returned by this method before (fresh). You can think of this as being
     * newly created key/s, although the notion of "create" is not really valid for a
     * {@link DeterministicKeyChain}. You should use this when the user is definitely going to hand this key out
     * to someone who wishes to send money.
     * <p>This method is not supposed to be used for married keychains and will throw UnsupportedOperationException if
     * the active chain is married.
     * For married keychains use {@link #freshAddress(KeyChain.KeyPurpose)}
     * to get a proper P2SH address</p>
     */

    public List<DeterministicKey> freshKeys(EvolutionContact contact, KeyChainType type, int numberOfKeys) {
        DeterministicKeyChain chain = getFriendKeyChain(contact, type);
        if (chain.isMarried()) {
            throw new UnsupportedOperationException("Key is not suitable to receive coins for married keychains." +
                    " Use freshAddress to get P2SH address instead");
        }
        return chain.getKeys(KeyPurpose.RECEIVE_FUNDS, numberOfKeys);   // Always returns the next key along the key chain.
    }

    /**
     * Returns address for a {@link #freshKey(org.bitcoinj.evolution.EvolutionContact, org.bitcoinj.wallet.FriendKeyChain.KeyChainType)}
     */
    public Address freshAddress(EvolutionContact contact, FriendKeyChain.KeyChainType type) {
        DeterministicKeyChain chain = getFriendKeyChain(contact, type);
        if (chain.isMarried()) {
            Script outputScript = chain.freshOutputScript(KeyPurpose.RECEIVE_FUNDS);
            checkState(outputScript.isPayToScriptHash()); // Only handle P2SH for now
            Address freshAddress = Address.fromP2SHScript(params, outputScript);
            maybeLookaheadScripts();
            currentAddresses.put(KeyPurpose.RECEIVE_FUNDS, freshAddress);
            return freshAddress;
        } else {
            return freshKey(contact, type).toAddress(params);
        }
    }
}
