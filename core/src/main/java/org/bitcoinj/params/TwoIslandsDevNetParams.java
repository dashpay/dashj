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

public class TwoIslandsDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "two-islands";

    private static final String[] MASTERNODES = new String[]{
        "34.221.170.217",
        "54.191.146.189",
        "54.149.47.81",
        "44.227.219.110",
        "35.84.65.19",
        "34.218.147.83",
        "52.10.213.115",
        "44.241.67.131",
        "54.187.37.165",
        "44.228.137.254",
        "35.166.84.162",
        "44.231.62.211",
        "52.39.77.20",
        "18.236.195.112"
    };

    public TwoIslandsDevNetParams() {
        super(DEVNET_NAME, "yXs5gFBzepP6buEXsAi23yoHdbuQvzvx4N", 20001,
                MASTERNODES, true, -1);
        dnsSeeds = MASTERNODES;
        dropPeersAfterBroadcast = false; // this network is too small
        DIP0024BlockHeight = 300;
        isDIP24Only = false;
        basicBLSSchemeActivationHeight = 1200;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET_PLATFORM;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_DEVNET;
    }

    private static TwoIslandsDevNetParams instance;

    public static TwoIslandsDevNetParams get() {
        if (instance == null) {
            instance = new TwoIslandsDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
