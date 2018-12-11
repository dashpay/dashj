package org.bitcoinj.kits;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.security.SecureRandom;

import static org.bitcoinj.wallet.DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS;

public class EvolutionWalletAppKit extends WalletAppKit {

    protected DeterministicKeyChain blockchainUserKeyChain;
    protected Wallet evoUserWallet;


    public EvolutionWalletAppKit(Context context, File directory, String filePrefix, boolean liteMode) {
        super(context, directory, filePrefix, liteMode);
    }

    public EvolutionWalletAppKit(NetworkParameters params, File directory, String filePrefix, boolean liteMode) {
        super(params, directory, filePrefix, liteMode);
    }

    @Override
    protected Wallet createWallet() {
        DeterministicSeed seed;
        if (restoreFromSeed != null) {
            seed = restoreFromSeed;
        } else {

            seed = new DeterministicSeed(new SecureRandom(), DEFAULT_SEED_ENTROPY_BITS,
                    "", Utils.currentTimeSeconds());
        }

        // Create BIP32 (m/0') keychain
        DeterministicKeyChain bip32chain = new DeterministicKeyChain(seed, DeterministicKeyChain.ACCOUNT_ZERO_PATH);
        // Create BIP44 (m/44'/5'/0')
        DeterministicKeyChain bip44chain = new DeterministicKeyChain(seed, params.getId().equals(NetworkParameters.ID_MAINNET) ?
                DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH : DeterministicKeyChain.BIP44_ACCOUNT_ZERO_PATH_TESTNET);

        KeyChainGroup kcg = new KeyChainGroup(params);
        kcg.addAndActivateHDChain(bip32chain);
        kcg.addAndActivateHDChain(bip44chain); //default keychain

        blockchainUserKeyChain = new DeterministicKeyChain(seed, DeterministicKeyChain.EVOLUTION_ACCOUNT_PATH);

        KeyChainGroup evoUserKcg = new KeyChainGroup(params);
        evoUserKcg.addAndActivateHDChain(blockchainUserKeyChain);

        if (walletFactory != null) {
            evoUserWallet = walletFactory.create(params, evoUserKcg);
            return walletFactory.create(params, kcg);

        } else {
            evoUserWallet = walletFactory.create(params, evoUserKcg);
            return new Wallet(params, kcg);  // default
        }
    }

    public DeterministicKeyChain getBlockchainUserKeyChain() {
        return blockchainUserKeyChain;
    }

    public Wallet getEvoUserWallet() {
        return evoUserWallet;
    }
}
