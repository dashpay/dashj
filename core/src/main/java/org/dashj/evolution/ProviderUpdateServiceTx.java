package org.dashj.evolution;


import org.dashj.core.*;
import org.dashj.crypto.BLSPublicKey;
import org.dashj.crypto.BLSSecretKey;
import org.dashj.crypto.BLSSignature;
import org.dashj.script.Script;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class ProviderUpdateServiceTx extends SpecialTxPayload {
    public static final int CURRENT_VERSION = 1;
    public static final int MESSAGE_SIZE = 181;
    public static final int MESSAGE_SIZE_WITHOUT_SIGNATURE = MESSAGE_SIZE - 96;


    Sha256Hash proTxHash;
    MasternodeAddress address;
    Script scriptOperatorPayout;
    Sha256Hash inputsHash;  //replay protection
    BLSSignature signature;

    public ProviderUpdateServiceTx(NetworkParameters params, Transaction tx) {
        super(params, tx);
    }

    public ProviderUpdateServiceTx(NetworkParameters params, int version, Sha256Hash proTxHash,
                                   MasternodeAddress address,
                            Script scriptOperatorPayout, Sha256Hash inputsHash) {
        super(params, version);
        this.proTxHash = proTxHash;
        this.address = address.duplicate();
        this.scriptOperatorPayout = scriptOperatorPayout;
        this.inputsHash = inputsHash;
        length = MESSAGE_SIZE_WITHOUT_SIGNATURE;
    }

    public ProviderUpdateServiceTx(NetworkParameters params, int version, Sha256Hash proTxHash,
                                   MasternodeAddress address,
                                   Script scriptOperatorPayout, Sha256Hash inputsHash, BLSSignature signature) {
        this(params, version, proTxHash, address, scriptOperatorPayout, inputsHash);
        this.signature = signature;
        length = MESSAGE_SIZE;
    }

    public ProviderUpdateServiceTx(NetworkParameters params, int version, Sha256Hash proTxHash,
                                   MasternodeAddress address,
                                   Script scriptOperatorPayout, Sha256Hash inputsHash, BLSSecretKey signingKey) {
        this(params, version, proTxHash, address, scriptOperatorPayout, inputsHash);
        sign(signingKey);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();  //read version
        proTxHash = readHash();
        address = new MasternodeAddress(params, payload, cursor, NetworkParameters.ProtocolVersion.CURRENT.getBitcoinProtocolVersion());
        cursor += address.getMessageSize();
        scriptOperatorPayout = new Script(readByteArray());
        inputsHash = readHash();
        signature = new BLSSignature(params, payload, cursor);
        cursor += signature.getMessageSize();

        length = cursor - offset;
    }

    protected void bitcoinSerializeWithoutSignature(OutputStream stream) throws IOException{
        super.bitcoinSerializeToStream(stream);
        stream.write(proTxHash.getReversedBytes());
        address.bitcoinSerialize(stream);
        if(scriptOperatorPayout != null)
            Utils.bytesToByteStream(scriptOperatorPayout.getProgram(), stream);
        else stream.write(new byte[0]);
        stream.write(inputsHash.getReversedBytes());
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        bitcoinSerializeWithoutSignature(stream);
        signature.bitcoinSerialize(stream);
    }

    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    public String toString() {
        String payee = "unknown";
        try {
            payee = scriptOperatorPayout.getToAddress(params).toString();
        } catch (AddressFormatException x) {
            //swallow
        } catch (ScriptException x) {
            //swallow
        }
        return String.format("ProUpServTx(version=%d, proTxHash=%s, address=%s, operatorPayoutAddress=%s)",
                version, proTxHash, address, payee);

    }

    @Override
    public Transaction.Type getType() {
        return Transaction.Type.TRANSACTION_PROVIDER_UPDATE_SERVICE;
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
        result.append("service", address.toString());

        try {
            Address destination = scriptOperatorPayout.getToAddress(params);
            result.append("payoutAddress", destination);
        } catch (AddressFormatException x) {
            //swallow
        }
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

    void sign(BLSSecretKey signingKey) {
        signature = signingKey.Sign(getSignatureHash());
        length = MESSAGE_SIZE;
        unCache();
    }
}
