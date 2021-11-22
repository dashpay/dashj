/*
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

import com.google.common.collect.LinkedHashMultimap;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.json.*;

import static org.bitcoinj.governance.GovernanceException.Type.*;
import static org.bitcoinj.governance.GovernanceVote.MAX_SUPPORTED_VOTE_SIGNAL;
import static org.bitcoinj.governance.GovernanceVote.VoteOutcome;
import static org.bitcoinj.governance.GovernanceVote.VoteOutcome.VOTE_OUTCOME_ABSTAIN;
import static org.bitcoinj.governance.GovernanceVote.VoteOutcome.VOTE_OUTCOME_NO;
import static org.bitcoinj.governance.GovernanceVote.VoteOutcome.VOTE_OUTCOME_YES;
import static org.bitcoinj.governance.GovernanceVote.VoteSignal;
import static org.bitcoinj.governance.GovernanceVote.VoteSignal.*;

public class GovernanceObject extends Message implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(GovernanceObject.class);

    // critical section to protect the inner data structures
    ReentrantLock lock = Threading.lock("MasternodeManager");

    public static final int MAX_GOVERNANCE_OBJECT_DATA_SIZE = 16 * 1024;
    public static final int MIN_GOVERNANCE_PEER_PROTO_VERSION = 70206;
    public static final int GOVERNANCE_FILTER_PROTO_VERSION = 70206;

    public static final double GOVERNANCE_FILTER_FP_RATE = 0.001;

    public static final int GOVERNANCE_OBJECT_UNKNOWN = 0;
    public static final int GOVERNANCE_OBJECT_PROPOSAL = 1;
    public static final int GOVERNANCE_OBJECT_TRIGGER = 2;
    public static final int GOVERNANCE_OBJECT_WATCHDOG = 3;

    public static final Coin GOVERNANCE_PROPOSAL_FEE_TX = Coin.valueOf(5, 0);

    public static final long GOVERNANCE_FEE_CONFIRMATIONS = 6;
    public static final long GOVERNANCE_MIN_RELAY_FEE_CONFIRMATIONS = 1;
    public static final long GOVERNANCE_UPDATE_MIN = 60*60;
    public static final long GOVERNANCE_DELETION_DELAY = 10*60;
    public static final long GOVERNANCE_ORPHAN_EXPIRATION_TIME = 10*60;
    public static final long GOVERNANCE_WATCHDOG_EXPIRATION_TIME = 2*60*60;

    public static final int GOVERNANCE_TRIGGER_EXPIRATION_BLOCKS = 576;

// FOR SEEN MAP ARRAYS - GOVERNANCE OBJECTS AND VOTES
    static final int SEEN_OBJECT_IS_VALID = 0;
    static final int SEEN_OBJECT_ERROR_INVALID = 1;
    static final int SEEN_OBJECT_ERROR_IMMATURE = 2;
    static final int SEEN_OBJECT_EXECUTED = 3; //used for triggers
    static final int SEEN_OBJECT_UNKNOWN = 4; // the default

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
    private byte [] data;

    /// Masternode info for signed objects
    private TransactionOutPoint masternodeOutpoint;

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

    public void setDirtyCache(boolean fDirtyCache) {
        this.fDirtyCache = fDirtyCache;
        unCache();
    }

    /// Object is no longer of interest
    private boolean fExpired;

    /// Failed to parse object data
    private boolean fUnparsable;

    protected HashMap<TransactionOutPoint, VoteRecord> mapCurrentMNVotes;

    /// Limited map of votes orphaned by MN
    private /*CacheMultiMap*/LinkedHashMultimap<TransactionOutPoint, Pair<Integer, GovernanceVote>> mapOrphanVotes = LinkedHashMultimap.create(100,100);

    private GovernanceObjectVoteFile fileVotes;

    Context context;
    public GovernanceObject(NetworkParameters params) {
        super(params);
    }

    public GovernanceObject(NetworkParameters params, byte[] payload) {
        super(params, payload, 0);
        context = Context.get();
        mapCurrentMNVotes = new HashMap<TransactionOutPoint, VoteRecord>();
    }

    public GovernanceObject(NetworkParameters params, byte[] payload, int cursor) {
        super(params, payload, cursor);
        context = Context.get();
        mapCurrentMNVotes = new HashMap<TransactionOutPoint, VoteRecord>();
    }

    public final long getCreationTime() {
        return nTime;
    }

    public final long getDeletionTime() {
        return nDeletionTime;
    }

    public final void setDeletionTime(long deletionTime) {
        nDeletionTime = deletionTime;
    }

    public final int getObjectType() {
        return nObjectType;
    }

    public final Sha256Hash getCollateralHash() {
        return nCollateralHash;
    }

    public final TransactionOutPoint getMasternodeOutpoint() {
        return masternodeOutpoint;
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
    public final void setExpired(boolean expired) {
        fExpired = expired;
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
        data = readByteArray();
        nObjectType = (int)readUint32();
        masternodeOutpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += masternodeOutpoint.getMessageSize();
        vchSig = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig.getMessageSize();
        length = cursor - offset;

        fileVotes = new GovernanceObjectVoteFile();

        // AFTER DESERIALIZATION OCCURS, CACHED VARIABLES MUST BE CALCULATED MANUALLY
    }
    void parseFromDisk() {
        nDeletionTime = readInt64();
        fExpired = readBytes(1)[0] == 0 ? false : true;
        int size = (int)readVarInt();
        mapCurrentMNVotes = new HashMap<TransactionOutPoint, VoteRecord>();
        for(int i = 0; i < size; ++i) {
            TransactionOutPoint vin = new TransactionOutPoint(params, payload, offset);
            cursor += vin.getMessageSize();
            VoteRecord vr = new VoteRecord(params, payload, offset);
            cursor += vr.getMessageSize();
            mapCurrentMNVotes.put(vin, vr);
        }
        fileVotes = new GovernanceObjectVoteFile(params, payload, offset);
        cursor += fileVotes.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(nHashParent.getReversedBytes());
        Utils.uint32ToByteStreamLE(nRevision, stream);
        Utils.int64ToByteStreamLE(nTime, stream);
        stream.write(nCollateralHash.getReversedBytes());
        Utils.bytesToByteStream(data, stream);
        Utils.uint32ToByteStreamLE(nObjectType, stream);
        masternodeOutpoint.bitcoinSerialize(stream);
        vchSig.bitcoinSerialize(stream);
    }

    public void serializeToDisk(OutputStream stream) throws IOException {
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
        return "GovernanceObject: " + getDataAsPlainString().substring(0, 100);
    }


    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(nHashParent.getReversedBytes());
            Utils.uint32ToByteStreamLE(nRevision, bos);
            Utils.int64ToByteStreamLE(nTime, bos);
            Utils.stringToByteStream(getDataAsHexString(), bos);
            new TransactionInput(params, null, new byte[0], masternodeOutpoint).bitcoinSerialize(bos);
            vchSig.bitcoinSerialize(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Sha256Hash getSignatureHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(nHashParent.getReversedBytes());
            Utils.uint32ToByteStreamLE(nRevision, bos);
            Utils.int64ToByteStreamLE(nTime, bos);
            bos.write(nCollateralHash.getReversedBytes());
            Utils.bytesToByteStream(data, bos);
            Utils.uint32ToByteStreamLE(nObjectType, bos);
            masternodeOutpoint.bitcoinSerialize(bos);
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

    public void updateLocalValidity() {
        //LOCK(cs_main); how to do this?
        // THIS DOES NOT CHECK COLLATERAL, THIS IS CHECKED UPON ORIGINAL ARRIVAL
        fCachedLocalValidity = isValidLocally(strLocalValidityError, false);
    }
    public static class Validity {
        public String strError = "";
        public boolean fMissingMasternode = false;
        public boolean fMissingConfirmations = false;
    }

    public boolean isValidLocally(String strError, boolean fCheckCollateral) {
        Validity validity = new Validity();
        return isValidLocally(validity, fCheckCollateral);
    }
    public boolean isValidLocally(Validity validity, boolean fCheckCollateral) {
        validity.fMissingMasternode = false;
        validity.fMissingConfirmations = false;

        if (fUnparsable) {
            validity.strError = "Object data unparseable";
            return false;
        }

        switch (nObjectType) {
            case GOVERNANCE_OBJECT_PROPOSAL:
            case GOVERNANCE_OBJECT_TRIGGER:
            case GOVERNANCE_OBJECT_WATCHDOG:
                break;
            default:
                validity.strError = String.format("Invalid object type %d", nObjectType);
                return false;
        }

        // IF ABSOLUTE NO COUNT (NO-YES VALID VOTES) IS MORE THAN 10% OF THE NETWORK MASTERNODES, OBJ IS INVALID

        // CHECK COLLATERAL IF REQUIRED (HIGH CPU USAGE)

        if (fCheckCollateral) {
            if ((nObjectType == GOVERNANCE_OBJECT_TRIGGER) || (nObjectType == GOVERNANCE_OBJECT_WATCHDOG)) {
                String strOutpoint = masternodeOutpoint.toStringShort();
                Masternode infoMn = context.masternodeListManager.getListAtChainTip().getMNByCollateral(masternodeOutpoint);
                if (infoMn == null ) {

                    /* TODO:  fix this
                    Pair<Masternode.CollateralStatus, Integer> status = Masternode.checkCollateral(masternodeOutpoint);

                    Masternode.CollateralStatus err = status.getFirst();
                    if (err == Masternode.CollateralStatus.COLLATERAL_OK) {
                        validity.fMissingMasternode =  true;
                        validity.strError = "Masternode not found: " + strOutpoint;
                    } else if (err == Masternode.CollateralStatus.COLLATERAL_UTXO_NOT_FOUND) {
                        validity.strError = "Failed to find Masternode UTXO, missing masternode=" + strOutpoint + "\n";
                    } else if (err == Masternode.CollateralStatus.COLLATERAL_INVALID_AMOUNT) {
                        validity.strError = "Masternode UTXO should have 1000 DASH, missing masternode=" + strOutpoint + "\n";
                    }
                    */

                    return false;
                }

                // Check that we have a valid MN signature
                if (!checkSignature(infoMn.getPubKeyOperator())) {
                    validity.strError = "Invalid masternode signature for: " + strOutpoint + ", pubkey id = " + infoMn.getPubKeyOperator().toString();
                    return false;
                }

                return true;
            }

            if (!isCollateralValid(validity)) {
                return false;
            }
        }

		/*
		    TODO

		    - There might be an issue with multisig in the coinbase on mainnet, we will add support for it in a future release.
		    - Post 12.2+ (test multisig coinbase transaction)
		*/

        // 12.1 - todo - compile error
        // if(address.IsPayToScriptHash()) {
        //     strError = "Governance system - multisig is not currently supported";
        //     return false;
        // }

        return true;
    }

    @Deprecated
    boolean checkSignature(PublicKey pubKeyMasternode)
    {
        StringBuilder strError = new StringBuilder();

        String strMessage = getSignatureMessage();

        if (!MessageSigner.verifyMessage(pubKeyMasternode, vchSig, strMessage, strError)) {
            log.error("CGovernance::CheckSignature -- VerifyMessage() failed, error: {}", strError);
            return false;
        }
        return true;
    }

    boolean checkSignature(BLSPublicKey pubKey) {
        BLSSignature sig = new BLSSignature(vchSig.getBytes());
        if (!sig.verifyInsecure(pubKey, getSignatureHash())) {
            log.error("GovernanceObject.CheckSignature -- VerifyInsecure() failed\n");
            return false;
        }
        return true;
    }

    Coin getMinCollateralFee()
    {
        // Only 1 type has a fee for the moment but switch statement allows for future object types
        switch(nObjectType) {
            case GOVERNANCE_OBJECT_PROPOSAL:    return GOVERNANCE_PROPOSAL_FEE_TX;
            case GOVERNANCE_OBJECT_TRIGGER:     return Coin.ZERO;
            case GOVERNANCE_OBJECT_WATCHDOG:    return Coin.ZERO;
            default:                            return NetworkParameters.MAX_MONEY;
        }
    }

    boolean isCollateralValid(Validity validity)
    {
        validity.strError = "";
        validity.fMissingConfirmations = false;
        Coin nMinFee = getMinCollateralFee();
        Sha256Hash nExpectedHash = getHash();

        Transaction txCollateral;
        Sha256Hash nBlockHash;

        // RETRIEVE TRANSACTION IN QUESTION

        /*if(!GetTransaction(nCollateralHash, txCollateral, Params().GetConsensus(), nBlockHash, true)){
            strError = strprintf("Can't find collateral tx %s", txCollateral.ToString());
            log.info("CGovernanceObject::IsCollateralValid -- %s\n", strError);
            return false;
        }

        if(txCollateral.vout.size() < 1) {
            strError = strprintf("tx vout size less than 1 | %d", txCollateral.vout.size());
            log.info("CGovernanceObject::IsCollateralValid -- %s\n", strError);
            return false;
        }

        // LOOK FOR SPECIALIZED GOVERNANCE SCRIPT (PROOF OF BURN)

        CScript findScript;
        findScript << OP_RETURN << ToByteVector(nExpectedHash);

        DBG( cout << "IsCollateralValid: txCollateral.vout.size() = " << txCollateral.vout.size() << endl; );

        DBG( cout << "IsCollateralValid: findScript = " << ScriptToAsmStr( findScript, false ) << endl; );

        DBG( cout << "IsCollateralValid: nMinFee = " << nMinFee << endl; );


        bool foundOpReturn = false;
        BOOST_FOREACH(const CTxOut o, txCollateral.vout) {
        DBG( cout << "IsCollateralValid txout : " << o.ToString()
                << ", o.nValue = " << o.nValue
                << ", o.scriptPubKey = " << ScriptToAsmStr( o.scriptPubKey, false )
                << endl; );
        if(!o.scriptPubKey.IsPayToPublicKeyHash() && !o.scriptPubKey.IsUnspendable()) {
            strError = strprintf("Invalid Script %s", txCollateral.ToString());
            log.info ("CGovernanceObject::IsCollateralValid -- %s\n", strError);
            return false;
        }
        if(o.scriptPubKey == findScript && o.nValue >= nMinFee) {
            DBG( cout << "IsCollateralValid foundOpReturn = true" << endl; );
            foundOpReturn = true;
        }
        else  {
            DBG( cout << "IsCollateralValid No match, continuing" << endl; );
        }

    }

        if(!foundOpReturn){
            strError = strprintf("Couldn't find opReturn %s in %s", nExpectedHash.ToString(), txCollateral.ToString());
            log.info ("CGovernanceObject::IsCollateralValid -- %s\n", strError);
            return false;
        }

        // GET CONFIRMATIONS FOR TRANSACTION

        AssertLockHeld(cs_main);
        int nConfirmationsIn = instantsend.GetConfirmations(nCollateralHash);
        if (nBlockHash != uint256()) {
            BlockMap::iterator mi = mapBlockIndex.find(nBlockHash);
            if (mi != mapBlockIndex.end() && (*mi).second) {
                CBlockIndex* pindex = (*mi).second;
                if (chainActive.Contains(pindex)) {
                    nConfirmationsIn += chainActive.Height() - pindex->nHeight + 1;
                }
            }
        }

        if(nConfirmationsIn < GOVERNANCE_FEE_CONFIRMATIONS) {
            strError = strprintf("Collateral requires at least %d confirmations to be relayed throughout the network (it has only %d)", GOVERNANCE_FEE_CONFIRMATIONS, nConfirmationsIn);
            if (nConfirmationsIn >= GOVERNANCE_MIN_RELAY_FEE_CONFIRMATIONS) {
                fMissingConfirmations = true;
                strError += ", pre-accepted -- waiting for required confirmations";
            } else {
                strError += ", rejected -- try again later";
            }
            log.info ("CGovernanceObject::IsCollateralValid -- %s\n", strError);

            return false;
        }*/

        validity.strError = "valid";
        return true;
    }

    /**
     *   GetData - As
     *   --------------------------------------------------------
     *
     */

    public String getDataAsHexString() {
        return Utils.HEX.encode(data);
    }

    public String getDataAsPlainString() {
        return new String(data);
    }

    public void updateSentinelVariables() {
        // CALCULATE MINIMUM SUPPORT LEVELS REQUIRED
        if(context.masternodeListManager.getLock().isHeldByCurrentThread()) {

        }
        int nMnCount = context.masternodeListManager.getListAtChainTip().countEnabled();
        if (nMnCount == 0) {
            return;
        }

        // CALCULATE THE MINUMUM VOTE COUNT REQUIRED FOR FULL SIGNAL

        // todo - 12.1 - should be set to `10` after governance vote compression is implemented
        int nAbsVoteReq = Math.max(params.getGovernanceMinQuorum(), nMnCount / 10);
        int nAbsDeleteReq = Math.max(params.getGovernanceMinQuorum(), (2 * nMnCount) / 3);
        // todo - 12.1 - Temporarily set to 1 for testing - reverted
        //nAbsVoteReq = 1;

        // SET SENTINEL FLAGS TO FALSE

        fCachedFunding = false;
        fCachedValid = true; //default to valid
        fCachedEndorsed = false;
        fDirtyCache = false;

        // SET SENTINEL FLAGS TO TRUE IF MIMIMUM SUPPORT LEVELS ARE REACHED
        // ARE ANY OF THESE FLAGS CURRENTLY ACTIVATED?

        if (getAbsoluteYesCount(VOTE_SIGNAL_FUNDING) >= nAbsVoteReq) {
            fCachedFunding = true;
        }
        if ((getAbsoluteYesCount(VOTE_SIGNAL_DELETE) >= nAbsDeleteReq) && !fCachedDelete) {
            fCachedDelete = true;
            if (nDeletionTime == 0) {
                nDeletionTime = Utils.currentTimeSeconds();
            }
        }
        if (getAbsoluteYesCount(VOTE_SIGNAL_ENDORSED) >= nAbsVoteReq) {
            fCachedEndorsed = true;
        }

        if (getAbsoluteNoCount(VOTE_SIGNAL_VALID) >= nAbsVoteReq) {
            fCachedValid = false;
        }
    }

    public int countMatchingVotes(VoteSignal eVoteSignalIn, VoteOutcome eVoteOutcomeIn) {
        int nCount = 0;
        for (Map.Entry<TransactionOutPoint, VoteRecord> it : mapCurrentMNVotes.entrySet()) {
            final VoteRecord recVote = it.getValue();
            VoteInstance voteInstance = recVote.mapInstances.get(eVoteSignalIn.value);
            if (voteInstance == null) {
                continue;
            }
            if (voteInstance.eOutcome == eVoteOutcomeIn) {
                ++nCount;
            }
        }
        return nCount;
    }

    public int getAbsoluteYesCount(VoteSignal eVoteSignalIn) {
        return getYesCount(eVoteSignalIn) - getNoCount(eVoteSignalIn);
    }
    public int getAbsoluteNoCount(VoteSignal eVoteSignalIn) {
        return getNoCount(eVoteSignalIn) - getYesCount(eVoteSignalIn);
    }
    public int getYesCount(VoteSignal eVoteSignalIn) {
        return countMatchingVotes(eVoteSignalIn, VOTE_OUTCOME_YES);
    }

    public int getNoCount(VoteSignal eVoteSignalIn) {
        return countMatchingVotes(eVoteSignalIn, VOTE_OUTCOME_NO);
    }

    public int getAbstainCount(VoteSignal eVoteSignalIn) {
        return countMatchingVotes(eVoteSignalIn, VOTE_OUTCOME_ABSTAIN);
    }

    public Pair<Boolean, VoteRecord> getCurrentMNVotes(TransactionOutPoint mnCollateralOutpoint) {
        Pair<Boolean, VoteRecord> result = new Pair<Boolean, VoteRecord>(false, null); //default to failure
        VoteRecord it = mapCurrentMNVotes.get(mnCollateralOutpoint);
        if (it == null) {
            return result;
        }
        result.setFirst(true); //success
        result.setSecond(it);
        return result;
    }

    public void relay() {
        // Do not relay
    }

    public boolean processVote(Peer pfrom, GovernanceVote vote, GovernanceException exception) {
        if (context.masternodeSync.syncFlags.contains(MasternodeSync.SYNC_FLAGS.SYNC_MASTERNODE_LIST) &&
                context.masternodeListManager.getListAtChainTip().getMNByCollateral(vote.getMasternodeOutpoint()) == null) {
            String message = "CGovernanceObject::ProcessVote -- Masternode index not found";
            exception.setException(message, GOVERNANCE_EXCEPTION_WARNING);
            if (mapOrphanVotes.put(vote.getMasternodeOutpoint(), new Pair<Integer, GovernanceVote>((int)(Utils.currentTimeSeconds() + GOVERNANCE_ORPHAN_EXPIRATION_TIME), vote))) {
                if (pfrom != null) {
                    //TODO: context.masternodeManager.askForMN(pfrom, vote.getMasternodeOutpoint());
                }
                log.info("{}", message);
            } else {
                log.info("gobject--{}", message);
            }
            return false;
        }

        VoteRecord recVote = mapCurrentMNVotes.get(vote.getMasternodeOutpoint());
        if (recVote == null) {
            recVote = new VoteRecord(params);
            mapCurrentMNVotes.put(vote.getMasternodeOutpoint(), recVote);
        }

        VoteSignal eSignal = vote.getSignal();
        if (eSignal == VOTE_SIGNAL_NONE) {
            String signalMessage = "CGovernanceObject::ProcessVote -- Vote signal: none";
            log.info("gobject--{}", signalMessage);
            exception.setException(signalMessage, GOVERNANCE_EXCEPTION_WARNING);
            return false;
        }
        if (eSignal.getValue() > MAX_SUPPORTED_VOTE_SIGNAL) {
            String signalMessage = "CGovernanceObject::ProcessVote -- Unsupported vote signal: " + GovernanceVoting.convertSignalToString(vote.getSignal());
            log.info("{}", signalMessage);
            exception.setException(signalMessage, GOVERNANCE_EXCEPTION_PERMANENT_ERROR, 20);
            return false;
        }
        VoteInstance voteInstance = recVote.mapInstances.get(eSignal.getValue());
        if (voteInstance == null) {
            voteInstance = new VoteInstance(params);
            recVote.mapInstances.put(eSignal.getValue(), voteInstance);
        }

        // Reject obsolete votes
        if (vote.getTimestamp() < voteInstance.nCreationTime) {
            String obMessage = "CGovernanceObject::ProcessVote -- Obsolete vote";
            log.info("gobject--{}", obMessage);
            exception.setException(obMessage, GOVERNANCE_EXCEPTION_NONE);
            return false;
        }

        long nNow = Utils.currentTimeSeconds();
        long nVoteTimeUpdate = voteInstance.nTime;
        if (context.governanceManager.areRateChecksEnabled()) {
            long nTimeDelta = nNow - voteInstance.nTime;
            if (nTimeDelta < GOVERNANCE_UPDATE_MIN) {
                String oftenMessage = "CGovernanceObject::ProcessVote -- Masternode voting too often, MN outpoint = " +
                        vote.getMasternodeOutpoint().toStringShort() + ", governance object hash = " + getHash().toString() +
                        ", time delta = " + nTimeDelta;
                log.info("gobject--{}", oftenMessage);
                exception.setException(oftenMessage, GOVERNANCE_EXCEPTION_TEMPORARY_ERROR);
                nVoteTimeUpdate = nNow;
                return false;
            }
        }
        // Finally check that the vote is actually valid (done last because of cost of signature verification)
        if (!vote.isValid(true)) {
            String validMessage = "CGovernanceObject::ProcessVote -- Invalid vote" + ", MN outpoint = " + vote.getMasternodeOutpoint().toStringShort() + ", governance object hash = " + getHash().toString() + ", vote hash = " + vote.getHash().toString();
            log.info("gobject--{}", validMessage);
            exception.setException(validMessage, GOVERNANCE_EXCEPTION_PERMANENT_ERROR, 20);
            context.governanceManager.addInvalidVote(vote);
            return false;
        }
        if (!context.masternodeMetaDataManager.addGovernanceVote(vote.getMasternodeOutpoint(), vote.getParentHash())) {
            String unableMessage = "CGovernanceObject::ProcessVote -- Unable to add governance vote" + ", MN outpoint = " + vote.getMasternodeOutpoint().toStringShort() + ", governance object hash = " + getHash().toString();
            log.info("gobject--{}", unableMessage);
            exception.setException(unableMessage, GOVERNANCE_EXCEPTION_PERMANENT_ERROR);
            return false;
        }
        voteInstance = new VoteInstance(params, vote.getOutcome(), nVoteTimeUpdate, vote.getTimestamp());
        if (!fileVotes.hasVote(vote.getHash())) {
            fileVotes.addVote(vote);
        }
        fDirtyCache = true;
        return true;
    }

    public JSONObject getJSONObject() {
        JSONTokener parser = new JSONTokener(getDataAsPlainString());
        JSONObject jsonObject = new JSONObject(parser);
        return jsonObject;
    }

    public void clearMasternodeVotes() {
        Iterator<Map.Entry<TransactionOutPoint, VoteRecord>> it = mapCurrentMNVotes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<TransactionOutPoint, VoteRecord> entry = it.next();
            if (context.masternodeListManager.getListAtChainTip().getMNByCollateral(entry.getKey()) == null) {
                fileVotes.removeVotesFromMasternode(entry.getKey());
                it.remove();
            }
        }
    }

    public String getSignatureMessage() {
        lock.lock();
        try {
            String strMessage = nHashParent.toString() + "|" +
                    nRevision + "|" +
                    nTime + "|" +
                    getDataAsHexString() + "|" +
                    masternodeOutpoint.toStringShort() + "|" +
                    nCollateralHash.toString();

            return strMessage;
        } finally {
            lock.unlock();
        }
    }

    public boolean sign(ECKey keyMasternode, PublicKey pubKeyMasternode) {
        StringBuilder strError = new StringBuilder();

        String strMessage = getSignatureMessage();
        if ((vchSig = MessageSigner.signMessage(strMessage, keyMasternode)) == null) {
            log.error("CGovernanceObject::Sign -- SignMessage() failed");
            return false;
        }

        if (!MessageSigner.verifyMessage(pubKeyMasternode, vchSig, strMessage, strError)) {
            log.error("CGovernanceObject::Sign -- VerifyMessage() failed, error: {}", strError);
            return false;
        }

        log.info("sign -- pubkey id = {}, masternode = {}", Utils.HEX.encode(pubKeyMasternode.getId()), masternodeOutpoint.toStringShort());
        return true;
    }



}
