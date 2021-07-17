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

public class SchnappsDevNetParams extends DevNetParams {

    private static String DEVNET_NAME = "schnapps";

    private static String [] MASTERNODES = new String [] {
            "34.220.142.11",
            "35.165.226.18",
            "34.213.172.28",
            "54.186.163.198",
            "35.165.78.181",
            "34.208.97.20",
            "18.236.96.101",
            "34.208.108.211",
            "34.221.197.92"
    };

    public SchnappsDevNetParams() {
        super(DEVNET_NAME, "yTrnnUkRXADy7C3CizQ1uCu1Qtd2WAmxNH", 20001,
                MASTERNODES, true, 70218);
        dnsSeeds = MASTERNODES;
        //TODO: Normally this is the way to set up dnsSeeds
        //dnsSeeds = new String[] { "seed-1.schnapps.networks.dash.org" };
    }

    private static SchnappsDevNetParams instance;

    public static SchnappsDevNetParams get() {
        if(instance == null) {
            instance = new SchnappsDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
