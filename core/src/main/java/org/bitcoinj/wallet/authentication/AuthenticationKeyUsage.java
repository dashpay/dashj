/*
 * Copyright (c) 2023 Dash Core Group
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

package org.bitcoinj.wallet.authentication;

import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.IKey;
import org.bitcoinj.wallet.AuthenticationKeyChain;

import javax.annotation.Nullable;

public class AuthenticationKeyUsage {
    private final IKey key;
    private final AuthenticationKeyChain.KeyChainType type;
    private AuthenticationKeyStatus status;
    private final Sha256Hash whereUsed;
    private @Nullable MasternodeAddress address; // this may not be available

    AuthenticationKeyUsage(IKey key, AuthenticationKeyChain.KeyChainType type, AuthenticationKeyStatus status, Sha256Hash whereUsed, @Nullable MasternodeAddress address) {
        this.key = key;
        this.type = type;
        this.status = status;
        this.whereUsed = whereUsed;
        this.address = address;
    }

    public static AuthenticationKeyUsage createVoting(IKey key, Sha256Hash proRegTx, MasternodeAddress address) {
        return new AuthenticationKeyUsage(key, AuthenticationKeyChain.KeyChainType.MASTERNODE_VOTING, AuthenticationKeyStatus.CURRENT, proRegTx, address);
    }

    public static AuthenticationKeyUsage createOwner(IKey key, Sha256Hash proRegTx, MasternodeAddress address) {
        return new AuthenticationKeyUsage(key, AuthenticationKeyChain.KeyChainType.MASTERNODE_OWNER, AuthenticationKeyStatus.CURRENT, proRegTx, address);
    }

    public static AuthenticationKeyUsage createOperator(IKey key, Sha256Hash proRegTx, MasternodeAddress address) {
        return new AuthenticationKeyUsage(key, AuthenticationKeyChain.KeyChainType.MASTERNODE_OPERATOR, AuthenticationKeyStatus.CURRENT, proRegTx, address);
    }

    public static AuthenticationKeyUsage createPlatformNodeId(IKey key, Sha256Hash proRegTx, MasternodeAddress address) {
        return new AuthenticationKeyUsage(key, AuthenticationKeyChain.KeyChainType.MASTERNODE_PLATFORM_OPERATOR, AuthenticationKeyStatus.CURRENT, proRegTx, address);
    }

    public IKey getKey() {
        return key;
    }

    public AuthenticationKeyChain.KeyChainType getType() {
        return type;
    }

    public AuthenticationKeyStatus getStatus() {
        return status;
    }

    public void setStatus(AuthenticationKeyStatus status) {
        this.status = status;
    }

    public Sha256Hash getWhereUsed() {
        return whereUsed;
    }

    public @Nullable MasternodeAddress getAddress() {
        return address;
    }

    public void setAddress(@Nullable MasternodeAddress address) {
        this.address = address;
    }

    @Override
    public String toString() {
        return "hash160:" + Utils.HEX.encode(key.getPubKeyHash()) + ": " + type + " " + status + " " + address + " " + whereUsed;
    }
}
