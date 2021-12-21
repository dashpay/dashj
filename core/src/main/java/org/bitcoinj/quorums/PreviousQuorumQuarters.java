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

package org.bitcoinj.quorums;

import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;

import java.util.ArrayList;

public class PreviousQuorumQuarters {
    public ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterHMinusC;
    public ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterHMinus2C;
    public ArrayList<ArrayList<SimplifiedMasternodeListEntry>> quarterHMinus3C;
}
