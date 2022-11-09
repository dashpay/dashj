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

public class ChaChaDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "chacha";

    private static final String[] MASTERNODES = new String[]{
        "34.213.73.187",
        "35.166.223.113",
        "34.222.70.155",
        "54.188.28.123",
        "52.33.126.127",
        "34.212.132.210",
        "54.191.175.225",
        "54.213.218.58",
        "34.210.252.158",
        "34.221.203.109",
        "52.38.9.28",
        "34.221.123.183",
        "35.91.217.175",
        "34.211.90.216",
        "35.89.101.137",
        "52.40.174.175",
        "34.221.153.236",
        "54.189.17.85",
    };

    public ChaChaDevNetParams() {
        super(DEVNET_NAME, "ybiRzdGWFeijAgR7a8TJafeNi6Yk6h68ps", 20001,
                MASTERNODES, true, -1);
        dnsSeeds = MASTERNODES;
        dropPeersAfterBroadcast = false; // this network is too small
        DIP0024BlockHeight = 300;
        isDIP24Only = false;
        basicBLSSchemeActivationHeight = 1200;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_DEVNET;
        addLLMQ(LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024);
    }

    private static ChaChaDevNetParams instance;

    public static ChaChaDevNetParams get() {
        if (instance == null) {
            instance = new ChaChaDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
