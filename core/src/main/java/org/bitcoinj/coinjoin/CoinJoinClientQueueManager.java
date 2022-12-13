package org.bitcoinj.coinjoin;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Peer;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

public class CoinJoinClientQueueManager extends CoinJoinBaseManager {
    private final Context context;
    private final Logger log = LoggerFactory.getLogger(CoinJoinClientManager.class);

    public CoinJoinClientQueueManager(Context context) {
        super();
        this.context = context;
    }

    public void processDSQueue(Peer from, CoinJoinQueue dsq, boolean enable_bip61) {
        boolean isLocked = queueLock.tryLock();
        if (isLocked) {
            try {
                // process every dsq only once
                for (CoinJoinQueue q : coinJoinQueue) {
                    if (q == dsq) {
                        return;
                    }
                    if (q.isReady() == dsq.isReady() && q.getMasternodeOutpoint().equals(dsq.getMasternodeOutpoint())) {
                        // no way the same mn can send another dsq with the same readiness this soon
                        log.info("coinjoin: DSQUEUE -- Peer {} is sending WAY too many dsq messages for a masternode with collateral {}", from.getAddress().getAddr(), dsq.getMasternodeOutpoint().toStringShort());
                        return;
                    }
                }
            } // cs_vecqueue
            finally {
                queueLock.unlock();
            }


            log.info("coinjoin: DSQUEUE -- {} new", dsq);

            if (dsq.isTimeOutOfBounds())
                return;

            SimplifiedMasternodeList mnList = context.masternodeListManager.getListAtChainTip();
            Masternode dmn = mnList.getValidMNByCollateral(dsq.getMasternodeOutpoint()); //TODO - we don't have this
            if (dmn == null)
                return;

            if (!dsq.checkSignature(dmn.getPubKeyOperator())) {
                // add 10 points to ban score
                return;
            }

            // if the queue is ready, submit if we can
            if (dsq.isReady() && isTrySubmitDenominate(dmn)) {
                log.info("coinjoin: DSQUEUE -- CoinJoin queue ({}) is ready on masternode {}", dsq, dmn.getService());
            } else {
                long nLastDsq = context.masternodeMetaDataManager.getMetaInfo(dmn.getProTxHash()).getLastDsq();
                long nDsqThreshold = context.masternodeMetaDataManager.getDsqThreshold(dmn.getProTxHash(), mnList.getValidMNsCount());
                log.info("coinjoin: DSQUEUE -- lastDsq: {}  dsqThreshold: {}  dsqCount: {}", nLastDsq, nDsqThreshold, context.masternodeMetaDataManager.getDsqCount());
                // don't allow a few nodes to dominate the queuing process
                if (nLastDsq != 0 && nDsqThreshold > context.masternodeMetaDataManager.getDsqCount()) {
                    log.info("coinjoin: DSQUEUE -- Masternode {} is sending too many dsq messages", dmn.getProTxHash());
                    return;
                }

                context.masternodeMetaDataManager.allowMixing(dmn.getProTxHash());

                log.info("coinjoin: DSQUEUE -- new CoinJoin queue ({}) from masternode {}", dsq, dmn.getService());


                context.coinJoinManager.coinJoinClientManagers.values().stream().anyMatch(new Predicate<CoinJoinClientManager>() {
                    @Override
                    public boolean test(CoinJoinClientManager coinJoinClientManager) {
                        return coinJoinClientManager.markAlreadyJoinedQueueAsTried(dsq);
                    }
                });

                if (queueLock.tryLock()) {
                    try {
                        coinJoinQueue.add(dsq);
                    } finally {
                        queueLock.unlock();
                    }
                }
            }
        } // not locked
    }

    private boolean isTrySubmitDenominate(Masternode dmn) {
        return context.coinJoinManager.coinJoinClientManagers.values().stream().anyMatch(new Predicate<CoinJoinClientManager>() {
            @Override
            public boolean test(CoinJoinClientManager coinJoinClientManager) {
                return coinJoinClientManager.trySubmitDenominate(dmn.getService());
            }
        });
    }

    public void doMaintenance() {

        if (!CoinJoinClientOptions.isEnabled())
            return;
        if (context.masternodeSync == null)
            return;

        if (context.masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) &&
                !context.masternodeSync.isBlockchainSynced())
            return;

        checkQueue();
    }
}
