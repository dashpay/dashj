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

public class WhiteRussianDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "white-russian";

    private static final String[] MASTERNODES = new String[]{
        "35.85.152.110",
        "34.209.13.56",
        "52.42.93.34",
        "35.87.154.139",
        "35.92.216.172",
        "34.222.169.49",
        "52.27.159.100",
        "35.90.131.248",
        "34.211.144.169",
        "35.163.17.85",
        "52.26.67.115",
        "35.92.6.130",
        "54.191.109.168",
        "52.39.100.224",
        "54.200.39.51",
        "54.185.210.60",
        "35.89.197.145",
        "18.246.65.63",

    };

    public WhiteRussianDevNetParams() {
        super(DEVNET_NAME, "yZaEFuVfaycMzvQbHH7dgbDPJ6F2AGLqzR", 20001,
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

    private static WhiteRussianDevNetParams instance;

    public static WhiteRussianDevNetParams get() {
        if (instance == null) {
            instance = new WhiteRussianDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
