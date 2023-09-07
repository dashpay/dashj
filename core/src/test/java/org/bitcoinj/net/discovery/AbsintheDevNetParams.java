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

package org.bitcoinj.net.discovery;

import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.quorums.LLMQParameters;

public class AbsintheDevNetParams extends DevNetParams {

    private static final String DEVNET_NAME = "absinthe";

    private static final String[] MASTERNODES = new String[]{
            "54.203.248.31",
            "54.244.207.116",
    };

    private static final String[] HP_MASTERNODES = new String[]{
        "52.12.65.230",
        "35.88.162.148",
        "35.87.149.127",
        "34.216.109.34",
        "52.40.57.30",
        "54.245.53.222",
        "54.244.210.173",
        "34.215.201.219",
        "35.91.255.242",
        "54.245.169.72",
        "54.184.78.233",
        "35.88.21.135",
        "52.36.206.44",
        "34.218.253.121",
    };

    public AbsintheDevNetParams() {
        super(DEVNET_NAME, "yQaxrDEMJ7t2d4eDTugn3FY87T78j3fJX3", 20001,
                MASTERNODES, true, 70227);
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

    @Override
    public String[] getDefaultHPMasternodeList() {
        return HP_MASTERNODES;
    }
}
