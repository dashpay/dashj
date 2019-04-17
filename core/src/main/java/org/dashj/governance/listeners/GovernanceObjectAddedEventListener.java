package org.dashj.governance.listeners;

import org.dashj.core.Sha256Hash;
import org.dashj.governance.GovernanceObject;

public interface GovernanceObjectAddedEventListener {
    void onGovernanceObjectAdded(Sha256Hash nHash, GovernanceObject object);
}