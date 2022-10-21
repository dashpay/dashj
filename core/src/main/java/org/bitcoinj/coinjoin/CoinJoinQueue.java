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

import org.bitcoinj.core.MasternodeSignature;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A currently in progress mixing merge and denomination information
 */
// dsq
public class CoinJoinQueue extends Message {
    private static final Logger log = LoggerFactory.getLogger(CoinJoinQueue.class);

    private int denomination;
    private TransactionOutPoint masternodeOutpoint;
    private long time;
    private boolean ready;  // Ready to submit
    private MasternodeSignature signature;
    // memory only
    private boolean tried;

    public CoinJoinQueue(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
    }

    public CoinJoinQueue(
        NetworkParameters params,
        int denomination,
        TransactionOutPoint masternodeOutpoint,
        long time,
        boolean ready,
        MasternodeSignature signature
    ) {
        super(params);
        this.denomination = denomination;
        this.masternodeOutpoint = masternodeOutpoint;
        this.time = time;
        this.ready = ready;
        this.signature = signature;
    }

    @Override
    protected void parse() throws ProtocolException {
        denomination = (int)readUint32();
        masternodeOutpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += masternodeOutpoint.getMessageSize();
        time = readInt64();
        ready = readBytes(1)[0] == 1;
        signature = new MasternodeSignature(params, payload, cursor);
        cursor += signature.getMessageSize();

        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(denomination, stream);
        masternodeOutpoint.bitcoinSerialize(stream);
        Utils.int64ToByteStreamLE(time, stream);
        stream.write(ready ? 1 : 0);
        signature.bitcoinSerialize(stream);
    }

    public Sha256Hash getSignatureHash() {
        try {
            ByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            Utils.uint32ToByteStreamLE(denomination, bos);
            masternodeOutpoint.bitcoinSerialize(bos);
            Utils.int64ToByteStreamLE(time, bos);
            bos.write(ready ? 1 : 0);

            return Sha256Hash.twiceOf(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean checkSignature(BLSPublicKey pubKey) {
        Sha256Hash hash = getSignatureHash();
        BLSSignature sig = new BLSSignature(signature.getBytes());

        if (!sig.verifyInsecure(pubKey, hash)) {
            log.info("CoinJoinQueue-CheckSignature -- VerifyInsecure() failed\n");
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return String.format(
                "CoinJoinQueue(denomination=%d, masternodeOutpoint=%s, time=%d, ready=%s)",
                denomination,
                masternodeOutpoint.toString(),
                time,
                ready
        );
    }

    public int getDenomination() {
        return denomination;
    }

    public TransactionOutPoint getMasternodeOutpoint() {
        return masternodeOutpoint;
    }

    public long getTime() {
        return time;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isTried() {
        return tried;
    }

    public void setTried(boolean tried) {
        this.tried = tried;
    }

    public MasternodeSignature getSignature() {
        return signature;
    }

    public boolean isTimeOutOfBounds() {
        return isTimeOutOfBounds(Utils.currentTimeMillis());
    }
    public boolean isTimeOutOfBounds(long currentTime) {
        return currentTime - time > CoinJoinConstants.COINJOIN_QUEUE_TIMEOUT ||
                time - currentTime > CoinJoinConstants.COINJOIN_QUEUE_TIMEOUT;
    }
}