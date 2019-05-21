package org.bitcoinj.quorums;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class SPVInstantSendDatabase extends AbstractManager implements InstantSendDatabase {

    private static final Logger log = LoggerFactory.getLogger(SPVInstantSendDatabase.class);
    private ReentrantLock lock = Threading.lock("SPVInstantSendDatabase");

    InstantSendManager manager;
    HashMap<Sha256Hash, InstantSendLock> islockCache;
    HashMap<Sha256Hash, Sha256Hash> txidCache;
    HashMap<TransactionOutPoint, Sha256Hash> outpointCache;
    HashMap<Sha256Hash, Long> isLockHashHeight;

    @Override
    public String toString() {
        return String.format("SPVISDB:  isLockCache:  %d, txidCache: %d, outpointCache: %d, isLockHashHeight: %d",
                islockCache.size(), txidCache.size(), outpointCache.size(), isLockHashHeight.size());
    }

    public SPVInstantSendDatabase(Context context) {
        super(context);
        islockCache = new HashMap<Sha256Hash, InstantSendLock>();
        txidCache = new HashMap<Sha256Hash, Sha256Hash>();
        outpointCache = new HashMap<TransactionOutPoint, Sha256Hash>();
        isLockHashHeight = new HashMap<Sha256Hash, Long>();
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


    public void writeInstantSendLockMined(Sha256Hash hash, long height) {
        lock.lock();
        try {
            isLockHashHeight.put(hash, height);
        } finally {
            lock.unlock();
        }
    }
    public void removeInstantSendLockMined(Sha256Hash hash, long height) {
        lock.lock();
        try {
            if(isLockHashHeight.containsKey(hash))
                isLockHashHeight.remove(hash);
        } finally {
            lock.unlock();
        }
    }

    public HashMap<Sha256Hash, InstantSendLock> removeConfirmedInstantSendLocks(int untilHeight) {

        HashMap<Sha256Hash, InstantSendLock> result = new HashMap<Sha256Hash, InstantSendLock>();

        Iterator<Map.Entry<Sha256Hash, Long>> iterator = isLockHashHeight.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<Sha256Hash, Long> entry = iterator.next();
            long height = entry.getValue();
            if(height > untilHeight)
                break;
            Sha256Hash lockHash = entry.getKey();
            InstantSendLock islock = getInstantSendLockByHash(lockHash);
            if(islock != null) {
                removeInstantSendLock(lockHash, islock);
                result.put(lockHash, islock);
                iterator.remove();
            }
        }
        return result;
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

    @Override
    public void close() {

    }
}
