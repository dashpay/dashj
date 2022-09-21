package org.bitcoinj.evolution;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.params.JackDanielsDevNetParams;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.ThreeThreeThreeDevNetParams;
import org.bitcoinj.quorums.QuorumRotationInfo;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FlatDB;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.store.SPVBlockStore;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Created by hashengineering on 11/26/18.
 */
public class QuorumRotationStateTest {

    static Context context;
    static NetworkParameters PARAMS;
    static MainNetParams MAINPARAMS;
    static DevNetParams DEVNETPARAMS;
    static PeerGroup peerGroup;
    static byte[] txdata;
    static BlockChain blockChain;

    @BeforeClass
    public static void startup() throws BlockStoreException {
        MAINPARAMS = MainNetParams.get();
        PARAMS = TestNet3Params.get();
        DEVNETPARAMS = JackDanielsDevNetParams.get();
        initContext(MAINPARAMS);

        //PeerGroup peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);
        //txdata = Utils.HEX.decode("0300090001d4ad073ec40da120d28a47164753f4f5ad80d0dc3b918b39223d36ebdfacdef6000000006b483045022100a65429d4f2ab2df58cafdaaffe874ef260f610e068e89a4455fbf92261156bb7022015733ae5aef3006fd5781b91f97ca1102edf09e9383ca761e407c619d13db7660121034c1f31446c5971558b9027499c3678483b0deb06af5b5ccd41e1f536af1e34cafeffffff0200e1f50500000000016ad2d327cc050000001976a9141eccbe2508c7741d2e4c517f87565e7d477cfbbc88ac000000002201002369fced72076b33e25c5ca31efb605037e3377c8e1989eb9ec968224d5e22b4");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";
    }

    private static void initContext(NetworkParameters params) throws BlockStoreException {
        context = new Context(params);
        if (blockChain == null) {
            blockChain = new BlockChain(context, new SPVBlockStore(params, new File(Objects.requireNonNull(QuorumRotationStateTest.class.getResource("mainnet.spvchain")).getPath())));
        }
        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);
        context.initDash(true, true);

        context.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
    }

    @Test
    public void processQRInfoMessage() throws BlockStoreException, IOException {
        // this is for mainnet
        URL datafile = Objects.requireNonNull(getClass().getResource("QRINFO_0_1739226.dat"));

        initContext(MAINPARAMS);

        InputStream stream = Files.newInputStream(new File(datafile.getPath()).toPath());

        byte [] buffer = new byte[(int) stream.available()];
        //noinspection ResultOfMethodCallIgnored
        stream.read(buffer);

        SimplifiedMasternodeListManager manager = new SimplifiedMasternodeListManager(context);
        context.setMasternodeListManager(manager);

        QuorumRotationInfo qrinfo = new QuorumRotationInfo(context.getParams(), buffer);

        manager.processDiffMessage(null, qrinfo, false);


        assertEquals(1738936, manager.getQuorumListAtTip(context.getParams().getLlmqDIP0024InstantSend()).getHeight());
    }
}
