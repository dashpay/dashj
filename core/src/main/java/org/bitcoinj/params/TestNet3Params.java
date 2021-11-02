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

        dnsSeeds = new String[]{
                "testnet-seed.dashdot.io" // this seeder is offline
        };

        bip32HeaderP2PKHpub = 0x043587cf; // The 4 byte header that serializes in base58 to "tpub".
        bip32HeaderP2PKHpriv = 0x04358394; // The 4 byte header that serializes in base58 to "tprv"

        checkpoints.put(261, Sha256Hash.wrap("00000c26026d0815a7e2ce4fa270775f61403c040647ff2c3091f99e894a4618"));
        checkpoints.put(1999, Sha256Hash.wrap("00000052e538d27fa53693efe6fb6892a0c1d26c0235f599171c48a3cce553b1"));
        checkpoints.put(2999, Sha256Hash.wrap("0000024bc3f4f4cb30d29827c13d921ad77d2c6072e586c7f60d83c2722cdcc5"));

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
            "52.36.244.225", "52.39.164.105", "54.185.249.226", "34.219.169.55",
            "54.213.219.155", "35.165.207.13", "34.220.41.134", "18.237.204.153",
            "54.191.15.3", "34.212.231.240", "35.163.99.20", "54.218.107.83",
            "18.237.5.33", "54.191.32.70", "54.69.210.42", "34.215.192.133",
            "52.34.141.75", "54.201.239.109", "18.236.233.120", "34.210.81.39",
            "34.221.42.205", "34.209.124.112", "54.188.17.60", "18.236.68.153",
            "54.184.140.221", "54.212.75.8", "34.222.127.158", "34.220.17.107",
            "34.221.254.29", "34.216.195.19", "35.160.140.37", "54.70.65.199",
            "34.220.53.77", "54.244.41.15", "34.215.144.176", "54.201.162.86",
            "34.211.244.117", "34.223.226.224", "52.40.101.104", "34.219.93.145",
            "34.217.23.70", "54.185.1.69", "54.190.73.116", "52.26.220.40",
            "34.218.76.179", "54.212.84.164", "54.191.227.118", "18.237.147.160",
            "52.11.133.244", "34.208.88.128", "18.237.128.46", "52.32.232.156",
            "54.201.236.212", "54.218.127.128", "54.71.107.225", "54.187.11.213",
            "18.236.216.191", "34.208.190.130", "54.201.189.185", "34.214.2.219",
            "34.222.135.203", "54.191.237.52", "54.201.42.245", "52.41.198.242",
            "34.220.74.48", "54.149.252.146", "54.148.215.161", "34.211.46.222",
            "54.149.133.143", "54.149.80.193", "35.166.57.113", "54.184.7.184",
            "34.222.168.33", "34.222.102.137", "52.13.119.69", "34.215.55.0",
            "18.236.160.247", "54.185.249.172", "34.220.124.90", "34.219.94.178",
            "34.213.5.102", "54.218.113.88", "34.214.102.160", "35.166.29.155",
            "54.191.110.152", "35.167.25.157", "35.166.79.235", "54.189.87.145",
            "18.236.169.114", "54.203.241.214", "54.189.162.193", "54.218.70.46",
            "34.220.88.70", "34.215.67.224", "54.148.106.179", "34.212.226.44",
            "52.38.77.105", "52.12.47.86", "35.160.13.25", "52.27.198.246",
            "34.215.57.86", "34.222.6.55", "54.68.10.46", "34.209.73.208",
            "52.38.248.133", "54.218.251.43", "54.187.229.6", "54.218.104.194",
            "54.187.50.120", "34.212.65.137", "18.236.199.232", "34.220.228.214",
            "52.11.252.174", "54.191.221.246", "34.209.166.42", "34.217.98.54",
            "34.222.242.228", "54.187.224.80", "54.188.193.70", "54.213.89.75",
            "34.219.81.129", "54.184.183.20", "54.202.157.120", "54.190.217.178",
            "34.220.12.188", "18.236.128.49", "52.32.143.49", "34.220.144.226",
            "35.164.96.124", "34.212.178.215", "54.245.197.173", "34.217.43.189",
            "34.222.225.76", "35.163.152.74", "35.165.37.186", "54.201.242.241",
            "34.216.200.22", "54.214.68.206", "52.43.197.215", "54.212.18.218",
            "52.11.85.154", "34.219.36.94", "34.211.49.3", "18.236.78.191",
            "34.215.146.162", "35.164.180.39"
    };

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
