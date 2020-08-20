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

    private static String DEVNET_NAME = "mobile-2";

    public static String[] DNS_SEEDERS = new String[]{
            "seed-1.mobile.networks.dash.org",
            "seed-2.mobile.networks.dash.org",
            "seed-3.mobile.networks.dash.org",
            "seed-4.mobile.networks.dash.org",
            "seed-5.mobile.networks.dash.org"
    };

    public static String[] MASTERNODES = new String[]{
            "34.217.130.113",
            "34.212.127.218",
            "34.217.109.240",
            "35.165.117.23",
            "54.185.186.244",
            "34.222.113.168",
            "54.218.48.42",
            "34.222.214.130",
            "34.212.55.24",
            "34.212.175.168",
            "34.222.50.176",
            "34.217.210.86",
            "54.190.107.64"
    };

    public MobileDevNetParams() {
        super(DEVNET_NAME, "yQuAu9YAMt4yEiXBeDp3q5bKpo7jsC2eEj", 20001,
                DNS_SEEDERS, true, 70216);
    }

    private static MobileDevNetParams instance;

    public static MobileDevNetParams get() {
        if (instance == null) {
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
