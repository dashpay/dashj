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

import com.google.common.base.Preconditions;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

/*
 * Created by Hash Engineering on 8/25/2018.
 */

/**
 * KeyId stores a Hash160 of a public key.  It is displayed in big endian.
 */
public class KeyId extends TransactionDestination {
    public static final KeyId KEYID_ZERO = new KeyId(new byte[20]);

    public KeyId(NetworkParameters params, byte[] payload, int offset) throws ProtocolException {
        super(params, payload, offset);
    }

    private KeyId(byte [] keyId) {
        super(keyId);
    }

    public static KeyId fromBytes(byte[] bytes) {
        return new KeyId(bytes);
    }

    private KeyId(byte [] key, boolean isLittleEndian) {
        super(key);
        Preconditions.checkArgument(key.length == 20);
        if (isLittleEndian) {
            bytes = new byte[key.length];
            System.arraycopy(key, 0, bytes, 0, key.length);
        } else {
            bytes = Utils.reverseBytes(key);
        }
    }

    public static KeyId fromBytes(byte[] bytes, boolean isLittleEndian) {
        return new KeyId(bytes, isLittleEndian);
    }

    public static KeyId fromString(String keyId) {
        return new KeyId(Utils.reverseBytes(Utils.HEX.decode(keyId)));
    }

    public String toString() {
        return "KeyId(" + Utils.HEX.encode(Utils.reverseBytes(bytes)) +")";
    }

    @Override
    public Address getAddress(NetworkParameters params) {
        return Address.fromPubKeyHash(params, bytes);
    }

    @Override
    public Script getScript() {
        return ScriptBuilder.createP2PKHOutputScript(bytes);
    }
}
