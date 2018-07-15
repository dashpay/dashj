package org.bitcoinj.core;

import org.bitcoinj.net.Dos;
import org.bitcoinj.utils.Pair;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.core.CoinDefinition.PROTOCOL_VERSION;
import static org.bitcoinj.core.Context.fMasterNode;
import static org.bitcoinj.core.Masternode.CollateralStatus.COLLATERAL_SPV_ASSUME_VALID;
import static org.bitcoinj.core.Masternode.CollateralStatus.COLLATERAL_UTXO_NOT_FOUND;
import static org.bitcoinj.core.MasternodeInfo.State.*;

/**
 * Created by Hash Engineering on 2/8/2015.
 */
public class Masternode extends Message {
    private static final Logger log = LoggerFactory.getLogger(Masternode.class);
    ReentrantLock lock = Threading.lock("Masternode");
    long lastTimeChecked;


    public enum CollateralStatus {
        COLLATERAL_OK,
        COLLATERAL_UTXO_NOT_FOUND,
        COLLATERAL_INVALID_AMOUNT,
        COLLATERAL_INVALID_PUBKEY,
        COLLATERAL_SPV_ASSUME_VALID, //SPV mode assumes valid
    }

    public static final int MASTERNODE_CHECK_SECONDS               =   5;
    public static final int MASTERNODE_MIN_MNB_SECONDS             =   5 * 60;
    public static final int MASTERNODE_MIN_MNP_SECONDS             =  10 * 60;
    public static final int MASTERNODE_EXPIRATION_SECONDS          =  65 * 60;
    public static final int MASTERNODE_SENTINEL_MAX_SECONDS = 120 * 60;
    public static final int MASTERNODE_NEW_START_REQUIRED_SECONDS  = 180 * 60;

    public static final int MASTERNODE_POSE_BAN_MAX_SCORE          = 5;

    MasternodeInfo info;
    MasternodePing lastPing;
    MasternodeSignature vchSig;

    Sha256Hash nCollateralMinConfBlockHash;
    int nBlockLastPaid;
    int nPoSeBanScore;
    int nPoSeBanHeight;
    boolean fAllowMixingTx;
    boolean fUnitTest;

    // KEEP TRACK OF GOVERNANCE ITEMS EACH MASTERNODE HAS VOTE UPON FOR RECALCULATION
    HashMap<Sha256Hash, Integer> mapGovernanceObjectsVotedOn;

    Context context;

    public Masternode(Context context)
    {
        super(context.getParams());
        this.context = context;

        info = new MasternodeInfo();

        nCollateralMinConfBlockHash = Sha256Hash.ZERO_HASH;
        nBlockLastPaid = 0;
        nPoSeBanScore = 0;
        nPoSeBanHeight = 0;
        fAllowMixingTx = false;
        fUnitTest = false;
        lastPing = MasternodePing.EMPTY;
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();
    }

    public Masternode(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
        context = Context.get();
    }

    public Masternode(Context context, byte [] payload, int cursor)
    {
        super(context.getParams(), payload, cursor);
        this.context = context;
    }

    public Masternode(Masternode other)
    {
        super(other.params);
        this.context = other.context;

        info = new MasternodeInfo(other.info);
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();
        for (Map.Entry<Sha256Hash, Integer> entry : other.mapGovernanceObjectsVotedOn.entrySet())
        {
            mapGovernanceObjectsVotedOn.put(entry.getKey(), entry.getValue());
        }
        nCollateralMinConfBlockHash = Sha256Hash.ZERO_HASH;
        this.lastPing = other.lastPing;
        this.vchSig = other.vchSig.duplicate();
        nCollateralMinConfBlockHash = Sha256Hash.wrap(other.nCollateralMinConfBlockHash.getBytes());
        nBlockLastPaid = other.nBlockLastPaid;
        nPoSeBanScore = other.nPoSeBanScore;
        nPoSeBanHeight = other.nPoSeBanHeight;
        fAllowMixingTx = other.fAllowMixingTx;
        fUnitTest = other.fUnitTest;
    }

