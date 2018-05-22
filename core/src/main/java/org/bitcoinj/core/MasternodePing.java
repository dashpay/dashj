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
import org.bitcoinj.net.Dos;
import org.bitcoinj.store.BlockStoreException;
import org.darkcoinj.DarkSendSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import static com.hashengineering.crypto.X11.x11Digest;
import static org.bitcoinj.core.Masternode.MASTERNODE_EXPIRATION_SECONDS;
import static org.bitcoinj.core.Masternode.MASTERNODE_NEW_START_REQUIRED_SECONDS;
import static org.bitcoinj.core.Utils.int64ToByteStreamLE;

public class MasternodePing extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(MasternodePing.class);

    public static final int  MASTERNODE_MIN_MNP_SECONDS   =          (10*60);
    public static final int MASTERNODE_MIN_MNB_SECONDS    =         (5*60);
    public static final int DEFAULT_SENTINEL_VERSION = 0x010001;

    TransactionInput vin;
    Sha256Hash blockHash;
    long sigTime;
    MasternodeSignature vchSig;
    boolean sentinelIsCurrent = false; // true if last sentinel ping was actual
    // MSB is always 0, other 3 bits corresponds to x.x.x version scheme
    int sentinelVersion = DEFAULT_SENTINEL_VERSION;

    Context context;

    MasternodePing(Context context) {
        super(context.getParams());
        this.context = context;
        vin = new TransactionInput(context.getParams(), null, null, new TransactionOutPoint(context.getParams(), 0, Sha256Hash.ZERO_HASH));
    }

    MasternodePing(Context context, TransactionOutPoint outPoint)
    {
        //used by masternodes only
        throw new NotImplementedException();
    }

    MasternodePing(NetworkParameters params, byte[] bytes)
    {
        super(params, bytes, 0);
        this.context = Context.get();
    }

    MasternodePing(NetworkParameters params, byte[] bytes, int cursor) {
        super(params, bytes, cursor);
        this.context = Context.get();
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
        cursor += MasternodeSignature.calcLength(buf, cursor);

        cursor += 1;

        cursor += 4;

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

        cursor += 1;

        cursor += 4;

        return cursor;

    }

    @Override
    protected void parse() throws ProtocolException {

        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        blockHash = readHash();

        sigTime = readInt64();

        vchSig = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig.getMessageSize();

        if(cursor == length) { //this will be a bug
            sentinelIsCurrent = false;
            sentinelVersion = DEFAULT_SENTINEL_VERSION;
        }
        else {
            byte b[] = readBytes(1);
            sentinelIsCurrent = b[0] == 1;
            sentinelVersion = (int)readUint32();
        }

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        stream.write(blockHash.getReversedBytes());
        int64ToByteStreamLE(sigTime, stream);
        vchSig.bitcoinSerialize(stream);
        stream.write(sentinelIsCurrent ? 1 : 0);
        Utils.uint32ToByteStreamLE(sentinelVersion, stream);
    }

    boolean isExpired() { return Utils.currentTimeSeconds() - sigTime > MASTERNODE_NEW_START_REQUIRED_SECONDS; }

    boolean checkAndUpdate(Masternode mn, boolean fromNewBroadcast, Dos nDos)
    {
        if(!simpleCheck(nDos))
        {
            return false;
        }

        if (mn == null) {
            log.info("masternode--CMasternodePing::CheckAndUpdate -- Couldn't find Masternode entry, masternode={}", vin.getOutpoint().toStringShort());
            return false;
        }

        if(!fromNewBroadcast) {
            if(mn.isUpdateRequired())
            {
                log.info("masternode--CMasternodePing::CheckAndUpdate -- masternode protocol is outdated, masternode={}", vin.getOutpoint().toStringShort());
                return false;
            }

            if (mn.isNewStartRequired()) {
                log.info("masternode--CMasternodePing::CheckAndUpdate -- masternode is completely expired, new start is required, masternode={}", vin.getOutpoint().toStringShort());
                return false;
            }
        }

        try {

            StoredBlock storedBlock = context.masternodeManager.blockChain.getBlockStore().get(blockHash);

            if(storedBlock != null) {

                if (storedBlock.getHeight() < context.masternodeManager.blockChain.getChainHead().getHeight() - 24) {
                    log.info("CMasternodePing::CheckAndUpdate - Masternode {} block hash {} is too old", vin.toString(), blockHash.toString());
                    return false;
                }
            }
        } catch (BlockStoreException x) {
            log.info("CMasternodePing::CheckAndUpdate - Masternode {} block hash {} is too old", vin.toString(), blockHash.toString());
            return false;
        }

        log.info("masternode - CMasternodePing::CheckAndUpdate - New Ping - "+ getHash().toString() +" - "+ blockHash.toString()+" - "+ sigTime);

        // update only if there is no known ping for this masternode or
        // last ping was more then MASTERNODE_MIN_MNP_SECONDS-60 ago comparing to this one
        if(mn.isPingedWithin(MASTERNODE_MIN_MNP_SECONDS - 60, sigTime)) {
            log.info("masternode--CMasternodePing::CheckAndUpdate -- Masternode ping arrived too early, masternode"+ vin.getOutpoint().toStringShort());
            return false;
        }
        if(!checkSignature(mn.info.pubKeyMasternode))
            return false;
        // so, ping seems to be ok

        // if we are still syncing and there was no known ping for this mn for quite a while
        // (NOTE: assuming that MASTERNODE_EXPIRATION_SECONDS/2 should be enough to finish mn list sync)
        if(!context.masternodeSync.isMasternodeListSynced() && !mn.isPingedWithin(MASTERNODE_EXPIRATION_SECONDS/2)) {
            // let's bump sync timeout
            log.info("masternode--CMasternodePing::CheckAndUpdate -- bumping sync timeout, masternode={}", vin.getOutpoint().toStringShort());
            context.masternodeSync.BumpAssetLastTime("CMasternodePing::CheckAndUpdate");
        }

        // let's store this ping as the last one
        log.info("masternode--CMasternodePing::CheckAndUpdate -- Masternode ping accepted, masternode={}", vin.getOutpoint().toStringShort());
        mn.lastPing = this;

        // and update mnodeman.mapSeenMasternodeBroadcast.lastPing which is probably outdated
        MasternodeBroadcast mnb = new MasternodeBroadcast(mn);
        Sha256Hash hash = mnb.getHash();
        if (context.masternodeManager.mapSeenMasternodeBroadcast.containsKey(hash)) {
            context.masternodeManager.mapSeenMasternodeBroadcast.get(hash).getSecond().lastPing = this;
        }

        // force update, ignoring cache
        mn.check(true);
        // relay ping for nodes in ENABLED/EXPIRED/WATCHDOG_EXPIRED state only, skip everyone else
        if (!mn.isEnabled() && !mn.isExpired() && !mn.isWatchdogExpired()) return false;

        log.info("masternode--CMasternodePing::CheckAndUpdate -- Masternode ping acceepted and relayed, masternode={}", vin.getOutpoint().toStringShort());
        relay();

        return true;
    }

    boolean sign(ECKey keyMasternode, PublicKey publicKeyMasternode)
    {
        sigTime = Utils.currentTimeSeconds();
        String message = vin.toStringCpp() + blockHash + sigTime;
        StringBuilder errorMessage = new StringBuilder();

        try {
            MasternodeSignature signature = MessageSigner.signMessage(message, keyMasternode);
            if(!MessageSigner.verifyMessage(publicKeyMasternode, signature, message, errorMessage)) {
                log.error("MasternodePing::sign -=- verifyMessage failed, error:" + errorMessage);
            }
        }
        catch (KeyCrypterException kce) {
            log.error("MasternodePing::sign -=- signMessage failed");
            return false;
        }

        return true;

    }

    boolean checkSignature(PublicKey pubKeyMasternode) {
        String strMessage = vin.toStringCpp() + blockHash.toString() + sigTime;
        StringBuilder errorMessage = new StringBuilder();

        if (!MessageSigner.verifyMessage(pubKeyMasternode, vchSig, strMessage, errorMessage)) {
            log.info("CMasternodePing::CheckSignature - Got bad Masternode ping signature " + vin.getOutpoint().toStringShort() + " Error " + errorMessage);
            return false;
        }
        return true;
    }

    public boolean simpleCheck(Dos nDos) {
        nDos.set(0);
        if (sigTime > Utils.currentTimeSeconds() + 60 * 60) {
            log.info("CMasternodePing::SimpleCheck -- Signature rejected, too far into the future, masternode="+ vin.getOutpoint().toStringShort());
            nDos.set(1);
            return false;
        }

        try {
            StoredBlock mi = context.blockChain.getBlockStore().get(blockHash);
            if (mi == null) {
                log.info("masternode--CMasternodePing::SimpleCheck -- Masternode ping is invalid, unknown block hash: masternode={} blockHash={}", vin.getOutpoint().toStringShort(), blockHash);
                // maybe we stuck or forked so we shouldn't ban this node, just fail to accept this ping
                // TODO: or should we also request this block?
                return false;
            }
        }
        catch (BlockStoreException x)
        {
            log.info("masternode--CMasternodePing::SimpleCheck -- Masternode ping is invalid, unknown block hash: masternode={} blockHash={} with Exception: {}", vin.getOutpoint().toStringShort(), blockHash, x.getMessage());
        }

        log.info("masternode--CMasternodePing::SimpleCheck -- Masternode ping verified: masternode={}  blockHash={}  sigTime={}", vin.getOutpoint().toStringShort(), blockHash, sigTime);
        return true;
    }

    public boolean equals(Object o)
    {
        MasternodePing mnp = (MasternodePing)o;
        try {
            if (mnp.vin == null && this.vin == null)
                return true; //check for Empty
            if (mnp.vin != null && mnp.vin.equals(this.vin) &&
                    mnp.blockHash != null && mnp.blockHash.equals(this.blockHash)) {
                return true;
            }
        }
        catch (NullPointerException npe)
        {
            log.warn(npe.getMessage());
        }
        return false;
    }

    public static MasternodePing EMPTY = new MasternodePing(Context.get());

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
    String getHexData() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(400);
            bitcoinSerialize(bos);
            return Utils.HEX.encode(bos.toByteArray());
        } catch (IOException x) {
            return "";
        }
    }
}
