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

    private static String [] MASTERNODES = new String [] {
            "18.237.82.208", "54.149.106.153", "54.202.230.77",
            "18.237.204.156", "54.186.232.69", "54.218.124.137",
            "52.32.58.19", "54.212.228.74","34.219.23.213",
            "34.222.212.20", "34.220.26.32", "34.209.79.74",
            "35.165.164.201",
    };

    public MobileDevNetParams() {
        super(DEVNET_NAME, "yQuAu9YAMt4yEiXBeDp3q5bKpo7jsC2eEj", 20001,
                MASTERNODES, true, 70216);
    }

    private static MobileDevNetParams instance;

    public static MobileDevNetParams get() {
        if(instance == null) {
            instance = new MobileDevNetParams();
            add(instance);
        }
        return instance;
    }
}
