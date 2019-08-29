package org.dashj.core;

/**
 * Created by Hash Engineering on 5/14/2016.
 */
@Deprecated
public interface MasternodeManagerListener {
    void onMasternodeCountChanged(int newCount);
}