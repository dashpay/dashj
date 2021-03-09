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

        if (CoinDefinition.supportsTestNet)
            checkState(genesisHash.equals(CoinDefinition.testnetGenesisHash));
        alertSigningKey = HEX.decode(CoinDefinition.TESTNET_SATOSHI_KEY);

        dnsSeeds = new String[]{
                "testnet-seed.dashdot.io"
        };

        bip32HeaderP2PKHpub = 0x043587cf; // The 4 byte header that serializes in base58 to "tpub".
        bip32HeaderP2PKHpriv = 0x04358394; // The 4 byte header that serializes in base58 to "tprv"

        checkpoints.put(261, Sha256Hash.wrap("00000c26026d0815a7e2ce4fa270775f61403c040647ff2c3091f99e894a4618"));
        checkpoints.put(1999, Sha256Hash.wrap("00000052e538d27fa53693efe6fb6892a0c1d26c0235f599171c48a3cce553b1"));
        checkpoints.put(2999, Sha256Hash.wrap("0000024bc3f4f4cb30d29827c13d921ad77d2c6072e586c7f60d83c2722cdcc5"));

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

    public static String[] MASTERNODES = {
            "54.189.162.193",
            "35.165.37.186",
            "34.220.12.192",
            "54.185.249.172",
            "54.184.140.221",
            "54.185.1.69",
            "54.190.73.116",
            "34.216.200.22",
            "54.69.210.42",
            "18.237.47.243",
            "34.214.2.219",
            "34.211.49.3",
            "34.222.242.228",
            "54.201.189.185",
            "34.212.231.240",
            "34.219.36.94",
            "34.212.65.137",
            "34.215.192.133",
            "35.160.13.25",
            "54.148.106.179",
            "34.210.81.39",
            "34.215.57.86",
            "54.212.84.164",
            "54.68.10.46",
            "18.236.160.247",
            "52.32.232.156",
            "34.220.17.107",
            "54.191.32.70",
            "52.36.244.225",
            "54.185.249.226",
            "35.167.25.157",
            "54.212.75.8",
            "34.211.46.222",
            "34.221.42.205",
            "54.218.70.46",
            "34.211.221.92",
            "34.220.228.214",
            "35.165.207.13",
            "52.11.133.244",
            "54.201.242.241",
            "34.221.254.29",
            "52.38.248.133",
            "54.245.197.173",
            "54.202.157.120",
            "34.219.81.129",
            "18.236.78.191",
            "18.236.169.114",
            "54.213.219.155",
            "52.13.119.69",
            "54.70.65.199",
            "34.214.102.160",
            "34.208.88.128",
            "34.212.226.44",
            "35.160.140.37",
            "54.187.50.120",
            "35.164.96.124",
            "34.222.225.76",
            "54.184.183.20",
            "52.26.220.40",
            "18.237.5.33",
            "34.219.93.145",
            "34.220.144.226",
            "34.220.12.188",
            "35.163.152.74",
            "54.201.236.212",
            "54.187.229.6",
            "35.164.180.39",
            "34.216.195.19",
            "34.215.144.176",
            "54.203.241.214",
            "34.222.127.158",
            "34.220.124.90",
            "54.202.194.212",
            "54.149.205.69",
            "52.39.217.90",
            "34.209.53.60",
            "34.220.220.105",
            "54.191.233.209",
            "54.71.15.196",
            "34.221.163.27",
            "34.212.178.215",
            "34.214.50.199",
            "34.219.33.53",
            "54.188.148.59",
            "34.216.234.147",
            "54.187.186.193",
            "54.201.42.245",
            "54.149.100.15",
            "18.237.147.112",
            "35.167.66.154",
            "54.188.67.6",
            "34.217.123.19",
            "34.213.7.154",
            "18.237.128.46",
            "52.35.57.19",
            "52.40.2.106",
            "54.244.213.177",
            "54.189.196.250",
            "54.189.87.145",
            "54.201.6.34",
            "18.236.199.232",
            "54.245.57.167",
            "54.190.110.68",
            "52.26.212.105",
            "54.190.222.166",
            "34.220.169.23",
            "34.213.5.102",
            "34.221.246.31",
            "34.209.238.100",
            "54.149.160.85",
            "34.212.235.52",
            "34.219.111.222",
            "18.236.128.49",
            "35.161.255.92",
            "52.34.73.197",
            "52.24.207.213",
            "34.217.133.24",
            "52.35.175.249",
            "34.217.28.248",
            "34.220.88.70",
            "54.188.17.60",
            "54.186.101.247",
            "54.191.91.18",
            "34.209.166.42",
            "34.220.53.77",
            "54.200.113.232",
            "52.34.94.83",
            "18.236.254.166",
            "34.218.60.169",
            "34.217.43.189",
            "54.191.173.160",
            "54.149.252.146",
            "54.201.162.86",
            "54.213.149.235",
            "18.237.175.7",
            "54.214.68.206",
            "54.213.153.101",
            "34.221.201.117",
            "52.37.52.137",
            "35.163.99.20",
            "34.213.185.136",
            "34.217.23.70",
            "34.222.6.55",
            "52.40.101.104",
            "34.221.230.164",
            "54.212.234.12",
            "54.148.229.157",
            "34.220.117.188",
            "34.219.169.55",
            "34.208.190.130",
            "52.12.47.86",
            "52.36.78.35",
            "34.209.124.112",
            "52.13.113.56",
            "54.184.7.184",
            "18.237.153.152",
            "54.148.34.108",
            "54.188.79.177",
            "34.220.70.166",
            "54.201.239.109",
            "34.215.118.56",
            "34.222.168.33",
            "52.25.120.214",
            "52.43.213.174",
            "52.11.164.192",
            "34.222.170.60",
            "54.218.127.128",
            "35.163.240.49",
            "34.222.135.203",
            "52.37.9.64",
            "34.220.41.134",
            "54.200.180.217",
            "34.220.74.48",
            "18.237.197.52",
            "34.221.147.35",
            "52.89.115.79",
            "54.191.50.175",
            "35.165.18.182",
            "18.236.235.73",
            "52.41.198.242",
            "52.12.205.4",
            "34.219.92.120",
            "54.202.230.194",
            "54.70.97.183",
            "34.222.129.96",
            "34.217.66.212",
            "54.191.110.152",
            "34.219.224.131",
            "54.191.205.103",
            "34.211.55.248",
            "52.27.198.246",
            "54.191.237.52",
            "34.220.203.174",
            "54.218.101.246",
            "54.214.136.154",
            "54.218.113.88",
            "34.219.94.178",
            "54.245.170.248",
            "35.162.21.59",
            "52.27.77.36",
            "18.237.240.5",
            "52.34.185.113",
            "54.69.176.14",
            "34.214.118.162",
            "52.43.197.215",
            "34.211.244.117",
            "34.219.236.158",
            "54.214.150.119",
            "34.220.13.128",
            "54.191.221.246",
            "34.218.250.248",
            "52.13.182.252",
            "54.149.231.161",
            "54.188.193.70",
            "54.187.30.237",
            "34.220.68.104",
            "54.203.134.157",
            "54.191.227.118",
            "34.219.163.64",
            "34.212.45.207",
            "54.191.15.3",
            "54.218.104.194",
            "52.32.143.49",
            "54.212.18.218",
            "54.149.133.143",
            "34.220.176.42",
            "52.34.141.75",
            "54.184.164.170",
            "54.218.251.43",
            "34.220.74.134",
            "34.220.46.120",
            "34.222.139.133",
            "18.236.233.120",
            "52.39.148.96",
            "34.217.98.54",
            "35.163.65.89",
            "54.148.215.161",
            "34.222.102.137",
            "18.236.216.191",
            "54.149.80.193",
            "34.221.81.204",
            "34.209.73.208",
            "18.237.194.75",
            "18.237.204.153",
            "34.222.48.40",
            "34.209.70.251",
            "54.213.230.243",
            "35.166.144.47",
            "52.39.30.29",
            "52.12.123.180",
            "34.223.226.224",
            "54.244.41.15",
            "52.13.84.216",
            "52.26.198.61",
            "54.71.175.3",
            "34.222.18.196",
            "34.215.67.224",
            "54.71.107.225",
            "54.190.217.178",
            "34.215.55.0",
            "52.38.77.105",
            "52.27.85.85",
            "54.70.55.164",
            "18.237.32.119",
            "54.190.5.245",
            "34.219.216.77",
            "34.221.31.124",
            "35.160.245.50",
            "52.11.252.174",
            "52.12.29.246",
            "34.222.56.156",
            "54.187.224.80",
            "34.217.73.253",
            "54.218.107.83",
            "34.215.146.162",
            "18.236.102.127",
            "34.215.181.20",
            "34.210.115.174",
            "34.218.76.179",
            "34.222.236.150",
            "35.166.57.113",
            "34.222.40.104",
            "18.236.68.153",
            "52.35.186.48",
            "54.202.8.183",
            "54.186.170.213",
            "35.166.79.235",
            "54.187.11.213",
            "18.237.147.160"
    };

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
