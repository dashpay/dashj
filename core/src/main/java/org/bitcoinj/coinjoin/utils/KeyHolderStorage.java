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

import com.google.common.collect.Lists;
import org.bitcoinj.script.Script;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.WalletEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_EXTRA;

public class KeyHolderStorage {
    static final Logger log = LoggerFactory.getLogger(KeyHolderStorage.class);
    private final ReentrantLock lock = Threading.lock("storage");
    @GuardedBy("lock")
    ArrayList<KeyHolder> storage = new ArrayList<>();

    public Script addKey(WalletEx wallet) {
        KeyHolder keyHolder = new KeyHolder(wallet);
        Script script = keyHolder.getScriptForDestination();

        lock.lock();
        try {
            storage.add(keyHolder);
            log.info(COINJOIN_EXTRA, "KeyHolderStorage.addKey: storage size {}", storage.size());
        } finally {
            lock.unlock();
        }
        return script;
    }

    public void keepAll() {
        ArrayList<KeyHolder> tmp;
        lock.lock();
        try {
            tmp = storage;
            storage = new ArrayList<>();
        } finally {
            lock.unlock();
        }

        if (!tmp.isEmpty()) {
            for (KeyHolder key : tmp) {
                key.keepKey();
            }
            log.info(COINJOIN_EXTRA, "keepAll: {} keys kept", tmp.size());
        }
    }

    public void returnAll() {
        ArrayList<KeyHolder> tmp;
        lock.lock();
        try {
            tmp = storage;
            storage = new ArrayList<>();
        } finally {
            lock.unlock();
        }

        if (!tmp.isEmpty()) {
            for (KeyHolder key : tmp) {
                key.returnKey();
            }
            log.info(COINJOIN_EXTRA, "returnAll: {} keys returned", tmp.size());
        }
    }
}
