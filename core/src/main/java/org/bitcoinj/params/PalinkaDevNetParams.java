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
            "54.214.223.133",
            "34.222.170.91",
            "54.187.180.191",
            "54.218.238.240",
            "54.212.184.233",
            "34.217.123.47",
            "35.160.208.146",
            "54.213.188.235",
            "34.216.205.76",
            "52.25.119.181",
            "34.217.44.188",
            "34.219.217.150",
            "34.216.221.94"
    };

    public PalinkaDevNetParams() {
        super(DEVNET_NAME, "yMtULrhoxd8vRZrsnFobWgRTidtjg2Rnjm", 20001,
                MASTERNODES, true, 70215);
        dnsSeeds = new String[] { "seed-1.palinka.networks.dash.org" };
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
