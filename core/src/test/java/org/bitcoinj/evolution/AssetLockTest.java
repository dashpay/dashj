package org.bitcoinj.evolution;

import com.google.common.collect.Lists;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.BLSLazySignature;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.DefaultRiskAnalysis;
import org.bitcoinj.wallet.SendRequest;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.bitcoinj.core.Coin.CENT;
import static org.bitcoinj.core.Coin.COIN;
import static org.bitcoinj.core.Utils.HEX;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AssetLockTest {
    UnitTestParams PARAMS = UnitTestParams.get();
    Context context = new Context(PARAMS);
    ArrayList<Transaction> dummyTransactions;
    ArrayList<TransactionOutput> dummyOutputs;

    @Before
    public void setupDummyInputs()
    {
        dummyTransactions = Lists.newArrayListWithCapacity(2);
        dummyTransactions.add(new Transaction(PARAMS));
        dummyTransactions.add(new Transaction(PARAMS));
        dummyOutputs = Lists.newArrayListWithCapacity(4);
        // Add some keys to the keystore:
        ECKey [] key = new ECKey[] {
                new ECKey(), new ECKey(), new ECKey(), new ECKey()
        };

        dummyTransactions.get(0).addOutput(CENT.multiply(11), ScriptBuilder.createP2PKOutputScript(key[0]));
        dummyTransactions.get(0).addOutput(CENT.multiply(50), ScriptBuilder.createP2PKOutputScript(key[1]));
        dummyOutputs.addAll(dummyTransactions.get(0).getOutputs());


        dummyTransactions.get(0).addOutput(CENT.multiply(21), ScriptBuilder.createP2PKOutputScript(key[2]));
        dummyTransactions.get(0).addOutput(CENT.multiply(22), ScriptBuilder.createP2PKOutputScript(key[3]));
        dummyOutputs.addAll(dummyTransactions.get(1).getOutputs());
    }

    private Transaction createAssetLockTx(ECKey key) {

        ArrayList<TransactionOutput> creditOutputs = Lists.newArrayListWithCapacity(2);
        creditOutputs.add(new TransactionOutput(PARAMS, null, CENT.multiply(17), ScriptBuilder.createP2PKOutputScript(key).getProgram()));
        creditOutputs.add(new TransactionOutput(PARAMS, null, CENT.multiply(13), ScriptBuilder.createP2PKOutputScript(key).getProgram()));

        AssetLockPayload assetLockTx = new AssetLockPayload(PARAMS, 1, creditOutputs);

        Transaction tx = new Transaction(PARAMS);
        tx.setVersionAndType(3, Transaction.Type.TRANSACTION_ASSET_LOCK);
        tx.setExtraPayload(assetLockTx);
        tx.addInput(dummyTransactions.get(0).getTxId(), 1, new Script(new byte[65]));
        tx.addOutput(CENT.multiply(30), ScriptBuilder.createOpReturnScript(new byte[0]));
        tx.addOutput(CENT.multiply(20), ScriptBuilder.createP2PKOutputScript(key));
        return tx;
    }

    private Transaction createAssetUnlockTx(ECKey key)
    {
        int version = 1;
        // just a big number bigger than uint32_t
        long index = 0x001122334455667788L;
        // big enough to overflow int32_t
        int fee = 2000000000;
        // just big enough to overflow uint16_t
        int requestedHeight = 1000000;
        Sha256Hash quorumHash = Sha256Hash.ZERO_HASH;
        BLSLazySignature quorumSig = new BLSLazySignature(PARAMS);
        AssetUnlockPayload assetUnlockTx = new AssetUnlockPayload(PARAMS, version, index, fee, requestedHeight, quorumHash, quorumSig);

        Transaction tx = new Transaction(PARAMS);
        tx.setVersionAndType(3, Transaction.Type.TRANSACTION_ASSET_UNLOCK);
        tx.setExtraPayload(assetUnlockTx);

        tx.addOutput(CENT.multiply(10), ScriptBuilder.createP2PKOutputScript(key));
        tx.addOutput(CENT.multiply(20), ScriptBuilder.createP2PKOutputScript(key));

        return tx;
    }

    @Test
    public void assetLock() {
        ECKey key = new ECKey();

        Transaction tx = createAssetLockTx(key);
        assertSame(DefaultRiskAnalysis.RuleViolation.NONE, DefaultRiskAnalysis.isStandard(tx));

        tx.verify();
        assertTrue(tx.getInputs().stream().allMatch(input -> DefaultRiskAnalysis.isInputStandard(input) == DefaultRiskAnalysis.RuleViolation.NONE));

        // Check version
        assertEquals(3, tx.getVersionShort());
        AssetLockPayload lockPayload = (AssetLockPayload) tx.getExtraPayloadObject();
        assertEquals(1, lockPayload.getVersion());
    }

    @Test
    public void assetUnlock() {
        ECKey key = new ECKey();
        final Transaction tx = createAssetUnlockTx(key);
        assertSame(DefaultRiskAnalysis.RuleViolation.NONE, DefaultRiskAnalysis.isStandard(tx));
        tx.verify();

        // Check version
        assertEquals(3, tx.getVersionShort());
        AssetUnlockPayload lockPayload = (AssetUnlockPayload) tx.getExtraPayloadObject();
        assertEquals(1, lockPayload.getVersion());
    }

    @Test
    public void assetLockTransaction() {
        byte[] txData = HEX.decode("0300080001c0ca6104d7d94599c38656bbfec8ec925a9811f0b2463c9ba9ea2b609d233106010000006a473044022033d6a940edc8cb83e970d84271e0a18b95efa6f471bced3926f9352c4265aa8f02200889d172ddaba02e5862300402a34edb6536d6b11d8d1880b36b7d531c0c18cb0121039356f0ec5aac76734d5bf26825b390d8a533b400be99c9b55cc1cfe4d1468881000000000247983ea20200000023210327eea375486607a9b92ae4d48355e8f47f26358768792570d5c7c9b153a17996ac2f959f3b00000000026a000000000046010200e1f505000000001976a91437297a20af91ebe053c6dd53eea1b8eb68b70b4188ac2fb4a935000000001976a91437297a20af91ebe053c6dd53eea1b8eb68b70b4188ac");
        Transaction tx = new Transaction(PARAMS, txData, 0);
        AssetLockPayload assetLockPayload = (AssetLockPayload) tx.getExtraPayloadObject();
        assertEquals(1, assetLockPayload.getVersion());
        assertEquals(Transaction.Type.TRANSACTION_ASSET_LOCK, assetLockPayload.getType());
        assertEquals(2, assetLockPayload.getCreditOutputs().size());
        assertEquals(Coin.COIN, assetLockPayload.getCreditOutputs().get(0).getValue());
        assertEquals(ScriptBuilder.createOutputScript(Address.fromBase58(PARAMS, "yRM7hdX9SoP5giL37oX11fdWofFf2t9kUv")), assetLockPayload.getCreditOutputs().get(0).getScriptPubKey());
        assertTrue(tx.getOutputs().stream().anyMatch(output -> ScriptPattern.isAssetLock(output.getScriptPubKey())));
    }

    @Test
    public void createAssetLockTransaction() {
        ECKey privateKey = new ECKey();
        Coin credits = COIN;
        SendRequest request = SendRequest.assetLock(PARAMS, privateKey, credits);
        AssetLockPayload payload = (AssetLockPayload) request.tx.getExtraPayloadObject();
        assertEquals(AssetLockPayload.CURRENT_VERSION, payload.getVersion());
        assertEquals(1, payload.getCreditOutputs().size());
        assertEquals(COIN, payload.getCreditOutputs().get(0).getValue());
        assertArrayEquals(privateKey.getPubKeyHash(), ScriptPattern.extractHashFromP2PKH(payload.getCreditOutputs().get(0).getScriptPubKey()));
        assertTrue(request.tx.getOutputs().stream().anyMatch(output -> ScriptPattern.isAssetLock(output.getScriptPubKey())));
    }

    @Test
    public void assetUnlockTransaction() {
        byte[] txData = HEX.decode("03000900000190cff405000000002321039a6271d3b5903f32f7a8ad72d98e69c7b1082a75f823b7d805c44d4661a28b90ac000000009101650000000000000070110100cb0500000a1a259b5f2e1dfef995c31d031bdd0eb246b2927141e20bb0c85de928a9800f83df18fb02820af5d579b663bd4765026846ad9ed1a654f0c3678a02496599a92e4cdb2e07e5703521f43f823c2560180d5d97f36f876a99ba9db87f86d6398be1a43e3dde88a7fd445a805eaa8a22426bf201653daf100fbaf58d9a4f1d809d");
        Transaction tx = new Transaction(PARAMS, txData, 0);
        AssetUnlockPayload assetUnlockPayload = (AssetUnlockPayload) tx.getExtraPayloadObject();
        assertEquals(1, assetUnlockPayload.getVersion());
        assertEquals(Transaction.Type.TRANSACTION_ASSET_UNLOCK, assetUnlockPayload.getType());
        assertEquals(101, assetUnlockPayload.getIndex());
        assertEquals(70000, assetUnlockPayload.getFee());
        assertEquals(1483, assetUnlockPayload.getRequestedHeight());
        assertEquals(Sha256Hash.wrap("0f80a928e95dc8b00be2417192b246b20edd1b031dc395f9fe1d2e5f9b251a0a"), assetUnlockPayload.getQuorumHash());
        assertEquals("83df18fb02820af5d579b663bd4765026846ad9ed1a654f0c3678a02496599a92e4cdb2e07e5703521f43f823c2560180d5d97f36f876a99ba9db87f86d6398be1a43e3dde88a7fd445a805eaa8a22426bf201653daf100fbaf58d9a4f1d809d", assetUnlockPayload.getQuorumSig().toStringHex());
    }
}
