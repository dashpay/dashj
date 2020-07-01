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
            "54.188.88.39",
            "52.26.143.147",
            "54.200.90.174",
            "54.202.16.138",
            "34.219.140.22",
            "54.214.219.223",
            "18.236.222.182",
            "34.221.194.153",
            "54.218.125.120",
            "34.209.161.52",
            "54.188.128.22",
            "52.40.76.97",
            "18.236.119.178"
    };

    public PalinkaDevNetParams() {
        super(DEVNET_NAME, "yMtULrhoxd8vRZrsnFobWgRTidtjg2Rnjm", 20001,
                MASTERNODES, true, 70215);
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
