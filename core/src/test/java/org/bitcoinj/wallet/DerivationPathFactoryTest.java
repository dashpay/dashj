package org.bitcoinj.wallet;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DerivationPathFactoryTest {

    private static final NetworkParameters UNITTEST = UnitTestParams.get();
    private static final NetworkParameters MAINNET = MainNetParams.get();

    // m / 9' / 5' / 3' / 0' - 1000 DASH for masternode
    public static final ImmutableList<ChildNumber> MASTERNODE_HOLDINGS_PATH = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.FIVE_HARDENED, new ChildNumber(3, true), ChildNumber.ZERO_HARDENED);
    public static final ImmutableList<ChildNumber> MASTERNODE_HOLDINGS_PATH_TESTNET = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.ONE_HARDENED, new ChildNumber(3, true), ChildNumber.ZERO_HARDENED);

    // m / 9' / 5' / 3' / 1' - Masternode Voting Path
    public static final ImmutableList<ChildNumber> PROVIDER_VOTING_PATH = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.FIVE_HARDENED, new ChildNumber(3, true), ChildNumber.ONE_HARDENED);
    public static final ImmutableList<ChildNumber> PROVIDER_VOTING_PATH_TESTNET = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.ONE_HARDENED, new ChildNumber(3, true), ChildNumber.ONE_HARDENED);

    // m / 9' / 5' / 3' / 2' - Masternode Owner Path
    public static final ImmutableList<ChildNumber> PROVIDER_OWNER_PATH = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.FIVE_HARDENED, new ChildNumber(3, true), new ChildNumber(2, true));
    public static final ImmutableList<ChildNumber> PROVIDER_OWNER_PATH_TESTNET = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.ONE_HARDENED, new ChildNumber(3, true), new ChildNumber(2, true));

    // m / 9' / 5' / 3' / 3' - Masternode Operator Path
    public static final ImmutableList<ChildNumber> PROVIDER_OPERATOR_PATH = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.FIVE_HARDENED, new ChildNumber(3, true), new ChildNumber(3, true));
    public static final ImmutableList<ChildNumber> PROVIDER_OPERATOR_PATH_TESTNET = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.ONE_HARDENED, new ChildNumber(3, true), new ChildNumber(3, true));

    // m / 9' / 5' / 0' / 0' / 0' / 0'  - Blockchain Identity Path
    public static final ImmutableList<ChildNumber> BLOCKCHAIN_IDENTITY_PATH = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.FIVE_HARDENED, ChildNumber.FIVE_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED);
    public static final ImmutableList<ChildNumber> BLOCKCHAIN_IDENTITY_PATH_TESTNET = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.ONE_HARDENED, ChildNumber.FIVE_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED);

    // m / 9' / 5' / 0' / 0' / 0' / 0'  - Blockchain Identity Path
    public static final ImmutableList<ChildNumber> BLOCKCHAIN_IDENTITY_PATH_KEY_ZERO = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.FIVE_HARDENED, ChildNumber.FIVE_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED);
    public static final ImmutableList<ChildNumber> BLOCKCHAIN_IDENTITY_PATH_TESTNET_KEY_ZERO = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.ONE_HARDENED, ChildNumber.FIVE_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED, ChildNumber.ZERO_HARDENED);

    // Blockchain Identity Funding Path
    // m/9'/5'/5'/1' (mainnet)
    // m/9'/1'/5'/1' (testnet, devnets)
    public static final ImmutableList<ChildNumber> BLOCKCHAIN_IDENTITY_FUNDING_PATH = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.FIVE_HARDENED, ChildNumber.FIVE_HARDENED, ChildNumber.ONE_HARDENED);
    public static final ImmutableList<ChildNumber> BLOCKCHAIN_IDENTITY_FUNDING_PATH_TESTNET = ImmutableList.of(new ChildNumber(9, true),
            ChildNumber.ONE_HARDENED, ChildNumber.FIVE_HARDENED, ChildNumber.ONE_HARDENED);

    @Test
    public void validateMainNet() {
        DerivationPathFactory factory = new DerivationPathFactory(MAINNET);

        assertEquals(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH, factory.bip44DerivationPath(0));
        assertEquals(DeterministicKeyChain.ACCOUNT_ZERO_PATH, factory.bip32DerivationPath(0));
        assertEquals(DeterministicKeyChain.ACCOUNT_ONE_PATH, factory.bip32DerivationPath(1));

        assertEquals(MASTERNODE_HOLDINGS_PATH, factory.masternodeHoldingsDerivationPath());
        assertEquals(PROVIDER_OWNER_PATH, factory.masternodeOwnerDerivationPath());
        assertEquals(PROVIDER_VOTING_PATH, factory.masternodeVotingDerivationPath());
        assertEquals(PROVIDER_OPERATOR_PATH, factory.masternodeOperatorDerivationPath());

        assertEquals(BLOCKCHAIN_IDENTITY_PATH, factory.blockchainIdentityECDSADerivationPath());
        assertEquals(BLOCKCHAIN_IDENTITY_PATH_KEY_ZERO, factory.blockchainIdentityECDSADerivationPath(0));
        assertEquals(BLOCKCHAIN_IDENTITY_FUNDING_PATH, factory.blockchainIdentityRegistrationFundingDerivationPath());
    }

    @Test
    public void validateTestNet() {
        DerivationPathFactory factory = new DerivationPathFactory(UNITTEST);

        assertEquals(DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH_TESTNET, factory.bip44DerivationPath(0));
        assertEquals(DeterministicKeyChain.ACCOUNT_ZERO_PATH, factory.bip32DerivationPath(0));
        assertEquals(DeterministicKeyChain.ACCOUNT_ONE_PATH, factory.bip32DerivationPath(1));

        assertEquals(MASTERNODE_HOLDINGS_PATH_TESTNET, factory.masternodeHoldingsDerivationPath());
        assertEquals(PROVIDER_OWNER_PATH_TESTNET, factory.masternodeOwnerDerivationPath());
        assertEquals(PROVIDER_VOTING_PATH_TESTNET, factory.masternodeVotingDerivationPath());
        assertEquals(PROVIDER_OPERATOR_PATH_TESTNET, factory.masternodeOperatorDerivationPath());

        assertEquals(BLOCKCHAIN_IDENTITY_PATH_TESTNET, factory.blockchainIdentityECDSADerivationPath());
        assertEquals(BLOCKCHAIN_IDENTITY_PATH_TESTNET_KEY_ZERO, factory.blockchainIdentityECDSADerivationPath(0));
        assertEquals(BLOCKCHAIN_IDENTITY_FUNDING_PATH_TESTNET, factory.blockchainIdentityRegistrationFundingDerivationPath());
    }
}
