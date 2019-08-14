package org.bitcoinj.wallet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.ExtendedChildNumber;
import org.bitcoinj.crypto.KeyCrypter;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoinj.wallet.FriendKeyChain.*;

public class FriendKeyChainGroup extends KeyChainGroup {
    public FriendKeyChainGroup(NetworkParameters params) {
        super(params);
    }

    protected FriendKeyChainGroup(NetworkParameters params, @Nullable BasicKeyChain basicKeyChain, List<DeterministicKeyChain> chains,
                            @Nullable EnumMap<KeyChain.KeyPurpose, DeterministicKey> currentKeys, @Nullable KeyCrypter crypter) {
        super(params, basicKeyChain, chains, currentKeys, crypter);
    }

    public FriendKeyChain getFriendKeyChain(Sha256Hash myBlockchainUserId, Sha256Hash theirBlockchainUserId, FriendKeyChain.KeyChainType type ) {
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
            if(accountPath.get(PATH_INDEX_TO_ID).equals(new ExtendedChildNumber(to)) &&
            accountPath.get(PATH_INDEX_FROM_ID).equals(new ExtendedChildNumber(from)))
                return (FriendKeyChain)chain;
        }
        return null;
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
        return new FriendKeyChainGroup(params, basicKeyChain, chains, currentKeys, crypter);
    }
}
