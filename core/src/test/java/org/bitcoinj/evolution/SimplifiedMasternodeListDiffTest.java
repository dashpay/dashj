/*
 * Copyright 2021 Dash Core Group
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

import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.dashj.bls.BLSJniLibrary;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimplifiedMasternodeListDiffTest {
    // qrinfo object
    byte [] payloadOne;
    TestNet3Params PARAMS = TestNet3Params.get();
    MainNetParams MAINNET = MainNetParams.get();
    static {
        BLSJniLibrary.init();
    }

    private byte[] loadMnListDiff(String filename) throws IOException {
        InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream(filename));
        byte[] payloadOne = new byte [inputStream.available()];
        checkState(inputStream.read(payloadOne) != 0);
        return payloadOne;
    }

    // 2023-06-16: mainnet is on 19.1 with protocol 70227
    //             testnet is on 19.2 with protocol 70228

    @Test
    public void mnlistdiff_70227() throws IOException {
        payloadOne = loadMnListDiff("mnlistdiff-mainnet-0-1888465-70227.dat");
        SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(MAINNET, payloadOne, 70227);
        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());

        assertTrue(mnlistdiff.hasChanges());
        assertEquals(Sha256Hash.wrap("0000000000000011a67470334158a97a99867e7969343ee871b6ea1c7733e510"), mnlistdiff.getBlockHash());

        assertFalse(mnlistdiff.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());
    }

    @Test
    public void mnlistdiff_70228_beforeActivation() throws IOException {
        BLSScheme.setLegacyDefault(true); // the qrinfo will set the scheme to basic
        payloadOne = loadMnListDiff("mnlistdiff-testnet-0-849810-70228-before19.2HF.dat");
        SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(PARAMS, payloadOne, 70228);
        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());

        assertTrue(mnlistdiff.hasChanges());
        assertEquals(Sha256Hash.wrap("0000001e0b53d7e4e2dea97b0cb8b705fd8b4a6e6d51470f13b56d6588a61f77"), mnlistdiff.getBlockHash());
        assertEquals(1, mnlistdiff.getVersion());
        assertFalse(mnlistdiff.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());
    }

    @Test
    public void mnlistdiff_70228_afterActivation() throws IOException {
        BLSScheme.setLegacyDefault(true); // the qrinfo will set the scheme to basic
        payloadOne = loadMnListDiff("mnlistdiff-testnet-0-850798-70228-after19.2HF.dat");
        SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(PARAMS, payloadOne, 70228);
        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());

        assertTrue(mnlistdiff.hasChanges());
        assertEquals(Sha256Hash.wrap("000001a505e030a10fa15b0f1abfe3314886ab8080f5c777321f55749457c7a6"), mnlistdiff.getBlockHash());
        assertEquals(1, mnlistdiff.getVersion());
        assertTrue(mnlistdiff.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());
    }

    @Test
    public void mnlistdiff_70230() throws IOException {
        BLSScheme.setLegacyDefault(true); // the qrinfo will set the scheme to basic
        payloadOne = loadMnListDiff("mnlistdiff-mainnet-0-2028691-70230.dat");
        SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(PARAMS, payloadOne, 70230);
        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());

        assertTrue(mnlistdiff.hasChanges());
        assertEquals(Sha256Hash.wrap("000000000000000f78a0addf3f9a4c65a4d0f2ca8e63d5893f8227e1585ef3d8"), mnlistdiff.getBlockHash());
        assertEquals(1, mnlistdiff.getVersion());
        assertTrue(mnlistdiff.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());
    }

    /**
     * A Dash Core v24 regtest mnlistdiff with one version 3 Evo node and one version 3 regular masternode,
     * whose addresses are ExtNetInfo. Generated by contrib/v24-fixtures/gen_v24_fixtures.py.
     */
    @Test
    public void mnlistdiff_withExtAddrEntries_parsesAndVerifiesRoot() throws Exception {
        RegTestParams regtest = RegTestParams.get();
        JSONObject vectors = new JSONObject(new String(loadMnListDiff("v24-regtest-vectors.json"), StandardCharsets.UTF_8));
        JSONObject expected = vectors.getJSONObject("mnlistdiff");
        payloadOne = loadMnListDiff(expected.getString("file"));
        SimplifiedMasternodeListDiff mnlistdiff = new SimplifiedMasternodeListDiff(regtest, payloadOne, expected.getInt("protocol"));
        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());
        assertEquals(Sha256Hash.wrap(expected.getString("blockhash")), mnlistdiff.getBlockHash());

        // every entry is version 3, or the fixture does not exercise ExtNetInfo
        JSONArray coreEntries = expected.getJSONArray("mnList");
        assertEquals(coreEntries.length(), mnlistdiff.getMnList().size());
        UnsafeByteArrayOutputStream entries = new UnsafeByteArrayOutputStream();
        for (int i = 0; i < coreEntries.length(); i++) {
            JSONObject coreEntry = coreEntries.getJSONObject(i);
            SimplifiedMasternodeListEntry entry = mnlistdiff.getMnList().get(i);
            assertEquals(3, entry.getVersion());
            assertEquals(Sha256Hash.wrap(coreEntry.getString("proRegTxHash")), entry.getProRegTxHash());
            assertEquals(coreEntry.getInt("nType"), entry.getType());
            assertTrue(entry.isValid());

            JSONObject addresses = coreEntry.getJSONObject("addresses");
            String[] core = addresses.getJSONArray("core_p2p").getString(0).split(":");
            assertEquals(new MasternodeAddress(core[0], Integer.parseInt(core[1])), entry.getService());
            if (entry.isHPMN()) {
                String https = addresses.getJSONArray("platform_https").getString(0);
                String p2p = addresses.getJSONArray("platform_p2p").getString(0);
                assertEquals(Integer.parseInt(https.substring(https.indexOf(':') + 1)), entry.getPlatformHTTPPort());
                assertEquals(Integer.parseInt(p2p.substring(p2p.indexOf(':') + 1)), entry.getNetInfo().getPlatformP2PPort());
                assertEquals(KeyId.fromString(coreEntry.getString("platformNodeID")), entry.getPlatformNodeId());
            }
            // re-serialize from the parsed fields rather than the cached bytes
            entry.bitcoinSerializeToStream(entries);
        }
        String reserialized = Utils.HEX.encode(entries.toByteArray());
        assertTrue("re-serialized entries are not in the payload", Utils.HEX.encode(payloadOne).contains(reserialized));

        // and the thing that actually matters
        SimplifiedMasternodeList list = new SimplifiedMasternodeList(regtest).applyDiff(mnlistdiff);
        Sha256Hash coreRoot = Sha256Hash.wrap(expected.getString("merkleRootMNList"));
        assertEquals(coreRoot, ((CoinbaseTx) mnlistdiff.getCoinBaseTx().getExtraPayloadObject()).getMerkleRootMasternodeList());
        assertEquals(coreRoot, list.calculateMerkleRoot());
        assertTrue(list.verify(mnlistdiff.getCoinBaseTx(), mnlistdiff, null));

        // a stored entry (as in a .mnlist file) loads back with the same addresses and leaf hash
        for (SimplifiedMasternodeListEntry entry : mnlistdiff.getMnList()) {
            UnsafeByteArrayOutputStream stored = new UnsafeByteArrayOutputStream();
            entry.bitcoinSerializeToStream(stored);
            SimplifiedMasternodeListEntry loaded = new SimplifiedMasternodeListEntry(regtest, stored.toByteArray(), 0,
                    list.getProtocolVersion());
            assertEquals(stored.size(), loaded.getMessageSize());
            assertEquals(entry.getNetInfo(), loaded.getNetInfo());
            assertEquals(entry.getPlatformHTTPPort(), loaded.getPlatformHTTPPort());
            assertEquals(entry.getHash(), loaded.getHash());
        }
    }
}
