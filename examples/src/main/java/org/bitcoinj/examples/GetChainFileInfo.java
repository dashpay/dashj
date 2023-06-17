package org.bitcoinj.examples;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;

import java.io.File;

public class GetChainFileInfo {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("GetChainFileInfo network file");
            System.out.println("  missing the filename");
        }
        BriefLogFormatter.initWithSilentBitcoinJ();
        String network = args[0];
        String chainFilename = args[1];
        NetworkParameters params;

        switch (network) {
            case "testnet":
                params = TestNet3Params.get();
                break;
            default:
                params = MainNetParams.get();
                break;
        }
        try {
            File chainFile = new File(chainFilename);

            // Setting up the BlochChain, the BlocksStore and connecting to the network.
            SPVBlockStore chainStore = new SPVBlockStore(params, chainFile);
            BlockChain chain = new BlockChain(params, chainStore);

            System.out.println("Chain File Info");
            System.out.println("Filename:  " + chainFilename);

            System.out.println("Chainhead:");
            System.out.println(chain.getChainHead());
            chainStore.close();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }
}
