package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.TransactionReceivedInBlockListener;
import org.bitcoinj.evolution.listeners.EvolutionUserRemovedEventListener;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.evolution.listeners.EvolutionUserAddedEventListener;
import org.bitcoinj.utils.ListenerRegistration;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
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
        this.userAddedListeners = new CopyOnWriteArrayList<ListenerRegistration<EvolutionUserAddedEventListener>>();
        this.userRemovedListeners = new CopyOnWriteArrayList<ListenerRegistration<EvolutionUserRemovedEventListener>>();
    }

    public EvolutionUserManager(Context context) {
        super(context);
        userMap = new HashMap<Sha256Hash, EvolutionUser>(1);
        this.userAddedListeners = new CopyOnWriteArrayList<ListenerRegistration<EvolutionUserAddedEventListener>>();
        this.userRemovedListeners = new CopyOnWriteArrayList<ListenerRegistration<EvolutionUserRemovedEventListener>>();
    }

    public EvolutionUserManager(NetworkParameters params, byte [] payload) {
        super(params, payload, 0);
        this.userAddedListeners = new CopyOnWriteArrayList<ListenerRegistration<EvolutionUserAddedEventListener>>();
        this.userRemovedListeners = new CopyOnWriteArrayList<ListenerRegistration<EvolutionUserRemovedEventListener>>();
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
        queueOnUserRemoved(user);
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

    public EvolutionUser getUserByName(String username) {
        Sha256Hash regTxId = getUserIdByName(username);
        if(regTxId != null)
            return getUser(regTxId);
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

    void checkSubTxRegister(Transaction tx, StoredBlock prevBlock) throws VerificationException {
        lock.lock();
        try {
            SubTxRegister subTxRegister = new SubTxRegister(tx.getParams(), tx);

            subTxRegister.check();

            if(userNameExists(subTxRegister.getUserName())) {
                EvolutionUser user = getUserByName(subTxRegister.getUserName());
                if(user.getRegTxId().equals(tx.getHash()))
                    return;  //we have already processed this subTxRegister, so return
                //This username conflicts with an existing user
                throw new VerificationException("Username exists: " + subTxRegister.getUserName());
            }

            Coin topupAmount = getTxBurnAmount(tx);

            if(topupAmount.compareTo(SubTxTopup.MIN_SUBTX_TOPUP) < 0)
                throw new VerificationException("SubTxRegister:  Topup too low: " + topupAmount + " < " + SubTxTopup.MIN_SUBTX_TOPUP);

            StringBuilder errorMessage = new StringBuilder();
            if(!HashSigner.verifyHash(subTxRegister.getSignHash(), subTxRegister.getPubKeyId().getBytes(), subTxRegister.getSignature(), errorMessage))
            {
                throw new VerificationException("SubTxRegister: invalid signature: "+ errorMessage);
            }

        } catch (ProtocolException x) {

        } finally {
            lock.unlock();
        }
    }

    void checkSubTxTopup(Transaction tx, StoredBlock prev)
    {
        lock.lock();
        try {
            SubTxTopup topup = (SubTxTopup)tx.getExtraPayloadObject();
            topup.check();

            EvolutionUser user = getUser(topup.getRegTxId());

            if (user == null) {
                throw new SpecialTxException.UserDoesNotExist(topup.regTxId);
            }

            if(user.isClosed())
                throw new VerificationException("SubTxTopup:  user ["+ topup.regTxId +"] is closed");

            Coin topupAmount = getTxBurnAmount(tx);
            if (topupAmount.compareTo(SubTxTopup.MIN_SUBTX_TOPUP) < 0) {
                throw new VerificationException("SubTxTopup:  Topup too low: " + topupAmount + " < " + SubTxTopup.MIN_SUBTX_TOPUP);
            }

        } finally {
            lock.unlock();
        }
    }

    void checkSubTxResetKey(Transaction tx, StoredBlock prev)
    {
        lock.lock();
        try {

            SubTxResetKey subTx = (SubTxResetKey)tx.getExtraPayloadObject();
            subTx.check();

            EvolutionUser user = getUser(subTx.regTxId);
            checkSubTxAndFeeForUser(tx, subTx, user);

        } finally {
            lock.unlock();
        }
    }

    void checkSubTxAndFeeForUser(Transaction tx, SubTxForExistingUser subTxRet, EvolutionUser user) throws VerificationException
    {
        checkSubTxForUser(tx, subTxRet, user);

        // TODO min fee depending on TS size
        if (subTxRet.creditFee.compareTo(EVO_TS_MIN_FEE) < 0 || subTxRet.creditFee.compareTo(EVO_TS_MAX_FEE) > 0) {
            throw new VerificationException("SubTx:  fees are too high or low");
        }

        if (user.getCreditBalance().compareTo(subTxRet.creditFee) < 0) {
            // Low DoS score as peers may not know about the low balance (e.g. due to not mined topups)
            //state.DoS(10, false, REJECT_INSUFFICIENTFEE, "bad-subtx-nocredits");
            throw new VerificationException("SubTx:  Not enough credits");
        }
    }

    void checkSubTxForUser(Transaction tx, SubTxForExistingUser subTxRet, EvolutionUser user)
    {
        if (getSubTxAndUser(tx) == null) {
            throw new SpecialTxException.UserDoesNotExist(((SubTxForExistingUser)tx.getExtraPayloadObject()).regTxId);
        }

        if (!subTxRet.hashPrevSubTx.equals(user.getCurSubTx())) {
            throw new VerificationException("SubTx: prev subtx not equal to current user["+user.getUserName()+"] tx: " + subTxRet.hashPrevSubTx + " != " + user.getCurSubTx());
        }

        StringBuilder errorMessage = new StringBuilder();
        if (!HashSigner.verifyHash(subTxRet.getSignHash(), user.getCurPubKeyID().getBytes(), subTxRet.getSignature(), errorMessage)) {
            throw new VerificationException("SubTx: invalid signature: " + errorMessage);
        }
    }

    EvolutionUser getSubTxAndUser(Transaction tx) {
        return getSubTxAndUser(tx, false);
    }

    EvolutionUser getSubTxAndUser(Transaction tx, boolean allowClosed) throws VerificationException
    {
        SubTxForExistingUser subTx = (SubTxForExistingUser)tx.getExtraPayloadObject();
        subTx.check();

        EvolutionUser user = getUser(subTx.getRegTxId());

        if(user == null)
            return null;

        if(!allowClosed && user.isClosed())
            return null;

        return user;
    }

    EvolutionUser buildUser(Transaction regTx) throws VerificationException
    {
        if (regTx == null) {
            return null;
        }

        checkSubTxRegister(regTx, null);

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

            EvolutionUser user = getUser(tx.getHash());
            if(user != null)
                return false;  //User exists, skip further processing

            Coin topupAmount = getTxBurnAmount(tx);

            user = new EvolutionUser(tx, subTx.userName, subTx.getPubKeyId());
            user.addTopUp(topupAmount, tx);
            //userDb.pushSubTx(tx.getHash(), tx.getHash());
            //userDb.pushPubKey(tx.getHash(), subTx.getPubKeyId());
            writeUser(user);
            queueOnUserAdded(user);
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

            if(user.hasTopup(tx)) // don't process again, if it was already added
                return false;

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

            if(user.hasReset(tx))
                return false;

            if (!processSubTxResetKeyForUser(user, tx, subTx)) {
                return false;
            }

            //specialTxFees += subTx.creditFee;

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

    public boolean processSpecialTransaction(Transaction tx, Block currentBlock) throws VerificationException {
        try {
            StoredBlock prev = currentBlock != null ? context.blockChain.getBlockStore().get(currentBlock.getHash()) : null;
            prev = prev != null ? prev.getPrev(context.blockChain.getBlockStore()) : null;

            switch (tx.getType()) {
                case TRANSACTION_SUBTX_REGISTER:
                    checkSubTxRegister(tx, prev);
                    return processSubTxRegister(tx, prev);
                case TRANSACTION_SUBTX_TOPUP:
                    checkSubTxTopup(tx, prev);
                    return processSubTxTopup(tx, prev);
                case TRANSACTION_SUBTX_RESETKEY:
                    checkSubTxResetKey(tx, prev);
                    return processSubTxResetKey(tx, prev);
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

    private transient CopyOnWriteArrayList<ListenerRegistration<EvolutionUserAddedEventListener>> userAddedListeners;
    private transient CopyOnWriteArrayList<ListenerRegistration<EvolutionUserRemovedEventListener>> userRemovedListeners;

    /**
     * Adds an event listener object. Methods on this object are called when new user is being added or removed.
     * Runs the listener methods in the user thread.
     */
    public void addUserAddedListener(EvolutionUserAddedEventListener listener) {
        addUserAddedListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when new user is being added or removed.
     * The listener is executed by the given executor.
     */
    public void addUserAddedListener(EvolutionUserAddedEventListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        userAddedListeners.add(new ListenerRegistration<EvolutionUserAddedEventListener>(listener, executor));
    }

    /**
     * Adds an event listener object. Methods on this object are called when new user is being added or removed.
     * Runs the listener methods in the user thread.
     */
    public void addUserRemovedListener(EvolutionUserRemovedEventListener listener) {
        addUserRemovedListener(listener, Threading.USER_THREAD);
    }

    /**
     * Adds an event listener object. Methods on this object are called when new user is being added or removed.
     * The listener is executed by the given executor.
     */
    public void addUserRemovedListener(EvolutionUserRemovedEventListener listener, Executor executor) {
        // This is thread safe, so we don't need to take the lock.
        userRemovedListeners.add(new ListenerRegistration<EvolutionUserRemovedEventListener>(listener, executor));
    }

    /**
     * Removes the given event listener object. Returns true if the listener was removed, false if that listener
     * was never added.
     */
    public boolean removeEventListener(EvolutionUserAddedEventListener listener) {
        return ListenerRegistration.removeFromList(listener, userAddedListeners);
    }

    public void queueOnUserAdded(final EvolutionUser user) {
        for (final ListenerRegistration<EvolutionUserAddedEventListener> registration : userAddedListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onUserAdded(user);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onUserAdded(user);
                    }
                });
            }
        }
    }

    public void queueOnUserRemoved(final EvolutionUser user) {
        for (final ListenerRegistration<EvolutionUserRemovedEventListener> registration : userRemovedListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onUserRemoved(user);
            } else {
                registration.executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        registration.listener.onUserRemoved(user);
                    }
                });
            }
        }
    }

    public List<EvolutionUser> getUsers() {
        lock.lock();
        try {
            List<EvolutionUser> evoUsersList = new ArrayList<EvolutionUser>(userMap.size());
            for (Map.Entry<Sha256Hash, EvolutionUser> entry : userMap.entrySet()) {
                evoUsersList.add(entry.getValue());
            }
            return evoUsersList;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String toString() {
        return "EvolutionUserManager:  " + userMap.size() + " users.";
    }
}
