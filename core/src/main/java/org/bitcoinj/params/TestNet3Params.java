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

        packetMagic = 0xcee2caff;
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
        "34.216.69.175",
        "34.218.75.96",
        "18.237.220.27",
        "54.191.38.234",
        "35.93.95.157",
        "35.166.240.246",
        "35.91.186.151",
        "35.161.123.225",
        "54.212.169.115",
        "54.187.168.12",
        "18.236.191.199",
        "18.237.165.224",
        "52.36.42.1",
        "34.220.32.216",
        "34.222.238.250",
        "35.93.34.215",
        "34.221.10.165",
        "54.203.241.195",
        "35.92.138.231",
        "52.38.169.156",
        "54.187.21.108",
        "34.219.48.219",
        "34.217.38.221",
        "35.92.157.107",
        "18.236.82.230",
        "54.184.225.22",
        "18.237.144.120",
        "18.237.254.214",
        "35.88.29.196",
        "35.89.126.245",
        "34.217.148.69",
        "35.89.193.46",
        "35.89.119.37",
        "54.186.161.236",
        "52.13.63.7",
        "18.236.233.225",
        "54.191.138.72",
        "54.191.59.75",
        "18.237.160.84",
        "54.218.236.193",
        "54.148.178.140",
        "35.161.168.136",
        "35.161.33.22",
        "35.160.130.44",
        "34.212.104.247",
        "52.13.29.83",
        "54.149.39.172",
        "35.89.93.255",
        "35.85.46.137",
        "54.188.154.252",
        "54.218.11.172",
        "54.186.47.235",
        "54.244.145.48",
        "18.236.232.153",
        "34.220.226.135",
        "34.220.30.59",
        "18.237.160.97",
        "18.236.146.99",
        "35.88.169.18",
        "35.164.17.198",
        "54.218.80.158",
        "34.220.102.107",
        "35.165.60.113",
        "34.215.234.63",
        "35.89.146.226",
        "35.89.86.26",
        "35.88.160.231",
        "54.245.8.155",
        "52.36.221.94",
        "52.35.46.188",
        "34.221.153.67",
        "54.212.153.15",
        "35.85.55.192",
        "35.89.105.90",
        "52.24.22.52",
        "54.212.33.96",
        "52.10.19.238",
        "35.160.190.165",
        "34.219.3.61",
        "54.187.32.187",
        "35.162.221.77",
        "35.91.64.183",
        "18.236.189.15",
        "35.91.145.195",
        "54.186.251.30",
        "54.245.9.46",
        "35.91.48.128",
        "54.244.75.85",
        "34.221.159.53",
        "35.89.88.166",
        "54.185.249.165",
        "54.245.9.209",
        "34.222.167.162",
        "54.191.88.232",
        "54.191.111.112",
        "18.237.146.37",
        "35.89.148.27",
        "35.91.8.48",
        "18.236.208.138",
        "54.213.60.60",
        "35.92.151.94",
        "18.237.241.37",
        "54.202.209.229",
        "34.221.9.70",
        "35.89.38.62",
        "54.218.81.193",
        "34.221.158.21",
        "35.88.97.57",
        "54.68.247.40",
        "35.88.168.176",
        "35.89.202.171",
        "34.220.106.205",
        "34.210.86.23",
    };

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
