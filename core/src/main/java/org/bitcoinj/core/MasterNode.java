package org.bitcoinj.core;

import com.squareup.okhttp.internal.Network;
import org.bitcoinj.core.*;
import org.darkcoinj.DarkSend;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;

import static org.bitcoinj.core.Utils.int64ToByteStreamLE;

/**
 * Created by Eric on 2/8/2015.
 */
public class Masternode extends Message{
    long lastTimeChecked;

    public int MASTERNODE_ENABLED = 1;
    public int MASTERNODE_EXPIRED = 2;
    public int MASTERNODE_VIN_SPENT = 3;
    public int MASTERNODE_REMOVE = 4;
    public int MASTERNODE_POS_ERROR = 5;

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


    public Masternode(NetworkParameters params)
    {
        super(params);
    }

    public Masternode(NetworkParameters params, byte [] payload)
    {
        super(params, payload, 0);
    }

    public Masternode(Masternode other)
    {
        super(other.params);

        this.vin = other.vin;  //TODO:  need to make copies of all these?
        this.address = new MasternodeAddress(other.address.getAddr(), other.address.getPort(), CoinDefinition.PROTOCOL_VERSION);
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

        //public int activeState;
        cursor += 4;
        //long sigTime; //mnb message time
        cursor += 8;
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

        lastPing = new MasternodePing(params, payload, cursor);
        cursor += lastPing.getMessageSize();

        activeState = (int)readUint32();

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

        /*
        Sha256Hash CalculateScore(int mod, long nBlockHeight)
        {
            //if(chainActive.Tip() == NULL) return 0;

            Sha256Hash hash = Sha256Hash.ZERO_HASH;
            Sha256Hash aux = new Sha256Hash(vin.getOutpoint().getHash().toBigInteger().add(BigInteger.valueOf(vin.getOutpoint().getIndex())).toByteArray());

            //We can't get the hash of the block, reliably because we don't have access to the blockchain
            //if(!GetBlockHash(hash, nBlockHeight)) return 0;

            uint256 hash2 = Hash(BEGIN(hash), END(hash));
            uint256 hash3 = Hash(BEGIN(hash), END(aux));

            uint256 r = (hash3 > hash2 ? hash3 - hash2 : hash2 - hash3);

            return r;
        }
        */
        public void UpdateLastSeen()
        { UpdateLastSeen(0);}
        public void UpdateLastSeen(long override)
        {
            if(override == 0){
            //lastTimeSeen = Utils.currentTimeSeconds();
            } else {
            //lastTimeSeen = override;
            }
        }

        /*long SliceHash(Sha256Hash hash, int slice)
        {
            long n = 0;
            //Utils.readInt64()
            memcpy(&n, &hash+slice*64, 64);
            return n;
        }*/

        public void Check()
        {
            //once spent, stop doing the checks
            /*if(enabled==3) return;


            if(!UpdatedWithin(MasterNodeSystem.MASTERNODE_REMOVAL_SECONDS)){
                enabled = 4;
                return;
            }

            if(!UpdatedWithin(MasterNodeSystem.MASTERNODE_EXPIRATION_SECONDS)){
                enabled = 2;
                return;
            }
            */

            /*if(!unitTest){
                CValidationState state;
                CTransaction tx = CTransaction();
                CTxOut vout = CTxOut(999.99*COIN, darkSendPool.collateralPubKey);
                tx.vin.push_back(vin);
                tx.vout.push_back(vout);

                if(!AcceptableInputs(mempool, state, tx)){
                    enabled = 3;
                    return;
                }
            }*/

            //enabled = 1; // OK
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

        public boolean IsEnabled()
        {
            /*return enabled == 1;*/
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
