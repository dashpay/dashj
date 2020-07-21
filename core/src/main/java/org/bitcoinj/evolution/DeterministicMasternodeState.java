package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;

import java.io.IOException;
import java.io.OutputStream;

public class DeterministicMasternodeState extends ChildMessage {
    int registeredHeight;//{-1};
    int lastPaidHeight;//{0};
    int PoSePenality;//{0};
    int PoSeRevivedHeight;//{-1};
    int PoSeBanHeight;//{-1};
    short revocationReason;//{ProUpRevTx::REASON_NOT_SPECIFIED};

    Sha256Hash confirmedHash;
    Sha256Hash confirmedHashWithProRegTxHash;

    KeyId keyIDOwner;
    KeyId keyIDOperator;
    BLSPublicKey pubKeyOperator;
    KeyId keyIDVoting;
    MasternodeAddress address;
    Script scriptPayout;
    Script scriptOperatorPayout;

    DeterministicMasternodeState(NetworkParameters params, byte [] payload, int offset) {
        super(params, payload, offset);
    }

    public DeterministicMasternodeState(DeterministicMasternodeState other) {
        registeredHeight = other.registeredHeight;
        lastPaidHeight = other.lastPaidHeight;
        PoSePenality = other.PoSePenality;
        PoSeRevivedHeight = other.PoSeRevivedHeight;
        PoSeBanHeight = other.PoSeBanHeight;
        revocationReason = other.revocationReason;
        keyIDOwner = other.keyIDOwner;
        pubKeyOperator = other.pubKeyOperator;
        keyIDVoting = other.keyIDVoting;
        address = other.address.duplicate();
        scriptPayout = new Script(other.scriptPayout.getProgram());
        scriptOperatorPayout = new Script(other.scriptOperatorPayout.getProgram());
    }

    @Override
    protected void parse() throws ProtocolException {
        registeredHeight = (int)readUint32();
        lastPaidHeight = (int)readUint32();
        PoSePenality = (int)readUint32();
        PoSeRevivedHeight = (int)readUint32();
        PoSeBanHeight = (int)readUint32();
        revocationReason = (short)readUint16();
        confirmedHash = readHash();
        confirmedHashWithProRegTxHash = readHash();
        keyIDOwner = new KeyId(params, payload, cursor);
        cursor += keyIDOwner.getMessageSize();
        if(!params.isSupportingEvolution()) {
            keyIDOperator = new KeyId(params, payload, cursor);
            cursor += keyIDOperator.getMessageSize();
        } else {
            pubKeyOperator = new BLSPublicKey(params, payload, cursor);
            cursor += pubKeyOperator.getMessageSize();
        }
        keyIDVoting = new KeyId(params, payload, cursor);
        cursor += keyIDVoting.getMessageSize();
        address = new MasternodeAddress(params, payload, cursor, 0);
        cursor += address.getMessageSize();
        scriptPayout = new Script(readByteArray());
        scriptOperatorPayout = new Script(readByteArray());
    }

    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        Utils.uint32ToByteStreamLE(registeredHeight, stream);
        Utils.uint32ToByteStreamLE(lastPaidHeight, stream);
        Utils.uint32ToByteStreamLE(PoSePenality, stream);
        Utils.uint32ToByteStreamLE(PoSeRevivedHeight, stream);
        Utils.uint32ToByteStreamLE(PoSeBanHeight, stream);
        Utils.uint16ToByteStreamLE(revocationReason, stream);
        stream.write(confirmedHash.getReversedBytes());
        stream.write(confirmedHashWithProRegTxHash.getReversedBytes());
        keyIDOwner.bitcoinSerialize(stream);
        if(!params.isSupportingEvolution()) {
            keyIDOperator.bitcoinSerialize(stream);
        } else {
            pubKeyOperator.bitcoinSerialize(stream);
        }
        keyIDVoting.bitcoinSerialize(stream);
        address.bitcoinSerialize(stream);
        Utils.bytesToByteStream(scriptPayout.getProgram(), stream);
        Utils.bytesToByteStream(scriptOperatorPayout.getProgram(), stream);
    }

    public String toString()
    {
        String payoutAddress = "unknown";
        String operatorRewardAddress = "none";
        try {
            payoutAddress = scriptPayout.getToAddress(params).toString();
        } catch (AddressFormatException x) {

        } catch (ScriptException x) {

        }
        try {
            operatorRewardAddress = scriptOperatorPayout.getToAddress(params).toString();
        } catch (AddressFormatException x) {

        } catch (ScriptException x) {

        }

        return String.format("DeterministicMNState(registeredHeight=%d, lastPaidHeight=%d, PoSePenality=%d, PoSeRevivedHeight=%d, PoSeBanHeight=%d, revocationReason=%d, "+
                "keyIDOwner=%s, pubkeyOperator=%s, keyIDVoting=%s, addr=%s, payoutAddress=%s, operatorRewardAddress=%s)",
                registeredHeight, lastPaidHeight, PoSePenality, PoSeRevivedHeight, PoSeBanHeight, revocationReason,
                keyIDOwner, pubKeyOperator, keyIDVoting, address.toString(), payoutAddress, operatorRewardAddress);
    }
}
