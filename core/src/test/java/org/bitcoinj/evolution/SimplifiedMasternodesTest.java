package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.params.MainNetParams;
import static org.junit.Assert.*;

import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.ThreeThreeThreeDevNetParams;
import org.bitcoinj.quorums.SimplifiedQuorumList;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FlatDB;
import org.bitcoinj.store.MemoryBlockStore;

import org.junit.BeforeClass;
import org.junit.Test;

import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Created by hashengineering on 11/26/18.
 */
public class SimplifiedMasternodesTest {

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
        DEVNETPARAMS = ThreeThreeThreeDevNetParams.get();
        initContext(PARAMS);

        //PeerGroup peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);
        txdata = Utils.HEX.decode("0300090001d4ad073ec40da120d28a47164753f4f5ad80d0dc3b918b39223d36ebdfacdef6000000006b483045022100a65429d4f2ab2df58cafdaaffe874ef260f610e068e89a4455fbf92261156bb7022015733ae5aef3006fd5781b91f97ca1102edf09e9383ca761e407c619d13db7660121034c1f31446c5971558b9027499c3678483b0deb06af5b5ccd41e1f536af1e34cafeffffff0200e1f50500000000016ad2d327cc050000001976a9141eccbe2508c7741d2e4c517f87565e7d477cfbbc88ac000000002201002369fced72076b33e25c5ca31efb605037e3377c8e1989eb9ec968224d5e22b4");         //"01000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84";
    }

    private static void initContext(NetworkParameters params) throws BlockStoreException {
        context = new Context(params);
        if (blockChain == null) {
            blockChain = new BlockChain(context, new MemoryBlockStore(params));
        }
        peerGroup = new PeerGroup(context.getParams(), blockChain, blockChain);
        context.initDash(true, true);

        context.setPeerGroupAndBlockChain(peerGroup, blockChain, blockChain);
    }

    @Test
    public void merkleRoots() throws UnknownHostException
    {
        ArrayList<SimplifiedMasternodeListEntry> entries = new ArrayList<SimplifiedMasternodeListEntry>(15);
        for (int i = 0; i < 15; i++) {
            SimplifiedMasternodeListEntry smle = new SimplifiedMasternodeListEntry(PARAMS);
            smle.proRegTxHash = Sha256Hash.wrap(String.format("%064x", i));
            smle.confirmedHash = Sha256Hash.wrap(String.format("%064x", i));

            byte [] ip = {0, 0, 0, (byte)i};
            smle.service = new MasternodeAddress(InetAddress.getByAddress(ip), i);

            byte [] skBuf = new byte[BLSSecretKey.BLS_CURVE_SECKEY_SIZE];
            skBuf[0] = (byte)i;
            BLSSecretKey sk = new BLSSecretKey(skBuf);


            smle.pubKeyOperator = new BLSLazyPublicKey(sk.GetPublicKey());
            smle.keyIdVoting = new KeyId(Utils.reverseBytes(Utils.HEX.decode(String.format("%040x", i))));
            smle.isValid = true;

            entries.add(smle);
        }

        String [] expectedHashes = {
                "373b549f6380d8f7b04d7b04d7c58a749c5cbe3bf41536785ba819879c4870f1",
                "3a1010e28226558560e5296bcee6bf0b9b963b73a1514f5aa2885e270f6b90c1",
                "85d3d93b28689128daf3a41d706ae5002f447b9b6372776f0ca9d53b31146884",
                "8930eee6bd2e7971a7090edfb79f74c00a12280e59adfc2cc99d406a01e368f9",
                "dc2e69caa0ef97e8f5cf40a9530641bd4933dd8c9ad533054537728f7e5f58c2",
                "3e4a0e0a0d2ed397fa27221de3047de21f50d17d0ba43738cbdb9fee96c7cb46",
                "eb18476a1496e1cb912b1d4dd93314b78c6a679d83cae8e144a717b967dc4b8c",
                "6c0d01fa40ac11d7b523facd2bf5632c83f7e4df3f60fd1b364ea90f6c852156",
                "c9e3e69d54e6e95b280ae102593fe114cf4620fa89dd88da1a146ada08815d68",
                "1023f67f735e8e9403d5f083e7a17489619b1790feac4f6b133e9dda15999ae6",
                "5d5fc77944f7c72df236a5baf460c7b9a947144d54d0953521f1494c8a2f7aaa",
                "ac7db66820de3c7506f8c6415fd352e36ac5f27c6adbdfb74de3e109d0d277df",
                "cbc25ca965d0fa69a1fdc1d796b8ee2726a0e2137414e92fb9541630e3189901",
                "ac9934c4049ae952d41fb38e7e9659a558a5ce748bdb7fb613741598d1b16a27",
                "a61177eb14450bb8c56e5f0547035e0f3a70fe46f36901351cc568b2e48e29d0",
        };
        ArrayList<String> calculatedHashes = new ArrayList<String>(15);

        for (SimplifiedMasternodeListEntry smle : entries) {
            calculatedHashes.add(smle.getHash().toString());
        }

        assertArrayEquals(expectedHashes, calculatedHashes.toArray());

        SimplifiedMasternodeList sml = new SimplifiedMasternodeList(PARAMS, entries);

        String expectedMerkleRoot = "b2303aca677ae2091c882e44b58f57869fa88a6db1f4e1a5d71975e5387fa195";
        String calculatedMerkleRoot = sml.calculateMerkleRoot().toString();

        assertEquals(expectedMerkleRoot, calculatedMerkleRoot);
    }

    @Test
    public void loadFromBootStrapFile() throws BlockStoreException{
        // this is for mainnet
        URL datafile = getClass().getResource("ML1088640.dat");

        initContext(MAINPARAMS);

        SimplifiedMasternodeListManager manager = new SimplifiedMasternodeListManager(context);
        context.setMasternodeListManager(manager);
        SimplifiedMasternodeListManager.setBootStrapFilePath(datafile.getPath(), SimplifiedMasternodeListManager.LLMQ_FORMAT_VERSION);

        manager.resetMNList(true, true);

        try {
            SimplifiedMasternodeListManager.bootStrapLoaded.get();
            assertEquals(1088640, manager.getMasternodeList().getHeight());
        } catch (InterruptedException | ExecutionException x) {
            fail("unable to load bootstrap file");
        }
    }

    @Test
    public void loadFromFile() throws Exception {
        initContext(PARAMS);
        URL datafile = getClass().getResource("simplifiedmasternodelistmanager.dat");
        FlatDB<SimplifiedMasternodeListManager> db = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true);

        SimplifiedMasternodeListManager managerDefaultNames = new SimplifiedMasternodeListManager(Context.get());
        context.setMasternodeListManager(managerDefaultNames);
        assertEquals(db.load(managerDefaultNames), true);

        SimplifiedMasternodeListManager managerSpecific = new SimplifiedMasternodeListManager(Context.get());
        context.setMasternodeListManager(managerSpecific);
        FlatDB<SimplifiedMasternodeListManager> db2 = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true, managerSpecific.getDefaultMagicMessage(), 2);
        assertEquals(db2.load(managerSpecific), true);

        //check to make sure that they have the same number of masternodes
        assertEquals(managerDefaultNames.getMasternodeList().size(), managerSpecific.getMasternodeList().size());

        //load a file with version 1, expecting version 2
        SimplifiedMasternodeListManager managerSpecificFail = new SimplifiedMasternodeListManager(Context.get());
        context.setMasternodeListManager(managerSpecificFail);
        FlatDB<SimplifiedMasternodeListManager> db3 = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true, managerSpecific.getDefaultMagicMessage(), 3);
        assertEquals(db3.load(managerSpecificFail), false);
    }

    @Test
    public void quorumHashTest() {

        String [] hashesAsStrings = {
                "314ed832f858399c62237bdc7d1be68e228cf3ea735d9f9f96001cf2d30ca47b",
                "41f0423366bee938df49427492dfd664a537cf32a849b7bb83d2a652e8794fea",
                "51b6e27b612977ddd8685c90a8c4af39654c3d2f36176d2d15c846ffe8da12c0",
                "9e2b9bc9934937c7cb1a72232de1ab1416d6e8c4e9794698b28d6d1c88fd72ed"
        };

        ArrayList<Sha256Hash> hashes = new ArrayList<>();

        for (int i = 0; i < 4; ++i) {
            hashes.add(Sha256Hash.wrapReversed(Utils.HEX.decode(hashesAsStrings[i])));
        }

        System.out.println(SimplifiedQuorumList.calculateMerkleRoot(hashes));
    }

}
