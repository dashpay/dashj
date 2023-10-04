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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import com.google.common.collect.Lists;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;
import org.junit.Before;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
public class BlockQueueTest {

    private BlockQueue blockQueue;
    private StoredBlock testBlock;

    @Before
    public void setup() {
        blockQueue = new BlockQueue();

        // Assuming a hypothetical `StoredBlock` creation method.
        // Actual creation may vary based on how `StoredBlock` and `Block` objects are instantiated in `DashJ`
        testBlock = createTestBlock();
    }

    @Test
    public void testAdd() {
        assertTrue(blockQueue.add(testBlock));
        assertEquals(1, blockQueue.size());
    }

    @Test
    public void testPop() {
        blockQueue.add(testBlock);
        StoredBlock poppedBlock = blockQueue.pop();
        assertEquals(testBlock, poppedBlock);
        assertEquals(0, blockQueue.size());
    }

    @Test
    public void testPeek() {
        blockQueue.add(testBlock);
        StoredBlock peekedBlock = blockQueue.peek();
        assertEquals(testBlock, peekedBlock);
        assertEquals(1, blockQueue.size()); // Peek shouldn't remove the block
    }

    @Test
    public void testContains() {
        blockQueue.add(testBlock);
        assertTrue(blockQueue.contains(testBlock));
    }

    @Test
    public void testClear() {
        blockQueue.add(testBlock);
        blockQueue.clear();
        assertEquals(0, blockQueue.size());
    }

    @Test
    public void testIterator() {
        blockQueue.add(testBlock);
        Iterator<StoredBlock> iterator = blockQueue.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(testBlock, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testAddAll() {
        List<StoredBlock> blocks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            blocks.add(createTestBlock());
        }
        blockQueue.addAll(blocks);
        assertEquals(5, blockQueue.size());
    }

    private StoredBlock createTestBlock() {
        // Assuming a hypothetical `StoredBlock` creation method.
        // Actual creation may vary based on `StoredBlock` and `Block` instantiation in `DashJ`.
        // For this example, we're using a mock method to represent the creation.
        return new StoredBlock(
                new Block(UnitTestParams.get(), 3, Sha256Hash.ZERO_HASH, Sha256Hash.twiceOf(new byte[0]), 0L, 1L, 2L, Lists.newArrayList()),
                BigInteger.TEN,
                1
                );
    }
}
