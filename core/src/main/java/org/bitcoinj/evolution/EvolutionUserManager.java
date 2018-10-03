package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static org.bitcoinj.evolution.SubTxTransition.EVO_TS_MAX_FEE;
import static org.bitcoinj.evolution.SubTxTransition.EVO_TS_MIN_FEE;

public class EvolutionUserManager extends AbstractManager implements TransactionReceivedInBlockListener {

    private static final Logger log = LoggerFactory.getLogger(EvolutionUserManager.class);
    public ReentrantLock lock = Threading.lock("EvolutionUserManager");

    EvolutionUser currentUser;
    HashMap<Sha256Hash, EvolutionUser> userMap;

    EvolutionUserManager() {
        super(Context.get());
        userMap = new HashMap<Sha256Hash, EvolutionUser>(1);
    }

    public EvolutionUserManager(Context context) {
        super(context);
        userMap = new HashMap<Sha256Hash, EvolutionUser>(1);
    }

    public EvolutionUserManager(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
    }

    @Override
    protected void parse() throws ProtocolException {
        int size = (int)readVarInt();
        userMap = new HashMap<Sha256Hash, EvolutionUser>(size);
        for(int i = 0; i < size; ++i) {
            EvolutionUser user = new EvolutionUser(params, payload, cursor);
            cursor += user.getMessageSize();
            userMap.put(user.getRegTxId(), user);
        }
        Sha256Hash currentUserHash = readHash();
        currentUser = userMap.get(currentUserHash);
        if(currentUser == null)
            currentUser = userMap.values().iterator().next();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(new VarInt(userMap.size()).encode());
        for(Map.Entry<Sha256Hash, EvolutionUser> entry : userMap.entrySet()) {
            entry.getValue().bitcoinSerializeToStream(stream);
        }
        if(currentUser != null)
            stream.write(currentUser.getRegTxId().getReversedBytes());
        else stream.write(Sha256Hash.ZERO_HASH.getReversedBytes());
    }

    @Override
    public int calculateMessageSizeInBytes() {
        return 0;
    }

    @Override
    public void clear() {
        userMap.clear();
    }

    @Override
    public void checkAndRemove() {

    }

    @Override
    public AbstractManager createEmpty() {
        return new EvolutionUserManager();
    }

    public void deleteUser(Sha256Hash regTxId) {
        if(userMap.containsKey(regTxId))
            return;

        EvolutionUser user = getUser(regTxId);
        if (user == null)
            return;

        userMap.remove(user);
    }

    public EvolutionUser getUser(Sha256Hash regTxId) {
        return userMap.get(regTxId);
    }

    public Sha256Hash getUserIdByName(String userName) {
        for(Map.Entry<Sha256Hash, EvolutionUser> user : userMap.entrySet())
        {
            if(user.getValue().getUserName().equals(userName))
                return user.getValue().getCurSubTx();
        }
        return null;
    }

    public boolean userExists(Sha256Hash regTxId) {
        return userMap.containsKey(regTxId);
    }

    public boolean userNameExists(String userName) {
        return getUserIdByName(userName) != null;
    }

    public void writeUser(EvolutionUser user) {
        userMap.put(user.getRegTxId(), user);
    }

    Coin getTxBurnAmount(Transaction tx)
    {
        Coin burned = Coin.ZERO;
        for (TransactionOutput txo : tx.getOutputs()) {
            if(txo.getScriptPubKey().isOpReturn()) {
                    burned = burned.add(txo.getValue());
            }
        }
        return burned;
    }

    boolean checkSubTxRegister(Transaction tx, StoredBlock prevBlock) {
        lock.lock();
        try {
            SubTxRegister subTxRegister = new SubTxRegister(tx.getParams(), tx);

            if(!subTxRegister.check())
                return false;

            if(userNameExists(subTxRegister.getUserName()))
                return false;

            Coin topupAmount = getTxBurnAmount(tx);

            if(topupAmount.compareTo(SubTxTopup.MIN_SUBTX_TOPUP) < 0)
                return false;

            StringBuilder errorMessage = new StringBuilder();
            if(!HashSigner.verifyHash(subTxRegister.getSignHash(), subTxRegister.getPubKeyId().getBytes(), subTxRegister.getSignature(), errorMessage))
            {
                return false;
            }
            return true;

        } catch (ProtocolException x) {

        } finally {
            lock.unlock();
        }
        return false;
    }

