package org.bitcoinj.core;

import com.squareup.okhttp.internal.Network;
import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.darkcoinj.DarkSend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.core.Utils.int64ToByteStreamLE;

/**
 * Created by Eric on 2/8/2015.
 */
public class Masternode extends Message{
    private static final Logger log = LoggerFactory.getLogger(Masternode.class);
    ReentrantLock lock = Threading.lock("Masternode");
    long lastTimeChecked;

    public static int MASTERNODE_ENABLED = 1;
    public static int MASTERNODE_EXPIRED = 2;
    public static int MASTERNODE_VIN_SPENT = 3;
    public static int MASTERNODE_REMOVE = 4;
    public static int MASTERNODE_POS_ERROR = 5;

    public static int  MASTERNODE_MIN_CONFIRMATIONS    =       15;
    public static int  MASTERNODE_MIN_MNP_SECONDS      =       (10*60);
    public static int  MASTERNODE_MIN_MNB_SECONDS      =       (5*60);
    public static int  MASTERNODE_PING_SECONDS         =       (5*60);
    public static int  MASTERNODE_EXPIRATION_SECONDS   =       (65*60);
    public static int  MASTERNODE_REMOVAL_SECONDS      =       (75*60);
    public static int  MASTERNODE_CHECK_SECONDS        =       5;

    public TransactionInput vin;
    public MasternodeAddress address;
    public PublicKey pubkey;
    public PublicKey pubkey2;
    public MasternodeSignature sig;
    public int activeState;
    public long sigTime; //mnb message time

    int cacheInputAge;
    int cacheInputAgeBlock;
    boolean unitTest;
    boolean allowFreeTx;
    public int protocolVersion;

    //the dsq count from the last dsq broadcast of this node
    long nLastDsq;
    int nScanningErrorCount;
    int nLastScanningErrorBlockHeight;
    MasternodePing lastPing;

    public long lastDseep;// temporary, do not save. Remove after migration to v12
    long lastDSeep;// temporary, do not save. Remove after migration to v12

        //other variables

        DarkSend darkSend;


    Context context;

