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

    public ImmutableList<ChildNumber> blockchainIdentityRegistrationFundingDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(12, true))
                .build();
    }

    public ImmutableList<ChildNumber> blockchainIdentityDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(ChildNumber.FIVE_HARDENED)
                .add(ChildNumber.ZERO_HARDENED)
                .add(ChildNumber.ZERO_HARDENED)
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

    public ImmutableList<ChildNumber> masternodeVotingDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(3, true))
                .add(ChildNumber.ONE_HARDENED)
                .build();
    }

    public ImmutableList<ChildNumber> masternodeOwnerDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(3, true))
                .add(new ChildNumber(2, true))
                .build();
    }

    public ImmutableList<ChildNumber> masternodeOperatorDerivationPath() {
        return ImmutableList.<ChildNumber>builder()
                .add(FEATURE_PURPOSE)
                .add(coinType)
                .add(new ChildNumber(3, true))
                .add(new ChildNumber(3, true))
                .build();
    }

    public ImmutableList<ChildNumber> bip44DerivationPath(int account) {
        return ImmutableList.<ChildNumber>builder()
                .add(new ChildNumber(44, true))
                .add(coinType)
                .add(new ChildNumber(account, true))
                .build();
    }

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
