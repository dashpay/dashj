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

public class MojitoDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "mojito";

    private static final String[] MASTERNODES = new String[]{
        "35.91.72.103",
        "35.87.140.64",
        "35.88.93.189",
        "54.212.13.99",
        "52.32.240.193",
        "54.71.209.203",
        "34.220.229.64",
        "54.185.157.224",
        "34.219.83.228",
        "52.42.97.123",
        "35.89.120.122",
        "18.237.57.30",
        "54.149.185.48",
        "35.163.184.206",
        "35.89.100.95",
        "35.88.254.18",
        "34.220.68.151",
        "35.162.144.29",
    };

    public MojitoDevNetParams() {
        super(DEVNET_NAME, "yXePLfsnJHGbM2LAWcxXaJaixX4qKs38g1", 20001,
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

    private static MojitoDevNetParams instance;

    public static MojitoDevNetParams get() {
        if (instance == null) {
            instance = new MojitoDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
