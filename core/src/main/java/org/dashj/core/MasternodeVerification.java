/**
 * Copyright 2014 Hash Engineering Solutions
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;

public class MasternodeVerification extends Message implements Serializable{

    private static final Logger log = LoggerFactory.getLogger(MasternodeVerification.class);

    TransactionOutPoint masternodeOutpoint1;
    TransactionOutPoint masternodeOutpoint2;
    NetAddress addr;
    int nonce;
    int blockHeight;
    MasternodeSignature vchSig1;
    MasternodeSignature vchSig2;

    Context context;

    MasternodeVerification(Context context) {
        super(context.getParams());
        this.context = context;
    }

    MasternodeVerification(Context context, NetAddress addr, int nonce, int blockHeight)
    {
        super(context.getParams());
        this.context = context;
        this.addr = addr;
        this.nonce = nonce;
        this.blockHeight = blockHeight;
    }
    MasternodeVerification(NetworkParameters params, byte [] payload)
    {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {

        masternodeOutpoint1 = new TransactionOutPoint(params, payload, cursor);
        cursor += masternodeOutpoint1.getMessageSize();

        masternodeOutpoint2 = new TransactionOutPoint(params, payload, cursor);
        cursor += masternodeOutpoint2.getMessageSize();

        addr = new NetAddress(params, payload, cursor, 0);

        nonce = (int)readUint32();
        blockHeight = (int)readUint32();

        vchSig1 = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig1.getMessageSize();

        vchSig2 = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig2.getMessageSize();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        masternodeOutpoint1.bitcoinSerialize(stream);
        masternodeOutpoint2.bitcoinSerialize(stream);
        addr.bitcoinSerialize(stream);
        uint32ToByteStreamLE(nonce, stream);
        uint32ToByteStreamLE(blockHeight, stream);
        vchSig1.bitcoinSerialize(stream);
        vchSig2.bitcoinSerialize(stream);
    }

    void relay()
    {
    }

    public Sha256Hash getHash(){
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            masternodeOutpoint1.bitcoinSerialize(bos);
            masternodeOutpoint2.bitcoinSerialize(bos);
            addr.bitcoinSerialize(bos);
            uint32ToByteStreamLE(nonce, bos);
            uint32ToByteStreamLE(blockHeight, bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }
}
