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

        addrSeeds = new int[] {
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

        fulfilledRequestExpireTime = 5*60;
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
            "1.159.143.235",
            "100.24.239.64",
            "104.236.52.214",
            "104.238.156.109",
            "104.248.135.44",
            "104.248.218.23",
            "104.248.242.126",
            "104.248.92.98",
            "106.12.73.74",
            "107.150.121.217",
            "107.22.199.130",
            "108.61.189.144",
            "108.61.192.47",
            "109.235.71.56",
            "109.97.214.43",
            "11.122.33.44",
            "11.122.33.45",
            "114.23.54.141",
            "116.202.68.142",
            "116.203.197.7",
            "116.203.200.139",
            "116.203.204.120",
            "116.203.87.12",
            "128.199.99.191",
            "134.209.2.128",
            "134.209.231.79",
            "134.209.5.148",
            "134.209.90.112",
            "135.181.111.191",
            "135.181.111.192",
            "136.244.89.226",
            "138.68.45.118",
            "139.159.206.76",
            "139.59.249.65",
            "139.59.35.20",
            "139.59.81.170",
            "139.59.86.146",
            "142.93.163.66",
            "142.93.40.79",
            "144.217.86.47",
            "144.76.66.20",
            "145.239.235.16",
            "145.239.235.17",
            "145.239.235.18",
            "149.248.51.30",
            "149.248.55.77",
            "149.28.203.190",
            "155.138.226.83",
            "155.138.239.217",
            "157.230.110.86",
            "157.230.19.127",
            "157.230.247.219",
            "159.203.21.20",
            "159.203.34.99",
            "159.224.190.244",
            "159.65.105.41",
            "159.65.69.245",
            "159.69.154.119",
            "159.69.72.12",
            "159.89.137.143",
            "161.189.67.25",
            "165.22.213.149",
            "165.22.233.59",
            "165.227.10.68",
            "165.227.20.111",
            "165.227.63.223",
            "167.71.223.212",
            "167.99.110.59",
            "167.99.164.60",
            "167.99.183.55",
            "176.48.187.2",
            "178.128.87.111",
            "178.62.203.249",
            "178.62.68.10",
            "178.62.93.226",
            "18.222.111.70",
            "18.231.111.219",
            "18.236.102.127",
            "18.236.128.49",
            "18.236.160.247",
            "18.236.169.114",
            "18.236.199.232",
            "18.236.212.138",
            "18.236.216.191",
            "18.236.233.120",
            "18.236.235.73",
            "18.236.254.166",
            "18.236.68.153",
            "18.236.78.191",
            "18.237.128.46",
            "18.237.147.112",
            "18.237.147.160",
            "18.237.153.152",
            "18.237.175.7",
            "18.237.194.75",
            "18.237.197.52",
            "18.237.204.153",
            "18.237.240.5",
            "18.237.32.119",
            "18.237.47.243",
            "18.237.5.33",
            "182.50.125.85",
            "185.195.19.212",
            "185.213.37.1",
            "185.213.37.2",
            "185.62.150.195",
            "195.128.102.75",
            "195.141.143.49",
            "195.201.19.40",
            "195.201.37.255",
            "198.199.74.241",
            "207.154.242.157",
            "207.154.250.175",
            "207.246.97.105",
            "23.240.232.195",
            "23.91.97.211",
            "3.129.25.142",
            "3.221.29.23",
            "3.226.83.105",
            "34.208.190.130",
            "34.208.88.128",
            "34.209.124.112",
            "34.209.166.42",
            "34.209.211.134",
            "34.209.238.100",
            "34.209.53.60",
            "34.209.70.251",
            "34.209.73.208",
            "34.210.115.174",
            "34.210.246.185",
            "34.210.81.39",
            "34.211.221.92",
            "34.211.244.117",
            "34.211.46.222",
            "34.211.49.3",
            "34.211.55.248",
            "34.212.178.215",
            "34.212.226.44",
            "34.212.231.240",
            "34.212.235.52",
            "34.212.45.207",
            "34.212.65.137",
            "34.213.185.136",
            "34.213.5.102",
            "34.213.7.154",
            "34.214.102.160",
            "34.214.118.162",
            "34.214.2.219",
            "34.214.50.199",
            "34.215.118.56",
            "34.215.144.176",
            "34.215.146.162",
            "34.215.181.20",
            "34.215.192.133",
            "34.215.55.0",
            "34.215.57.86",
            "34.215.67.224",
            "34.216.195.19",
            "34.216.200.22",
            "34.216.234.147",
            "34.217.123.19",
            "34.217.133.24",
            "34.217.23.70",
            "34.217.28.248",
            "34.217.43.189",
            "34.217.66.212",
            "34.217.73.253",
            "34.217.98.54",
            "34.218.129.98",
            "34.218.250.248",
            "34.218.60.169",
            "34.218.76.179",
            "34.219.111.222",
            "34.219.163.64",
            "34.219.169.55",
            "34.219.216.77",
            "34.219.224.131",
            "34.219.236.158",
            "34.219.33.53",
            "34.219.36.94",
            "34.219.81.129",
            "34.219.92.120",
            "34.219.93.145",
            "34.219.94.178",
            "34.220.117.188",
            "34.220.12.188",
            "34.220.12.192",
            "34.220.124.90",
            "34.220.13.128",
            "34.220.144.226",
            "34.220.169.23",
            "34.220.17.107",
            "34.220.176.42",
            "34.220.203.174",
            "34.220.220.105",
            "34.220.228.214",
            "34.220.41.134",
            "34.220.46.120",
            "34.220.53.77",
            "34.220.68.104",
            "34.220.70.166",
            "34.220.74.134",
            "34.220.74.48",
            "34.220.88.70",
            "34.221.109.68",
            "34.221.147.35",
            "34.221.163.27",
            "34.221.201.117",
            "34.221.230.164",
            "34.221.246.31",
            "34.221.254.29",
            "34.221.31.124",
            "34.221.42.205",
            "34.221.81.204",
            "34.222.102.137",
            "34.222.127.158",
            "34.222.129.96",
            "34.222.135.203",
            "34.222.139.133",
            "34.222.168.33",
            "34.222.170.60",
            "34.222.18.196",
            "34.222.225.76",
            "34.222.236.150",
            "34.222.242.228",
            "34.222.40.104",
            "34.222.48.40",
            "34.222.56.156",
            "34.222.6.55",
            "34.223.226.224",
            "34.224.152.100",
            "34.225.128.228",
            "34.233.155.236",
            "35.160.13.25",
            "35.160.140.37",
            "35.160.245.50",
            "35.161.101.35",
            "35.161.255.92",
            "35.162.21.59",
            "35.163.152.74",
            "35.163.226.32",
            "35.163.240.49",
            "35.163.65.89",
            "35.163.99.20",
            "35.164.180.39",
            "35.164.96.124",
            "35.165.18.182",
            "35.165.207.13",
            "35.165.37.186",
            "35.166.144.47",
            "35.166.29.155",
            "35.166.57.113",
            "35.166.79.235",
            "35.167.25.157",
            "35.167.66.154",
            "35.167.68.255",
            "35.168.78.191",
            "35.169.113.136",
            "35.172.52.88",
            "35.175.62.106",
            "35.185.202.219",
            "43.229.77.46",
            "45.32.211.155",
            "45.32.86.231",
            "45.48.168.16",
            "45.63.104.104",
            "45.77.176.16",
            "45.77.222.60",
            "46.101.243.84",
            "46.101.52.138",
            "47.75.68.154",
            "51.107.4.38",
            "52.11.133.244",
            "52.11.164.192",
            "52.11.252.174",
            "52.11.85.154",
            "52.12.123.180",
            "52.12.205.4",
            "52.12.29.246",
            "52.12.47.86",
            "52.13.113.56",
            "52.13.119.69",
            "52.13.182.252",
            "52.13.84.216",
            "52.21.8.124",
            "52.220.133.88",
            "52.220.61.88",
            "52.24.207.213",
            "52.25.120.214",
            "52.26.198.61",
            "52.26.212.105",
            "52.26.220.40",
            "52.27.161.229",
            "52.27.198.246",
            "52.27.77.36",
            "52.27.85.85",
            "52.32.143.49",
            "52.32.232.156",
            "52.34.141.75",
            "52.34.185.113",
            "52.34.73.197",
            "52.34.94.83",
            "52.35.175.249",
            "52.35.186.48",
            "52.35.57.19",
            "52.35.83.81",
            "52.36.244.225",
            "52.36.64.148",
            "52.36.78.35",
            "52.37.52.137",
            "52.37.9.64",
            "52.38.248.133",
            "52.38.77.105",
            "52.39.148.96",
            "52.39.164.105",
            "52.39.217.90",
            "52.39.30.29",
            "52.40.101.104",
            "52.40.2.106",
            "52.41.198.242",
            "52.42.113.36",
            "52.42.213.147",
            "52.43.197.215",
            "52.43.213.174",
            "52.52.139.186",
            "52.89.115.79",
            "54.148.106.179",
            "54.148.215.161",
            "54.148.229.157",
            "54.148.34.108",
            "54.149.100.15",
            "54.149.112.241",
            "54.149.133.143",
            "54.149.160.85",
            "54.149.205.69",
            "54.149.207.193",
            "54.149.231.161",
            "54.149.252.146",
            "54.149.80.193",
            "54.157.8.145",
            "54.184.140.221",
            "54.184.164.170",
            "54.184.183.20",
            "54.184.65.161",
            "54.184.7.184",
            "54.185.1.69",
            "54.185.249.172",
            "54.185.249.226",
            "54.186.101.247",
            "54.186.170.213",
            "54.187.11.213",
            "54.187.186.193",
            "54.187.224.80",
            "54.187.229.6",
            "54.187.30.237",
            "54.187.50.120",
            "54.188.148.59",
            "54.188.17.60",
            "54.188.193.70",
            "54.188.67.6",
            "54.188.79.177",
            "54.189.162.193",
            "54.189.196.250",
            "54.189.87.145",
            "54.190.110.68",
            "54.190.173.23",
            "54.190.217.178",
            "54.190.222.166",
            "54.190.5.245",
            "54.190.73.116",
            "54.191.110.152",
            "54.191.15.3",
            "54.191.173.160",
            "54.191.205.103",
            "54.191.221.246",
            "54.191.227.118",
            "54.191.233.209",
            "54.191.237.52",
            "54.191.32.70",
            "54.191.50.175",
            "54.191.91.18",
            "54.200.113.232",
            "54.200.180.217",
            "54.200.200.228",
            "54.201.162.86",
            "54.201.189.185",
            "54.201.236.212",
            "54.201.239.109",
            "54.201.242.241",
            "54.201.42.245",
            "54.201.6.34",
            "54.202.157.120",
            "54.202.194.212",
            "54.202.230.194",
            "54.202.8.183",
            "54.203.134.157",
            "54.203.241.214",
            "54.212.18.218",
            "54.212.234.12",
            "54.212.75.8",
            "54.212.84.164",
            "54.213.149.235",
            "54.213.153.101",
            "54.213.219.155",
            "54.213.230.243",
            "54.213.89.75",
            "54.214.136.154",
            "54.214.150.119",
            "54.214.68.206",
            "54.218.101.246",
            "54.218.104.194",
            "54.218.107.83",
            "54.218.113.88",
            "54.218.127.128",
            "54.218.251.43",
            "54.218.70.46",
            "54.236.214.8",
            "54.244.213.177",
            "54.244.41.15",
            "54.245.170.248",
            "54.245.197.173",
            "54.245.57.167",
            "54.68.10.46",
            "54.69.176.14",
            "54.69.210.42",
            "54.70.55.164",
            "54.70.65.199",
            "54.70.97.183",
            "54.71.107.225",
            "54.71.15.196",
            "54.71.175.3",
            "64.193.62.206",
            "68.183.167.16",
            "68.183.196.93",
            "78.46.161.22",
            "78.46.185.94",
            "80.240.23.199",
            "83.80.229.213",
            "85.209.240.99",
            "89.17.41.106",
            "95.179.164.87",
            "95.179.251.182",
            "95.216.174.152",
    };

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
