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

import org.bitcoinj.quorums.LLMQParameters;

public class MalortDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "malort";

    private static final String[] MASTERNODES = new String[]{
            "35.87.82.87",
            "52.42.154.157",
            "52.11.185.242",
            "54.184.87.141",
            "54.201.188.15",
            "54.189.24.195",
            "54.186.154.71",
            "34.210.88.30",
            "18.237.201.80",
            "54.191.157.233",
            "52.11.29.182",
            "18.237.134.48",
            "54.188.47.140",
            "35.87.213.85",
            "52.37.54.4",
            "34.222.0.41",
            "34.213.235.240",
            "54.70.58.217",
            "34.213.3.43",
            "54.71.64.108",
            "34.221.116.72",
            "54.202.3.151",
            "34.220.150.226",
            "34.212.137.236",
            "34.222.43.203",
            "54.203.114.28",
            "54.149.208.129",
            "52.41.124.138",
            "35.162.139.6",
            "54.189.5.184",
            "54.212.206.221",
    };

    public MalortDevNetParams() {
        super(DEVNET_NAME, "yZeZhBYxmxVkoKHsgGxbzj8snbU17DYeZJ", 20001,
                MASTERNODES, true, 70220);
        dnsSeeds = MASTERNODES;

        // minimumDifficultyBlocks = 1000;
        DIP0024BlockHeight = 300;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET;
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
