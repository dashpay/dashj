package org.bitcoinj.quorums;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.evolution.SimplifiedMasternodesTest;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.ThreeThreeThreeDevNetParams;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FlatDB;
import org.bitcoinj.store.SPVBlockStore;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class QuorumRotationStateTest {

    static Context context;
    static NetworkParameters PARAMS;
    static MainNetParams MAINPARAMS;
    static PeerGroup peerGroup;
    static BlockChain blockChain;

    @BeforeClass
    public static void startup() throws BlockStoreException {
        MAINPARAMS = MainNetParams.get();
        PARAMS = ThreeThreeThreeDevNetParams.get();
        initContext(PARAMS);
    }

    private static void initContext(NetworkParameters params) throws BlockStoreException {
        context = new Context(params);
        if (blockChain == null) {
            blockChain = new BlockChain(context, new SPVBlockStore(params, new File(SimplifiedMasternodesTest.class.getResource("devnet-333.spvchain").getFile())));
        }
        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);
        context.initDash(true, true);

        context.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
    }

    // this is not supported yet
    @Test
    public void loadFromBootStrapFileV3() throws BlockStoreException {
        URL datafile = getClass().getResource("qrinfo--1-24868.dat");
        initContext(PARAMS);

        SimplifiedMasternodeListManager manager = new SimplifiedMasternodeListManager(context);
        context.setMasternodeListManager(manager);
        SimplifiedMasternodeListManager.setBootStrapFilePath(datafile.getPath(), SimplifiedMasternodeListManager.QUORUM_ROTATION_FORMAT_VERSION);

        manager.resetMNList(true, true);

        try {
            SimplifiedMasternodeListManager.bootStrapLoaded.get();
            assertEquals(24856, manager.getMasternodeList().getHeight());
        } catch (InterruptedException | ExecutionException x) {
            fail("unable to load bootstrap file");
        }

    }

    // not supported yet, due to problems with Context.masternodeListManager dependencies
    @Test
    public void loadQuorumRotationStateFromFile() throws Exception {
        URL datafile = getClass().getResource("devnet-333.mnlist");
        FlatDB<SimplifiedMasternodeListManager> db = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true);

        SimplifiedMasternodeListManager managerDefaultNames = new SimplifiedMasternodeListManager(Context.get());
        managerDefaultNames.setBlockChain(blockChain, blockChain, null, context.quorumManager, context.quorumSnapshotManager);
        assertTrue(db.load(managerDefaultNames));

        SimplifiedMasternodeListManager managerSpecific = new SimplifiedMasternodeListManager(Context.get());
        managerSpecific.setBlockChain(blockChain, blockChain, null, context.quorumManager, context.quorumSnapshotManager);
        FlatDB<SimplifiedMasternodeListManager> db2 = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true, managerSpecific.getDefaultMagicMessage(), 3);
        assertTrue(db2.load(managerSpecific));

        //check to make sure that they have the same number of masternodes
        assertEquals(managerDefaultNames.getMasternodeList().size(), managerSpecific.getMasternodeList().size());

        //load a file with version 3, expecting version 4
        SimplifiedMasternodeListManager managerSpecificFail = new SimplifiedMasternodeListManager(Context.get());
        managerSpecificFail.setBlockChain(blockChain, blockChain, null, context.quorumManager, context.quorumSnapshotManager);
        FlatDB<SimplifiedMasternodeListManager> db3 = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true, managerSpecific.getDefaultMagicMessage(), 4);
        assertFalse(db3.load(managerSpecificFail));
    }
}
