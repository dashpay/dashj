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

package org.bitcoinj.net.discovery;

import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.quorums.LLMQParameters;

public class OuzoDevNetParams extends DevNetParams {
    private static final String DEVNET_NAME = "ouzo";

    private static final String[] MASTERNODES = new String[] {
        "34.219.156.87",
        "34.212.27.111",
        "34.210.85.44",
        "34.211.109.220",
        "35.160.224.143"
    };

    private static final String[] HP_MASTERNODES = new String[] {
        "54.191.116.54",
        "52.12.171.109",
        "54.68.190.236",
        "18.237.173.165",
        "34.216.169.111",
        "34.212.19.42",
        "18.237.223.99",
        "18.236.129.28",
        "52.88.126.23",
        "35.88.90.103",
        "34.222.48.194",
        "35.93.78.120",
        "35.88.92.118",
        "34.217.42.245",
        "54.148.14.151",
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
