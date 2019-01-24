/*
 * Copyright 2013 Google Inc.
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

package org.bitcoinj.wallet;

import org.bitcoinj.core.*;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.testing.TestWithWallet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InstantXCoinSelectorTest extends TestWithWallet {
    private static final NetworkParameters PARAMS = UnitTestParams.get();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        Utils.setMockClock(); // Use mock clock
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void selectable() throws Exception {
        InstantXCoinSelector instantXCoinSelector = InstantXCoinSelector.get();
        Transaction t;
        t = new Transaction(PARAMS);
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        assertFalse(instantXCoinSelector.isTransactionSelectable(t));
        t.getConfidence().setSource(TransactionConfidence.Source.SELF);
        assertFalse(instantXCoinSelector.isTransactionSelectable(t));

        t = new Transaction(PARAMS);
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        t.getConfidence().setDepthInBlocks(UnitTestParams.get().getInstantSendConfirmationsRequired() - 1);
        assertFalse(instantXCoinSelector.isTransactionSelectable(t));
        t.getConfidence().setDepthInBlocks(UnitTestParams.get().getInstantSendConfirmationsRequired());
        assertTrue(instantXCoinSelector.isTransactionSelectable(t));
        t.getConfidence().setDepthInBlocks(UnitTestParams.get().getInstantSendConfirmationsRequired() + 1);
        assertTrue(instantXCoinSelector.isTransactionSelectable(t));
    }
}