    boolean checkSubTxTopup(Transaction tx, StoredBlock prev)
    {
        lock.lock();
        try {
            SubTxTopup topup = (SubTxTopup)tx.getExtraPayloadObject();
            if(!topup.check())
                return false;

            EvolutionUser user = getUser(topup.getRegTxId());

            if (user == null) {
                return false;
            }

            if(user.isClosed())
                return false;

            Coin topupAmount = getTxBurnAmount(tx);
            if (topupAmount.compareTo(SubTxTopup.MIN_SUBTX_TOPUP) < 0) {
                return false;
            }

            if(user.hasTopup(tx))
                return false;

            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean checkSubTxResetKey(Transaction tx, StoredBlock prev)
    {
        lock.lock();
        try {

            SubTxResetKey subTx = (SubTxResetKey)tx.getExtraPayloadObject();
            if(!subTx.check())
                return false;

            EvolutionUser user = getUser(subTx.regTxId);
            if (!checkSubTxAndFeeForUser(tx, subTx, user)){
                return false;
            }

            if(user.hasReset(tx))
                return false;

            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean checkSubTxAndFeeForUser(Transaction tx, SubTxForExistingUser subTxRet, EvolutionUser user)
    {
        if (!checkSubTxForUser(tx, subTxRet, user)) {
            return false;
        }

        // TODO min fee depending on TS size
        if (subTxRet.creditFee.compareTo(EVO_TS_MIN_FEE) < 0 || subTxRet.creditFee.compareTo(EVO_TS_MAX_FEE) > 0) {
            return false;
        }

        if (user.getCreditBalance().compareTo(subTxRet.creditFee) < 0) {
            // Low DoS score as peers may not know about the low balance (e.g. due to not mined topups)
            return false;//state.DoS(10, false, REJECT_INSUFFICIENTFEE, "bad-subtx-nocredits");
        }
        return true;
    }

    boolean checkSubTxForUser(Transaction tx, SubTxForExistingUser subTxRet, EvolutionUser user)
    {
        if (getSubTxAndUser(tx) == null) {
            return false;
        }

        if (!subTxRet.hashPrevSubTx.equals(user.getCurSubTx())) {
            return false;
        }

        StringBuilder strError = new StringBuilder();
        if (!HashSigner.verifyHash(subTxRet.getSignHash(), user.getCurPubKeyID().getBytes(), subTxRet.getSignature(), strError)) {
        // TODO immediately ban?
            return false;
        }

        return true;
    }

    EvolutionUser getSubTxAndUser(Transaction tx) {
        return getSubTxAndUser(tx, false);
    }

    EvolutionUser getSubTxAndUser(Transaction tx, boolean allowClosed)
    {
        SubTxForExistingUser subTx = (SubTxForExistingUser)tx.getExtraPayloadObject();
        if(!subTx.check())
            return null;

        EvolutionUser user = getUser(subTx.getRegTxId());

        if(user == null)
            return null;

        if(!allowClosed && user.isClosed())
            return null;

        return user;
    }

    EvolutionUser buildUser(Transaction regTx)
    {
        if (regTx == null) {
            return null;
        }

        if (!checkSubTxRegister(regTx, null)) {
            return null;
        }

        SubTxRegister subTx = (SubTxRegister)regTx.getExtraPayloadObject();

        EvolutionUser user = new EvolutionUser(regTx, subTx.getUserName(), subTx.getPubKeyId());
        user.addTopUp(getTxBurnAmount(regTx), regTx);

        return user;
    }

    public boolean processSubTxRegister(Transaction tx, StoredBlock prev)
    {
        lock.lock();
        try {

            SubTxRegister subTx = (SubTxRegister)tx.getExtraPayloadObject();

            Coin topupAmount = getTxBurnAmount(tx);

            EvolutionUser user = new EvolutionUser(tx, subTx.userName, subTx.getPubKeyId());
            user.addTopUp(topupAmount, tx);
            //userDb.pushSubTx(tx.getHash(), tx.getHash());
            //userDb.pushPubKey(tx.getHash(), subTx.getPubKeyId());
            writeUser(user);
            if(userMap.size() == 1)
                currentUser = user;

            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean processSubTxTopup(Transaction tx, StoredBlock prev)
    {
        lock.lock();
        try {

            SubTxTopup subTx = (SubTxTopup)tx.getExtraPayloadObject();
            EvolutionUser user = getUser(subTx.getRegTxId());
            if (user == null) {
                return false;
            }

            if (!processSubTxTopupForUser(user, tx, subTx)) {
                return false;
            }

            // We don't push the subTx hash here as everyone can topup a users credits and the order is also not important
            writeUser(user);
            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean processSubTxTopupForUser(EvolutionUser user, Transaction tx, SubTxTopup subTx)
    {
        Coin topupAmount = getTxBurnAmount(tx);
        user.addTopUp(topupAmount, tx);
        return true;
    }

    boolean processSubTxResetKey(Transaction tx, StoredBlock prev)
    {
        lock.lock();
        try {


            SubTxResetKey subTx = (SubTxResetKey) tx.getExtraPayloadObject();
            EvolutionUser user = getSubTxAndUser(tx);
            if (user == null)
                return false;

            if (!processSubTxResetKeyForUser(user, tx, subTx)) {
                return false;
            }

            //pecialTxFees += subTx.creditFee;

            writeUser(user);
            //userDb.PushSubTx(subTx.regTxId, tx.GetHash());
            //userDb.PushPubKey(subTx.regTxId, subTx.newPubKeyId);

            return true;
        } finally {
            lock.unlock();
        }
    }

    boolean processSubTxResetKeyForUser(EvolutionUser user, Transaction tx, SubTxResetKey subTx)
    {
        user.setCurSubTx(tx.getHash());
        user.setCurPubKeyID(subTx.getNewPubKeyId());
        user.addSpend(subTx.getCreditFee());
        user.addReset(tx);
        return true;
    }

    public EvolutionUser getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(EvolutionUser currentUser) {
        this.currentUser = currentUser;
    }

    public boolean processSpecialTransaction(Transaction tx, Block currentBlock) {
        try {
            StoredBlock prev = currentBlock != null ? context.blockChain.getBlockStore().get(currentBlock.getHash()) : null;
            prev = prev != null ? prev.getPrev(context.blockChain.getBlockStore()) : null;

            switch (tx.getType()) {
                case TRANSACTION_SUBTX_REGISTER:
                    return checkSubTxRegister(tx, prev) && processSubTxRegister(tx, prev);
                case TRANSACTION_SUBTX_TOPUP:
                    return checkSubTxTopup(tx, prev) && processSubTxTopup(tx, prev);
                case TRANSACTION_SUBTX_RESETKEY:
                    return checkSubTxResetKey(tx, prev) && processSubTxResetKey(tx, prev);
                case TRANSACTION_SUBTX_CLOSEACCOUNT:
                case TRANSACTION_SUBTX_TRANSITION:
                        return false;
            }
        } catch (BlockStoreException x) {
            return false;
        }
        return false;
    }

    @Override
    public void receiveFromBlock(Transaction tx, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
        //this does not handle side chains!
        processSpecialTransaction(tx, block.getHeader());
    }

    @Override
    public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, BlockChain.NewBlockType blockType, int relativityOffset) throws VerificationException {
        return false;
    }

    @Override
    public String toString() {
        return "EvolutionUserManager:  " + userMap.size() + " users.";
    }
}
