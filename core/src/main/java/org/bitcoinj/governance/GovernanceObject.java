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
package org.bitcoinj.governance;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultimap;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.darkcoinj.DarkSendSigner;
import org.darkcoinj.InstantSend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.manager.GovernanceManager.MAX_GOVERNANCE_OBJECT_DATA_SIZE;
import static org.darkcoinj.InstantSend.INSTANTSEND_TIMEOUT_SECONDS;

public class GovernanceObject extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(GovernanceObject.class);

    // critical section to protect the inner data structures
    ReentrantLock lock = Threading.lock("MasternodeManager");

    /// Object typecode
    private int nObjectType;

    /// parent object, 0 is root
    private Sha256Hash nHashParent;

    /// object revision in the system
    private int nRevision;

    /// time this object was created
    private long nTime;

    /// time this object was marked for deletion
    private long nDeletionTime;

    /// fee-tx
    private Sha256Hash nCollateralHash;

    /// Data field - can be used for anything
    private String strData;

    /// Masternode info for signed objects
    private TransactionInput vinMasternode;

    private MasternodeSignature vchSig;

    /// is valid by blockchain
    private boolean fCachedLocalValidity;
    private String strLocalValidityError;

    // VARIOUS FLAGS FOR OBJECT / SET VIA MASTERNODE VOTING

    /// true == minimum network support has been reached for this object to be funded (doesn't mean it will for sure though)
    private boolean fCachedFunding;

    /// true == minimum network has been reached flagging this object as a valid and understood governance object (e.g, the serialized data is correct format, etc)
    private boolean fCachedValid;

    /// true == minimum network support has been reached saying this object should be deleted from the system entirely
    private boolean fCachedDelete;

    /** true == minimum network support has been reached flagging this object as endorsed by an elected representative body
     * (e.g. business review board / technecial review board /etc)
     */
    private boolean fCachedEndorsed;

    /// object was updated and cached values should be updated soon
    private boolean fDirtyCache;

    /// Object is no longer of interest
    private boolean fExpired;

    /// Failed to parse object data
    private boolean fUnparsable;

    private TreeMap<TransactionOutPoint, VoteRecord> mapCurrentMNVotes;

    /// Limited map of votes orphaned by MN
    private /*CacheMultiMap*/LinkedHashMultimap<TransactionOutPoint, Pair<Integer, GovernanceVote>> mapOrphanVotes = LinkedHashMultimap.create(100,100);

    private GovernanceObjectVoteFile fileVotes;


    public GovernanceObject(NetworkParameters params, byte[] payload)
    {
        super(params, payload, 0);
    }


    public final long getCreationTime() {
        return nTime;
    }

    public final long getDeletionTime() {
        return nDeletionTime;
    }

    public final int getObjectType() {
        return nObjectType;
    }

    public final Sha256Hash getCollateralHash() {
        return nCollateralHash;
    }

    public final TransactionInput getMasternodeVin() {
        return vinMasternode;
    }

    public final boolean isSetCachedFunding() {
        return fCachedFunding;
    }

    public final boolean isSetCachedValid() {
        return fCachedValid;
    }

    public final boolean isSetCachedDelete() {
        return fCachedDelete;
    }

    public final boolean isSetCachedEndorsed() {
        return fCachedEndorsed;
    }

    public final boolean isSetDirtyCache() {
        return fDirtyCache;
    }

    public final boolean isSetExpired() {
        return fExpired;
    }

    public final void invalidateVoteCache() {
        fDirtyCache = true;
    }

    public final GovernanceObjectVoteFile getVoteFile() {
        return fileVotes;
    }


    protected static int calcLength(byte[] buf, int offset) {
        int cursor = offset;// + 4;

        return cursor - offset;
    }
    @Override
    protected void parse() throws ProtocolException {
        nHashParent = readHash();
        nRevision = (int)readUint32();
        nTime = readInt64();
        nCollateralHash = readHash();
        strData = readStr();
        if(strData.length() > MAX_GOVERNANCE_OBJECT_DATA_SIZE)
            throw new ProtocolException("String length limit exceeded");
        nObjectType = (int)readUint32();
        vinMasternode = new TransactionInput(params, null, payload, cursor);
        cursor += vinMasternode.getMessageSize();
        vchSig = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig.getMessageSize();
        length = cursor - offset;

        // AFTER DESERIALIZATION OCCURS, CACHED VARIABLES MUST BE CALCULATED MANUALLY
    }
    void parseFromDisk() {
        parse();
        nDeletionTime = readInt64();
        fExpired = readBytes(1)[0] == 0 ? false : true;
        int size = (int)readVarInt();
        mapCurrentMNVotes = new TreeMap<TransactionOutPoint, VoteRecord>();
        for(int i = 0; i < size; ++i) {
            TransactionOutPoint vin = new TransactionOutPoint(params, payload, offset);
            cursor += vin.getMessageSize();
            VoteRecord vr = new VoteRecord(params, payload, offset);
            cursor += vr.getMessageSize();
            mapCurrentMNVotes.put(vin, vr);
        }
        fileVotes = new GovernanceObjectVoteFile();
        cursor = fileVotes.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(nHashParent.getReversedBytes());
        Utils.uint32ToByteStreamLE(nRevision, stream);
        Utils.int64ToByteStreamLE(nTime, stream);
        stream.write(nCollateralHash.getReversedBytes());
        Utils.stringToByteStream(strData, stream);
        Utils.uint32ToByteStreamLE(nObjectType, stream);
        vinMasternode.bitcoinSerialize(stream);
        vchSig.bitcoinSerialize(stream);
    }

    public void serializeToDisk(OutputStream stream) throws IOException {
        bitcoinSerializeToStream(stream);
        log.info("gobject--CGovernanceObject::SerializationOp writing votes to disk");
        Utils.int64ToByteStreamLE(nDeletionTime, stream);
        stream.write((byte)(fExpired ? 0 : 1));
        stream.write(new VarInt(mapCurrentMNVotes.size()).encode());
        Iterator<Map.Entry<TransactionOutPoint, VoteRecord>> it =  mapCurrentMNVotes.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<TransactionOutPoint, VoteRecord> entry = it.next();
            entry.getKey().bitcoinSerialize(stream);
            entry.getValue().bitcoinSerialize(stream);
        }
        fileVotes.bitcoinSerialize(stream);
        log.info("gobject--CGovernanceObject::SerializationOp hash = {}, vote count = {}", getHash().toString(), fileVotes.getVoteCount());
    }

    public String toString() {
        return "";
    }


    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(nHashParent.getReversedBytes());
            Utils.uint32ToByteStreamLE(nRevision, bos);
            Utils.int64ToByteStreamLE(nTime, bos);
            Utils.stringToByteStream(strData, bos);
            vinMasternode.bitcoinSerialize(bos);
            vchSig.bitcoinSerialize(bos);
            return Sha256Hash.twiceOf(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
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
