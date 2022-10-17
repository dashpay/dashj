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

import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

/**
 * Created by Hash Engineering on 8/25/2018.
 */
public class ScriptId extends TransactionDestination {
    public static final ScriptId SCRIPT_ID_ZERO = new ScriptId(new byte[20]);

    public ScriptId(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    public ScriptId(byte [] keyId) {
        super(keyId);
    }

    public String toString()
    {
        return "ScriptId(" + Utils.HEX.encode(bytes) +")";
    }

    @Override
    public Address getAddress(NetworkParameters params) {
        return Address.fromScriptHash(params, bytes);
    }

    @Override
    public Script getScript() {
        return ScriptBuilder.createP2SHOutputScript(bytes);
    }
}
