/*
 * Copyright (c) 2021 Dash Core Group
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

package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;

import java.util.HashMap;

public class DerivationPathFactory {

    private NetworkParameters params;
    private final ChildNumber coinType;
    private static final ChildNumber FEATURE_PURPOSE = ChildNumber.NINE_HARDENED;
    private static final ChildNumber FEATURE_PURPOSE_IDENTITIES = ChildNumber.FIVE_HARDENED;
    public static final ChildNumber FEATURE_PURPOSE_DASHPAY = new ChildNumber(15, true);

    public DerivationPathFactory(NetworkParameters params) {
        this.params = params;
        this.coinType = new ChildNumber(params.getCoinType(), true);
    }

    /** blockchain identity registration funding derivation path
     * m/9'/5'/5'/1' (mainnet)
     * m/9'/1'/5'/1' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityRegistrationFundingDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(FEATURE_PURPOSE_IDENTITIES)
                .add(ChildNumber.ONE_HARDENED)
                .build();
    }

    /** blockchain identity topup funding derivation path
     * m/9'/5'/5'/2' (mainnet)
     * m/9'/1'/5'/2' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityTopupFundingDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(FEATURE_PURPOSE_IDENTITIES)
                .add(new ChildNumber(2, true))
                .build();
    }

    /** blockchain identity invitation funding derivation path
     * m/9'/5'/5'/3' (mainnet)
     * m/9'/1'/5'/3' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> identityInvitationFundingDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(FEATURE_PURPOSE_IDENTITIES)
                .add(new ChildNumber(3, true))
                .build();
    }

    /** blockchain identity keys derivation root path (EC Keys)
     * m/9'/5'/5'/0'/0'/(blockchain identity index)' (mainnet)
     * m/9'/1'/5'/0'/0'/(blockchain identity index)' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityECDSADerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(FEATURE_PURPOSE_IDENTITIES)
                .add(ChildNumber.ZERO_HARDENED) //sub feature 0
                .add(ChildNumber.ZERO_HARDENED) //key type (0 is EC key)
                .add(ChildNumber.ZERO_HARDENED) //identity index (default to 0 for now)
                .build();
    }

    /** blockchain identity keys derivation path (EC Keys)
     * m/9'/5'/5'/0'/0'/(blockchain identity index)'/(key index)' (mainnet)
     * m/9'/1'/5'/0'/0'/(blockchain identity index)'/(key index)' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityECDSADerivationPath(int index) {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(FEATURE_PURPOSE_IDENTITIES)
                .add(ChildNumber.ZERO_HARDENED) //sub feature 0
                .add(ChildNumber.ZERO_HARDENED) //key type (0 is EC key)
                .add(ChildNumber.ZERO_HARDENED) //identity index (default to 0 for now)
                .add(new ChildNumber(index, true)) //key index (default to 0 for now)
                .build();
    }

    /** blockchain identity keys derivation root path (BLS Keys)
     * m/9'/5'/5'/0'/1'/(blockchain identity index = 0) (mainnet)
     * m/9'/1'/5'/0'/1'/(blockchain identity index = 0) (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityBLSDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(FEATURE_PURPOSE_IDENTITIES)
                .add(ChildNumber.ZERO_HARDENED) //sub feature 0
                .add(ChildNumber.ONE_HARDENED) //key type (1 is BLS key)
                .add(ChildNumber.ZERO_HARDENED) // identity index (default to 0 for now)
                .build();
    }

    /** blockchain identity keys derivation path (BLS Keys)
     * m/9'/5'/5'/0'/1'/(blockchain identity index)/(key index)' (mainnet)
     * m/9'/1'/5'/0'/1'/(blockchain identity index)/(key index)' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityBLSDerivationPath(int index) {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(FEATURE_PURPOSE_IDENTITIES)
                .add(ChildNumber.ZERO_HARDENED) //sub feature 0
                .add(ChildNumber.ONE_HARDENED) //key type (1 is BLS key)
                .add(ChildNumber.ZERO_HARDENED) // identity index (default to 0 for now)
                .add(new ChildNumber(index, true)) //key index (default to 0 for now)
                .build();
    }

    public ImmutableList<ChildNumber> masternodeHoldingsDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(3, true))
                .add(ChildNumber.ZERO_HARDENED)
                .build();
    }

    /** provider voting keys derivation path
     * m/9'/5'/3'/1' (mainnet)
     * m/9'/1'/3'/1' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> masternodeVotingDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(3, true))
                .add(ChildNumber.ONE_HARDENED)
                .build();
    }

    /** provider owner keys derivation path
     * m/9'/5'/3'/2' (mainnet)
     * m/9'/1'/3'/2' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> masternodeOwnerDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(3, true))
                .add(new ChildNumber(2, true))
                .build();
    }

    /** provider operator keys derivation path
     * m/9'/5'/3'/3' (mainnet)
     * m/9'/1'/3'/3' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> masternodeOperatorDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(3, true))
                .add(new ChildNumber(3, true))
                .build();
    }

    /** mixed coins derivation path
     * m/9'/5'/4' (mainnet)
     * m/9'/1'/4' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> coinJoinDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(4, true))
                .build();
    }

    /** provider platform keys derivation path
     * m/9'/5'/3'/4' (mainnet)
     * m/9'/1'/3'/4' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> masternodePlatformDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(3, true))
                .add(new ChildNumber(4, true))
                .build();
    }

    /** Default wallet derivation path
     * m/44'/5'/@account' (mainnet)
     * m/44'/1'/@account' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> bip44DerivationPath(int account) {
        return ImmutableList.<ChildNumber>builder()
                .add(new ChildNumber(44, true))
                .add(coinType)
                .add(new ChildNumber(account, true))
                .build();
    }

    /** Legacy wallet derivation path
     * m/@account' (mainnet, testnet, devnets)
     */
    public ImmutableList<ChildNumber> bip32DerivationPath(int account) {
        return ImmutableList.<ChildNumber>builder()
                .add(new ChildNumber(account, true))
                .build();
    }
    // keep once instance per network
    private static final HashMap<String, DerivationPathFactory> instances = new HashMap<>();

    public static DerivationPathFactory get(NetworkParameters params) {
        if(instances.get(params.getId()) == null) {
            instances.put(params.getId(), new DerivationPathFactory(params));
        }
        return instances.get(params.getId());
    }

    public NetworkParameters getParams() {
        return params;
    }
}
