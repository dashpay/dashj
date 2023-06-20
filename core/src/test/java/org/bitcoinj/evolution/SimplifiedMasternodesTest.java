/*
 * Copyright 2019 Dash Core Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.params.DevNetParams;
import org.bitcoinj.params.MainNetParams;

import static org.bitcoinj.evolution.SimplifiedMasternodeListEntry.LEGACY_BLS_VERSION;
import static org.junit.Assert.*;

import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.params.WhiteRussianDevNetParams;
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
import java.util.Objects;

/**
 * Created by hashengineering on 11/26/18.
 */
public class SimplifiedMasternodesTest {

    static Context context;
    static NetworkParameters PARAMS;
    static MainNetParams MAINPARAMS;
    static DevNetParams DEVNETPARAMS;
    static PeerGroup peerGroup;
    static BlockChain blockChain;

    @BeforeClass
    public static void startup() throws BlockStoreException {
        MAINPARAMS = MainNetParams.get();
        PARAMS = TestNet3Params.get();
        DEVNETPARAMS = WhiteRussianDevNetParams.get();
        initContext(PARAMS);
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
            SimplifiedMasternodeListEntry smle = new SimplifiedMasternodeListEntry(PARAMS, LEGACY_BLS_VERSION);
            smle.proRegTxHash = Sha256Hash.wrap(String.format("%064x", i));
            smle.confirmedHash = Sha256Hash.wrap(String.format("%064x", i));

            byte [] ip = {0, 0, 0, (byte)i};
            smle.service = new MasternodeAddress(InetAddress.getByAddress(ip), i);

            byte [] skBuf = new byte[BLSSecretKey.BLS_CURVE_SECKEY_SIZE];
            skBuf[0] = (byte)i;
            BLSSecretKey sk = new BLSSecretKey(skBuf);


            smle.pubKeyOperator = new BLSLazyPublicKey(sk.getPublicKey(), true);
            smle.keyIdVoting = KeyId.fromBytes(Utils.HEX.decode(String.format("%040x", i)), false);
            smle.isValid = true;
            smle.version = SimplifiedMasternodeListDiff.CURRENT_VERSION;

            entries.add(smle);
        }

        String [] expectedHashes = {
                "c26be2378ee9da070e997b908b1a40988dcd92557638e0ecff84a8896f1a9f3d",
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

        SimplifiedMasternodeList sml = new SimplifiedMasternodeList(PARAMS, entries,
                NetworkParameters.ProtocolVersion.BLS_LEGACY.getBitcoinProtocolVersion());

        String expectedMerkleRoot = "3b2a4f6be32c13070979910150d6be0ff1890f17b3169d1eabb96b217b6df2d7";
        String calculatedMerkleRoot = sml.calculateMerkleRoot().toString();

        assertEquals(expectedMerkleRoot, calculatedMerkleRoot);
    }

    @Test
    public void loadFromFile() throws Exception {
        loadFromFile("simplifiedmasternodelistmanager.dat", 2);
    }

    @Test
    public void loadFromFile_v3_before19_2HardFork() throws Exception {
        loadFromFile("testnet-before19.2HF_70227.mnlist", 3);
    }

    @Test
    public void loadFromFile_v5_before19_2HardFork() throws Exception {
        loadFromFile("manager-testnet-v5-before19.2HF.mnlist", 5);
    }

    @Test
    public void loadFromFile_v5_after19_2HardFork() throws Exception {
        loadFromFile("testnet-after19.2HF.mnlist", 5);
    }

    private void loadFromFile(String filename, int fileVersion) throws BlockStoreException {
        initContext(PARAMS);
        URL datafile = Objects.requireNonNull(getClass().getResource(filename));
        FlatDB<SimplifiedMasternodeListManager> db = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true);

        SimplifiedMasternodeListManager managerDefaultNames = new SimplifiedMasternodeListManager(Context.get());
        context.setMasternodeListManager(managerDefaultNames);
        assertTrue(db.load(managerDefaultNames));

        SimplifiedMasternodeListManager managerSpecific = new SimplifiedMasternodeListManager(Context.get());
        context.setMasternodeListManager(managerSpecific);
        FlatDB<SimplifiedMasternodeListManager> db2 = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true, managerSpecific.getDefaultMagicMessage(), fileVersion);
        assertTrue(db2.load(managerSpecific));

        //check to make sure that they have the same number of masternodes
        assertEquals(managerDefaultNames.getMasternodeList().size(), managerSpecific.getMasternodeList().size());

        //load a file with version 6, expecting version 5
        SimplifiedMasternodeListManager managerSpecificFail = new SimplifiedMasternodeListManager(Context.get());
        context.setMasternodeListManager(managerSpecificFail);
        FlatDB<SimplifiedMasternodeListManager> db3 = new FlatDB<SimplifiedMasternodeListManager>(Context.get(), datafile.getFile(), true, managerSpecific.getDefaultMagicMessage(), fileVersion + 1);
        assertFalse(db3.load(managerSpecificFail));
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