    public Masternode(MasternodeBroadcast mnb)
    {
        //LOCK(cs);
        super(mnb.params);
        info = new MasternodeInfo(mnb.getParams(), mnb.info.activeState, mnb.info.nProtocolVersion,
                mnb.info.sigTime, mnb.info.outpoint, mnb.info.address, mnb.info.pubKeyCollateralAddress,
                mnb.info.pubKeyMasternode);
        lastPing = mnb.lastPing;
        vchSig = mnb.vchSig.duplicate();
        fAllowMixingTx = true;
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();
        nCollateralMinConfBlockHash = Sha256Hash.ZERO_HASH;
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

        //MasternodeAddress address;
        cursor += MasternodeAddress.MESSAGE_SIZE;
        //PublicKey pubkey;
        cursor += PublicKey.calcLength(buf, cursor);

        //PublicKey pubKeyMasternode;
        cursor += PublicKey.calcLength(buf, cursor);

        // byte [] sig;
        cursor += MasternodeSignature.calcLength(buf, cursor);


        //long sigTime; //mnb message time
        cursor += 8;
        //protocol Version
        cursor += 4;
        //public int activeState;
        cursor += 4;
        //        MasternodePing lastPing;
        cursor += MasternodePing.calcLength(buf, offset);
        //int cacheInputAge;
        cursor += 4;
        //int cacheInputAgeBlock;
        cursor += 4;
        //boolean unitTest;
        cursor += 1;
        //boolean allowFreeTx;
        cursor += 1;
        //int protocolVersion;
        cursor += 4;

        //the dsq count from the last dsq broadcast of this node
        //long nLastDsq;
        cursor += 8;
        //int nScanningErrorCount;
        cursor += 4;
        //int nLastScanningErrorBlockHeight;
        cursor += 4;

        return cursor - offset;
    }

    public int calculateMessageSizeInBytes()
    {
        int cursor = 0;

        //vin
        cursor += 36;

        //MasternodeAddress address;
        cursor += MasternodeAddress.MESSAGE_SIZE;
        //PublicKey pubkey;
        cursor += info.pubKeyCollateralAddress.calculateMessageSizeInBytes();

        //PublicKey pubKeyMasternode;
        cursor += info.pubKeyMasternode.calculateMessageSizeInBytes();

        // byte [] sig;
        cursor += vchSig.calculateMessageSizeInBytes(); //calcLength(buf, cursor);


        //long sigTime; //mnb message time
        cursor += 8;
        //protocol Version
        cursor += 4;
        //public int activeState;
        cursor += 4;
        //        MasternodePing lastPing;
        cursor += lastPing.calculateMessageSizeInBytes();
        //int cacheInputAge;
        cursor += 4;
        //int cacheInputAgeBlock;
        cursor += 4;
        //boolean unitTest;
        cursor += 1;
        //boolean allowFreeTx;
        cursor += 1;
        //int protocolVersion;
        cursor += 4;

        //the dsq count from the last dsq broadcast of this node
        //long nLastDsq;
        cursor += 8;
        //int nScanningErrorCount;
        cursor += 4;
        //int nLastScanningErrorBlockHeight;
        cursor += 4;

        return cursor;
    }

