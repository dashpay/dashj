package org.bitcoinj.evolution;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class EvolutionUser extends ChildMessage {

    private Sha256Hash regTxId;
    private Transaction regTx;
    String userName;

    KeyId curPubKeyID;
    private Sha256Hash hashCurSubTx;
    private Sha256Hash hashCurSTPacket;

    Coin topupCredits;
    Coin spentCredits;

    boolean closed;

    protected HashMap<Sha256Hash, Transaction> topupTxMap;
    protected HashMap<Sha256Hash, Transaction> resetTxMap;

    EvolutionUser(NetworkParameters params) {
        super(params);
        regTxId = Sha256Hash.ZERO_HASH;
        hashCurSTPacket = Sha256Hash.ZERO_HASH;
        hashCurSubTx = Sha256Hash.ZERO_HASH;
        topupTxMap = new HashMap<Sha256Hash, Transaction>(1);
        resetTxMap = new HashMap<Sha256Hash, Transaction>(1);
    }

    /*public EvolutionUser(Sha256Hash regTxId, String userName, KeyId curPubKeyID) {
        this.regTxId = regTxId;
        this.userName = userName;
        this.hashCurSubTx = regTxId;
        this.curPubKeyID = curPubKeyID;
        this.topupCredits = Coin.ZERO;
        this.spentCredits = Coin.ZERO;
        hashCurSTPacket = Sha256Hash.ZERO_HASH;
        hashCurSubTx = Sha256Hash.ZERO_HASH;
    }*/

    public EvolutionUser(Transaction regTx, String userName, KeyId curPubKeyID) {
        this.regTxId = regTx.getHash();
        this.regTx = regTx;
        this.userName = userName;
        this.hashCurSubTx = regTxId;
        this.curPubKeyID = curPubKeyID;
        this.topupCredits = Coin.ZERO;
        this.spentCredits = Coin.ZERO;
        hashCurSTPacket = Sha256Hash.ZERO_HASH;
        topupTxMap = new HashMap<Sha256Hash, Transaction>(1);
        resetTxMap = new HashMap<Sha256Hash, Transaction>(1);
    }

    public EvolutionUser(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    @Override
    protected void parse() throws ProtocolException {
        regTxId = readHash();
        regTx = new Transaction(params, payload, cursor);
        cursor += regTx.getMessageSize();
        userName = readStr();
        curPubKeyID = new KeyId(params, payload, cursor);
        cursor += curPubKeyID.getMessageSize();
        hashCurSubTx = readHash();
        hashCurSTPacket = readHash();
        topupCredits = Coin.valueOf(readInt64());
        spentCredits = Coin.valueOf(readInt64());
        closed = readBytes(1)[0] == 1 ? true : false;
        int size = (int)readVarInt();
        topupTxMap = new HashMap<Sha256Hash, Transaction>(size);
        for(int i = 0; i < size; ++i) {
            Transaction topup = new Transaction(params, payload, cursor);
            cursor += topup.getMessageSize();
            topupTxMap.put(topup.getHash(), topup);
        }
        size = (int)readVarInt();
        resetTxMap = new HashMap<Sha256Hash, Transaction>(size);
        for(int i = 0; i < size; ++i) {
            Transaction reset = new Transaction(params, payload, cursor);
            cursor += reset.getMessageSize();
            resetTxMap.put(reset.getHash(), reset);
        }
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(regTxId.getReversedBytes());
        regTx.bitcoinSerialize(stream);
        Utils.stringToByteStream(userName, stream);
        curPubKeyID.bitcoinSerialize(stream);
        stream.write(hashCurSubTx.getReversedBytes());
        stream.write(hashCurSTPacket.getReversedBytes());
        Utils.int64ToByteStreamLE(topupCredits.getValue(), stream);
        Utils.int64ToByteStreamLE(spentCredits.getValue(), stream);
        stream.write(closed ? 1 : 0);
        stream.write(new VarInt(topupTxMap.size()).encode());
        for(Map.Entry<Sha256Hash, Transaction> entry : topupTxMap.entrySet()) {
            entry.getValue().bitcoinSerialize(stream);
        }
        stream.write(new VarInt(resetTxMap.size()).encode());
        for(Map.Entry<Sha256Hash, Transaction> entry : resetTxMap.entrySet()) {
            entry.getValue().bitcoinSerialize(stream);
        }
    }

    public Sha256Hash getRegTxId() {
        return regTxId;
    }

    public Transaction getRegTx() {
        return regTx;
    }

    public String getUserName(){
        return userName;
    }

    public Coin getTopUpCredits(){
        return topupCredits;
    }

    public Coin getSpentCredits(){
        return spentCredits;
    }

    public Coin getCreditBalance(){
        return topupCredits.subtract(spentCredits);
    }

    public void addTopUp(Coin amount, Transaction topupTx) {
        topupCredits = topupCredits.add(amount);
        topupTxMap.put(topupTx.getHash(), topupTx);
    }

    public void addSpend(Coin amount) {
        spentCredits = spentCredits.add(amount);
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public boolean isClosed(){
        return closed;
    }

    public void setCurPubKeyID(KeyId keyID) {
        curPubKeyID = keyID;
    }

    public KeyId getCurPubKeyID(){
        return curPubKeyID;
    }

    public void setCurSubTx(Sha256Hash subTxHash) {
        hashCurSubTx = subTxHash;
    }

    public void setCurHashSTPacket( Sha256Hash hashSTPacket) {
        hashCurSTPacket = hashSTPacket;
    }

    public Sha256Hash getCurSubTx(){
        return hashCurSubTx;
    }
    public Sha256Hash getCurHashSTPacket() {
        return hashCurSTPacket;
    }

    @Override
    public String toString() {
        return "EvolutionUser:  " + userName + " ["+getCreditBalance()+"] ";
    }

    public boolean hasTopup(Transaction tx) {
        return topupTxMap.containsKey(tx.getHash());
    }

    public boolean hasReset(Transaction tx) {
        return resetTxMap.containsKey(tx.getHash());
    }

    public void addReset(Transaction tx) {
        resetTxMap.put(tx.getHash(), tx);
    }
}
