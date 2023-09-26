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

public class BinTangDevNetParams extends DevNetParams {
    private static final String DEVNET_NAME = "bintang";

    private static final String[] MASTERNODES = new String[] {
        "34.213.25.113",
        "34.221.14.36",
        "54.212.235.188",
        "18.237.94.193",
        "35.88.177.184",
        "18.236.202.174",
        "35.93.150.186",
        "34.208.58.6",
        "35.90.217.152",
        "34.216.30.128",
        "54.201.96.255",
        "54.201.242.188",
        "35.93.23.127",
        "35.88.187.214",
        "34.222.238.16",
        "54.186.159.169",
        "52.88.93.8",
        "52.37.86.233",
    };

    public BinTangDevNetParams() {
        super(DEVNET_NAME, "yZLSzMpkSk9aAYujdiMauQi4MYjQQwFgGQ", 20001,
                MASTERNODES, true, 70220);
        dnsSeeds = MASTERNODES;
        dropPeersAfterBroadcast = false; // this network is too small
        DIP0024BlockHeight = -1;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_DEVNET;
        addLLMQ(LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024);
    }

    private static BinTangDevNetParams instance;

    public static BinTangDevNetParams get() {
        if (instance == null) {
            instance = new BinTangDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
