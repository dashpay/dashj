/**
 * Copyright 2013 Google Inc.
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

package org.bitcoinj.store;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.MappedByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

// TODO: Lose the mmap in this class. There are too many platform bugs that require odd workarounds.

/**
 * An SPVBlockStore holds a limited number of block headers in a memory mapped ring buffer. With such a store, you
 * may not be able to process very deep re-orgs and could be disconnected from the chain (requiring a replay),
 * but as they are virtually unheard of this is not a significant risk.
 */
public class HashStore {
    private static final Logger log = LoggerFactory.getLogger(HashStore.class);

    /** The default number of headers that will be stored in the ring buffer. */
    public static final int DEFAULT_NUM_HASHES = 5000;
    public static final String HEADER_MAGIC = "SPVH";

    protected volatile MappedByteBuffer buffer;
    protected int numHeaders;
    protected NetworkParameters params;

    protected ReentrantLock lock = Threading.lock("SPVBlockStore");

    // The entire ring-buffer is mmapped and accessing it should be as fast as accessing regular memory once it's
    // faulted in. Unfortunately, in theory practice and theory are the same. In practice they aren't.
    //
    // MMapping a file in Java does not give us a byte[] as you may expect but rather a ByteBuffer, and whilst on
    // the OpenJDK/Oracle JVM calls into the get() methods are compiled down to inlined native code on Android each
    // get() call is actually a full-blown JNI method under the hood, meaning it's unbelievably slow. The caches
    // below let us stay in the JIT-compiled Java world without expensive JNI transitions and make a 10x difference!
    protected LinkedHashMap<Integer, Sha256Hash> blockCache = new LinkedHashMap<Integer, Sha256Hash>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Sha256Hash> entry) {
            return size() > 2050;  // Slightly more than the difficulty transition period.
        }
    };
    BlockStore blockStore;

    public HashStore(BlockStore blockStore)
    {
        this.blockStore = blockStore;
    }


    public void put(StoredBlock block) throws BlockStoreException {
        lock.lock();
        try {
            blockCache.put(block.getHeight(), block.getHeader().getHash());
        } finally { lock.unlock(); }
    }

    @Nullable
    public Sha256Hash get(int blockHeight) throws BlockStoreException {

        lock.lock();
        try {
            Sha256Hash cacheHit = blockCache.get(blockHeight);
            if (cacheHit != null)
                return cacheHit;
            return null;
        } catch (ProtocolException e) {
            throw new RuntimeException(e);  // Cannot happen.
        } finally { lock.unlock(); }
    }


    public Sha256Hash getBlockHash(int blockHeight)
    {
        try {
            StoredBlock head = blockStore.getChainHead();
            if (head == null)
                return null;
            if (blockHeight == 0)
                blockHeight = head.getHeight();

            if (blockCache.containsKey(blockHeight)) {
                return blockCache.get(blockHeight);
            }

            StoredBlock cursor = head;

            if(head.getHeight() == 0 || head.getHeight()+1 < blockHeight)
                return null;

            int blocksAgo = 0;
            if(blockHeight > 0)
                blocksAgo = (head.getHeight() +1) - blockHeight;

            if(blocksAgo < 0)
                return null;

            int n = 0;
            for (; cursor.getHeight() > 0; )
            {
                if(n >= blocksAgo) {
                    Sha256Hash hash = cursor.getHeader().getHash();
                    blockCache.put(blockHeight, hash);
                    return hash;
                }
                n++;
                StoredBlock prev = cursor.getPrev(blockStore);
                if(prev == null)
                    break;
                cursor = prev;
            }
            while (cursor != null && cursor.getHeight() != (blockHeight-1)) {
                cursor = cursor.getPrev(blockStore);
            }

        } catch (BlockStoreException x) {
            return null;
        }
        return null;

    }
    public int getLowestHeight() {
        int min = -1;
        for(Integer i : blockCache.keySet()) {
            if(min == -1) {
                min = i;
                continue;
            }
            if(i < min)
                min = i;
        }
        return min;
    }
}