    public Masternode(Context context)
    {
        super(context.getParams());
        this.context = context;

        vin = null;
        address = null;
        pubkey = new PublicKey();
        pubkey2 = new PublicKey();
        sig = null;
        activeState = MASTERNODE_ENABLED;
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
        this.pubkey = other.pubkey.duplicate();
        this.pubkey2 = other.pubkey2.duplicate();

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
        vin = mnb.vin;
        address = mnb.address;
        pubkey = mnb.pubkey;
        pubkey2 = mnb.pubkey2;
        sig = mnb.sig;
        activeState = MASTERNODE_ENABLED;
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

        //MasternodeAddress address;
        cursor += MasternodeAddress.MESSAGE_SIZE;
        //PublicKey pubkey;
        cursor += PublicKey.calcLength(buf, cursor);

        //PublicKey pubkey2;
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
        cursor += pubkey.calculateMessageSizeInBytes();

        //PublicKey pubkey2;
        cursor += pubkey2.calculateMessageSizeInBytes();

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
    void parse() throws ProtocolException {
        if (parsed)
            return;


        vin = new TransactionInput(params, null, payload, cursor);
        cursor += vin.getMessageSize();

        address = new MasternodeAddress(params, payload, cursor, CoinDefinition.PROTOCOL_VERSION);
        cursor += address.getMessageSize();

        pubkey = new PublicKey(params, payload, cursor);
        cursor += pubkey.getMessageSize();

        pubkey2 = new PublicKey(params, payload, cursor);
        cursor += pubkey2.getMessageSize();

        sig = new MasternodeSignature(params, payload, cursor);
        cursor += sig.getMessageSize();

        sigTime = readInt64();

        protocolVersion = (int)readUint32();

        activeState = (int)readUint32();

        lastPing = new MasternodePing(params, payload, cursor);
        cursor += lastPing.getMessageSize();



        cacheInputAge = (int)readUint32();
        cacheInputAgeBlock = (int)readUint32();

        unitTest = readBytes(1)[0] == 1;

        allowFreeTx = readBytes(1)[0] == 1;

        nLastDsq = readInt64();

        nScanningErrorCount = (int)readUint32();
        nLastScanningErrorBlockHeight = (int)readUint32();

        length = cursor - offset;

    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {

        vin.bitcoinSerialize(stream);
        address.bitcoinSerialize(stream);
        pubkey.bitcoinSerialize(stream);
        pubkey2.bitcoinSerialize(stream);

        sig.bitcoinSerialize(stream);

        Utils.int64ToByteStreamLE(sigTime, stream);
        Utils.uint32ToByteStreamLE(protocolVersion, stream);

        Utils.uint32ToByteStreamLE(activeState, stream);

        lastPing.bitcoinSerialize(stream);

        Utils.uint32ToByteStreamLE(cacheInputAge, stream);
        Utils.uint32ToByteStreamLE(cacheInputAgeBlock, stream);

        byte value [] = new byte[1];
        value[0] = (byte)(unitTest ? 1 : 0);
        stream.write(value);

        value[0] = (byte)(allowFreeTx ? 1 : 0);
        stream.write(value);

        Utils.int64ToByteStreamLE(nLastDsq, stream);

        Utils.uint32ToByteStreamLE(nScanningErrorCount, stream);
        Utils.uint32ToByteStreamLE(nLastScanningErrorBlockHeight, stream);
    }

    //
    // Deterministically calculate a given "score" for a Masternode depending on how close it's hash is to
    // the proof of work for that block. The further away they are the better, the furthest will win the election
    // and get paid this block
    //
    Sha256Hash calculateScore(int mod, long nBlockHeight)
    {
        //if(blockChain.getChainHead() == null)
        //    return Sha256Hash.ZERO_HASH;

        //uint256 hash = 0;
        BigInteger bi_aux = new BigInteger(vin.getOutpoint().getHash().getBytes()).add(BigInteger.valueOf(vin.getOutpoint().getIndex()));
        Sha256Hash aux = Sha256Hash.of(bi_aux.toByteArray());

        //uint256 aux = vin.prevout.hash + vin.prevout.n;

        /*if(!GetBlockHash(hash, nBlockHeight)) {
            log.info("CalculateScore ERROR - nHeight {} - Returned 0", nBlockHeight);
            return 0;
        }*/
        Sha256Hash hash = context.masternodeManager.getBlockHash(nBlockHeight);
        if(hash.equals(Sha256Hash.ZERO_HASH))
        {
            log.info("CalculateScore ERROR - nHeight {} - Returned 0", nBlockHeight);
            return Sha256Hash.ZERO_HASH;
        }


        //CHashWriter ss(SER_GETHASH, PROTOCOL_VERSION);
        //ss << hash;
        //uint256 hash2 = ss.GetHash();

        Sha256Hash hash2 = Sha256Hash.twiceOf(hash.getBytes());

        /*CHashWriter ss2(SER_GETHASH, PROTOCOL_VERSION);
        ss2 << hash;
        ss2 << aux;
        uint256 hash3 = ss2.GetHash();*/

        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bos.write(hash.getBytes());
            bos.write(aux.getBytes());
            Sha256Hash hash3 = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));

            BigInteger bhash2 = new BigInteger(hash2.getBytes());
            BigInteger bhash3 = new BigInteger(hash3.getBytes());

            //uint256 r = (hash3 > hash2 ? hash3 - hash2 : hash2 - hash3);
            if(bhash3.compareTo(bhash2) > 0)
                return Sha256Hash.of(bhash3.subtract(bhash2).toByteArray());
            else return Sha256Hash.of(bhash2.subtract(bhash3).toByteArray());
        }
        catch (IOException x)
        {
            return Sha256Hash.ZERO_HASH;
        }


    }

    Sha256Hash calculateScore(int mod, Sha256Hash hash)
    {
        return calculateScore(vin, hash);

    }

    Sha256Hash calculateScoreTest()
    {
        //if(blockChain.getChainHead() == null)
        //    return Sha256Hash.ZERO_HASH;

        /*
CalculateScore:-------
, vin=CTxIn(COutPoint(b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb, 0), scriptSig=)
vin.prevout.hash=b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb
vin.prevout.n=0
hash=00000000000642c0b18cafc97a23ffd6e5eeb0a63b600a0d3f9630a93b674ae0
aux=b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb
2016-03-01 07:37:39 hash2=8802d328293c18864b4c2e5d4de40f21e650ceb3ce55414ce54e2e321d33b75c
2016-03-01 07:37:39 hash3=0411aaa87e4632a79846ad4a9b69d66cc25b9c69966e913ddaf8997ccc2a0b16
2016-03-01 07:37:39 r=83f1287faaf5e5deb3058112b27a38b523f5324a37e6b00f0a5594b55109ac46 (hash2-hash3)
                      83f1287faaf5e5deb3058112b27a38b523f5324a37e6b00f0a5594b55109ac46
*/
        TransactionInput vin = new TransactionInput(params,null, new byte[0],
                new TransactionOutPoint(params, 0,
                        Sha256Hash.wrap(Utils.HEX.decode("b4bc8e63e2d703ba86b74f9df2d13089e07eef45afbd31614eb6ad29d4f9acdb"))));

        Sha256Hash hash;// = context.masternodeManager.getBlockHash(nBlockHeight);

        hash = Sha256Hash.wrap(Utils.HEX.decode("00000000000642c0b18cafc97a23ffd6e5eeb0a63b600a0d3f9630a93b674ae0"));

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
        if(activeState == MASTERNODE_VIN_SPENT) return;


        if(!isPingedWithin(MASTERNODE_REMOVAL_SECONDS)){
            activeState = MASTERNODE_REMOVE;
            return;
        }

        if(!isPingedWithin(MASTERNODE_EXPIRATION_SECONDS)){
            activeState = MASTERNODE_EXPIRED;
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

        activeState = MASTERNODE_ENABLED; // OK
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
            return activeState == MASTERNODE_ENABLED;
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
            pubkey2 = mnb.pubkey2;
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
}
