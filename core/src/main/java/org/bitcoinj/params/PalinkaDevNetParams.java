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
            "52.12.2.242",
            "34.219.86.70",
            "34.209.152.117",
            "54.245.182.120",
            "34.219.179.226",
            "54.245.160.193",
            "34.215.219.45",
            "34.221.212.4",
            "34.220.98.95",
            "52.35.250.121",
            "54.202.75.194",
            "34.222.151.181",
            "52.12.235.213"
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
