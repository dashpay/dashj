package org.bitcoinj.core;


import org.darkcoinj.DarkSendSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

/**
 * Created by Hash Engineering on 2/20/2016.
 */
public class SporkManager {
    private static final Logger log = LoggerFactory.getLogger(SporkManager.class);

    MasternodeSignature sig;
    String strMasterPrivKey;


    HashMap<Sha256Hash, SporkMessage> mapSporks;
    HashMap<Integer, SporkMessage> mapSporksActive;

    AbstractBlockChain blockChain;
    //NetworkParameters context;
    Context context;

    SporkManager(Context context)
    {
        this.context = context;
        mapSporks = new HashMap<Sha256Hash, SporkMessage>();
        mapSporksActive = new HashMap<Integer, SporkMessage>();
    }

    void setBlockChain(AbstractBlockChain blockChain)
    {
        this.blockChain = blockChain;
    }

    void processSpork(Peer from, SporkMessage spork) {
        if (context.isLiteMode() && !context.allowInstantXinLiteMode()) return; //disable all darksend/masternode related functionality

            //if (chainActive.Tip() == NULL) return;

            Sha256Hash hash = spork.getHash();
            if (mapSporksActive.containsKey(spork.nSporkID)) {
                if (mapSporksActive.get(spork.nSporkID).nTimeSigned >= spork.nTimeSigned) {
                    if (DarkCoinSystem.fDebug) log.info("spork - seen "+hash.toString()+" block " + blockChain.getBestChainHeight());
                    return;
                } else {
                    if (DarkCoinSystem.fDebug)
                        log.info("spork - got updated spork "+hash.toString()+" block " +blockChain.getBestChainHeight());
                }
            }

            log.info("spork - new "+hash.toString()+" ID "+spork.nSporkID+" Time "+spork.nTimeSigned+" bestHeight" + blockChain.getBestChainHeight());

            if (!checkSignature(spork)) {
                log.info("spork - invalid signature");
                //Misbehaving(pfrom -> GetId(), 100);
                return;
            }

            mapSporks.put(hash, spork);
            mapSporksActive.put(spork.nSporkID, spork);
            relay(spork);

            //does a task if needed
            executeSpork(spork.nSporkID, spork.nValue);

    }
    /*
    void processGetSporks(GetSporksMessage m)
    {
        if (strCommand == "getsporks")
        {
            std::map<int, CSporkMessage>::iterator it = mapSporksActive.begin();

            while(it != mapSporksActive.end()) {
                pfrom->PushMessage("spork", it->second);
                it++;
            }
        }

    }*/

    public static final int  SPORK_START              =                             10001;
    public static final int  SPORK_END           =                                  10012;

    public  static final int  SPORK_2_INSTANTX     =                                 10001;
    public  static final int  SPORK_3_INSTANTX_BLOCK_FILTERING =                     10002;
    public  static final int  SPORK_5_MAX_VALUE                            =         10004;
    public  static final int  SPORK_7_MASTERNODE_SCANNING                  =         10006;
    public  static final int  SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT       =         10007;
    public  static final int  SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT        =         10008;
    public  static final int  SPORK_10_MASTERNODE_PAY_UPDATED_NODES        =         10009;
    public  static final int  SPORK_11_RESET_BUDGET                        =         10010;
    public  static final int  SPORK_12_RECONSIDER_BLOCKS                   =         10011;
    public  static final int  SPORK_13_ENABLE_SUPERBLOCKS                  =         10012;

    public  static final int  SPORK_2_INSTANTX_DEFAULT                     =         978307200;  //2001-1-1
    public  static final int  SPORK_3_INSTANTX_BLOCK_FILTERING_DEFAULT     =         1424217600;  //2015-2-18
    public  static final int  SPORK_5_MAX_VALUE_DEFAULT                    =         1000;        //1000 DASH
    public  static final int  SPORK_7_MASTERNODE_SCANNING_DEFAULT          =         978307200;   //2001-1-1
    public  static final long  SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT_DEFAULT=        4070908800L;   //OFF
    public  static final long  SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT_DEFAULT =        4070908800L;   //OFF
    public  static final long  SPORK_10_MASTERNODE_PAY_UPDATED_NODES_DEFAULT =        4070908800L;   //OFF
    public  static final long  SPORK_11_RESET_BUDGET_DEFAULT                 =        0;
    public  static final int  SPORK_12_RECONSIDER_BLOCKS_DEFAULT            =        0;
    public  static final long  SPORK_13_ENABLE_SUPERBLOCKS_DEFAULT           =        4070908800L;   //OFF

