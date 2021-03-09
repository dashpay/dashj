/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

import static org.bitcoinj.core.Utils.HEX;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.bitcoinj.core.*;
import org.bitcoinj.quorums.LLMQParameters;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the testnet, a separate public instance of Dash that has relaxed rules suitable for development
 * and testing of applications and new Dash versions.
 */
public class TestNet3Params extends AbstractBitcoinNetParams {

    public static final int TESTNET_MAJORITY_DIP0001_WINDOW = 4032;
    public static final int TESTNET_MAJORITY_DIP0001_THRESHOLD = 3226;

    public static final int TESTNET_MAJORITY_WINDOW = 100;
    public static final int TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED = 75;
    public static final int TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 51;

    public TestNet3Params() {
        super();
        id = ID_TESTNET;

        // Genesis hash is

        packetMagic = CoinDefinition.testnetPacketMagic;
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;

        maxTarget = CoinDefinition.proofOfWorkLimit;//Utils.decodeCompactBits(0x1d00ffffL);
        port = CoinDefinition.TestPort;
        addressHeader = CoinDefinition.testnetAddressHeader;
        p2shHeader = CoinDefinition.testnetp2shHeader;
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(CoinDefinition.testnetGenesisBlockTime);
        genesisBlock.setDifficultyTarget(CoinDefinition.testnetGenesisBlockDifficultyTarget);
        genesisBlock.setNonce(CoinDefinition.testnetGenesisBlockNonce);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = CoinDefinition.subsidyDecreaseBlockCount;
        String genesisHash = genesisBlock.getHashAsString();

        if(CoinDefinition.supportsTestNet)
            checkState(genesisHash.equals(CoinDefinition.testnetGenesisHash));
        alertSigningKey = HEX.decode(CoinDefinition.TESTNET_SATOSHI_KEY);

        dnsSeeds = new String[] {
                "testnet-seed.dashdot.io"
        };

        bip32HeaderP2PKHpub = 0x043587cf; // The 4 byte header that serializes in base58 to "tpub".
        bip32HeaderP2PKHpriv = 0x04358394; // The 4 byte header that serializes in base58 to "tprv"

        checkpoints.put(    261, Sha256Hash.wrap("00000c26026d0815a7e2ce4fa270775f61403c040647ff2c3091f99e894a4618"));
        checkpoints.put(   1999, Sha256Hash.wrap("00000052e538d27fa53693efe6fb6892a0c1d26c0235f599171c48a3cce553b1"));
        checkpoints.put(   2999, Sha256Hash.wrap("0000024bc3f4f4cb30d29827c13d921ad77d2c6072e586c7f60d83c2722cdcc5"));

        addrSeeds = new int[]{
                0xaa34ca12,
                0xaa34ca12,
                0xaa34ca12,
                0xaa34ca12,
                0xaa34ca12,
                0xaa34ca12,
                0x140fff22,
                0x140fff22,
                0x140fff22,
                0x140fff22,
                0x140fff22,
                0x140fff22,
                0x35d03234,
                0x35d03234,
                0x35d03234,
                0x35d03234,
                0x35d03234,
                0x35d03234,
                0x55ee213f,
                0x55ee213f,
                0x55ee213f,
                0x55ee213f,
                0x55ee213f,
                0x55ee213f,
                0x10ebef91,
                0x11ebef91,
                0x12ebef91,
                0x13ebef91,
                0x14ebef91,
                0x15ebef91,
                0x16ebef91,
                0x17ebef91,
                0x18ebef91,
                0x19ebef91,
                0xf9cb3eb2
        };
        bip32HeaderP2PKHpub = 0x043587cf;
        bip32HeaderP2PKHpriv = 0x04358394;

        strSporkAddress = "yjPtiKh2uwk3bDutTEA2q9mCtXyiZRWn55";
        budgetPaymentsStartBlock = 4100;
        budgetPaymentsCycleBlocks = 50;
        budgetPaymentsWindowBlocks = 10;

        majorityEnforceBlockUpgrade = TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TESTNET_MAJORITY_WINDOW;

        DIP0001Window = TESTNET_MAJORITY_DIP0001_WINDOW;
        DIP0001Upgrade = TESTNET_MAJORITY_DIP0001_THRESHOLD;
        DIP0001BlockHeight = 4400;

        fulfilledRequestExpireTime = 5 * 60;
        masternodeMinimumConfirmations = 1;
        superblockStartBlock = 4200;
        superblockCycle = 24;
        nGovernanceMinQuorum = 1;
        nGovernanceFilterElements = 500;

        powDGWHeight = 4002;
        powKGWHeight = 4002;
        powAllowMinimumDifficulty = true;
        powNoRetargeting = false;

        instantSendConfirmationsRequired = 2;
        instantSendKeepLock = 6;

        DIP0003BlockHeight = 7000;
        deterministicMasternodesEnabledHeight = 7300;
        deterministicMasternodesEnabled = true;

        DIP0008BlockHeight = 78800;

        //LLMQ parameters
        llmqs = new HashMap<LLMQParameters.LLMQType, LLMQParameters>(3);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_50_60, LLMQParameters.llmq50_60);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_400_60, LLMQParameters.llmq400_60);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_400_85, LLMQParameters.llmq400_85);
        llmqs.put(LLMQParameters.LLMQType.LLMQ_100_67, LLMQParameters.llmq100_67);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_100_67;

        BIP65Height = 2431; // 0000039cf01242c7f921dcb4806a5994bc003b48c1973ae0c89b67809c2bb2ab

        coinType = 1;
        assumeValidQuorums = new ArrayList<>();
        assumeValidQuorums.add(Sha256Hash.wrap("0000000007697fd69a799bfa26576a177e817bc0e45b9fcfbf48b362b05aeff2"));
        assumeValidQuorums.add(Sha256Hash.wrap("000000339cd97d45ee18cd0cba0fd590fb9c64e127d3c30885e5b7376af94fdf"));
        assumeValidQuorums.add(Sha256Hash.wrap("0000007833f1b154218be64712cabe0e7c695867cc0c452311b2d786e14622fa"));
     }

    private static TestNet3Params instance;

    public static synchronized TestNet3Params get() {
        if (instance == null) {
            instance = new TestNet3Params();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_TESTNET;
    }
}
