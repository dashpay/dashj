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

public class MobileDevNetParams extends DevNetParams {

    private static String DEVNET_NAME = "mobile";

    public static String [] DNS_SEEDERS = new String [] {
            "seed.mobile.networks.dash.org",
    };

    public static String [] MASTERNODES = new String [] {
            "34.222.170.127",
            "54.184.198.68",
            "34.216.139.17",
            "54.218.124.42",
            "52.26.228.25",
            "34.213.26.85",
            "34.219.38.82",
            "34.217.61.209",
            "54.184.183.120",
            "54.202.130.163",
            "54.214.177.132",
            "54.212.91.206",
            "34.219.148.253",
    };

    public MobileDevNetParams() {
        super(DEVNET_NAME, "yQuAu9YAMt4yEiXBeDp3q5bKpo7jsC2eEj", 20001,
                DNS_SEEDERS, true, 70216);
    }

    private static MobileDevNetParams instance;

    public static MobileDevNetParams get() {
        if(instance == null) {
            instance = new MobileDevNetParams();
            add(instance);
        }
        return instance;
    }

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
