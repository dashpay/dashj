package org.bitcoinj.governance;

import org.bitcoinj.core.ChildMessage;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 6/2/2018.
 */
public class LastObjectRecord extends ChildMessage {
    public LastObjectRecord(NetworkParameters params) {
        this(params, true);
    }

    public LastObjectRecord(NetworkParameters params, boolean fStatusOKIn) {
        this.triggerBuffer = new RateCheckBuffer(params);
        this.watchdogBuffer = new RateCheckBuffer(params);
        this.fStatusOK = fStatusOKIn;
    }

    public LastObjectRecord(NetworkParameters params, byte [] payload, int cursor) {
        super(params, payload, cursor);
    }

    public RateCheckBuffer triggerBuffer;
    public RateCheckBuffer watchdogBuffer;
    public boolean fStatusOK;

    @Override
    protected void parse() throws ProtocolException {
        triggerBuffer = new RateCheckBuffer(params, payload, offset);
        cursor += triggerBuffer.getMessageSize();
        watchdogBuffer = new RateCheckBuffer(params, payload, offset);
        cursor += watchdogBuffer.getMessageSize();
        fStatusOK = readBytes(1)[0] == 0 ? false : true;
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        triggerBuffer.bitcoinSerialize(stream);
        watchdogBuffer.bitcoinSerialize(stream);
        stream.write((byte)(fStatusOK == true ? 1 : 0));
    }

    public String toString() {
        return "";
    }
}
