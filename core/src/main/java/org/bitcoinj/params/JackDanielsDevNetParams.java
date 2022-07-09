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

public class JackDanielsDevNetParams extends DevNetParams {
    private static final String DEVNET_NAME = "jack-daniels";

    private static final String[] MASTERNODES = new String[] {
        "52.25.116.200",
        "52.41.135.78",
        "54.244.162.144",
        "35.166.81.45",
        "34.220.92.205",
        "52.24.101.11",
        "34.219.78.82",
        "35.161.44.62",
        "52.10.179.99",
        "34.210.63.1",
        "35.87.203.243",
        "35.90.124.254",
    };

    public JackDanielsDevNetParams() {
        super(DEVNET_NAME, "yRisqbEVej7gJHUQ2i566YsbWZg61732PW", 20001,
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

    private static JackDanielsDevNetParams instance;

    public static JackDanielsDevNetParams get() {
        if (instance == null) {
            instance = new JackDanielsDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