    // grab the spork, otherwise say it's off
    public boolean isSporkActive(int nSporkID)
    {
        long r = -1;

        if(mapSporksActive.containsKey(nSporkID)){
            r = mapSporksActive.get(nSporkID).nValue;
        } else {
            if(nSporkID == SPORK_2_INSTANTX) r = SPORK_2_INSTANTX_DEFAULT;
            if(nSporkID == SPORK_3_INSTANTX_BLOCK_FILTERING) r = SPORK_3_INSTANTX_BLOCK_FILTERING_DEFAULT;
            if(nSporkID == SPORK_5_MAX_VALUE) r = SPORK_5_MAX_VALUE_DEFAULT;
            if(nSporkID == SPORK_7_MASTERNODE_SCANNING) r = SPORK_7_MASTERNODE_SCANNING_DEFAULT;
            if(nSporkID == SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT) r = SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT_DEFAULT;
            if(nSporkID == SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT) r = SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT_DEFAULT;
            if(nSporkID == SPORK_10_MASTERNODE_PAY_UPDATED_NODES) r = SPORK_10_MASTERNODE_PAY_UPDATED_NODES_DEFAULT;
            if(nSporkID == SPORK_11_RESET_BUDGET) r = SPORK_11_RESET_BUDGET_DEFAULT;
            if(nSporkID == SPORK_12_RECONSIDER_BLOCKS) r = SPORK_12_RECONSIDER_BLOCKS_DEFAULT;
            if(nSporkID == SPORK_13_ENABLE_SUPERBLOCKS) r = SPORK_13_ENABLE_SUPERBLOCKS_DEFAULT;

            if(r == -1) log.info("GetSpork::Unknown Spork "+ nSporkID);
        }
        if(r == -1) r = 4070908800L; //return 2099-1-1 by default

        return r < Utils.currentTimeSeconds();
    }

    // grab the value of the spork on the network, or the default
    public long getSporkValue(int nSporkID)
    {
        long r = -1;

        if(mapSporksActive.containsKey(nSporkID)){
            r = mapSporksActive.get(nSporkID).nValue;
        } else {
            if(nSporkID == SPORK_2_INSTANTX) r = SPORK_2_INSTANTX_DEFAULT;
            if(nSporkID == SPORK_3_INSTANTX_BLOCK_FILTERING) r = SPORK_3_INSTANTX_BLOCK_FILTERING_DEFAULT;
            if(nSporkID == SPORK_5_MAX_VALUE) r = SPORK_5_MAX_VALUE_DEFAULT;
            if(nSporkID == SPORK_7_MASTERNODE_SCANNING) r = SPORK_7_MASTERNODE_SCANNING_DEFAULT;
            if(nSporkID == SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT) r = SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT_DEFAULT;
            if(nSporkID == SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT) r = SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT_DEFAULT;
            if(nSporkID == SPORK_10_MASTERNODE_PAY_UPDATED_NODES) r = SPORK_10_MASTERNODE_PAY_UPDATED_NODES_DEFAULT;
            if(nSporkID == SPORK_11_RESET_BUDGET) r = SPORK_11_RESET_BUDGET_DEFAULT;
            if(nSporkID == SPORK_12_RECONSIDER_BLOCKS) r = SPORK_12_RECONSIDER_BLOCKS_DEFAULT;
            if(nSporkID == SPORK_13_ENABLE_SUPERBLOCKS) r = SPORK_13_ENABLE_SUPERBLOCKS_DEFAULT;

            if(r == -1) log.info("GetSpork::Unknown Spork "+  nSporkID);
        }

        return r;
    }

    public void executeSpork(int nSporkID, long nValue)
    {
        if(nSporkID == SPORK_11_RESET_BUDGET && nValue == 1){
            //budget.Clear();
        }

        //correct fork via spork technology
        if(nSporkID == SPORK_12_RECONSIDER_BLOCKS && nValue > 0) {
            //log.info("Spork::ExecuteSpork -- Reconsider Last %d Blocks\n", nValue);

            reprocessBlocks((int)nValue);
        }
    }

    void reprocessBlocks(int nBlocks)
    {
        //DashJ will not do this for now.

        /*
        std::map<uint256, int64_t>::iterator it = mapRejectedBlocks.begin();
        while(it != mapRejectedBlocks.end()){
            //use a window twice as large as is usual for the nBlocks we want to reset
            if((*it).second  > GetTime() - (nBlocks*60*5)) {
                BlockMap::iterator mi = mapBlockIndex.find((*it).first);
                if (mi != mapBlockIndex.end() && (*mi).second) {
                    LOCK(cs_main);

                    CBlockIndex* pindex = (*mi).second;
                    LogPrintf("ReprocessBlocks - %s\n", (*it).first.ToString());

                    CValidationState state;
                    ReconsiderBlock(state, pindex);
                }
            }
            ++it;
        }

        CValidationState state;
        {
            LOCK(cs_main);
            DisconnectBlocksAndReprocess(nBlocks);
        }

        if (state.IsValid()) {
            ActivateBestChain(state);
        }
        */
    }


    boolean checkSignature(SporkMessage spork)
    {
        //note: need to investigate why this is failing
        //std::string strMessage = boost::lexical_cast<std::string>(spork.nSporkID) + boost::lexical_cast<std::string>(spork.nValue) + boost::lexical_cast<std::string>(spork.nTimeSigned);
        String strMessage = "" + spork.nSporkID + spork.nValue + spork.nTimeSigned;
        PublicKey pubkey = new PublicKey(Utils.HEX.decode(context.getParams().getSporkKey()));

        StringBuilder errorMessage = new StringBuilder();
        if(!DarkSendSigner.verifyMessage(pubkey, spork.sig, strMessage, errorMessage)){
            return false;
        }

        return true;
    }

