package org.bitcoinj.governance.listeners;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.governance.GovernanceObject;

public interface GovernanceObjectAddedEventListener {
    void onGovernanceObjectAdded(Sha256Hash nHash, GovernanceObject object);
}