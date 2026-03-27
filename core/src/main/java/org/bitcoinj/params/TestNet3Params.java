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

import org.bitcoinj.core.*;
import org.bitcoinj.quorums.LLMQParameters;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the testnet, a separate public instance of Dash that has relaxed rules suitable for development
 * and testing of applications and new Dash versions.
 */
public class TestNet3Params extends AbstractBitcoinNetParams {

    public static final int TESTNET_MAJORITY_WINDOW = 100;
    public static final int TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED = 75;
    public static final int TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 51;

    public TestNet3Params() {
        super();
        id = ID_TESTNET;

        packetMagic = 0xcee2caffL;
        interval = INTERVAL;
        targetTimespan = TARGET_TIMESPAN;

        // 00000fffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
        maxTarget = Utils.decodeCompactBits(0x1e0fffffL);
        port = 19999;
        addressHeader = 140;
        p2shHeader = 19;
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1390666206L);
        genesisBlock.setDifficultyTarget(0x1e0ffff0L);
        genesisBlock.setNonce(3861367235L);
        spendableCoinbaseDepth = 100;
        subsidyDecreaseBlockCount = 210240;
        String genesisHash = genesisBlock.getHashAsString();

        checkState(genesisHash.equals("00000bafbc94add76cb75e2ec92894837288a481e5c005f6563d91623bf8bc2c"));
        alertSigningKey = HEX.decode("04517d8a699cb43d3938d7b24faaff7cda448ca4ea267723ba614784de661949bf632d6304316b244646dea079735b9a6fc4af804efb4752075b9fe2245e14e412");

        dnsSeeds = new String[] {
                "testnet-seed.dashdot.io" // this seeder is offline
        };

        bip32HeaderP2PKHpub = 0x043587cf; // The 4 byte header that serializes in base58 to "tpub".
        bip32HeaderP2PKHpriv = 0x04358394; // The 4 byte header that serializes in base58 to "tprv"
        dip14HeaderP2PKHpub = 0x0eed270b; // The 4 byte header that serializes in base58 to "dptp".
        dip14HeaderP2PKHpriv = 0x0eed2774; // The 4 byte header that serializes in base58 to "dpts"


        checkpoints.put(261, Sha256Hash.wrap("00000c26026d0815a7e2ce4fa270775f61403c040647ff2c3091f99e894a4618"));
        checkpoints.put(1999, Sha256Hash.wrap("00000052e538d27fa53693efe6fb6892a0c1d26c0235f599171c48a3cce553b1"));
        checkpoints.put(2999, Sha256Hash.wrap("0000024bc3f4f4cb30d29827c13d921ad77d2c6072e586c7f60d83c2722cdcc5"));
        checkpoints.put(96090, Sha256Hash.wrap("00000000033df4b94d17ab43e999caaf6c4735095cc77703685da81254d09bba"));
        checkpoints.put(200000, Sha256Hash.wrap("000000001015eb5ef86a8fe2b3074d947bc972c5befe32b28dd5ce915dc0d029"));
        checkpoints.put(395750, Sha256Hash.wrap("000008b78b6aef3fd05ab78db8b76c02163e885305545144420cb08704dce538"));
        checkpoints.put(470000, Sha256Hash.wrap("0000009303aeadf8cf3812f5c869691dbd4cb118ad20e9bf553be434bafe6a52"));
        checkpoints.put(794950, Sha256Hash.wrap("000001860e4c7248a9c5cc3bc7106041750560dc5cd9b3a2641b49494bcff5f2"));
        checkpoints.put(808000, Sha256Hash.wrap("00000104cb60a2b5e00a8a4259582756e5bf0dca201c0993c63f0e54971ea91a"));
        checkpoints.put(850100, Sha256Hash.wrap("000004728b8ff2a16b9d4eebb0fd61eeffadc9c7fe4b0ec0b5a739869401ab5b"));
        checkpoints.put(899760, Sha256Hash.wrap("000007b169cbf75796ee1147b24f6cdf627c189490d7472187408e0902413a68"));

        // updated with Dash Core 21.0.0 seed list
        addrSeeds = new int[] {
                0xaef43c3e,
                0xd85ed536,
                0x8992bf36,
                0x5945bc36,
                0x309a5934,
                0x7412a223,
                0x078f5c23,
                0xdac55b23,
                0x59865b23,
                0x1e86dc22,
                0xc31ad222
        };

