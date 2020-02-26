/*
 * Copyright 2019 Dash Core Group
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

public class EvoNetParams extends DevNetParams {

    /*
        connection parameters:
        https://dashplatform.readme.io/docs/tutorial-connecting-to-evonet-dash-core-full-node
     */
    public static String DEVNET_NAME = "evonet";
    public static String [] MASTERNODES = {
            "34.217.94.88", "34.208.173.119", "35.161.212.27",
            "34.220.12.121", "50.112.229.110", "34.222.26.118",
            "34.221.120.97", "18.237.194.152", "34.218.253.214",
            "54.200.220.68", "52.12.224.246", "18.236.79.253",
            "34.208.77.15", "54.185.99.219", "34.217.45.119",
            "52.33.191.53", "34.222.237.87", "34.209.48.117",
            "52.33.121.214", "52.13.80.40", "35.165.144.151",
            "34.217.93.253", "34.209.44.204", "34.217.89.121",
            "54.213.125.157", "34.221.121.70", "52.26.165.185",
            "54.214.211.126", "54.202.56.123", "54.245.133.124",
            "35.164.133.49", "54.218.229.66", "54.200.209.54",
            "54.186.254.185", "54.245.69.214", "54.188.117.192",
            "54.71.4.204", "54.148.62.72", "54.218.79.125",
            "54.218.245.178", "54.218.111.152", "54.203.46.145",
            "34.217.52.61", "54.187.142.253", "18.236.79.63",
            "54.212.239.148", "18.237.125.30", "34.216.223.116",
            "34.222.25.238",
    };

    public EvoNetParams() {
        super(DEVNET_NAME, "yQuAu9YAMt4yEiXBeDp3q5bKpo7jsC2eEj", 20001, MASTERNODES, true, 70215);
    }

    private static EvoNetParams instance;

    public static EvoNetParams get() {
        if(instance == null) {
            instance = new EvoNetParams();
            add(instance);
        }
        return instance;
    }
}
