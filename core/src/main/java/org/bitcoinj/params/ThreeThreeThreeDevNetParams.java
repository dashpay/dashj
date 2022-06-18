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

public class ThreeThreeThreeDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "333";

    private static final String[] MASTERNODES = new String[]{
            "35.85.147.139",
            "52.32.211.97",
            "54.245.53.143",
            "54.186.99.190",
            "52.13.97.179",
            "54.149.247.114",
            "34.212.174.115",
            "34.220.227.121",
            "35.160.66.86",
            "54.218.101.31",
            "34.217.146.220",
            "35.86.86.185"
    };

    public ThreeThreeThreeDevNetParams() {
        super(DEVNET_NAME, "yM6zJAMWoouAZxPvqGDbuHb6BJaD6k4raQ", 20001,
                MASTERNODES, true, 70220);
        dnsSeeds = MASTERNODES;
        dropPeersAfterBroadcast = false; // this network is too small
        DIP0024BlockHeight = 300;
        isDIP24Only = false;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_DEVNET;
        addLLMQ(LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024);
    }

    private static ThreeThreeThreeDevNetParams instance;

    public static ThreeThreeThreeDevNetParams get() {
        if (instance == null) {
            instance = new ThreeThreeThreeDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
