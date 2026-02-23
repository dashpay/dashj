/*
 * Copyright (c) 2022 Dash Core Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bitcoinj.coinjoin;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import org.bitcoinj.coinjoin.utils.CoinJoinResult;
import org.bitcoinj.coinjoin.utils.RelayTransaction;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.InventoryItem;
import org.bitcoinj.core.InventoryMessage;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.BLSLazyPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.evolution.Masternode;
import org.bitcoinj.evolution.SimplifiedMasternodeList;
import org.bitcoinj.evolution.SimplifiedMasternodeListEntry;
import org.bitcoinj.manager.DashSystem;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_ENTRY_MAX_SIZE;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_QUEUE_TIMEOUT;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_SIGNING_TIMEOUT;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_ALREADY_HAVE;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_DENOM;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_ENTRIES_FULL;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_INVALID_COLLATERAL;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_MAXIMUM;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_MN_LIST;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_MODE;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_QUEUE_FULL;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_RECENT;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_SESSION;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_ENTRIES_ADDED;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_NOERR;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_SUCCESS;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_ACCEPTING_ENTRIES;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_ERROR;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_IDLE;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_QUEUE;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_SIGNING;
import static org.bitcoinj.coinjoin.PoolStatusUpdate.STATUS_ACCEPTED;
import static org.bitcoinj.coinjoin.PoolStatusUpdate.STATUS_REJECTED;

/**
 * Simulates a coinjoin server (mixing masternode)
 */
public class CoinJoinServer extends CoinJoinBaseSession {

    static final Logger log = LoggerFactory.getLogger(CoinJoinServer.class);
    static final Random random = new Random();
    private final DashSystem system;
    private final CoinJoinBaseManager baseManager = new CoinJoinBaseManager();
    public CoinJoinServer(Context context, DashSystem system) {
        super(context);
        this.system = system;
        operatorSecretKey = BLSSecretKey.fromSeed(Sha256Hash.ZERO_HASH.getBytes());
        entry = new SimplifiedMasternodeListEntry(
                context.getParams(),
                Sha256Hash.ZERO_HASH,
                Sha256Hash.ZERO_HASH,
                new MasternodeAddress("127.0.0.1", 2003),
                KeyId.fromBytes(new ECKey().getPubKeyHash()),
                new BLSLazyPublicKey(operatorSecretKey.getPublicKey()),
                true
        );

        proTxHash = Sha256Hash.ZERO_HASH;
    }

    protected ArrayList<Transaction> sessionCollaterals = new ArrayList<>();

    private RelayTransaction relayTransaction = null;

    SimplifiedMasternodeListEntry entry;
    Sha256Hash proTxHash;
    BLSSecretKey operatorSecretKey;

    boolean feeCharged = false;
    Sha256Hash feeTxId = null;

    public BLSSecretKey getOperatorSecretKey() {
        return operatorSecretKey;
    }

    public Sha256Hash getProTxHash() {
        return proTxHash;
    }

    public SimplifiedMasternodeListEntry getMasternodeInfo() {
        return entry;
    }

    public void setSession(int sessionID) {
        this.sessionID.set(sessionID);
    }

    public void setDenomination(int denomination) {
        this.sessionDenom = denomination;
    }

    public CoinJoinResult isAcceptableDSA(CoinJoinAccept dsa) {
        if (!CoinJoin.isValidDenomination(dsa.getDenomination())) {
            log.info("coinjoin: denom not valid!");
            return CoinJoinResult.fail(ERR_DENOM);
        }

        // check collateral
        // the server doesn't have full access to the inputs
        if (!CoinJoin.isCollateralValid(dsa.getTxCollateral(), false)) {
            log.info("coinjoin: collateral not valid!");
            return CoinJoinResult.fail(ERR_INVALID_COLLATERAL);
        }

        return CoinJoinResult.success();
    }

