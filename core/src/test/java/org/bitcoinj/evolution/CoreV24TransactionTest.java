/*
 * Copyright 2026 Dash Core Group.
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

import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.KeyId;
import org.bitcoinj.core.MasternodeAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.wallet.DefaultRiskAnalysis;
import org.dashj.bls.BLSJniLibrary;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Transactions as Dash Core v24 creates or decodes them, captured from a regtest node by
 * contrib/v24-fixtures/gen_v24_fixtures.py. Expected txids and fields come from Core itself.
 */
public class CoreV24TransactionTest {
    private static final RegTestParams REGTEST = RegTestParams.get();
    private static JSONObject vectors;

    static {
        BLSJniLibrary.init();
    }

    @BeforeClass
    public static void loadVectors() throws IOException {
        new Context(REGTEST);
        try (InputStream stream = Objects.requireNonNull(
                CoreV24TransactionTest.class.getResourceAsStream("v24-regtest-vectors.json"))) {
            byte[] bytes = new byte[stream.available()];
            int read = 0;
            while (read < bytes.length) {
                read += stream.read(bytes, read, bytes.length - read);
            }
            vectors = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static JSONObject vector(String name) {
        return vectors.has(name) ? vectors.getJSONObject(name) : vectors.getJSONObject("special").getJSONObject(name);
    }

    private static Transaction tx(String name) {
        return new Transaction(REGTEST, Utils.HEX.decode(vector(name).getString("hex")));
    }

    private static void assertTxId(String name, Transaction tx) {
        assertEquals(Sha256Hash.wrap(vector(name).getString("txid")), tx.getTxId());
    }

    /** the payload re-serialized from its parsed fields must be the payload on the wire */
    private static void assertPayloadRoundTrip(Transaction tx) {
        assertArrayEquals(tx.getExtraPayload(), tx.getExtraPayloadObject().getPayload());
    }

    private static MasternodeAddress address(JSONObject addresses, String purpose) {
        String[] parts = addresses.getJSONArray(purpose).getString(0).split(":");
        return new MasternodeAddress(parts[0], Integer.parseInt(parts[1]));
    }

    // §2 asset unlock v2: the txid excludes the quorum signing info

    @Test
    public void assetUnlockV2_txidExcludesQuorumSigningInfo() {
        Transaction tx = tx("assetunlock_v2_a");
        assertTrue(tx.isAssetUnlockWithStableTxid());
        assertTxId("assetunlock_v2_a", tx);
        assertEquals(Sha256Hash.wrap(vector("assetunlock_v2_a").getString("instanceHash")), tx.getInstanceHash());

        // re-signing changes only the trailing 132 bytes, so the txid must not move
        Transaction resigned = tx("assetunlock_v2_b");
        assertEquals(tx.getTxId(), resigned.getTxId());
        assertEquals(Sha256Hash.wrap(vector("assetunlock_v2_b").getString("instanceHash")), resigned.getInstanceHash());
        assertNotEquals(tx.getInstanceHash(), resigned.getInstanceHash());

        assertEquals(2, tx.getExtraPayloadObject().getVersion());
        assertPayloadRoundTrip(tx);
    }

    @Test
    public void assetUnlockV2_txidFromP2PMessage() {
        // the serializer hands the transaction the hash of the whole message, which is the instance hash
        byte[] bytes = Utils.HEX.decode(vector("assetunlock_v2_a").getString("hex"));
        BitcoinSerializer serializer = new BitcoinSerializer(REGTEST, false);
        Transaction tx = serializer.makeTransaction(bytes, 0, bytes.length, Sha256Hash.hashTwice(bytes));
        assertTxId("assetunlock_v2_a", tx);
    }

    @Test
    public void assetUnlockV1_txidUnchanged() {
        Transaction tx = tx("assetunlock_v1");
        assertFalse(tx.isAssetUnlockWithStableTxid());
        assertTxId("assetunlock_v1", tx);
        assertEquals(tx.getTxId(), tx.getInstanceHash());
        assertEquals(Sha256Hash.wrapReversed(Sha256Hash.hashTwice(tx.bitcoinSerialize())), tx.getTxId());
    }

    // §3 the new special transaction types

    @Test
    public void proDisTx_doesNotDecodeAsTypeMax() {
        Transaction tx = tx("prodistx");
        assertEquals(Transaction.Type.TRANSACTION_PROVIDER_DISSOLVE, tx.getType());
        assertEquals(10, vector("prodistx").getInt("type"));
        assertTrue(tx.getType().isSpecial());
        assertNull(tx.getExtraPayloadObject());
        assertTxId("prodistx", tx);
        assertEquals(vector("prodistx").getString("hex"), Utils.HEX.encode(tx.bitcoinSerialize()));
        assertTrue(tx.toString().contains("TRANSACTION_PROVIDER_DISSOLVE"));
    }

    @Test
    public void unknownSpecialTypes_roundTripWithoutDecoding() {
        assertEquals(Transaction.Type.TRANSACTION_PROVIDER_UPDATE_SHARE, tx("type11").getType());
        assertEquals(Transaction.Type.TRANSACTION_PROVIDER_UPDATE_SHARED_REGISTRAR, tx("type12").getType());
        for (String name : new String[]{"type11", "type12"}) {
            Transaction tx = tx(name);
            assertTxId(name, tx);
            assertEquals(vector(name).getString("hex"), Utils.HEX.encode(tx.bitcoinSerialize()));
        }
    }

    @Test
    public void v24Types_areStandardVersions() {
        for (String name : new String[]{"prodistx", "type11", "type12"}) {
            assertNotEquals(DefaultRiskAnalysis.RuleViolation.VERSION, DefaultRiskAnalysis.isStandard(tx(name)));
        }
        assertEquals(Transaction.Type.TRANSACTION_UNKNOWN, Transaction.Type.fromValue(13));
    }

    // §4 ProTx version 3 payloads

    @Test
    public void proRegTx_v3_evo_parsesExtAddr() {
        Transaction tx = tx("proregtx_v3_evo");
        JSONObject core = vector("proregtx_v3_evo").getJSONObject("json").getJSONObject("payload");
        ProviderRegisterTx p = (ProviderRegisterTx) tx.getExtraPayloadObject();
        assertEquals(3, p.getVersion());
        assertEquals(MasternodeType.HIGHPERFORMANCE.index, p.getMasternodeType());
        JSONObject addresses = core.getJSONObject("addresses");
        assertEquals(address(addresses, "core_p2p"), p.getAddress());
        assertEquals(address(addresses, "core_p2p"), p.getNetInfo().getPrimaryService());
        assertEquals(address(addresses, "platform_p2p").getPort(), p.getPlatformP2PPort());
        assertEquals(address(addresses, "platform_https").getPort(), p.getPlatformHTTPPort());
        assertEquals(KeyId.fromString(core.getString("platformNodeID")), p.getPlatformNodeID());
        assertEquals(Sha256Hash.wrap(core.getString("collateralHash")), p.getCollateralOutpoint().getHash());
        assertEquals(core.getString("ownerAddress"), p.getOwnerAddress().toString());
        assertEquals(core.getString("votingAddress"), p.getVotingAddress().toString());
        assertEquals(Sha256Hash.wrap(core.getString("inputsHash")), p.getInputsHash());
        assertPayouts(core.getJSONArray("payouts"), p.getPayouts());
        assertFalse(p.isShared());
        assertTxId("proregtx_v3_evo", tx);
        assertPayloadRoundTrip(tx);
    }

    @Test
    public void proRegTx_v3_regular_parsesPayouts() {
        Transaction tx = tx("proregtx_v3_regular");
        JSONObject core = vector("proregtx_v3_regular").getJSONObject("json").getJSONObject("payload");
        ProviderRegisterTx p = (ProviderRegisterTx) tx.getExtraPayloadObject();
        assertEquals(3, p.getVersion());
        assertEquals(MasternodeType.REGULAR.index, p.getMasternodeType());
        assertEquals(address(core.getJSONObject("addresses"), "core_p2p"), p.getAddress());
        assertEquals(2, p.getPayouts().size());
        assertPayouts(core.getJSONArray("payouts"), p.getPayouts());
        assertEquals(core.getString("pubKeyOperator"), p.getPubkeyOperator().toString());
        assertEquals(Sha256Hash.wrap(core.getString("inputsHash")), p.getInputsHash());
        assertTxId("proregtx_v3_regular", tx);
        assertPayloadRoundTrip(tx);
    }

    @Test
    public void proUpRegTx_v3_parsesPayouts() {
        Transaction tx = tx("proupregtx_v3");
        JSONObject core = vector("proupregtx_v3").getJSONObject("json").getJSONObject("payload");
        ProviderUpdateRegistarTx p = (ProviderUpdateRegistarTx) tx.getExtraPayloadObject();
        assertEquals(3, p.getVersion());
        assertEquals(Sha256Hash.wrap(core.getString("proTxHash")), p.getProTxHash());
        assertPayouts(core.getJSONArray("payouts"), p.getPayouts());
        assertEquals(Sha256Hash.wrap(core.getString("inputsHash")), p.getInputsHash());
        assertTxId("proupregtx_v3", tx);
        assertPayloadRoundTrip(tx);
    }

    @Test
    public void proUpServTx_v3_parsesExtAddr() {
        Transaction tx = tx("proupservtx_v3_evo");
        JSONObject core = vector("proupservtx_v3_evo").getJSONObject("json").getJSONObject("payload");
        ProviderUpdateServiceTx p = (ProviderUpdateServiceTx) tx.getExtraPayloadObject();
        assertEquals(3, p.getVersion());
        assertEquals(MasternodeType.HIGHPERFORMANCE, p.getMasternodeType());
        assertEquals(Sha256Hash.wrap(core.getString("proTxHash")), p.getProTxHash());
        JSONObject addresses = core.getJSONObject("addresses");
        assertEquals(address(addresses, "core_p2p"), p.getAddress());
        assertEquals(address(addresses, "platform_p2p").getPort(), p.getPlatformP2PPort());
        assertEquals(address(addresses, "platform_https").getPort(), p.getPlatformHTTPPort());
        assertEquals(KeyId.fromString(core.getString("platformNodeID")), p.getPlatformNodeID());
        assertEquals(Sha256Hash.wrap(core.getString("inputsHash")), p.getInputsHash());
        assertTxId("proupservtx_v3_evo", tx);
        assertPayloadRoundTrip(tx);
    }

    @Test
    public void proRegTx_v3_sharedRegistration_roundTrips() throws IOException {
        // Core's v3 regular registration, with two collateral shares and their raw join signatures spliced in
        ProviderRegisterTx regular = (ProviderRegisterTx) tx("proregtx_v3_regular").getExtraPayloadObject();
        byte[] original = regular.getPayload();
        // everything after the shares count: early period, early penalty, inputs hash and the signature
        int tailLength = 4 + 8 + 32 + regular.getSignature().getMessageSize();
        int sharesCountOffset = original.length - tailLength - 1;
        assertEquals(0, original[sharesCountOffset]);

        String refund1 = "76a914" + "01".repeat(20) + "88ac";
        String refund2 = "76a914" + "02".repeat(20) + "88ac";
        String reward2 = "76a914" + "03".repeat(20) + "88ac";
        UnsafeByteArrayOutputStream payload = new UnsafeByteArrayOutputStream();
        payload.write(original, 0, sharesCountOffset);
        payload.write(2);
        Utils.int64ToByteStreamLE(Coin.COIN.multiply(600).value, payload);
        Utils.bytesToByteStream(Utils.HEX.decode(refund1), payload);
        Utils.bytesToByteStream(new byte[0], payload);                         // reward script: use the refund
        payload.write(Utils.HEX.decode("aa".repeat(20)));
        Utils.int64ToByteStreamLE(Coin.COIN.multiply(400).value, payload);
        Utils.bytesToByteStream(Utils.HEX.decode(refund2), payload);
        Utils.bytesToByteStream(Utils.HEX.decode(reward2), payload);
        payload.write(Utils.HEX.decode("bb".repeat(20)));
        payload.write(Utils.HEX.decode("1c" + "11".repeat(64)));               // join signatures, no length prefix
        payload.write(Utils.HEX.decode("1f" + "22".repeat(64)));
        Utils.uint32ToByteStreamLE(576, payload);                               // nEarlyPeriodBlocks
        Utils.int64ToByteStreamLE(Coin.COIN.multiply(5).value, payload);        // nEarlyPenalty
        payload.write(original, original.length - tailLength + 12, tailLength - 12);
        byte[] bytes = payload.toByteArray();

        Transaction tx = new Transaction(REGTEST);
        tx.setVersionAndType(Transaction.SPECIAL_VERSION, Transaction.Type.TRANSACTION_PROVIDER_REGISTER);
        tx.setExtraPayload(bytes);
        ProviderRegisterTx p = (ProviderRegisterTx) tx.getExtraPayloadObject();
        assertEquals(bytes.length, p.getMessageSize());
        assertTrue(p.isShared());
        List<CollateralShare> shares = p.getShares();
        assertEquals(2, shares.size());
        assertEquals(Coin.COIN.multiply(600), shares.get(0).getAmount());
        assertEquals(refund1, Utils.HEX.encode(shares.get(0).getRewardScript().getProgram()));
        assertEquals(reward2, Utils.HEX.encode(shares.get(1).getRewardScript().getProgram()));
        assertEquals(576, p.getEarlyPeriodBlocks());
        assertEquals(Coin.COIN.multiply(5), p.getEarlyPenalty());
        assertEquals(regular.getInputsHash(), p.getInputsHash());
        assertEquals(regular.getPayouts(), p.getPayouts());
        assertArrayEquals(bytes, p.getPayload());
    }

    private static void assertPayouts(JSONArray core, List<MasternodePayoutShare> payouts) {
        assertEquals(core.length(), payouts.size());
        for (int i = 0; i < core.length(); i++) {
            JSONObject payout = core.getJSONObject(i);
            assertEquals(payout.getString("script"), Utils.HEX.encode(payouts.get(i).getScriptPayout().getProgram()));
            assertEquals(payout.getInt("reward"), payouts.get(i).getReward());
            assertEquals(payout.getString("address"), payouts.get(i).getScriptPayout().getToAddress(REGTEST).toString());
        }
    }

    // §5 CbTx version 4

    @Test
    public void cbTxV4_parsesAndRoundTrips() {
        Transaction cb = tx("cbtx_v4");
        JSONObject core = vector("cbtx_v4").getJSONObject("cbTx");
        CoinbaseTx p = (CoinbaseTx) cb.getExtraPayloadObject();
        assertEquals(4, p.getVersion());
        assertEquals(core.getLong("height"), p.getHeight());
        assertEquals(Sha256Hash.wrap(core.getString("merkleRootMNList")), p.getMerkleRootMasternodeList());
        assertEquals(Sha256Hash.wrap(core.getString("merkleRootQuorums")), p.getMerkleRootQuorums());
        assertEquals(Sha256Hash.wrap(core.getString("merkleRootAssetUnlocks")), p.getMerkleRootAssetUnlocks());
        assertEquals(vector("cbtx_v4").getString("hex"), Utils.HEX.encode(cb.bitcoinSerialize()));
        assertTxId("cbtx_v4", cb);
        assertPayloadRoundTrip(cb);
    }
}
