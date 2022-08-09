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
import org.bitcoinj.params.TestNet3Params;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QuorumRotationInfoTest {
    // qrinfo object
    byte [] payloadOne;
    TestNet3Params PARAMS = TestNet3Params.get();

    @Before
    public void startUp() throws IOException {
        InputStream inputStream = Objects.requireNonNull(getClass().getResourceAsStream("qrinfo--1-24868.dat"));
        payloadOne = new byte [inputStream.available()];
        checkState(inputStream.read(payloadOne) != 0);
    }

    @Test
    public void roundTripTest() {
        QuorumRotationInfo quorumRotationInfo = new QuorumRotationInfo(PARAMS, payloadOne);
        assertArrayEquals(payloadOne, quorumRotationInfo.bitcoinSerialize());

        assertTrue(quorumRotationInfo.hasChanges());
        assertEquals(Sha256Hash.wrap("000003227cf2f83a1faa683ece5b875abeb555ebf1252f62cb28a96d459bcc11"), quorumRotationInfo.mnListDiffTip.blockHash);
    }
}
