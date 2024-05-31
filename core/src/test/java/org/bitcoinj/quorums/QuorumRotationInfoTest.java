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

package org.bitcoinj.quorums;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.BLSScheme;
import org.bitcoinj.evolution.SimplifiedMasternodeListDiff;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.dashj.bls.BLSJniLibrary;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuorumRotationInfoTest {
    // qrinfo object
    byte [] payloadOne;
    TestNet3Params PARAMS = TestNet3Params.get();
    MainNetParams MAINNET = MainNetParams.get();
    static {
        BLSJniLibrary.init();
    }

    private byte[] loadQRInfo(String filename) throws IOException {
        InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream(filename));
        byte[] payloadOne = new byte [inputStream.available()];
        checkState(inputStream.read(payloadOne) != 0);
        return payloadOne;
    }

    @Test
    public void core18RoundTripTest() throws IOException {
        BLSScheme.setLegacyDefault(true);
        payloadOne = loadQRInfo("qrinfo--1-24868.dat");
        QuorumRotationInfo quorumRotationInfo = new QuorumRotationInfo(PARAMS, payloadOne, 70220);
        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());

        assertTrue(quorumRotationInfo.hasChanges());
        assertEquals(Sha256Hash.wrap("000003227cf2f83a1faa683ece5b875abeb555ebf1252f62cb28a96d459bcc11"), quorumRotationInfo.mnListDiffTip.blockHash);
    }

    // 2023-06-16: mainnet is on 19.1 with protocol 70227
    //             testnet is on 19.2 with protocol 70228

    @Test
    public void qrinfo_70227() throws IOException {
        payloadOne = loadQRInfo("qrinfo-mainnet-0-1888473_70227.dat");
        QuorumRotationInfo quorumRotationInfo = new QuorumRotationInfo(MAINNET, payloadOne, 70227);
        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());

        assertTrue(quorumRotationInfo.hasChanges());
        assertEquals(Sha256Hash.wrap("000000000000000cafc4b174a51768b6216880613ce1ef19add8e84ca48c97d1"), quorumRotationInfo.mnListDiffTip.blockHash);
        assertEquals(SimplifiedMasternodeListDiff.LEGACY_BLS_VERSION, quorumRotationInfo.mnListDiffAtH.getVersion());
        assertFalse(quorumRotationInfo.mnListDiffAtH.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());
    }

    @Test
    public void qrinfo_70228_beforeActivation() throws IOException {
        BLSScheme.setLegacyDefault(true); // the qrinfo will set the scheme to basic
        payloadOne = loadQRInfo("qrinfo-testnet-0-849809_70228-before19.2HF.dat");
        QuorumRotationInfo quorumRotationInfo = new QuorumRotationInfo(PARAMS, payloadOne, 70228);
        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());

        assertTrue(quorumRotationInfo.hasChanges());
        assertEquals(Sha256Hash.wrap("000001a7d846454edba8aa61df85ce277980897503e45fcc0c39bd19ff2ccbcf"), quorumRotationInfo.mnListDiffTip.blockHash);
        assertEquals(1, quorumRotationInfo.mnListDiffAtH.getVersion());
        assertFalse(quorumRotationInfo.mnListDiffAtH.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());
    }

    @Test
    public void qrinfo_70228_afterActivation() throws IOException {
        BLSScheme.setLegacyDefault(true); // the qrinfo will set the scheme to basic
        payloadOne = loadQRInfo("qrinfo-testnet-0-850806-70228-after19.2HF.dat");
        QuorumRotationInfo quorumRotationInfo = new QuorumRotationInfo(PARAMS, payloadOne, 70228);
        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());

        assertTrue(quorumRotationInfo.hasChanges());
        assertEquals(Sha256Hash.wrap("00000134f050317635efc92333a106c25219c8d0fe3ad8fbccba48b6dd4057d3"), quorumRotationInfo.mnListDiffTip.blockHash);
        assertEquals(1, quorumRotationInfo.mnListDiffAtH.getVersion());
        assertTrue(quorumRotationInfo.mnListDiffAtH.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());
    }

    @Test
    public void qrinfo_70230_afterActivation() throws IOException {
        BLSScheme.setLegacyDefault(true); // the qrinfo will set the scheme to basic
        payloadOne = loadQRInfo("qrinfo-testnet-0-905770-70230-after20.HF.dat");
        QuorumRotationInfo quorumRotationInfo = new QuorumRotationInfo(PARAMS, payloadOne, 70230);
        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());

        assertTrue(quorumRotationInfo.hasChanges());
        // 905770
        assertEquals(Sha256Hash.wrap("000002920ed0a1295fbd27e0acbdc5451200040fafb5fecd56f355cd7d7b9b73"), quorumRotationInfo.mnListDiffTip.blockHash);
        assertEquals(1, quorumRotationInfo.mnListDiffAtH.getVersion());
        assertTrue(quorumRotationInfo.mnListDiffAtH.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());
    }

    @Test
    public void qrinfo_70230() throws IOException {
        payloadOne = loadQRInfo("qrinfo-mainnet-0-2028764-70230.dat");
        QuorumRotationInfo quorumRotationInfo = new QuorumRotationInfo(MAINNET, payloadOne, 702230);
        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());

        assertTrue(quorumRotationInfo.hasChanges());
        assertEquals(Sha256Hash.wrap("00000000000000239004bad185d58602b8b90cc8211d29f55b93d72bdaa3a098"), quorumRotationInfo.mnListDiffTip.blockHash);
        assertEquals(SimplifiedMasternodeListDiff.LEGACY_BLS_VERSION, quorumRotationInfo.mnListDiffAtH.getVersion());
        assertTrue(quorumRotationInfo.mnListDiffAtH.hasBasicSchemeKeys());

        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());
    }
}