    public void processAccept(Peer from, CoinJoinAccept dsa)
    {
        if (isSessionReady()) {
            // too many users in this session already, reject new ones
            log.info("coinjoin server:DSACCEPT -- queue is already full!\n");
            pushStatus(from, STATUS_REJECTED, ERR_QUEUE_FULL);
            return;
        }

        log.info("coinjoin server:DSACCEPT -- denom {} ({})  txCollateral {}", dsa.getDenomination(), CoinJoin.denominationToString(dsa.getDenomination()), dsa.getTxCollateral()); /* Continued */

        SimplifiedMasternodeList mnList = system.masternodeListManager.getListAtChainTip();
        Masternode dmn = mnList.getMN(proTxHash);
        if (dmn == null) {
            pushStatus(from, STATUS_REJECTED, ERR_MN_LIST);
            return;
        }

        if (sessionCollaterals.isEmpty()) {
            lock.lock();
            try {
                if (baseManager.coinJoinQueue.stream().anyMatch(q -> q.getParams().equals(proTxHash))) {
                    // refuse to create another queue this often
                    log.info("coinjoin server:DSACCEPT -- last dsq is still in queue, refuse to mix\n");
                    pushStatus(from, STATUS_REJECTED, ERR_RECENT);
                    return;
                }
            } finally {
                lock.unlock();
            }

            long nLastDsq = system.masternodeMetaDataManager.getMetaInfo(dmn.getProTxHash()).getLastDsq();
            long nDsqThreshold = system.masternodeMetaDataManager.getDsqThreshold(dmn.getProTxHash(), mnList.getValidMNsCount());
            if (nLastDsq != 0 && nDsqThreshold > system.masternodeMetaDataManager.getDsqCount()) {
                log.info("coinjoin server:DSACCEPT -- last dsq too recent, must wait: peer addr={}", from.getAddress().getSocketAddress());
                pushStatus(from, STATUS_REJECTED, ERR_RECENT);
                return;
            }
        }

        PoolMessage messageID = MSG_NOERR;

        CoinJoinResult result = sessionID.get() == 0 ? createNewSession(dsa)
                : addUserToExistingSession(dsa);
        if (result.isSuccess()) {
            log.info("coinjoin server:DSACCEPT -- is compatible, please submit!\n");
            pushStatus(from, STATUS_ACCEPTED, messageID);
        } else {
            log.info("coinjoin server:DSACCEPT -- not compatible with existing transactions!\n");
            pushStatus(from, STATUS_REJECTED, messageID);
        }
    }

    CoinJoinResult addUserToExistingSession(CoinJoinAccept dsa) {
        // this operation is not supported
        return CoinJoinResult.fail();
    }

    void setState(PoolState newState)  {

        if (newState == POOL_STATE_ERROR) {
            log.info("CCoinJoinServer::SetState -- Can't set state to ERROR as a Masternode. \n");
            return;
        }

        log.info("CCoinJoinServer::SetState -- state: {}, newState: {}\n", state, newState);
        timeLastSuccessfulStep.set(Utils.currentTimeSeconds());
        state.set(newState);
    }

    CoinJoinResult createNewSession(CoinJoinAccept dsa) {
        if (sessionID.get() != 0)
            return CoinJoinResult.fail();

        // new session can only be started in idle mode
        if (state.get() != POOL_STATE_IDLE) {
            log.info("CoinJoinServer::CreateNewSession -- incompatible mode: state={}", state);
            return CoinJoinResult.fail(ERR_MODE);
        }

        CoinJoinResult isAcceptable = isAcceptableDSA(dsa);
        if (!isAcceptable.isSuccess())
            return isAcceptable;

        // start new session
        //nMessageIDRet = MSG_NOERR;
        sessionID.set(getRandInt(999999) + 1);
        sessionDenom = dsa.getDenomination();

        setState(POOL_STATE_QUEUE);

        // TODO: enable this code and adjust tests as necessary
        // broadcast that I'm accepting entries, only if it's the first entry through
        // CoinJoinQueue dsq = new CoinJoinQueue(context.getParams(), sessionDenom, masternodeOutpoint, Utils.currentTimeSeconds(), false);
        // log.info("coinjoin server: signing and relaying new queue: {}", dsq);
        // dsq.sign(operatorSecretKey);
        // relay(dsq);
        // coinJoinQueue.add(dsq);

        sessionCollaterals.add((dsa.getTxCollateral()));
        log.info("CoinJoinServer -- new session created, sessionID: {}  sessionDenom: {} ({})  sessionCollaterals.size(): {}  CoinJoin.getMaxPoolParticipants(): {}",
                sessionID, sessionDenom, CoinJoin.denominationToString(sessionDenom), sessionCollaterals.size(), CoinJoin.getMaxPoolParticipants(context.getParams()));

        return CoinJoinResult.success();
    }

