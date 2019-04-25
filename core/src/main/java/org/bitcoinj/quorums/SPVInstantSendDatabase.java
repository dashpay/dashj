package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class SPVInstantSendDatabase extends AbstractManager implements InstantSendDatabase {

    private static final Logger log = LoggerFactory.getLogger(MasternodeManager.class);
    private ReentrantLock lock = Threading.lock("SPVInstantSendDatabase");

    InstantSendManager manager;
    HashMap<Sha256Hash, InstantSendLock> islockCache;
    HashMap<Sha256Hash, Sha256Hash> txidCache;
    HashMap<TransactionOutPoint, Sha256Hash> outpointCache;

    public SPVInstantSendDatabase(Context context) {
        super(context);
        islockCache = new HashMap<Sha256Hash, InstantSendLock>();
        txidCache = new HashMap<Sha256Hash, Sha256Hash>();
        outpointCache = new HashMap<TransactionOutPoint, Sha256Hash>();
    }

    @Override
    public void setInstantSendManager(InstantSendManager manager) {
        this.manager = manager;
    }

    public void writeNewInstantSendLock(Sha256Hash hash, InstantSendLock islock) {
        lock.lock();
        try {
            islockCache.put(hash, islock);
            txidCache.put(islock.txid, hash);
            for (TransactionOutPoint in : islock.inputs) {
                outpointCache.put(in, hash);
            }
        } finally {
            lock.unlock();
        }
        //save
    }
    public void removeInstantSendLock(Sha256Hash hash, InstantSendLock islock) {
        lock.lock();
        try {
            islockCache.remove(hash);
            txidCache.remove(islock.txid);
            for (TransactionOutPoint in : islock.inputs) {
                outpointCache.remove(in);
            }
        } finally {
            lock.unlock();
        }
    }
    //void removeInstantSendLock(CDBBatch& batch, Sha256Hash hash, InstantSendLock islock);

    public void writeInstantSendLockMined(Sha256Hash hash, int nHeight) {

    }
    public void removeInstantSendLockMined(Sha256Hash hash, int nHeight) {

    }

    public HashMap<Sha256Hash, InstantSendLock> removeConfirmedInstantSendLocks(int nUntilHeight) {
        /*auto it = std::unique_ptr<CDBIterator>(db.NewIterator());

        auto firstKey = BuildInversedISLockMinedKey(nUntilHeight, uint256());

        it->Seek(firstKey);

        CDBBatch deleteBatch(db);
        HashMap<Sha256Hash, InstantSendLock> ret = new HashMap<Sha256Hash, InstantSendLock>();
        while (it->Valid()) {
            decltype(firstKey) curKey;
            if (!it->GetKey(curKey) || std::get<0>(curKey) != "is_m") {
                break;
            }
            uint32_t nHeight = std::numeric_limits<uint32_t>::max() - be32toh(std::get<1>(curKey));
            if (nHeight > nUntilHeight) {
                break;
            }

            auto& islockHash = std::get<2>(curKey);
            auto islock = GetInstantSendLockByHash(islockHash);
            if (islock) {
                RemoveInstantSendLock(deleteBatch, islockHash, islock);
                ret.emplace(islockHash, islock);
            }

            deleteBatch.Erase(curKey);

            it->Next();
        }

        db.WriteBatch(deleteBatch);

        return ret;
        */
        return null;
    }

    public InstantSendLock getInstantSendLockByHash(Sha256Hash hash) {
        lock.lock();
        try {
            return islockCache.get(hash);
        } finally {
            lock.unlock();
        }
    }

    public Sha256Hash getInstantSendLockHashByTxid(Sha256Hash txid) {
        lock.lock();
        try {
            return txidCache.get(txid);
        } finally {
            lock.unlock();
        }
    }

    public InstantSendLock getInstantSendLockByTxid(Sha256Hash txid) {
        Sha256Hash islockHash = getInstantSendLockHashByTxid(txid);
        if (islockHash == null) {
            return null;
        }
        return getInstantSendLockByHash(islockHash);
    }
    public InstantSendLock getInstantSendLockByInput(TransactionOutPoint outpoint) {
        lock.lock();
        try {
            Sha256Hash islockHash = outpointCache.get(outpoint);
            if (islockHash == null) {
                return null;
            }
            return getInstantSendLockByHash(islockHash);
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void parse() throws ProtocolException {

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        lock.lock();
        try {

        } finally {
            lock.unlock();
        }
    }

    @Override
    public AbstractManager createEmpty() {
        return new SPVInstantSendDatabase(Context.get());
    }

    @Override
    public void checkAndRemove() {

    }

    @Override
    public void clear() {

    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }
}
