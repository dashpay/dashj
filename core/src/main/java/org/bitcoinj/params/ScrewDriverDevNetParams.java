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

public class ScrewDriverDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "screwdriver";

    private static final String[] MASTERNODES = new String[]{
        "35.92.67.183",
        "54.244.217.185",
        "54.149.244.160",
        "35.93.71.90",
        "52.34.46.50",
        "35.160.137.75",
        "35.92.93.204",
        "54.70.34.204",
        "35.91.184.146",
        "52.35.142.34",
        "35.89.135.53",
    };

    public ScrewDriverDevNetParams() {
        super(DEVNET_NAME, "yibwxyuuKsP6kBsq74vu9p6ju97qEb2B4b", 20001,
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

    private static ScrewDriverDevNetParams instance;

    public static ScrewDriverDevNetParams get() {
        if (instance == null) {
            instance = new ScrewDriverDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
