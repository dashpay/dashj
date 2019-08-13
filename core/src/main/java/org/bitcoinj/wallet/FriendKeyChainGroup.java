package org.bitcoinj.wallet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.ExtendedChildNumber;

import static org.bitcoinj.wallet.FriendKeyChain.*;

public class FriendKeyChainGroup extends KeyChainGroup {
    public FriendKeyChainGroup(NetworkParameters params) {
        super(params);
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
}
