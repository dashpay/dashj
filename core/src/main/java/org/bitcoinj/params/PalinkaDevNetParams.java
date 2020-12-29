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
            "52.89.101.172",
            "54.70.199.82",
            "34.220.50.96",
            "54.149.38.35",
            "34.211.76.12",
            "54.212.209.152",
            "52.12.203.2",
            "34.213.204.50",
            "34.217.214.198",
            "34.217.76.124",
            "54.187.21.212",
            "34.217.132.252",
            "34.222.147.170",
            "54.200.77.116",
            "34.213.41.202",
            "34.215.182.32",
            "52.41.67.136",
            "34.216.68.187",
            "34.222.212.15",
            "34.215.122.194",
            "34.215.39.205",
            "34.216.74.171",
            "54.202.184.205",
            "52.37.177.95",
            "34.221.231.203",
            "54.213.36.40",
            "34.222.251.7",
            "52.32.127.26",
            "34.220.11.0",
            "34.221.219.223",
            "18.236.231.158",
            "34.221.78.201",
            "35.166.237.40",
            "34.220.73.69",
            "54.218.229.48",
            "34.208.136.153",
            "54.186.99.233",
            "18.236.189.104",
            "34.219.37.35",
            "52.10.250.11",
            "52.41.236.248",
            "18.236.206.17"
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
