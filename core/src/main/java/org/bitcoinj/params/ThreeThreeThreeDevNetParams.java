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
        "34.220.100.153",
        "52.12.210.34",
        "54.71.255.176",
        "54.187.123.197",
        "34.217.195.49",
        "35.89.69.137",
        "34.210.15.156",
        "52.39.39.40",
        "54.245.132.133",
        "18.236.89.14",
        "35.163.60.183",
        "18.237.244.153",
        "35.89.54.219"
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
