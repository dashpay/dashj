package org.bitcoinj.evolution;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.ExtendedChildNumber;
import org.bitcoinj.crypto.HDPath;
import org.bitcoinj.wallet.FriendKeyChain;

import java.util.List;

public class EvolutionContact {
    Sha256Hash evolutionUserId;
    Sha256Hash friendUserId;
    int userAccount;
    int friendAccountReference;

    public EvolutionContact(String userId, String friendUserId) {
        this(Sha256Hash.wrap(Base58.decode(userId)), Sha256Hash.wrap(Base58.decode(friendUserId)));
    }

    public EvolutionContact(String userId, int account, String friendUserId, int friendAccountReference) {
        this(Sha256Hash.wrap(Base58.decode(userId)), account, Sha256Hash.wrap(Base58.decode(friendUserId)), friendAccountReference);
    }

    public EvolutionContact(Sha256Hash evolutionUserId, Sha256Hash friendUserId) {
        this(evolutionUserId, 0, friendUserId, 0);
    }

    public EvolutionContact(Sha256Hash evolutionUserId, int userAccount, Sha256Hash friendUserId, int friendAccountReference) {
        this.evolutionUserId = evolutionUserId;
        this.userAccount = userAccount;
        this.friendUserId = friendUserId;
        this.friendAccountReference = friendAccountReference;
    }

    public EvolutionContact(List<ChildNumber> accountPath, boolean owner) {
        this.evolutionUserId = Sha256Hash.wrap(((ExtendedChildNumber)accountPath.get(owner ?
                FriendKeyChain.PATH_INDEX_TO_ID : FriendKeyChain.PATH_INDEX_FROM_ID)).bi());
        this.friendUserId = Sha256Hash.wrap(((ExtendedChildNumber)accountPath.get(!owner ?
                FriendKeyChain.PATH_INDEX_TO_ID : FriendKeyChain.PATH_INDEX_FROM_ID)).bi());
        this.userAccount = owner ? accountPath.get(FriendKeyChain.PATH_INDEX_ACCOUNT).num() : 0;
        this.friendAccountReference = !owner ? accountPath.get(FriendKeyChain.PATH_INDEX_ACCOUNT).num() : -1;
    }

    public Sha256Hash getEvolutionUserId() {
        return evolutionUserId;
    }

    public Sha256Hash getFriendUserId() {
        return friendUserId;
    }

    public int getUserAccount() {
        return userAccount;
    }

    public int getFriendAccountReference() {
        return friendAccountReference;
    }
}
