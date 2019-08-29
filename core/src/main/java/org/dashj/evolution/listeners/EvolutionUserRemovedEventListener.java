package org.bitcoinj.evolution.listeners;

import org.bitcoinj.evolution.EvolutionUser;

public interface EvolutionUserRemovedEventListener {

    void onUserRemoved(EvolutionUser user);
}