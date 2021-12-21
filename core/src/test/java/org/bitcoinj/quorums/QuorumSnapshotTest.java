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

import org.bitcoinj.core.Utils;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class QuorumSnapshotTest {

    byte [] payloadOne = Utils.HEX.decode("000000001fb95e7b0300");
    TestNet3Params PARAMS = TestNet3Params.get();

    @Test
    public void loadPayloadTest() {
        List<Boolean>  quorumMemberList = Arrays.asList(true, false, false, true, true, true, false, true, false, true,
                true, true, true, false, true, false, true, true, false, true, true, true, true, false, true, true,
                false, false, false, false, false);

        QuorumSnapshot quorumSnapshot = new QuorumSnapshot(PARAMS, payloadOne, 0);
        assertEquals(quorumMemberList, quorumSnapshot.getActiveQuorumMembers());
        assertEquals(SnapshotSkipMode.MODE_NO_SKIPPING.getValue(), quorumSnapshot.getSkipListMode());
        assertEquals(0, quorumSnapshot.getSkipList().size());
    }

    @Test
    public void roundTripTest() {
        QuorumSnapshot quorumSnapshot = new QuorumSnapshot(PARAMS, payloadOne, 0);
        assertArrayEquals(payloadOne, quorumSnapshot.bitcoinSerialize());
    }
}
