package org.bitcoinj.core;

/**
 * Created by Hash Engineering on 2/28/2016.
 */
public interface MasternodeSyncListener {
    void onSyncStatusChanged(int newStatus, double syncStatus);
}