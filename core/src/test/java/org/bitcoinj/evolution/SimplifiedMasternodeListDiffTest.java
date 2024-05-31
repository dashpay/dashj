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

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.dashj.bls.BLSJniLibrary;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
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
        assertEquals(Sha256Hash.wrap("0000000000000011a67470334158a97a99867e7969343ee871b6ea1c7733e510"), mnlistdiff.blockHash);

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
        assertEquals(Sha256Hash.wrap("0000001e0b53d7e4e2dea97b0cb8b705fd8b4a6e6d51470f13b56d6588a61f77"), mnlistdiff.blockHash);
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
        assertEquals(Sha256Hash.wrap("000001a505e030a10fa15b0f1abfe3314886ab8080f5c777321f55749457c7a6"), mnlistdiff.blockHash);
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
        assertEquals(Sha256Hash.wrap("000000000000000f78a0addf3f9a4c65a4d0f2ca8e63d5893f8227e1585ef3d8"), mnlistdiff.blockHash);
        assertEquals(1, mnlistdiff.getVersion());
        assertTrue(mnlistdiff.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, mnlistdiff.bitcoinSerialize());
    }
}
