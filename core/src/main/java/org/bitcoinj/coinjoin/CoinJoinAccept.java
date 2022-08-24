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
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;

import java.io.IOException;
import java.io.OutputStream;

// dsa
public class CoinJoinAccept extends Message {
    private int denomination;
    private Transaction txCollateral;

    public CoinJoinAccept(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
    }

    public CoinJoinAccept(NetworkParameters params, int denomination, Transaction txCollateral) {
        super(params);
        this.denomination = denomination;
        this.txCollateral = txCollateral;
    }

    @Override
    protected void parse() throws ProtocolException {
        denomination = (int)readUint32();
        txCollateral = new Transaction(params, payload, cursor);
        cursor += txCollateral.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(denomination, stream);
        txCollateral.bitcoinSerialize(stream);
    }

    @Override
    public String toString() {
        return String.format(
                "CoinJoinAccept(denomination=%d, txCollateral=%s)",
                denomination,
                txCollateral.getTxId()
        );
    }

    public int getDenomination() {
        return denomination;
    }

    public Transaction getTxCollateral() {
        return txCollateral;
    }
}