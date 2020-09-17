package org.bitcoinj.evolution.listeners;

import org.bitcoinj.evolution.EvolutionUser;
@Deprecated
public interface EvolutionUserAddedEventListener {

    void onUserAdded(EvolutionUser user);
}