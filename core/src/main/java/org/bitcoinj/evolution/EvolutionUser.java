package org.bitcoinj.evolution;

import org.bitcoinj.core.*;

import java.io.IOException;
import java.io.OutputStream;

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

    EvolutionUser(NetworkParameters params) {
        super(params);
        regTxId = Sha256Hash.ZERO_HASH;
        hashCurSTPacket = Sha256Hash.ZERO_HASH;
        hashCurSubTx = Sha256Hash.ZERO_HASH;
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

    public void addTopUp(Coin amount) {
        topupCredits = topupCredits.add(amount);
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
}
