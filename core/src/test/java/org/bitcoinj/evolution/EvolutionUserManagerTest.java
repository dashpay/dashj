package org.bitcoinj.evolution;

import org.bitcoinj.core.*;
import org.bitcoinj.params.UnitTestParams;
import org.bitcoinj.script.ScriptBuilder;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.bitcoinj.script.ScriptOpCodes.OP_RETURN;

public class EvolutionUserManagerTest {
    Context context;
    UnitTestParams PARAMS;
    byte[] registerTxData;
    byte[] topupTxData;

    @Before
    public void startup() {
        PARAMS = UnitTestParams.get();
        context = Context.getOrCreate(PARAMS);
        registerTxData = Utils.HEX.decode("03000800013f39fe95e37ce75bf7de2a89496e8c485f75f808b597c7c11fe9f023ec8726d3010000006a473044022033bafeac5704355c7855a6ad099bd6834cbcf3b052e42ed83945c58aae904aa4022073e747d376a8dcd2b5eb89fef274b01c0194ee9a13963ebbc657963417f0acf3012102393c140e7b53f3117fd038581ae66187c4be33f49e33a4c16ffbf2db1255e985feffffff0240420f0000000000016a9421be1d000000001976a9145f461d2cdae3e8244c6dbc6de58ad06ccd22890388ac000000006101000873616d697366756ec3bfec8ca49279bb1375ad3461f654ff1a277d464120f19af9563ef387fef19c82bc4027152ef5642fe8158ffeb3b8a411d9a967b6af0104b95659106c8a9d7451478010abe042e58afc9cdaf006f77cab16edcb6f84");
        topupTxData = Utils.HEX.decode("0300090001d4ad073ec40da120d28a47164753f4f5ad80d0dc3b918b39223d36ebdfacdef6000000006a47304402203822cbd38bc5dfe4fe0ec7f3740d09a2a05ed67020879c3b364ea0e7348880bd022039c3ea29f261060353099a5596e268ae5e4c928e59211e4ffeddf10de128bcf10121034c1f31446c5971558b9027499c3678483b0deb06af5b5ccd41e1f536af1e34cafeffffff0200e1f50500000000016ad2d327cc050000001976a9141eccbe2508c7741d2e4c517f87565e7d477cfbbc88ac00000000220100e079491e35935f9fe4c4853f5a6c1c6fbf3bad40b5be7f58b8bd32b3ce68338f");
    }

    @Test
    public void createtopup() {
        Sha256Hash blockchainUserRegistrationTransactionHash = Sha256Hash.wrap("8f3368ceb332bdb8587fbeb540ad3bbf6f1c6c5a3f85c4e49f5f93351e4979e0");
        Sha256Hash inputId = Sha256Hash.wrap("f6deacdfeb363d22398b913bdcd080adf5f4534716478ad220a10dc43e07add4");
        Address outputAddress = Address.fromBase58(PARAMS, "yP8JPjWoc2u8rSN6F4eE5FQn3nQiQJ9jDs");
        Sha256Hash txId = Sha256Hash.wrap("715f96a80e0e4feb8a94f2e9f4f6821dd4502f0ae6c43013ec6e77985d059b55");
        Address inputAddress = Address.fromBase58(PARAMS, "yYZqfmQhqMSF1PL7xeNHzQM3q9rktXFPLN");
        String inputPrivateKey = "cNYPkC4hGoE11ieBr2GgwyUct8zY1HLi5S5K2LLPMewtQGJsbu9H";
        ECKey privateKey = DumpedPrivateKey.fromBase58(PARAMS, inputPrivateKey).getKey();

        Transaction tx = new Transaction(PARAMS);
        tx.setVersion(3);
        tx.setType(Transaction.Type.TRANSACTION_SUBTX_TOPUP);
        TransactionOutPoint outpoint = new TransactionOutPoint(PARAMS, 0, inputId);
        TransactionInput txin = new TransactionInput(PARAMS, null, new byte[0], outpoint);
        txin.setSequenceNumber(TransactionInput.NO_SEQUENCE-1);

        TransactionOutput output = new TransactionOutput(PARAMS, null, Coin.valueOf(24899998674L), ScriptBuilder.createOutputScript(outputAddress).getProgram());

        TransactionOutput outputOpReturn = new TransactionOutput(PARAMS, null, Coin.valueOf(100000000), new ScriptBuilder().op(OP_RETURN).build().getProgram());
        tx.addOutput(outputOpReturn);
        tx.addOutput(output);

        SubTxTopup subtx = new SubTxTopup(1, blockchainUserRegistrationTransactionHash);
        tx.setExtraPayload(subtx.getPayload());

        tx.addSignedInput(txin, ScriptBuilder.createOutputScript(inputAddress), privateKey, Transaction.SigHash.ALL, false);
        assertEquals(3, tx.getVersion());
        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            tx.bitcoinSerialize(bos);
            assertEquals(tx.getVersion(), 3);
        } catch (IOException x) {
            fail();
        }
    }

    @Test
    public void testManager() {
        EvolutionUserManager manager = new EvolutionUserManager(context);
        Transaction tx = new Transaction(PARAMS, registerTxData);

        if(!manager.checkSubTxRegister(tx, null))
            fail();

        if(!manager.processSubTxRegister(tx, null, Coin.valueOf(10000)))
            fail();

        EvolutionUser user = manager.getUser(tx.getHash());
        EvolutionUser currentUser = manager.getCurrentUser();

        SubTxRegister subtx = (SubTxRegister)tx.getExtraPayloadObject();
        assertEquals(subtx.getUserName(), user.userName);
        assertEquals(subtx.getPubKeyId(), user.curPubKeyID);

        Transaction secondTx = new Transaction(PARAMS, topupTxData);
        SubTxTopup topup = (SubTxTopup)secondTx.getExtraPayloadObject();

        if(!manager.checkSubTxTopup(secondTx, null))
            fail();

        if(!manager.processSubTxTopup(secondTx, null, Coin.valueOf(10000)))
            fail();

        assertEquals(101000000L, user.getCreditBalance().getValue());

        try {
            UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream();
            manager.bitcoinSerialize(bos);
            EvolutionUserManager secondManager = new EvolutionUserManager(PARAMS, bos.toByteArray());
            EvolutionUser secondUser = secondManager.getUser(tx.getHash());
            EvolutionUser secondCurrentUser = secondManager.getCurrentUser();

            assertEquals(subtx.getUserName(), secondUser.getUserName());
            assertEquals(subtx.getPubKeyId(), secondUser.getCurPubKeyID());
            assertEquals(tx.getHash(), secondUser.getRegTx().getHash());
            assertEquals(currentUser.getRegTxId(), secondCurrentUser.getRegTxId());
        } catch (IOException x) {
            fail();
        }

    }
}
