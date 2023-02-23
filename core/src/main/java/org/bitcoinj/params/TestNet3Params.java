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

        // updated with Dash Core 0.17.0.3 seed list
        addrSeeds = new int[]{
                0x10a8302d,
                0x4faf4433,
                0x05dacd3c,
                0x939c6e8f,
                0xf9cb3eb2,
                0xf093bdce
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

        //LLMQ parameters
        addLLMQ(LLMQParameters.LLMQType.LLMQ_50_60);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_400_60);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_400_85);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_100_67);
        addLLMQ(LLMQParameters.LLMQType.LLMQ_60_75);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_100_67;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_60_75;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_50_60;

        BIP34Height = 76;   // 000008ebb1db2598e897d17275285767717c6acfeac4c73def49fbea1ddcbcb6
        BIP65Height = 2431; // 0000039cf01242c7f921dcb4806a5994bc003b48c1973ae0c89b67809c2bb2ab
        BIP66Height = 2075;

        coinType = 1;
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

    public static String[] MASTERNODES = {
            "35.91.179.0",
            "54.191.111.41",
            "34.212.226.87",
            "54.213.127.112",
            "54.191.58.35",
            "35.91.48.209",
            "18.236.181.174",
            "35.92.221.71",
            "34.219.95.101",
            "52.11.3.7",
            "54.201.159.221",
            "35.92.173.122",
            "35.160.56.59",
            "35.86.118.220",
            "35.161.227.35",
            "34.211.242.198",
            "34.222.85.12",
            "35.92.191.185",
            "34.219.116.233",
            "34.221.101.180",
            "35.167.67.238",
            "34.222.124.130",
            "34.221.27.254",
            "54.184.180.128",
            "54.213.66.83",
            "34.221.111.42",
            "18.246.24.120",
            "35.87.33.37",
            "54.202.136.162",
            "34.222.86.167",
            "54.201.255.251",
            "34.218.58.97",
            "54.212.132.76",
            "35.91.85.112",
            "35.90.34.163",
            "52.43.89.56",
            "52.24.13.132",
            "35.88.112.88",
            "34.221.146.243",
            "35.163.221.58",
            "34.222.63.208",
            "34.220.192.214",
            "52.39.66.234",
            "35.89.111.160",
            "54.190.26.8",
            "18.236.165.215",
            "54.201.230.38",
            "18.237.221.26",
            "35.87.181.253",
            "54.202.28.46",
            "35.89.37.191",
            "52.13.3.149",
            "34.219.201.245",
            "18.237.180.82",
            "35.89.76.145",
            "34.213.185.174",
            "34.219.94.173",
            "35.85.217.85",
            "54.188.236.87",
            "54.201.0.235",
            "52.38.160.165",
            "34.222.248.101",
            "18.237.26.129",
            "34.216.201.112",
            "52.37.71.171",
            "35.91.52.84",
            "54.188.76.44",
            "54.202.247.210",
            "54.189.31.212",
            "35.87.154.70",
            "52.43.68.213",
            "34.211.5.77",
            "34.211.228.209",
            "54.187.30.214",
            "34.219.12.229",
            "34.222.158.67",
            "54.202.255.193",
            "35.89.61.156",
            "54.212.39.231",
            "52.89.16.198",
            "35.89.73.193",
            "35.86.247.93",
            "35.90.214.63",
            "34.222.244.197",
            "54.212.230.59",
            "54.184.235.127",
            "35.89.35.171",
            "18.236.138.8",
            "35.93.113.214",
            "35.89.57.113",
            "18.237.71.213",
            "34.214.124.179",
            "35.91.51.25",
            "35.85.216.233",
            "34.212.154.65",
            "54.202.128.11",
            "34.216.66.180",
            "34.214.222.106",
            "52.39.5.16",
            "34.215.193.67",
            "34.213.185.242",
            "34.208.194.84",
            "35.93.160.103",
            "54.185.135.212",
            "35.90.229.223",
            "35.86.108.179",
            "35.92.240.108",
            "52.35.200.90",
            "35.92.37.22",
            "35.87.141.114",
            "34.221.146.99",
            "34.217.110.1",
            "52.12.214.237",
    };

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
