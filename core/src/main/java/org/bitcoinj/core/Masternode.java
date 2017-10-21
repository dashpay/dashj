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

import static org.bitcoinj.core.MasterNodeSystem.MASTERNODE_REMOVAL_SECONDS;

/**
 * Created by Eric on 2/8/2015.
 */
public class Masternode extends Message{
    private static final Logger log = LoggerFactory.getLogger(Masternode.class);
    ReentrantLock lock = Threading.lock("Masternode");
    long lastTimeChecked;

    enum State {
        MASTERNODE_PRE_ENABLED(0),
        MASTERNODE_ENABLED(1),
        MASTERNODE_EXPIRED(2),
        MASTERNODE_VIN_SPENT(3),
        MASTERNODE_REMOVE(4),
        MASTERNODE_POS_ERROR(5);

         State(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        private static final Map<Integer, State> typesByValue = new HashMap<Integer, State>();

        static {
            for (State type : State.values()) {
                typesByValue.put(type.value, type);
            }
        }

        private final int value;

        public static State forValue(int value) {
            return typesByValue.get(value);
        }
    }

    public static final int MASTERNODE_CHECK_SECONDS               =   5;
    public static final int MASTERNODE_MIN_MNB_SECONDS             =   5 * 60;
    public static final int MASTERNODE_MIN_MNP_SECONDS             =  10 * 60;
    public static final int MASTERNODE_EXPIRATION_SECONDS          =  65 * 60;
    public static final int MASTERNODE_WATCHDOG_MAX_SECONDS        = 120 * 60;
    public static final int MASTERNODE_NEW_START_REQUIRED_SECONDS  = 180 * 60;

    public static final int MASTERNODE_POSE_BAN_MAX_SCORE          = 5;

    public TransactionInput vin;
    public MasternodeAddress address;
    public PublicKey pubKeyCollateralAddress;
    public PublicKey pubKeyMasternode;
    public MasternodeSignature sig;
    public long sigTime; //mnb message time
    long nLastDsq;
    public long nTimeLastChecked;
    public long nTimeLastPaid;
    public long nTimeLastWatchdogVote;
    public State activeState;
    public int nCacheCollateralBlock;
    public int nBlockLastPaid;
    public int protocolVersion;
    public int nPoSeBanScore;
    public int nPoSeBanHeight;
    boolean fAllowMixingTx;
    boolean unitTest;

    HashMap<Sha256Hash, Integer> mapGovernanceObjectsVotedOn = new HashMap<Sha256Hash, Integer>();

    int cacheInputAge;
    int cacheInputAgeBlock;

    boolean allowFreeTx;


    //the dsq count from the last dsq broadcast of this node

    int nScanningErrorCount;
    int nLastScanningErrorBlockHeight;
    MasternodePing lastPing;

    Context context;

    public Masternode(Context context)
    {
        super(context.getParams());
        this.context = context;

        vin = null;
        address = null;
        pubKeyCollateralAddress = new PublicKey();
        pubKeyMasternode = new PublicKey();
        sig = null;
        activeState = State.MASTERNODE_ENABLED;
        sigTime = Utils.currentTimeSeconds();
        lastPing = MasternodePing.EMPTY;
        cacheInputAge = 0;
        cacheInputAgeBlock = 0;
        unitTest = false;
        allowFreeTx = true;
        protocolVersion = CoinDefinition.PROTOCOL_VERSION;
        nLastDsq = 0;
        nScanningErrorCount = 0;
        nLastScanningErrorBlockHeight = 0;
        lastTimeChecked = 0;
    }

    public Masternode(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
        context = Context.get();
        //calculateScoreTest();
    }

    public Masternode(Context context, byte [] payload, int cursor)
    {
        super(context.getParams(), payload, cursor);
        this.context = context;
        //calculateScoreTest();
    }

    public Masternode(Masternode other)
    {
        super(other.params);
        this.context = other.context;
        //LOCK(cs);
        this.vin = other.vin;  //TODO:  need to make copies of all these?
        this.address = new MasternodeAddress(other.address.getAddr(), other.address.getPort());
        this.pubKeyCollateralAddress = other.pubKeyCollateralAddress.duplicate();
        this.pubKeyMasternode = other.pubKeyMasternode.duplicate();

        //These are good
        this.sig = other.sig.duplicate();
        this.activeState = other.activeState;
        this.sigTime = other.sigTime;
        this.cacheInputAge = other.cacheInputAge;
        this.cacheInputAgeBlock = other.cacheInputAgeBlock;
        this.unitTest = other.unitTest;
        this.allowFreeTx = other.allowFreeTx;
        this.protocolVersion = other.protocolVersion;
        this.nLastDsq = other.nLastDsq;
        this.nScanningErrorCount = other.nScanningErrorCount;
        this.nLastScanningErrorBlockHeight = other.nLastScanningErrorBlockHeight;
    }