        bip32HeaderP2PKHpub = 0x043587cf;
        bip32HeaderP2PKHpriv = 0x04358394;

        strSporkAddress = "yjPtiKh2uwk3bDutTEA2q9mCtXyiZRWn55";
        minSporkKeys = 1;
        budgetPaymentsStartBlock = 4100;
        budgetPaymentsCycleBlocks = 50;
        budgetPaymentsWindowBlocks = 10;

        majorityEnforceBlockUpgrade = TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TESTNET_MAJORITY_WINDOW;

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
        DIP0024BlockHeight = 769700 + 4 * 288;
        v19BlockHeight = 850100;
        v20BlockHeight = 905100;

        //LLMQ parameters
        addLLMQ(LLMQParameters.LLMQType.LLMQ_50_60);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_400_60);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_400_85);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_100_67);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_60_75);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_25_67);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_25_67;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_60_75;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqTypeAssetLocks = LLMQParameters.LLMQType.LLMQ_50_60;

        BIP34Height = 76;   // 000008ebb1db2598e897d17275285767717c6acfeac4c73def49fbea1ddcbcb6
        BIP65Height = 2431; // 0000039cf01242c7f921dcb4806a5994bc003b48c1973ae0c89b67809c2bb2ab
        BIP66Height = 2075;

        coinType = 1;
        assumeValidQuorums.add(Sha256Hash.wrap("0000000007697fd69a799bfa26576a177e817bc0e45b9fcfbf48b362b05aeff2"));
        assumeValidQuorums.add(Sha256Hash.wrap("000000339cd97d45ee18cd0cba0fd590fb9c64e127d3c30885e5b7376af94fdf"));
        assumeValidQuorums.add(Sha256Hash.wrap("0000007833f1b154218be64712cabe0e7c695867cc0c452311b2d786e14622fa"));

        // coinjoin
        poolMinParticipants = 2;
        poolMaxParticipants = 20;
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

    public static String[] MASTERNODES = {
        "68.67.122.30",
        "68.67.122.31",
        "68.67.122.32",
        "68.67.122.33",
        "68.67.122.34",
        "68.67.122.35",
        "68.67.122.36",
        "68.67.122.37",
        "68.67.122.38",
        "68.67.122.39",
        "68.67.122.40",
        "68.67.122.41",
        "68.67.122.42",
        "68.67.122.43",
        "68.67.122.44",
        "68.67.122.45",
        "68.67.122.46",
        "68.67.122.47",
        "68.67.122.48",
        "68.67.122.49",
        "68.67.122.50",
        "68.67.122.51",
        "68.67.122.52",
        "68.67.122.53",
        "68.67.122.54",
        "68.67.122.55",
        "68.67.122.56",
        "68.67.122.57",
        "68.67.122.58",
        "68.67.122.59",
        "68.67.122.60",
        "68.67.122.61",
        "68.67.122.62",
        "68.67.122.63",
        "68.67.122.64",
        "68.67.122.65",
        "68.67.122.66",
        "68.67.122.67",
        "68.67.122.68",
        "68.67.122.69",
        "68.67.122.70",
        "68.67.122.71",
        "68.67.122.72",
        "68.67.122.73",
        "68.67.122.74",
        "68.67.122.75",
        "68.67.122.76",
        "68.67.122.77",
        "68.67.122.78",
        "68.67.122.79",
        "68.67.122.80",
        "68.67.122.81",
        "68.67.122.82"
    };

    public String [] HP_MASTERNODES = {
        "34.214.48.68",
        "35.166.18.166",
        "68.67.122.3",
        "52.42.202.128",
        "52.12.176.90",
        "44.233.44.95",
        "35.167.145.149",
        "52.34.144.50",
        "44.240.98.102",
        "54.201.32.131",
        "52.10.229.11",
        "52.13.132.146",
        "44.228.242.181",
        "35.82.197.197",
        "52.40.219.41",
        "44.239.39.153",
        "54.149.33.167",
        "68.67.122.18",
        "52.33.28.47",
        "52.43.86.231",
        "52.43.13.92",
        "68.67.122.22",
        "52.89.154.48",
        "52.24.124.162",
        "44.227.137.77",
        "35.85.21.179",
        "54.187.14.232",
        "54.68.235.201",
        "68.67.122.29"
    };

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }

    @Override
    public String[] getDefaultHPMasternodeList() {
        return HP_MASTERNODES;
    }
}
