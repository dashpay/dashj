/*
 * Copyright (c) 2022 Dash Core Group
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
package org.bitcoinj.coinjoin;

import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.UnitTestParams;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PendingDSARequestTest {

    @Test
    public void pendingDSARequestTest() throws UnknownHostException {
        PendingDsaRequest dsaRequest = new PendingDsaRequest();
        assertNull(dsaRequest.getAddress());
        assertNull(dsaRequest.getDsa());
        assertTrue(dsaRequest.isExpired());
        
        PendingDsaRequest dsaRequestTwo = new PendingDsaRequest();
        assertEquals(dsaRequest, dsaRequestTwo);
        CoinJoinAccept cja = new CoinJoinAccept(UnitTestParams.get(), 4, null);
        
        MasternodeAddress cserv = new MasternodeAddress(InetAddress.getLocalHost(), 1111);
        PendingDsaRequest customRequest = new PendingDsaRequest(cserv, cja);

        assertEquals(customRequest.getAddress(), cserv);
        assertEquals(customRequest.getDsa(), cja);
        assertFalse(customRequest.isExpired());
        Utils.setMockClock(Utils.currentTimeSeconds() + 15);
        assertFalse(customRequest.isExpired());
        Utils.setMockClock(Utils.currentTimeSeconds() + 1);
        assertTrue(customRequest.isExpired());

        assertNotEquals(dsaRequest, customRequest);
        assertFalse(dsaRequest.bool());
        assertTrue(customRequest.bool());
    }
}
