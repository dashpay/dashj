package org.bitcoinj.quorums;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;

import java.util.HashMap;

public interface InstantSendDatabase {

    void setInstantSendManager(InstantSendManager manager);

    void writeNewInstantSendLock(Sha256Hash hash, InstantSendLock islock);
    void removeInstantSendLock(Sha256Hash hash, InstantSendLock islock);
    //void removeInstantSendLock(CDBBatch& batch, Sha256Hash hash, InstantSendLock islock);

    void writeInstantSendLockMined(Sha256Hash hash, long nHeight);
    void removeInstantSendLockMined(Sha256Hash hash, long nHeight);
    HashMap<Sha256Hash, InstantSendLock> removeConfirmedInstantSendLocks(int nUntilHeight);

    InstantSendLock getInstantSendLockByHash(Sha256Hash hash);
    Sha256Hash getInstantSendLockHashByTxid(Sha256Hash txid);
    InstantSendLock getInstantSendLockByTxid(Sha256Hash txid);
    InstantSendLock getInstantSendLockByInput(TransactionOutPoint outpoint);
}
