package org.darkcoinj;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.TransactionInput;

import java.net.InetSocketAddress;

/**
 * Created by Eric on 2/8/2015.
 */
public class MasterNode {
    public InetSocketAddress getAddress() {
        return address;
    }

    public InetSocketAddress address;
    public    TransactionInput vin;
        long lastTimeSeen;
        ECKey pubkey;
        public ECKey pubkey2;
        byte [] sig;
        long now; //dsee message times
        long lastDseep;
        int cacheInputAge;
        int cacheInputAgeBlock;
        int enabled;
        boolean unitTest;
        boolean allowFreeTx;
        public int protocolVersion;

        //the dsq count from the last dsq broadcast of this node
        long nLastDsq;

        //other variables
        DarkSend darkSend;

        MasterNode(DarkSend darkSend, InetSocketAddress newAddr, TransactionInput newVin, ECKey newPubkey, byte [] newSig, long newNow, ECKey newPubkey2, int protocolVersionIn)
        {
            this.darkSend = darkSend;
            address = newAddr;
            vin = newVin;
            pubkey = newPubkey;
            pubkey2 = newPubkey2;
            sig = newSig;
            now = newNow;
            enabled = 1;
            lastTimeSeen = 0;
            unitTest = false;
            cacheInputAge = 0;
            cacheInputAgeBlock = 0;
            nLastDsq = 0;
            lastDseep = 0;
            allowFreeTx = true;
            protocolVersion = protocolVersionIn;
        }
        /*
        Sha256Hash CalculateScore(int mod, long nBlockHeight)
        {
            if(chainActive.Tip() == NULL) return 0;

            Sha256Hash hash = Sha256Hash.ZERO_HASH;
            if(!darkSend.darkSendPool.GetLastValidBlockHash(hash, mod, nBlockHeight)) return 0;
            byte [] hash2 = X11.x11Digest(hash.getBytes());

            // we'll make a 4 dimensional point in space
            // the closest masternode to that point wins
            long a1 = hash2.Get64(0);
            long a2 = hash2.Get64(1);
            long a3 = hash2.Get64(2);
            long a4 = hash2.Get64(3);

            //copy part of our source hash
            int i1, i2, i3, i4;
            i1=0;i2=0;i3=0;i4=0;
            memcpy(&i1, &a1, 1);
            memcpy(&i2, &a2, 1);
            memcpy(&i3, &a3, 1);
            memcpy(&i4, &a4, 1);

            //split up our mn hash
            uint64_t b1 = vin.prevout.hash.Get64(0);
            uint64_t b2 = vin.prevout.hash.Get64(1);
            uint64_t b3 = vin.prevout.hash.Get64(2);
            uint64_t b4 = vin.prevout.hash.Get64(3);

            //move mn hash around
            b1 <<= (i1 % 64);
            b2 <<= (i2 % 64);
            b3 <<= (i3 % 64);
            b4 <<= (i4 % 64);

            // calculate distance between target point and mn point
            uint256 r = 0;
            r +=  (a1 > b1 ? a1 - b1 : b1 - a1);
            r +=  (a2 > b2 ? a2 - b2 : b2 - a2);
            r +=  (a3 > b3 ? a3 - b3 : b3 - a3);
            r +=  (a4 > b4 ? a4 - b4 : b4 - a4);


    //LogPrintf(" -- MasterNode CalculateScore() n2 = %s \n", n2.ToString().c_str());
    //LogPrintf(" -- MasterNode CalculateScore() vin = %s \n", vin.prevout.hash.GetHex().c_str());
    //LogPrintf(" -- MasterNode CalculateScore() n3 = %s \n", n3.ToString().c_str());

            return r;
        }

        void UpdateLastSeen(int64_t override=0)
        {
            if(override == 0){
            lastTimeSeen = GetAdjustedTime();
            } else {
            lastTimeSeen = override;
            }
        }

        inline uint64_t SliceHash(uint256& hash, int slice)
        {
            long n = 0;
            memcpy(&n, &hash+slice*64, 64);
            return n;
        }

        void Check();

        bool UpdatedWithin(int seconds)
        {
            // LogPrintf("UpdatedWithin %d, %d --  %d \n", GetAdjustedTime() , lastTimeSeen, (GetAdjustedTime() - lastTimeSeen) < seconds);
            return (Utils.currentTimeSeconds() - lastTimeSeen < seconds);
            //return (GetAdjustedTime() - lastTimeSeen) < seconds;
        }

        void Disable()
        {
            lastTimeSeen = 0;
        }

        boolean IsEnabled()
        {
            return enabled == 1;
        }

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
