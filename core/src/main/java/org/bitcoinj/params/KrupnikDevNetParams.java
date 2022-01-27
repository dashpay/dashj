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

public class KrupnikDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "krupnik";

    private static final String[] MASTERNODES = new String[]{
            "34.210.237.116",
            "54.69.65.231",
            "54.185.90.95",
            "54.186.234.0",
            "35.87.212.139",
            "34.212.52.44",
            "34.217.47.197",
            "34.220.79.131",
            "18.237.212.176",
            "54.188.17.188",
            "34.210.1.159",
    };

    public KrupnikDevNetParams() {
        super(DEVNET_NAME, "yPBtLENPQ6Ri1R7SyjevvvyMdopdFJUsRo", 20001,
                MASTERNODES, true, 70219);
        dnsSeeds = MASTERNODES;
        addLLMQ(LLMQParameters.LLMQType.LLMQ_DEVNET);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
    }

    private static KrupnikDevNetParams instance;

    public static KrupnikDevNetParams get() {
        if (instance == null) {
            instance = new KrupnikDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
