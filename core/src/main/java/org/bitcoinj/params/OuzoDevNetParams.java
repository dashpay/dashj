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

public class OuzoDevNetParams extends DevNetParams {
    private static final String DEVNET_NAME = "ouzo";

    private static final String[] MASTERNODES = new String[] {
        "54.201.238.2",
        "34.213.181.82",
        "54.201.128.224",
        "35.162.210.116",
        "35.164.57.238"
    };

    private static final String[] HP_MASTERNODES = new String[] {
        "35.89.20.22",
        "54.245.17.19",
        "35.92.194.218",
        "54.184.120.70",
        "34.222.63.83",
        "34.211.67.75",
        "54.218.36.175",
        "34.222.111.80",
        "34.212.230.153",
        "35.87.1.243",
        "35.87.111.188",
        "18.236.219.58",
        "35.91.85.79",
        "54.188.181.224",
        "18.246.67.106"
    };

    public OuzoDevNetParams() {
        super(DEVNET_NAME, "ye5hSqkLZkwcx7rJei7pwTNkdCwxAzMDhh", 20001,
                MASTERNODES, true, 70230);
        dnsSeeds = MASTERNODES;
        dropPeersAfterBroadcast = false; // this network is too small
        DIP0024BlockHeight = 300;
        v19BlockHeight = 300;
        v20BlockHeight = 300;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_DEVNET;
        addLLMQ(LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024);
    }

    private static OuzoDevNetParams instance;

    public static OuzoDevNetParams get() {
        if (instance == null) {
            instance = new OuzoDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }

    @Override
    public String[] getDefaultHPMasternodeList() {
        return HP_MASTERNODES;
    }
}
