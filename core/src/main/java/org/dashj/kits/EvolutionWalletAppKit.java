package org.bitcoinj.kits;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.security.SecureRandom;

import static org.bitcoinj.wallet.DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS;

public class EvolutionWalletAppKit extends WalletAppKit {

    ImmutableList<ChildNumber> EVOLUTION_ACCOUNT_PATH = ImmutableList.of(new ChildNumber(5, true),
            ChildNumber.FIVE_HARDENED, ChildNumber.ZERO_HARDENED);

    public EvolutionWalletAppKit(Context context, File directory, String filePrefix, boolean liteMode) {
        super(context, directory, filePrefix, liteMode);
    }

    public EvolutionWalletAppKit(NetworkParameters params, File directory, String filePrefix, boolean liteMode) {
        super(params, directory, filePrefix, liteMode);
    }

    @Override
    protected Wallet createWallet() {
        KeyChainGroup kcg;
        if (restoreFromSeed != null)
            kcg = new KeyChainGroup(params, restoreFromSeed, EVOLUTION_ACCOUNT_PATH);
        else {
            kcg = new KeyChainGroup(params, new DeterministicSeed(new SecureRandom(), DEFAULT_SEED_ENTROPY_BITS,
                    "", Utils.currentTimeSeconds()), EVOLUTION_ACCOUNT_PATH);
        }

        if (walletFactory != null) {
            return walletFactory.create(params, kcg);
        } else {
            return new Wallet(params, kcg);  // default
        }
    }
}
