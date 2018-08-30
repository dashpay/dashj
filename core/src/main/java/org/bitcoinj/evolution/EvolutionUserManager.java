package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class EvolutionUserManager extends AbstractManager {

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

    void deleteUser(Sha256Hash regTxId) {
        if(userMap.containsKey(regTxId))
            return;

        EvolutionUser user = getUser(regTxId);
        if (user == null)
            return;

        userMap.remove(user);
    }

    EvolutionUser getUser(Sha256Hash regTxId) {
        return userMap.get(regTxId);
    }

    Sha256Hash getUserIdByName(String userName) {
        for(Map.Entry<Sha256Hash, EvolutionUser> user : userMap.entrySet())
        {
            if(user.getValue().getUserName().equals(userName))
                return user.getValue().getCurSubTx();
        }
        return null;
    }

    boolean userExists(Sha256Hash regTxId) {
        return userMap.containsKey(regTxId);
    }

    boolean userNameExists(String userName) {
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

            SubTxTopup subTx = (SubTxTopup)tx.getExtraPayloadObject();
            EvolutionUser user = getUser(subTx.getRegTxId());
            if (user == null) {
                return false;
            }

            Coin topupAmount = getTxBurnAmount(tx);
            if (topupAmount.compareTo(SubTxTopup.MIN_SUBTX_TOPUP) < 0) {
                return false;
            }

            return true;
        } finally {
            lock.unlock();
        }
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
        user.addTopUp(getTxBurnAmount(regTx));

        return user;
    }

    public boolean processSubTxRegister(Transaction tx, StoredBlock prev, Coin specialTxFees)
    {
        lock.lock();
        try {

            SubTxRegister subTx = (SubTxRegister)tx.getExtraPayloadObject();

            Coin topupAmount = getTxBurnAmount(tx);

            EvolutionUser user = new EvolutionUser(tx, subTx.userName, subTx.getPubKeyId());
            user.addTopUp(topupAmount);
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

    boolean processSubTxTopup(Transaction tx, StoredBlock prev, Coin specialTxFees)
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
        user.addTopUp(topupAmount);
        return true;
    }

    public EvolutionUser getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(EvolutionUser currentUser) {
        this.currentUser = currentUser;
    }
}
