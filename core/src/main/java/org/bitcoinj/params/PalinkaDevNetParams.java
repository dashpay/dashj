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
        "54.202.53.159",
        "52.12.118.171",
        "34.215.142.194",
        "34.219.48.227",
        "54.244.202.193",
        "34.223.252.101",
        "54.188.0.113",
        "54.200.18.37",
        "34.213.11.229",
        "34.220.174.81",
        "52.41.24.77",
        "54.212.141.136",
        "35.160.250.92",
        "54.201.89.193",
        "34.210.163.222",
        "35.160.106.196",
        "52.12.30.86",
        "34.221.191.222",
        "34.222.121.119",
        "34.210.88.147",
        "54.213.48.134",
        "54.190.44.57",
        "34.222.51.72",
        "54.190.3.115",
        "34.217.147.161",
        "34.219.189.35",
        "35.167.163.127",
        "34.217.146.164",
        "34.221.71.234",
        "35.167.59.118",
        "54.202.80.196",
        "50.112.24.35",
        "34.211.56.71",
        "34.223.224.86",
        "34.214.36.99",
        "34.219.88.217",
        "34.222.23.119",
        "54.191.148.19",
        "34.221.5.65",
        "54.185.253.219",
        "34.221.31.196",
        "34.215.217.86",
        "54.218.124.141",
        "52.10.180.189",
        "54.201.35.213",
        "34.209.74.195",
        "54.185.161.36",
        "35.162.124.249",
        "54.184.65.137",
        "34.211.156.78",
        "54.68.190.64",
        "52.39.11.80",
        "54.213.96.104",
        "54.202.45.125",
        "34.214.73.87",
    };

    public PalinkaDevNetParams() {
        super(DEVNET_NAME, "yYZh9ZGhvr4TAid4BZ9XZ5u6HU2LKkY4XV", 20001,
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
