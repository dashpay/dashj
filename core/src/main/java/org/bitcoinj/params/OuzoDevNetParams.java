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
                "35.88.153.100",
                "35.90.86.5",
                "54.201.101.166",
                "34.221.211.13",
                "34.211.239.27",
                "34.209.234.219",
                "34.217.126.166",
                "54.149.125.171",
                "34.221.134.128",
                "35.92.44.96",
                "54.188.181.173",
                "35.88.196.32",
                "52.13.97.221",
                "34.218.76.191",
                "34.221.191.53",
                "35.89.136.16",
                "34.220.42.33",
                "18.237.57.152"
};

    public OuzoDevNetParams() {
        super(DEVNET_NAME, "yMu45N9hjWm6YZfonsnasugkiaL5ihrkkc", 20001,
                MASTERNODES, true, 70220);
        dnsSeeds = MASTERNODES;
        dropPeersAfterBroadcast = false; // this network is too small
        DIP0024BlockHeight = -1;
        isDIP24Only = false;

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
}
