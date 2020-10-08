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
            "52.13.103.87",
            "34.222.93.50",
            "52.12.115.4",
            "52.24.159.236",
            "35.167.229.36",
            "34.220.91.64",
            "34.214.159.94",
            "34.219.147.102",
            "54.185.66.134",
            "52.27.96.24",
            "34.222.63.37",
            "34.216.79.150",
            "34.219.177.88",
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
