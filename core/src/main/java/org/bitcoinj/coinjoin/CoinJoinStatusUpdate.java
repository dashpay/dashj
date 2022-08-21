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

// dssu
public class CoinJoinStatusUpdate extends Message {
    private int sessionID;
    private PoolState state;
    private PoolStatusUpdate statusUpdate;
    private PoolMessage messageID;

    public CoinJoinStatusUpdate(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
    }

    public CoinJoinStatusUpdate(
            NetworkParameters params,
            int sessionID,
            PoolState state,
            PoolStatusUpdate statusUpdate,
            PoolMessage messageID
    ) {
        super(params);
        this.sessionID = sessionID;
        this.state = state;
        this.statusUpdate = statusUpdate;
        this.messageID = messageID;
    }

    @Override
    protected void parse() throws ProtocolException {
        sessionID = (int)readUint32();
        state = PoolState.fromValue((int)readUint32());

        if (protocolVersion <= 702015) {
            cursor += 4; // Skip deprecated nEntriesCount
        }

        statusUpdate = PoolStatusUpdate.fromValue((int)readUint32());
        messageID = PoolMessage.fromValue((int)readUint32());
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(sessionID, stream);
        Utils.uint32ToByteStreamLE(state.value, stream);

        if (protocolVersion <= 702015) {
            Utils.uint32ToByteStreamLE(0, stream); // nEntriesCount, deprecated
        }

        Utils.uint32ToByteStreamLE(statusUpdate.value, stream);
        Utils.uint32ToByteStreamLE(messageID.value, stream);
    }

    @Override
    public String toString() {
        return String.format(
                "CoinJoinStatusUpdate(sessionID=%d, state=%d, statusUpdate=%d, messageID=%d)",
                sessionID,
                state.value,
                statusUpdate.value,
                messageID.value
        );
    }

    public int getSessionID() {
        return sessionID;
    }

    public PoolState getState() {
        return state;
    }

    public PoolStatusUpdate getStatusUpdate() {
        return statusUpdate;
    }

    public PoolMessage getMessageID() {
        return messageID;
    }
}