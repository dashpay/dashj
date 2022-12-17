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

import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by Hash Engineering on 4/12/2019.
 */
// senddsq
public class SendCoinJoinQueue extends Message {
    private boolean send;

    public SendCoinJoinQueue(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
    }

    public SendCoinJoinQueue(NetworkParameters params, boolean send) {
        super(params);
        this.send = send;
    }

    @Override
    protected void parse() throws ProtocolException {
        send = readBytes(1)[0] == 1;
        length = 1;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(send ? 1 : 0);
    }

    @Override
    public String toString() {
        return String.format("SendDsq(send=%s)", send);
    }

    public boolean getSend() {
        return send;
    }
}
