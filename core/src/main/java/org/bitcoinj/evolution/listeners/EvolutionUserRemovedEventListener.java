package org.bitcoinj.evolution.listeners;

import org.bitcoinj.evolution.EvolutionUser;
@Deprecated
public interface EvolutionUserRemovedEventListener {

    void onUserRemoved(EvolutionUser user);
}