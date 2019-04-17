package org.bitcoinj.evolution.listeners;

import org.bitcoinj.evolution.EvolutionUser;

public interface EvolutionUserAddedEventListener {

    void onUserAdded(EvolutionUser user);
}