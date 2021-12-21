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

public class MalortDevNetParams extends DevNetParams {

    private static String DEVNET_NAME = "malort";

    private static String [] MASTERNODES = new String [] {
        "35.86.100.245",
        "34.221.160.14",
        "34.221.45.128",
        "34.217.24.41",
        "34.210.73.201",
        "35.88.247.4",
        "54.68.232.134",
        "54.190.158.28",
        "34.209.88.7",
        "35.161.234.223",
        "54.214.190.142",
        "34.221.195.54",
        "35.87.121.116",
        "34.222.232.184",
        "34.217.111.235",
        "52.32.155.63",
        "54.202.34.111",
        "35.162.231.166",
        "34.219.79.178",
        "18.236.206.50",
        "18.237.176.218",
        "35.165.42.167",
        "35.88.239.126",
        "52.36.166.22",
        "54.185.15.53",
        "35.161.247.157",
        "52.39.10.184",
        "34.216.71.7",
        "35.88.62.92",
        "18.236.244.236",
        "34.219.131.94",
};

    public MalortDevNetParams() {
        super(DEVNET_NAME, "yTrnnUkRXADy7C3CizQ1uCu1Qtd2WAmxNH", 20001,
                MASTERNODES, true, 70220);
        dnsSeeds = MASTERNODES;

        // minimumDifficultyBlocks = 1000;
        DIP0024BlockHeight = 2200;

        llmqChainLocks = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqForInstantSend = LLMQParameters.LLMQType.LLMQ_DEVNET;
        llmqTypePlatform = LLMQParameters.LLMQType.LLMQ_DEVNET;
    }

    private static MalortDevNetParams instance;

    public static MalortDevNetParams get() {
        if(instance == null) {
            instance = new MalortDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
