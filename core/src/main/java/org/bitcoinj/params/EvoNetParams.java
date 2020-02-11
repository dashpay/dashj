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
    public static String [] MASTERNODES = {" 18.236.131.253", "34.217.94.88", "34.208.173.119", "35.161.212.27"};

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
