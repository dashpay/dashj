package org.dashj.quorums.listeners;

import org.dashj.quorums.RecoveredSignature;

public interface RecoveredSignatureListener {

    void onNewRecoveredSignature(RecoveredSignature signature);
}