    public Masternode(MasternodeBroadcast mnb)
    {
        //LOCK(cs);
        super(mnb.params);
        context = Context.get();
        vin = mnb.vin;
        address = mnb.address;
        pubKeyCollateralAddress = mnb.pubKeyCollateralAddress;
        pubKeyMasternode = mnb.pubKeyMasternode;
        sig = mnb.sig;
        activeState = State.MASTERNODE_ENABLED;
        sigTime = mnb.sigTime;
        lastPing = mnb.lastPing;
        cacheInputAge = 0;
        cacheInputAgeBlock = 0;
        unitTest = false;
        allowFreeTx = true;
        protocolVersion = mnb.protocolVersion;
        nLastDsq = mnb.nLastDsq;
        nScanningErrorCount = 0;
        nLastScanningErrorBlockHeight = 0;
        lastTimeChecked = 0;
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

        long scriptLen = vin.getScriptBytes().length;
        // 4 = length of sequence field (unint32)
        cursor += scriptLen + 4 + VarInt.sizeOf(scriptLen);

        //MasternodeAddress address;
        cursor += MasternodeAddress.MESSAGE_SIZE;
        //PublicKey pubkey;
        cursor += pubKeyCollateralAddress.calculateMessageSizeInBytes();

        //PublicKey pubKeyMasternode;
        cursor += pubKeyMasternode.calculateMessageSizeInBytes();

        // byte [] sig;
        cursor += sig.calculateMessageSizeInBytes(); //calcLength(buf, cursor);


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

        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        address = new MasternodeAddress(params, payload, cursor, CoinDefinition.PROTOCOL_VERSION);
        cursor += address.getMessageSize();

        pubKeyCollateralAddress = new PublicKey(params, payload, cursor);
        cursor += pubKeyCollateralAddress.getMessageSize();

        pubKeyMasternode = new PublicKey(params, payload, cursor);
        cursor += pubKeyMasternode.getMessageSize();

        sig = new MasternodeSignature(params, payload, cursor);
        cursor += sig.getMessageSize();

        sigTime = readInt64();
        nLastDsq = readInt64();

        nTimeLastChecked = readInt64();
        nTimeLastPaid = readInt64();
        nTimeLastWatchdogVote = readInt64();

        activeState = State.forValue((int)readUint32());

        nCacheCollateralBlock = (int) readUint32();
        nBlockLastPaid = (int)readUint32();
        protocolVersion = (int)readUint32();

        nPoSeBanScore = (int)readUint32();
        nPoSeBanHeight = (int)readUint32();

        fAllowMixingTx = readBytes(1)[0] == 1;
        unitTest = readBytes(1)[0] == 1;

        long entries = readVarInt();


        for(long i = 0; i < entries; ++i)
        {
            mapGovernanceObjectsVotedOn.put(readHash(), (int)readUint32());
        }




        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        address.bitcoinSerialize(stream);
        pubKeyCollateralAddress.bitcoinSerialize(stream);
        pubKeyMasternode.bitcoinSerialize(stream);

        sig.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(sigTime, stream);
        Utils.int64ToByteStreamLE(nLastDsq, stream);
        Utils.int64ToByteStreamLE(nTimeLastChecked, stream);
        Utils.int64ToByteStreamLE(nTimeLastPaid, stream);
        Utils.int64ToByteStreamLE(nTimeLastWatchdogVote, stream);
        Utils.uint32ToByteStreamLE(activeState.getValue(), stream);

        Utils.uint32ToByteStreamLE(nCacheCollateralBlock, stream);
        Utils.uint32ToByteStreamLE(nBlockLastPaid, stream);
        Utils.uint32ToByteStreamLE(protocolVersion, stream);
        Utils.uint32ToByteStreamLE(nPoSeBanScore, stream);
        Utils.uint32ToByteStreamLE(nPoSeBanHeight, stream);

        byte value [] = new byte[1];

        value[0] = (byte)(fAllowMixingTx ? 1 : 0);
        stream.write(value);

        value[0] = (byte)(unitTest ? 1 : 0);
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
        return calculateScore(vin, hash);

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

        //uint256 aux = vin.prevout.hash + vin.prevout.n;

        /*if(!GetBlockHash(hash, nBlockHeight)) {
            log.info("CalculateScore ERROR - nHeight {} - Returned 0", nBlockHeight);
            return 0;
        }*/

/*        if(hash.equals(Sha256Hash.ZERO_HASH))
        {
            log.info("CalculateScore ERROR - Returned 0");
            return Sha256Hash.ZERO_HASH;
        }*/



        //CHashWriter ss(SER_GETHASH, PROTOCOL_VERSION);
        //ss << hash;
        //uint256 hash2 = ss.GetHash();

        Sha256Hash hash2 = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(hash.getReversedBytes()));

