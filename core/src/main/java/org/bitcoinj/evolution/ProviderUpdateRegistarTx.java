package org.bitcoinj.evolution;


import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class ProviderUpdateRegistarTx extends SpecialTxPayload {
    public static final int CURRENT_VERSION = 1;
    public static final int LEGACY_BLS_VERSION = 1;
    public static final int BASIC_BLS_VERSION = 2;
    public static final int MESSAGE_SIZE = 228;
    public static final int MESSAGE_SIZE_WITHOUT_SIGNATURE = MESSAGE_SIZE - 65;


    Sha256Hash proTxHash;
    int mode;
    BLSPublicKey pubkeyOperator;
    KeyId keyIDVoting;
    Script scriptPayout;
    Sha256Hash inputsHash;  //replay protection
    MasternodeSignature signature;

    public ProviderUpdateRegistarTx(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public ProviderUpdateRegistarTx(NetworkParameters params, int version, Sha256Hash proTxHash,
                                    int mode, BLSPublicKey pubkeyOperator, KeyId keyIDVoting,
                                    Script scriptPayout, Sha256Hash inputsHash) {
        super(params, version);
        this.proTxHash = proTxHash;
        this.mode = mode;
        this.pubkeyOperator = pubkeyOperator;
        this.keyIDVoting = keyIDVoting;
        this.scriptPayout = scriptPayout;
        this.inputsHash = inputsHash;
        length = MESSAGE_SIZE_WITHOUT_SIGNATURE;
    }

    public ProviderUpdateRegistarTx(NetworkParameters params, int version, Sha256Hash proTxHash,
                                    int mode, BLSPublicKey pubkeyOperator, KeyId keyIDVoting,
                                    Script scriptPayout, Sha256Hash inputsHash, MasternodeSignature signature) {
        this(params, version, proTxHash, mode, pubkeyOperator, keyIDVoting, scriptPayout, inputsHash);
        this.signature = signature;
        length = MESSAGE_SIZE;
    }

    public ProviderUpdateRegistarTx(NetworkParameters params, int version, Sha256Hash proTxHash,
                                    int mode, BLSPublicKey pubkeyOperator, KeyId keyIDVoting,
                                    Script scriptPayout, Sha256Hash inputsHash, ECKey signingKey) {
        this(params, version, proTxHash, mode, pubkeyOperator, keyIDVoting, scriptPayout, inputsHash);
        sign(signingKey);
        length = MESSAGE_SIZE;
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();  //read version
        proTxHash = readHash();
        mode = readUint16();
        pubkeyOperator = new BLSPublicKey(params, payload, cursor, version == LEGACY_BLS_VERSION);
        cursor += pubkeyOperator.getMessageSize();
        keyIDVoting = new KeyId(params, payload, cursor);
        cursor += keyIDVoting.getMessageSize();
        scriptPayout = new Script(readByteArray());
        inputsHash = readHash();
        signature = new MasternodeSignature(params, payload, cursor);
        cursor += signature.getMessageSize();

        length = cursor - offset;
    }

    protected void bitcoinSerializeWithoutSignature(OutputStream stream) throws IOException{
        super.bitcoinSerializeToStream(stream);
        stream.write(proTxHash.getReversedBytes());
        Utils.uint16ToByteStreamLE(mode, stream);
        pubkeyOperator.bitcoinSerialize(stream);
        keyIDVoting.bitcoinSerialize(stream);
        Utils.bytesToByteStream(scriptPayout.getProgram(), stream);
        stream.write(inputsHash.getReversedBytes());
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        bitcoinSerializeWithoutSignature(stream);
        if(signature != null)
            signature.bitcoinSerialize(stream);
        else stream.write(0);
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        String payee = "unknown";
        try {
            payee = scriptPayout.getToAddress(params).toString();
        } catch (AddressFormatException x) {
            //swallow
        } catch (ScriptException x) {
            //swallow
        }
        return String.format("ProUpRegTx(version=%d, proTxHash=%s, pubkeyOperator=%s, votingAddress=%s, payoutAddress=%s)",
                version, proTxHash, pubkeyOperator, keyIDVoting, payee);

    }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_PROVIDER_UPDATE_REGISTRAR;
    }

    @Override
    public String getName() {
        return "providerRegisterTx";
    }

    @Override
    public JSONObject toJson() {
        JSONObject result = new JSONObject();

        result.append("version", version);
        result.append("proTxHash", proTxHash);
        result.append("votingAddress", keyIDVoting);

        try {
            Address destination = scriptPayout.getToAddress(params);
            result.append("payoutAddress", destination);
        } catch (AddressFormatException x) {
            //swallow
        }
        result.append("pubkeyOperator", pubkeyOperator);
        result.append("inputsHash", inputsHash);

        return result;
    }

    @Override
    public Sha256Hash getHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bitcoinSerializeWithoutSignature(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x.getMessage());
        }
    }

    public Sha256Hash getSignatureHash() {
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            bitcoinSerializeWithoutSignature(bos);
            return Sha256Hash.wrap(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException x) {
            throw new RuntimeException(x.getMessage());
        }
    }

    void sign(ECKey signingKey) {
        signature = HashSigner.signHash(getSignatureHash(), signingKey);
        unCache();
    }
}
