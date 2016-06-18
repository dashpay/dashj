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

import org.bitcoinj.store.BlockStoreException;
import org.darkcoinj.DarkSendSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Arrays;

import static com.hashengineering.crypto.X11.x11Digest;
import static org.bitcoinj.core.Utils.int64ToByteStreamLE;

public class MasternodePing extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(MasternodePing.class);

    public static final int  MASTERNODE_MIN_MNP_SECONDS   =          (10*60);
    public static final int MASTERNODE_MIN_MNB_SECONDS    =         (5*60);

    TransactionInput vin;
    Sha256Hash blockHash;
    long sigTime;
    MasternodeSignature vchSig;

    //DarkCoinSystem system;
    Context context;

    MasternodePing(Context context) {
        super(context.getParams());
        this.context = context;
    }
    /*MasternodePing(NetworkParameters context, AbstractBlockChain blockChain, TransactionInput newVin)
    {
        super(context);
    }*/

    MasternodePing(NetworkParameters params, byte[] bytes)
    {
        super(params, bytes, 0);
        this.context = Context.get();
    }

    MasternodePing(NetworkParameters params, byte[] bytes, int cursor) {
        super(params, bytes, cursor);
        this.context = Context.get();
    }

    @Override
    protected void parseLite() throws ProtocolException {
        if (parseLazy && length == UNKNOWN_LENGTH) {
            length = calcLength(payload, offset);
            cursor = offset + length;
        }
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

        //blockHash
        cursor += 32;
        //sigTime
        cursor += 8;
        //vchSig
        cursor = MasternodeSignature.calcLength(buf, cursor);

        return cursor - offset;
    }

    public int calculateMessageSizeInBytes()
    {
        int cursor = 0;

        //vin
        cursor += 36;

        long scriptLen = vin.getScriptBytes().length;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + VarInt.sizeOf(scriptLen);

        //blockHash
        cursor += 32;
        //sigTime
        cursor += 8;
        //vchSig

        cursor += vchSig.calculateMessageSizeInBytes();

        return cursor;

    }

    @Override
    void parse() throws ProtocolException {
        if (parsed)
            return;

        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        blockHash = readHash();

        sigTime = readInt64();

        vchSig = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig.getMessageSize();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        stream.write(blockHash.getReversedBytes());
        int64ToByteStreamLE(sigTime, stream);
        vchSig.bitcoinSerialize(stream);
    }

    boolean checkAndUpdate(boolean fRequireEnabled)
    {
        if (sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            log.info("CMasternodePing::CheckAndUpdate - Signature rejected, too far into the future "+ vin.toString());
            //nDos = 1;
            return false;
        }

        if (sigTime <= Utils.currentTimeSeconds() - 60 * 60) {
            log.info("CMasternodePing::CheckAndUpdate - Signature rejected, too far into the past {} - {} {} \n", vin.toString(), sigTime, Utils.currentTimeSeconds());
            //nDos = 1;
            return false;
        }

        log.info("masternode - CMasternodePing::CheckAndUpdate - New Ping - "+ getHash().toString() +" - "+ blockHash.toString()+" - "+ sigTime);

        // see if we have this Masternode
        Masternode pmn = context.masternodeManager.find(vin);
        if(pmn != null && pmn.protocolVersion >= context.masternodePayments.getMinMasternodePaymentsProto())
        {
            if (fRequireEnabled && !pmn.isEnabled()) return false;

            // LogPrintf("mnping - Found corresponding mn for vin: %s\n", vin.ToString());
            // update only if there is no known ping for this masternode or
            // last ping was more then MASTERNODE_MIN_MNP_SECONDS-60 ago comparing to this one
            if(!pmn.isPingedWithin(MASTERNODE_MIN_MNP_SECONDS - 60, sigTime)) {
                String strMessage = vin.toStringCpp() + blockHash.toString() + sigTime;

                StringBuilder errorMessage = new StringBuilder();
                if (!DarkSendSigner.verifyMessage(pmn.pubkey2, vchSig, strMessage, errorMessage)) {
                    log.info("CMasternodePing::CheckAndUpdate - Got bad Masternode address signature " + vin.toString());
                    //nDos = 33;
                    return false;
                }

                try {

                    StoredBlock storedBlock = context.masternodeManager.blockChain.getBlockStore().get(blockHash);

                    if(storedBlock != null) {

                        if (storedBlock.getHeight() < context.masternodeManager.blockChain.getChainHead().getHeight() - 24) {
                            log.info("CMasternodePing::CheckAndUpdate - Masternode {} block hash {} is too old", vin.toString(), blockHash.toString());
                            return false;
                        }
                    }
                    else
                    {
                        if (DarkCoinSystem.fDebug)
                            log.info("CMasternodePing::CheckAndUpdate - Masternode {} block hash {} is unknown", vin.toString(), blockHash.toString());
                    }

                } catch (BlockStoreException x) {
                    return false;
                }
                catch (Exception x) {
                    return false;
                }
                /* java code is above:
                BlockMap::iterator mi = mapBlockIndex.find(blockHash);
                if (mi != mapBlockIndex.end() && (*mi).second)
                {
                    if((*mi).second->nHeight < chainActive.Height() - 24)
                    {
                        log.info("CMasternodePing::CheckAndUpdate - Masternode {2} block hash {2} is too old\n", vin.ToString(), blockHash.ToString());
                        // Do nothing here (no Masternode update, no mnping relay)
                        // Let this node to be visible but fail to accept mnping

                        return false;
                    }
                } else {
                if (DarkCoinSystem.fDebug) log.info("CMasternodePing::CheckAndUpdate - Masternode %s block hash %s is unknown\n", vin.ToString(), blockHash.ToString());
                // maybe we stuck so we shouldn't ban this node, just fail to accept it
                // TODO: or should we also request this block?

                    return false;
                }

*/
                pmn.lastPing = this;

                //mnodeman.mapSeenMasternodeBroadcast.lastPing is probably outdated, so we'll update it
                MasternodeBroadcast mnb = new MasternodeBroadcast(pmn);
                Sha256Hash hash = mnb.getHash();
                if(context.masternodeManager.mapSeenMasternodeBroadcast.containsKey(hash)) {
                    context.masternodeManager.mapSeenMasternodeBroadcast.get(hash).lastPing = this;
                }

                pmn.check(true);
                if(pmn.isEnabled()) return false;

                log.info("masternode-CMasternodePing::CheckAndUpdate - Masternode ping accepted, vin: "+ vin.toString());

                relay();
                return true;
            }
            log.info("masternode - CMasternodePing::CheckAndUpdate - Masternode ping arrived too early, vin: "+ vin.toString());
            //nDos = 1; //disable, this is happening frequently and causing banned peers
            return false;
        }
        log.info("masternode - CMasternodePing::CheckAndUpdate - Couldn't find compatible Masternode entry, vin: "+ vin.toString());

        return false;
    }
    boolean checkAndUpdate()
    {
        return checkAndUpdate(false);
    }

    public boolean equals(Object o)
    {
        maybeParse();
        MasternodePing mnp = (MasternodePing)o;
        if(mnp.sigTime == this.sigTime &&
                mnp.vin.equals(this.vin) &&
                mnp.vchSig.equals(this.vchSig) &&
                mnp.blockHash.equals(this.blockHash))
        {

                return true;
        }
        return false;
    }

    static MasternodePing EMPTY = new MasternodePing(Context.get());

    static MasternodePing empty() { return EMPTY; }

    void relay()
    {
        //CInv inv(MSG_MASTERNODE_PING, GetHash());
        //RelayInv(inv);
    }

    public Sha256Hash getHash(){
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(vchSig.calculateMessageSizeInBytes()+8);
            vin.bitcoinSerialize(bos);
            Utils.int64ToByteStreamLE(sigTime, bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }
}
