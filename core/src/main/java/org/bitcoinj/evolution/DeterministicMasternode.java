package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;

import java.io.IOException;
import java.io.OutputStream;

public class DeterministicMasternode extends Masternode {

    public DeterministicMasternode(NetworkParameters params, byte [] payload, int cursor) {
        super(params, payload, cursor);
    }

    public DeterministicMasternode(DeterministicMasternode other) {
        super(other.params);
        proTxHash = other.proTxHash;
        collateralOutpoint = other.collateralOutpoint;
        operatorReward = other.operatorReward;
        state = new DeterministicMasternodeState(other.state);
    }

    TransactionOutPoint collateralOutpoint;
    short operatorReward;
    DeterministicMasternodeState state;

    @Override
    protected void parse() throws ProtocolException {
        proTxHash = readHash();
        collateralOutpoint = new TransactionOutPoint(params, payload, cursor);
        cursor += collateralOutpoint.getMessageSize();
        operatorReward = (short)readUint16();
        state = new DeterministicMasternodeState(params, payload, cursor);
        cursor += state.getMessageSize();
        length = cursor - offset;
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        stream.write(proTxHash.getReversedBytes());
        collateralOutpoint.bitcoinSerialize(stream);
        Utils.uint16ToByteStreamLE(operatorReward, stream);
        state.bitcoinSerializeToStream(stream);
    }

    public Sha256Hash getConfirmedHash() {
        return state.confirmedHash;
    }

    public MasternodeAddress getService() {
        return state.address;
    }

    public KeyId getKeyIdOwner() {
        return state.keyIDOwner;
    }

    public BLSPublicKey getPubKeyOperator() {
        return state.pubKeyOperator;
    }

    public KeyId getKeyIdVoting() {
        return state.keyIDVoting;
    }

    public boolean isValid() {
        return false;
    }

    public Sha256Hash getConfirmedHashWithProRegTxHash() {
        return state.confirmedHashWithProRegTxHash;
    }


    public String toString()
    {
        return String.format("DeterministicMN(proTxHash=%s, nCollateralIndex=%s, operatorReward=%f, state=%s",
                proTxHash, collateralOutpoint, (double)operatorReward / 100, state.toString());
    }
}
