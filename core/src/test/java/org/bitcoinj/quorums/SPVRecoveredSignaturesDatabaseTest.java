/*
 * Copyright 2025 Dash Core Group
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

package org.bitcoinj.quorums;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSLazySignature;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class SPVRecoveredSignaturesDatabaseTest {

    private UnitTestParams params;
    private SPVRecoveredSignaturesDatabase database;
    private Context context;

    @Before
    public void setUp() {
        params = UnitTestParams.get();
        context = new Context(params);
        database = new SPVRecoveredSignaturesDatabase(context);
    }

    private RecoveredSignature createTestRecoveredSignature(int llmqType, String idHex, String msgHashHex, String quorumHashHex) {
        RecoveredSignature recSig = new RecoveredSignature();
        recSig.llmqType = llmqType;
        recSig.id = Sha256Hash.wrap(idHex);
        recSig.msgHash = Sha256Hash.wrap(msgHashHex);
        recSig.quorumHash = Sha256Hash.wrap(quorumHashHex);
        recSig.signature = new BLSLazySignature(params, new byte[96], 0); // empty signature for testing
        return recSig;
    }

    @Test
    public void testWriteAndRetrieveRecoveredSignature() {
        // Test data
        int llmqType = 1;
        String idHex = "1111111111111111111111111111111111111111111111111111111111111111";
        String msgHashHex = "2222222222222222222222222222222222222222222222222222222222222222";
        String quorumHashHex = "3333333333333333333333333333333333333333333333333333333333333333";

        RecoveredSignature recSig = createTestRecoveredSignature(llmqType, idHex, msgHashHex, quorumHashHex);

        // Write signature
        database.writeRecoveredSig(recSig);

        // Verify hasRecoveredSig
        assertTrue(database.hasRecoveredSig(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id, recSig.msgHash));

        // Verify hasRecoveredSigForId
        assertTrue(database.hasRecoveredSigForId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));

        // Verify getRecoveredSigById
        RecoveredSignature retrieved = database.getRecoveredSigById(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id);
        assertNotNull(retrieved);
        assertEquals(recSig.id, retrieved.id);
        assertEquals(recSig.msgHash, retrieved.msgHash);

        // Verify hasRecoveredSigForHash
        assertTrue(database.hasRecoveredSigForHash(recSig.getHash()));

        // Verify getRecoveredSigByHash
        Sha256Hash signHash = LLMQUtils.buildSignHash(recSig.llmqType, recSig.quorumHash, recSig.id, recSig.id);
        RecoveredSignature retrievedByHash = database.getRecoveredSigByHash(signHash);
        assertNotNull(retrievedByHash);
        assertEquals(recSig.id, retrievedByHash.id);
    }

    @Test
    public void testHasRecoveredSigReturnsFalseForNonExistent() {
        String idHex = "1111111111111111111111111111111111111111111111111111111111111111";
        String msgHashHex = "2222222222222222222222222222222222222222222222222222222222222222";
        
        Sha256Hash id = Sha256Hash.wrap(idHex);
        Sha256Hash msgHash = Sha256Hash.wrap(msgHashHex);

        assertFalse(database.hasRecoveredSig(LLMQParameters.LLMQType.LLMQ_50_60, id, msgHash));
        assertFalse(database.hasRecoveredSigForId(LLMQParameters.LLMQType.LLMQ_50_60, id));
        assertFalse(database.hasRecoveredSigForHash(Sha256Hash.wrap("4444444444444444444444444444444444444444444444444444444444444444")));
    }

    @Test
    public void testVotingFunctionality() {
        String idHex = "1111111111111111111111111111111111111111111111111111111111111111";
        String msgHashHex = "2222222222222222222222222222222222222222222222222222222222222222";
        
        Sha256Hash id = Sha256Hash.wrap(idHex);
        Sha256Hash msgHash = Sha256Hash.wrap(msgHashHex);
        LLMQParameters.LLMQType llmq = LLMQParameters.LLMQType.LLMQ_50_60;

        // Initially, no vote
        assertFalse(database.hasVotedOnId(llmq, id));
        assertNull(database.getVoteForId(llmq, id));

        // Write vote
        database.writeVoteForId(llmq, id, msgHash);

        // Verify vote exists
        assertTrue(database.hasVotedOnId(llmq, id));
        assertEquals(msgHash, database.getVoteForId(llmq, id));
    }

    @Test
    public void testCleanupOldRecoveredSignatures() {
        // Create test signature
        int llmqType = 1;
        String idHex = "1111111111111111111111111111111111111111111111111111111111111111";
        String msgHashHex = "2222222222222222222222222222222222222222222222222222222222222222";
        String quorumHashHex = "3333333333333333333333333333333333333333333333333333333333333333";

        RecoveredSignature recSig = createTestRecoveredSignature(llmqType, idHex, msgHashHex, quorumHashHex);
        database.writeRecoveredSig(recSig);

        // Verify signature exists
        assertTrue(database.hasRecoveredSigForId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));

        // Cleanup with future timestamp (should remove all)
        long futureTime = Utils.currentTimeMillis() + 10000;
        database.cleanupOldRecoveredSignatures(futureTime);

        // Verify signature was removed
        assertFalse(database.hasRecoveredSigForId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));
        assertFalse(database.hasRecoveredSigForHash(recSig.getHash()));
    }

    @Test
    public void testClearDatabase() {
        // Add test data
        int llmqType = 1;
        String idHex = "1111111111111111111111111111111111111111111111111111111111111111";
        String msgHashHex = "2222222222222222222222222222222222222222222222222222222222222222";
        String quorumHashHex = "3333333333333333333333333333333333333333333333333333333333333333";

        RecoveredSignature recSig = createTestRecoveredSignature(llmqType, idHex, msgHashHex, quorumHashHex);
        database.writeRecoveredSig(recSig);
        database.writeVoteForId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id, recSig.msgHash);

        // Verify data exists
        assertTrue(database.hasRecoveredSigForId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));
        assertTrue(database.hasVotedOnId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));

        // Clear database
        database.clear();

        // Verify data was cleared
        assertFalse(database.hasRecoveredSigForId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));
        assertFalse(database.hasVotedOnId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        final int numThreads = 10;
        final int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        // Test concurrent read/write operations
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String idHex = String.format("%064d", threadId * operationsPerThread + j);
                        String msgHashHex = String.format("%064d", (threadId * operationsPerThread + j) * 2);
                        String quorumHashHex = String.format("%064d", (threadId * operationsPerThread + j) * 3);

                        RecoveredSignature recSig = createTestRecoveredSignature(1, idHex, msgHashHex, quorumHashHex);
                        
                        // Write signature
                        database.writeRecoveredSig(recSig);
                        
                        // Read signature
                        assertTrue(database.hasRecoveredSigForId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));
                        
                        // Write vote
                        database.writeVoteForId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id, recSig.msgHash);
                        
                        // Read vote
                        assertTrue(database.hasVotedOnId(LLMQParameters.LLMQType.LLMQ_50_60, recSig.id));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all threads to complete
        assertTrue("Concurrent test failed to complete within timeout", 
                   latch.await(30, TimeUnit.SECONDS));
        
        executor.shutdown();
    }

    @Test
    public void testMultipleSignaturesForSameId() {
        int llmqType = 1;
        String idHex = "1111111111111111111111111111111111111111111111111111111111111111";
        String msgHash1Hex = "2222222222222222222222222222222222222222222222222222222222222222";
        String msgHash2Hex = "3333333333333333333333333333333333333333333333333333333333333333";
        String quorumHashHex = "4444444444444444444444444444444444444444444444444444444444444444";

        Sha256Hash id = Sha256Hash.wrap(idHex);
        LLMQParameters.LLMQType llmq = LLMQParameters.LLMQType.LLMQ_50_60;

        // Write first signature
        RecoveredSignature recSig1 = createTestRecoveredSignature(llmqType, idHex, msgHash1Hex, quorumHashHex);
        database.writeRecoveredSig(recSig1);

        // Write second signature with same ID but different msgHash
        RecoveredSignature recSig2 = createTestRecoveredSignature(llmqType, idHex, msgHash2Hex, quorumHashHex);
        database.writeRecoveredSig(recSig2);

        // Should have signature for ID (last written)
        assertTrue(database.hasRecoveredSigForId(llmq, id));
        
        // Retrieved signature should be the last one written
        RecoveredSignature retrieved = database.getRecoveredSigById(llmq, id);
        assertNotNull(retrieved);
        assertEquals(id, retrieved.id);
        assertEquals(recSig2.msgHash, retrieved.msgHash); // Should be the second one
    }

    @Test
    public void testCreateEmpty() {
        SPVRecoveredSignaturesDatabase emptyDb = (SPVRecoveredSignaturesDatabase) database.createEmpty();
        assertNotNull(emptyDb);
        assertNotSame(database, emptyDb);
        
        // Empty database should return false for all queries
        String idHex = "1111111111111111111111111111111111111111111111111111111111111111";
        String msgHashHex = "2222222222222222222222222222222222222222222222222222222222222222";
        Sha256Hash id = Sha256Hash.wrap(idHex);
        Sha256Hash msgHash = Sha256Hash.wrap(msgHashHex);
        
        assertFalse(emptyDb.hasRecoveredSig(LLMQParameters.LLMQType.LLMQ_50_60, id, msgHash));
        assertFalse(emptyDb.hasRecoveredSigForId(LLMQParameters.LLMQType.LLMQ_50_60, id));
        assertFalse(emptyDb.hasVotedOnId(LLMQParameters.LLMQType.LLMQ_50_60, id));
    }
}