        /*CHashWriter ss2(SER_GETHASH, PROTOCOL_VERSION);
        ss2 << hash;
        ss2 << aux;
        uint256 hash3 = ss2.GetHash();*/
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


       /*public void UpdateLastSeen()
        { UpdateLastSeen(0);}
        public void UpdateLastSeen(long override)
        {
            if(override == 0){
            //lastTimeSeen = Utils.currentTimeSeconds();
            } else {
            //lastTimeSeen = override;
            }
        }*/

        /*long SliceHash(Sha256Hash hash, int slice)
        {
            long n = 0;
            //Utils.readInt64()
            memcpy(&n, &hash+slice*64, 64);
            return n;
        }*/

        public void check() { check(false); }

    public void check(boolean forceCheck)
    {

        //if(ShutdownRequested()) return;

        if(!forceCheck && (Utils.currentTimeSeconds() - lastTimeChecked < MASTERNODE_CHECK_SECONDS)) return;
        lastTimeChecked = Utils.currentTimeSeconds();


        //once spent, stop doing the checks
        if(activeState == State.MASTERNODE_VIN_SPENT) return;

        // If there are no pings for quite a long time ...
        if(!isPingedWithin(MASTERNODE_REMOVAL_SECONDS)
                // or doesn't meet payments requirements ...
                || protocolVersion < context.masternodePayments.getMinMasternodePaymentsProto()
                // or it's our own node and we just updated it to the new protocol but we are still waiting for activation -
                || (pubKeyMasternode.equals(context.activeMasternode.pubKeyMasternode) && protocolVersion < params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT))){
            activeState = State.MASTERNODE_REMOVE;
            return;
        }

        if(!isPingedWithin(MASTERNODE_EXPIRATION_SECONDS)){
            activeState = State.MASTERNODE_EXPIRED;
            return;
        }


        if(lastPing.sigTime - sigTime < MASTERNODE_MIN_MNP_SECONDS){
            activeState = State.MASTERNODE_PRE_ENABLED;
            return;
        }

        if(!unitTest){
            //TODO:  Not sure how to impliment this
            /*CValidationState state;
            //CMutableTransaction tx = CMutableTransaction();
            Transaction tx = new Transaction(context);
            TransactionOutput vout = new TransactionOutput(tx, Coin.valueOf(999, 99), darkSendPool.collateralPubKey);
            tx.addInput(vin);
            tx.addOutput(vout);
            {
                //TRY_LOCK(cs_main, lockMain);
                //if(!lockMain) return;

                if(!AcceptableInputs(mempool, state, CTransaction(tx), false, NULL)){
                    activeState = MASTERNODE_VIN_SPENT;
                    return;

                }
            }
            */
        }

        activeState = State.MASTERNODE_ENABLED; // OK
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
            return activeState == State.MASTERNODE_ENABLED;
        }
        public boolean isPreEnabled()
        {
            return activeState == State.MASTERNODE_PRE_ENABLED;
        }

    public String status() {
        String strStatus = "unknown";

        if(activeState == State.MASTERNODE_PRE_ENABLED) strStatus = "PRE_ENABLED";
        if(activeState == State.MASTERNODE_ENABLED) strStatus     = "ENABLED";
        if(activeState == State.MASTERNODE_EXPIRED) strStatus     = "EXPIRED";
        if(activeState == State.MASTERNODE_VIN_SPENT) strStatus   = "VIN_SPENT";
        if(activeState == State.MASTERNODE_REMOVE) strStatus      = "REMOVE";
        if(activeState == State.MASTERNODE_POS_ERROR) strStatus   = "POS_ERROR";

        return strStatus;
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
        if(mnb.sigTime > sigTime) {
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

        /*
        int GetMasternodeInputAge()
        {
            if(chainActive.Tip() == NULL) return 0;

            if(cacheInputAge == 0){
                cacheInputAge = GetInputAge(vin);
                cacheInputAgeBlock = chainActive.Tip()->nHeight;
            }

            return cacheInputAge+(chainActive.Tip()->nHeight-cacheInputAgeBlock);
        }
        */

    public MasternodeInfo getInfo()
    {
        MasternodeInfo info = new MasternodeInfo();
        info.vin = vin;
        info.addr = address;
        info.pubKeyCollateralAddress = pubKeyCollateralAddress;
        info.pubKeyMasternode = pubKeyMasternode;
        info.sigTime = sigTime;
        info.nLastDsq = nLastDsq;
        info.nTimeLastChecked = nTimeLastChecked;
        info.nTimeLastPaid = nTimeLastPaid;
        info.nTimeLastWatchdogVote = nTimeLastWatchdogVote;
        info.nActiveState = activeState;
        info.nProtocolVersion = protocolVersion;
        info.fInfoValid = true;
        return info;
    }

    void poSeBan() {

    }
}
