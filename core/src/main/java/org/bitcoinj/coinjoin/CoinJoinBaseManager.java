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

import com.google.common.collect.Lists;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_EXTRA;

public class CoinJoinBaseManager {

    private final Logger log = LoggerFactory.getLogger(CoinJoinBaseManager.class);
    protected final ReentrantLock queueLock = Threading.lock("queueLock");

    @GuardedBy("queueLock")
    protected final ArrayList<CoinJoinQueue> coinJoinQueue;

    public CoinJoinBaseManager() {
        coinJoinQueue = Lists.newArrayList();
    }

    protected void setNull() {
        coinJoinQueue.clear();
    }

    protected void checkQueue() {
        if (queueLock.tryLock()) {
            try {
                Iterator<CoinJoinQueue> it = coinJoinQueue.iterator();
                while (it.hasNext()) {
                    CoinJoinQueue queue = it.next();
                    if (queue.isTimeOutOfBounds(Utils.currentTimeSeconds())) {
                        log.info(COINJOIN_EXTRA, "Removing a queue {}", queue);
                        it.remove();
                    }
                }
            } finally {
                queueLock.unlock();
            }
        }
    }

    public int getQueueSize() {
        return coinJoinQueue.size();
    }

    public CoinJoinQueue getQueueItemAndTry() {
        if (queueLock.tryLock()) {
            try {
                for (CoinJoinQueue dsq : coinJoinQueue) {
                    if (dsq.isTried() || dsq.isTimeOutOfBounds())
                        continue;
                    dsq.setTried(true);
                    return dsq;
                }
            } finally {
                queueLock.unlock();
            }
        }
        return null;
    }
}
