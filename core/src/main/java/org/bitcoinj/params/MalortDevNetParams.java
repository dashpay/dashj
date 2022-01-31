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
        "18.237.252.92",
        "54.70.175.91",
        "18.237.141.114",
        "52.27.98.239",
        "34.222.107.24",
        "35.89.13.46",
        "52.35.109.107",
        "35.166.27.56",
        "34.221.125.132",
        "35.88.254.151",
        "54.191.254.244",
        "54.186.19.51",
        "34.208.33.201",
        "54.190.191.15",
        "54.202.226.206",
        "35.161.52.77",
        "34.216.132.145",
        "54.202.203.197",
        "54.202.7.31",
        "54.191.61.174",
        "34.221.198.190",
        "52.39.21.163",
        "35.87.152.197",
        "52.33.50.210",
        "34.220.186.181",
        "54.218.97.70",
        "54.149.225.130",
        "54.202.57.238",
        "52.32.176.236",
        "54.189.76.182",
        "35.87.102.5",

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
