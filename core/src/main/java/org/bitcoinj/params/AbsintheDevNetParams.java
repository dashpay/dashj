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

public class AbsintheDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "absinthe";

    private static final String[] MASTERNODES = new String[]{
        "34.217.90.41",
        "34.219.142.157",
        "54.203.57.163",
        "54.189.147.138",
        "35.87.141.100",
        "52.24.16.52",
        "18.237.105.25",
        "34.219.194.54",
        "35.165.169.209",
        "34.219.134.212",
        "54.201.12.128",
        "35.88.132.125",
        "54.213.67.58",
        "35.166.147.71",
        "35.92.129.119",
        "35.91.168.157",
    };

    public AbsintheDevNetParams() {
        super(DEVNET_NAME, "yQaxrDEMJ7t2d4eDTugn3FY87T78j3fJX3", 20001,
                MASTERNODES, true, -1);
        dnsSeeds = MASTERNODES;
        dropPeersAfterBroadcast = false; // this network is too small
        DIP0024BlockHeight = 300;
        basicBLSSchemeActivationHeight = 1200;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypeDIP0024InstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET_DIP0024;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET_PLATFORM;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_DEVNET;
    }

    private static AbsintheDevNetParams instance;

    public static AbsintheDevNetParams get() {
        if (instance == null) {
            instance = new AbsintheDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