    private boolean hasTimedOut() {
        if (state.get() == POOL_STATE_IDLE) return false;

        int nTimeout = (state.get() == POOL_STATE_SIGNING) ? COINJOIN_SIGNING_TIMEOUT : COINJOIN_QUEUE_TIMEOUT;

        return Utils.currentTimeSeconds() - timeLastSuccessfulStep.get() >= nTimeout;
    }

    protected boolean isSessionReady() {
        if (state.get() == POOL_STATE_QUEUE) {
            if (context.getParams().getId().equals(NetworkParameters.ID_UNITTESTNET)) {
                if (sessionCollaterals.size() >= 1) {
                    return true;
                }
            }

            if (sessionCollaterals.size() >= CoinJoin.getMaxPoolParticipants(context.getParams())) {
                return true;
            }
            if (hasTimedOut() && sessionCollaterals.size() >= CoinJoin.getMaxPoolParticipants(context.getParams())) {
                return true;
            }
        }
        return state.get() == POOL_STATE_ACCEPTING_ENTRIES;
    }

    /*
        Check to see if we're ready for submissions from clients
        After receiving multiple dsa messages, the queue will switch to "accepting entries"
        which is the active state right before merging the transaction
    */
    void checkForCompleteQueue() {

        if (state.get() == POOL_STATE_QUEUE && isSessionReady()) {
            setState(POOL_STATE_ACCEPTING_ENTRIES);

            CoinJoinQueue dsq = new CoinJoinQueue(context.getParams(), sessionDenom, proTxHash, Utils.currentTimeSeconds(), true);
            log.info("CoinJoinServer::CheckForCompleteQueue -- queue is ready, signing and relaying ({}) " +
                    "with {} participants", dsq, sessionCollaterals.size());
            dsq.sign(operatorSecretKey);
            relay(dsq);
        }
    }

    public void processEntry(Peer from, CoinJoinEntry entry) {
        //do we have enough users in the current session?
        if (!isSessionReady()) {
            log.info("coinjoin: DSVIN -- session not complete!");
            pushStatus(from, STATUS_REJECTED, ERR_SESSION);
            return;
        }

        log.info("coinjoin: DSVIN -- txCollateral {}", entry.getTxCollateral());

        entry.setPeer(from);
        CoinJoinResult result = addEntry(entry);
        if (result.isSuccess()) {
            pushStatus(from, STATUS_ACCEPTED, result.getMessageId());
            checkPool();
            lock.lock();
            try {
                relayStatus(STATUS_ACCEPTED);
            } finally {
                lock.unlock();
            }
        } else {
            pushStatus(from, STATUS_REJECTED, result.getMessageId());
        }
    }

    void pushStatus(Peer node, PoolStatusUpdate nStatusUpdate, PoolMessage nMessageID) {
        if (node == null)
            return;
        CoinJoinStatusUpdate psssup = new CoinJoinStatusUpdate(
                context.getParams(),
                sessionID.get(),
                state.get(),
                nStatusUpdate,
                nMessageID
        );
        node.sendMessage(psssup);
    }


