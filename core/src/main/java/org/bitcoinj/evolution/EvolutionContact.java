package org.bitcoinj.evolution;

import org.bitcoinj.core.Sha256Hash;

public class EvolutionContact {
    Sha256Hash evolutionUserId;
    Sha256Hash friendUserId;
    int userAccount;

    public EvolutionContact(Sha256Hash evolutionUserId, Sha256Hash friendUserId) {
        this(evolutionUserId, 0, friendUserId);
    }

    public EvolutionContact(Sha256Hash evolutionUserId, int userAccount, Sha256Hash friendUserId) {
        this.evolutionUserId = evolutionUserId;
        this.userAccount = userAccount;
        this.friendUserId = friendUserId;
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
}
