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
            "18.237.88.106", "34.212.69.250", "52.13.161.110",
            "52.24.198.145", "54.244.36.140", "35.166.226.20", 
            "54.214.117.53", "54.212.114.204", "52.13.92.167",
            "54.187.95.200", "54.185.222.73", "34.220.134.51",
            "34.214.143.249", "35.162.23.56", "18.237.190.60", 
            "52.36.80.182", "54.71.60.103", "52.24.232.168",
            "18.236.254.166", "54.187.215.38", "35.166.207.191",
            "52.39.238.5", "34.221.141.147", "54.200.212.117",
            "54.187.178.138", "54.189.154.241", "52.24.203.28",
            "52.33.207.244", "34.220.38.116", "34.221.111.92",
            "54.149.99.4", "54.245.142.243", "54.149.72.137",
            "34.222.49.202", "34.215.175.142", "45.32.136.14",
            "52.43.158.21", "34.212.137.130","54.184.225.122",
            "34.222.130.18","34.223.227.233", "34.211.141.125",
            "54.188.94.141", "34.222.164.12", "34.215.228.175",
            "35.166.50.163", "52.34.219.71", "54.245.4.139",
            "54.200.82.127", "34.212.245.91", "34.215.98.56"
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

    @Override
    public String[] getDefaultMasternodeList() {
        return MASTERNODES;
    }
}
