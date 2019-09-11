package org.bitcoinj.kits;

import com.google.common.collect.ImmutableList;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Wallet;

import java.io.File;
import java.security.SecureRandom;

import static org.bitcoinj.wallet.DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS;

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
            kcg = KeyChainGroup.builder(params).addChain(DeterministicKeyChain.builder().seed(restoreFromSeed).accountPath(EVOLUTION_ACCOUNT_PATH).build()).build();
        else {
            kcg = KeyChainGroup.builder(params).addChain(DeterministicKeyChain.builder().random(new SecureRandom(), DEFAULT_SEED_ENTROPY_BITS).passphrase("").accountPath(EVOLUTION_ACCOUNT_PATH).build()).build();
        }

        if (walletFactory != null) {
            return walletFactory.create(params, kcg);
        } else {
            return new Wallet(params, kcg);  // default
        }
    }
}