    @Override
    protected void parse() throws ProtocolException {

        info = new MasternodeInfo();
        info.outpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += info.outpoint.getMessageSize();

        info.address = new MasternodeAddress(params, payload, cursor, PROTOCOL_VERSION);
        cursor += info.address.getMessageSize();

        info.pubKeyCollateralAddress = new PublicKey(params, payload, cursor);
        cursor += info.pubKeyCollateralAddress.getMessageSize();

        info.pubKeyMasternode = new PublicKey(params, payload, cursor);
        cursor += info.pubKeyMasternode.getMessageSize();

        vchSig = new MasternodeSignature(params, payload, cursor);
        cursor += vchSig.getMessageSize();

        info.sigTime = readInt64();
        info.nLastDsq = readInt64();

        info.nTimeLastChecked = readInt64();
        info.nTimeLastPaid = readInt64();

        info.activeState = MasternodeInfo.State.forValue((int)readUint32());

        nCollateralMinConfBlockHash = readHash();
        nBlockLastPaid = (int)readUint32();
        info.nProtocolVersion = (int)readUint32();

        nPoSeBanScore = (int)readUint32();
        nPoSeBanHeight = (int)readUint32();

        fAllowMixingTx = readBytes(1)[0] == 1;
        fUnitTest = readBytes(1)[0] == 1;

        long entries = readVarInt();
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>((int)entries);
        for(long i = 0; i < entries; ++i)
        {
            mapGovernanceObjectsVotedOn.put(readHash(), (int)readUint32());
        }

        length = cursor - offset;

        lastPing = MasternodePing.EMPTY;
    }


    public void masterNodeSerialize(OutputStream stream) throws IOException {
        info.outpoint.bitcoinSerialize(stream);
        info.address.bitcoinSerialize(stream);
        info.pubKeyCollateralAddress.bitcoinSerialize(stream);
        info.pubKeyMasternode.bitcoinSerialize(stream);

        vchSig.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(info.sigTime, stream);
        Utils.int64ToByteStreamLE(info.nLastDsq, stream);
        Utils.int64ToByteStreamLE(info.nTimeLastChecked, stream);
        Utils.int64ToByteStreamLE(info.nTimeLastPaid, stream);
        Utils.uint32ToByteStreamLE(info.activeState.getValue(), stream);
        stream.write(nCollateralMinConfBlockHash.getReversedBytes());
        Utils.uint32ToByteStreamLE(nBlockLastPaid, stream);
        Utils.uint32ToByteStreamLE(info.nProtocolVersion, stream);
        Utils.uint32ToByteStreamLE(nPoSeBanScore, stream);
        Utils.uint32ToByteStreamLE(nPoSeBanHeight, stream);

        byte value [] = new byte[1];

        value[0] = (byte)(fAllowMixingTx ? 1 : 0);
        stream.write(value);

        value[0] = (byte)(fUnitTest ? 1 : 0);
        stream.write(value);

        stream.write(new VarInt(mapGovernanceObjectsVotedOn.size()).encode());
        for(Map.Entry<Sha256Hash, Integer> e: mapGovernanceObjectsVotedOn.entrySet())
        {
            stream.write(e.getKey().getReversedBytes());
            Utils.uint32ToByteStreamLE(e.getValue(), stream);
        }

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        masterNodeSerialize(stream);
    }
    static boolean isValidStateForAutoStart(MasternodeInfo.State nActiveStateIn)
    {
        return  nActiveStateIn == MasternodeInfo.State.MASTERNODE_ENABLED ||
                nActiveStateIn == MasternodeInfo.State.MASTERNODE_PRE_ENABLED ||
                nActiveStateIn == MasternodeInfo.State.MASTERNODE_EXPIRED ||
                nActiveStateIn == MasternodeInfo.State.MASTERNODE_SENTINEL_EXPIRED;
    }

    boolean isValidForPayment()
    {
        if(info.activeState == MasternodeInfo.State.MASTERNODE_ENABLED) {
            return true;
        }
        if(!context.sporkManager.isSporkActive(SporkManager.SPORK_14_REQUIRE_SENTINEL_FLAG) &&
                (info.activeState == MasternodeInfo.State.MASTERNODE_SENTINEL_EXPIRED)) {
            return true;
        }

        return false;
    }

