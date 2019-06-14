package org.bitcoinj.quorums.listeners;

import org.bitcoinj.quorums.RecoveredSignature;

public interface RecoveredSignatureListener {

    void onNewRecoveredSignature(RecoveredSignature signature);
}