    void checkPool() {
        int entries = getEntriesCount();
        if (entries != 0) log.info("CoinJoinServer -- entries count {}", entries);

        // If we have an entry for each collateral, then create final tx
        // if we need to support more than one user, then this will break the tests
        if (state.get() == POOL_STATE_ACCEPTING_ENTRIES && getEntriesCount() >= 1) {
            log.info("CCoinJoinServer::CheckPool -- FINALIZE TRANSACTIONS\n");
            createFinalTransaction();
            return;
        }

        // If we have an entry for each collateral, then create final tx
        if (state.get() == POOL_STATE_ACCEPTING_ENTRIES && getEntriesCount() == sessionCollaterals.size()) {
            log.info("CCoinJoinServer::CheckPool -- FINALIZE TRANSACTIONS\n");
            createFinalTransaction();
            return;
        }

        // Check for Time Out
        // If we timed out while accepting entries, then if we have more than minimum, create final tx
        if (state.get() == POOL_STATE_ACCEPTING_ENTRIES && hasTimedOut()
                && getEntriesCount() >= CoinJoin.getMinPoolParticipants(context.getParams())) {
            // Punish misbehaving participants
            //chargeFees();
            // Try to complete this session ignoring the misbehaving ones
            createFinalTransaction();
            return;
        }

        // If we have all the signatures, try to compile the transaction
        if (state.get() == POOL_STATE_SIGNING && isSignaturesComplete()) {
            log.info("CCoinJoinServer::CheckPool -- SIGNING\n");
            commitFinalTransaction();
            return;
        }
    }


    // Check to make sure everything is signed
    boolean isSignaturesComplete() {
        lock.lock();
        try {
            return entries.stream().allMatch(
                    coinJoinEntry -> coinJoinEntry.getMixingInputs().stream().allMatch(
                            coinJoinTransactionInput -> coinJoinTransactionInput.hasSignature()
                    )
            );
        } finally {
            lock.unlock();
        }
    }

    private void commitFinalTransaction() {
        
        Transaction finalTransaction = new Transaction(finalMutableTransaction.getParams(), finalMutableTransaction.bitcoinSerialize());
        Sha256Hash hashTx = finalTransaction.getTxId();

        log.info("CoinJoinServer::CommitFinalTransaction -- finalTransaction={}", finalTransaction.getTxId()); /* Continued */


            // See if the transaction is valid
            boolean anyNotSigned = finalTransaction.getInputs().stream().anyMatch(
                    transactionInput -> transactionInput.getScriptBytes().length == 0
            );
            if (anyNotSigned) {
                log.info("CoinJoinServer: transaction not valid");
                relayCompletedTransaction(PoolMessage.ERR_INVALID_TX);
                return;
            }


        log.info("CoinJoinServer -- CREATING DSTX\n");

        // create and sign masternode dstx transaction
        if (CoinJoin.getDSTX(hashTx) == null) {
            CoinJoinBroadcastTx dstxNew = new CoinJoinBroadcastTx(context.getParams(), finalTransaction, proTxHash, Utils.currentTimeSeconds());
            dstxNew.sign(operatorSecretKey);
            CoinJoin.addDSTX(dstxNew);
        }

        log.info("CoinJoinServer -- TRANSMITTING DSTX\n");

        // Tell the clients it was successful
        relayCompletedTransaction(MSG_SUCCESS);

        InventoryItem item = new InventoryItem(InventoryItem.Type.DarkSendTransaction, hashTx);
        InventoryMessage inv = new InventoryMessage(context.getParams());
        inv.addItem(item);
        relay(inv);

        // Randomly charge clients
        chargeRandomFees();

        // Reset
        log.info("CoinJoinServer::CommitFinalTransaction -- COMPLETED -- RESETTING");
        lock.lock();
        try {
            setNull();
        } finally {
            lock.unlock();
        }
    }

