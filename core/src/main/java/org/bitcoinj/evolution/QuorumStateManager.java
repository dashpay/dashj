package org.bitcoinj.evolution;

import java.io.FileNotFoundException;

public interface QuorumStateManager {
    void save() throws FileNotFoundException;
    boolean isLoadedFromFile();
    boolean notUsingBootstrapFile();
    boolean notUsingBootstrapFileAndStream();
    void setLoadingBootstrap();
    void loadBootstrapAndSync();
}
