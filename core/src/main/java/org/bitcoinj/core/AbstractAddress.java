/*
 * Copyright 2018 Andreas Schildbach
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

import javax.annotation.Nullable;

import org.bitcoinj.script.Script.ScriptType;

/**
 * <p>
 * Base class for addresses including regular addresses ({@link Address}).
 * </p>
 * 
 * <p>
 * Use {@link #fromString(NetworkParameters, String)} to conveniently construct any kind of address from its textual
 * form.
 * </p>
 */
public abstract class AbstractAddress extends PrefixedChecksummedBytes {
    public AbstractAddress(NetworkParameters params, byte[] bytes) {
        super(params, bytes);
    }

    /**
     * Construct an address from its textual form.
     * 
     * @param params
     *            the expected network this address is valid for, or null if the network should be derived from the
     *            textual form
     * @param str
     *            the textual form of the address, such as "17kzeh4N8g49GFvdDzSf8PjaPfyoD1MndL" or
     *            "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4"
     * @return constructed address
     * @throws AddressFormatException
     *             if the given string doesn't parse or the checksum is invalid
     * @throws AddressFormatException.WrongNetwork
     *             if the given string is valid but not for the expected network (eg testnet vs mainnet)
     */
    public static AbstractAddress fromString(@Nullable NetworkParameters params, String str)
            throws AddressFormatException {
        try {
            return Address.fromBase58(params, str);
        } catch (AddressFormatException.WrongNetwork x) {
            throw x;
        }
    }

    /**
     * Get either the public key hash or script hash that is encoded in the address.
     * 
     * @return hash that is encoded in the address
     */
    public abstract byte[] getHash();

    /**
     * Get the type of output script that will be used for sending to the address.
     * 
     * @return type of output script
     */
    public abstract ScriptType getOutputScriptType();
}
