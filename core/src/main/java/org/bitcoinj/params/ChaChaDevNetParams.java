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
        "52.12.83.1",
        "54.185.181.2",
        "35.93.63.215",
        "34.220.179.132",
        "54.70.61.128",
        "35.90.125.129",
        "35.86.106.137",
        "54.191.116.254",
        "54.149.139.205",
        "52.34.56.84",
        "35.88.184.140",
        "18.237.62.240",
        "54.212.6.77",
        "34.221.87.100",
        "35.161.109.67",
        "35.91.172.122",
        "54.202.33.84",
        "35.89.126.233",
    };

    public ChaChaDevNetParams() {
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
