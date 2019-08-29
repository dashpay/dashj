package org.dashj.core.listeners;

import org.dashj.core.SporkMessage;

public interface SporkUpdatedEventListener {
    void onSporkUpdated(SporkMessage sporkMessage);
}