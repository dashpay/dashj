/*
 * Copyright (c) 2022 Dash Core Group
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
package org.bitcoinj.coinjoin;

import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Utils;

public class PendingDsaRequest {
    private static final int TIMEOUT = 15;
    private MasternodeAddress addr;
    private CoinJoinAccept dsa;
    private long nTimeCreated = 0;

    public PendingDsaRequest() {

    }

    public PendingDsaRequest(MasternodeAddress addr, CoinJoinAccept dsa) {
        this.addr = addr;
        this.dsa = dsa;
        nTimeCreated = Utils.currentTimeSeconds();
    }

    public CoinJoinAccept getDsa() {
        return dsa;
    }

    public MasternodeAddress getAddress() {
        return addr;
    }

    boolean isExpired() {
        return Utils.currentTimeSeconds() - nTimeCreated > TIMEOUT;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PendingDsaRequest) {
            PendingDsaRequest pdr = (PendingDsaRequest) o;
            if (o == this)
                return true;
            if (addr == null && pdr.addr != null)
                return false;
            if (dsa == null && pdr.dsa != null)
                return false;
            if (addr == pdr.addr && dsa == pdr.dsa)
                return true;
            return addr.equals(pdr.addr) && dsa.equals(pdr.dsa);
        }
        return false;
    }

    public boolean bool() {
        return !equals(new PendingDsaRequest());
    }
}
