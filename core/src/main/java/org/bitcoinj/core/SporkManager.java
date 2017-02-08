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

    static final int SPORK_START                                            = 10001;
    static final int SPORK_END                                              = 10013;

    public static final int SPORK_2_INSTANTSEND_ENABLED                            = 10001;
    public static final int SPORK_3_INSTANTSEND_BLOCK_FILTERING                    = 10002;
    public static final int SPORK_5_INSTANTSEND_MAX_VALUE                          = 10004;
    public static final int SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT                 = 10007;
    public static final int SPORK_9_SUPERBLOCKS_ENABLED                            = 10008;
    public static final int SPORK_10_MASTERNODE_PAY_UPDATED_NODES                  = 10009;
    public static final int SPORK_12_RECONSIDER_BLOCKS                             = 10011;
    public static final int SPORK_13_OLD_SUPERBLOCK_FLAG                           = 10012;
    public static final int SPORK_14_REQUIRE_SENTINEL_FLAG                         = 10013;

    public static final long SPORK_2_INSTANTSEND_ENABLED_DEFAULT                = 0;            // ON
    public static final long SPORK_3_INSTANTSEND_BLOCK_FILTERING_DEFAULT        = 0;            // ON
    public static final long SPORK_5_INSTANTSEND_MAX_VALUE_DEFAULT              = 1000;         // 1000 DASH
    public static final long SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT_DEFAULT     = 4070908800L;// OFF
    public static final long SPORK_9_SUPERBLOCKS_ENABLED_DEFAULT                = 4070908800L;// OFF
    public static final long SPORK_10_MASTERNODE_PAY_UPDATED_NODES_DEFAULT      = 4070908800L;// OFF
    public static final long SPORK_12_RECONSIDER_BLOCKS_DEFAULT                 = 0;            // 0 BLOCKS
    public static final long SPORK_13_OLD_SUPERBLOCK_FLAG_DEFAULT               = 4070908800L;// OFF
    public static final long SPORK_14_REQUIRE_SENTINEL_FLAG_DEFAULT             = 4070908800L;// OFF


    // grab the spork, otherwise say it's off
    public boolean isSporkActive(int nSporkID)
    {
        long r = -1;

        if(mapSporksActive.containsKey(nSporkID)){
            r = mapSporksActive.get(nSporkID).nValue;
        } else {
            switch (nSporkID) {
                case SPORK_2_INSTANTSEND_ENABLED:               r = SPORK_2_INSTANTSEND_ENABLED_DEFAULT; break;
                case SPORK_3_INSTANTSEND_BLOCK_FILTERING:       r = SPORK_3_INSTANTSEND_BLOCK_FILTERING_DEFAULT; break;
                case SPORK_5_INSTANTSEND_MAX_VALUE:             r = SPORK_5_INSTANTSEND_MAX_VALUE_DEFAULT; break;
                case SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT:    r = SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT_DEFAULT; break;
                case SPORK_9_SUPERBLOCKS_ENABLED:               r = SPORK_9_SUPERBLOCKS_ENABLED_DEFAULT; break;
                case SPORK_10_MASTERNODE_PAY_UPDATED_NODES:     r = SPORK_10_MASTERNODE_PAY_UPDATED_NODES_DEFAULT; break;
                case SPORK_12_RECONSIDER_BLOCKS:                r = SPORK_12_RECONSIDER_BLOCKS_DEFAULT; break;
                case SPORK_13_OLD_SUPERBLOCK_FLAG:              r = SPORK_13_OLD_SUPERBLOCK_FLAG_DEFAULT; break;
                case SPORK_14_REQUIRE_SENTINEL_FLAG:            r = SPORK_14_REQUIRE_SENTINEL_FLAG_DEFAULT; break;
                default:
                    log.info("spork", "CSporkManager::IsSporkActive -- Unknown Spork ID" + nSporkID);
                    r = 4070908800L; // 2099-1-1 i.e. off by default
                    break;
            }
        }

        return r < Utils.currentTimeSeconds();
    }

    // grab the value of the spork on the network, or the default
    public long getSporkValue(int nSporkID)
    {
        long r = -1;

        if(mapSporksActive.containsKey(nSporkID)){
            return mapSporksActive.get(nSporkID).nValue;
        } else {
            switch (nSporkID) {
                case SPORK_2_INSTANTSEND_ENABLED:               return SPORK_2_INSTANTSEND_ENABLED_DEFAULT;
                case SPORK_3_INSTANTSEND_BLOCK_FILTERING:       return SPORK_3_INSTANTSEND_BLOCK_FILTERING_DEFAULT;
                case SPORK_5_INSTANTSEND_MAX_VALUE:             return SPORK_5_INSTANTSEND_MAX_VALUE_DEFAULT;
                case SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT:    return SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT_DEFAULT;
                case SPORK_9_SUPERBLOCKS_ENABLED:               return SPORK_9_SUPERBLOCKS_ENABLED_DEFAULT;
                case SPORK_10_MASTERNODE_PAY_UPDATED_NODES:     return SPORK_10_MASTERNODE_PAY_UPDATED_NODES_DEFAULT;
                case SPORK_12_RECONSIDER_BLOCKS:                return SPORK_12_RECONSIDER_BLOCKS_DEFAULT;
                case SPORK_13_OLD_SUPERBLOCK_FLAG:              return SPORK_13_OLD_SUPERBLOCK_FLAG_DEFAULT;
                case SPORK_14_REQUIRE_SENTINEL_FLAG:            return SPORK_14_REQUIRE_SENTINEL_FLAG_DEFAULT;
                default:
                    log.info("spork", "CSporkManager::GetSporkValue -- Unknown Spork ID "+ nSporkID);
                    return -1;
            }
        }
    }

    public void executeSpork(int nSporkID, long nValue)
    {
        //if(nSporkID == SPORK_11_RESET_BUDGET && nValue == 1){
        //   //budget.Clear();
        //}

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
        std::map<uint256, long>::iterator it = mapRejectedBlocks.begin();
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
        if (strName == "SPORK_2_INSTANTSEND_ENABLED")               return SPORK_2_INSTANTSEND_ENABLED;
        if (strName == "SPORK_3_INSTANTSEND_BLOCK_FILTERING")       return SPORK_3_INSTANTSEND_BLOCK_FILTERING;
        if (strName == "SPORK_5_INSTANTSEND_MAX_VALUE")             return SPORK_5_INSTANTSEND_MAX_VALUE;
        if (strName == "SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT")    return SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT;
        if (strName == "SPORK_9_SUPERBLOCKS_ENABLED")               return SPORK_9_SUPERBLOCKS_ENABLED;
        if (strName == "SPORK_10_MASTERNODE_PAY_UPDATED_NODES")     return SPORK_10_MASTERNODE_PAY_UPDATED_NODES;
        if (strName == "SPORK_12_RECONSIDER_BLOCKS")                return SPORK_12_RECONSIDER_BLOCKS;
        if (strName == "SPORK_13_OLD_SUPERBLOCK_FLAG")              return SPORK_13_OLD_SUPERBLOCK_FLAG;
        if (strName == "SPORK_14_REQUIRE_SENTINEL_FLAG")            return SPORK_14_REQUIRE_SENTINEL_FLAG;

        log.info("spork", "CSporkManager::GetSporkIDByName -- Unknown Spork name: "+ strName);
        return -1;
    }

    String getSporkNameByID(int id)
    {
        switch (id) {
            case SPORK_2_INSTANTSEND_ENABLED:               return "SPORK_2_INSTANTSEND_ENABLED";
            case SPORK_3_INSTANTSEND_BLOCK_FILTERING:       return "SPORK_3_INSTANTSEND_BLOCK_FILTERING";
            case SPORK_5_INSTANTSEND_MAX_VALUE:             return "SPORK_5_INSTANTSEND_MAX_VALUE";
            case SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT:    return "SPORK_8_MASTERNODE_PAYMENT_ENFORCEMENT";
            case SPORK_9_SUPERBLOCKS_ENABLED:               return "SPORK_9_SUPERBLOCKS_ENABLED";
            case SPORK_10_MASTERNODE_PAY_UPDATED_NODES:     return "SPORK_10_MASTERNODE_PAY_UPDATED_NODES";
            case SPORK_12_RECONSIDER_BLOCKS:                return "SPORK_12_RECONSIDER_BLOCKS";
            case SPORK_13_OLD_SUPERBLOCK_FLAG:              return "SPORK_13_OLD_SUPERBLOCK_FLAG";
            case SPORK_14_REQUIRE_SENTINEL_FLAG:            return "SPORK_14_REQUIRE_SENTINEL_FLAG";
            default:
                log.info("spork", "CSporkManager::GetSporkNameByID -- Unknown Spork ID "+ id);
                return "Unknown";
        }
    }
}
