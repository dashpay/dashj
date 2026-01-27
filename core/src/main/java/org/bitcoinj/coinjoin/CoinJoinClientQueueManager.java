/*
 * Copyright (c) 2022 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoinj.coinjoin;

import org.bitcoinj.coinjoin.utils.CoinJoinManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeSync;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.MasternodeMetaDataManager;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.function.Predicate;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_EXTRA;

public class CoinJoinClientQueueManager extends CoinJoinBaseManager {
    private final Context context;
    private final CoinJoinManager coinJoinManager;
    private final SimplifiedMasternodeListManager masternodeListManager;
    private final MasternodeMetaDataManager masternodeMetaDataManager;
    private final MasternodeSync masternodeSync;
    private final Logger log = LoggerFactory.getLogger(CoinJoinClientManager.class);
    private final HashMap<Sha256Hash, Long> spammingMasternodes = new HashMap();

    public CoinJoinClientQueueManager(Context context, CoinJoinManager coinJoinManager, SimplifiedMasternodeListManager masternodeListManager, MasternodeMetaDataManager masternodeMetaDataManager, MasternodeSync masternodeSync) {
        super();
        this.coinJoinManager = coinJoinManager;
        this.masternodeListManager = masternodeListManager;
        this.masternodeMetaDataManager = masternodeMetaDataManager;
        this.masternodeSync = masternodeSync;
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
                    if (q.isReady() == dsq.isReady() && q.getProTxHash().equals(dsq.getProTxHash())) {
                        // no way the same mn can send another dsq with the same readiness this soon
                        if (!spammingMasternodes.containsKey(dsq.getProTxHash())) {
                            spammingMasternodes.put(dsq.getProTxHash(), Utils.currentTimeMillis());
                            log.info(COINJOIN_EXTRA, "coinjoin: DSQUEUE: Peer {} is sending WAY too many dsq messages for a mn {}", from.getAddress().getAddr(), dsq.getProTxHash());
                        }
                        return;
                    }
                }
            } // cs_vecqueue
            finally {
                queueLock.unlock();
            }


            log.info(COINJOIN_EXTRA, "coinjoin: DSQUEUE -- {} new", dsq);

            if (dsq.isTimeOutOfBounds())
                return;

            SimplifiedMasternodeList mnList = masternodeListManager.getListAtChainTip();
            Masternode dmn = mnList.getMN(dsq.getProTxHash());
            if (dmn == null)
                return;

            if (!dsq.checkSignature(dmn.getPubKeyOperator())) {
                // add 10 points to ban score
                return;
            }

            // if the queue is ready, submit if we can
            if (dsq.isReady() && isTrySubmitDenominate(dmn)) {
                log.info("coinjoin: DSQUEUE: {} is ready on mn {}", dsq, dmn.getService());
            } else {
                long nLastDsq = masternodeMetaDataManager.getMetaInfo(dmn.getProTxHash()).getLastDsq();
                long nDsqThreshold = masternodeMetaDataManager.getDsqThreshold(dmn.getProTxHash(), mnList.getValidMNsCount());
                log.info(COINJOIN_EXTRA, "coinjoin: DSQUEUE -- lastDsq: {}  dsqThreshold: {}  dsqCount: {}",
                        nLastDsq, nDsqThreshold, masternodeMetaDataManager.getDsqCount());
                // don't allow a few nodes to dominate the queuing process
                if (nLastDsq != 0 && nDsqThreshold > masternodeMetaDataManager.getDsqCount()) {
                    if (!spammingMasternodes.containsKey(dsq.getProTxHash())) {
                        spammingMasternodes.put(dsq.getProTxHash(), Utils.currentTimeMillis());
                        log.info(COINJOIN_EXTRA, "coinjoin: DSQUEUE: Mn {} is sending too many dsq messages", dmn.getProTxHash());
                    }
                    return;
                }

                masternodeMetaDataManager.allowMixing(dmn.getProTxHash());

                log.info("coinjoin: DSQUEUE: new {} from mn {}", dsq, dmn.getService().getAddr());

                coinJoinManager.coinJoinClientManagers.values().stream().anyMatch(
                        coinJoinClientManager -> coinJoinClientManager.markAlreadyJoinedQueueAsTried(dsq)
                );

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
        return coinJoinManager.coinJoinClientManagers.values().stream().anyMatch(new Predicate<CoinJoinClientManager>() {
            @Override
            public boolean test(CoinJoinClientManager coinJoinClientManager) {
                return coinJoinClientManager.trySubmitDenominate(dmn.getService());
            }
        });
    }

    public void doMaintenance() {

        if (!CoinJoinClientOptions.isEnabled())
            return;
        if (masternodeSync == null)
            return;

        if (masternodeSync.hasSyncFlag(MasternodeSync.SYNC_FLAGS.SYNC_GOVERNANCE) &&
                !masternodeSync.isBlockchainSynced())
            return;

        checkQueue();

        spammingMasternodes.entrySet().removeIf(entry -> entry.getValue() + CoinJoinConstants.COINJOIN_QUEUE_TIMEOUT * 1000 < Utils.currentTimeMillis());
    }
}
