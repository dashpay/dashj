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
package org.bitcoinj.coinjoin.utils;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.NoDestination;
import org.bitcoinj.core.TransactionDestination;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.Wallet;

public class ReserveDestination extends ReserveScript {
    //! The wallet to reserve from
    protected final Wallet wallet;
    //LegacyScriptPubKeyMan* m_spk_man{nullptr};

    //! The index of the address's key in the keypool
    protected long index = -1;
    //! The public key for the address
    protected ECKey vchPubKey;
    //! The destination
    protected TransactionDestination address;
    //! Whether this is from the internal (change output) keypool
    protected boolean internal = false;

    //! Construct a ReserveDestination object. This does NOT reserve an address yet
    public ReserveDestination(Wallet wallet) {
        this.wallet = wallet;
    }

    //! Destructor. If a key has been reserved and not KeepKey'ed, it will be returned to the keypool
    protected void finalize() {
        returnDestination();
    }

    //! Reserve an address
    public TransactionDestination getReservedDestination(boolean internal) {
        if (index == -1) {
            DeterministicKey key = wallet.freshCoinJoinKey();
            if (key == null) {
                return null;
            }
            vchPubKey = key;
            index = key.getChildNumber().i();
            this.internal = true;
        }

        return new KeyId(vchPubKey.getPubKeyHash());
    }

    //! Return reserved address
    void returnDestination() {
        // TODO tell the wallet to reserve the destination
        index = -1;
        vchPubKey = null;
        address = NoDestination.get();
    }
    //! Keep the address. Do not return it's key to the keypool when this object goes out of scope
    public void keepDestination() {
        // TODO: tell the wallt to keep the destination
        index = -1;
        vchPubKey = null;
        address = NoDestination.get();
    }

    @Override
    void keepScript() {
        keepDestination();
    }
}
