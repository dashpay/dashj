package org.bitcoinj.governance;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * Created by Eric on 5/23/2018.
 */
public class RateCheckBuffer extends ChildMessage {

    private static final int RATE_BUFFER_SIZE = 5;

    private ArrayList<Long> vecTimestamps;

    private int nDataStart;

    private int nDataEnd;

    private boolean fBufferEmpty;

    public RateCheckBuffer(NetworkParameters params) {
        super(params);
        this.vecTimestamps = new ArrayList<Long>(RATE_BUFFER_SIZE);
        this.nDataStart = 0;
        this.nDataEnd = 0;
        this.fBufferEmpty = true;
    }

    public RateCheckBuffer(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public final void addTimestamp(long nTimestamp) {
        if ((nDataEnd == nDataStart) && !fBufferEmpty) {
            // Buffer full, discard 1st element
            nDataStart = (nDataStart + 1) % RATE_BUFFER_SIZE;
        }
        vecTimestamps.add(nDataEnd, nTimestamp);
        nDataEnd = (nDataEnd + 1) % RATE_BUFFER_SIZE;
        fBufferEmpty = false;
    }

    public final long getMinTimestamp() {
        int nIndex = nDataStart;
        long nMin = Long.MAX_VALUE;
        if (fBufferEmpty) {
            return nMin;
        }
        do {
            if (vecTimestamps.get(nIndex) < nMin) {
                nMin = vecTimestamps.get(nIndex);
            }
            nIndex = (nIndex + 1) % RATE_BUFFER_SIZE;
        } while (nIndex != nDataEnd);
        return nMin;
    }

    public final long getMaxTimestamp() {
        int nIndex = nDataStart;
        long nMax = 0;
        if (fBufferEmpty) {
            return nMax;
        }
        do {
            if (vecTimestamps.get(nIndex) > nMax) {
                nMax = vecTimestamps.get(nIndex);
            }
            nIndex = (nIndex + 1) % RATE_BUFFER_SIZE;
        } while (nIndex != nDataEnd);
        return nMax;
    }

    public final int getCount() {
        int nCount = 0;
        if (fBufferEmpty) {
            return 0;
        }
        if (nDataEnd > nDataStart) {
            nCount = nDataEnd - nDataStart;
        } else {
            nCount = RATE_BUFFER_SIZE - nDataStart + nDataEnd;
        }

        return nCount;
    }

    public final double getRate() {
        int nCount = getCount();
        if (nCount < RATE_BUFFER_SIZE) {
            return 0.0;
        }
        long nMin = getMinTimestamp();
        long nMax = getMaxTimestamp();
        if (nMin == nMax) {
            // multiple objects with the same timestamp => infinite rate
            return 1.0e10;
        }
        return (double)nCount / (double)(nMax - nMin);
    }

    protected void parse()
    {
        long size = readVarInt();
        vecTimestamps = new ArrayList<Long>((int)size);
        for(int i = 0; i < size; ++i) {
            vecTimestamps.add(readInt64());
        }
        nDataStart = (int)readUint32();
        nDataEnd = (int)readUint32();
        fBufferEmpty = readBytes(1)[0] != 0 ? true : false;

        length = cursor - offset;
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(vecTimestamps.size()).encode());
        for(Long l : vecTimestamps) {
            Utils.int64ToByteStreamLE(l, stream);
        }
        Utils.uint32ToByteStreamLE(nDataStart, stream);
        Utils.uint32ToByteStreamLE(nDataEnd, stream);
        byte [] value = new byte[1];
        value[0] = (byte)(fBufferEmpty ? 1 : 0);
        stream.write(value);
    }
}

