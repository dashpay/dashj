/*
 * Copyright 2021 Dash Core Group
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

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.quorums.LLMQParameters;

public class MalortDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "malort";

    private static final String[] MASTERNODES = new String[]{
        "34.217.101.214",
        "52.24.32.135",
        "35.160.75.183",
        "18.236.136.225",
        "35.86.153.102",
        "34.221.192.170",
        "34.219.111.234",
        "35.88.174.158",
        "18.237.63.200",
        "54.188.12.74",
        "18.236.246.15",
        "34.220.115.178",
        "35.165.66.189",
        "35.87.14.168",
        "34.212.139.17",
        "34.222.44.80",
        "52.35.137.47",
        "52.39.67.55",
        "54.191.5.112",
        "34.220.224.234",
        "54.184.182.124",
        "34.222.121.252",
        "18.236.109.157",
        "18.237.205.175",
        "54.202.7.61",
        "34.213.205.6",
        "35.163.147.204",
        "35.89.250.3",
        "35.87.251.45",
        "54.203.144.181",
        "54.218.121.189",
        "54.148.204.66",
        "34.208.17.220",
        "52.38.242.89",
        "54.202.144.171",
        "35.89.62.188",
        "34.221.166.161",
        "35.163.53.152",
        "54.149.160.181",
        "34.217.113.238",
        "54.202.42.126",
        "35.164.136.98",
        "18.237.110.144",
        "34.213.222.104",
        "54.212.157.181",
        "34.221.168.25",
        "54.191.152.99",
        "34.220.115.7",
        "34.218.226.22",
        "34.220.54.3",
        "54.212.186.88",
        "34.212.205.147",
        "54.218.96.192",
        "35.88.252.196",
        "34.216.74.37",
        "34.211.224.153",
        "35.89.9.148",
        "54.69.200.233",
        "35.88.174.161",
        "54.185.68.102",
        "35.89.74.203",
        "34.208.87.204",
        "34.221.46.52",
        "35.88.163.164",
        "35.88.112.176",
        "34.215.213.203",
        "34.220.242.127",
        "34.214.85.81",
        "35.89.195.197",
        "54.186.192.121",
        "52.39.197.90",
        "34.210.152.22",
        "54.188.72.251",
        "52.37.145.57",
        "35.163.28.241",
        "18.236.104.42",
        "35.89.165.251",
        "34.209.204.43",
        "54.188.4.101",
        "54.188.182.225",
        "35.160.65.220",
        "35.161.106.50",
        "34.220.160.87",
        "34.221.54.24",
        "34.211.34.73",
        "35.86.120.160",
        "35.165.204.30",
        "52.26.100.18",
        "54.191.186.78",
        "18.237.196.115",
        "34.216.21.44",
        "34.219.239.14",
        "34.223.254.90",
        "18.237.118.205",
        "34.220.38.9",
        "54.149.188.96",
        "34.217.215.38",
        "54.212.237.102",
        "34.219.148.66",
        "52.38.40.75",
        "54.187.93.108",
        "35.89.60.166",
        "54.202.102.252",
        "35.163.250.101",
        "34.212.171.113",
};

    public MalortDevNetParams() {
        super(DEVNET_NAME, "yfxbrFWaHdfAAYGqAsPVsUF1YeJKwbKb5x", 20001,
                MASTERNODES, true, 70220);

        // minimumDifficultyBlocks = 1000;
        DIP0024BlockHeight = Integer.MAX_VALUE;//5227;
        isDIP24Only = false;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_50_60;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_60_75;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_100_67;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_50_60;
        addLLMQ(LLMQParameters.LLMQType.LLMQ_60_75);

        assumeValidQuorums.add(Sha256Hash.wrap("0000077bac16cbd6d41e516d2086d400ae462c77fa2be5c27d683ada286845ae"));
        assumeValidQuorums.add(Sha256Hash.wrap("00000bbaf57a31ce8f0c482b31d6e1e38844c33021a2a55ef2230f425b6194d1"));
        assumeValidQuorums.add(Sha256Hash.wrap("0000072f64f0ceb8584fdb94278bcc49efa281da9bee3e25fb45f963400cbbd9"));
    }

    private static MalortDevNetParams instance;

    public static MalortDevNetParams get() {
        if (instance == null) {
            instance = new MalortDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
