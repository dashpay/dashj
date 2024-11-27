package org.bitcoinj.quorums;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.SporkId;
import org.bitcoinj.core.Utils;
import org.bitcoinj.evolution.SimplifiedMasternodeListManager;
import org.bitcoinj.manager.DashSystem;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FlatDB;
import org.bitcoinj.store.SPVBlockStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChainLocksHandlerTest {
    ChainLocksHandler chainLocksHandler;
    NetworkParameters params = TestNet3Params.get();
    Context context = new Context(params);
    DashSystem system;
    BlockStore blockStore;
    BlockChain blockChain;
    PeerGroup peerGroup;
    String blockchainFile = "testnet-with-tip-905775.spvchain";
    String mnlistFile = "testnet-905558-70230.mnlist";

    byte[] clsigData = Utils.HEX.decode("2fd20d00b5232b4c4c97c95831208bb665148e82ce1a53bbcf9c48911579ac32e80000008c07ad4582ccf69a5583bcd3bb33e756ca680abd36b7879c9bf757d9803990c728861f33d25441afeca358894e346517030e19fbb3db4397f75220ace49d3fb19ec269dbac9b386a26d14c81e714ece3ce588d26d22fc7126778ebf6b101ad60");
    ChainLockSignature clsig;

    @Before
    public void setUp() throws BlockStoreException {
        initContext();
        clsig = new ChainLockSignature(params, clsigData, false);
    }

    @After
    public void tearDown() throws BlockStoreException {
        system.close();
        system.remove();
        blockStore.close();
    }

    void initContext() throws BlockStoreException {
        if (context == null || !context.getParams().equals(params))
            context = new Context(params);
        blockStore = new SPVBlockStore(params, new File(Objects.requireNonNull(getClass().getResource(blockchainFile)).getPath()));

        blockChain = new BlockChain(context, blockStore);
        system = new DashSystem(context);
        system.initDash(true, true);
        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);

        SimplifiedMasternodeListManager manager = system.masternodeListManager;
        URL mnlistManagerFile = Objects.requireNonNull(getClass().getResource(mnlistFile));
        FlatDB<SimplifiedMasternodeListManager> db2 = new FlatDB<>(Context.get(), mnlistManagerFile.getFile(), true, manager.getDefaultMagicMessage(), 5);
        assertTrue(db2.load(manager));

        system.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
        system.sporkManager.processSporkForUnitTesting(SporkId.SPORK_19_CHAINLOCKS_ENABLED);

        chainLocksHandler = system.chainLockHandler;
        chainLocksHandler.checkActiveState();
    }
    @Test
    public void processChainLockTest() {
        chainLocksHandler.start();
        chainLocksHandler.processChainLockSignature(null, clsig);
        assertEquals(905775, chainLocksHandler.getBestChainLockBlockHeight());
        assertEquals(905775, chainLocksHandler.getBestChainLockBlock().getHeight());
        assertEquals(Sha256Hash.wrap("000000e832ac791591489ccfbb531ace828e1465b68b203158c9974c4c2b23b5"), chainLocksHandler.getBestChainLockBlock().getHeader().getHash());
        chainLocksHandler.stop();
    }

    @Test
    public void serializationTest() {
        URL datafile = Objects.requireNonNull(getClass().getResource("testnet-block-905773.chainlocks"));
        FlatDB<ChainLocksHandler> clh = new FlatDB<>(context, datafile.getFile(), true);
        clh.load(chainLocksHandler);

        assertEquals(905773, chainLocksHandler.getBestChainLockBlockHeight());
        assertEquals(905773, chainLocksHandler.getBestChainLockBlock().getHeight());
        assertEquals(Sha256Hash.wrap("0000012b464fd5e05164dc7eb7aae34980b42ef3fdaab1c66f583e9d54af073d"), chainLocksHandler.getBestChainLockBlock().getHeader().getHash());
    }
}
