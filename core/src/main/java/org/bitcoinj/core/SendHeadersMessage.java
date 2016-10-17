package org.bitcoinj.core;

/**
 * Created by Eric on 10/16/2016.
 */
public class SendHeadersMessage extends EmptyMessage {
    public SendHeadersMessage() {
        super();
    }

    public SendHeadersMessage(NetworkParameters params) {
        super(params);
        length = 0;
    }
}
