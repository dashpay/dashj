package org.dashj.kits;

import com.google.common.collect.ImmutableList;
import org.dashj.core.Context;
import org.dashj.core.NetworkParameters;
import org.dashj.core.Utils;
import org.dashj.crypto.ChildNumber;
import org.dashj.wallet.DeterministicSeed;
import org.dashj.wallet.KeyChainGroup;
import org.dashj.wallet.Wallet;

import java.io.File;
import java.security.SecureRandom;

import static org.dashj.wallet.DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS;

public class EvolutionWalletAppKit extends WalletAppKit {

    @Deprecated
    public static ImmutableList<ChildNumber> EVOLUTION_ACCOUNT_PATH = ImmutableList.of(new ChildNumber(5, true),
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
