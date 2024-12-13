/*
 * Copyright 2023 Dash Core Group
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

package org.bitcoinj.core;

import org.bitcoinj.store.BlockStoreException;

import javax.annotation.Nullable;

import static java.lang.Math.max;

/**
 * Manages a header chain and the regular blockchain
 */
public class DualBlockChain {
    private final @Nullable AbstractBlockChain headersChain;
    private final AbstractBlockChain blockChain;

    public DualBlockChain(@Nullable AbstractBlockChain headersChain, AbstractBlockChain blockChain) {
        this.headersChain = headersChain;
        this.blockChain = blockChain;
    }

    public AbstractBlockChain getBlockChain() {
        return blockChain;
    }

    public AbstractBlockChain getHeadersChain() {
        return headersChain;
    }


    public int getBlockHeight(Sha256Hash blockHash) {
        try {
            return getLongestChain().getBlockStore().get(blockHash).getHeight();
        } catch (BlockStoreException x) {
            return -1;
        }
    }

    public int getBestChainHeight() {
        int height = blockChain.getBestChainHeight();
        if (headersChain != null)
            height = max(headersChain.getBestChainHeight(), blockChain.getBestChainHeight());
        return height;
    }

    public StoredBlock getBlockOld(Sha256Hash blockHash) {
        try {
            StoredBlock block =  blockChain.getBlockStore().get(blockHash);
            if (block == null && headersChain != null) {
                block = headersChain.getBlockStore().get(blockHash);
            }
            return block;
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private AbstractBlockChain getLongestChain() {
        return headersChain != null && headersChain.getBestChainHeight() > blockChain.getBestChainHeight() ?
            headersChain : blockChain;
    }

    public StoredBlock getBlock(Sha256Hash blockHash) {
        try {
            AbstractBlockChain chain = getLongestChain();
            return chain.getBlockStore().get(blockHash);
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public StoredBlock getBlockAncestor(StoredBlock block, int height) {
        try {
            AbstractBlockChain chain = getLongestChain();
            return block.getAncestor(chain.getBlockStore(), height);
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public StoredBlock getBlock(int height) {
        try {
            AbstractBlockChain chain = getLongestChain();
            return chain.getBlockStore().get(height);
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public StoredBlock getChainHead() {
        return getLongestChain().chainHead;
    }
}
