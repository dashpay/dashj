/*
 * Copyright 2020 Dash Core Group
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

public class PalinkaDevNetParams extends DevNetParams {

    private static String DEVNET_NAME = "palinka";

    private static String [] MASTERNODES = new String [] {
            "54.213.104.142",
            "35.166.218.241",
            "34.220.185.186",
            "52.34.170.173",
            "34.209.17.188",
            "54.202.65.107",
            "35.162.26.9",
            "34.221.120.157",
            "34.211.82.76",
            "18.236.221.244",
            "34.221.152.195",
            "34.223.236.172",
            "54.187.237.108",
            "34.211.227.165",
            "52.43.2.66",
            "54.70.41.101",
            "34.212.177.62",
            "54.189.195.70",
            "54.201.1.97",
            "34.218.48.84",
            "35.155.198.247",
            "34.217.126.174",
            "34.214.174.215",
            "34.223.1.1",
            "34.216.87.26",
            "18.236.128.42",
            "54.70.59.107",
            "34.219.104.11",
            "34.219.188.44",
            "54.218.7.240",
            "18.236.173.38",
            "54.184.16.202",
            "34.212.251.172",
            "54.218.76.245",
            "54.187.249.82",
            "34.213.180.200",
            "35.165.8.80",
            "18.237.12.238",
            "54.191.206.78",
            "34.219.208.105",
            "54.218.222.184"
    };

    public PalinkaDevNetParams() {
        super(DEVNET_NAME, "yMtULrhoxd8vRZrsnFobWgRTidtjg2Rnjm", 20001,
                MASTERNODES, true, 70215);
        dnsSeeds = MASTERNODES;
        //TODO: Normally this is the way to set up dnsSeeds
        //dnsSeeds = new String[] { "seed-1.palinka.networks.dash.org" };
    }

    private static PalinkaDevNetParams instance;

    public static PalinkaDevNetParams get() {
        if(instance == null) {
            instance = new PalinkaDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
