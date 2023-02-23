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
        "34.222.50.127",
        "54.184.88.196",
        "34.220.160.44",
        "35.88.208.132",
        "54.201.94.25",
        "35.91.0.64",
        "34.212.225.222",
        "35.91.107.251",
        "18.236.108.59",
        "18.237.168.207",
        "52.42.161.19",
        "34.216.240.176",
        "34.212.169.34",
        "34.212.20.156",
        "34.210.76.97",
        "34.215.99.247",
        "35.165.211.75",
        "35.91.200.106",
        "35.167.103.31",
        "54.188.68.5",
        "35.89.166.103",
        "52.25.73.15",
        "54.212.27.211",
        "54.149.165.217",
        "35.88.194.155",
        "34.222.123.215",
        "35.89.113.194",
        "35.167.24.233",
        "35.160.143.192",
        "54.186.59.239",


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
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET_PLATFORM;
        llmqTypeMnhf = LLMQParameters.LLMQType.LLMQ_DEVNET;
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
