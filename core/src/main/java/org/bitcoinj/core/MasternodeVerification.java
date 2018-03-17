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

import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static org.bitcoinj.core.Masternode.MASTERNODE_EXPIRATION_SECONDS;
import static org.bitcoinj.core.Utils.int64ToByteStreamLE;
import static org.bitcoinj.core.Utils.uint32ToByteStreamLE;

public class MasternodeVerification extends Message implements Serializable{

    private static final Logger log = LoggerFactory.getLogger(MasternodeVerification.class);

    TransactionInput vin1;
    TransactionInput vin2;
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

    protected static int calcLength(byte[] buf, int offset) {
        VarInt varint;

        int cursor = offset;

        //vin
        cursor += 36;
        varint = new VarInt(buf, cursor);
        long scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        cursor += 36;
        varint = new VarInt(buf, cursor);
        scriptLen = varint.value;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + varint.getOriginalSizeInBytes();

        //addr
        cursor += NetAddress.MESSAGE_SIZE;
        //nonce
        cursor += 4;
        //blockHeight
        cursor += 4;
        //vchSig
        cursor += MasternodeSignature.calcLength(buf, cursor);
        cursor += MasternodeSignature.calcLength(buf, cursor);

        return cursor - offset;
    }

    public int calculateMessageSizeInBytes()
    {
        int cursor = 0;

        //vin
        cursor += 36;

        long scriptLen = vin1.getScriptBytes().length;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + VarInt.sizeOf(scriptLen);

        scriptLen = vin2.getScriptBytes().length;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + VarInt.sizeOf(scriptLen);

        //addr
        cursor += NetAddress.MESSAGE_SIZE;
        //nonce
        cursor += 4;
        //blockHeight
        cursor += 4;
        //vchSig
        cursor += vchSig1.calculateMessageSizeInBytes();
        cursor += vchSig2.calculateMessageSizeInBytes();
        return cursor;

    }

    @Override
    protected void parse() throws ProtocolException {

        vin1 = new TransactionInput(params, null, payload, cursor);
        cursor += vin1.getMessageSize();

        vin2 = new TransactionInput(params, null, payload, cursor);
        cursor += vin2.getMessageSize();

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
        vin1.bitcoinSerialize(stream);
        vin2.bitcoinSerialize(stream);
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
            vin1.bitcoinSerialize(bos);
            vin2.bitcoinSerialize(bos);
            addr.bitcoinSerialize(bos);
            uint32ToByteStreamLE(nonce, bos);
            uint32ToByteStreamLE(blockHeight, bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }
}