    public void relayCompletedTransaction(PoolMessage nMessageID) {
        
        log.info("CoinJoinServer: nSessionID: {}  nSessionDenom: {} ({})",
                sessionID, sessionDenom, CoinJoin.denominationToString(sessionDenom));

        // final mixing tx with empty signatures should be relayed to mixing participants only
        for (CoinJoinEntry entry : entries) {
            entry.getPeer().sendMessage(new CoinJoinComplete(context.getParams(), sessionID.get(), nMessageID));
        }
    }

    public Transaction createFinalTransaction() {
        Transaction finalTx = new Transaction(context.getParams());

        for (CoinJoinEntry entry : entries) {
            for (TransactionInput input : entry.getMixingInputs()) {
                finalTx.addInput(input);
            }

            for (TransactionOutput output : entry.getMixingOutputs()) {
                finalTx.addOutput(output);
            }
        }

        // fill up the rest of the TX since this class only handles a single user
        for (int i = 0; i < 10 - finalTx.getInputs().size(); i++) {
            ECKey signingKey = new ECKey();
            Address address = Address.fromKey(context.getParams(), signingKey);
            finalTx.addOutput(CoinJoin.denominationToAmount(getSessionDenom()), address);
            byte[] txId = new byte[32];
            random.nextBytes(txId);
            TransactionOutPoint outPoint = new TransactionOutPoint(context.getParams(), random.nextInt(10), Sha256Hash.wrap(txId));
            TransactionInput input = new TransactionInput(context.getParams(), null, new byte[]{}, outPoint);
            finalTx.addSignedInput(input, ScriptBuilder.createOutputScript(address), signingKey, Transaction.SigHash.ALL, false);
        }
        setState(POOL_STATE_SIGNING);
        relayFinalTransaction(finalTx);
        finalMutableTransaction = finalTx;
        return finalTx;
    }

    public CoinJoinResult addEntry(CoinJoinEntry entry) {
        if (getEntriesCount() >= sessionCollaterals.size()) {
            log.info("coinjoin: ERROR: entries is full!");
            return CoinJoinResult.fail(ERR_ENTRIES_FULL);
        }

        if (!CoinJoin.isCollateralValid(entry.getTxCollateral(), false)) {
            log.info("coinjoin: ERROR: collateral not valid!");
            return CoinJoinResult.fail(ERR_INVALID_COLLATERAL);
        }

        if (entry.getMixingInputs().size() > COINJOIN_ENTRY_MAX_SIZE) {
            log.info("coinjoin: ERROR: too many inputs! {}/{}", entry.getMixingInputs().size(), COINJOIN_ENTRY_MAX_SIZE);
            consumeCollateral(entry.getTxCollateral());
            return CoinJoinResult.fail(ERR_MAXIMUM);
        }

        ArrayList<TransactionInput> vin = new ArrayList<>();
        for (TransactionInput txin : entry.getMixingInputs()) {
            log.info("coinjoin: txin={}",txin);
            lock.lock();
            try {
                for (CoinJoinEntry inner_entry :entries) {
                    if (inner_entry.getMixingInputs().stream().anyMatch(coinJoinTransactionInput -> coinJoinTransactionInput.getOutpoint().equals(txin.getOutpoint()))) {
                        log.info("coinjoin: ERROR: already have this txin in entries");
                        // Two peers sent the same input? Can't really say who is the malicious one here,
                        // could be that someone is picking someone else's inputs randomly trying to force
                        // collateral consumption. Do not punish.
                        return CoinJoinResult.fail(ERR_ALREADY_HAVE);
                    }
                }

                vin.add(txin);
            } finally {
                lock.unlock();
            }
        }

        ValidInOuts validResult = isValidInOuts(vin, entry.getMixingOutputs());
        if (!validResult.result) {
            log.info("coinjoin: ERROR! isValidInOuts() failed: {}", CoinJoin.getMessageByID(validResult.messageId));
            if (validResult.consumeCollateral) {
                consumeCollateral(entry.getTxCollateral());
            }
            return CoinJoinResult.fail(validResult.messageId);
        }

        lock.lock();
        try {
            entries.add(entry);
        } finally {
            lock.unlock();
        }

        log.info("coinjoin: adding entry {} of {} required", getEntriesCount(), CoinJoin.getMaxPoolParticipants(context.getParams()));
        return CoinJoinResult.success(MSG_ENTRIES_ADDED);
    }

