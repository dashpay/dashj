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
        "52.33.205.201",
        "54.187.0.112",
        "54.191.24.26",
        "34.219.75.55",
        "34.222.39.72",
        "54.245.65.81",
        "34.218.233.58",
        "54.190.145.8",
        "35.163.78.75",
        "34.219.73.212",
        "54.184.16.24",
        "54.186.234.96",
        "34.217.77.46",
        "34.220.68.124",
        "18.237.146.234",
        "18.237.100.208",
        "52.26.159.27",
        "34.208.17.128",
        "54.68.152.187",
        "34.215.177.167",
        "34.221.199.188",
        "52.36.87.248",
        "34.218.58.30",
        "34.221.190.159",
        "34.209.142.196",
        "52.24.214.216",
        "35.88.228.131",
        "54.185.217.140",
        "54.201.8.32",
        "34.222.165.63",
        "34.220.162.26",
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
