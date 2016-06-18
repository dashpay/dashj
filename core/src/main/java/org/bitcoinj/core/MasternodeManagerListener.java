package org.bitcoinj.core;

/**
 * Created by Hash Engineering on 5/14/2016.
 */
public interface MasternodeManagerListener {
    void onMasternodeCountChanged(int newCount);
}