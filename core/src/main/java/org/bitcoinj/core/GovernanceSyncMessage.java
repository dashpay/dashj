package org.bitcoinj.core;

/**
 * Created by Eric on 2/12/2017.
 */
public class GovernanceSyncMessage extends EmptyMessage {
    public GovernanceSyncMessage() {
        super();
    }

    public GovernanceSyncMessage(NetworkParameters params) {
        super(params);
        length = 0;
    }
}