    boolean sign(SporkMessage spork)
    {
        //std::string strMessage = boost::lexical_cast<std::string>(spork.nSporkID) + boost::lexical_cast<std::string>(spork.nValue) + boost::lexical_cast<std::string>(spork.nTimeSigned);
        String strMessage = "" + spork.nSporkID + spork.nValue + spork.nTimeSigned;

        ECKey key2;
        PublicKey pubkey2;
        StringBuilder errorMessage = new StringBuilder();

        if((key2 = DarkSendSigner.setKey(strMasterPrivKey, errorMessage)) == null)
        {
            log.info("CMasternodePayments::Sign - ERROR: Invalid masternodeprivkey: "+ errorMessage);
            return false;
        }
        pubkey2 = new PublicKey(key2.getPubKey());

        if((spork.sig = DarkSendSigner.signMessage(strMessage, errorMessage, key2)) == null) {
            log.info("CMasternodePayments::Sign - Sign message failed");
            return false;
        }

        if(!DarkSendSigner.verifyMessage(pubkey2, spork.sig, strMessage, errorMessage)) {
            log.info("CMasternodePayments::Sign - Verify message failed");
            return false;
        }

        return true;
    }

    boolean updateSpork(int nSporkID, long nValue)
    {

        SporkMessage msg = new SporkMessage(context.getParams());
        msg.nSporkID = nSporkID;
        msg.nValue = nValue;
        msg.nTimeSigned = Utils.currentTimeSeconds();
        if(sign(msg)){
            relay(msg);
            mapSporks.put(msg.getHash(), msg);
            mapSporksActive.put(nSporkID, msg);
            return true;
        }

        return false;
    }

    void relay(SporkMessage msg)
    {
        //InventoryItem inv = new InventoryItem(InventoryItem.Type.Spork, msg.getHash());
        //RelayInv(inv);
        //TODO:  Not needed in SPV
    }

    boolean setPrivKey(String strPrivKey)
    {
        SporkMessage msg = new SporkMessage(context.getParams());

        // Test signing successful, proceed
        strMasterPrivKey = strPrivKey;

        sign(msg);

        if(checkSignature(msg)){
            log.info("SporkManager::setPrivKey - Successfully initialized as spork signer\n");
            return true;
        } else {
            return false;
        }
    }

    int getSporkIDByName(String strName)
    {
        if(strName == "SPORK_2_INSTANTX") return SPORK_2_INSTANTX;
        if(strName == "SPORK_3_INSTANTX_BLOCK_FILTERING") return SPORK_3_INSTANTX_BLOCK_FILTERING;
        if(strName == "SPORK_5_MAX_VALUE") return SPORK_5_MAX_VALUE;
        if(strName == "SPORK_7_MASTERNODE_SCANNING") return SPORK_7_MASTERNODE_SCANNING;
        if(strName == "SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT") return SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT;
        if(strName == "SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT") return SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT;
        if(strName == "SPORK_10_MASTERNODE_PAY_UPDATED_NODES") return SPORK_10_MASTERNODE_PAY_UPDATED_NODES;
        if(strName == "SPORK_11_RESET_BUDGET") return SPORK_11_RESET_BUDGET;
        if(strName == "SPORK_12_RECONSIDER_BLOCKS") return SPORK_12_RECONSIDER_BLOCKS;
        if(strName == "SPORK_13_ENABLE_SUPERBLOCKS") return SPORK_13_ENABLE_SUPERBLOCKS;

        return -1;
    }

    String getSporkNameByID(int id)
    {
        if(id == SPORK_2_INSTANTX) return "SPORK_2_INSTANTX";
        if(id == SPORK_3_INSTANTX_BLOCK_FILTERING) return "SPORK_3_INSTANTX_BLOCK_FILTERING";
        if(id == SPORK_5_MAX_VALUE) return "SPORK_5_MAX_VALUE";
        if(id == SPORK_7_MASTERNODE_SCANNING) return "SPORK_7_MASTERNODE_SCANNING";
        if(id == SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT) return "SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT";
        if(id == SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT) return "SPORK_9_MASTERNODE_BUDGET_ENFORCEMENT";
        if(id == SPORK_10_MASTERNODE_PAY_UPDATED_NODES) return "SPORK_10_MASTERNODE_PAY_UPDATED_NODES";
        if(id == SPORK_11_RESET_BUDGET) return "SPORK_11_RESET_BUDGET";
        if(id == SPORK_12_RECONSIDER_BLOCKS) return "SPORK_12_RECONSIDER_BLOCKS";
        if(id == SPORK_13_ENABLE_SUPERBLOCKS) return "SPORK_13_ENABLE_SUPERBLOCKS";

        return "Unknown";
    }
}
