package org.bitcoinj.coinjoin;

import com.google.common.collect.Lists;
import org.bitcoinj.coinjoin.utils.CoinJoinResult;
import org.bitcoinj.coinjoin.utils.RelayTransaction;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_ENTRY_MAX_SIZE;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_QUEUE_TIMEOUT;
import static org.bitcoinj.coinjoin.CoinJoinConstants.COINJOIN_SIGNING_TIMEOUT;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_ALREADY_HAVE;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_DENOM;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_ENTRIES_FULL;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_INVALID_COLLATERAL;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_MAXIMUM;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_MODE;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_QUEUE_FULL;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_RECENT;
import static org.bitcoinj.coinjoin.PoolMessage.ERR_SESSION;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_ENTRIES_ADDED;
import static org.bitcoinj.coinjoin.PoolMessage.MSG_NOERR;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_ACCEPTING_ENTRIES;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_ERROR;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_IDLE;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_QUEUE;
import static org.bitcoinj.coinjoin.PoolState.POOL_STATE_SIGNING;
import static org.bitcoinj.coinjoin.PoolStatusUpdate.STATUS_ACCEPTED;
import static org.bitcoinj.coinjoin.PoolStatusUpdate.STATUS_REJECTED;

public class CoinJoinServer extends CoinJoinBaseSession {

    static final Logger log = LoggerFactory.getLogger(CoinJoinServer.class);
    static final Random random = new Random();
    private final CoinJoinBaseManager baseManager = new CoinJoinBaseManager();
    public CoinJoinServer(Context context) {
        super(context);
        operatorSecretKey = BLSSecretKey.fromSeed(Sha256Hash.ZERO_HASH.getBytes());
        entry = new SimplifiedMasternodeListEntry(
                context.getParams(),
                Sha256Hash.ZERO_HASH,
                Sha256Hash.ZERO_HASH,
                new MasternodeAddress("127.0.0.1", 2003),
                new KeyId(new ECKey().getPubKeyHash()),
                new BLSLazyPublicKey(operatorSecretKey.GetPublicKey()),
                true
        );

        masternodeOutpoint = new TransactionOutPoint(context.getParams(), 0, Sha256Hash.ZERO_HASH);
    }

    protected ArrayList<Transaction> sessionCollaterals = Lists.newArrayList();

    private RelayTransaction relayTransaction = null;

    SimplifiedMasternodeListEntry entry;
    TransactionOutPoint masternodeOutpoint;
    BLSSecretKey operatorSecretKey;

    public BLSSecretKey getOperatorSecretKey() {
        return operatorSecretKey;
    }

    public TransactionOutPoint getMasternodeOutpoint() {
        return masternodeOutpoint;
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

        SimplifiedMasternodeList mnList = context.masternodeListManager.getListAtChainTip();
        Masternode dmn = mnList.getValidMNByCollateral(masternodeOutpoint);
        //if (!dmn) {
        //    PushStatus(from, STATUS_REJECTED, ERR_MN_LIST);
        //    return;
        //}

        if (sessionCollaterals.isEmpty()) {
            lock.lock();
            try {
                if (baseManager.coinJoinQueue.stream().anyMatch(new Predicate<CoinJoinQueue>() {
                    @Override
                    public boolean test(CoinJoinQueue q) {
                        return q.getMasternodeOutpoint().equals(masternodeOutpoint);
                    }
                })) {
                    // refuse to create another queue this often
                    log.info("coinjoin server:DSACCEPT -- last dsq is still in queue, refuse to mix\n");
                    pushStatus(from, STATUS_REJECTED, ERR_RECENT);
                    return;
                }
            } finally {
                lock.unlock();
            }

            long nLastDsq = context.masternodeMetaDataManager.getMetaInfo(dmn.getProTxHash()).getLastDsq();
            long nDsqThreshold = context.masternodeMetaDataManager.getDsqThreshold(dmn.getProTxHash(), mnList.getValidMNsCount());
            if (nLastDsq != 0 && nDsqThreshold > context.masternodeMetaDataManager.getDsqCount()) {
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
        return CoinJoinResult.fail();
        /*
        if (!fMasternodeMode || nSessionID == 0 || IsSessionReady()) return false;

        if (!IsAcceptableDSA(dsa, nMessageIDRet)) {
            return false;
        }

        // we only add new users to an existing session when we are in queue mode
        if (nState != POOL_STATE_QUEUE) {
            nMessageIDRet = ERR_MODE;
            LogPrint(BCLog::COINJOIN, "CCoinJoinServer::AddUserToExistingSession -- incompatible mode: nState=%d\n", nState);
            return false;
        }

        if (dsa.nDenom != nSessionDenom) {
            LogPrint(BCLog::COINJOIN, "CCoinJoinServer::AddUserToExistingSession -- incompatible denom %d (%s) != nSessionDenom %d (%s)\n",
                    dsa.nDenom, CCoinJoin::DenominationToString(dsa.nDenom), nSessionDenom, CCoinJoin::DenominationToString(nSessionDenom));
            nMessageIDRet = ERR_DENOM;
            return false;
        }

        // count new user as accepted to an existing session

        nMessageIDRet = MSG_NOERR;
        vecSessionCollaterals.push_back(MakeTransactionRef(dsa.txCollateral));

        LogPrint(BCLog::COINJOIN, "CCoinJoinServer::AddUserToExistingSession -- new user accepted, nSessionID: %d  nSessionDenom: %d (%s)  vecSessionCollaterals.size(): %d  CCoinJoin::GetMaxPoolParticipants(): %d\n",
                nSessionID, nSessionDenom, CCoinJoin::DenominationToString(nSessionDenom), vecSessionCollaterals.size(), CCoinJoin::GetMaxPoolParticipants());

        return true;*/
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

        /*if (!fUnitTest) {
            //broadcast that I'm accepting entries, only if it's the first entry through
            CoinJoinQueue dsq(nSessionDenom, WITH_LOCK(activeMasternodeInfoCs, return activeMasternodeInfo.outpoint), GetAdjustedTime(), false);
            log.info("coinjoin server:CCoinJoinServer::CreateNewSession -- signing and relaying new queue: %s\n", dsq.ToString());
            dsq.Sign();
            dsq.Relay(connman);
            LOCK(cs_vecqueue);
            vecCoinJoinQueue.push_back(dsq);
        }*/

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

            CoinJoinQueue dsq = new CoinJoinQueue(context.getParams(), sessionDenom, masternodeOutpoint, Utils.currentTimeSeconds(), true);
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

        //entry.addr = from->addr;
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
        // empty for now
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
            finalTx.addOutput(CoinJoin.denominationToAmount(getSessionDenom()), Address.fromKey(context.getParams(), new ECKey()));
            byte[] txId = new byte[32];
            random.nextBytes(txId);
            TransactionOutPoint outPoint = new TransactionOutPoint(context.getParams(), random.nextInt(10), Sha256Hash.wrap(txId));
            TransactionInput input = new TransactionInput(context.getParams(), null, new byte[]{}, outPoint);
            finalTx.addInput(input);
        }
        setState(POOL_STATE_SIGNING);
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

        ArrayList<TransactionInput> vin = Lists.newArrayList();
        for (TransactionInput txin : entry.getMixingInputs()) {
            log.info("coinjoin: -- txin={}",txin);
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
        // do nothign for now
    }

    protected void relay(CoinJoinQueue dsq) {
        for (Peer peer : context.peerGroup.getConnectedPeers()) {
            peer.sendMessage(dsq);
        }
    }
}
