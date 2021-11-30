package org.bitcoinj.core;

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 2/27/2015.
 *
 * ssc Message
 */
public class SyncStatusCount extends Message {
    private static final Logger log = LoggerFactory.getLogger(SyncStatusCount.class);

    int itemId;
    int count;

    public SyncStatusCount(NetworkParameters params, byte [] payloadBytes)
    {
        super(params, payloadBytes, 0);
    }

    public SyncStatusCount(NetworkParameters params, byte [] payloadBytes, int cursor)
    {
        super(params, payloadBytes, cursor);
    }

    public SyncStatusCount(int itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    protected static int calcLength(byte[] buf, int offset) {

        int cursor = offset;

        //vin
        cursor += 8;

        return cursor - offset;
    }

    @Override
    protected void parse() throws ProtocolException {
        itemId = (int)readUint32();
        count = (int)readUint32();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(itemId, stream);
        Utils.uint32ToByteStreamLE(count, stream);
    }
}
