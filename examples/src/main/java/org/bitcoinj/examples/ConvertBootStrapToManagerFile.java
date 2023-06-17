package org.bitcoinj.examples;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;

import java.util.concurrent.ExecutionException;


public class ConvertBootStrapToManagerFile {

    static Context context;
    static PeerGroup peerGroup;
    static BlockChain blockChain;
    private static void initContext(NetworkParameters params) throws BlockStoreException {
        context = new Context(params);
        if (blockChain == null) {
            blockChain = new BlockChain(context, new MemoryBlockStore(params));
        }
        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);
        context.initDash(true, true);

        context.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
    }
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("ConvertBootStrapToManagerFile network mnlistdiff qrinfo");
            System.out.println("  one or more arguments are missing!");
            return;
        }
        BriefLogFormatter.init();
        String network = args[0];
        String mnlistdiffFilename = args[1];
        String qrinfoFilename = args[2];
        int fileVersion = 5;
        if (args.length == 4) {
            fileVersion = Integer.parseInt(args[3]);
        }

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
            initContext(params);
            //Context context = Context.getOrCreate(params);
            //context.initDash(true, true);
            context.initDashSync(".", "convert");
            context.masternodeListManager.setBootstrap(mnlistdiffFilename, qrinfoFilename, fileVersion);
            context.masternodeListManager.resetMNList(true, true);
            try {
                context.masternodeListManager.waitForBootstrapLoaded();
                context.masternodeListManager.save();

                Thread.sleep(5000);
                System.out.println("Save complete.");
            } catch (ExecutionException e) {
                System.out.println("Failed to load bootstrap");
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                System.out.println("Failed to load bootstrap");
                throw new RuntimeException(e);
            }
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }

    }
}
