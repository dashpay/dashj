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
 *
 * This file was generated by SWIG (http://www.swig.org) and modified.
 * Version 3.0.12
 */

package org.bitcoinj.core;

import org.bitcoinj.manager.DashSystem;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.quorums.ChainLocksHandler;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

import static org.junit.Assert.fail;

/**
 * We don't do any wallet tests here, we leave that to {@link ChainSplitTest}
 */

public abstract class ChainLockBlockChainTest {
    @org.junit.Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final Logger log = LoggerFactory.getLogger(ChainLockBlockChainTest.class);

    protected static final NetworkParameters PARAMS = new UnitTestParams() {
        @Override public int getInterval() {
            return 10000;
        }
    };
    private static final NetworkParameters MAINNET = MainNetParams.get();

    protected BlockChain chain;
    protected BlockStore store;
    protected DashSystem system;

    @Before
    public void setUp() throws Exception {
        BriefLogFormatter.init();
        Context context = new Context(PARAMS, 100, Coin.ZERO, false);
        Context.propagate(context);
        system = new DashSystem(Context.get());
        system.initDash(false, true);
    }

    @After
    public void tearDown() {
        system.remove();
    }

    public abstract BlockStore createStore(NetworkParameters params, int blockCount) throws BlockStoreException;

    public abstract void resetStore(BlockStore store) throws BlockStoreException;

    /*@Test
    public void testDefaultGeneratedChain() throws Exception {
        ChainLockBlockTestGenerator generator = new ChainLockBlockTestGenerator(PARAMS);
        RuleList blockList = generator.getBlocksToTest(false, false, null);
        testGeneratedChain(blockList);
    }

    @Test
    public void testSimpleGeneratedChain() throws Exception {
        ChainLockBlockTestGenerator generator = new ChainLockBlockTestGenerator(PARAMS);
        RuleList blockList = generator.getSimpleChainBlocksToTest(false, false, null);
        testGeneratedChain(blockList);
    }*/

    @Test
    public void testGeneratedChain() throws Exception {
        // Tests various test cases from FullBlockTestGenerator

        ChainLockBlockTestGenerator generator = new ChainLockBlockTestGenerator(PARAMS);
        RuleList blockList = generator.getBlocksToTest(false, false, null);

        store = createStore(PARAMS, blockList.maximumReorgBlockCount);
        chain = new BlockChain(PARAMS, store);

        Context context = Context.getOrCreate(PARAMS);

        system.setPeerGroupAndBlockChain(null, chain, null);
        system.masternodeSync.syncFlags = EnumSet.noneOf(MasternodeSync.SYNC_FLAGS.class);

        ChainLocksHandler chainLocksHandler = system.chainLockHandler;

        for (Rule rule : blockList.list) {
            if (rule instanceof FullBlockTestGenerator.BlockAndValidity) {

                FullBlockTestGenerator.BlockAndValidity block = (FullBlockTestGenerator.BlockAndValidity) rule;
                log.info("Testing rule " + block.ruleName + " with block hash " + block.block.getHash());
                boolean threw = false;
                try {
                    if (chain.add(block.block) != block.connects) {
                        log.error("Block didn't match connects flag on block " + block.ruleName);
                        fail();
                    }
                } catch (VerificationException e) {
                    threw = true;
                    if (!block.throwsException) {
                        log.error("Block didn't match throws flag on block " + block.ruleName);
                        throw e;
                    }
                    if (block.connects) {
                        log.error("Block didn't match connects flag on block " + block.ruleName);
                        fail();
                    }
                }
                if (!threw && block.throwsException) {
                    log.error("Block didn't match throws flag on block " + block.ruleName);
                    fail();
                }
                if (!chain.getChainHead().getHeader().getHash().equals(block.hashChainTipAfterBlock)) {
                    log.error("New block head didn't match the correct value after block " + block.ruleName);
                    fail();
                }
                if (chain.getChainHead().getHeight() != block.heightAfterBlock) {
                    log.error("New block head didn't match the correct height after block " + block.ruleName);
                    fail();
                }
            } else if (rule instanceof ChainLockRule) {
                ChainLockRule chainLockRule = (ChainLockRule)rule;
                log.info("Testing rule " + chainLockRule.ruleName + " with block hash " + chainLockRule.block.getHash());
                chainLocksHandler.setBestChainLockBlockMock(chainLockRule.block, chainLockRule.height);

                Sha256Hash chainHeadHash = chain.chainHead.getHeader().getHash();
                //how to trigger
                if (chainLockRule.hashChainTipAfterLock != null) {
                    if (!chainHeadHash.equals(chainLockRule.hashChainTipAfterLock)) {
                        log.error("New block head didn't match the specified hash after chainlock: " + chainLockRule.ruleName);
                        fail();
                    }
                } else if (!chainHeadHash.equals(chainLockRule.block.getHash())) {
                    log.error("New block head didn't match the correct hash after chainlock: " + chainLockRule.ruleName);
                    fail();
                }
            }
        }
        try {
            store.close();
        } catch (Exception e) {}
    }
}
