/*
 * Copyright 2020 Dash Core Group
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

package org.bitcoinj.core;

import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SporkMessageTest {
    static NetworkParameters PARAMS = TestNet3Params.get();
    static Context context = new Context(PARAMS);

    static {
        context.initDash(true, true);
    }

    @Test
    public void verifySpork() {
        byte [] sporkData = Utils.HEX.decode("1227000000000000000000003b5d255b00000000411b49b470662d7f4068f5630ee90a531302ab9046d1cf8333b138c3e42db67a64ca53c390124832785a8934cf5e8a74dad9db8834c32662f8c1c69c23577b39622f");
        Sha256Hash sporkHash = Sha256Hash.wrap("d1d32f00374284b19ee33f9ef19386055fd15091ce4a476ab58701603594cc5e");
        SporkMessage sporkMessage = new SporkMessage(PARAMS, sporkData, 0);

        assertEquals(SporkId.SPORK_3_INSTANTSEND_BLOCK_FILTERING, sporkMessage.getSporkId());
        assertEquals(0, sporkMessage.getValue());
        assertEquals(1529175355L, sporkMessage.getTimeSigned());
        assertEquals(sporkHash, sporkMessage.getSignatureHash());

        assertTrue(sporkMessage.checkSignature(Address.fromString(PARAMS, PARAMS.getSporkAddress()).getHash()));
    }
}