    public boolean validateFinalTransaction(List<CoinJoinEntry> entries, Transaction finalMutableTransaction) {
        for (CoinJoinEntry entry: entries){
            // Check that the final transaction has all our outputs
            for (TransactionOutput txout : entry.getMixingOutputs()) {
                boolean found = false;
                for (TransactionOutput output : finalMutableTransaction.getOutputs()) {
                    found = txout.getValue().equals(output.getValue())
                            && Arrays.equals(txout.getScriptBytes(), output.getScriptBytes());
                    if (found) {
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean addScriptSig(TransactionInput txinNew)
    {
        
        log.info("CoinJoinServer::AddScriptSig -- scriptSig={}", Utils.HEX.encode(txinNew.getScriptBytes()));

        lock.lock();
        try {
            for (CoinJoinEntry entry : entries){
                if (entry.getMixingInputs().stream().anyMatch(new Predicate<CoinJoinTransactionInput>() {
                    @Override
                    public boolean test(CoinJoinTransactionInput coinJoinTransactionInput) {
                        return coinJoinTransactionInput.getScriptSig().equals(txinNew.getScriptSig());
                    }
                })) {
                    log.info("CoinJoinServer::AddScriptSig -- already exists\n");
                    return false;
                }
            }

            if (!isInputScriptSigValid(txinNew)) {
                log.info("CoinJoinServer::AddScriptSig -- Invalid scriptSig\n");
                return false;
            }

            log.info("CoinJoinServer::AddScriptSig -- scriptSig={} new", txinNew.getScriptSig());

            for (TransactionInput txin :finalMutableTransaction.getInputs()){
                if (txin.getOutpoint().equals(txinNew.getOutpoint()) && txin.getSequenceNumber() == txinNew.getSequenceNumber()) {
                    txin.setScriptSig(txinNew.getScriptSig());
                    log.info("CoinJoinServer::AddScriptSig -- adding to finalMutableTransaction, scriptSig={}", Utils.HEX.encode(txinNew.getScriptBytes()));
                }
            }
            for (CoinJoinEntry entry : entries){
                if (entry.addScriptSig(txinNew)) {
                    log.info("CoinJoinServer::AddScriptSig -- adding to entries, scriptSig={}", txinNew.getScriptSig());
                    return true;
                }
            }

            log.info("CoinJoinServer::AddScriptSig -- Couldn't set sig!\n");
            return false;
        } finally {
            lock.unlock();
        }
    }

    // Check to make sure a given input matches an input in the pool and its scriptSig is valid
    private boolean isInputScriptSigValid(TransactionInput txin) {
        Transaction txNew = new Transaction(context.getParams());
        int nTxInIndex = -1;
        Script sigPubKey;

        {
            int i = 0;
            for (CoinJoinEntry entry: entries) {
            for (TransactionOutput txout: entry.getMixingOutputs()) {
                txNew.addOutput(txout);
            }
            for (CoinJoinTransactionInput txdsin: entry.getMixingInputs()) {
                txNew.addInput(txdsin);

                if (txdsin.getOutpoint().equals(txin.getOutpoint())) {
                    nTxInIndex = i;
                    sigPubKey = txdsin.getPrevPubKey();
                }
                i++;
            }
        }
        }
        if (nTxInIndex >= 0) { //might have to do this one input at a time?
            txNew.getInput(nTxInIndex).setScriptSig(txin.getScriptSig());
            log.info("CoinJoinServer -- verifying scriptSig {}", txin.getScriptSig());
           
        } else {
            log.info("CoinJoinServer -- Failed to find matching input in pool, {}", txin);
            return false;
        }

        log.info("CoinJoinServer -- Successfully validated input and scriptSig");
        return true;
    }

    public void processSignedInputs(Peer from, CoinJoinSignedInputs signedInputs) {
        List<TransactionInput> txInputs = signedInputs.getInputs();

        log.info("coinjoin server: DSSIGNFINALTX -- vecTxIn.size() {}", txInputs.size());

        int nTxInIndex = 0;
        int nTxInsCount = txInputs.size();

        for (TransactionInput txin : txInputs) {
            nTxInIndex++;
            if (!addScriptSig(txin)) {
                log.info("coinjoin server: DSSIGNFINALTX -- AddScriptSig() failed at {}/{}, session: {}", nTxInIndex, nTxInsCount, sessionID);
                lock.lock();
                try {
                    relayStatus(STATUS_REJECTED);
                } finally {
                    lock.unlock();
                }
                return;
            }
            log.info("coinjoin server: DSSIGNFINALTX -- AddScriptSig() {}/{} success", nTxInIndex, nTxInsCount);
        }
        // all is good
        checkPool();
    }

    public void setNull() {
        sessionCollaterals.clear();
        super.setNull();
        baseManager.setNull();
    }

    public void checkQueue() {
        baseManager.checkQueue();
    }

    public int getQueueSize() {
        return baseManager.getQueueSize();
    }

    public CoinJoinQueue getQueueItemAndTry() {
        return baseManager.getQueueItemAndTry();
    }

    public void setRelayTransaction(RelayTransaction relayTransaction) {
        this.relayTransaction = relayTransaction;
    }

    protected void consumeCollateral(Transaction tx) {
        if (relayTransaction != null) {
            relayTransaction.relay(tx);
            log.info("coinjoin: Collateral was consumed {}", tx.getTxId());
            feeCharged = true;
            feeTxId = tx.getTxId();
        }
    }

    private int getRandInt(int max) {
        return random.nextInt(max);
    }

    /*
        Charge the collateral randomly.
        Mixing is completely free, to pay miners we randomly pay the collateral of users.

        Collateral Fee Charges:

        Being that mixing has "no fees" we need to have some kind of cost associated
        with using it to stop abuse. Otherwise, it could serve as an attack vector and
        allow endless transaction that would bloat Dash and make it unusable. To
        stop these kinds of attacks 1 in 10 successful transactions are charged. This
        adds up to a cost of 0.001DRK per transaction on average.
    */
    void chargeRandomFees() {
        for (Transaction txCollateral : sessionCollaterals) {
            if (getRandInt(100) > 10) return;
            log.info("CoinJoinServer: charging random fees, txCollateral={}", txCollateral);
            consumeCollateral(txCollateral);
        }
    }

    protected void relayStatus(PoolStatusUpdate statusUpdate) {
        relayStatus(statusUpdate, MSG_NOERR);
    }
    protected void relayStatus(PoolStatusUpdate statusUpdate, PoolMessage messageID) {
        // status updates should be relayed to mixing participants only
        for (CoinJoinEntry entry : entries) {
            // make sure everyone is still connected
            pushStatus(entry.getPeer(), statusUpdate, messageID);
        }
    }

    protected void relayFinalTransaction(Transaction finalTx) {
        log.info("CoinJoinServer -- nSessionID: {}  nSessionDenom: {} ({})",
                sessionID, sessionDenom, CoinJoin.denominationToString(sessionDenom));

        // final mixing tx with empty signatures should be relayed to mixing participants only
        for (CoinJoinEntry entry : entries) {
            try {
                ListenableFuture sentFuture = entry.getPeer().sendMessage(new CoinJoinFinalTransaction(context.getParams(), sessionID.get(), finalTx));
                sentFuture.get();
            } catch (InterruptedException | ExecutionException x) {
                relayStatus(STATUS_REJECTED);

            }
        }
    }

    protected void relay(Message message) {
        for (Peer peer : system.peerGroup.getConnectedPeers()) {
            peer.sendMessage(message);
        }
    }
}
