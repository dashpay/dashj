/*
 * Copyright 2020 Dash Core Group
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

package org.bitcoinj.evolution.listeners;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.evolution.AssetLockTransaction;

/**
 *
 */

public interface AssetLockTransactionEventListener {
    /**
     *
     * @param tx the credit funding transaction
     * @param block the block that the transaction was contained in, unless the tx is not confirmed
     * @param blockType main chain or side chain
     */
    void onTransactionReceived(AssetLockTransaction tx, StoredBlock block,
                               BlockChain.NewBlockType blockType);
}
