package org.darkcoinj;

import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;

/**
 * Created by Eric on 2/8/2015.
 */
public class DarkSendPool {
    private static final Logger log = LoggerFactory.getLogger(DarkSendPool.class);
    static final int MIN_PEER_PROTO_VERSION = 70054;

    // clients entries
    ArrayList<DarkSendEntry> myEntries;
    // masternode entries
    ArrayList<DarkSendEntry> entries;
    // the finalized transaction ready for signing
    Transaction finalTransaction;

    long lastTimeChanged;
    long lastAutoDenomination;

    int state;
    int entriesCount;
    int lastEntryAccepted;
    int countEntriesAccepted;

    // where collateral should be made out to
    Script collateralPubKey;

    ArrayList<TransactionInput> lockedCoins;

    Sha256Hash masterNodeBlockHash;

    String lastMessage;
    boolean completedTransaction;
    boolean unitTest;
    InetSocketAddress submittedToMasternode;

    int sessionID;
    int sessionDenom; //Users must submit an denom matching this
    int sessionUsers; //N Users have said they'll join
    boolean sessionFoundMasternode; //If we've found a compatible masternode
    long sessionTotalValue; //used for autoDenom
    ArrayList<Transaction> vecSessionCollateral;

    int cachedLastSuccess;
    int cachedNumBlocks; //used for the overview screen
    int minBlockSpacing; //required blocks between mixes
    Transaction txCollateral;

    long lastNewBlock;

    //debugging data
    String strAutoDenomResult;

    //incremented whenever a DSQ comes through
    long nDsqCount;

    DarkCoinSystem system;

    DarkSendPool(DarkCoinSystem system)
    {
        this.system = system;
        /* DarkSend uses collateral addresses to trust parties entering the pool
            to behave themselves. If they don't it takes their money. */

        cachedLastSuccess = 0;
        cachedNumBlocks = 0;
        unitTest = false;
        txCollateral = new Transaction(system.params);
        minBlockSpacing = 1;
        nDsqCount = 0;
        lastNewBlock = 0;

      //  SetNull();
    }
    /*
    void InitCollateralAddress(){
        String strAddress = "";
        if(system.params.getId() == NetworkParameters.ID_MAINNET) {
            strAddress = "Xq19GqFvajRrEdDHYRKGYjTsQfpV5jyipF";
        } else {
            strAddress = "y1EZuxhhNMAUofTBEeLqGE1bJrpC2TWRNp";
        }
        SetCollateralAddress(strAddress);
    }

    void SetMinBlockSpacing(int minBlockSpacingIn){
        minBlockSpacing = minBlockSpacingIn;
    }

    bool SetCollateralAddress(std::string strAddress);
    void Reset();
    void SetNull(bool clearEverything=false);

    void UnlockCoins();

    boolean IsNull()
    {
        return (state == DarkSend.POOL_STATUS_ACCEPTING_ENTRIES && entries.isEmpty() && myEntries.isEmpty());
    }

    int GetState()
    {
        return state;
    }

    int GetEntriesCount()
    {
        if(system.fMasterNode){
            return entries.size();
        } else {
            return entriesCount;
        }
    }

    int GetLastEntryAccepted()
    {
        return lastEntryAccepted;
    }

    int GetCountEntriesAccepted()
    {
        return countEntriesAccepted;
    }

    int GetMyTransactionCount()
    {
        return myEntries.size();
    }

    void UpdateState(int newState)
    {
        if (system.fMasterNode && (newState == DarkSend.POOL_STATUS_ERROR || newState == DarkSend.POOL_STATUS_SUCCESS)){
            log.info("CDarkSendPool::UpdateState() - Can't set state to ERROR or SUCCESS as a masternode. \n");
            return;
        }

        log.info("CDarkSendPool::UpdateState() == %d | %d \n", state, newState);
        if(state != newState){
            lastTimeChanged = Utils.currentTimeMillis();
            if(system.fMasterNode) {
                RelayDarkSendStatus(system.darkSend.darkSendPool.sessionID, system.darkSend.darkSendPool.GetState(),system.darkSend.darkSendPool.GetEntriesCount(), system.darkSend.MASTERNODE_RESET);
            }
        }
        state = newState;
    }

    int GetMaxPoolTransactions()
    {
        //if we're on testnet, just use two transactions per merge
        if(system.params.getId() == NetworkParameters.ID_TESTNET || system.params.getId() == NetworkParameters.ID_REGTEST) return 2;

        //use the production amount
        return system.darkSend.POOL_MAX_TRANSACTIONS;
    }

    //Do we have enough users to take entries?
    boolean IsSessionReady(){
        return sessionUsers >= GetMaxPoolTransactions();
    }

    // Are these outputs compatible with other client in the pool?
    boolean IsCompatibleWithEntries(std::vector<CTxOut> vout);
    // Is this amount compatible with other client in the pool?
    boolean IsCompatibleWithSession(int64_t nAmount, CTransaction txCollateral, std::string& strReason);

    // Passively run Darksend in the background according to the configuration in settings (only for QT)
    boolean DoAutomaticDenominating(bool fDryRun=false, bool ready=false);
    boolean PrepareDarksendDenominate();


    // check for process in Darksend
    void Check();
    // charge fees to bad actors
    void ChargeFees();
    // rarely charge fees to pay miners
    void ChargeRandomFees();
    void CheckTimeout();
    // check to make sure a signature matches an input in the pool
    bool SignatureValid(const CScript& newSig, const CTxIn& newVin);
    // if the collateral is valid given by a client
    bool IsCollateralValid(const CTransaction& txCollateral);
    // add a clients entry to the pool
    bool AddEntry(const std::vector<CTxIn>& newInput, const int64_t& nAmount, const CTransaction& txCollateral, const std::vector<CTxOut>& newOutput, std::string& error);
    // add signature to a vin
    bool AddScriptSig(const CTxIn& newVin);
    // are all inputs signed?
    bool SignaturesComplete();
    // as a client, send a transaction to a masternode to start the denomination process
    void SendDarksendDenominate(std::vector<CTxIn>& vin, std::vector<CTxOut>& vout, int64_t amount);
    // get masternode updates about the progress of darksend
    bool StatusUpdate(int newState, int newEntriesCount, int newAccepted, std::string& error, int newSessionID=0);

    // as a client, check and sign the final transaction
    bool SignFinalTransaction(CTransaction& finalTransactionNew, CNode* node);

    // get block hash by height
    bool GetBlockHash(uint256& hash, int nBlockHeight);
    // get the last valid block hash for a given modulus
    bool GetLastValidBlockHash(uint256& hash, int mod=1, int nBlockHeight=0);
    // process a new block
    void NewBlock();
    void CompletedTransaction(bool error, std::string lastMessageNew);
    void ClearLastMessage();
    // used for liquidity providers
    bool SendRandomPaymentToSelf();
    // split up large inputs or make fee sized inputs
    bool MakeCollateralAmounts();
    bool CreateDenominated(int64_t nTotalValue);
    // get the denominations for a list of outputs (returns a bitshifted integer)
    int GetDenominations(const std::vector<CTxOut>& vout);
    void GetDenominationsToString(int nDenom, std::string& strDenom);
    // get the denominations for a specific amount of darkcoin.
    int GetDenominationsByAmount(int64_t nAmount, int nDenomTarget=0);

    int GetDenominationsByAmounts(std::vector<int64_t>& vecAmount);
    */
}
