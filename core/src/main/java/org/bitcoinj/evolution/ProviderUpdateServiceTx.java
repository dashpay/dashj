/*
 * Copyright 2019 Dash Core Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.evolution;


import org.bitcoinj.core.*;
import org.bitcoinj.crypto.BLSPublicKey;
import org.bitcoinj.crypto.BLSSecretKey;
import org.bitcoinj.crypto.BLSSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

public class ProviderUpdateServiceTx extends SpecialTxPayload {
    public static final int CURRENT_VERSION = 2;
    public static final int LEGACY_BLS_VERSION = 1;
    public static final int BASIC_BLS_VERSION = 2;
    public static final int MESSAGE_SIZE = 181;
    public static final int MESSAGE_SIZE_WITHOUT_SIGNATURE = MESSAGE_SIZE - 96;


    MasternodeType type;
    Sha256Hash proTxHash;
    MasternodeAddress address;
    Script scriptOperatorPayout;
    Sha256Hash inputsHash;  //replay protection
    KeyId platformNodeID;
    int platformP2PPort;
    int platformHTTPPort;
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
        this.type = MasternodeType.REGULAR;
        length = MESSAGE_SIZE_WITHOUT_SIGNATURE;
    }

    public ProviderUpdateServiceTx(NetworkParameters params, byte[] payload, int offset) {
        super(params, payload, offset);
    }

    public ProviderUpdateServiceTx(NetworkParameters params, int version, Sha256Hash proTxHash,
                                   MasternodeAddress address,
                                   Script scriptOperatorPayout, Sha256Hash inputsHash, BLSSignature signature) {
        this(params, version, proTxHash, address, scriptOperatorPayout, inputsHash);
        this.signature = signature;
        this.type = MasternodeType.REGULAR;
        length = MESSAGE_SIZE;
    }

    public ProviderUpdateServiceTx(NetworkParameters params, int version, Sha256Hash proTxHash,
                                   MasternodeAddress address,
                                   Script scriptOperatorPayout, Sha256Hash inputsHash, BLSSecretKey signingKey, boolean legacy) {
        this(params, version, proTxHash, address, scriptOperatorPayout, inputsHash);
        sign(signingKey, legacy);
    }

    @Override
    protected void parse() throws ProtocolException {
        super.parse();  //read version
        if (version == BASIC_BLS_VERSION) {
            type = MasternodeType.getMasternodeType(readUint16());
        }
        proTxHash = readHash();
        address = new MasternodeAddress(params, payload, cursor, params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT));
        cursor += address.getMessageSize();
        scriptOperatorPayout = new Script(readByteArray());
        inputsHash = readHash();
        if (version == BASIC_BLS_VERSION && type == MasternodeType.HIGHPERFORMANCE) {
            platformNodeID = new KeyId(params, payload, cursor);
            cursor += platformNodeID.getMessageSize();
            platformP2PPort = readUint16();
            platformHTTPPort = readUint16();
        }
        signature = new BLSSignature(params, payload, cursor, version == LEGACY_BLS_VERSION);
        cursor += signature.getMessageSize();

        length = cursor - offset;
    }

    protected void bitcoinSerializeWithoutSignature(OutputStream stream) throws IOException{
        super.bitcoinSerializeToStream(stream);
        if (version == BASIC_BLS_VERSION) {
            Utils.uint16ToByteStreamLE(type.index, stream);
        }
        stream.write(proTxHash.getReversedBytes());
        address.bitcoinSerialize(stream);
        if(scriptOperatorPayout != null)
            Utils.bytesToByteStream(scriptOperatorPayout.getProgram(), stream);
        else stream.write(new byte[0]);
        stream.write(inputsHash.getReversedBytes());

        if (version == BASIC_BLS_VERSION && type == MasternodeType.HIGHPERFORMANCE) {
            platformNodeID.bitcoinSerialize(stream);
            Utils.uint16ToByteStreamLE(platformP2PPort,stream);
            Utils.uint16ToByteStreamLE(platformHTTPPort,stream);
        }
    }
    @Override
    protected void bitcoinSerializeToStream(OutputStream stream) throws IOException {
        bitcoinSerializeWithoutSignature(stream);
        signature.bitcoinSerialize(stream, version == LEGACY_BLS_VERSION);
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
        signature = signingKey.sign(getSignatureHash());
        length = MESSAGE_SIZE;
        unCache();
    }

    void sign(BLSSecretKey signingKey, boolean legacy) {
        signature = signingKey.sign(getSignatureHash(), legacy);
        length = MESSAGE_SIZE;
        unCache();
    }

    public Sha256Hash getProTxHash() {
        return proTxHash;
    }

    public MasternodeAddress getAddress() {
        return address;
    }

    public Script getScriptOperatorPayout() {
        return scriptOperatorPayout;
    }
}
