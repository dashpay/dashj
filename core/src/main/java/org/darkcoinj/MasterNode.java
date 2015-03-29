package org.darkcoinj;

import org.bitcoinj.core.*;

/**
 * Created by Eric on 2/8/2015.
 */
public class MasterNode {
    public PeerAddress getAddress() {
        return address;
    }

    public PeerAddress address;
    public    TransactionInput vin;
        public long lastTimeSeen;
        public PublicKey pubkey;
        public PublicKey pubkey2;
        public byte [] sig;
        public long now; //dsee message times
        public long lastDseep;
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

        public MasterNode(PeerAddress newAddr, TransactionInput newVin, PublicKey newPubkey, byte [] newSig, long newNow, PublicKey newPubkey2, int protocolVersionIn)
        {
            //this.darkSend = darkSend;
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
            lastTimeSeen = Utils.currentTimeSeconds();
            } else {
            lastTimeSeen = override;
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
            if(enabled==3) return;


            if(!UpdatedWithin(MasterNodeSystem.MASTERNODE_REMOVAL_SECONDS)){
                enabled = 4;
                return;
            }

            if(!UpdatedWithin(MasterNodeSystem.MASTERNODE_EXPIRATION_SECONDS)){
                enabled = 2;
                return;
            }

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

            enabled = 1; // OK
        }

        public boolean UpdatedWithin(int seconds)
        {
            // LogPrintf("UpdatedWithin %d, %d --  %d \n", GetAdjustedTime() , lastTimeSeen, (GetAdjustedTime() - lastTimeSeen) < seconds);
            return (Utils.currentTimeSeconds() - lastTimeSeen) < seconds;
            //return (GetAdjustedTime() - lastTimeSeen) < seconds;
        }

        public void Disable()
        {
            lastTimeSeen = 0;
        }

        public boolean IsEnabled()
        {
            return enabled == 1;
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
