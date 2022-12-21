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
package org.bitcoinj.coinjoin.utils;

import org.bitcoinj.coinjoin.CoinJoin;
import org.bitcoinj.coinjoin.CoinJoinBroadcastTx;
import org.bitcoinj.coinjoin.CoinJoinClientManager;
import org.bitcoinj.coinjoin.CoinJoinClientQueueManager;
import org.bitcoinj.coinjoin.CoinJoinComplete;
import org.bitcoinj.coinjoin.CoinJoinFinalTransaction;
import org.bitcoinj.coinjoin.CoinJoinQueue;
import org.bitcoinj.coinjoin.CoinJoinStatusUpdate;
import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.evolution.Masternode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CoinJoinManager {

    private static final Logger log = LoggerFactory.getLogger(CoinJoinManager.class);
    private final Context context;
    public final HashMap<String, CoinJoinClientManager> coinJoinClientManagers;
    private final CoinJoinClientQueueManager coinJoinClientQueueManager;

    private MasternodeGroup masternodeGroup;

    private ScheduledFuture<?> schedule;
    private AbstractBlockChain blockChain;

    public CoinJoinManager(Context context) {
        this.context = context;
        coinJoinClientManagers = new HashMap<>();
        coinJoinClientQueueManager = new CoinJoinClientQueueManager(context);
    }

    public static boolean isCoinJoinMessage(Message message) {
        return message instanceof CoinJoinStatusUpdate ||
                message instanceof CoinJoinFinalTransaction ||
                message instanceof CoinJoinComplete ||
                message instanceof CoinJoinQueue ||
                message instanceof CoinJoinBroadcastTx;
    }

    public CoinJoinClientQueueManager getCoinJoinClientQueueManager() {
        return coinJoinClientQueueManager;
    }

    public void processMessage(Peer from, Message message) {
        if (message instanceof CoinJoinQueue) {
            coinJoinClientQueueManager.processDSQueue(from, (CoinJoinQueue) message, false);
        } else if(message instanceof CoinJoinBroadcastTx) {
            processBroadcastTx((CoinJoinBroadcastTx) message);
        } else  {
            for (CoinJoinClientManager clientManager : coinJoinClientManagers.values()) {
                clientManager.processMessage(from, message, false);
            }
        }
    }

    private void processBroadcastTx(CoinJoinBroadcastTx dstx) {
        CoinJoin.addDSTX(dstx);
    }

    int tick = 0;

    public void doMaintenance() {
        // report masternode group
        tick++;
        if (tick % 10 == 0) {
            log.info("coinjoin: connected to {} masternodes", masternodeGroup.getConnectedPeers().size());
            for (Peer peer : masternodeGroup.getConnectedPeers()) {
                log.info("coinjoin: connected to masternode {}", peer);
            }
        }
        coinJoinClientQueueManager.doMaintenance();

        for (CoinJoinClientManager clientManager: coinJoinClientManagers.values()) {
            clientManager.doMaintenance();
        }
    }

    private Runnable maintenanceRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                doMaintenance();
            } catch (Exception x) {
                log.info("error when running doMaintenance", x);
            }
        }
    };

    public void start(ScheduledExecutorService scheduledExecutorService) {
        schedule = scheduledExecutorService.scheduleWithFixedDelay(
                maintenanceRunnable, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        if (schedule != null) {
            schedule.cancel(false);
            schedule = null;
        }

        for (CoinJoinClientManager clientManager: coinJoinClientManagers.values()) {
            clientManager.resetPool();
            clientManager.stopMixing();
            clientManager.close(context.blockChain);
        }
    }

    public void initMasternodeGroup(AbstractBlockChain blockChain) {
        this.blockChain = blockChain;
        masternodeGroup = new MasternodeGroup(context, blockChain);
    }

    public void setBlockchain(AbstractBlockChain blockChain) {

    }

    public boolean isMasternodeOrDisconnectRequested(MasternodeAddress address) {
        return masternodeGroup.isMasternodeOrDisconnectRequested(address);
    }

    public boolean addPendingMasternode(Sha256Hash proTxHash) {
        return masternodeGroup.addPendingMasternode(proTxHash);
    }

    public boolean forPeer(MasternodeAddress address, MasternodeGroup.ForPeer forPeer) {
        return masternodeGroup.forPeer(address, forPeer);
    }

    //boolean isRunning = false;

    public void startAsync() {
        if (!masternodeGroup.isRunning()) {
            log.info("coinjoin: broadcasting senddsq(true) to all peers");
            context.peerGroup.shouldSendDsq(true);
            masternodeGroup.startAsync();
        }
    }

    public void stopAsync() {
        if (masternodeGroup.isRunning()) {
            context.peerGroup.shouldSendDsq(false);
            masternodeGroup.stopAsync();
        }
    }

    public void disconnectMasternode(Masternode service) {
        masternodeGroup.disconnectMasternode(service);
    }

    @VisibleForTesting
    public void setMasternodeGroup(MasternodeGroup masternodeGroup) {
        this.masternodeGroup = masternodeGroup;
    }
}