    //
    // Deterministically calculate a given "score" for a Masternode depending on how close it's hash is to
    // the proof of work for that block. The further away they are the better, the furthest will win the election
    // and get paid this block
    //
    Sha256Hash calculateScore(int nBlockHeight)
    {
        //if(blockChain.getChainHead() == null)
        //    return Sha256Hash.ZERO_HASH;

        //uint256 hash = 0;
        Sha256Hash hash = context.hashStore.getBlockHash(nBlockHeight);
        if(hash.equals(Sha256Hash.ZERO_HASH))
        {
            log.info("CalculateScore ERROR - nHeight {} - Returned 0", nBlockHeight);
            return Sha256Hash.ZERO_HASH;
        }

        return calculateScore(hash);
    }

    Sha256Hash calculateScore(Sha256Hash hash)
    {
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
        try {
            info.outpoint.bitcoinSerialize(bos);
            //TODO:  look below if this fails.
            bos.write(nCollateralMinConfBlockHash.getReversedBytes());
            bos.write(hash.getReversedBytes());
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x.getMessage());
        }
    }

    @Deprecated
    static Sha256Hash calculateScore(TransactionInput vin, Sha256Hash hash)
    {
        //uint256 hash = 0;

        BigInteger bi_aux = vin.getHash().toBigInteger().add(BigInteger.valueOf(vin.getOutpoint().getIndex()));
        byte [] temp = new byte[32];
        byte [] bi_bytes = bi_aux.toByteArray();
        int length = bi_bytes[0] == 0 ?
                java.lang.Math.min(bi_bytes.length -1, 32) :
                java.lang.Math.min(bi_bytes.length, 32);
        System.arraycopy(bi_bytes, bi_bytes[0] == 0 ? 1 : 0, temp, 0, length);
        Sha256Hash aux = Sha256Hash.wrap(temp);

        Sha256Hash hash2 = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(hash.getReversedBytes()));

        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(hash.getReversedBytes());
            bos.write(aux.getReversedBytes());
            Sha256Hash hash3 = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));

            BigInteger bhash2 = hash2.toBigInteger();
            BigInteger bhash3 = hash3.toBigInteger();

            //uint256 r = (hash3 > hash2 ? hash3 - hash2 : hash2 - hash3);
            if (bhash3.compareTo(bhash2) > 0)
            {
                byte [] subtraction = bhash3.subtract(bhash2).toByteArray();
                length = subtraction[0] == 0 ?
                        java.lang.Math.min(subtraction.length -1, 32) :
                        java.lang.Math.min(subtraction.length, 32);
                System.arraycopy(subtraction, subtraction[0] == 0 ? 1 : 0, temp, 0, length);
                return Sha256Hash.wrap(temp);
            }
            else
            {
                byte [] subtraction = bhash2.subtract(bhash3).toByteArray();
                length = subtraction[0] == 0 ?
                        java.lang.Math.min(subtraction.length -1, 32) :
                        java.lang.Math.min(subtraction.length, 32);
                System.arraycopy(subtraction, subtraction[0] == 0 ? 1 : 0, temp, 0, length);
                return Sha256Hash.wrap(temp);
            }
        }
        catch (IOException x)
        {
            return Sha256Hash.ZERO_HASH;
        }

    }

    public void check() { check(false); }

    public void check(boolean forceCheck)
    {

        lock.lock();
        try {
            //if(ShutdownRequested()) return;

            if (!forceCheck && (Utils.currentTimeSeconds() - lastTimeChecked < MASTERNODE_CHECK_SECONDS)) return;
            lastTimeChecked = Utils.currentTimeSeconds();

            log.info("masternode--CMasternode::Check -- Masternode {} is in {} state", info.outpoint.toStringShort(), getStateString());

            //once spent, stop doing the checks
            if(isOutpointSpent()) return;

            int nHeight = 0;
            if(!fUnitTest) {
                CollateralStatus err = checkCollateral(info.outpoint).getFirst();
                if (err == COLLATERAL_UTXO_NOT_FOUND) {
                    info.activeState = MASTERNODE_OUTPOINT_SPENT;
                    log.info("masternode--CMasternode::Check -- Failed to find Masternode UTXO, masternode={}", info.outpoint.toStringShort());
                    return;
                }

                nHeight = context.blockChain.getBestChainHeight();
            }

            if(isPoSeBanned()) {
                if(nHeight < nPoSeBanHeight) return; // too early?
                // Otherwise give it a chance to proceed further to do all the usual checks and to change its state.
                // Masternode still will be on the edge and can be banned back easily if it keeps ignoring mnverify
                // or connect attempts. Will require few mnverify messages to strengthen its position in mn list.
                log.info("CMasternode::Check -- Masternode {} is unbanned and back in list now", info.outpoint.toStringShort());
                decreasePoSeBanScore();
            } else if(nPoSeBanScore >= MASTERNODE_POSE_BAN_MAX_SCORE) {
                info.activeState = MASTERNODE_POSE_BAN;
                // ban for the whole payment cycle
                nPoSeBanHeight = nHeight + context.masternodeManager.size();
                log.info("CMasternode::Check -- Masternode {} is banned till block %d now", info.outpoint.toStringShort(), nPoSeBanHeight);
                return;
            }

            MasternodeInfo.State nActiveStatePrev = info.activeState;
            boolean fOurMasternode = DarkCoinSystem.fMasterNode && context.activeMasternode.pubKeyMasternode.equals(info.pubKeyMasternode);

            // masternode doesn't meet payment protocol requirements ...
            boolean fRequireUpdate = info.nProtocolVersion < context.masternodePayments.getMinMasternodePaymentsProto() ||
                    // or it's our own node and we just updated it to the new protocol but we are still waiting for activation ...
                    (fOurMasternode && info.nProtocolVersion < PROTOCOL_VERSION);

            if(fRequireUpdate) {
                info.activeState = MASTERNODE_UPDATE_REQUIRED;
                if(nActiveStatePrev != info.activeState) {
                    log.info("masternode--CMasternode::Check -- Masternode {} is in %s state now", info.outpoint.toStringShort(), getStateString());
                }
                return;
            }

            // keep old masternodes on start, give them a chance to receive updates...
            boolean fWaitForPing = !context.masternodeSync.isMasternodeListSynced() && !isPingedWithin(MASTERNODE_MIN_MNP_SECONDS);

            if(fWaitForPing && !fOurMasternode) {
                // ...but if it was already expired before the initial check - return right away
                if(isExpired() || isSentinelExpired() || isNewStartRequired()) {
                    log.info("masternode--CMasternode::Check -- Masternode %s is in %s state, waiting for ping", info.outpoint.toStringShort(), getStateString());
                    return;
                }
            }

            // don't expire if we are still in "waiting for ping" mode unless it's our own masternode
            if(!fWaitForPing || fOurMasternode) {

                if(!isPingedWithin(MASTERNODE_NEW_START_REQUIRED_SECONDS)) {
                    info.activeState = MASTERNODE_NEW_START_REQUIRED;
                    if(nActiveStatePrev != info.activeState) {
                        log.info("masternode--CMasternode::Check -- Masternode {} is in {} state now", info.outpoint.toStringShort(), getStateString());
                    }
                    return;
                }

                boolean sentinelPingActive = context.masternodeSync.isSynced() && context.masternodeManager.isSentinelPingActive();
                boolean sentinelPingExpired = (sentinelPingActive && isPingedWithin(MASTERNODE_SENTINEL_MAX_SECONDS));

                log.info("masternode--CMasternode::Check -- outpoint={}, nTimeLastWatchdogVote={}, GetAdjustedTime()={}, fWatchdogExpired={}",
                        info.outpoint.toStringShort(), Utils.currentTimeSeconds(), Utils.currentTimeSeconds(), sentinelPingExpired);

                if(sentinelPingExpired) {
                    info.activeState = MASTERNODE_SENTINEL_EXPIRED;
                    if(nActiveStatePrev != info.activeState) {
                        log.info("masternode--CMasternode::Check -- Masternode {} is in {} state now", info.outpoint.toStringShort(), getStateString());
                    }
                    return;
                }

                if(!isPingedWithin(MASTERNODE_EXPIRATION_SECONDS)) {
                    info.activeState = MASTERNODE_EXPIRED;
                    if(nActiveStatePrev != info.activeState) {
                        log.info("masternode--CMasternode::Check -- Masternode {} is in {} state now", info.outpoint.toStringShort(), getStateString());
                    }
                    return;
                }
            }

            if(lastPing.sigTime - info.sigTime < MASTERNODE_MIN_MNP_SECONDS) {
                info.activeState = MASTERNODE_PRE_ENABLED;
                if(nActiveStatePrev != info.activeState) {
                    log.info("masternode--CMasternode::Check -- Masternode {} is in {} state now", info.outpoint.toStringShort(), getStateString());
                }
                return;
            }

            info.activeState = MASTERNODE_ENABLED; // OK
            if(nActiveStatePrev != info.activeState) {
                log.info("masternode--CMasternode::Check -- Masternode {} is in {} state now", info.outpoint.toStringShort(), getStateString());
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     *   FLAG GOVERNANCE ITEMS AS DIRTY
     *
     *   - When masternode come and go on the network, we must flag the items they voted on to recalc it's cached flags
     *
     */
    void flagGovernanceItemsAsDirty()
    {
        ArrayList<Sha256Hash> vecDirty = new ArrayList<Sha256Hash>();
        {
            Iterator<Map.Entry<Sha256Hash, Integer>> it = mapGovernanceObjectsVotedOn.entrySet().iterator();
            while(it.hasNext()) {
                vecDirty.add(it.next().getKey());
            }
        }
        for(int i = 0; i < vecDirty.size(); ++i) {
            context.masternodeManager.addDirtyGovernanceObjectHash(vecDirty.get(i));
        }
    }

    public boolean isEnabled()
    {
        /*return enabled == 1;*/
        return info.activeState == MasternodeInfo.State.MASTERNODE_ENABLED;
    }

    public boolean isPreEnabled()
    {
        return info.activeState == MasternodeInfo.State.MASTERNODE_PRE_ENABLED;
    }
    public boolean isPoSeBanned() { return info.activeState == MasternodeInfo.State.MASTERNODE_POSE_BAN; }
    // NOTE: this one relies on nPoSeBanScore, not on nActiveState as everything else here
    public boolean isPoSeVerified() { return nPoSeBanScore <= -MASTERNODE_POSE_BAN_MAX_SCORE; }
    public boolean isExpired() { return info.activeState == MasternodeInfo.State.MASTERNODE_EXPIRED; }
    public boolean isOutpointSpent() { return info.activeState == MasternodeInfo.State.MASTERNODE_OUTPOINT_SPENT; }
    public boolean isUpdateRequired() { return info.activeState == MasternodeInfo.State.MASTERNODE_UPDATE_REQUIRED; }
    public boolean isSentinelExpired() { return info.activeState == MasternodeInfo.State.MASTERNODE_SENTINEL_EXPIRED; }
    public boolean isNewStartRequired() { return info.activeState == MasternodeInfo.State.MASTERNODE_NEW_START_REQUIRED; }


    public static String stateToString(MasternodeInfo.State nStateIn)
    {
        switch(nStateIn) {
            case MASTERNODE_PRE_ENABLED:            return "PRE_ENABLED";
            case MASTERNODE_ENABLED:                return "ENABLED";
            case MASTERNODE_EXPIRED:                return "EXPIRED";
            case MASTERNODE_OUTPOINT_SPENT:         return "OUTPOINT_SPENT";
            case MASTERNODE_UPDATE_REQUIRED:        return "UPDATE_REQUIRED";
            case MASTERNODE_SENTINEL_EXPIRED:       return "SENTINEL_EXPIRED";
            case MASTERNODE_NEW_START_REQUIRED:     return "NEW_START_REQUIRED";
            case MASTERNODE_POSE_BAN:               return "POSE_BAN";
            default:                                return "UNKNOWN";
        }
    }

    public String getStateString()
    {
        return stateToString(info.activeState);
    }

    public String getStatus()
    {
        // TODO: return smth a bit more human readable here
        return getStateString();
    }

    boolean isBroadcastedWithin(int seconds)
    {
        return (Utils.currentTimeSeconds() - info.sigTime) < seconds;
    }
    boolean isPingedWithin(int seconds, long now)
    {
        if(lastPing == null || lastPing.equals(MasternodePing.empty()))
            return false;
        if(now == -1)
            now = Utils.currentTimeSeconds();

        return (now - lastPing.sigTime) < seconds;
    }
    boolean isPingedWithin(int seconds)
    {
        return isPingedWithin(seconds, -1);
    }

    //
    // When a new masternode broadcast is sent, update our information
    //
    boolean updateFromNewBroadcast(MasternodeBroadcast mnb)
    {
        if(mnb.info.sigTime <= info.sigTime && !mnb.fRecovery) return false;

        info.pubKeyMasternode = mnb.info.pubKeyMasternode;
        info.sigTime = mnb.info.sigTime;
        vchSig = mnb.vchSig;
        info.nProtocolVersion = mnb.info.nProtocolVersion;
        info.address = mnb.info.address;
        nPoSeBanScore = 0;
        nPoSeBanHeight = 0;
        info.nTimeLastChecked = 0;
        Dos nDos = new Dos();
        if(mnb.lastPing.equals(MasternodePing.EMPTY) || (!mnb.lastPing.equals(MasternodePing.EMPTY) && mnb.lastPing.checkAndUpdate(this, true, nDos))) {
            lastPing = mnb.lastPing;
            context.masternodeManager.mapSeenMasternodePing.put(lastPing.getHash(), lastPing);
        }
        // if it matches our Masternode privkey...
        if(fMasterNode && info.pubKeyMasternode == context.activeMasternode.pubKeyMasternode) {
            nPoSeBanScore = -MASTERNODE_POSE_BAN_MAX_SCORE;
            if(info.nProtocolVersion == PROTOCOL_VERSION) {
                // ... and PROTOCOL_VERSION, then we've been remotely activated ...
                context.activeMasternode.manageState();
            } else {
                // ... otherwise we need to reactivate our node, do not add it to the list and do not relay
                // but also do not ban the node we get this message from
                log.info("CMasternode::UpdateFromNewBroadcast -- wrong PROTOCOL_VERSION, re-activate your MN: message nProtocolVersion={}  PROTOCOL_VERSION={}", info.nProtocolVersion, PROTOCOL_VERSION);
                return false;
            }
        }
        return true;
    }

    void updateLastPaid(StoredBlock pindex, int nMaxBlocksToScanBack)
    {
        /*const CBlockIndex *BlockReading = pindex;

        CScript mnpayee = GetScriptForDestination(pubKeyCollateralAddress.GetID());
        // LogPrint("masternode--CMasternode::UpdateLastPaidBlock -- searching for block with payment to %s\n", vin.prevout.ToStringShort());

        LOCK(cs_mapMasternodeBlocks);

        for (int i = 0; BlockReading && BlockReading->nHeight > nBlockLastPaid && i < nMaxBlocksToScanBack; i++) {
            if(mnpayments.mapMasternodeBlocks.count(BlockReading->nHeight) &&
                    mnpayments.mapMasternodeBlocks[BlockReading->nHeight].HasPayeeWithVotes(mnpayee, 2))
            {
                CBlock block;
                if(!ReadBlockFromDisk(block, BlockReading, Params().GetConsensus())) // shouldn't really happen
                    continue;

                CAmount nMasternodePayment = GetMasternodePayment(BlockReading->nHeight, block.vtx[0].GetValueOut());

                BOOST_FOREACH(CTxOut txout, block.vtx[0].vout)
                if(mnpayee == txout.scriptPubKey && nMasternodePayment == txout.nValue) {
                    nBlockLastPaid = BlockReading->nHeight;
                    nTimeLastPaid = BlockReading->nTime;
                    LogPrint("masternode--CMasternode::UpdateLastPaidBlock -- searching for block with payment to %s -- found new %d\n", vin.prevout.ToStringShort(), nBlockLastPaid);
                    return;
                }
            }

            if (BlockReading->pprev == NULL) { assert(BlockReading); break; }
            BlockReading = BlockReading->pprev;
        }
        */

        // Last payment for this masternode wasn't found in latest mnpayments blocks
        // or it was found in mnpayments blocks but wasn't found in the blockchain.
        // LogPrint("masternode--CMasternode::UpdateLastPaidBlock -- searching for block with payment to %s -- keeping old %d\n", vin.prevout.ToStringShort(), nBlockLastPaid);
    }

    public MasternodeInfo getInfo()
    {
        MasternodeInfo info = new MasternodeInfo(this.info);
        info.nTimeLastPing = lastPing.sigTime;
        info.fInfoValid = true;
        return info;
    }

    public boolean isValidNetAddr()
    {
        return isValidNetAddr(info.address);
    }

    public boolean isValidNetAddr(MasternodeAddress address)
    {
        return params.getId().equals(NetworkParameters.ID_REGTEST) ||
                (address.isIPv4() && !address.getSocketAddress().isUnresolved());
    }

    void increasePoSeBanScore() { if(nPoSeBanScore < MASTERNODE_POSE_BAN_MAX_SCORE) nPoSeBanScore++; }
    void decreasePoSeBanScore() { if(nPoSeBanScore > -MASTERNODE_POSE_BAN_MAX_SCORE) nPoSeBanScore--; }
    void poSeBan() { nPoSeBanScore = MASTERNODE_POSE_BAN_MAX_SCORE; }

    boolean isInputAssociatedWithPubkey()
    {
        //TODO:  can this be fixed?
        /*Script payee = ScriptBuilder.createOutputScript(new Address(params, info.pubKeyCollateralAddress.getId()));

        Transaction tx;
        Sha256Hash hash;
        if(GetTransaction(info.outpoint.getHash(), tx, Params().GetConsensus(), hash, true)) {
            for(TransactionOutput out : tx.getOutputs()) {
                if (out.getValue() == Coin.valueOf(1000, 0) && out.getScriptPubKey() == payee) return true;
            }
        }
        */
        return false;
    }

    static public Pair<CollateralStatus, Integer> checkCollateral(TransactionOutPoint outpoint)
    {
        //TODO:  can this be fixed?  - perhaps with a full node that watches masternodes?
        /*AssertLockHeld(cs_main);
        int HeightRet;

        if(!GetUTXOCoin(outpoint, coin)) {
            return COLLATERAL_UTXO_NOT_FOUND;
        }

        if(coin.out.nValue != 1000 * COIN) {
            return COLLATERAL_INVALID_AMOUNT;
        }

        nHeightRet = coin.nHeight;
        return COLLATERAL_OK;*/
        return new Pair<CollateralStatus, Integer>(COLLATERAL_SPV_ASSUME_VALID, -1);
    }

    void addGovernanceVote(Sha256Hash nGovernanceObjectHash)
    {
        if(mapGovernanceObjectsVotedOn.containsKey(nGovernanceObjectHash)) {
            Integer it = mapGovernanceObjectsVotedOn.get(nGovernanceObjectHash);
            mapGovernanceObjectsVotedOn.put(nGovernanceObjectHash, it++);
        } else {
            mapGovernanceObjectsVotedOn.put(nGovernanceObjectHash, 1);
        }
    }

    void removeGovernanceObject(Sha256Hash nGovernanceObjectHash)
    {
        Integer it = mapGovernanceObjectsVotedOn.get(nGovernanceObjectHash);
        if(it == null) {
            return;
        }
        mapGovernanceObjectsVotedOn.remove(nGovernanceObjectHash);
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
