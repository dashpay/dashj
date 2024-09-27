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
import org.bitcoinj.wallet.WalletEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReserveDestination extends ReserveScript {
    private static Logger log = LoggerFactory.getLogger(ReserveDestination.class);
    //! The wallet to reserve from
    protected final WalletEx wallet;

    //! The index of the address's key in the keypool
    protected long index = -1;
    //! The public key for the address
    protected ECKey vchPubKey;
    //! The destination
    protected TransactionDestination address;
    //! Whether this is from the internal (change output) keypool
    protected boolean internal = false;

    //! Construct a ReserveDestination object. This does NOT reserve an address yet
    public ReserveDestination(WalletEx wallet) {
        this.wallet = wallet;
    }

    //! Reserve an address
    public TransactionDestination getReservedDestination(boolean internal) {
        if (index == -1) {
            DeterministicKey key = wallet.getCoinJoin().getUnusedKey();
            if (key == null) {
                return null;
            }
            vchPubKey = key;
            index = key.getChildNumber().i();
            this.internal = true;
        }

        address = KeyId.fromBytes(vchPubKey.getPubKeyHash());
        return address;
    }

    //! Return reserved address
    void returnDestination() {
        // TODO tell the wallet to reserve the destination
        if (vchPubKey != null) {
            if (vchPubKey instanceof DeterministicKey) {
                wallet.getCoinJoin().addUnusedKey((DeterministicKey) vchPubKey);
            } else {
                log.warn("cannot return key: {}", vchPubKey);
            }
        } else if (address instanceof KeyId){
            wallet.getCoinJoin().addUnusedKey((KeyId) address);
        } else if (!(address instanceof NoDestination)) {
            log.warn("cannot return key: {}", address);
        }
        index = -1;
        vchPubKey = null;
        address = NoDestination.get();
    }
    //! Keep the address. Do not return it's key to the keypool when this object goes out of scope
    public void keepDestination() {
        // TODO: tell the wallet to keep the destination
        // TODO tell the wallet to reserve the destination
        if (vchPubKey != null) {
            if (vchPubKey instanceof DeterministicKey) {
                wallet.getCoinJoin().removeUnusedKey(KeyId.fromBytes(vchPubKey.getPubKeyHash()));
            }
        } else if (address instanceof KeyId){
            wallet.getCoinJoin().removeUnusedKey((KeyId) address);
        } else if (!(address instanceof NoDestination)) {
            log.warn("cannot keep key: {}", address);
        }
        index = -1;
        vchPubKey = null;
        address = NoDestination.get();
    }

    @Override
    void keepScript() {
        keepDestination();
    }
}
