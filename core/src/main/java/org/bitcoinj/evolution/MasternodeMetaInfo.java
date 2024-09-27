package org.bitcoinj.evolution;

import org.bitcoinj.core.ChildMessage;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class MasternodeMetaInfo extends ChildMessage {
    ReentrantLock lock = Threading.lock("masternode_meta_info");

    @GuardedBy("lock")
    Sha256Hash proTxHash;

    //the dsq count from the last dsq broadcast of this node
    private AtomicLong lastDsq = new AtomicLong(0);

    private AtomicInteger mixingTxCount = new AtomicInteger(0);

    public MasternodeMetaInfo(Sha256Hash proTxHash) {
        this.proTxHash = proTxHash;
    }

    public MasternodeMetaInfo(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    protected void parse() throws ProtocolException {
        proTxHash = readHash();
        lastDsq.set(readInt64());
        mixingTxCount.set((int)readUint32());
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(proTxHash.getReversedBytes());
        Utils.int64ToByteStreamLE(lastDsq.get(), stream);
        Utils.uint32ToByteStreamLE(mixingTxCount.get(), stream);
    }

    public Sha256Hash getProTxHash() {
        return proTxHash;
    }

    public long getLastDsq() {
        return lastDsq.get();
    }

    public int getMixingTxCount() {
        return mixingTxCount.get();
    }

    public void setLastDsq(long lastDsq) {
        this.lastDsq.set(lastDsq);
    }

    public void setMixingTxCount(int mixingTxCount) {
        this.mixingTxCount.set(mixingTxCount);
    }
}
