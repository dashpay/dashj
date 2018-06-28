package org.bitcoinj.core;

/**
 * Created by Hash Engineering on 06/26/2018.
 */
public class SendCompactBlocksMessage extends EmptyMessage {

    public SendCompactBlocksMessage(NetworkParameters params) {
        super(params);
        length = 0;
    }
}
