/*
 * Copyright 2018 Dash Core Group
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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by HashEngineering on 5/9/2018.
 */
public class MessageSignerTest {
    Context context;

    @Before
    public void setUp()
    {
        context = new Context(MainNetParams.get());
    }

    @Test
    public void verifySignatureTest()
    {
        PublicKey publicKey = new PublicKey(Utils.HEX.decode("02cf6cecbac4b6b541ba06c7b58477297a06b2295cf9e2a95566241c287096b895"));
        MasternodeSignature vchSig = new MasternodeSignature(Utils.HEX.decode("1f4af594078c451b20fbe0ebae22a919b8694b8c4683f0cb36dd835e8a1f6114277d0568f4dcac1d479c169d56a921687f9ae8b315e687ef1920a347dcaa32dc1a"));
        String message = "139.59.254.15:999915239445811bd94fd9f0b98eb669fa2c1dc1bda3da99f4c69ed40ce1ccc666e3be757d966d927e90173f970a0c70208";
        StringBuilder errorMessage = new StringBuilder();

        assertTrue(MessageSigner.verifyMessage(publicKey, vchSig, message, errorMessage));
        assertFalse(MessageSigner.verifyMessage(publicKey, vchSig, "140.59.254.15:999915239445811bd94fd9f0b98eb669fa2c1dc1bda3da99f4c69ed40ce1ccc666e3be757d966d927e90173f970a0c70208", errorMessage));
    }
}
