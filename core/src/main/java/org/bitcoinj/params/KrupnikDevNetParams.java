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

    private static String DEVNET_NAME = "krupnik";

    private static String [] MASTERNODES = new String [] {
        "52.38.91.91",
        "34.219.219.19",
        "34.221.32.211",
        "35.87.4.244",
        "54.188.168.220",
        "34.211.88.72",
        "35.87.2.214",
        "35.86.171.21",
        "54.184.175.69",
        "52.25.246.83",
        "35.162.208.70",
    };

    public KrupnikDevNetParams() {
        super(DEVNET_NAME, "yMe6YVixWJ5797yN5AY4KzgJbHmXwVVsBg", 20001,
                MASTERNODES, true, 70219);
        dnsSeeds = MASTERNODES;
        addLLMQ(LLMQParameters.LLMQType.LLMQ_DEVNET);
        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
    }

    private static KrupnikDevNetParams instance;

    public static KrupnikDevNetParams get() {
        if(instance == null) {
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
