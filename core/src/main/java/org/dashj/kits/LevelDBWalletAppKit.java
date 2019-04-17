package org.dashj.kits;

import org.dashj.core.Context;
import org.dashj.core.NetworkParameters;
import org.dashj.store.BlockStore;
import org.dashj.store.BlockStoreException;
import org.dashj.store.LevelDBBlockStore;
import org.dashj.store.SPVBlockStore;

import java.io.File;

/**
 * Created by Eric on 2/23/2016.
 */
public class LevelDBWalletAppKit extends WalletAppKit {
    public LevelDBWalletAppKit(NetworkParameters params, File directory, String filePrefix) {
        super(params, directory, filePrefix);
    }

    /**
     * Override this to use a {@link BlockStore} that isn't the default of {@link SPVBlockStore}.
     */
    protected BlockStore provideBlockStore(File file) throws BlockStoreException {
        return new LevelDBBlockStore(context, file);
    }
}
