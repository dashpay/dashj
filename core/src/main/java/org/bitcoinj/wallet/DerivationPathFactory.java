package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;

public class DerivationPathFactory {

    private NetworkParameters params;
    private final ChildNumber coinType;
    private static final ChildNumber FEATURE_PURPOSE = ChildNumber.NINE_HARDENED;

    public DerivationPathFactory(NetworkParameters params) {
        this.params = params;
        this.coinType = new ChildNumber(params.getCoinType(), true);
    }

    /** blockchain identity registration funding derivation path
     * m/9'/5'/12' (mainnet)
     * m/9'/1'/12' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityRegistrationFundingDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(12, true))
                .build();
    }

    /** blockchain identity topup funding derivation path
     * m/9'/5'/12' (mainnet)
     * m/9'/1'/12' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityTopupFundingDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(13, true))
                .build();
    }

    /** blockchain identity keys derivation path (EC Keys)
     * m/9'/5'/5'/0'/0' (mainnet)
     * m/9'/1'/5'/0'/0' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityECDSADerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(ChildNumber.FIVE_HARDENED)
                .add(ChildNumber.ZERO_HARDENED)
                .add(ChildNumber.ZERO_HARDENED)
                .build();
    }

    /** blockchain identity keys derivation path (BLS Keys)
     * m/9'/5'/5'/0'/1' (mainnet)
     * m/9'/1'/5'/0'/1' (testnet, devnets)
     */
    public ImmutableList<ChildNumber> blockchainIdentityBLSDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(ChildNumber.FIVE_HARDENED)
                .add(ChildNumber.ZERO_HARDENED)
                .add(ChildNumber.ONE_HARDENED)
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

    // Do we need this?
    private static DerivationPathFactory instance;
    public static DerivationPathFactory get(NetworkParameters params) {
        if(instance == null) {
            instance = new DerivationPathFactory(params);
        }
        return instance;
    }
}
