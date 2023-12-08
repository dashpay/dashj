/*
 * Copyright 2013 Google Inc.
 * Copyright 2018 Andreas Schildbach
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

package org.bitcoinj.params;

import org.bitcoinj.core.Block;
import org.bitcoinj.quorums.LLMQParameters;

import java.math.BigInteger;
import java.util.HashMap;

import static com.google.common.base.Preconditions.checkState;

/**
 * Network parameters for the regression test mode of bitcoind in which all blocks are trivially solvable.
 */
public class RegTestParams extends AbstractBitcoinNetParams {
    private static final BigInteger MAX_TARGET = new BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);

    public RegTestParams() {
        super();
        packetMagic = 0xfcc1b7dcL;
        addressHeader = 140;
        p2shHeader = 19;
        targetTimespan = TARGET_TIMESPAN;
        dumpedPrivateKeyHeader = 128 + 140;
        genesisBlock.setTime(1417713337L);
        genesisBlock.setDifficultyTarget(0x207fffff);
        genesisBlock.setNonce(1096447);
        spendableCoinbaseDepth = 100;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000008ca1832a4baf228eb1553c03d3a2c8e02399550dd6ea8d65cec3ef23d2e"));
        dnsSeeds = null;
        addrSeeds = null;
        bip32HeaderP2PKHpub = 0x043587cf; // The 4 byte header that serializes in base58 to "tpub".
        bip32HeaderP2PKHpriv = 0x04358394; // The 4 byte header that serializes in base58 to "tprv"
        bip32HeaderP2WPKHpub = 0x045f1cf6; // The 4 byte header that serializes in base58 to "vpub".
        bip32HeaderP2WPKHpriv = 0x045f18bc; // The 4 byte header that serializes in base58 to "vprv"
        dip14HeaderP2PKHpub = 0x02FDA7E8; // The 4 byte header that serializes in base58 to "dptp".
        dip14HeaderP2PKHpriv = 0x02FDA7FD; // The 4 byte header that serializes in base58 to "dpts"

        // Difficulty adjustments are disabled for regtest.
        // By setting the block interval for difficulty adjustments to Integer.MAX_VALUE we make sure difficulty never
        // changes.
        interval = Integer.MAX_VALUE;
        maxTarget = MAX_TARGET;
        subsidyDecreaseBlockCount = 150;
        port = 19899;
        id = ID_REGTEST;

        majorityEnforceBlockUpgrade = TestNet3Params.TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TestNet3Params.TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MainNetParams.MAINNET_MAJORITY_WINDOW;

        DIP0001BlockHeight = 15000;
        strSporkAddress = "yjPtiKh2uwk3bDutTEA2q9mCtXyiZRWn55";
        minSporkKeys = 1;

        fulfilledRequestExpireTime = 5*60;
        masternodeMinimumConfirmations = 1;
        superblockStartBlock = 1500;
        superblockCycle = 10;
        nGovernanceMinQuorum = 1;
        nGovernanceFilterElements = 100;

        powDGWHeight = 34140;
        powKGWHeight = 15200;
        powAllowMinimumDifficulty = true;
        powNoRetargeting = true;

        instantSendConfirmationsRequired = 2;

        budgetPaymentsStartBlock = 1000;
        budgetPaymentsCycleBlocks = 50;
        budgetPaymentsWindowBlocks = 10;

        //LLMQ parameters
        addLLMQ(LLMQParameters.LLMQType.LLMQ_TEST);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_TEST_INSTANTSEND);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_TEST_V17);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_TEST_DIP0024);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_TEST_PLATFORM);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_TEST;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_TEST_INSTANTSEND;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_TEST_DIP0024;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_TEST_PLATFORM;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_TEST;
        llmqTypeAssetLocks = LLMQParameters.LLMQType.LLMQ_TEST;

        BIP34Height = 100000000;
        BIP65Height = 1365;
        BIP66Height = 1251;

        coinType = 1;
    }

    @Override
    public boolean allowEmptyPeerChain() {
        return true;
    }

    private static Block genesis;

    @Override
    public Block getGenesisBlock() {
        synchronized (RegTestParams.class) {
            if (genesis == null) {
                genesis = super.getGenesisBlock();
                genesis.setNonce(2);
                genesis.setDifficultyTarget(0x207fFFFFL);
                genesis.setTime(1296688602L);
            }
            return genesis;
        }
    }

    private static RegTestParams instance;
    public static synchronized RegTestParams get() {
        if (instance == null) {
            instance = new RegTestParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_REGTEST;
    }
}
