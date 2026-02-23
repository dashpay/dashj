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

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.HDPath;

import java.util.Arrays;
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
    public HDPath blockchainIdentityRegistrationFundingDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                FEATURE_PURPOSE_IDENTITIES,
                ChildNumber.ONE_HARDENED));
    }

    /** blockchain identity topup funding derivation path
     * m/9'/5'/5'/2' (mainnet)
     * m/9'/1'/5'/2' (testnet, devnets)
     */
    public HDPath blockchainIdentityTopupFundingDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                FEATURE_PURPOSE_IDENTITIES,
                new ChildNumber(2, true)));
    }

    /** blockchain identity invitation funding derivation path
     * m/9'/5'/5'/3' (mainnet)
     * m/9'/1'/5'/3' (testnet, devnets)
     */
    public HDPath identityInvitationFundingDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                FEATURE_PURPOSE_IDENTITIES,
                new ChildNumber(3, true)));
    }

    /** blockchain identity keys derivation root path (EC Keys)
     * m/9'/5'/5'/0'/0'/(blockchain identity index)' (mainnet)
     * m/9'/1'/5'/0'/0'/(blockchain identity index)' (testnet, devnets)
     */
    public HDPath blockchainIdentityECDSADerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                FEATURE_PURPOSE_IDENTITIES,
                ChildNumber.ZERO_HARDENED, //sub feature 0
                ChildNumber.ZERO_HARDENED, //key type (0 is EC key)
                ChildNumber.ZERO_HARDENED)); //identity index (default to 0 for now)
    }

    /** blockchain identity keys derivation path (EC Keys)
     * m/9'/5'/5'/0'/0'/(blockchain identity index)'/(key index)' (mainnet)
     * m/9'/1'/5'/0'/0'/(blockchain identity index)'/(key index)' (testnet, devnets)
     */
    public HDPath blockchainIdentityECDSADerivationPath(int index) {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                FEATURE_PURPOSE_IDENTITIES,
                ChildNumber.ZERO_HARDENED, //sub feature 0
                ChildNumber.ZERO_HARDENED, //key type (0 is EC key)
                ChildNumber.ZERO_HARDENED, //identity index (default to 0 for now)
                new ChildNumber(index, true))); //key index (default to 0 for now)
    }

    /** blockchain identity keys derivation root path (BLS Keys)
     * m/9'/5'/5'/0'/1'/(blockchain identity index = 0) (mainnet)
     * m/9'/1'/5'/0'/1'/(blockchain identity index = 0) (testnet, devnets)
     */
    public HDPath blockchainIdentityBLSDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                FEATURE_PURPOSE_IDENTITIES,
                ChildNumber.ZERO_HARDENED, //sub feature 0
                ChildNumber.ONE_HARDENED, //key type (1 is BLS key)
                ChildNumber.ZERO_HARDENED)); // identity index (default to 0 for now)
    }

    /** blockchain identity keys derivation path (BLS Keys)
     * m/9'/5'/5'/0'/1'/(blockchain identity index)/(key index)' (mainnet)
     * m/9'/1'/5'/0'/1'/(blockchain identity index)/(key index)' (testnet, devnets)
     */
    public HDPath blockchainIdentityBLSDerivationPath(int index) {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                FEATURE_PURPOSE_IDENTITIES,
                ChildNumber.ZERO_HARDENED, //sub feature 0
                ChildNumber.ONE_HARDENED, //key type (1 is BLS key)
                ChildNumber.ZERO_HARDENED, // identity index (default to 0 for now)
                new ChildNumber(index, true))); //key index (default to 0 for now)
    }

    public HDPath masternodeHoldingsDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                new ChildNumber(3, true),
                ChildNumber.ZERO_HARDENED));
    }

    /** provider voting keys derivation path
     * m/9'/5'/3'/1' (mainnet)
     * m/9'/1'/3'/1' (testnet, devnets)
     */
    public HDPath masternodeVotingDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                new ChildNumber(3, true),
                ChildNumber.ONE_HARDENED));
    }

    /** provider owner keys derivation path
     * m/9'/5'/3'/2' (mainnet)
     * m/9'/1'/3'/2' (testnet, devnets)
     */
    public HDPath masternodeOwnerDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                new ChildNumber(3, true),
                new ChildNumber(2, true)));
    }

    /** provider operator keys derivation path
     * m/9'/5'/3'/3' (mainnet)
     * m/9'/1'/3'/3' (testnet, devnets)
     */
    public HDPath masternodeOperatorDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                new ChildNumber(3, true),
                new ChildNumber(3, true)));
    }

    /** mixed coins derivation path
     * m/9'/5'/4'/account' (mainnet)
     * m/9'/1'/4'/account (testnet, devnets)
     */
    public HDPath coinJoinDerivationPath(int account) {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                new ChildNumber(4, true),
                new ChildNumber(account, true)));
    }

    /** provider platform keys derivation path
     * m/9'/5'/3'/4' (mainnet)
     * m/9'/1'/3'/4' (testnet, devnets)
     */
    public HDPath masternodePlatformDerivationPath() {
        return HDPath.of(Arrays.asList(
                FEATURE_PURPOSE,
                coinType,
                new ChildNumber(3, true),
                new ChildNumber(4, true)));
    }

    /** Default wallet derivation path
     * m/44'/5'/@account' (mainnet)
     * m/44'/1'/@account' (testnet, devnets)
     */
    public HDPath bip44DerivationPath(int account) {
        return HDPath.of(Arrays.asList(
                new ChildNumber(44, true),
                coinType,
                new ChildNumber(account, true)));
    }

    /** Legacy wallet derivation path
     * m/@account' (mainnet, testnet, devnets)
     */
    public HDPath bip32DerivationPath(int account) {
        return HDPath.of(Arrays.asList(
                new ChildNumber(account, true)));
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