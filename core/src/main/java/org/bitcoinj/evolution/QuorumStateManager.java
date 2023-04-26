/*
 * Copyright 2022 Dash Core Group
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

package org.bitcoinj.evolution;

import com.google.common.util.concurrent.SettableFuture;
import org.bitcoinj.core.Peer;
import org.bitcoinj.quorums.QuorumRotationInfo;

import javax.annotation.Nullable;

public interface QuorumStateManager {
    void save();
    boolean isLoadedFromFile();

    void processDiffMessage(Peer peer, SimplifiedMasternodeListDiff mnlistdiff, boolean isLoadingBootStrap);

    void processDiffMessage(Peer peer, QuorumRotationInfo qrinfo, boolean isLoadingBootStrap, @Nullable SettableFuture<Boolean> opComplete);
}
