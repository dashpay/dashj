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
        DIP0024BlockHeight = Integer.MAX_VALUE;

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
        "34.215.201.169",
        "34.213.226.143",
        "35.90.175.105",
        "54.212.56.164",
        "34.220.85.19",
        "34.222.76.26",
        "54.184.106.44",
        "54.185.174.141",
        "35.90.236.1",
        "18.237.79.212",
        "54.202.25.226",
        "35.167.3.35",
        "34.208.249.51",
        "52.26.78.151",
        "34.221.77.65",
        "54.202.62.244",
        "35.165.175.185",
        "52.38.43.254",
        "35.89.135.84",
        "54.202.179.253",
        "34.219.104.40",
        "52.13.19.26",
        "54.213.45.110",
        "35.90.129.145",
        "34.216.128.111",
        "35.90.96.31",
        "35.90.165.61",
        "34.222.138.111",
        "34.216.130.211",
        "35.165.59.241",
        "34.220.227.165",
        "52.32.232.156",
        "34.212.226.44",
        "35.160.140.37",
        "52.11.133.244",
        "54.201.189.185",
        "34.216.200.22",
        "34.220.144.226",
        "34.216.195.19",
        "54.185.1.69",
        "35.166.29.155",
        "18.237.147.160",
        "34.220.228.214",
        "54.212.75.8",
        "35.163.152.74",
        "18.236.78.191",
        "54.148.106.179",
        "34.211.46.222",
        "35.160.13.25",
        "34.220.17.107",
        "34.212.65.137",
        "54.185.249.172",
        "54.70.65.199",
        "54.69.210.42",
        "18.236.160.247",
        "54.245.197.173",
        "54.187.11.213",
        "54.218.70.46",
        "35.165.207.13",
        "34.211.49.3",
        "34.219.36.94",
        "34.222.127.158",
        "34.222.242.228",
        "52.26.220.40",
        "52.36.244.225",
        "34.222.225.76",
        "18.236.169.114",
        "54.201.236.212",
        "54.203.241.214",
        "34.221.254.29",
        "54.187.50.120",
        "54.184.140.221",
        "34.215.192.133",
        "35.164.180.39",
        "54.184.183.20",
        "52.43.197.215",
        "54.201.42.245",
        "54.218.113.88",
        "54.244.141.192",
        "34.217.98.54",
        "34.222.168.33",
        "52.32.143.49",
        "54.187.224.80",
        "54.189.87.145",
        "52.39.164.105",
        "54.70.55.164",
        "54.214.68.206",
        "54.201.239.109",
        "34.215.146.162",
        "18.236.233.120",
        "54.190.217.178",
        "34.220.41.134",
        "34.212.178.215",
        "34.219.169.55",
        "54.218.251.43",
        "18.236.216.191",
        "54.188.17.60",
        "54.191.227.118",
        "34.213.5.102",
        "35.166.79.235",
        "54.71.107.225",
        "54.201.162.86",
        "52.34.141.75",
        "34.217.43.189",
        "52.38.77.105",
        "52.11.252.174",
        "54.191.221.246",
        "54.218.107.83",
        "54.212.18.218",
        "34.220.53.77",
        "54.244.41.15",
        "34.222.135.203",
        "54.191.110.152",
        "34.208.190.130",
        "34.222.6.55",
        "54.184.7.184",
        "52.12.47.86",
        "34.217.28.248",
        "54.149.252.146",
        "54.149.80.193",
        "34.215.55.0",
        "34.220.74.48",
        "34.209.124.112",
        "34.217.23.70",
        "34.222.102.137",
        "34.209.166.42",
        "18.236.128.49",
        "35.163.99.20",
        "34.215.67.224",
        "34.211.244.117",
        "18.236.199.232",
        "54.191.237.52",
        "34.223.226.224",
        "54.149.133.143",
        "52.41.198.242",
        "54.148.215.161",
        "54.188.193.70",
        "54.218.104.194",
        "34.209.73.208",
        "34.220.88.70",
        "52.40.101.104",
        "18.236.68.153",
        "34.218.76.179",
        "34.219.94.178",
        "54.218.127.128",
        "52.27.198.246",
        "18.237.204.153",
        "35.166.57.113",
        "54.191.15.3",
        "18.237.128.46",
        "34.219.63.49",
        "35.163.156.71",
        "52.24.45.31",
        "54.149.187.78",
        "35.86.134.29",
        "52.12.226.94",
        "54.190.27.112",
        "54.244.10.24",
        "35.90.159.41",
        "18.236.164.203",
        "54.202.241.115",
        "34.220.140.204",
        "34.208.209.129",
        "52.37.169.196",
        "52.25.200.163",
        "54.202.209.119",
        "54.189.113.62",
        "52.40.27.14",
        "54.244.231.230",
        "35.88.57.90",
        "35.89.2.174",
        "35.91.153.134",
        "34.217.52.238",
        "54.187.239.13",
        "18.236.133.95",
        "34.220.131.73",
        "52.34.225.198",
        "35.90.0.112",
        "54.244.108.202",
        "35.90.153.10",
        "34.220.175.88",
        "54.203.13.147",
        "35.91.116.224",
        "35.165.24.65",
        "34.219.210.0",
        "35.91.22.144",
        "54.200.63.42",
        "54.212.230.134",

    };

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
