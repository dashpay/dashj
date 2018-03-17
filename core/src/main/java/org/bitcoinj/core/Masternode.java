package org.bitcoinj.core;

import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.core.CoinDefinition.PROTOCOL_VERSION;
import static org.bitcoinj.core.MasterNodeSystem.MASTERNODE_REMOVAL_SECONDS;
import static org.bitcoinj.core.Masternode.CollateralStatus.COLLATERAL_UTXO_NOT_FOUND;
import static org.bitcoinj.core.MasternodeInfo.State.*;

/**
 * Created by Eric on 2/8/2015.
 */
public class Masternode extends Message{
    private static final Logger log = LoggerFactory.getLogger(Masternode.class);
    ReentrantLock lock = Threading.lock("Masternode");
    long lastTimeChecked;


    enum CollateralStatus {
        COLLATERAL_OK,
        COLLATERAL_UTXO_NOT_FOUND,
        COLLATERAL_INVALID_AMOUNT
    }

    public static final int MASTERNODE_CHECK_SECONDS               =   5;
    public static final int MASTERNODE_MIN_MNB_SECONDS             =   5 * 60;
    public static final int MASTERNODE_MIN_MNP_SECONDS             =  10 * 60;
    public static final int MASTERNODE_EXPIRATION_SECONDS          =  65 * 60;
    public static final int MASTERNODE_WATCHDOG_MAX_SECONDS        = 120 * 60;
    public static final int MASTERNODE_NEW_START_REQUIRED_SECONDS  = 180 * 60;

    public static final int MASTERNODE_POSE_BAN_MAX_SCORE          = 5;

    MasternodeInfo info;
    MasternodePing lastPing;
    MasternodeSignature vchSig;

    Sha256Hash nCollateralMinConfBlockHash = Sha256Hash.ZERO_HASH;
    int nBlockLastPaid = 0;
    int nPoSeBanScore = 0;
    int nPoSeBanHeight = 0;
    boolean fAllowMixingTx = false;
    boolean fUnitTest = false;

    // KEEP TRACK OF GOVERNANCE ITEMS EACH MASTERNODE HAS VOTE UPON FOR RECALCULATION
    HashMap<Sha256Hash, Integer> mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();

    Context context;

    public Masternode(Context context)
    {
        super(context.getParams());
        this.context = context;

        info = new MasternodeInfo();
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
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();
    }

    public Masternode(Masternode other)
    {
        super(other.params);
        this.context = other.context;

        info = new MasternodeInfo(other.info);
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>(other.mapGovernanceObjectsVotedOn.size());
        for (Map.Entry<Sha256Hash, Integer> entry : other.mapGovernanceObjectsVotedOn.entrySet())
        {
            mapGovernanceObjectsVotedOn.put(entry.getKey(), entry.getValue());
        }
    }

    public Masternode(MasternodeBroadcast mnb)
    {
        //LOCK(cs);
        super(mnb.params);
        info = new MasternodeInfo(mnb.getParams(), mnb.info.activeState, mnb.info.nProtocolVersion,
                mnb.info.sigTime, mnb.info.vin.getOutpoint(), mnb.info.address, mnb.info.pubKeyCollateralAddress,
                mnb.info.pubKeyMasternode, mnb.info.sigTime);
        lastPing = mnb.lastPing;
        vchSig = new MasternodeSignature(mnb.vchSig.getBytes());
        fAllowMixingTx = true;
        mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();

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

        long scriptLen = info.vin.getScriptBytes().length;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + VarInt.sizeOf(scriptLen);

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
        info.vin = new TransactionInput(params, null, payload, cursor);
        cursor += info.vin.getMessageSize();

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
        info.nTimeLastWatchdogVote = readInt64();

        info.activeState = MasternodeInfo.State.forValue((int)readUint32());

        nCollateralMinConfBlockHash = readHash();
        nBlockLastPaid = (int)readUint32();
        info.nProtocolVersion = (int)readUint32();

        nPoSeBanScore = (int)readUint32();
        nPoSeBanHeight = (int)readUint32();

        fAllowMixingTx = readBytes(1)[0] == 1;
        fUnitTest = readBytes(1)[0] == 1;

        long entries = readVarInt();


        for(long i = 0; i < entries; ++i)
        {
            mapGovernanceObjectsVotedOn.put(readHash(), (int)readUint32());
        }




        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        info.vin.bitcoinSerialize(stream);
        info.address.bitcoinSerialize(stream);
        info.pubKeyCollateralAddress.bitcoinSerialize(stream);
        info.pubKeyMasternode.bitcoinSerialize(stream);

        vchSig.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(info.sigTime, stream);
        Utils.int64ToByteStreamLE(info.nLastDsq, stream);
        Utils.int64ToByteStreamLE(info.nTimeLastChecked, stream);
        Utils.int64ToByteStreamLE(info.nTimeLastPaid, stream);
        Utils.int64ToByteStreamLE(info.nTimeLastWatchdogVote, stream);
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

    //
    // Deterministically calculate a given "score" for a Masternode depending on how close it's hash is to
    // the proof of work for that block. The further away they are the better, the furthest will win the election
    // and get paid this block
    //
    Sha256Hash calculateScore(int mod, int nBlockHeight)
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

        return calculateScore(mod, hash);
    }

