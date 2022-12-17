/*
 * Copyright 2022 Dash Core Group
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
import org.bitcoinj.core.Utils;

import java.io.IOException;
import java.io.OutputStream;

// dsc
public class CoinJoinComplete extends Message {
    private int msgSessionID;
    private PoolMessage msgMessageID;

    public CoinJoinComplete(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
    }

    public CoinJoinComplete(NetworkParameters params, int msgSessionID, PoolMessage msgMessageID) {
        super(params);
        this.msgSessionID = msgSessionID;
        this.msgMessageID = msgMessageID;
    }

    @Override
    protected void parse() throws ProtocolException {
        msgSessionID = (int)readUint32();
        msgMessageID = PoolMessage.fromValue((int)readUint32());
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(msgSessionID, stream);
        Utils.uint32ToByteStreamLE(msgMessageID.value, stream);
    }

    @Override
    public String toString() {
        return String.format(
                "CoinJoinComplete(msgSessionID=%d, msgMessageID=%d)",
                msgSessionID,
                msgMessageID.value
        );
    }

    public int getMsgSessionID() {
        return msgSessionID;
    }

    public PoolMessage getMsgMessageID() {
        return msgMessageID;
    }
}
