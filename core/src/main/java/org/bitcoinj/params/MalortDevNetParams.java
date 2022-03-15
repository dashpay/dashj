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
        "52.32.143.199",
        "35.163.10.221",
        "34.214.64.88",
        "54.69.64.188",
        "54.214.176.77",
        "54.203.139.239",
        "54.184.92.14",
        "54.201.150.219",
        "52.41.151.238",
        "35.87.79.250",
        "34.212.69.124",
        "54.190.47.14",
        "52.10.222.11",
        "52.32.33.70",
        "34.221.131.65",
        "54.203.26.179",
        "35.87.145.153",
        "18.237.213.29",
        "35.165.78.112",
        "54.191.79.53",
        "34.219.100.75",
        "34.217.211.73",
        "54.188.204.152",
        "54.244.211.150",
        "35.161.91.251",
        "54.191.131.7",
        "18.237.195.82",
        "34.221.206.98",
        "34.214.107.161",
        "34.212.172.179",
        "34.219.10.158",
    };

    public MalortDevNetParams() {
        super(DEVNET_NAME, "yZeZhBYxmxVkoKHsgGxbzj8snbU17DYeZJ", 20001,
                MASTERNODES, true, 70220);
        dnsSeeds = MASTERNODES;

        // minimumDifficultyBlocks = 1000;
        DIP0024BlockHeight = 300;
        isDIP24Only = true;

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
