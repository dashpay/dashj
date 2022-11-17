package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.governance.GovernanceObject;
import org.bitcoinj.utils.Threading;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class MasternodeMetaDataManager extends AbstractManager {

    ReentrantLock lock = Threading.lock("metadatamanager");

    @GuardedBy("lock")
    HashMap<Sha256Hash, MasternodeMetaInfo> metaInfos = new HashMap<>();
    @GuardedBy("lock")
    ArrayList<Sha256Hash> dirtyGovernanceObjectHashes = new ArrayList<>();

    // keep track of dsq count to prevent masternodes from gaming coinjoin queue
    private AtomicLong dsqCount = new AtomicLong(0);

    public MasternodeMetaDataManager(Context context) {
        super(context);
    }

    public boolean addGovernanceVote(TransactionOutPoint outPoint, Sha256Hash hash) {
        return false;
    }

    public void removeGovernanceObject(Sha256Hash governanceObject) {

    }

    public ArrayList<Sha256Hash> getAndClearDirtyGovernanceObjectHashes() {
        return new ArrayList<Sha256Hash>();
    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }

    @Override
    public void checkAndRemove() {
        // do nothing
    }

    @Override
    public void clear() {
        metaInfos.clear();
        dirtyGovernanceObjectHashes.clear();
    }

    @Override
    public AbstractManager createEmpty() {
        return null;
    }


    @GuardedBy("lock")
    @Override
    public void close() {

    }

    @Override
    protected void parse() throws ProtocolException {
        clear();
        int size = (int)readVarInt();
        for (int i = 0; i< size; ++i) {
            MasternodeMetaInfo info = new MasternodeMetaInfo(params, payload, cursor);
            cursor += info.getMessageSize();
            metaInfos.put(info.getProTxHash(), info);
        }
        dsqCount.set(readInt64());
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(VarInt.sizeOf(metaInfos.size()));
        for (MasternodeMetaInfo info : metaInfos.values()) {
            info.bitcoinSerialize(stream);
        }
        Utils.int64ToByteStreamLE(dsqCount.get(), stream);
    }

    public MasternodeMetaInfo getMetaInfo(Sha256Hash proTxHash) {
        return getMetaInfo(proTxHash, true);
    }
    @GuardedBy("lock")
    MasternodeMetaInfo getMetaInfo(Sha256Hash proTxHash, boolean create) {
        MasternodeMetaInfo info = metaInfos.get(proTxHash);
        if (info != null) {
            return info;
        }
        if (!create) {
            return null;
        }
        info = new MasternodeMetaInfo(proTxHash);
        metaInfos.put(proTxHash, info);
        return info;
    }

    public long getDsqCount() {
        return dsqCount.get();
    }

    public long getDsqThreshold(Sha256Hash proTxHash, int mnCount) {
        MasternodeMetaInfo metaInfo = getMetaInfo(proTxHash);
        if (metaInfo == null) {
            // return a threshold which is slightly above nDsqCount i.e. a no-go
            return getDsqCount() + 1;
        }
        return metaInfo.getLastDsq() + mnCount / 5;
    }

    public void allowMixing(Sha256Hash proTxHash) {
        MasternodeMetaInfo mm = getMetaInfo(proTxHash);
        dsqCount.getAndIncrement();
        mm.setLastDsq(getDsqCount());
        mm.setMixingTxCount(0);
    }
    void disallowMixing(Sha256Hash proTxHash) {
        MasternodeMetaInfo mm = getMetaInfo(proTxHash);
        mm.setMixingTxCount(mm.getMixingTxCount()+1);
    }

    @GuardedBy("lock")
    @Override
    public String toString() {
        return "Masternodes: meta infos object count: " + metaInfos.size() +
                ", dsqCount: " + getDsqCount();
    }
}
