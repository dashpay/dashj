/*
 * Copyright 2022 Dash Core Group.
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
package org.bitcoinj.crypto;

import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;

import java.io.IOException;
import java.io.OutputStream;


public abstract class BLSAbstractLazyObject extends Message {

    byte [] buffer;
    boolean initialized;
    boolean legacy;

    protected BLSAbstractLazyObject(NetworkParameters params) {
        super(params);
    }

    protected BLSAbstractLazyObject(BLSAbstractLazyObject lazyObject) {
        super(lazyObject.params);
        this.buffer = lazyObject.buffer;
        this.initialized = lazyObject.initialized;
        this.legacy = lazyObject.legacy;
    }

    protected BLSAbstractLazyObject(NetworkParameters params, byte [] payload, int offset, boolean legacy) {
        super(params, payload, offset, params.getProtocolVersionNum(legacy ? NetworkParameters.ProtocolVersion.BLS_LEGACY : NetworkParameters.ProtocolVersion.BLS_BASIC));
    }

    @Override
    protected void parse() throws ProtocolException {
        legacy = protocolVersion == NetworkParameters.ProtocolVersion.BLS_LEGACY.getBitcoinProtocolVersion();
        initialized = false;
    }

    protected abstract void bitcoinSerializeToStream(OutputStream stream, boolean legacy) throws IOException;

    public void bitcoinSerialize(OutputStream stream, boolean legacy) throws IOException {
        bitcoinSerializeToStream(stream, legacy);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