    Sha256Hash calculateScore(int mod, Sha256Hash hash)
    {
        return calculateScore(info.vin, hash);

    }

    static Sha256Hash calculateScore(TransactionInput vin, Sha256Hash hash)
    {
        //uint256 hash = 0;
        BigInteger bi_aux = vin.getOutpoint().getHash().toBigInteger().add(BigInteger.valueOf(vin.getOutpoint().getIndex()));
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

            log.info("masternode--CMasternode::Check -- Masternode {} is in {} state\n", vin.prevout.ToStringShort(), GetStateString());

            //once spent, stop doing the checks
            if(isOutpointSpent()) return;

            int nHeight = 0;
            if(!fUnitTest) {
                //TRY_LOCK(cs_main, lockMain);
                //if(!lockMain) return;

                CollateralStatus err = CheckCollateral(vin.prevout);
                if (err == COLLATERAL_UTXO_NOT_FOUND) {
                    info.activeState = MASTERNODE_OUTPOINT_SPENT;
                    log.info("masternode", "CMasternode::Check -- Failed to find Masternode UTXO, masternode=%s\n", vin.prevout.ToStringShort());
                    return;
                }

                nHeight = context.blockChain.getBestChainHeight();
            }

            if(isPoSeBanned()) {
                if(nHeight < nPoSeBanHeight) return; // too early?
                // Otherwise give it a chance to proceed further to do all the usual checks and to change its state.
                // Masternode still will be on the edge and can be banned back easily if it keeps ignoring mnverify
                // or connect attempts. Will require few mnverify messages to strengthen its position in mn list.
                LogPrintf("CMasternode::Check -- Masternode %s is unbanned and back in list now\n", vin.prevout.ToStringShort());
                decreasePoSeBanScore();
            } else if(nPoSeBanScore >= MASTERNODE_POSE_BAN_MAX_SCORE) {
                info.activeState = MASTERNODE_POSE_BAN;
                // ban for the whole payment cycle
                nPoSeBanHeight = nHeight + context.masternodeManager.size();
                LogPrintf("CMasternode::Check -- Masternode %s is banned till block %d now\n", vin.prevout.ToStringShort(), nPoSeBanHeight);
                return;
            }

            MasternodeInfo.State nActiveStatePrev = info.activeState;
            boolean fOurMasternode = DarkCoinSystem.fMasterNode && context.activeMasternode.pubKeyMasternode == pubKeyMasternode;

            // masternode doesn't meet payment protocol requirements ...
            boolean fRequireUpdate = info.nProtocolVersion < context.masternodePayments.GetMinMasternodePaymentsProto() ||
                    // or it's our own node and we just updated it to the new protocol but we are still waiting for activation ...
                    (fOurMasternode && info.nProtocolVersion < PROTOCOL_VERSION);

            if(fRequireUpdate) {
                info.activeState = MASTERNODE_UPDATE_REQUIRED;
                if(nActiveStatePrev != info.activeState) {
                    log.info("masternode", "CMasternode::Check -- Masternode %s is in %s state now\n", vin.prevout.ToStringShort(), GetStateString());
                }
                return;
            }

            // keep old masternodes on start, give them a chance to receive updates...
            boolean fWaitForPing = !context.masternodeSync.isMasternodeListSynced() && !IsPingedWithin(MASTERNODE_MIN_MNP_SECONDS);

            if(fWaitForPing && !fOurMasternode) {
                // ...but if it was already expired before the initial check - return right away
                if(isExpired() || isWatchdogExpired() || isNewStartRequired()) {
                    log.info("masternode", "CMasternode::Check -- Masternode %s is in %s state, waiting for ping\n", vin.prevout.ToStringShort(), GetStateString());
                    return;
                }
            }

            // don't expire if we are still in "waiting for ping" mode unless it's our own masternode
            if(!fWaitForPing || fOurMasternode) {

                if(!isPingedWithin(MASTERNODE_NEW_START_REQUIRED_SECONDS)) {
                    info.activeState = MASTERNODE_NEW_START_REQUIRED;
                    if(nActiveStatePrev != info.activeState) {
                        log.info("masternode", "CMasternode::Check -- Masternode %s is in %s state now\n", vin.prevout.ToStringShort(), GetStateString());
                    }
                    return;
                }

                boolean fWatchdogActive = context.masternodeSync.isSynced() && context.masternodeManager.isWatchdogActive();
                boolean fWatchdogExpired = (fWatchdogActive && ((Utils.currentTimeSeconds() - info.nTimeLastWatchdogVote) > MASTERNODE_WATCHDOG_MAX_SECONDS));

                log.info("masternode", "CMasternode::Check -- outpoint=%s, nTimeLastWatchdogVote=%d, GetAdjustedTime()=%d, fWatchdogExpired=%d\n",
                        info.vin.getOutpoint().toStringShort(), info.nTimeLastWatchdogVote, Utils.currentTimeSeconds(), fWatchdogExpired);

                if(fWatchdogExpired) {
                    info.activeState = MASTERNODE_WATCHDOG_EXPIRED;
                    if(nActiveStatePrev != info.activeState) {
                        log.info("masternode", "CMasternode::Check -- Masternode %s is in %s state now\n", vin.prevout.ToStringShort(), GetStateString());
                    }
                    return;
                }

                if(!isPingedWithin(MASTERNODE_EXPIRATION_SECONDS)) {
                    info.activeState = MASTERNODE_EXPIRED;
                    if(nActiveStatePrev != info.activeState) {
                        log.info("masternode", "CMasternode::Check -- Masternode %s is in %s state now\n", vin.prevout.ToStringShort(), GetStateString());
                    }
                    return;
                }
            }

            if(lastPing.sigTime - info.sigTime < MASTERNODE_MIN_MNP_SECONDS) {
                info.activeState = MASTERNODE_PRE_ENABLED;
                if(nActiveStatePrev != info.activeState) {
                    log.info("masternode", "CMasternode::Check -- Masternode %s is in %s state now\n", vin.prevout.ToStringShort(), GetStateString());
                }
                return;
            }

            info.activeState = MASTERNODE_ENABLED; // OK
            if(nActiveStatePrev != info.activeState) {
                log.info("masternode", "CMasternode::Check -- Masternode %s is in %s state now\n", vin.prevout.ToStringShort(), GetStateString());
            }

        } finally {
            lock.unlock();
        }
    }

        public boolean UpdatedWithin(int seconds)
        {
            // LogPrintf("UpdatedWithin %d, %d --  %d \n", GetAdjustedTime() , lastTimeSeen, (GetAdjustedTime() - lastTimeSeen) < seconds);
            return false;//(Utils.currentTimeSeconds() - lastTimeSeen) < seconds;
            //return (GetAdjustedTime() - lastTimeSeen) < seconds;
        }

        public void Disable()
        {
            /*lastTimeSeen = 0;*/
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
    public boolean isWatchdogExpired() { return info.activeState == MasternodeInfo.State.MASTERNODE_WATCHDOG_EXPIRED; }
    public boolean isNewStartRequired() { return info.activeState == MasternodeInfo.State.MASTERNODE_NEW_START_REQUIRED; }


    String stateToString(MasternodeInfo.State nStateIn)
    {
        switch(nStateIn) {
            case MASTERNODE_PRE_ENABLED:            return "PRE_ENABLED";
            case MASTERNODE_ENABLED:                return "ENABLED";
            case MASTERNODE_EXPIRED:                return "EXPIRED";
            case MASTERNODE_OUTPOINT_SPENT:         return "OUTPOINT_SPENT";
            case MASTERNODE_UPDATE_REQUIRED:        return "UPDATE_REQUIRED";
            case MASTERNODE_WATCHDOG_EXPIRED:       return "WATCHDOG_EXPIRED";
            case MASTERNODE_NEW_START_REQUIRED:     return "NEW_START_REQUIRED";
            case MASTERNODE_POSE_BAN:               return "POSE_BAN";
            default:                                return "UNKNOWN";
        }
    }

    String getStateString()
    {
        return stateToString(info.activeState);
    }

    String getStatus()
    {
        // TODO: return smth a bit more human readable here
        return getStateString();
    }

        boolean isBroadcastedWithin(int seconds)
        {
            return (Utils.currentTimeSeconds() - sigTime) < seconds;
        }
        boolean isPingedWithin(int seconds, long now)
        {
            if(now == -1)
                now = Utils.currentTimeSeconds();

            return (lastPing.equals(MasternodePing.empty()))
                    ? false
                    : (now - lastPing.sigTime) < seconds;
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
        if(mnb.info.sigTime > info.sigTime) {
            pubKeyMasternode = mnb.pubKeyMasternode;
            sigTime = mnb.sigTime;
            sig = mnb.sig;
            protocolVersion = mnb.protocolVersion;
            address = mnb.address.duplicate();
            lastTimeChecked = 0;
            int nDoS = 0;
            if(mnb.lastPing == new MasternodePing(context) || (!mnb.lastPing.equals(new MasternodePing(context)) && mnb.lastPing.checkAndUpdate(false))) {
                lastPing = mnb.lastPing;
                context.masternodeManager.updateMasternodePing(lastPing);
            }
            return true;
        }
        return false;
    }

    public MasternodeInfo getInfo()
    {
        MasternodeInfo info = new MasternodeInfo(this.info);
        info.nTimeLastPing = lastPing.sigTime;
        info.fInfoValid = true;
        return info;
    }

    void poSeBan() {

    }
}
